(ns diffing-proxy.diffing
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [differ.core :refer [diff patch]]
            [slingshot.slingshot :refer [try+]]))

(def cached-versions (atom {}))

(defn handle-diffed-route [base-backend-address request]
  (try+
    (let [resource-url (str base-backend-address (:uri request))
          backend-response (client/get resource-url)]
        (:body backend-response))
    (catch [:status 404] _
        {:status 404, :body "Not Found on the backend"})
    (catch map? {:keys [:status]}
        {:status 502
         :body (str "Bad Gateway\nBackend responded with " status)})))
