(ns msal-helper.azure
  (:require
   [cheshire.core :as json]
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
   


(defn get-secret-data [secret-url access-token]
  (->
   (client/get (format  "%s?api-version=7.0" secret-url)
               {:headers {:Authorization (str "Bearer " access-token)}
                :as :json})
   :body
   :value))

(defn set-secret-data [secret-url data access-token]
  (client/put (format  "%s?api-version=7.0" secret-url)
              {:form-params {:value data}
               :headers {:Authorization (str "Bearer " access-token)}
               :content-type :json
               :as :json}))



(defn call-ms-graph[f suffix access-token]
  (:body (f (str "https://graph.microsoft.com/v1.0/" suffix)
            {:headers {:Authorization (str "Bearer " access-token)
                       :Accept "application/json"}

             :as :json})))
               
              

(defn me [access-token]
  (call-ms-graph client/get  "me" access-token))

(comment
  (defn send-mail [access-token to subject body-text]
    (client/post (str "https://graph.microsoft.com/v1.0/" "me/SendMail")

                 {:headers {:Authorization (str "Bearer " access-token)
                            :Accept "application/json"}

                  :as :json
                  :content-type :json
                  :body (json/encode {:Message {:subject subject
                                                :body  {  :contentType "Text"
                                                        :content body-text}
                                                :toRecipients [ {:emailAddress {:address to}}]}})})))
