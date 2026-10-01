;; Differential: the Kotoba reading of kotoba.lang.package-contract (the
;; Form-based validators at the end of src/kotoba/lang/package_contract.cljk) on
;; the KIR interpreter, linked as a project, against the host reading (nbb).
;;
;; Cases: every entry of lang/package-conformance/manifest.edn (accepted and
;; expected-error fixtures), the manifest/lock mutants below, the manifest cid
;; (`compute-manifest-cid`), the tree and component content checks, and a
;; forged signature. For each, the Kotoba verdict string ("" when valid, else
;; the message, then "|" and the reason / missing field when there is one) is
;; compared with the host's. A FAIL line is a difference; the exit code is not set.
;;
;;   KROOTS=<source roots incl. kotoba-lang/lang/compat, colon separated> \
;;   node --stack-size=8192 nbb/cli.js --classpath <amu classpath> \
;;     scripts/verify_package_contract_twin.cljs
;;
;; CONFORMANCE=<dir> is lang/package-conformance (default: relative to the cwd).
;; ONLY=<regex> restricts the case ids; SKIP_SIG=1 skips the accepted manifests
;; (the cases that reach the Ed25519 verification; the interpreter takes minutes
;; for one). A manifest refused before the signature is still compared.
(ns verify-package-contract-twin
  (:require ["node:fs" :as fs]
            ["node:crypto" :as crypto]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [kotoba.sema :as sema]
            [kotoba.kir :as kir]
            [kotoba.compiler.project :as project]
            [kotoba.compiler.project-files :as pf]
            [kotoba.lang.package-contract :as c]
            [multiformats.core :as mf]
            [kotoba.bytes :as kbytes]))

(defn pr-flat
  "EDN text without #:ns{} map syntax, which kotoba.form's reader does not read."
  [x] (binding [*print-namespace-maps* false] (pr-str x)))

