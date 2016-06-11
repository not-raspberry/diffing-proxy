(ns diffing-proxy.main
  (:require [clojure.edn :as edn]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.params :refer [wrap-params]]
            [diffing-proxy.config :refer [read-command-line-opts]]
            [diffing-proxy.routes :refer [handler]])
  (:gen-class))


(def config)  ; set depending on the commandline config param in main
(def server)

(defn start-server!
  "Start the server and store it in a var."
  []
  (alter-var-root
    #'server
    (constantly (run-jetty (wrap-params (wrap-reload
                                          (partial #'handler
                                                   (:routes config)
                                                   (:backend config))))
                           (:proxy config)))))

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
              (alter-var-root #'config
                              (constantly (read-config (:config options))))
              (start-server!)))))
