(ns kotoba.core.contracts-test
  (:require [clojure.test :refer [deftest is run-tests]]
            [kotoba.core.contracts :as contracts]))

(deftest source-contract-loads-and-classifies
  (let [contract (contracts/source-contract)]
    (is (= [] (contracts/validate-source-contract contract)))
    (is (= :kotoba (contracts/source-kind contract "src/app.kotoba")))
    (is (= :cljc (contracts/source-kind contract "src/shared.cljc")))
    (is (nil? (contracts/source-kind contract "src/app.cljs")))
    (is (= :edn (contracts/source-kind contract "policy.edn")))
    (is (nil? (contracts/source-kind contract "README.md")))
    (is (= :kotoba (:kotoba.source/reader-target
                    (contracts/source-plan contract "src/shared.cljc"))))))

(deftest source-contract-rejects-drift
  (let [contract (contracts/source-contract)]
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo #"unsupported Kotoba reader target"
         (contracts/source-plan contract "src/shared.cljc" :unsupported)))
    (is (some #(= :duplicate-extensions (:problem %))
              (contracts/validate-source-contract
               (assoc-in contract [:source-kinds :duplicate]
                         {:extensions [".cljc"]
                          :reader-target :kotoba}))))
    (is (some #(= :invalid-reader-targets (:problem %))
              (contracts/validate-source-contract
               (assoc-in contract [:source-kinds :cljc :reader-targets]
                         [:clj :cljs :unknown]))))
    (is (some #(= :invalid-namespace-resolution-order (:problem %))
              (contracts/validate-source-contract
               (assoc-in contract [:namespace-resolution :kotoba]
                         [".unknown"]))))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo #"unsupported Kotoba source extension"
         (contracts/source-plan contract "README.md")))))

