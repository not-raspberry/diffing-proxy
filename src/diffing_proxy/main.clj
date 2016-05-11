(ns diffing-proxy.main
  (:require
            [clojure.edn :as edn]
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

(defn start-server! [config]
  "Start the server and store it in a var."
  (def server (run-jetty #'app (:proxy config))))

(defn stop-server! []
  "Stop the server if running."
  (if-let [server (resolve 'server)]
    (.stop @server)))

(defn read-config [path]
  (edn/read-string (slurp path)))

(defn reread-config! [path]
  "Re-read the config file and restart the server."
  (let [config (read-config path)]
    (stop-server!)
    (start-server! config)))

(defn -main [& args]
  (let [invocation (read-command-line-opts args)
        {:keys [options errors summary]} invocation]
    (cond
      errors (do
               (doseq [e errors] (println e))
               (System/exit 1))
      (:help options) (println summary)
      :else (start-server! (read-config (:config options))))))
