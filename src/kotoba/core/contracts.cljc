(ns kotoba.core.contracts
  "CLJC accessors and validation for Kotoba core EDN contracts."
  (:require [clojure.string :as str]
            #?(:clj [clojure.edn :as edn])
            #?(:clj [clojure.java.io :as io])))

(def source-contract-resource "kotoba/lang/source_contract.edn")
(def capability-contract-resource "kotoba/runtime/capability_contract.edn")

#?(:clj
   (defn resource-edn [path]
     (let [resource (io/resource path)]
       (when-not resource
         (throw (ex-info "missing Kotoba core contract resource" {:path path})))
       (-> resource slurp edn/read-string))))

#?(:clj
   (defn source-contract []
     (resource-edn source-contract-resource)))

#?(:clj
   (defn capability-contract []
     (resource-edn capability-contract-resource)))

(defn source-extension
  "Return the lowercase extension for a path-like string, including the dot."
  [path]
  #?(:clj
     (let [name (str path)
           idx (.lastIndexOf name ".")]
       (when (and (not= idx -1)
                  (< idx (dec (count name))))
         (str/lower-case (subs name idx))))
     :cljs nil))

(defn source-kind
  "Classify a source path under the source contract."
  [contract path]
  (let [ext (source-extension path)]
    (some (fn [[kind spec]]
            (when (some #{ext} (:extensions spec))
              kind))
          (:source-kinds contract))))

(defn accepted-source?
  [contract path]
  (boolean (source-kind contract path)))

(defn source-plan
  "Return source dispatch data from a source contract."
  ([contract path] (source-plan contract path nil))
  ([contract path reader-target]
   (let [kind (source-kind contract path)
         spec (get-in contract [:source-kinds kind])
         target (or reader-target (:reader-target spec) (:default-reader-target contract))
         allowed-targets (set (or (:reader-targets spec)
                                  [(:reader-target spec)]))]
     (when-not kind
       (throw (ex-info "unsupported Kotoba source extension"
                       {:path path
                        :extension (source-extension path)
                        :accepted-extensions (->> (:source-kinds contract)
                                                  vals
                                                  (mapcat :extensions)
                                                  distinct
                                                  vec)})))
     (when-not (contains? allowed-targets target)
       (throw (ex-info "unsupported Kotoba reader target"
                       {:path path
                        :kind kind
                        :reader-target target
                        :accepted-reader-targets (vec allowed-targets)})))
     {:kotoba.source/path (str path)
      :kotoba.source/kind kind
      :kotoba.source/extension (source-extension path)
      :kotoba.source/reader-target target
      :kotoba.source/canonical? (boolean (:canonical? spec))
      :kotoba.source/portable? (boolean (:portable? spec))
      :kotoba.source/data? (boolean (:data? spec))
      :kotoba.source/safe-gate-required? (boolean (:safe-gate-required? spec))})))

(defn capability-name
  [value]
  (cond
    (keyword? value) (str (namespace value) "/" (name value))
    (string? value) value
    :else (str value)))

(defn- positive-int? [value]
  (and (int? value) (pos? value)))

(defn capability-id
  [contract value]
  (get (:capability-ids contract) (capability-name value)))

(defn policy-capabilities
  [policy]
  (set (map capability-name (or (:kotoba.policy/capabilities policy)
                                (:capabilities policy)))))

(defn host-import-order
  [contract]
  (:host-import-order contract))

(defn host-imports
  [contract]
  (:host-imports contract))

(defn validate-source-contract
  [contract]
  (let [source-kinds (:source-kinds contract)
        namespace-resolution (:namespace-resolution contract)
        extensions (mapcat :extensions (vals source-kinds))
        known-kinds (set (keys source-kinds))
        known-reader-targets (set (concat (map :reader-target (vals source-kinds))
                                          (mapcat :reader-targets (vals source-kinds))))]
    (vec
     (concat
      (when (not= "kotoba.lang.source-contract.v0" (:schema contract))
        [{:problem :unexpected-schema :schema (:schema contract)}])
      (when-not (= "kotoba-lang/kotoba-core-contracts" (:authority contract))
        [{:problem :unexpected-authority :authority (:authority contract)}])
      (when (not (map? source-kinds))
        [{:problem :missing-source-kinds}])
      (when (not= (count extensions) (count (distinct extensions)))
        [{:problem :duplicate-extensions :extensions extensions}])
      (keep (fn [[kind {:keys [extensions reader-target reader-targets]}]]
              (cond
                (not (and (vector? extensions)
                          (seq extensions)
                          (every? #(and (string? %) (str/starts-with? % ".")) extensions)))
                {:problem :invalid-source-extensions :kind kind :extensions extensions}

                (not (keyword? reader-target))
                {:problem :invalid-reader-target :kind kind :reader-target reader-target}

                (and reader-targets
                     (or (not (vector? reader-targets))
                         (not (every? keyword? reader-targets))
                         (not (some #{reader-target} reader-targets))))
                {:problem :invalid-reader-targets :kind kind :reader-targets reader-targets}))
            source-kinds)
      (when-not (contains? known-kinds (:default-reader-target contract))
        [{:problem :default-reader-target-not-source-kind
          :default-reader-target (:default-reader-target contract)}])
      (when (not (map? namespace-resolution))
        [{:problem :missing-namespace-resolution}])
      (keep (fn [[target order]]
              (cond
                (not (contains? known-reader-targets target))
                {:problem :namespace-resolution-unknown-target :target target}

                (or (not (vector? order))
                    (not (every? #(some #{%} extensions) order)))
                {:problem :invalid-namespace-resolution-order
                 :target target
                 :order order}))
            namespace-resolution)))))

(defn validate-capability-contract
  [contract]
  (let [imports (:host-imports contract)
        order (:host-import-order contract)
        ids (:capability-ids contract)
        special-forms (:special-forms contract)]
    (vec
     (concat
      (when (not= "kotoba.runtime.capability-contract.v0" (:schema contract))
        [{:problem :unexpected-schema :schema (:schema contract)}])
      (when-not (= "kotoba-lang/kotoba-core-contracts" (:authority contract))
        [{:problem :unexpected-authority :authority (:authority contract)}])
      (when (not (map? ids))
        [{:problem :missing-capability-ids}])
      (when (not (map? imports))
        [{:problem :missing-host-imports}])
      (when-not (and (set? special-forms) (every? symbol? special-forms))
        [{:problem :invalid-special-forms :special-forms special-forms}])
      (when-not (and (vector? order) (every? symbol? order))
        [{:problem :invalid-host-import-order :host-import-order order}])
      (when (map? ids)
        (let [values (vals ids)]
          (concat
           (when-not (every? string? (keys ids))
             [{:problem :capability-id-keys-must-be-strings}])
           (when-not (every? positive-int? values)
             [{:problem :capability-id-values-must-be-positive-ints}])
           (when-not (= (count values) (count (distinct values)))
             [{:problem :duplicate-capability-ids :ids values}]))))
      (when (and (map? imports) (vector? order))
        (let [import-ops (set (keys imports))
              ordered-ops (set order)]
          (concat
           (when-not (= (count order) (count (distinct order)))
             [{:problem :duplicate-host-import-order :host-import-order order}])
           (when-not (= import-ops ordered-ops)
             [{:problem :host-import-order-must-cover-imports
               :missing-from-order (vec (sort-by str (remove ordered-ops import-ops)))
               :missing-from-imports (vec (sort-by str (remove import-ops ordered-ops)))}]))))
      (keep (fn [op]
              (when-not (contains? imports op)
                {:problem :ordered-import-missing-definition :op op}))
            order)
      (keep (fn [[op {:keys [capability params module field]}]]
              (cond
                (not (and module field (vector? params)))
                {:problem :invalid-host-import-shape :op op}

                (and capability (nil? (get ids capability)))
                {:problem :host-import-unknown-capability
                 :op op
                 :capability capability}))
            imports)))))

(defn validate-all
  ([] #?(:clj (validate-all (source-contract) (capability-contract))
         :cljs []))
  ([source capability]
   (vec (concat (validate-source-contract source)
                (validate-capability-contract capability)))))