(deftest capability-contract-loads-and-validates
  (let [contract (contracts/capability-contract)]
    (is (= [] (contracts/validate-capability-contract contract)))
    (is (= 203 (contracts/capability-id contract :notify/show)))
    (is (= 204 (contracts/capability-id contract "clipboard/text")))
    (is (= "http_fetch" (get-in contract [:host-imports 'http-fetch :field])))
    (is (= '[has-capability? notify-show clipboard-read clipboard-write
             clipboard-write-str http-fetch keychain-read keychain-write
             fs-read fs-write host-i64-roundtrip
             kgraph-assert! kgraph-retract! kgraph-get-objects kgraph-query
             log-write clock-monotonic random-bytes topic-publish topic-poll
             topic-take topic-count pci-config dma-map irq-subscribe mmio-map
             gen-keypair sign verify sha256-hex http-post log-read llm-infer
             gpu-clear cos sin gpu-set-position gpu-draw-frame]
           (contracts/host-import-order contract)))))

(deftest kototama-actor-host-imports-registered
  ;; kotoba-lang/kototama's actor:host ABI (kototama.contract /
  ;; kototama.tender, ADR-2607062330/2607062400): 6 of its 8 imports are
  ;; net-new here -- log-write/clock-monotonic already existed (registered
  ;; independently for aiueos's kernel capabilities, ADR-2607022700, with
  ;; identical wire signatures -- kototama's own :now/:log-append! were
  ;; renamed to reuse them rather than duplicate the registration).
  (let [contract (contracts/capability-contract)]
    (is (= [] (contracts/validate-capability-contract contract)))
    (doseq [[cap id] {"identity/keypair" 219 "identity/sign" 220
                       "identity/verify" 221 "hash/sha256" 222
                       "http/post" 223 "log/read" 224}]
      (is (= id (contracts/capability-id contract cap)) cap))
    (doseq [[op [cap field]] {'gen-keypair ["identity/keypair" "gen_keypair"]
                              'sign ["identity/sign" "sign"]
                              'verify ["identity/verify" "verify"]
                              'sha256-hex ["hash/sha256" "sha256_hex"]
                              'http-post ["http/post" "http_post"]
                              'log-read ["log/read" "log_read"]}]
      (is (= cap (get-in contract [:host-imports op :capability])) op)
      (is (= field (get-in contract [:host-imports op :field])) op)
      (is (= "kotoba" (get-in contract [:host-imports op :module])) op))))

(deftest llm-infer-host-import-registered
  ;; kototama.tender's Anthropic Messages API call, registered net-new
  ;; here (not shared with any pre-existing kernel capability).
  (let [contract (contracts/capability-contract)]
    (is (= [] (contracts/validate-capability-contract contract)))
    (is (= 225 (contracts/capability-id contract "llm/infer")))
    (is (= "llm/infer" (get-in contract [:host-imports 'llm-infer :capability])))
    (is (= "llm_infer" (get-in contract [:host-imports 'llm-infer :field])))
    (is (= "kotoba" (get-in contract [:host-imports 'llm-infer :module])))
    (is (= [:i32 :i32 :i32 :i32] (get-in contract [:host-imports 'llm-infer :params])))))

(deftest gpu-clear-host-import-registered
  ;; ADR-2607078000 Track B Phase 0 spike: the first kami:engine-independent
  ;; GPU host-import, registered net-new here (browser-only in practice --
  ;; no JVM WebGPU implementation exists or is planned; kotoba-core-contracts
  ;; itself stays host-neutral, same as every other entry in this table).
  (let [contract (contracts/capability-contract)]
    (is (= [] (contracts/validate-capability-contract contract)))
    (is (= 226 (contracts/capability-id contract "gpu/clear")))
    (is (= "gpu/clear" (get-in contract [:host-imports 'gpu-clear :capability])))
    (is (= "gpu_clear" (get-in contract [:host-imports 'gpu-clear :field])))
    (is (= "kotoba" (get-in contract [:host-imports 'gpu-clear :module])))
    (is (= [:i32] (get-in contract [:host-imports 'gpu-clear :params])))
    (is (= :i32 (get-in contract [:host-imports 'gpu-clear :result])))))

(deftest aiueos-kernel-cap-host-imports-registered
  ;; aiueos's 9 default kernel capabilities (aiueos.policy/default-kernel-caps
  ;; in aiueos-cljc-contract), registered per ADR-2607022700 so a `.kotoba`
  ;; guest component can `cap-acquire` them. topic/subscribe backs three ops
  ;; (poll/take/count), matching the retired Rust surface.rs registry.
  (let [contract (contracts/capability-contract)]
    (is (= [] (contracts/validate-capability-contract contract)))
    (doseq [[cap id] {"log/write" 210 "clock/monotonic" 211 "random/bytes" 212
                       "topic/publish" 213 "topic/subscribe" 214 "pci/config" 215
                       "dma/map" 216 "irq/subscribe" 217 "mmio/map" 218}]
      (is (= id (contracts/capability-id contract cap)) cap))
    (doseq [[op cap] {'log-write "log/write" 'clock-monotonic "clock/monotonic"
                       'random-bytes "random/bytes" 'topic-publish "topic/publish"
                       'topic-poll "topic/subscribe" 'topic-take "topic/subscribe"
                       'topic-count "topic/subscribe" 'pci-config "pci/config"
                       'dma-map "dma/map" 'irq-subscribe "irq/subscribe"
                       'mmio-map "mmio/map"}]
      (is (= cap (get-in contract [:host-imports op :capability])) op)
      (is (= "kotoba" (get-in contract [:host-imports op :module])) op))))

(deftest capability-contract-rejects-drift
  (let [contract (contracts/capability-contract)]
    (is (some #(= :duplicate-capability-ids (:problem %))
              (contracts/validate-capability-contract
               (assoc-in contract [:capability-ids "duplicate/cap"]
                         (get-in contract [:capability-ids "notify/show"])))))
    (is (some #(= :host-import-order-must-cover-imports (:problem %))
              (contracts/validate-capability-contract
               (update contract :host-import-order pop))))
    (is (some #(= :host-import-unknown-capability (:problem %))
              (contracts/validate-capability-contract
               (assoc-in contract [:host-imports 'notify-show :capability]
                         "unknown/capability"))))
    (is (some #(= :invalid-host-import-shape (:problem %))
              (contracts/validate-capability-contract
               (assoc-in contract [:host-imports 'notify-show :params]
                         '(:i32)))))
    (is (some #(= :invalid-special-forms (:problem %))
              (contracts/validate-capability-contract
               (assoc contract :special-forms [:do :let]))))))

(deftest all-contracts-validate
  (is (= [] (contracts/validate-all))))

(defn -main [& _]
  (let [result (run-tests 'kotoba.core.contracts-test)]
    (when (pos? (+ (:fail result) (:error result)))
      (System/exit 1))))
