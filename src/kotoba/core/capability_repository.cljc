(ns kotoba.core.capability-repository
  "Contract and catalog for one-authority-capability-per-repository packages."
  (:require [cbor.core :as cbor]
            [clojure.set :as set]
            [clojure.string :as str]
            [kotoba.core.actor-capability :as actor-capability]
            [multiformats.core :as mf]))

(def schema "kotoba.capability.repository.v1")
(def definition-schema "kotoba.capability-definition.v1")
(def definition-hash-contract
  "kotoba.capability-definition.v1|dag-cbor|cidv1|sha2-256|name-independent|actor-host-v0")
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

(def radicle-rids
  {"audio/io" "rad:zX2TtjXmQDxFG7RcjGFJ2fjZXAuB"
   "ble/scan" "rad:z3acpKDbCk1fnoJAeCzqDYubTdQ1u"
   "calendar/read" "rad:z2ma8sQFJZ11Zqh4wGqVNN3KxB5Qf"
   "cc/cdx-query" "rad:z3a2PAttEcoz8cosuXQFCfXzMsXmo"
   "cc/warc-extract" "rad:z4C2P4voivgJXd6sZhtthMTEadGin"
   "clipboard/text" "rad:z3mV88V83Ddw6B56Ux5kqtTuJxcV1"
   "clock/monotonic" "rad:z25t4k7snnY6CkuSJY2JCK4SANe2L"
   "component/database" "rad:z3UXbiYV8xBquMFAmDMfKfb3v5Y47"
   "component/http" "rad:z4WBMDJYF4cfhdBCdnLmSLiFGCd21"
   "contacts/read" "rad:z3wUmFNv5MiZ1e8ySHo8xxKhFo6yP"
   "corpus/append" "rad:z3wnw7KeZCBN5yiT81RgMg5Xp7xD6"
   "corpus/publish" "rad:z4LrejM2AFx9u8hgdW3gn1P6VW9R7"
   "crypto/tls" "rad:z3Rg9Wmr1RiGapTg8NzVRCg46teq8"
   "data/cbor" "rad:z24o5QsGMa3pkGEuqP48XSMDEPdFN"
   "data/json" "rad:z3yzK8jyMmrUaYuzMDfFH1W5k8H1a"
   "dma/map" "rad:zR1KKTVy51jQ9vHzLw6RoCfYi43K"
   "fs/app-data" "rad:z51vNMpisTEAsj9pHU6KQuUwfQZ3"
   "gpu/clear" "rad:z32P2Gc2pvSXCqdGEiTS4c3PMwfbY"
   "gpu/draw-frame" "rad:z2SfzeSnaCWBgokoof7YPXfMMYnqx"
   "gpu/set-position" "rad:zPTSg8QrCRGVC8bpZgqiu8KhZTL1"
   "graph/kotoba" "rad:z3Myn6N1k9ssuG9gdBwJfj7k3zo35"
   "hash/sha256" "rad:z26YBHK3P3JhT5JJhcxwXuHXjVnZA"
   "http/fetch" "rad:z2GoJStxV5pz3hEHYorXA3fW9ZJgG"
   "http/post" "rad:z2t8b61Lztq4wWDGLasNN1Wzqrc76"
   "identity/keypair" "rad:z4XZ2gd94u19C8vxYrrfAEVsVUCNK"
   "identity/sign" "rad:z4WcsYktoZ1HwvCPgKjXFa1J3kpi7"
   "identity/verify" "rad:z3T5b7WcUZ7reoSTwGrd1nfJ6KagG"
   "irq/subscribe" "rad:z3CMYzKaFFvZEco2jkL7wQa9kRcU3"
   "kami/engine" "rad:z2izrSL6G3Mtj4oM9jbUfz3qgfLua"
   "keychain/text" "rad:z2hJdyKaRfjfPuVXtK19qHW4guAx3"
   "ledger/append" "rad:z2xVd1aZhDKsFNk7TBkN5Bf3nWmuQ"
   "llm/infer" "rad:z3gjHkV7jc464fianWuZ7NxAcaR4Y"
   "log/read" "rad:z4NFr1rR5c7VKNCNgCuQBHbNgj43w"
   "log/write" "rad:z4DiGJTFkrNmj8QfHWr4vV9G5EgRC"
   "math/cos" "rad:z39ZE3TXtSgEsHketwNHmAdn9AzrG"
   "math/sin" "rad:z3V5Cu6W19fQ8yBmDgxLc4dm5Toit"
   "mmio/map" "rad:z3Pfx2pcf59cizdozsEEY1m6a2si3"
   "motion/read" "rad:zhZe1g9AMiTBbL5MfqW59MnRfcps"
   "net/connect" "rad:z2WMw5nSRLyv8B8vBs4RMmec7WaB7"
   "net/transport" "rad:zmDR3paxbM73DcNvH2ssmZjidhDb"
   "notify/show" "rad:z3oKTdfzdH59rYbfjLmmVt2aUQiLR"
   "pci/config" "rad:z8G4LSaGWofnSDEWpSZXXzVJVmQe"
   "random/bytes" "rad:zN54zqoakxpYM5E91ubN137A1UPP"
   "render/galactic-frame" "rad:zqJghqCtnc6YMmnDFyvVDPVcbgUx"
   "secret/use-postgresql-cancel"
   "rad:z49wqJGXTgFrns2LwiH4xKUDz8Lao"
   "secret/use-scram-sha256" "rad:z2W5aBnfjesEAdvwEFfoWsUWn26rV"
   "time/now-days" "rad:zmLoczt7YwTvY6E9JspVRVdKjSdd"
   "topic/publish" "rad:zsxMV2Zs4ZgMJ5H5xd2Z62wwoxYA"
   "topic/subscribe" "rad:z4B6TCjQxVxktrSQwceuxWbfxZF4Y"
   "wifi/info" "rad:zsNYPBzNRdcNfMDrpmvuRnaxKiT5"})

