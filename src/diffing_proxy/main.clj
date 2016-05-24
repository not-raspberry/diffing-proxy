(ns diffing-proxy.main
  (:require [clojure.edn :as edn]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.params :refer [wrap-params]]
            [diffing-proxy.config :refer [read-command-line-opts]]
            [diffing-proxy.routes :refer [proxy-routes]])
  (:gen-class))


(def app-routes)  ; routes depend on the config - recomputed on
                  ; each request if files changed
(def config)  ; set depending on a commandline param in main
(def server)

(defn rebind-routes! []
  (def app-routes (proxy-routes (:routes config) (:backend config))))

(defn full-wrap-reload
  "Wrap the routes handler function with a custom wrapper that will not only
  reload all modified namespaces on each request but also recompute the routes.

  Routes are dynamic and dependent on the passed config, hence the overall
  ugliness."
  [handler]
  (let [custom-reloader! (fn [request] (rebind-routes!) (handler request))]
    (wrap-reload custom-reloader!)))

(defn start-server!
  "Start the server and store it in a var."
  []
  (rebind-routes!)
  (def server (run-jetty (wrap-params (full-wrap-reload #'app-routes)) (:proxy config))))

(defn stop-server!
  "Stop the server if running."
  []
  (if (bound? #'server)
    (.stop server)))

(defn read-config [path]
  (edn/read-string (slurp path)))

(defn -main
  "Handle command-line options, set the global vars depending on the passed
  config file and run the server."
  [& args]
  (let [invocation (read-command-line-opts args)
        {:keys [options errors summary]} invocation]
    (cond
      errors (do
               (doseq [e errors] (println e))
               (System/exit 1))
      (:help options) (println summary)
      :else (do
              (def config (read-config (:config options)))
              (start-server! config)))))
