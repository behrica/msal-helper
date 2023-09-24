(ns msahelper.azure
  (:require
   [clj-http.client :as client]))

   
   
(defn do-client-credentials[tenant-id resource client-id client-secret]
  (->
   (client/post (str "https://login.microsoftonline.com/" tenant-id  "/oauth2/token")
                {:form-params {:resource  resource
                               :client_id client-id
                               :client_secret client-secret
                               :grant_type "client_credentials"}
                               
                 :as :json})
                
   (get-in [:body :access_token])))
   




(defn call-ms-graph[f suffix access-token]
  (:body (f (str "https://graph.microsoft.com/v1.0/" suffix)
            {:headers {:Authorization (str "Bearer " access-token)
                       :Accept "application/json"}

             :as :json})))
               
              

(defn me [access-token]
  (call-ms-graph client/get  "me" access-token))
