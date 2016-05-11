(defproject diffing-proxy "0.1.0-SNAPSHOT"
  :description "A service do convert full state updates to incremental updates in HTTP reponses."
  :url "https://github.com/not-raspberry/diffing-proxy/"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.5.0"]
                 [ring "1.4.0"]
                 [differ "0.3.1"]
                 [org.clojure/tools.cli "0.3.4"]]
  :main ^:skip-aot diffing-proxy.main
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]]}
   :uberjar {:aot :all}})
