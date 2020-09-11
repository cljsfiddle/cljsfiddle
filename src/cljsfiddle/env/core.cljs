(ns cljsfiddle.env.core
  (:require [cljs.core.async :as async :refer-macros [go]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [cljs.js :as cljs.js]
            [cljs.tools.reader :as reader]
            [cognitect.transit :as transit]
            [clojure.string :as str]))

(defn state []
  (cljs.js/empty-state))

(goog-define cache-root
  "cache/builds/app/dev/ana/")

(defn sym->classpath [sym]
  (-> sym
      name
      (str/replace "." "/")
      (str/replace "-" "_")))

(defn load-ns!
  ([compiler-state sym]
   (load-ns! compiler-state sym sym))

  ([compiler-state sym alias]
   (load-ns! compiler-state sym alias (constantly nil)))

  ([compiler-state sym alias cb]
   (let [path (str cache-root (sym->classpath sym)  ".cljs.cache.transit.json")]
     (-> (js/fetch path)
         (.then (fn [response]
                  (.text response)))
         (.then (fn [body]
                  (let [rdr   (transit/reader :json)
                        cache (transit/read rdr body)]
                    (cljs.js/load-analysis-cache! compiler-state alias cache)
                    (cb {:compiler-state compiler-state
                         :sym            sym
                         :alias          alias
                         :cache          cache}))))))))

(defn eval!
  [compiler-state form]
  (go (<p! (-> (js/Promise. (fn [respond _]
                              (try (cljs.js/eval compiler-state form {:eval cljs.js/js-eval} respond)
                                   (catch :default e
                                     (respond {:error e})))))))))

(defn init!
  [compiler-state]
  (go (<p! (load-ns! compiler-state 'cljs.core))
      (<p! (load-ns! compiler-state 'cljsfiddle))))

(defn restart-env!
  [{:keys [compiler-state]} {:keys [metadata source]}]
  (go (reset! compiler-state (deref (state)))
      (async/<! (init! compiler-state))
      (async/<! (eval! compiler-state (reader/read-string source)))))
