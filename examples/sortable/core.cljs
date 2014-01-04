(ns examples.sortable.core
  (:refer-clojure :exclude [chars])
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [cljs.core.async :as async :refer [put! chan sliding-buffer alts!]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [clojure.string :as string]
            [goog.events :as events]
            [goog.dom :as gdom]
            [goog.style :as gstyle])
  (:import [goog.ui IdGenerator]
           [goog.events EventType]))

(enable-console-print!)

;; =============================================================================
;; Utilities

(defn guid []
  (.getNextUniqueId (.getInstance IdGenerator)))

(def chars (into [] "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"))

(defn rand-char []
  (nth chars (rand-int (count chars))))

(defn rand-word []
  (apply str (take (inc (rand-int 10)) (repeatedly rand-char))))

;; =============================================================================
;; Generic Draggable

(defn to? [owner next-state k]
  (and (not (om/get-state owner k))
       (k next-state)))

(defn from? [owner next-state k]
  (and (om/get-state owner k)
       (not (k next-state))))

(defn location [e]
  [(.-clientX e) (.-clientY e)])

(defn element-offset [el]
  (let [offset (gstyle/getPageOffset el)]
    [(.-x offset) (.-y offset)]))

(defn drag-start [e owner opts]
  (let [el (om/get-node owner "draggable")
        drag-start (location e)
        el-offset (element-offset el)
        drag-offset (vec (map - el-offset drag-start))
        delg (:delegate opts)]
    (if delg
      (put! (:chan delg)
        {:event :drag-start
         :location (vec (map + drag-start drag-offset))})
      (doto owner
        (om/set-state! :dragging true)
        (om/set-state! :location
          ((or (:constrain opts) identity) el-offset))
        (om/set-state! :drag-offset drag-offset)))))

(defn drag-stop [owner opts]
  (when (om/get-state owner :dragging)
    (if-let [delg (:delegate opts)]
      (put! (:chan delg) {:event :drag-stop})
      (doto owner
        (om/set-state! :dragging false)
        (om/set-state! :location nil)
        (om/set-state! :drag-offset nil)))))

(defn drag [e owner opts]
  (when (om/get-state owner :dragging)
    (let [loc ((or (:constrain opts) identity)
                (vec (map + (location e) (om/get-state owner :drag-offset))))
          delg (:delegate opts)]
      (if delg
        (put! (:chan delg) {:event :drag :location loc})
        (om/set-state! owner :location loc)))))

