(ns diffing-proxy.diffing
  (:require [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [ring.util.response :refer [response]]
            [differ.core :refer [diff]]
            [cheshire.core :refer [parse-string generate-string]]
            [slingshot.slingshot :refer [try+]])
  (:import [com.fasterxml.jackson.core JsonParseException]))


; Schema behind the atom:
; {"/path1"
;  ; value - sorted map:
;  {1 {"version" 1, "some_data" [], "sth_else" {}}
;   2 {"version" 2, "some_data" [1 2 3], "sth_else" {"a" 12}}
;   4 {"version" 4, "some_data" [1 2 3 4], "sth_else" {"a" 14}}}
;  "/path2" {}}
(def cached-versions (atom {}))

(defn update-cache [cache path version body]
  (->
    ; make sure it's a sorted-map:
    (update cache path #(if (nil? %) (sorted-map) %))
    (assoc-in [path version] body)))

(defn cache-response! [path version body]
  (swap! cached-versions #(update-cache % path version body)))

(defn integrate-response!
  [path parsed-response-body]
  (let [version (parsed-response-body "version")]
    (if (number? version)
      (cache-response! path version parsed-response-body)
      (log/error "Unversioned reponse to" path))))

(defn state-update-response
  "Serve incremental or full state update.

  Incremental state update will be returned if the client already holds some
  version of the state and the diffing-proxy has that version in its cache."
  [cache path client-version]
  (let [latest-version (second (last (get cache path)))]
    (if-let [client-state (get-in cache [path client-version])]
      (diff client-state latest-version)
      latest-version)))

(defn handle-diffed-route [base-backend-address path client-version]
  "Ask the backend for the most recent version, and if the client holds
  the state of some older version and the diffing-proxy cached that version,
  respond with a diff from the client version to the most recent one.
  If not, serve the full recent state.

  Backend response JSON will be memorised in `cached-versions` under
  its version key."
  (try+
    (let [resource-url (str base-backend-address path)
          body (:body (client/get resource-url))
          recent-state (parse-string body)]
      ; This is the current mean of updating the local cache. In order not
      ; to spam the backend with requests, backend requests should be throttled
      ; in future.
      (integrate-response! path recent-state)
      ; Therefore, do not rely on holding the `recent-state` here:
      (response (generate-string (state-update-response
                                   @cached-versions path client-version))))
    (catch [:status 404] _
      {:status 404, :body "Not Found on the backend"})
    (catch JsonParseException e
      (log/error "Backend responded an invalid JSON:" (.getMessage e))
      {:status 502
       :body "Bad Gateway\nBackend responded with an invalid JSON."})
    (catch map? {:keys [:status]}
      {:status 502
       :body (str "Bad Gateway\nBackend responded with " status)})))
