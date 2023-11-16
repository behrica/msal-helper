(ns msal-helper.msal
  (:require
   [msal-helper.azure :as azure]
   [clojure.string :as str])
   
  (:import
   [com.microsoft.aad.msal4j
    DeviceCodeFlowParameters
    ITokenCacheAccessAspect
    PublicClientApplication
    SilentParameters]
   [java.util.function Consumer]))


(defn azure-kv-cache [tenant-id
                      masl4j-token-cache-kv-secret-url
                      client-id-for-kv
                      client-secret-for-kv]
  {:get-data-fn (fn []
                  (let [access-token-for-key-vault (azure/do-client-credentials  tenant-id "https://vault.azure.net" client-id-for-kv client-secret-for-kv)]
                    (azure/get-secret-data masl4j-token-cache-kv-secret-url access-token-for-key-vault)))
   :set-data-fn (fn [data]
                  (let [access-token-for-key-vault (azure/do-client-credentials  tenant-id "https://vault.azure.net" client-id-for-kv client-secret-for-kv)]
                   (azure/set-secret-data masl4j-token-cache-kv-secret-url data access-token-for-key-vault)))})



(defn- build-app [tenant-id
                  client-id
                  cache]
                  
  (let [token-cache-aspect (reify ITokenCacheAccessAspect
                             (beforeCacheAccess [this iTokenCacheAccessContext]
                               ;; (println "get data from cache")
                               (let [data ((:get-data-fn cache))]
                                 (.. iTokenCacheAccessContext tokenCache (deserialize data))))
                             (afterCacheAccess [this iTokenCacheAccessContext]
                               (let [data (.. iTokenCacheAccessContext tokenCache serialize)]
                                 ((:set-data-fn cache) data))))]

    (.. (PublicClientApplication/builder client-id)
        (setTokenCacheAccessAspect token-cache-aspect)
        (authority (format "https://login.microsoftonline.com/%s/" tenant-id))
        build)))

(defn device-code-interactive-login
  "Authenticates interactively via device code flow and populates the cache. (a given Azure Key Vault)
  See function `try-silent-authenticate` for parameters.
  It returns a `java.util.concurrent.CompletableFuture` object which on de-referencing it
  will block and print on console the  code to use interaticely with Microsofts
  device code endpoint: https://microsoft.com/devicelogin
  The dereferenced value will be of type `com.microsoft.aad.adal4j.AuthenticationResult` and containing the access codes.

  After sucessfull interactive authentication the token data will be cached
  and following calls to `try-silent-authenticate` work without user intercation.
  "
  [tenant-id
   client-id
   scope
   cache]
  (let [app (build-app tenant-id client-id cache)
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

  It uses the given Azure Key Vault Secret as token cache.
  For this it reads token cache data from the specified Azure key vault secret `masl4j-token-cache-key-vault-secret-url`.
  It uses `client-id-for-kv` and `client-secret-for-kv` to authenticate to the key vault, so the Azure application `client-id-for-kv`
  needs to have adequate  permission on the key vault to read and write the secret specified by `masl4j-token-cache-key-vault-secret-url`

  The user specified by `user-name` need to be in the cached token data.
  `tenant-id` needs to be the tenant of the user-name
  `client-id` is the app to be authenticated for with the scope `scope`

  It returns on sucess an object of type `com.microsoft.aad.adal4j.AuthenticationResult`
  which contains the access token for the given user (if found in cached data)
  "
  [user-name
   tenant-id
   client-id
   scope
   cache]

  (let [app (build-app
             tenant-id
             client-id
             cache)

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
