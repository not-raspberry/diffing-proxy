(ns diffing-proxy.routes
  (:require [diffing-proxy.diffing :refer [dispatch-state-update]]))

(defn parse-version
  "Return parsed integer if `version-string` is not missing (nil) or empty, otherwise :invalid"
  [version-string]
  (when-not (or (empty? version-string) (nil? version-string))
    (try (Integer/parseInt version-string)
         (catch NumberFormatException e :invalid))))

(defn backend-address
  "Build a backend HTTP address from the passed backend config, fill in
  defaults."
  [backend-config]
  (str (:scheme backend-config "http")
    "://"
    (:host backend-config "localhost")
    ":"
    (:port backend-config 8000)))

(defn handle-diffed-route
  "Handler for a given path that will proxy backend state
  update requests on that path."
  [base-backend-address path request]
  (let [client-version-string (get-in request [:params "known-version"])
        client-version (parse-version client-version-string)]
      (if (= :invalid client-version)
        {:status 400, :body "Malformed 'known version' number"}
        (dispatch-state-update base-backend-address path client-version))))


(defmacro must-be-get [method success-form]
  `(if (= ~method :get)
     ~success-form
     {:status 405 :body "Method not allowed"}))

(defn handler
  [routes-config backend-config {path :uri method :request-method :as request}]
  (cond
    (= path "/") (must-be-get method
                              {:status 200
                               :body "Diffing proxy root. Nothing to be seen here.\n"})

    (not (contains? routes-config path)) {:status 404 :body "Not Found\n"}

    :else (must-be-get method (handle-diffed-route
                                (backend-address backend-config) path request))))
