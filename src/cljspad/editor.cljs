(ns cljspad.editor
  (:require ["@codemirror/fold" :as fold]
            ["@codemirror/gutter" :refer [lineNumbers]]
            ["@codemirror/highlight" :as highlight]
            ["@codemirror/history" :refer [history historyKeymap]]
            ["@codemirror/state" :refer [EditorState]]
            ["@codemirror/view" :as view :refer [EditorView]]
            ["react" :as react]
            [goog.object :as obj]
            [cljspad.env :as env]
            [nextjournal.clojure-mode :as cm-clj]
            [nextjournal.clojure-mode.live-grammar :as live-grammar]
            [nextjournal.clojure-mode.extensions.eval-region :as eval-region]
            [rehook.core :as rehook]
            [rehook.dom :refer-macros [defui]]))

(goog-define live-reload? false)

(defn editor-value
  [editor-view]
  (when editor-view
    (str (obj/getValueByKeys editor-view "state" "doc"))))

(defn copy-to-clipboard
  [editor-view]
  (when-let [value (editor-value editor-view)]
    (try
      (let [elem (js/document.createElement "textarea")]
        (js/document.body.appendChild elem)
        (aset elem "value" value)
        (.select elem)
        (js/document.execCommand "copy")
        (js/document.body.removeChild elem)
        true)
      (catch :default e
        (prn e)
        false))))

(def theme
  {:$content           {:white-space "pre-wrap"
                        :padding     "10px 0"}
   :$$focused          {:outline "none"}
   :$line              {:padding     "0 9px"
                        :line-height "1.6"
                        :font-size   "14px"
                        :font-family "Hack, monospace"}
   :$matchingBracket   {:border-bottom "1px solid #008080"
                        :color         "inherit"}
   :$gutters           {:background "transparent"
                        :border     "none"}
   :$gutterElement     {:margin-left "5px"}
   ;; only show cursor when focused
   :$cursor            {:visibility "hidden"}
   "$$focused $cursor" {:visibility "visible"}})

(defn eval-callback [x]
  (prn (-> x :result :value)))

(defn eval-form
  [compiler-state editor-view]
  (env/eval-form compiler-state (editor-value editor-view) eval-callback))

(defn eval-top-level
  [compiler-state editor-view]
  (let [state  (obj/getValueByKeys editor-view "state")
        source (eval-region/top-level-string state)]
    (env/eval-form compiler-state source eval-callback)))

(defn eval-at-cursor
  [compiler-state editor-view]
  (let [state  (obj/getValueByKeys editor-view "state")
        source (eval-region/cursor-node-string state)]
    (env/eval-form compiler-state source eval-callback)))

(defn eval-keymap
  [compiler-state]
  [{:key "Mod-Enter"
    :run (partial eval-form compiler-state)}
   {:key   "Alt-Enter"
    :shift (partial eval-top-level compiler-state)
    :run   (partial eval-at-cursor compiler-state)}])

(defn extensions
  [compiler-state]
  ;; Adapted from https://github.com/nextjournal/clojure-mode/blob/master/demo/src/nextjournal/clojure_mode/demo.cljs
  #js[(.theme EditorView (clj->js theme))
      (history)
      highlight/defaultHighlightStyle
      (view/drawSelection)
      (lineNumbers)
      (fold/foldGutter)
      (.. EditorState -allowMultipleSelections (of true))
      (if live-reload?
        ;; use live-reloading grammar
        #js[(cm-clj/syntax live-grammar/parser)
            (.slice cm-clj/default-extensions 1)]
        cm-clj/default-extensions)
      (.of view/keymap cm-clj/complete-keymap)
      (.of view/keymap historyKeymap)
      (.of view/keymap (clj->js (eval-keymap compiler-state)))])

(defn editor-opts
  [compiler-state ^js ref source]
  {:state  (.create EditorState (clj->js {:doc        (str source)
                                          :extensions (extensions compiler-state)}))
   :parent (.-current ref)})

(defui editor
  [{:keys [db compiler-state editor-view]} {:keys [height]}]
  (let [ref (react/useRef)
        [source _] (rehook/use-atom-path db [:source])]

    (rehook/use-effect
     (fn []
       (let [editor (EditorView. (clj->js (editor-opts compiler-state ref source)))]
         ;; editor needs to be global so that auxiliary functionality like export/copy-to-clipboard work
         (reset! editor-view editor)
         (fn []
           (.destroy editor)
           (reset! editor-view nil))))
     [source])

    [:div {:style {:overflow "auto" :height height}
           :ref   ref}]))