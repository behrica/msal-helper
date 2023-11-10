(ns msahelper.msa
  (:require
   [msahelper.azure :as azure]
   [clojure.string :as str])


  (:import
   [com.microsoft.aad.msal4j
    DeviceCodeFlowParameters
    ITokenCacheAccessAspect
    PublicClientApplication
    SilentParameters]
   [java.util.function Consumer]))





(defn build-app [client-id
                 masl4j-token-cache-secret-url
                 access-token-for-key-vault
                 tenant-id]
  (let [token-cache-aspect (reify ITokenCacheAccessAspect
                             (beforeCacheAccess [this iTokenCacheAccessContext]

                               (let [data (azure/get-secret-data masl4j-token-cache-secret-url access-token-for-key-vault)]
                                 (.. iTokenCacheAccessContext tokenCache (deserialize data))))


                             (afterCacheAccess [this iTokenCacheAccessContext]
                               (let [data (.. iTokenCacheAccessContext tokenCache serialize)]
                                 (println "set data in cache !!!")
                                 (azure/set-secret-data masl4j-token-cache-secret-url data access-token-for-key-vault))))]
    (.. (PublicClientApplication/builder client-id)
        (setTokenCacheAccessAspect token-cache-aspect)
        (authority (format "https://login.microsoftonline.com/%s/" tenant-id))
        build)))

(defn device-code-interactive-login
  [tenant-id
   masl4j-token-cache-kv-secret-url
   client-id-for-kv
   client-secret-for-kv
   client-id
   scope]
  (let [access-token-for-key-vault (azure/do-client-credentials  tenant-id "https://vault.azure.net" client-id-for-kv client-secret-for-kv)
        app (build-app client-id masl4j-token-cache-kv-secret-url access-token-for-key-vault tenant-id)
        consumer
        (reify Consumer
          (accept [this t]
            (println :accept t)
            (println (.message t)))

          (andThen [this c]
            (println :c c)))

        flow-params
        (..
         (DeviceCodeFlowParameters/builder
          scope
          consumer)
         (tenant tenant-id)
         build)]
    (.acquireToken app flow-params)))

 



(defn try-silent-authenticate
  "Tries to authenticate silently with Microsoft Identity without user interaction.
  For this it reads token cache data from the specified Azure key vault `masl4j-token-cache-key-vault-secret-url`.
  It uses `client-id` nad `client-secret` as autnetiction for the key vaul, so teh app `client-id` needs to have adequate
  permission on the key vault to read and write the seccret specified by `masl4j-token-cache-key-vault-secret-url`
  The user specified by `user-name` need to be in the cached token data.


  "
  [user-name
   tenant-id
   masl4j-token-cache-kv-secret-url
   client-id-for-kv
   client-secret-for-kv
   client-id
   scope]

  (let [access-token-for-key-vault (azure/do-client-credentials tenant-id "https://vault.azure.net" client-id-for-kv client-secret-for-kv)
        app (build-app client-id masl4j-token-cache-kv-secret-url access-token-for-key-vault tenant-id)

        accounts
        (iterator-seq
         (.. app getAccounts join iterator))

        account-map
        (zipmap
         (map  #(str/lower-case (.username %)) accounts)
         accounts)


        account (get account-map user-name)
        silent-parameters
        (.. (SilentParameters/builder scope account)
            (forceRefresh true)
            build)]
    (.. (.acquireTokenSilently app silent-parameters) join)))
