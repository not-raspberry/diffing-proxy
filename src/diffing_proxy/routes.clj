(ns diffing-proxy.routes
  (:require [compojure.core :refer [routes GET]]
            [compojure.route :as route]
            [diffing-proxy.diffing :refer [handle-diffed-route]]))

(defn
  proxy-routes
  "Generate a proxy route for each configured path plus default routes."
  [config-routes]
  (apply routes
         (concat
           [(GET "/" [] "Diffing proxy root. Nothing to be seen here.")]
           (for [[path _] config-routes]
             (GET path request (handle-diffed-route request)))
           [(route/not-found "Not Found")])))
