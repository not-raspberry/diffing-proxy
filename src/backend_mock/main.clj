(ns backend-mock.main
  "A mock web API - memorises POST request body for its path and sends it
  back in response to a GET request at the same path. It's possible to delete
  the resource by issuing a DELETE request.

  Super-hack! Reources with set values equal to \"sleep\" will sleep for 10 seconds
  on each request.

  Usage with curl:
  $ curl http://127.0.0.1:1212/ -d 'data' -H 'Content-Type: text/plain'
  Won't work without '-H' because otherwise curl will sent the data as a form."
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [response not-found]]
            [clojure.pprint :refer [pprint]])
  (:gen-class))

(def server)
(def resources (atom {}))

(defmulti handler :request-method)

(defmethod handler :get [request]
  (if-some [content (@resources (request :uri))]
    (do
      (when (= content "sleep")  ; super-hack to simulate delays
        (Thread/sleep 1000))
      (response content))
    (not-found "Not Found")))

(defmethod handler :post [request]
  (swap! resources #(assoc % (request :uri) (slurp (:body request))))
  (response "OK"))

(defmethod handler :delete [request]
  (swap! resources #(dissoc % (request :uri)))
  (response "OK"))

(defmethod handler :default [_]
  {:status 405, :headers {}, :body "Method Not Allowed"})

(defn start-server
  "Start the server and return it."
  [config]
  (run-jetty (wrap-params (wrap-reload #'handler)) config))

(defn start-server!
  "Start the server and store it in a var."
  [config]
  (alter-var-root
    #'server (constantly (start-server config))))

(defn -main []
  (start-server! {:port 8000}))
