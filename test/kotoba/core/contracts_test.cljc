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
             fs-read fs-write host-i64-roundtrip]
           (contracts/host-import-order contract)))))

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
