(ns cljsfiddle.effects
  "Global effects"
  (:require [rehook.dom :refer-macros [defui]]
            [rehook.core :as rehook]
            [cljs.core.async :as async :refer-macros [go]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [goog.events :as ev]
            [cljsfiddle.env.core :as env]
            [cljsfiddle.gist :as gist]
            [clojure.string :as str]
            [cljs.tools.reader.edn :as edn]
            ["highlight.js" :as hljs]
            ["marked" :as marked]
            [cljsfiddle.logging :as log]))

;; TODO: less imperative impl
#_(defn load-gist [{:keys [db] :as ctx} id]
  (go
   (swap! db assoc :loading? true)
   (let [result (async/<! (gist/load-gist id))]
     (if (:success? result)
       (do (async/<! (env/restart-env! ctx (-> db deref :version) result))
           (swap! db assoc :loading? false :source (:source result)))
       (swap! db assoc :error (:ex result) :loading? false)))))

#_(defn load-readme
  [{:keys [db compiler-state]}]
  ;; TODO: url pass in as variable
  (go (let [source (<p! (-> (js/fetch (str "/sandbox/" (:version @db) "/readme.cljs")) (.then #(.text %))))
            _      (async/<! (env/eval! compiler-state (-> db deref :version) source))]
        (swap! db assoc :source source))))

#_(defn nav-callback
  [ctx ev]
  (let [token (aget ev "token")
        [_ type id] (str/split token #"/")]
    (cond
      (= type "gist")
      (load-gist ctx id)

      (str/blank? token)
      (load-readme ctx)

      :else
      (js/console.log "Unknown token " token))))

#_(defui history [{:keys [history] :as ctx} _]
  (rehook/use-effect
   (fn []
     (let [ev (ev/listen history "navigate" (partial nav-callback ctx))]
       (.setEnabled history true)
       (fn []
         (.setEnabled history false)
         (ev/unlistenByKey ev))))
   []))

(defui manifest [{:keys [db]} _]
  (let [[version _] (rehook/use-atom-path db [:version])]
    (rehook/use-effect
     (fn []
       (-> (js/fetch (str "/sandbox/" version "/cljsfiddle.manifest.edn"))
           (.then #(.text %))
           (.then #(edn/read-string %))
           (.then #(swap! db assoc-in [:manifest version] %))
           (.catch #(js/console.log "Could not load manifest" %)))
       (constantly nil))
     [version])))

(defui highlight [_ _]
  (rehook/use-effect
   (fn []
     (hljs/initHighlightingOnLoad)
     (.setOptions marked #js {:highlight (fn [code lang]
                                           (aget (hljs/highlight lang code) "value"))})
     (constantly nil))
   []))

(defui logging [_ _]
  (rehook/use-effect
   (fn []
     (log/init!)
     (fn []
       (enable-console-print!)))
   []))

(defui bootstrap [{:keys [db compiler-state]} _]
  (let [[version _] (rehook/use-atom-path db [:version])]
    (rehook/use-effect
     (fn []
       (env/init compiler-state version)
       (constantly nil))
     [version])))