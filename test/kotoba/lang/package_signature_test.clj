(ns kotoba.lang.package-signature-test
  "Package signature verification, on the JVM, against the SAME committed
  golden vectors `test/nbb/package-signature.cljs` runs on Node.

  The pair is the point. Signature verification used to be `:clj`-only and
  rejected everything under `:cljs`, and the JVM suite passing said nothing
  about that -- it could not, because it never loaded the other branch. Two
  runtimes over one committed corpus is what makes the claim 'this verifies
  signatures' checkable rather than host-local."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [kotoba.lang.package-contract :as contract]))

(def vectors
  (edn/read-string
   (slurp (io/file "lang/package-conformance/signature-vectors.edn"))))

(defn- verdict
  "The contract's verdict on one signature, through the public entry point."
  [sig did]
  (contract/signatures-error [{:did did :alg :ed25519 :sig sig}]
                             (:manifest-cid vectors)))

(deftest golden-vectors-are-present-and-complete
  ;; An absent or truncated corpus must not read as a pass: every assertion
  ;; below is `nil`-shaped on one side, so a missing vector would quietly
  ;; verify nothing.
  (is (= 1 (:kotoba.lang.package.signature-vectors/version vectors)))
  (doseq [k [:manifest-cid :signer-did :other-did :valid-sig :tampered-sig
             :wrong-key-sig :other-message-sig :not-base64-sig]]
    (is (string? (get vectors k)) (str "missing vector " k))))

(deftest a-real-signature-verifies
  (is (nil? (verdict (:valid-sig vectors) (:signer-did vectors)))))

(deftest forged-and-mismatched-signatures-are-rejected-for-that-reason
  (testing "each rejection names signature verification, not some earlier check"
    (doseq [[label sig did]
            [["one flipped bit" (:tampered-sig vectors) (:signer-did vectors)]
             ["signed by another key" (:wrong-key-sig vectors) (:signer-did vectors)]
             ["valid, but over a different message"
              (:other-message-sig vectors) (:signer-did vectors)]
             ["real signature, attributed to another did"
              (:valid-sig vectors) (:other-did vectors)]
             ["not base64 at all" (:not-base64-sig vectors) (:signer-did vectors)]]]
      (let [result (verdict sig did)]
        (is (false? (:valid? result)) label)
        ;; Pin the literal. A rejection that happens for a different reason --
        ;; a shape check firing first, say -- is not evidence this function
        ;; verified anything.
        (is (= "signature verification failed" (:message result)) label)))))

(deftest a-rejection-can-still-come-from-an-earlier-check
  (testing "so the assertion above is discriminating, not vacuous"
    (is (= "signature alg unsupported"
           (:message (contract/signatures-error
                      [{:did (:signer-did vectors) :alg :rsa
                        :sig (:valid-sig vectors)}]
                      (:manifest-cid vectors)))))))
