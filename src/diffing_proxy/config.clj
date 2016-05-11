(ns diffing-proxy.config
  (:require [clojure.tools.cli :as cli]))

(def port-validator [#(> % 0) "Ports are integers bigger than zero."])

(def arg-spec
  [["-c" "--config FILE" "EDN config file"]
   ["-h" "--help"]])

(defn missing-config-argument-to-error [parsed-cmdline-map]
  "If the `-c`/`--config` argument is missing and the user is not asking
  for help, make it an error."
  (let [{options :options} parsed-cmdline-map]
    (if-not (or (contains? options :config)
                (contains? options :help))
      (update parsed-cmdline-map :errors #(conj % "No --config provided."))
      parsed-cmdline-map)))

(defn read-command-line-opts [options]
  (missing-config-argument-to-error
    (cli/parse-opts options arg-spec :strict true)))
