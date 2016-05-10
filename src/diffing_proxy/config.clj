(ns diffing-proxy.config
  (:require [clojure.tools.cli :as cli]))

(def defaults
  {:backend {:host "localhost"
             :port 8000}
   :diffed-resources [{:path "/resource/",
                       :version-param "v"}]})

(def port-validator [#(> % 0) "Ports are integers bigger than zero."])

(def arg-spec
  [[nil "--host HOST" "The host for the diffing proxy to bind to"
    :default "localhost"]
   [nil "--port PORT" "Port for the diffing proxy to listen on"
    :default 3000
    :parse-fn #(Integer/parseInt %)
    :validate port-validator]
   [nil "--backend-host HOST" "The host of the backend to query for state"
    :default "localhost"]
   [nil "--backend-port PORT" "Port of the backend"
    :default 8000
    :parse-fn #(Integer/parseInt %)
    :validate port-validator]
   [nil "--help"]])

(defn read-command-line-opts [options]
  (cli/parse-opts options arg-spec))
