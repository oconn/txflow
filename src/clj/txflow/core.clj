(ns txflow.core)

(defn- third
  ^{:doc "Returns the third value in the tuple (transition state)"
    :attribution "https://github.com/jebberjeb/fsmviz/blob/master/src/fsmviz/core.cljc"}
  [coll]
  (nth coll 2))

(defn- term-states
  ^{:doc "Returns states which have no outbound transitions."
    :attribution "https://github.com/jebberjeb/fsmviz/blob/master/src/fsmviz/core.cljc"}
  [tuples]
  (clojure.set/difference (set (map third tuples)) (set (map first tuples))))

(defn- map->tuples
  ^{:doc "Returns a collection of [from via to] tuples representing the FSM."
    :attribution "https://github.com/jebberjeb/fsmviz/blob/master/src/fsmviz/core.cljc"}
  [state-map]
  (mapcat (fn [[from m]]
            (map (fn [[trans to]]
                   [from trans to])
                    m))
          state-map))

(defn get-termination-states
  "Returns a set of termination states"
  [txflow-graph]
  (-> txflow-graph
      map->tuples
      term-states))

(defn is-termination-state?
  "Checks if the provided transition is a termination state"
  [txflow-graph state]
  (-> txflow-graph
      get-termination-states
      (contains? state)))

(defn next-state
  "Processes the next state in the transition tree"
  [state-transition-functions
   txflow-graph
   {:keys [transition event] :as context}]
  (let [next-transition
        (get-in txflow-graph [transition event])

        transition-fn
        (when next-transition
          (next-transition state-transition-functions))

        invalid-implementation
        (and (not transition-fn)
             (not (is-termination-state? txflow-graph next-transition)))]

    ;; Fails hard on invalid implementations
    (when invalid-implementation
      (throw (ex-info
              "Invalid tx-flow implementation"
              {:txflow-graph txflow-graph
               :exit-state next-transition
               :termination-states (get-termination-states txflow-graph)})))

    (if transition-fn
      (next-state state-transition-functions
                  txflow-graph
                  (transition-fn (assoc context
                                        :transition
                                        next-transition)))
      context)))

(defn initialize-txflow
  [initial-state
   state-transition-functions
   txflow-graph]
  (next-state state-transition-functions
              txflow-graph
              {:state initial-state
               :transition :start
               :event :init
               :error nil}))

(defn tx-handler
  "Formats TX response to be consumed by service handlers"
  ([result]
   (tx-handler result
                {:throw-with
                 (fn [error-map]
                   (throw (ex-info "TX Handler Error"
                                   (assoc error-map :type :tx-handler-error))))}))
  ([result {:keys [throw-with] :as options}]
   (let [{:keys [error state]} result]
     (if error
       (throw-with error)
       state))))
