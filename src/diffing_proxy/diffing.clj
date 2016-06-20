(ns diffing-proxy.diffing
  (:require [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [ring.util.response :refer [response]]
            [differ.core :refer [diff]]
            [cheshire.core :refer [parse-string generate-string]]
            [slingshot.slingshot :refer [try+]])
  (:import [com.fasterxml.jackson.core JsonParseException]
           [java.net ConnectException SocketTimeoutException]
           [org.apache.http.conn ConnectTimeoutException]))


; Schema behind the atom:
; {"/path1" (sorted-map
;             1 {"version" 1, "some_data" [], "sth_else" {}}
;             2 {"version" 2, "some_data" [1 2 3], "sth_else" {"a" 12}}
;             4 {"version" 4, "some_data" [1 2 3 4], "sth_else" {"a" 14}})
;             "/path2" (sorted-map)}
(def cached-versions (atom {}))

(defn update-cache [cache path version body]
  (->
    ; make sure it's a sorted-map:
    (update cache path #(if (nil? %) (sorted-map) %))
    (assoc-in [path version] body)))

(defn cache-response! [path version body]
  (swap! cached-versions #(update-cache % path version body)))

(defn integrate-response
  "Save the response in the cache for the path under its version.

  Log an error if the body contains no version."
  [caching-fn path parsed-response-body]
  (let [version (parsed-response-body "version")]
    (if (number? version)
      (caching-fn path version parsed-response-body)
      (log/error "Unversioned reponse to" path))))

(defn filter-headers
  "Discard headers that are related to the client-proxy connection and should not interfere
  with the proxy-backend queries."
  [headers-map]
  (dissoc headers-map
          "Accept-Encoding" "Accept-Charset" "Connection" "Content-Length" "Content-Type" "TE"
          "Upgrade"))

(defn query-backend
  "Ask the backend for a state update.

  Will throw com.fasterxml.jackson.core.JsonParseException if the backend
  responds with an invalid JSON. Propagates clj-http.client exceptions."
  [base-backend-address path backend-request-options]
  (->> (client/get (str base-backend-address path)
                   (update backend-request-options :headers filter-headers))
       :body
       parse-string))

(defn state-update-response
  "Serve incremental or full state update.

  Incremental state update will be returned if the client already holds some
  version of the state and the diffing-proxy has that version in its cache."
  [cache path client-version]
  (let [latest-version (second (last (get cache path)))]
    (if-some [client-state (get-in cache [path client-version])]
      (diff client-state latest-version)
      latest-version)))

(defn dispatch-state-update
  "Ask the backend for the most recent version, and if the client holds
  the state of some older version and the diffing-proxy cached that version,
  respond with a diff from the client version to the most recent one.
  If not, serve the full recent state.

  Backend response JSON will be memorised in `cached-versions` under
  its version key."
  [base-backend-address path backend-request-options client-version]
  (try+
    (let [recent-state (query-backend base-backend-address path backend-request-options)]
      (integrate-response cache-response! path recent-state)
      (response (generate-string (state-update-response
                                   @cached-versions path client-version))))
    (catch [:status 404] _
      {:status 404, :body "Not Found on the backend"})
    (catch JsonParseException e
      (log/error "Backend responded with an invalid JSON:" (.getMessage e))
      {:status 502
       :body "Bad Gateway\nBackend responded with an invalid JSON."})
    (catch ConnectTimeoutException _
      {:status 504 :body "Gateway Timeout\nBackend unreachable."})
    (catch SocketTimeoutException _
      {:status 504 :body "Gateway Timeout\nBackend responding too slowly."})
    (catch ConnectException _
      {:status 503 :body "Service Unavailable\nConnection refused."})
    (catch map? {:keys [:status]}
      {:status 502
       :body (str "Bad Gateway\nBackend responded with " status)})))
