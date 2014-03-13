(ns examples.instrument.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.reader :as reader]
            [sablono.core :as html :refer-macros [html]]
            [examples.instrument.editor :as editor]))

(enable-console-print!)

;; =============================================================================
;; Declarations

(def app-state
  (atom {:children [{:open? false :label "Foo"
                     :children
                     [{:open? false :label "Foo"}
                      {:open? false :label "Bar"
                       :children
                       [{:open? false :label "Foo"}
                        {:open? false :label "Bar"}
                        {:open? false :label "Baz"}]}
                      {:open? false :label "Baz"}]}
                    {:open? false :label "Bar"}
                    {:open? false :label "Baz"}]}))

;; =============================================================================
;; Application

(declare branch)

(defn leaf [{:keys [children open? label] :as data} owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div.radio
        (if children
          [:label
           [:input {:type "checkbox"
                    :checked open?
                    :onChange #(om/transact! data :open? not)}]
           label]
          [:div {:style {:margin-left "27px"}} label])
        (when (and children open?)
          (om/build branch data))]))))

(defn branch [data owner]
  (reify
    om/IRender
    (render [_]
      (apply dom/div nil
        (om/build-all leaf (:children data))))))

;; =============================================================================
;; Init

(om/root branch app-state
  {:target (.getElementById js/document "ex0")})

(om/root branch app-state
  {:target (.getElementById js/document "ex1")
   :instrument
   (fn [f cursor m]
     (if (= f leaf)
       (om/build* editor/editor (om/graft [f cursor m] cursor))
       ::om/pass))})
