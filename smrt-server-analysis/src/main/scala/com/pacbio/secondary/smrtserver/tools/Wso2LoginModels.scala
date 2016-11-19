package com.pacbio.secondary.smrtserver.tools

// misc WSO2 models not included in the API manager store or publisher APIs
case class ClientRegistrationRequest(callbackUrl: String, clientName: String, tokenScope: String = "Production", owner: String, grantType: String = "password refresh_token", saasApp: Boolean)

case class ClientRegistrationResponse(appOwner: Option[String], clientName: Option[String], callBackURL: String, isSaasApplication: Boolean, jsonString: String, clientId: String, clientSecret: String)

case class OauthToken(access_token: String, refresh_token: String, scope: String, token_type: String, expires_in: Int)
