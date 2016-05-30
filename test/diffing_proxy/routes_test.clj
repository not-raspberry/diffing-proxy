(ns diffing-proxy.routes-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [diffing-proxy.routes :refer :all]
            [diffing-proxy.diffing :refer :all]))

(def fake-backend-resources
  {"/some_route" {"version" 21, "some-data" {"a" 12}},
   "/another-route" {"version" 21, "other-data" [1 2 3]}})

(def fake-routes-config
  (zipmap (keys fake-backend-resources) (repeat {})))

(defn fake-dispatch-state-update
  "Simulates a fake backend query, without the diffing part."
  [base-backend-address path client-version]
  (if-let [fake-response (fake-backend-resources path)]
    {:status 200 :body fake-response}
    {:status 404 :body "Not Found on the backend"}))

(deftest test-routes
  (let [routes (proxy-routes fake-routes-config {})]
    (with-redefs [diffing-proxy.diffing/dispatch-state-update
                  fake-dispatch-state-update]

      (testing "main route"
        (let [response (routes (mock/request :get "/"))]
          (is (= (:status response) 200))))

      (testing "configured backend routes"
        (doseq [[route expected-response-body] fake-backend-resources]
          (let [response (routes (mock/request :get route))]
            (is (= (:status response) 200))
            (is (= (:body response) expected-response-body)))))

      (testing "not-found route"
        (let [response (routes (mock/request :get "/invalid"))]
          (is (= (:status response) 404)))))))

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