(ns msal-helper.silent-auth-test


  (:require [msal-helper.msal :as msal]
            [clojure.test :refer [deftest is]]
            [msal-helper.azure :as azure]))

            


(def ^:dynamic *username* nil)
(def ^:dynamic *tenant-id* nil)
(def ^:dynamic *client-id* nil)
(def ^:dynamic *azure-kv-secret-url* nil)
(def ^:dynamic *azure-kv-secret-client-id* nil)
(def ^:dynamic *azure-kv-secret-client-secret* nil)
(def ^:dynamic *expected-email* nil)
(def ^:dynamic *scope* nil)




(deftest silent-auth
  (let [auth-result
        (msal/try-silent-authenticate
         *username*
         *tenant-id*
         *client-id*
         *scope*
         (msal/azure-kv-cache

          *tenant-id*
          *azure-kv-secret-url*
          *azure-kv-secret-client-id*
          *azure-kv-secret-client-secret*))]

          

    (is (= *expected-email*
           (:mail (azure/me (.accessToken auth-result)))))))