(def env (fn [k d] (or (aget js/process.env k) d)))
(def roots (vec (str/split (env "KROOTS" "") #":")))
(def only (re-pattern (env "ONLY" ".")))
(def skip-sig? (= "1" (env "SKIP_SIG" "")))
(def probe-root "/private/tmp/scratch/pc_probe.cljk")

(def form-schema
  "(:schemas {:form/r [:record :form/r [[:tag :i64] [:s :string] [:n :i64] [:k :keyword] [:kids [:list [:ref :form/r]]] [:span :i64] [:data :bytes]]]})")

(def probe-src
  (str "(ns probe.pc\n  {:kotoba/export [main case-msg manifest-cid integrity-msg tree-msg lock-bytes-msg component-msg]}\n"
       "  (:require [kotoba.lang.package-contract :as c] [kotoba.form :as form] [kotoba.bytes :as kbytes])\n  " form-schema ")\n"
       "(defn- kw [k :keyword] [:ref :form/r] (form/keyword-form k))\n"
       "(defn- field [m [:ref :form/r] k :keyword] [:ref :form/r] (form/form-get m (kw k)))\n"
       ;; the verdict of an error value: message, then | and the reason / missing field
       "(defn- verdict [e [:ref :form/r]] :string
          (if (form/is-nil? e) \"\"
            (let [d (field e :data)
                  extra (cond (form/form-has? d (kw :reason)) (str \"|\" (document-edn-print (document-keyword (form/keyword-value (field d :reason)))))
                              (form/form-has? d (kw :missing)) (str \"|\" (document-edn-print (document-keyword (form/keyword-value (field d :missing)))))
                              :else \"\")]
              (str (form/string-value (field e :message)) extra))))\n"
       "(defn case-msg [kind :string tc :string data :string] :string
          (let [r (c/validate-case (form/edn-form tc) (form/edn-form data))]
            (if (form/is-true? (field r :valid?)) \"\" (verdict r))))\n"
       "(defn manifest-cid [data :string] :string (c/compute-manifest-cid (form/edn-form data)))\n"
       "(defn integrity-msg [data :string] :string (verdict (c/manifest-integrity-error (form/edn-form data))))\n"
       "(defn tree-msg [declared :string content :string] :string
          (verdict (c/tree-cid-error (form/string-form declared)
                                     (option-some-of [:option :vector-i64] (kbytes/utf8-encode content)))))\n"
       ;; a lock with per-dependency tree / component bytes (the text of the content, under the dep name)
       "(defn lock-bytes-msg [lock :string tc :string dep :string tree :string component :string] :string
          (let [opts (form/form-map
                       (typed-list-new [:list [:ref :form/r]]
                         (kw :tree-bytes-by-dep) (form/form-map (form/two-forms (form/string-form dep) (form/bytes-form (kbytes/utf8-bytes tree))))
                         (kw :component-bytes-by-dep) (form/form-map (form/two-forms (form/string-form dep) (form/bytes-form (kbytes/utf8-bytes component))))))]
            (verdict (c/lockfile-error (form/edn-form lock) (form/edn-form tc) opts))))\n"
       "(defn component-msg [declared :string content :string] :string
          (verdict (c/component-cid-error (form/string-form declared)
                                          (option-some-of [:option :vector-i64] (kbytes/utf8-encode content)))))\n"
       "(defn main [] :string \"ok\")\n"))

(.mkdirSync fs "/private/tmp/scratch" #js {:recursive true})
(.writeFileSync fs probe-root probe-src)
(def kir-mod
  (let [graph (pf/load-closed-graph probe-root roots)
        linked (project/link-source (:sources graph) (:root graph))
        hir (sema/analyze (:source linked) {:admit-linked-synthetics? true})]
    (kir/lower hir)))
(println "probe compiled")

(defn twin [fname & args]
  (try (kir/execute kir-mod (symbol fname) (vec args)
                    {:fuel 3000000000 :frames 200000 :cells 2000000000 :bytes 2000000000
                     :typed-cap-call (fn [cap-id _ _ request]
                                       (if (= 3 (js/Number cap-id))
                                         (-> (crypto/createHash "sha256") (.update request "utf8") (.digest "hex"))
                                         (throw (ex-info "no capability" {:cap cap-id}))))})
       (catch :default e (str "TRAP:" (ex-message e)))))

;; ---- the host verdicts, in the same shape ---------------------------------------

(defn verdict [e]
  (if (nil? e) ""
      (let [d (:data e)
            extra (cond (contains? d :reason) (str "|" (str (:reason d)))
                        (contains? d :missing) (str "|" (str (:missing d)))
                        :else "")]
        (str (:message e) extra))))

(def fails (atom 0))
(def total (atom 0))
(defn check [id want got]
  (swap! total inc)
  (when-not (= want got) (swap! fails inc))
  (println (if (= want got) "ok  " "FAIL") id (when-not (= want got) (pr-flat {:host want :kotoba got}))))

(defn read-file [p] (.readFileSync fs p "utf8"))
(def base (env "CONFORMANCE" "lang/package-conformance"))
(defn fixture [p] (read-file (str base "/" p)))

;; ---- 1. the conformance fixtures -----------------------------------------------------
(let [outer (edn/read-string (read-file (str base "/manifest.edn")))
      cases (edn/read-string (:kotoba.lang.package.conformance/cases (first outer)))]
  (doseq [tc cases
          :when (re-find only (str (:id tc)))
          :when (not (and skip-sig? (= :package-manifest (:type tc)) (= :accept (:kind tc))))]
    (let [text (fixture (:file tc))
          data (edn/read-string text)
          host (let [r (c/validate-case tc data)] (if (:valid? r) "" (verdict r)))]
      (check (:id tc) host (twin "case-msg" (name (:type tc)) (pr-flat tc) text)))))

;; ---- 2. mutants of the positive lock ---------------------------------------------------
(def lock-text (fixture "positive/kotoba.lock.edn"))
(def lock (edn/read-string lock-text))
(def dep0 (first (:deps lock)))
(def tc0 {:id :x :type :lockfile :declared-capabilities [:graph-read]})

(defn lock-case [id l tc]
  (when (re-find only id)
    (check id (let [r (c/lockfile-error l tc)] (verdict r)) (twin "case-msg" "lockfile" (pr-flat tc) (pr-flat l)))))

(doseq [[id l] {"lock-version-2" (assoc lock :kotoba.lock/version 2)
                "lock-version-missing" (dissoc lock :kotoba.lock/version)
                "lock-deps-map" (assoc lock :deps {})
                "lock-no-name" (update lock :deps (fn [d] [(dissoc (first d) :dep/name)]))
                "lock-no-signers-key" (update lock :deps (fn [d] [(dissoc (first d) :dep/signers)]))
                "lock-empty-signers" (update lock :deps (fn [d] [(assoc (first d) :dep/signers [])]))
                "lock-kind-bad" (update lock :deps (fn [d] [(assoc (first d) :dep/kind :nope)]))
                "lock-kind-tool" (update lock :deps (fn [d] [(assoc (first d) :dep/kind :tool)]))
                "lock-bad-repo-rid" (update lock :deps (fn [d] [(assoc (first d) :dep/repo-rid "bafy")]))
                "lock-bad-tree-cid" (update lock :deps (fn [d] [(assoc (first d) :dep/tree-cid 7)]))
                "lock-defcids-dup" (update lock :deps (fn [d] [(assoc (first d) :dep/definition-cids [(:dep/tree-cid dep0) (:dep/tree-cid dep0)])]))
                "lock-defcids-ok" (update lock :deps (fn [d] [(assoc (first d) :dep/definition-cids [(:dep/tree-cid dep0) (:dep/manifest-cid dep0)])]))
                "lock-defcids-str" (update lock :deps (fn [d] [(assoc (first d) :dep/definition-cids "x")]))
                "lock-defcids-notcid" (update lock :deps (fn [d] [(assoc (first d) :dep/definition-cids [(:dep/tree-cid dep0) "nope"])]))
                "lock-provides-bad" (update lock :deps (fn [d] [(assoc (first d) :dep/provides [:other.thing])]))
                "lock-provides-ok" (update lock :deps (fn [d] [(assoc (first d) :dep/provides [:app.kotoba.thing/v1])]))
                "lock-provides-str" (update lock :deps (fn [d] [(assoc (first d) :dep/consumes "x")]))
                "lock-caps-superset" (update lock :deps (fn [d] [(assoc (first d) :dep/capabilities [:graph-read :net])]))
                "lock-two-deps" (update lock :deps (fn [d] [(first d) (assoc (first d) :dep/signers [])]))}]
  (lock-case id l tc0))

(lock-case "lock-trust-revoked" lock (assoc tc0 :revoked-signers (vec (:dep/signers dep0))))
(lock-case "lock-trust-expired" lock (assoc tc0 :expired-signers (vec (:dep/signers dep0))))
(lock-case "lock-trust-compromised" lock (assoc tc0 :compromised-signers (vec (:dep/signers dep0))))
(lock-case "lock-trust-other" lock (assoc tc0 :revoked-signers ["did:key:z6Mkother"]))
(lock-case "lock-trust-none-declared" lock {:id :x :type :lockfile})

;; ---- 3. mutants of the positive manifest (before the signature is reached) ------------------
(def man-text (fixture "positive/package-manifest.edn"))
(def man (edn/read-string man-text))

(defn man-case [id m]
  (when (re-find only id)
    (check id (verdict (c/package-manifest-error m)) (twin "case-msg" "package-manifest" (pr-flat {:id :x :type :package-manifest}) (pr-flat m)))))

(doseq [[id m] {"man-no-name" (dissoc man :kotoba.package/name)
                "man-no-caps" (dissoc man :kotoba.package/capabilities)
                "man-kind-bad" (assoc man :kotoba.package/kind :nope)
                "man-adapter-empty" (assoc man :kotoba.package/kind :adapter :kotoba.package/consumes [])
                "man-adapter-none" (assoc man :kotoba.package/kind :adapter)
                "man-schema-empty" (assoc man :kotoba.package/kind :schema-contract :kotoba.package/provides [])
                "man-provides-notvec" (assoc man :kotoba.package/provides :app.kotoba.x)
                "man-provides-bad" (assoc man :kotoba.package/provides [:x/y])
                "man-bad-repo" (assoc man :kotoba.package/repo-rid "nope")
                "man-source-nil" (dissoc man :kotoba.package/source)
                "man-source-no-commit" (update man :kotoba.package/source dissoc :git-commit)
                "man-tree-bad" (assoc-in man [:kotoba.package/source :tree-cid] "bnope")
                "man-manifest-cid-bad" (assoc-in man [:kotoba.package/source :manifest-cid] 3)
                "man-caps-notvec" (assoc man :kotoba.package/capabilities #{:a})
                "man-sigs-empty" (assoc man :kotoba.package/signatures [])
                "man-sigs-nil" (assoc man :kotoba.package/signatures nil)
                "man-sig-no-did" (update man :kotoba.package/signatures (fn [s] [(dissoc (first s) :did)]))
                "man-sig-alg" (update man :kotoba.package/signatures (fn [s] [(assoc (first s) :alg :rsa)]))
                "man-sig-blank-did" (update man :kotoba.package/signatures (fn [s] [(assoc (first s) :did "  ")]))
                "man-sig-blank-sig" (update man :kotoba.package/signatures (fn [s] [(assoc (first s) :sig "")]))}]
  (man-case id m))

;; ---- 4. cids and content -------------------------------------------------------------------------
(when (re-find only "manifest-cid")
  (check "manifest-cid-positive" (c/compute-manifest-cid man) (twin "manifest-cid" man-text))
  (check "manifest-cid-ignores-signatures" (c/compute-manifest-cid (dissoc man :kotoba.package/signatures))
         (twin "manifest-cid" (pr-flat (dissoc man :kotoba.package/signatures))))
  (let [m2 (assoc man :kotoba.package/version "9.9.9")]
    (check "manifest-cid-changed" (c/compute-manifest-cid m2) (twin "manifest-cid" (pr-flat m2)))))

(when (re-find only "integrity")
  (check "integrity-positive" (verdict (c/manifest-integrity-error man)) (twin "integrity-msg" man-text))
  (let [m2 (assoc man :kotoba.package/version "9.9.9")]
    (check "integrity-tampered" (verdict (c/manifest-integrity-error m2)) (twin "integrity-msg" (pr-flat m2))))
  (let [m3 (assoc-in man [:kotoba.package/source :manifest-cid] "nope")]
    (check "integrity-undeclared" (verdict (c/manifest-integrity-error m3)) (twin "integrity-msg" (pr-flat m3)))))

(when (re-find only "tree")
  (let [content "tree-bytes-v1"
        cid (mf/cidv1-raw (kbytes/utf8-bytes content))]
    (check "tree-match" (verdict (c/tree-cid-error cid (kbytes/utf8-bytes content))) (twin "tree-msg" cid content))
    (check "tree-mismatch" (verdict (c/tree-cid-error cid (kbytes/utf8-bytes "other"))) (twin "tree-msg" cid "other"))
    (check "tree-bad-declared" (verdict (c/tree-cid-error "nope" (kbytes/utf8-bytes content))) (twin "tree-msg" "nope" content))
    (check "component-match" (verdict (c/component-cid-error cid (kbytes/utf8-bytes content))) (twin "component-msg" cid content))
    (check "component-mismatch" (verdict (c/component-cid-error cid (kbytes/utf8-bytes "other"))) (twin "component-msg" cid "other"))))

(when (re-find only "lock-bytes")
  (let [clock-text (fixture "positive/component-lock.edn")
        clock (edn/read-string clock-text)
        dep (first (:deps clock))
        content "component-bytes-v1"
        tc {:declared-capabilities []}
        host (fn [tree comp]
               (verdict (c/lockfile-error clock tc {:tree-bytes-by-dep {(:dep/name dep) (kbytes/utf8-bytes tree)}
                                                    :component-bytes-by-dep {(:dep/name dep) (kbytes/utf8-bytes comp)}})))]
    (check "lock-bytes-component-ok" (host "x" content)
           (twin "lock-bytes-msg" clock-text (pr-flat tc) (:dep/name dep) "x" content))
    (check "lock-bytes-component-other" (host "x" "other")
           (twin "lock-bytes-msg" clock-text (pr-flat tc) (:dep/name dep) "x" "other"))))

(println (str "cases " @total " failures " @fails))
