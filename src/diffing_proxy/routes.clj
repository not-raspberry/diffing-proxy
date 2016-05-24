(ns diffing-proxy.routes
  (:require [compojure.core :refer [routes GET]]
            [compojure.route :as route]
            [diffing-proxy.diffing :refer [handle-diffed-route]]))

(defn parse-version
  "Return parsed integer if `version-string` is not missing (nil) or empty, otherwise :invalid"
  [version-string]
  (if (or (empty? version-string) (nil? version-string))
    nil
    (try (Integer/parseInt version-string)
         (catch NumberFormatException e :invalid))))

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
               (GET path {params :params}
                    (let [{client-version-string "known-version"} params
                          client-version (parse-version client-version-string)]
                      (if (= :invalid client-version)
                        {:status 400, :body "Malformed 'known version' number"}
                        (handle-diffed-route base-backend-address path client-version)))))
             [(route/not-found "Not Found")]))))
