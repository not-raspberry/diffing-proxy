(ns backend-mock.main
  "A mock web API - memorises POST request body for its path and sends it
  back in response to a GET request at the same path. It's possible to delete
  the resource by issuing a DELETE request."
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.util.response :refer [response not-found]]
            [clojure.pprint :refer [pprint]])
  (:gen-class))

(def server)
(def resources (atom {}))

(defmulti handler :request-method)

(defmethod handler :get [request]
  (if-let [content (@resources (request :uri))]
    (response content)
    (not-found "Not Found")))

(defmethod handler :post [request]
  (swap! resources #(assoc % (request :uri) (slurp (:body request))))
  (response "OK"))

(defmethod handler :delete [request]
  (swap! resources #(dissoc % (request :uri)))
  (response "OK"))

(defmethod handler :default [_]
  {:status 405, :headers {}, :body "Method Not Allowed"})

(defn start-server!
  "Start the server and store it in a var."
  [config]
  (def server (run-jetty (wrap-reload #'handler) config)))

(defn -main []
  (start-server! {:port 8000}))