(def capability-effects
  {"ledger/append" #{:storage-write :integrity-record}
   "fs/app-data" #{:storage-read :storage-write}
   "notify/show" #{:external-communication :user-attention}
   "clipboard/text" #{:device-read :device-write :personal-data}
   "http/fetch" #{:network-read}
   "keychain/text" #{:storage-read :storage-write :secret}
   "contacts/read" #{:personal-data :storage-read}
   "calendar/read" #{:personal-data :storage-read}
   "graph/kotoba" #{:storage-read :storage-write}
   "log/write" #{:storage-write}
   "clock/monotonic" #{:clock}
   "random/bytes" #{:randomness}
   "topic/publish" #{:ipc-write}
   "topic/subscribe" #{:ipc-read}
   "pci/config" #{:device-control}
   "dma/map" #{:device-control :memory-access}
   "irq/subscribe" #{:device-read}
   "mmio/map" #{:device-control :memory-access}
   "identity/keypair" #{:crypto :secret}
   "identity/sign" #{:crypto :secret}
   "identity/verify" #{:crypto}
   "hash/sha256" #{:crypto}
   "http/post" #{:network-write :data-egress}
   "log/read" #{:storage-read}
   "llm/infer" #{:llm-inference :network-write :data-egress}
   "gpu/clear" #{:device-write}
   "math/cos" #{:pure-compute}
   "math/sin" #{:pure-compute}
   "gpu/set-position" #{:device-write}
   "gpu/draw-frame" #{:device-write}
   "time/now-days" #{:clock}
   "render/galactic-frame" #{:user-interface-read}
   "kami/engine" #{:simulation-read :simulation-write :randomness}
   "motion/read" #{:sensor-read :personal-data}
   "audio/io" #{:sensor-read :device-write :personal-data}
   "ble/scan" #{:sensor-read :network-read :personal-data}
   "wifi/info" #{:network-read :personal-data}
   "net/connect" #{:network-write}
   "crypto/tls" #{:network-write :crypto :secret}
   "net/transport" #{:network-read :network-write :data-egress}
   "component/http" #{:network-read :network-write :data-egress}
   "component/database"
   #{:network-read :network-write :storage-read :storage-write :data-egress}
   "secret/use-scram-sha256" #{:crypto :secret}
   "secret/use-postgresql-cancel" #{:network-write :secret}
   "data/cbor" #{:codec}
   "data/json" #{:codec}
   "cc/cdx-query" #{:network-read}
   "cc/warc-extract" #{:network-read :storage-write}
   "corpus/append" #{:storage-write}
   "corpus/publish" #{:network-write :external-communication :data-egress}})

(def approval-required-capabilities
  #{"notify/show" "clipboard/text" "keychain/text" "contacts/read"
    "calendar/read" "identity/keypair" "identity/sign" "http/post"
    "llm/infer" "motion/read" "audio/io" "ble/scan" "wifi/info"
    "net/connect" "crypto/tls" "net/transport" "component/http"
    "component/database" "secret/use-scram-sha256"
    "secret/use-postgresql-cancel" "corpus/publish"})

(defn repository-name [capability-id]
  (str "capability-" (str/replace capability-id "/" "-")))

(defn namespace-symbol [capability-id]
  (symbol
   (str "kotoba.capability."
        (str/replace capability-id "/" "."))))

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

(defn default-policy
  ([effects]
   (if (seq (set/intersection effects #{:network-write :secret}))
     :approval-required
     :autonomous))
  ([capability-id _effects]
   (if (contains? approval-required-capabilities capability-id)
     :approval-required
     :autonomous)))

(defn- utf8-bytes [value]
  #?(:clj (.getBytes ^String value "UTF-8")
     :cljs (.encode (js/TextEncoder.) value)))

(defn- stable-name [value]
  (cond
    (keyword? value) (subs (str value) 1)
    (symbol? value) (str value)
    :else (str value)))

(defn- cid-link [cid]
  (let [raw (seq (mf/cid->bytes cid))]
    (cbor/tagged 42 #?(:clj (byte-array (cons 0 raw))
                       :cljs (js/Uint8Array.
                              (clj->js (vec (cons 0 raw))))))))

(defn definition-hash-contract-cid
  "Identity of the versioned hashing rules, separate from any capability."
  []
  (mf/cidv1-raw (utf8-bytes definition-hash-contract)))

(defn definition-block
  "Canonical semantic identity block for a capability.

  Human names, repository locations, Radicle RIDs and provider availability
  are deliberately absent. As in Kotoba's Unison-like codebase, those are
  mutable discovery aliases; checked import/effect/policy meaning is identity."
  [manifest]
  (let [artifact (:capability/artifact manifest)]
    {"schema" definition-schema
     "version" 1
     "abi" {"namespace" actor-capability/actor-host-namespace
            "version" actor-capability/actor-host-version}
     "imports" (mapv stable-name (sort (:capability/imports manifest)))
     "effects" (mapv stable-name (sort (:capability/effects manifest)))
     "defaultPolicy" (stable-name (:capability/default-policy manifest))
     "artifactFormat" (stable-name (:format artifact))
     "dependencies" []
     "hashContract" (cid-link (definition-hash-contract-cid))}))

(defn definition-cid
  "Unison-like identity of the canonical capability meaning."
  [manifest]
  (mf/cidv1-dag-cbor (cbor/encode (definition-block manifest))))

(defn- with-definition-identity [manifest]
  (assoc manifest
         :capability/hash-contract-cid (definition-hash-contract-cid)
         :capability/definition-cid (definition-cid manifest)))

(defn repository-manifest [capability-id]
  (let [imports (get (imports-by-capability) capability-id)
        effects (get (effects-by-capability) capability-id)]
    (when-not (seq imports)
      (throw (ex-info "unknown actor:host capability"
                      {:capability/id capability-id})))
    (with-definition-identity
      {:schema schema
       :authority authority
       :capability/id capability-id
       :capability/version 1
       :capability/repository
       (str "kotoba-lang/" (repository-name capability-id))
       :capability/radicle-rid (get radicle-rids capability-id)
       :capability/imports imports
       :capability/effects effects
       :capability/default-policy (default-policy effects)
       :capability/provider-status :contract-only
       :capability/dependencies #{}
       :capability/artifact
       {:format :wasm-component
        :digest-required? true
        :signature-required? true}})))

(defn runtime-imports-by-capability
  "Group a capability_contract.edn host surface by capability ID, retaining
  registered capabilities whose provider imports are not implemented yet."
  [runtime-contract]
  (let [empty-groups
        (into {} (map (fn [capability-id] [capability-id #{}]))
              (keys (:capability-ids runtime-contract)))]
    (reduce-kv
     (fn [out op {:keys [capability]}]
       (if capability
         (update out capability (fnil conj #{}) (keyword (name op)))
         out))
     empty-groups
     (:host-imports runtime-contract))))

(defn full-repository-manifest [runtime-contract capability-id]
  (let [imports (get (runtime-imports-by-capability runtime-contract)
                     capability-id ::unknown)
        effects (get capability-effects capability-id)]
    (when (= ::unknown imports)
      (throw (ex-info "unknown runtime capability"
                      {:capability/id capability-id})))
    (when-not effects
      (throw (ex-info "capability effects are not classified"
                      {:capability/id capability-id})))
    (with-definition-identity
      {:schema schema
       :authority authority
       :capability/id capability-id
       :capability/version 1
       :capability/repository
       (str "kotoba-lang/" (repository-name capability-id))
       :capability/radicle-rid (get radicle-rids capability-id)
       :capability/imports imports
       :capability/effects effects
       :capability/default-policy (default-policy capability-id effects)
       :capability/provider-status :contract-only
       :capability/dependencies #{}
       :capability/artifact
       {:format :wasm-component
        :digest-required? true
        :signature-required? true}})))

(defn full-catalog [runtime-contract]
  (->> (:capability-ids runtime-contract)
       keys
       sort
       (mapv #(full-repository-manifest runtime-contract %))))

(defn actor-host-catalog []
  (->> (keys (imports-by-capability))
       sort
       (mapv repository-manifest)))

(defn repository-refs-for-imports
  "Return the exact atomic repository identities required by IMPORTS."
  [imports]
  (let [requested (set imports)]
    (->> (actor-host-catalog)
         (filter #(seq (set/intersection requested
                                        (:capability/imports %))))
         (mapv #(select-keys %
                            [:capability/id
                             :capability/version
                             :capability/definition-cid
                             :capability/hash-contract-cid
                             :capability/repository
                             :capability/radicle-rid])))))

(defn resolve-definition-cid
  "Resolve immutable semantic identity to its current discovery aliases.
  Returns nil for an unknown CID; names never participate in the lookup key."
  ([definition-cid]
   (resolve-definition-cid (actor-host-catalog) definition-cid))
  ([catalog definition-cid]
   (some #(when (= definition-cid (:capability/definition-cid %)) %)
         catalog)))

(defn validate-envelope-repositories
  "Require an envelope to name exactly the repositories owning its imports."
  [envelope]
  (let [expected (set (repository-refs-for-imports
                       (:tamaki.capability/imports envelope)))
        actual (set (:tamaki.capability/repositories envelope))]
    (vec
     (concat
      (when-not (vector? (:tamaki.capability/repositories envelope))
        [{:problem :capability-repositories-vector-required}])
      (when-not (= expected actual)
        [{:problem :capability-repository-set-mismatch
          :missing (set/difference expected actual)
          :excess (set/difference actual expected)}])))))

(defn validate-manifest
  ([manifest] (validate-manifest nil manifest))
  ([runtime-contract manifest]
  (let [capability-id (:capability/id manifest)
        expected (when (string? capability-id)
                   (try
                     (if runtime-contract
                       (full-repository-manifest runtime-contract capability-id)
                       (repository-manifest capability-id))
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
      (when-not (= 1 (:capability/version manifest))
        [{:problem :unexpected-capability-version}])
      (when-not (= (definition-hash-contract-cid)
                   (:capability/hash-contract-cid manifest))
        [{:problem :capability-hash-contract-cid-mismatch
          :expected (definition-hash-contract-cid)}])
      (when-not (= (definition-cid manifest)
                   (:capability/definition-cid manifest))
        [{:problem :capability-definition-cid-mismatch
          :expected (definition-cid manifest)}])
      (when (and expected
                 (not= (:capability/repository expected)
                       (:capability/repository manifest)))
        [{:problem :repository-name-mismatch
          :expected (:capability/repository expected)}])
      (when (and expected
                 (not= (:capability/radicle-rid expected)
                       (:capability/radicle-rid manifest)))
        [{:problem :radicle-rid-mismatch
          :expected (:capability/radicle-rid expected)}])
      (when-not (and (string? (:capability/radicle-rid manifest))
                     (re-matches #"rad:z[1-9A-HJ-NP-Za-km-z]+"
                                 (:capability/radicle-rid manifest)))
        [{:problem :radicle-rid-required}])
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
      (when (and expected
                 (not= (:capability/default-policy expected)
                       (:capability/default-policy manifest)))
        [{:problem :default-policy-mismatch
          :expected (:capability/default-policy expected)}])
      (when-not (= :contract-only (:capability/provider-status manifest))
        [{:problem :provider-status-must-be-contract-only}])
      (when-not (empty? (:capability/dependencies manifest))
        [{:problem :capability-dependencies-forbidden}])
      (when-not (= :wasm-component (:format artifact))
        [{:problem :artifact-format-must-be-wasm-component}])
      (when-not (true? (:digest-required? artifact))
        [{:problem :artifact-digest-required}])
      (when-not (true? (:signature-required? artifact))
        [{:problem :artifact-signature-required}]))))))

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

(defn validate-full-catalog [runtime-contract catalog]
  (let [ids (map :capability/id catalog)
        repos (map :capability/repository catalog)
        imports (set (mapcat :capability/imports catalog))
        expected-ids (set (keys (:capability-ids runtime-contract)))
        expected-imports
        (set (map (comp keyword name)
                  (keep (fn [[op definition]]
                          (when (:capability definition) op))
                        (:host-imports runtime-contract))))]
    (vec
     (concat
      (mapcat #(validate-manifest runtime-contract %) catalog)
      (when-not (= expected-ids (set ids))
        [{:problem :capability-id-coverage
          :missing (set/difference expected-ids (set ids))
          :excess (set/difference (set ids) expected-ids)}])
      (when-not (= (count ids) (count (distinct ids)))
        [{:problem :duplicate-capability-id}])
      (when-not (= (count repos) (count (distinct repos)))
        [{:problem :duplicate-capability-repository}])
      (when-not (= expected-imports imports)
        [{:problem :runtime-import-coverage
          :missing (set/difference expected-imports imports)
          :excess (set/difference imports expected-imports)}])))))
