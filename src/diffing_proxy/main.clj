(ns diffing-proxy.main
  (:require
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.reload :refer [wrap-reload]])
  (:gen-class))

(defroutes app-routes
  (GET "/" [] "Hello")
  (route/not-found "Not Found"))

(def app
  (wrap-reload app-routes))

(defn start-server! [options]
  (def server (run-jetty #'app options)))

(defn stop-server! []
  (if-let [server (resolve 'server)]
    (.stop @server)))

(defn -main [& args]
  (println "Main" args)
  (start-server! {:port 3999}))
