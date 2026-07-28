(ns kotoba.core.capability-repository
  "Contract and catalog for one-authority-capability-per-repository packages."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [kotoba.core.actor-capability :as actor-capability]))

(def schema "kotoba.capability.repository.v1")
(def authority "kotoba-lang/kotoba-core-contracts")

(def actor-host-capability-ids
  "Atomic authority capabilities in actor:host v0. Composition capabilities
  such as :organism/heartbeat are deliberately absent."
  {:gen-keypair "identity/keypair"
   :sign "identity/sign"
   :verify "identity/verify"
   :sha256-hex "hash/sha256"
   :http-post "http/post"
   :http-post-headers "http/post"
   :http-fetch "http/fetch"
   :log-read "log/read"
   :log-write "log/write"
   :clock-monotonic "clock/monotonic"
   :llm-infer "llm/infer"
   :cbor-encode "data/cbor"
   :json-encode "data/json"
   :json-extract-field "data/json"})

(defn repository-name [capability-id]
  (str "capability-" (str/replace capability-id "/" "-")))

(defn namespace-symbol [capability-id]
  (symbol
   (str "kotoba.capability."
        (str/replace capability-id #"[/_-]" "."))))

(defn imports-by-capability []
  (reduce-kv
   (fn [out import capability-id]
     (update out capability-id (fnil conj #{}) import))
   {}
   actor-host-capability-ids))

(defn effects-by-capability []
  (reduce-kv
   (fn [out capability-id imports]
     (assoc out capability-id (actor-capability/effects-for imports)))
   {}
   (imports-by-capability)))

(defn default-policy [effects]
  (cond
    (seq (set/intersection effects #{:network-write :secret}))
    :approval-required

    :else :autonomous))

(defn repository-manifest [capability-id]
  (let [imports (get (imports-by-capability) capability-id)
        effects (get (effects-by-capability) capability-id)]
    (when-not (seq imports)
      (throw (ex-info "unknown actor:host capability"
                      {:capability/id capability-id})))
    {:schema schema
     :authority authority
     :capability/id capability-id
     :capability/version 1
     :capability/repository
     (str "kotoba-lang/" (repository-name capability-id))
     :capability/imports imports
     :capability/effects effects
     :capability/default-policy (default-policy effects)
     :capability/provider-status :contract-only
     :capability/dependencies #{}
     :capability/artifact
     {:format :wasm-component
      :digest-required? true
      :signature-required? true}}))

(defn actor-host-catalog []
  (->> (keys (imports-by-capability))
       sort
       (mapv repository-manifest)))

(defn validate-manifest [manifest]
  (let [capability-id (:capability/id manifest)
        expected (when (string? capability-id)
                   (try
                     (repository-manifest capability-id)
                     (catch #?(:clj Exception :cljs :default) _ nil)))
        artifact (:capability/artifact manifest)]
    (vec
     (concat
      (when-not (= schema (:schema manifest))
        [{:problem :unexpected-schema}])
      (when-not (= authority (:authority manifest))
        [{:problem :unexpected-authority}])
      (when-not expected
        [{:problem :unknown-capability :capability/id capability-id}])
      (when (and expected
                 (not= (:capability/repository expected)
                       (:capability/repository manifest)))
        [{:problem :repository-name-mismatch
          :expected (:capability/repository expected)}])
      (when (and expected
                 (not= (:capability/imports expected)
                       (:capability/imports manifest)))
        [{:problem :import-surface-mismatch
          :expected (:capability/imports expected)}])
      (when (and expected
                 (not= (:capability/effects expected)
                       (:capability/effects manifest)))
        [{:problem :effect-surface-mismatch
          :expected (:capability/effects expected)}])
      (when-not (empty? (:capability/dependencies manifest))
        [{:problem :capability-dependencies-forbidden}])
      (when-not (= :wasm-component (:format artifact))
        [{:problem :artifact-format-must-be-wasm-component}])
      (when-not (true? (:digest-required? artifact))
        [{:problem :artifact-digest-required}])
      (when-not (true? (:signature-required? artifact))
        [{:problem :artifact-signature-required}])))))

(defn validate-catalog [catalog]
  (let [ids (map :capability/id catalog)
        repos (map :capability/repository catalog)
        imports (mapcat :capability/imports catalog)]
    (vec
     (concat
      (mapcat validate-manifest catalog)
      (when-not (= (count ids) (count (distinct ids)))
        [{:problem :duplicate-capability-id}])
      (when-not (= (count repos) (count (distinct repos)))
        [{:problem :duplicate-capability-repository}])
      (when-not (= (set imports) actor-capability/known-imports)
        [{:problem :actor-host-import-coverage
          :missing (set/difference actor-capability/known-imports (set imports))
          :excess (set/difference (set imports)
                                  actor-capability/known-imports)}])))))
