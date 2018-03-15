(ns txflow.core-test
  (:require [clojure.test :refer :all]

            [txflow.core :refer [get-termination-states
                                 is-termination-state?
                                 next-state
                                 initialize-txflow
                                 tx-handler]]))

(def sample-txflow-graph
  {:start
   {:init :state-1}

   :state-1
   {:intermediate-transition :state-2
    :end-transition :end-state-1}

   :state-2
   {:intermediate-transition :state-3
    :end-transition :end-state-2}

   :state-3
   {:intermediate-transition :state-4
    :end-transition :end-state-3}

   :state-4
   {:end-transition :end-state-4}})

(def sample-transition-functions
  {:state-1 #(-> %
                 (update :state inc)
                 (assoc :event :intermediate-transition))
   :state-2 #(-> %
                 (update :state inc)
                 (assoc :event :intermediate-transition))
   :state-3 #(-> %
                 (update :state inc)
                 (assoc :event :intermediate-transition))
   :state-4 #(-> %
                 (update :state inc)
                 (assoc :event :end-transition))})

(deftest get-termination-states-test
  (testing "Lists all possible termination states"
    (is (= #{:end-state-1 :end-state-2 :end-state-3 :end-state-4}
           (get-termination-states sample-txflow-graph)))))

(deftest is-termination-state-test
  (testing "Returns true if provided state is a termination state"
    (is (= true (is-termination-state? sample-txflow-graph
                                       :end-state-1))))
  (testing "Returns false if provided state is not a termination state"
    (is (= false (is-termination-state? sample-txflow-graph
                                        :state-1)))))

(deftest next-state-test
  (testing "Iterates through all transitions in a valid tranistion map"
    (is (= {:transition :state-4
            :event :end-transition
            :state 5}
           (next-state sample-transition-functions
                       sample-txflow-graph
                       {:state 1
                        :transition :start
                        :event :init}))))
  (testing "Returns an error on an invalid txflow"
    (is (= clojure.lang.ExceptionInfo
           (try (next-state {:state-1 #(-> %
                                           (update :state inc)
                                           (assoc :event :non-existent-transition))}
                            sample-txflow-graph
                            {:state 1
                             :transition :start
                             :event :init})
                (catch Exception e
                  (type e)))))))

(deftest initialize-txflow-test
  (testing "Builds and returns a fully transformed context"
    (is (= {:state 6
            :transition :state-4
            :event :end-transition
            :error nil}
           (initialize-txflow 2
                              sample-transition-functions
                              sample-txflow-graph))))
  (testing "Returns an error with an invalid implementation"
    (is (= clojure.lang.ExceptionInfo
           (try (initialize-txflow 2
                                   (assoc sample-transition-functions
                                          :state-2 #(assoc % :event :invalid-event))
                                   sample-txflow-graph)
                (catch Exception e
                  (type e)))))))

(deftest tx-handler-test
  (testing "Returns just the state property on successful transitions"
    (is (= 6
           (tx-handler (initialize-txflow 2
                                          sample-transition-functions
                                          sample-txflow-graph)))))
  (testing "Throws and error when an error is applied to a transition"
    (is (= :tx-handler-error
           (try (tx-handler (initialize-txflow
                             2
                             (assoc sample-transition-functions
                                    :state-2
                                    #(merge % {:error {}
                                               :event :end-transition}))
                             sample-txflow-graph))
                (catch Exception e
                  (-> e ex-data :type)))))))
