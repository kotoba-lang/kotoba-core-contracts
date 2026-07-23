(ns kotoba.core.contracts-test
  (:require [clojure.test :refer [deftest is run-tests testing]]
            [kotoba.core.contracts :as contracts]))

(deftest source-contract-loads-and-classifies
  (let [contract (contracts/source-contract)]
    (is (= [] (contracts/validate-source-contract contract)))
    (is (= :kotoba (contracts/source-kind contract "src/app.kotoba")))
    (is (= :cljc (contracts/source-kind contract "src/shared.cljc")))
    (is (= :cljk (contracts/source-kind contract "src/cell.cljk")))
    (is (= :cljs (contracts/source-kind contract "src/app.cljs")))
    (is (= :edn (contracts/source-kind contract "policy.edn")))
    (is (nil? (contracts/source-kind contract "README.md")))
    (is (= :kotoba (:kotoba.source/reader-target
                    (contracts/source-plan contract "src/shared.cljc"))))
    (is (= [:kotoba] (get-in contract [:source-kinds :kotoba :reader-targets])))
    (is (= :cljs (:kotoba.source/reader-target
                  (contracts/source-plan contract "src/app.cljs"))))
    (is (= :kotoba (:kotoba.source/reader-target
                 (contracts/source-plan contract "src/cell.cljk"))))))

(deftest source-contract-rejects-drift
  (let [contract (contracts/source-contract)]
    (is (thrown-with-msg?
         #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) #"unsupported Kotoba reader target"
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
         #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) #"unsupported Kotoba source extension"
         (contracts/source-plan contract "README.md")))))

(deftest source-contract-rejects-further-drift
  (testing "validate-source-contract's remaining branches -- previously
            untested despite source-contract-rejects-drift above already
            covering four of the seven. Each mutates one real, valid
            contract just enough to trip exactly one problem, confirmed via
            direct REPL calls before writing these assertions."
    (let [contract (contracts/source-contract)]
      (is (some #(= :missing-source-kinds (:problem %))
                (contracts/validate-source-contract (dissoc contract :source-kinds))))
      (is (some #(= :invalid-source-extensions (:problem %))
                (contracts/validate-source-contract
                 (assoc-in contract [:source-kinds :bad] {:extensions [] :reader-target :kotoba}))))
      (is (some #(= :default-reader-target-not-source-kind (:problem %))
                (contracts/validate-source-contract (assoc contract :default-reader-target :nope))))
      (is (some #(= :missing-namespace-resolution (:problem %))
                (contracts/validate-source-contract (dissoc contract :namespace-resolution))))
      (is (some #(= :namespace-resolution-unknown-target (:problem %))
                (contracts/validate-source-contract
                 (assoc-in contract [:namespace-resolution :bogus-target] [".kotoba"]))))
      (is (some #(= :unexpected-schema (:problem %))
                (contracts/validate-source-contract (assoc contract :schema "bogus"))))
      (is (some #(= :unexpected-authority (:problem %))
                (contracts/validate-source-contract (assoc contract :authority "bogus")))))))

