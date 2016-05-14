(ns diffing-proxy.routes
  (:require [compojure.core :refer [routes GET]]
            [compojure.route :as route]
            [diffing-proxy.diffing :refer [handle-diffed-route]]))

(defn proxy-routes
  "Generate a proxy route for each configured path plus default routes."
  [routes-config backend-config]
  (let [base-backend-address (str
                               (:scheme backend-config "http")
                               "://"
                               (:host backend-config "localhost")
                               ":"
                               (:port backend-config 8000))]
    (apply routes
           (concat
             [(GET "/" [] "Diffing proxy root. Nothing to be seen here.")]
             (for [[path _] routes-config]
               (GET path request (handle-diffed-route
                                   base-backend-address request)))
             [(route/not-found "Not Found")]))))