(defn draggable [item owner {:keys [chans] :as opts}]
  (reify
    om/IWillMount
    (will-mount [_]
      ;; support remote control
      (when-let [delg (:delegate opts)]
        (go (while true
              (let [[e c] (<! (:remote-control delg))]
                (case (:type e)
                  :drag (om/set-state! owner :location (:location e))
                  nil))))))
    om/IDidMount
    (did-mount [_ _]
      ;; capture the cell dimensions when it becomes available
      (let [dims (om/get-state owner :dimensions)]
        (when-not dims
          (let [size (-> owner (om/get-node "draggable") gstyle/getSize)
                dims [(.-width size) (.-height size)]]
            (om/set-state! owner :dimensions dims)
            (when-let [delg (:delegate opts)]
              (put! (:chan delg) {:event :dimensions :value dims}))))))
    om/IWillUpdate
    (will-update [_ next-props next-state]
      ;; begin dragging
      (when (to? owner next-state :dragging)
        (let [mouse-up (fn [e] (drag-stop owner opts))
              mouse-move #(drag % owner opts)]
          (om/set-state! owner :window-listeners
            [mouse-up mouse-move])
          (doto js/window
            (events/listen EventType.MOUSEUP mouse-up)
            (events/listen EventType.MOUSEMOVE mouse-move))))
      ;; end dragging
      (when (from? owner next-state :dragging)
        (let [[mouse-up mouse-move]
              (om/get-state owner :window-listeners)]
          (doto js/window
            (events/unlisten EventType.MOUSEUP mouse-up)
            (events/unlisten EventType.MOUSEMOVE mouse-move)))))
    om/IRender
    (render [_]
      (let [dragging (om/get-state owner :dragging)
            loc (:location item) ;; remote controlled
            style (cond
                    (or dragging loc)
                    (let [[x y] (or (om/get-state owner :location) loc)
                          [w h] (or (om/get-state owner :dimensions)
                                    (:dimensions item))]
                      #js {:position "absolute" :top y :left x :z-index 1
                           :width w :height h})
                    :else
                    #js {:position "static" :z-index 0})]
        (dom/li
          #js {:className (when dragging "dragging")
               :style style
               :ref "draggable"
               :onMouseDown #(drag-start % owner opts)
               :onMouseUp (fn [e] (drag-stop owner opts))
               :onMouseMove #(drag % owner opts)}
          (om/build (:view opts) item {:opts opts}))))))

;; =============================================================================
;; Generic Sortable

(defn sortable-spacer [info owner opts]
  (om/component
    (dom/li #js {:style #js {:visibility "hidden" :height (:height info)}})))

(defn start-sort [owner e]
  (om/set-state! owner :sorting true))

(defn handle-drop [owner e]
  (let [sort (om/get-state owner :sort)]
    (doto owner
      (om/set-state! :sorting false)
      (om/set-state! :sort
        (->> (concat (take sort (om/get-state! owner :drop))
                     [(:id e)]
                    (drop sort (om/get-state! owner :drop)))
          (into []))))))

(defn update-drop [owner [x y]]
  (om/set-state! owner :location location))

(defn sortable [{:keys [items sort]} owner opts]
  (reify
    om/IInitState
    (init-state [_] {:sort sort})
    om/IWillMount
    (will-mount [_]
      (let [drag-chan (chan)]
        (om/set-state! owner :drag-chan drag-chan)
        (go (while true
              (let [[e c] (<! drag-chan)]
                (case (:type e)
                  :dimensions (om/set-state! owner
                                :cell-dimensions (:dimensions e))
                  :drag-start (start-sort owner e) 
                  :drag-stop  (handle-drop owner e)
                  :drag       (update-drop owner (:location e))
                  nil))))))
    om/IWillUpdate
    (will-update [_ next-props next-state])
    om/IRender
    (render [_]
      (dom/div #js {:className "om-sortable"}
        (when-let [item (om/get-state owner :sorting)]
          (om/build draggable
            (assoc (items item)
              :location   (om/get-state owner :cell-location)
              :dimensions (om/get-state owner :cell-dimensions))
            {:opts opts :react-key "sortable-cell"}))
        (dom/ul #js {:key "list" :ref "list"}
          (into-array
            (map (fn [id]
                   (if-not (= id ::spacer)
                     (om/build draggable (items id) {:key :id :opts opts})
                     (om/build sortable-spacer (items id) {:react-key "spacer-cell"})))
              (om/get-state owner :sort))))))))

;; =============================================================================
;; Example

(def app-state
  (let [items (->> (take 10 (repeatedly guid))
                (map (fn [id] [id {:id id :text (rand-word)}]))
                (into {}))]
    (atom {:items items
           :sort (into [] (keys items))})))

(defn item [the-item owner opts]
  (om/component (dom/span nil (str "Item " (:text the-item)))))

(om/root app-state
  (fn [app owner]
    (reify
      om/IWillMount
      (will-mount [_]
        (let [{:keys [start drag stop] :as chans}
              (zipmap [:start :drag :stop]
                      (repeatedly #(chan (sliding-buffer 1))))]
          (om/set-state! owner :chans chans)
          (go (while true
                (alt!
                  start ([v c] (println "start"))
                  drag  ([v c] (println "drag"))
                  stop  ([v c] (println "stop")))))))
      om/IRender
      (render [_]
        (dom/div nil
          (dom/h2 nil "Sortable example")
          (om/build sortable app
            {:opts {:view item :chans (om/get-state owner :chans)}})))))
  (.getElementById js/document "app"))