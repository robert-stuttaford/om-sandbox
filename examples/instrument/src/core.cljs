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
  (atom {:ui [{:checked false :label "Foo" :count 0}
              {:checked false :label "Bar" :count 0}
              {:checked false :label "Baz" :count 0}]}))

;; =============================================================================
;; Application

(defn radio-button [data owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div.radio
        [:label
         [:input {:type "checkbox"
                  :checked (:checked data)
                  :onChange (fn [e]
                              (om/transact! data :checked not)
                              (om/transact! data :count inc))}]
         (:label data)]]))))

(defn all-buttons [data owner]
  (reify
    om/IRender
    (render [_]
      (apply dom/div nil
        (om/build-all radio-button (:ui data))))))

;; =============================================================================
;; Init

(om/root all-buttons app-state
  {:target (.getElementById js/document "ex0")})

(om/root all-buttons app-state
  {:target (.getElementById js/document "ex1")
   :instrument
   (fn [f cursor m]
     (if (= f radio-button)
       (om/build* editor/editor (om/graft [f cursor m] cursor))
       ::om/pass))})
