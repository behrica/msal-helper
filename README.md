# msal-helper

Just a bit of re-usable code to obtain and cache identity tokens comming from
Azure Identtity.
It uses [MSAL4j](https://github.com/AzureAD/microsoft-authentication-library-for-java)

It supports currently one (= my) scenario:
  - public client app
  - cache tokens in a Azure Key vault
  - allows to "init the cache" via the device code flow and commandline interactivity
  - after cache initialisation tokens can be obtained without need for user interactivity
  - the token gets "refreshed" on each use which allow for long-term non-interatice authentication
  
