(ns diffing-proxy.diffing-test
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clj-http.fake :refer [with-fake-routes]]
            [differ.core :refer [diff]]
            [diffing-proxy.diffing :refer :all]))

(defn values-sorted? [m] (every? sorted? (vals m)))

(deftest test-update-cache
  (testing "single update"
    (let [updated-cache (update-cache {} "/path" 20 {:some-key :some-val})]
      (is (= updated-cache {"/path" {20 {:some-key :some-val}}}))
      (is (values-sorted? updated-cache))))

  (testing "2 versions of the same resource"
    (let [twice-updated-cache
          (-> {}
              (update-cache "/path" 10 {"krakens" [200 300]})
              (update-cache "/path" 11 {"krakens" [200 300 400 500]}))]
      (is (= twice-updated-cache
             {"/path" {10 {"krakens" [200 300]}
                       11 {"krakens" [200 300 400 500]}}}))
      (is (values-sorted? twice-updated-cache))))

  (testing "2 different resources in the cache map"
    (let [two-resources-cache
          (-> {}
              (update-cache "/path1" 1 {"frogs" 32})
              (update-cache "/path2" 3 {"pears" 12}))]
      (is (= two-resources-cache
             {"/path1" {1 {"frogs" 32}}
              "/path2" {3 {"pears" 12}}})
          (is (values-sorted? two-resources-cache)))))

  (testing "if the cache is updated with a response which version it
           already holds but the body differs, that version is replaced
           in the cache"
    (is (= (update-cache {"/res" {13 {"frogs" 32}}}
                         "/res" 13 {"frogs" 64})
           {"/res" {13 {"frogs" 64}}}))))

(deftest test-integrate-response
  (let [fake-caching-fn (constantly :cached)]
    (testing "versioned responses are cached"
      (is (= (integrate-response fake-caching-fn "/path"
                                 {"version" 2, "value" "xxx"})
             :cached))
    (testing "unversioned responses are ignored"
      (is (nil? (integrate-response fake-caching-fn
                                    "/path" {"value" "xxx"})))))))

(deftest test-state-update-response
  (let [state-1 {"state" [123 123]}
        state-2 {"state" [123 123 222 222]}
        state-latest {"state" [123 123 222 222 333 444]}
        cache {"/path1" (sorted-map 1 state-1, 2 state-2, 3 state-latest)}
        empty-diff (diff {} {})]

    (testing "state-update-response returns the complete latest state if the
             client does not hold any version"
      (is (= (state-update-response cache "/path1" nil) state-latest)))

    (testing "state-update-response returns a diff from the version the client
             claims to know to the latest version"
      (is (= (state-update-response cache "/path1" 1)
             (diff state-1 state-latest)))
      (is (= (state-update-response cache "/path1" 2)
             (diff state-2 state-latest))))

    (testing "state-update-response returns an empty diff if the client already
             holds the latest version"
      (is (= (state-update-response cache "/path1" 3) empty-diff)))))

(deftest test-passing-headers
  (let [client-headers  ; a map of headers that should not be passed
        (conj (zipmap ["Accept-Encoding" "Accept-Charset" "Connection"
                       "Content-Length" "Content-Type" "TE" "Upgrade"]
                      (repeat "should-not-be-passed"))
              ["Blue" "lobster"] ["Cookie" "asdf"])]  ; plus some that shouldn

    (with-fake-routes
      {"http://backend.local/resource"
       (fn [{backend-request-headers :headers}]

         ; The following client's headers should not be passed - but there
         ; may be ones sent by the proxy.
         (are [client-header] (not= (backend-request-headers client-header)
                                    (client-headers client-header))
              "Accept-Encoding" "Accept-Charset" "Connection" "Content-Length"
              "Content-Type" "TE" "Upgrade")

         ; Headers that should be passed exactly:
         (are [client-header] (= (backend-request-headers client-header)
                                 (client-headers client-header))
              "Blue" "Cookie")

         {:status 200, :body "{\"version\": 21}"})}

      (dispatch-state-update
        "http://backend.local" "/resource" client-headers nil))))
