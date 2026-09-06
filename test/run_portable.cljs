#!/usr/bin/env nbb
;; The Node half of this repository's test suite: the package conformance
;; corpus and the signature golden vectors, run through the real validator on
;; this runtime.
;;
;; WHY A SECOND RUNTIME AT ALL. `kotoba.lang.package-contract` is `.cljc`, and
;; until 2026-09-06 its signature verification was `#?(:clj ...)`-only: under
;; `:cljs` it rejected EVERY signature, valid or forged, with "signature
;; verification not supported in this runtime". `clojure -M:test` was green
;; throughout and could not have been otherwise -- a `:clj` test never loads
;; the `:cljs` branch. This entry is the measurement that was missing, and the
;; reason a package lock can now be validated without a JVM.
;;
;; WHAT THIS REPLACES. `scripts/check-package-contract.bb` was a hand-written
;; SECOND copy of the validator, running on `bb` (retired by ADR-2607173000).
;; It had already drifted -- kotoba-lang#97 added the invalid-definition-cid
;; rule and the copy kept accepting the case. Nothing here re-implements the
;; validator: every verdict below comes from `contract/validate-case` or
;; `contract/signatures-error`, the same entry points the JVM suite calls.
;;
;;   nbb --classpath src:test:<deps> test/run_portable.cljs [root]
;;
;; exit 0 = every case reached the verdict the corpus says it should
;; exit 1 = a case disagreed (the report names which, and how)
;; exit 2 = the corpus could not be read -- distinct from a pass ON PURPOSE

(ns run-portable
  (:require ["node:fs" :as fs]
            ["node:path" :as path]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [kotoba.lang.package-contract :as contract]))

(def ^:private root (or (second *command-line-args*) "."))

(defn- refuse! [message data]
  (.error js/console (str "REFUSED: " message " " (pr-str data)))
  (.error js/console
          "Refusing to report a pass on a corpus this run could not read.")
  (.exit js/process 2))

(defn- read-edn [relative]
  (let [p (.join path root relative)]
    (when-not (.existsSync fs p)
      (refuse! "conformance file is missing" {:path relative}))
    (try
      (edn/read-string (.readFileSync fs p "utf8"))
      (catch :default e
        (refuse! "conformance file does not read as EDN"
                 {:path relative :error (.-message e)})))))

;; ── corpus 1: lang/package-conformance ───────────────────────────────────────

(defn- conformance-cases []
  (let [value (read-edn "lang/package-conformance/manifest.edn")
        entity (if (vector? value) (first value) value)
        cases (let [c (or (:cases entity)
                          (:kotoba.lang.package.conformance/cases entity))]
                (if (string? c) (edn/read-string c) c))]
    (when-not (= 1 (:kotoba.lang.package.conformance/version entity))
      (refuse! "package conformance version 1 required"
               {:value (:kotoba.lang.package.conformance/version entity)}))
    (when-not (vector? cases)
      (refuse! "package conformance cases vector required" {:value cases}))
    ;; An empty corpus is not a clean corpus.
    (when (empty? cases)
      (refuse! "package conformance corpus is empty" {:cases 0}))
    cases))

(defn- run-conformance-case [tc]
  (let [data (read-edn (str "lang/package-conformance/" (:file tc)))
        result (contract/validate-case tc data)
        label (name (:id tc))]
    (case (:kind tc)
      :accept
      (if (:valid? result)
        {:label label :ok true :accept? true :detail "accepted"}
        {:label label :ok false :accept? true
         :detail (str "expected accept, got: " (:message result))})

      :expect-error
      (cond
        (:valid? result)
        {:label label :ok false :accept? false
         :detail (str "expected rejection containing "
                      (pr-str (:error-contains tc)) ", got acceptance")}

        ;; Pin the REASON, not just the refusal. A case that rejects for an
        ;; unrelated reason has not exercised the rule it is named after.
        (not (str/includes? (or (:message result) "") (:error-contains tc)))
        {:label label :ok false :accept? false
         :detail (str "rejected for the wrong reason: expected "
                      (pr-str (:error-contains tc)) ", got "
                      (pr-str (:message result)))}

        :else
        {:label label :ok true :accept? false
         :detail (str "rejected: " (:message result))})

      {:label label :ok false :accept? false
       :detail (str "unknown case kind " (:kind tc))})))

