(defproject diffing-proxy "0.1.0-SNAPSHOT"
  :description "A service to convert full state updates to incremental updates in HTTP reponses."
  :url "https://github.com/not-raspberry/diffing-proxy/"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ring "1.5.0"]
                 [clj-http "2.2.0"]
                 [differ "0.3.1"]
                 [slingshot "0.12.2"]
                 [cheshire "5.6.1"]]
  :main ^:skip-aot diffing-proxy.main
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]
                        [clj-http-fake "1.0.2"]]}
   :backend-mock [:default {:main backend-mock.main}]
   :uberjar {:aot :all}})