(deftest capability-contract-loads-and-validates
  (let [contract (contracts/capability-contract)]
    (is (= [] (contracts/validate-capability-contract contract)))
    (is (= 203 (contracts/capability-id contract :notify/show)))
    (is (= 204 (contracts/capability-id contract "clipboard/text")))
    (is (= "http_fetch" (get-in contract [:host-imports 'http-fetch :field])))
    (is (= '[has-capability? notify-show clipboard-read clipboard-write
             clipboard-write-str http-fetch
             transport-connect tls-open tls-server-end-point
             transport-write transport-read transport-close
             http-open http-write http-read http-close http-get
             db-open db-write db-read db-close db-exchange pg-simple-query pg-open pg-query pg-query-state
             pg-prepare pg-prepare-typed pg-execute-params2 pg-execute-params
             pg-bind-portal pg-fetch-portal pg-close-portal
             pg-copy-out pg-copy-in
             pg-execute-batch
             pg-session-reset
             pg-pool-open pg-pool-acquire pg-pool-query pg-pool-release
             pg-pool-stats pg-pool-health pg-pool-drain
             pg-pool-close
             pg-close-statement
             pg-open-scram pg-open-scram-random
             pg-open-scram-cancellable-random pg-cancel-authority-use
             pg-close-scram scram-sha256 pg-cancel-register pg-cancel
             keychain-read keychain-write
             fs-read fs-write fs-write-atomic host-i64-roundtrip
             kgraph-assert! kgraph-retract! kgraph-get-objects kgraph-query
             log-write clock-monotonic random-bytes topic-publish topic-poll
             topic-take topic-count pci-config dma-map irq-subscribe mmio-map
             gen-keypair sign verify sha256-hex http-post log-read llm-infer
             gpu-clear cos sin gpu-set-position gpu-draw-frame
             now-days galactic-frame?
             kami-tick-n kami-spawn kami-despawn
             kami-set-position! kami-set-velocity! kami-get-x kami-get-y
             kami-set-position3! kami-set-velocity3! kami-get-z
             kami-count-tagged kami-nearest-tagged
             kami-move-tagged-toward! kami-despawn-within!
             kami-axis kami-rand
             motion-read audio-play audio-record ble-scan wifi-info
             cbor-encode json-encode json-extract-field]
           (contracts/host-import-order contract)))))

(deftest kami-engine-imports-registered
  ;; kami-* game-engine ECS surface (one shared "kami/engine" capability,
  ;; like "graph/kotoba" covering the kgraph-* quartet): the kami:engine
  ;; vocabulary exposed through this contract's single (module "kotoba")
  ;; ABI so a `.kotoba` guest can drive real game logic -- host owns entity
  ;; state/integration/tick, guest computes (gpu-set-position precedent).
  (let [contract (contracts/capability-contract)]
    (is (= [] (contracts/validate-capability-contract contract)))
    (is (= 233 (contracts/capability-id contract "kami/engine")))
    (doseq [[op [field params result]]
            {'kami-tick-n ["kami_tick_n" [] :i32]
             'kami-spawn ["kami_spawn" [:i32 :i32] :i32]
             'kami-despawn ["kami_despawn" [:i32] :i32]
             'kami-set-position! ["kami_set_position" [:i32 :f32 :f32] :i32]
             'kami-set-velocity! ["kami_set_velocity" [:i32 :f32 :f32] :i32]
             'kami-get-x ["kami_get_x" [:i32] :f32]
             'kami-get-y ["kami_get_y" [:i32] :f32]
             'kami-set-position3! ["kami_set_position3" [:i32 :f32 :f32 :f32] :i32]
             'kami-set-velocity3! ["kami_set_velocity3" [:i32 :f32 :f32 :f32] :i32]
             'kami-get-z ["kami_get_z" [:i32] :f32]
             'kami-count-tagged ["kami_count_tagged" [:i32 :i32] :i32]
             'kami-nearest-tagged ["kami_nearest_tagged" [:i32 :i32 :f32 :f32 :f32] :i32]
             'kami-move-tagged-toward! ["kami_move_tagged_toward" [:i32 :i32 :f32 :f32 :f32] :i32]
             'kami-despawn-within! ["kami_despawn_within" [:i32 :i32 :f32 :f32 :f32] :i32]
             'kami-axis ["kami_axis" [:i32 :i32] :f32]
             'kami-rand ["kami_rand" [:i32] :i32]}]
      (let [import (get-in contract [:host-imports op])]
        (is (= "kotoba" (:module import)) (str op))
        (is (= "kami/engine" (:capability import)) (str op))
        (is (= field (:field import)) (str op))
        (is (= params (:params import)) (str op))
        (is (= result (:result import)) (str op))))))

(deftest sensing-capability-imports-registered
  ;; ADR-2607140600 Phase 3a device-capability bridge (iPhone sensing for
  ;; the indoor floorplan-lab): 4 independently-metered/gateable
  ;; capabilities registered through the same pipeline as kami/engine
  ;; above -- each op has its OWN capability id (unlike kami-*'s one
  ;; shared id), mirroring how kgraph-*/topic-* group multiple ops under
  ;; one shared id vs. how most other ops get their own.
  (let [contract (contracts/capability-contract)]
    (is (= [] (contracts/validate-capability-contract contract)))
    (doseq [[cap id] {"motion/read" 234 "audio/io" 235 "ble/scan" 236 "wifi/info" 237}]
      (is (= id (contracts/capability-id contract cap)) cap))
    (doseq [[op [field capability params result]]
            {'motion-read ["motion_read" "motion/read" [:i32 :i32] :i32]
             'audio-play ["audio_play" "audio/io" [:i32 :i32] :i32]
             'audio-record ["audio_record" "audio/io" [:i32 :i32 :i32] :i32]
             'ble-scan ["ble_scan" "ble/scan" [:i32 :i32 :i32] :i32]
             'wifi-info ["wifi_info" "wifi/info" [:i32 :i32] :i32]}]
      (let [import (get-in contract [:host-imports op])]
        (is (= "kotoba" (:module import)) (str op))
        (is (= capability (:capability import)) (str op))
        (is (= field (:field import)) (str op))
        (is (= params (:params import)) (str op))
        (is (= result (:result import)) (str op))))
    (is (every? (set (contracts/host-import-order contract))
                '[motion-read audio-play audio-record ble-scan wifi-info]))))

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

(deftest kototama-cbor-json-imports-registered
  ;; kotoba-lang/kototama's actor:host ABI, second wave
  ;; (com-junkawasaki/root ADR-2607230943): CBOR/JSON wire-format encoding
  ;; for a future news-collecting fleet actor. All 3 net-new here (the
  ;; wave's third capability, GET-only HTTP fetch, deliberately reuses the
  ;; pre-existing "http/fetch" id 205 / `http-fetch` import instead --
  ;; see that entry's own comment above `data/cbor` in the EDN resource).
  (let [contract (contracts/capability-contract)]
    (is (= [] (contracts/validate-capability-contract contract)))
    (doseq [[cap id] {"data/cbor" 245 "data/json" 246}]
      (is (= id (contracts/capability-id contract cap)) cap))
    (doseq [[op [cap field params]]
            {'cbor-encode ["data/cbor" "cbor_encode" [:i32 :i32 :i32 :i32]]
             'json-encode ["data/json" "json_encode" [:i32 :i32 :i32 :i32]]
             'json-extract-field ["data/json" "json_extract_field"
                                  [:i32 :i32 :i32 :i32 :i32 :i32]]}]
      (is (= cap (get-in contract [:host-imports op :capability])) op)
      (is (= field (get-in contract [:host-imports op :field])) op)
      (is (= "kotoba" (get-in contract [:host-imports op :module])) op)
      (is (= params (get-in contract [:host-imports op :params])) op)
      (is (= :i32 (get-in contract [:host-imports op :result])) op))))

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

(deftest cos-sin-gpu-set-position-gpu-draw-frame-host-imports-registered
  ;; ADR-2607078000 Track B Phase 1: real f32 host-imports (kotoba-lang/
  ;; kotoba's compiler gained native f32 param/result support for this)
  ;; for orbital math (cos/sin) and the render-guest loop
  ;; (gpu-set-position/gpu-draw-frame) -- only gpu-clear had its own
  ;; dedicated field-lookup test before this (unlike gpu-clear, these were
  ;; previously only checked via the single golden host-import-order list).
  (let [contract (contracts/capability-contract)]
    (is (= [] (contracts/validate-capability-contract contract)))
    (is (= 227 (contracts/capability-id contract "math/cos")))
    (is (= "math/cos" (get-in contract [:host-imports 'cos :capability])))
    (is (= "cos" (get-in contract [:host-imports 'cos :field])))
    (is (= "kotoba" (get-in contract [:host-imports 'cos :module])))
    (is (= [:f32] (get-in contract [:host-imports 'cos :params])))
    (is (= :f32 (get-in contract [:host-imports 'cos :result])))

    (is (= 228 (contracts/capability-id contract "math/sin")))
    (is (= "math/sin" (get-in contract [:host-imports 'sin :capability])))
    (is (= "sin" (get-in contract [:host-imports 'sin :field])))
    (is (= [:f32] (get-in contract [:host-imports 'sin :params])))
    (is (= :f32 (get-in contract [:host-imports 'sin :result])))

    (is (= 229 (contracts/capability-id contract "gpu/set-position")))
    (is (= "gpu/set-position" (get-in contract [:host-imports 'gpu-set-position :capability])))
    (is (= "gpu_set_position" (get-in contract [:host-imports 'gpu-set-position :field])))
    (is (= [:i32 :f32 :f32 :f32] (get-in contract [:host-imports 'gpu-set-position :params]))
        "body-id: i32, x/y/z: f32")
    (is (= :i32 (get-in contract [:host-imports 'gpu-set-position :result])))

    (is (= 230 (contracts/capability-id contract "gpu/draw-frame")))
    (is (= "gpu/draw-frame" (get-in contract [:host-imports 'gpu-draw-frame :capability])))
    (is (= "gpu_draw_frame" (get-in contract [:host-imports 'gpu-draw-frame :field])))
    (is (= [] (get-in contract [:host-imports 'gpu-draw-frame :params])) "no-arg -- draws every stored position")
    (is (= :i32 (get-in contract [:host-imports 'gpu-draw-frame :result])))))

(deftest now-days-and-galactic-frame-host-imports-registered
  ;; ADR-2607078000 Track B Phase 1 follow-up: animation (now-days) +
  ;; galactic-frame toggle (galactic-frame?). Both are host-owned state the
  ;; guest reads with a no-arg call -- no guest-side clock/loop and no new
  ;; wasm export beyond `main` needed for either.
  (let [contract (contracts/capability-contract)]
    (is (= [] (contracts/validate-capability-contract contract)))
    (is (= 231 (contracts/capability-id contract "time/now-days")))
    (is (= "time/now-days" (get-in contract [:host-imports 'now-days :capability])))
    (is (= "now_days" (get-in contract [:host-imports 'now-days :field])))
    (is (= "kotoba" (get-in contract [:host-imports 'now-days :module])))
    (is (= [] (get-in contract [:host-imports 'now-days :params])) "no-arg -- host owns the clock")
    (is (= :f32 (get-in contract [:host-imports 'now-days :result])))

    (is (= 232 (contracts/capability-id contract "render/galactic-frame")))
    (is (= "render/galactic-frame" (get-in contract [:host-imports 'galactic-frame? :capability])))
    (is (= "galactic_frame" (get-in contract [:host-imports 'galactic-frame? :field])))
    (is (= [] (get-in contract [:host-imports 'galactic-frame? :params])) "no-arg -- host owns the toggle")
    (is (= :i32 (get-in contract [:host-imports 'galactic-frame? :result]))
        "an i32 is directly usable as a wasm `if` condition, nonzero = true")))

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

(deftest capability-contract-rejects-further-drift
  (testing "validate-capability-contract's remaining branches -- previously
            untested despite capability-contract-rejects-drift above already
            covering five of the twelve. Each mutates one real, valid
            contract just enough to trip exactly one problem, confirmed via
            direct REPL calls before writing these assertions."
    (let [contract (contracts/capability-contract)]
      (is (some #(= :missing-capability-ids (:problem %))
                (contracts/validate-capability-contract (dissoc contract :capability-ids))))
      (is (some #(= :missing-host-imports (:problem %))
                (contracts/validate-capability-contract (dissoc contract :host-imports))))
      (is (some #(= :capability-id-keys-must-be-strings (:problem %))
                (contracts/validate-capability-contract
                 (assoc-in contract [:capability-ids :bogus-keyword] 999))))
      (is (some #(= :capability-id-values-must-be-positive-ints (:problem %))
                (contracts/validate-capability-contract
                 (assoc-in contract [:capability-ids "bad/cap"] -1))))
      (is (some #(= :invalid-host-import-order (:problem %))
                (contracts/validate-capability-contract
                 (assoc contract :host-import-order [1 2 3]))))
      (is (some #(= :duplicate-host-import-order (:problem %))
                (contracts/validate-capability-contract
                 (update contract :host-import-order #(conj % (first %))))))
      (is (some #(= :ordered-import-missing-definition (:problem %))
                (contracts/validate-capability-contract
                 (update contract :host-import-order conj 'totally-unbound-op)))))))

(deftest all-contracts-validate
  (is (= [] (contracts/validate-all))))

(defn -main [& _]
  (let [result (run-tests 'kotoba.core.contracts-test)]
    (when (pos? (+ (:fail result) (:error result)))
      (System/exit 1))))