;; ── corpus 2: signature golden vectors ───────────────────────────────────────

(def ^:private vector-keys
  [:manifest-cid :signer-did :other-did :valid-sig :tampered-sig
   :wrong-key-sig :other-message-sig :not-base64-sig])

(defn- signature-vectors []
  (let [v (read-edn "lang/package-conformance/signature-vectors.edn")]
    (when-not (= 1 (:kotoba.lang.package.signature-vectors/version v))
      (refuse! "signature vectors version 1 required"
               {:value (:kotoba.lang.package.signature-vectors/version v)}))
    (doseq [k vector-keys]
      (when-not (string? (get v k))
        (refuse! "signature vector missing" {:key k})))
    v))

(defn- signature-cases [v]
  (let [verdict (fn [sig did alg]
                  (contract/signatures-error
                   [{:did did :alg alg :sig sig}] (:manifest-cid v)))]
    (into
     [{:label "signature/a real signature verifies" :accept? true
       :run #(verdict (:valid-sig v) (:signer-did v) :ed25519)}]
     (conj
      (mapv (fn [[label sig did]]
              {:label (str "signature/" label) :accept? false
               :because "signature verification failed"
               :run #(verdict sig did :ed25519)})
            [["one flipped bit" (:tampered-sig v) (:signer-did v)]
             ["signed by another key" (:wrong-key-sig v) (:signer-did v)]
             ["valid, but over a different message"
              (:other-message-sig v) (:signer-did v)]
             ["a real signature attributed to another did"
              (:valid-sig v) (:other-did v)]
             ["not base64 at all" (:not-base64-sig v) (:signer-did v)]])
      ;; Control. Without it, "everything above was rejected" would also be
      ;; true of a runtime that rejects unconditionally -- which is exactly
      ;; the state this file exists to have caught.
      {:label "signature/an earlier check can still fire" :accept? false
       :because "signature alg unsupported"
       :run #(verdict (:valid-sig v) (:signer-did v) :rsa)}))))

(defn- run-signature-case [{:keys [label accept? because run]}]
  (let [r (run)]
    (cond
      accept?
      (if (nil? r)
        {:label label :ok true :accept? true :detail "accepted"}
        {:label label :ok false :accept? true
         :detail (str "expected accept, got: " (:message r))})

      (nil? r)
      {:label label :ok false :accept? false
       :detail "expected rejection, got acceptance"}

      (not= because (:message r))
      {:label label :ok false :accept? false
       :detail (str "rejected for the wrong reason: " (pr-str (:message r)))}

      :else
      {:label label :ok true :accept? false
       :detail (str "rejected: " (:message r))})))

;; ── report ───────────────────────────────────────────────────────────────────

(let [conformance (conformance-cases)
      v (signature-vectors)
      signatures (signature-cases v)
      results (into (mapv run-conformance-case conformance)
                    (mapv run-signature-case signatures))
      expected (+ (count conformance) (count signatures))
      accepts (count (filter :accept? results))
      rejects (- (count results) accepts)
      bad (filterv (complement :ok) results)]
  (doseq [r results]
    (println (if (:ok r) "ok  " "FAIL") (:label r) "--" (:detail r)))
  (println (str "SCANNED\t" (count results) "/" expected
                "\taccept=" accepts "\treject=" rejects))
  (when (not= (count results) expected)
    (refuse! "not every case ran" {:ran (count results) :expected expected}))
  ;; A suite that only ever accepts, or only ever rejects, has shown one
  ;; direction of a two-directional claim.
  (when (or (zero? accepts) (zero? rejects))
    (refuse! "suite does not exercise both directions"
             {:accept accepts :reject rejects}))
  (println (str "nbb: " (count results) " tests, " (- (count results) (count bad))
                " passed"))
  (when (seq bad)
    (println (str "FAILED\t" (count bad)))
    (.exit js/process 1)))
