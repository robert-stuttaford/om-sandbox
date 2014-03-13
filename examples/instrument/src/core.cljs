(ns examples.instrument.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.reader :as reader]
            [sablono.core :as html :refer-macros [html]]
            [examples.instrument.editor :as editor]
            [cljs.core.async :refer [<! chan put! sliding-buffer]]))

(enable-console-print!)

;; =============================================================================
;; Declarations

(def app-state
  (atom {:children
         [{:open? false :label "Foo" :id 1
           :children
           [{:open? false :label "Foo" :id 11}
            {:open? false :label "Bar" :id 12
             :children
             [{:open? false :label "Foo" :id 21}
              {:open? false :label "Bar" :id 22}
              {:open? false :label "Baz" :id 23
               :children
               [{:open? false :label "Foo" :id 211}
                {:open? false :label "Bar" :id 212}
                {:open? false :label "Baz" :id 213}]}]}
            {:open? false :label "Baz" :id 13}]}
          {:open? false :label "Bar" :id 2}
          {:open? false :label "Baz" :id 3}]}))

;; =============================================================================
;; Application

(declare branch)

(defn close-children [children]
  (->> children
       (map (fn [child]
              (if (:open? child)
                (assoc child :open? false)
                child)))
       (map (fn [{:keys [children] :as child}]
              (if children
                (assoc child :children (close-children children))
                child)))
       vec))

(defn leaf [{:keys [children open? label id] :as data} owner]
  (reify
    om/IDisplayName
    (display-name [_]
      "Leaf")
    om/IRenderState
    (render-state [_ {:keys [control] :as state}]
      (html
       [:div.radio {:key id}
        (if children
          [:label (when open? {:style {:text-decoration "underline"}})
           [:input {:type "checkbox"
                    :checked open?
                    :onChange #(do ;(put! control (om/path data))
                                   (when open?
                                     (om/transact! data :children close-children))
                                   (om/transact! data :open? not))}]
           id ": " label]
          [:div {:style {:margin-left "27px"}} id ": " label])
        (when (and children open?)
          (om/build branch data {:init-state state}))]))))

(defn branch [data owner]
  (reify
    om/IDisplayName
    (display-name [_]
      "Branch")
    om/IRenderState
    (render-state [_ state]
      (apply dom/div nil
        (om/build-all leaf (:children data) {:init-state state})))))

(defn tree [data owner]
  (reify
    om/IDisplayName
    (display-name [_]
      "Tree")
    om/IWillMount
    (will-mount [_]
      (let [control (om/get-state owner :control)]
        (go (while true
              (when-let [value (<! control)]
                (prn (get-in @data value)))))))
    om/IInitState
    (init-state [_]
      {:control (chan)})
    om/IRenderState
    (render-state [_ state]
      (om/build branch data {:init-state state}))))

;; =============================================================================
;; Init

#_(om/root tree app-state
         {:target (.getElementById js/document "ex0")
          ;:tx-listen (fn [{:keys [new-state] :as one} two] (prn "###" new-state))
          })

(om/root tree app-state
           {:target (.getElementById js/document "ex1")
            :instrument
            (fn [f cursor m]
              (if (= f leaf)
                (om/build* editor/editor (om/graft [f cursor m] cursor))
                ::om/pass))})
