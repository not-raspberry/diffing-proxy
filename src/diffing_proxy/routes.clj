(ns diffing-proxy.routes
  (:require [compojure.core :refer [routes GET]]
            [compojure.route :as route]
            [diffing-proxy.diffing :refer [dispatch-state-update]]))

(defn parse-version
  "Return parsed integer if `version-string` is not missing (nil) or empty, otherwise :invalid"
  [version-string]
  (if (or (empty? version-string) (nil? version-string))
    nil
    (try (Integer/parseInt version-string)
         (catch NumberFormatException e :invalid))))

(defn diffed-route-handler
  "Returns a handler for a given path that will proxy backend state
  update requests on that path."
  [path base-backend-address]
  (GET path {params :params}
       (let [{client-version-string "known-version"} params
             client-version (parse-version client-version-string)]
         (if (= :invalid client-version)
           {:status 400, :body "Malformed 'known version' number"}
           (dispatch-state-update base-backend-address path client-version)))))

(defn dynamic-routes
  "Generates a sequence of diffed route handlers, depending on the config.

  Values in routes-config are currently ignored."
  [routes-config base-backend-address]
  (for [[path _] routes-config]
    (diffed-route-handler path base-backend-address)))

(defn backend-address
  "Build a backend HTTP address from the passed backend config, fill in
  defaults."
  [backend-config]
  (str (:scheme backend-config "http")
    "://"
    (:host backend-config "localhost")
    ":"
    (:port backend-config 8000)))

(defn proxy-routes
  "Generate a proxy route for each configured path plus default routes."
  [routes-config backend-config]
  (apply routes
         (concat
           [(GET "/" [] "Diffing proxy root. Nothing to be seen here.")]
           (dynamic-routes routes-config (backend-address backend-config))
           [(route/not-found "Not Found")])))
