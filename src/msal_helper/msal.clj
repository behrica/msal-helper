(ns msal-helper.msal
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [msal-helper.azure :as azure])

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
  "
  Returns two fns which know how to read/write a String (usualy serialized json) to a Azure Key Vault secret.
  These we call `the cache`
  
Parameters are:
  
`masl4j-token-cache-kv-secret-url` : The concrete secret URL in an Azure Key Vault to write to in form of https://a-key-vault.vault.azure.net/secrets/a-secret-name
`client-id-for-kv` : an app-id which has permissions to read and write the secret in `masl4j-token-cache-kv-secret-url`
`client-secret-for-kv`  : app secret of `client-id-for-kv`
`tenant-id` :  Tenant of the Key Vault and client-id used 
  
  
  The returned cache and its fns are used by `device-code-interactive-login` and  `try-silent-authenticate` 
  as data accessor in a ITokenCacheAccessAspect during authentication"
  {:get-data-fn (fn []
                  (let [access-token-for-key-vault (azure/do-client-credentials  tenant-id "https://vault.azure.net" client-id-for-kv client-secret-for-kv)]
                    (azure/get-secret-data masl4j-token-cache-kv-secret-url access-token-for-key-vault)))
   :set-data-fn (fn [data]
                  (let [access-token-for-key-vault (azure/do-client-credentials  tenant-id "https://vault.azure.net" client-id-for-kv client-secret-for-kv)]
                    (azure/set-secret-data masl4j-token-cache-kv-secret-url data access-token-for-key-vault)))})

(defn local-file-cache [file-path]
  {:get-data-fn (fn []
                  (when (.exists (io/file file-path))
                    (slurp file-path)
                    )
                  
                  )
   :set-data-fn (fn [data]
                  (spit file-path data)
                  )}
  )

(defn- build-app [tenant-id
                  client-id
                  cache]

  (let [token-cache-aspect (reify ITokenCacheAccessAspect
                             (beforeCacheAccess [this iTokenCacheAccessContext]
                               (println "retrieve data from cache")
                               (let [data ((:get-data-fn cache))]
                                 (.. iTokenCacheAccessContext tokenCache (deserialize data))))
                             (afterCacheAccess [this iTokenCacheAccessContext]
                               (println "write data to cache")
                               (let [data (.. iTokenCacheAccessContext tokenCache serialize)]
                                 ((:set-data-fn cache) data))))]

    (.. (PublicClientApplication/builder client-id)
        (setTokenCacheAccessAspect token-cache-aspect)
        (authority (format "https://login.microsoftonline.com/%s/" tenant-id))
        build)))

(defn device-code-interactive-login
  "Authenticates interactively via device code flow and refreshes the cache. 

   `tenant-id` :  Tenant of the client-id used for authentication
   `client-id` : Client-id (or app-id) which will be used for authentication
   `scope`     : Set of scopes the access token should allow and which the user will eventualy asked to consent to
   `cache`     : A cache created by fn `azure-kv-cache`. It is used to read and write the auth cache data


  It returns a `java.util.concurrent.CompletableFuture` object which on de-referencing it
  will block and print on console the  code to use interaticely with Microsofts
  device code endpoint: https://microsoft.com/devicelogin
  The dereferenced value will be of type `com.microsoft.aad.adal4j.AuthenticationResult` and containing the access codes.

  After sucessfull interactive authentication the token data will be cached freshly,
  and following calls to `try-silent-authenticate` work without user intercation.

  See here: https://learn.microsoft.com/en-us/entra/msal/java/getting-started/device-code-flow
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

  It uses the given cache (as created by fn azure-kv-cache)  as token cache.
  It reads token cache data from the cache, which allows to silently authenticate again Azure AD,
  given that the data in the cache is not expired.

  `user-name` : Email of user to authenticate, which need to be in the cached token data.
              An initial call to `device-code-interactive-login` makes this sure.
  `tenant-id` : Tenant of the user and client-id use for authentication
  `client-id` : Client-id (or app-id) which will be used for authentication
  `scopes` : Set of scopes the access token should allow to use 
  `cache` : A  cache created by fn `azure-kv-cache`. It is used to read and write the auth cache data

  So using the function refreshes the cached authentication information inside the given cache as a side-effect
  
  It returns on sucess an object of type `com.microsoft.aad.adal4j.AuthenticationResult`
  which contains the access token for the given user (if found in cached data)

  The function can fail at any moment, when the cached data expired or the 
  user did not yet consent to the scopes in `scopes`.
  In this case the fn `device-code-interactive-login` can be used to refresh the cached data,
  and to trigger re-consent if needed.
  This which requires a user present and it will use the device login authentication.

  If this functions is called regulary, the cache data will not expire.
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