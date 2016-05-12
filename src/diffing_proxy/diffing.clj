(ns diffing-proxy.diffing)

(def cached-versions (atom {}))

(defn handle-diffed-route [request]
  (str (:uri request)))
