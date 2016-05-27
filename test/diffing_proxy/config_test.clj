(ns diffing-proxy.config-test
  (:require [clojure.test :refer :all]
            [diffing-proxy.config :refer :all]))

(deftest test-missing-config-argument-to-error
  (testing "Lack of the --config argument is an error"
    (is
      (missing-config-argument-to-error {:options []})
      {:options [], :errors ["No --config provided."]}))

  (testing
    "When --config or --help are passed, commandline arguments are not transformed"
    (are [parsed-args] (= parsed-args (missing-config-argument-to-error parsed-args))
         {:options {:config "sth"}}
         {:options {:help true}})))

(deftest test-read-command-line-opts
  (are [cmdline-args errors]
       (= (:errors (read-command-line-opts cmdline-args)) errors)
       [] ["No --config provided."]
       ["--config" "abc"] nil
       ["--help"] nil))
