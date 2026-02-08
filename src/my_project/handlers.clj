(ns my-project.handlers
  (:require [reitit-extras.core :as reitit-extras]
            [ring.util.response :as response]
            [my-project.views :as views]))

(defn default-handler
  [error-text status-code]
  (fn [_]
    (-> (views/error-page error-text)
        (reitit-extras/render-html)
        (response/status status-code))))

(defn home-handler
  [_]
  (reitit-extras/render-html views/home-page))

(defn inspect-handler
  [_]
  (let [runtime (Runtime/getRuntime)
        total (.totalMemory runtime)
        free (.freeMemory runtime)
        used (- total free)
        max-mem (.maxMemory runtime)]
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body (str "JVM Memory Info\n"
                "Total: " (quot total 1048576) " MB\n"
                "Used:  " (quot used 1048576) " MB\n"
                "Free:  " (quot free 1048576) " MB\n"
                "Max:   " (quot max-mem 1048576) " MB\n")}))
