(ns diffing-proxy.routes-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [clj-http.fake :refer [with-fake-routes]]
            [cheshire.core :refer [generate-string]]
            [differ.core :refer [diff]]
            [backend-mock.main :as m]
            [diffing-proxy.routes :refer :all]
            [diffing-proxy.diffing :refer :all])
  (:import [java.net ServerSocket]))

(def port-to-occupy 8213)

(defn mock-server
  "Run a mock server in a separate thread."
  [f]
  (let [mock-server (m/start-server {:port port-to-occupy :join? false})]
    ; will sleep for 10 seconds if the root path is requested:
    (reset! m/resources {"/timeout" "sleep"})
    (f)
    (.stop mock-server)
    (reset! m/resources {})))

(defmacro test-with-fixture
  "Use the fixture within a block. Looks like it's time for another test framework."
  [fixture & test-forms]
  `(let [test-code# (fn [] ~@test-forms)]
     (~fixture test-code#)))


(def fake-backend-resources
  {"/some_route" {"version" 21, "some-data" {"a" 12}},
   "/another-route" {"version" 21, "other-data" [1 2 3]}})

(def fake-routes-config
  (zipmap (keys fake-backend-resources) (repeat {})))

(defn fake-dispatch-state-update
  "Simulates a fake backend query, without the diffing part."
  [base-backend-address path headers client-version]
  (if-let [fake-response (fake-backend-resources path)]
    {:status 200 :body fake-response}
    {:status 404 :body "Not Found on the backend"}))

(deftest test-handler
  (let [configured-handler (partial handler fake-routes-config {})]
    (with-redefs [diffing-proxy.diffing/dispatch-state-update
                  fake-dispatch-state-update]

      (testing "main route"
        (are [method] (= (:status (configured-handler (mock/request method "/")))
                         200)
             :get :head))

      (testing "configured backend routes"
        (doseq [[route expected-response-body] fake-backend-resources]
          (let [get-response (configured-handler (mock/request :get route))
                head-response (configured-handler (mock/request :head route))]
            (is (= (:status get-response) 200))
            (is (= (:body get-response) expected-response-body))
            (is (= (:status head-response) 200))
            (is (= (:body head-response) "")))))

      (testing "not-found"
        (are [path method]
             (= (:status (configured-handler (mock/request method path))) 404)
             "/what" :get
             "/what" :head
             "/what" :put
             "/what" :post
             "/wuut" :delete))

      (testing "wrong request methods"
        (let [existing-configured-route (first (keys fake-backend-resources))]
          (are [path method]
               (= (:status (configured-handler (mock/request method path))) 405)
               "/" :post
               "/" :delete
               "/" :brew-tea
               existing-configured-route :delete
               existing-configured-route :post
               existing-configured-route :put))))))


(deftest test-parse-version
  (are [version-string result] (= (parse-version version-string) result)
       "1" 1
       "1121" 1121
       "-1121" -1121
       "" nil
       nil nil
       "ad" :invalid
       "ðə→óœπęæ" :invalid
       "12ad" :invalid
       "-12al" :invalid
       "-12e1" :invalid))

(deftest test-backend-address
  (are [backend-config url] (= (backend-address backend-config) url)
       {} "http://localhost:8000"
       {:scheme "https"} "https://localhost:8000"
       {:host "1.2.3.4"} "http://1.2.3.4:8000"
       {:port 80} "http://localhost:80"

       {:scheme "http-over-avian-carriers" :host "here" :port "1337"}
       "http-over-avian-carriers://here:1337"))

(deftest test-dispatch-state-update
  (testing "when there are no cached versions, the response is a full state
           update, unless the client claims to hold the latest version,
           in which case it's expected to be an empty diff"
    (let [latest-state {"version" 10, "val" [1 2 3]}]
      (doseq [[version-held-by-client expected-response]
              [[nil latest-state]
               [5 latest-state]
               [10 (diff {} {})]]]
        (with-redefs
          [diffing-proxy.diffing/cached-versions (atom {})
           diffing-proxy.diffing/query-backend (constantly latest-state)]
          (let [response (dispatch-state-update
                           "some-backend-addr" "/path" {} version-held-by-client)]
            (is (= (:status response) 200))
            (is (= (:body response) (generate-string expected-response)))
            (is (= (get-in @cached-versions ["/path" 10]) latest-state)
                "The new response should be cached."))))))

  (testing "when the version held by the client is cached, the client is served
           a diff between the version it already knows and the latest one"
    (let [sample-versions (sorted-map 7 {"version" 7, "data" [1]},
                                      13 {"version" 13, "data" [1 2 3 4]},
                                      17 {"version" 17, "data" [1 2 3 4 5 6]})]
      (doseq [[client-state-number expected-diff]
              [[7 (diff (sample-versions 7) (sample-versions 17))]
               [13 (diff (sample-versions 13) (sample-versions 17))]]]
        (with-redefs
          [diffing-proxy.diffing/cached-versions (atom {"/path" sample-versions})
           diffing-proxy.diffing/query-backend (constantly (sample-versions 17))]
          (let [response (dispatch-state-update "http://some-backend-addr"
                                                "/path" {} client-state-number)]

            (is (= (:status response) 200))
            (is (= (:body response) (generate-string expected-diff)))
            (is (= (@cached-versions "/path") sample-versions)
                "The cache is not expected to change, since the version
                returned by the backend was already cached."))))))

  (testing "backend responding with 404"
    (with-fake-routes {"http://backend.local/pluto"
                       (constantly {:status 404, :body "Not Found"})}
      (is (= (dispatch-state-update "http://backend.local" "/pluto" {} nil)
             {:status 404, :body "Not Found on the backend"}))))

  (testing "backend responding with 5xx"
    (doseq [backend-status [500 501 503]]
      (with-fake-routes {"http://backend.local/pluto"
                         (constantly {:status backend-status :body "Error."})}
        (is (= (dispatch-state-update "http://backend.local" "/pluto" {} nil)
               {:status 502
                :body (str "Bad Gateway\nBackend responded with "
                           backend-status)})))))

  (testing "backend responding with an invalid JSON"
    (with-fake-routes {"http://backend.local/pluto"
                       (constantly {:status 200, :body "<html>"})}
      (is (= (dispatch-state-update "http://backend.local" "/pluto" {} nil)
             {:status 502
              :body "Bad Gateway\nBackend responded with an invalid JSON."}))))

  (testing "cannot connect to the backend"
    ; there should be nothing listening on that port
    (is (= (dispatch-state-update "http://localhost:43232" "/sth"
                                  {:conn-timeout 200} nil)
           {:status 503
            :body "Service Unavailable\nConnection refused."})))

  (testing "timeout for establishing backend connection"
    ; the 10.255.255.1 address is unroutable
    (is (= (dispatch-state-update "http://10.255.255.1" "/sth"
                                  {:conn-timeout 200} nil)
           {:status 504
            :body "Gateway Timeout\nBackend unreachable."})))

  (test-with-fixture
    mock-server
    (testing "timeout for reading from the backend connection socket"
      (is (= (dispatch-state-update
               (str "http://127.0.0.1:" port-to-occupy) "/timeout"
               {:socket-timeout 200} nil)
             {:status 504
              :body "Gateway Timeout\nBackend responding too slowly."})))))
