(ns diffing-proxy.diffing
  (:require [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [differ.core :refer [diff patch]]
            [cheshire.core :refer [parse-string]]
            [slingshot.slingshot :refer [try+]])
  (:import [com.fasterxml.jackson.core JsonParseException]))

; Schema behind the atom:
; {"/path1"
;  {1 {"version" 1, "some_data" [], "sth_else" {}}
;   2 {"version" 2, "some_data" [1 2 3], "sth_else" {"a" 12}}
;   4 {"version" 4, "some_data" [1 2 3 4], "sth_else" {"a" 14}}}
;  "/path2" {}}
(def cached-versions (atom {}))

(defn update-cache [cache path version body]
  (assoc-in cache [path version] body))

(defn cache-response! [path version body]
  (swap! cached-versions #(update-cache % path version body)))

(defn integrate-response!
  [path parsed-response-body]
  (let [version (parsed-response-body "version")]
    (if (number? version)
      (cache-response! path version parsed-response-body)
      (log/error "Unversioned reponse to" path))))

(defn handle-diffed-route [base-backend-address request]
  (try+
    (let [path (:uri request)
          resource-url (str base-backend-address path)
          body (:body (client/get resource-url))
          parsed-json (parse-string body)]
      (integrate-response! path parsed-json)
      body)
    (catch [:status 404] _
      {:status 404, :body "Not Found on the backend"})
    (catch JsonParseException e
      (log/error "Backend responded an invalid JSON:" (.getMessage e))
      {:status 502
       :body (str "Bad Gateway\nBackend responded with an invalid JSON.")})
    (catch map? {:keys [:status]}
      {:status 502
       :body (str "Bad Gateway\nBackend responded with " status)})))
