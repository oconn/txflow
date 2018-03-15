(ns txflow.test-utils
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

(defn track-service-map
  "Watches each call to each injected service and reports on the number of
  times it is called."
  [transition-tree & body]
  (let [listener
        (atom (reduce (fn [service-map
                          [service-name _ _]]
                        (assoc service-map service-name {:called 0
                                                         :invalid-params 0}))
                      {}
                      body))

        mock-service
        {:listener listener
         :service-map
         (reduce (fn [service-map
                     [service-name input-spec output-spec]]
                   (assoc service-map service-name
                          (fn [& args]
                            (swap! listener update-in [service-name :called] inc)

                            (when-not (s/valid? input-spec args)
                              (swap! listener update-in [service-name :invalid-params] inc))

                            (try
                              (gen/generate (s/gen output-spec))
                              (catch Exception e (prn e))))))
                 {}
                 body)}]
    (if-not (s/valid? transition-tree (:service-map mock-service))
      (throw (ex-info "Invalid txflow implementation" {:spec transition-tree
                                                       :service-map body}))
      mock-service)))

(defn service-calls
  "Checks that the expected service calls have been made"
  [listener call-definition]
  (reduce-kv
   (fn [passing service count]
     (if-not passing
       false
       (and (= count (get-in @listener [service :called]))
            (= 0 (get-in @listener [service :invalid-params])))))
   true
   call-definition))
