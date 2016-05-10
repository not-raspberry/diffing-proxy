(ns diffing-proxy.main
  (:require
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.reload :refer [wrap-reload]]
            [diffing-proxy.config :refer [read-command-line-opts]])
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
  (let [invocation (read-command-line-opts args)
        {:keys [options arguments errors summary]} invocation]
    (cond
      errors (doseq [e errors] (println e))
      (:help options) (println summary)
      :else (start-server! options))))
