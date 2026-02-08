(ns my-project.routes
  (:require [ring.util.response :as response]
            [my-project.handlers :as handlers]))

(def routes
  [["/" {:name ::home
         :get {:handler handlers/home-handler}
         :responses {200 {:body string?}}}]
   ["/health" {:name ::health-check
               :get {:handler (fn [_] (response/response "OK"))}}]
   ["/inspect" {:name ::inspect
                :get {:handler handlers/inspect-handler}}]])
