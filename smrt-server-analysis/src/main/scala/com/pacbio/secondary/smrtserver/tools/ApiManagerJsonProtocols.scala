package com.pacbio.secondary.smrtserver.tools

import spray.json._
import fommil.sjs.FamilyFormats
import shapeless.{cachedImplicit, Typeable}

import org.wso2.carbon.apimgt.rest.api.store
import org.wso2.carbon.apimgt.rest.api.publisher

object ApiManagerJsonProtocols extends DefaultJsonProtocol with FamilyFormats {

  // client reg
  implicit val clientRegistrationRequestFormat = jsonFormat6(ClientRegistrationRequest)
  implicit val clientRegistrationResponseFormat = jsonFormat7(ClientRegistrationResponse)

  // oauth
  implicit val oauthTokenFormat = jsonFormat5(OauthToken)

  // API Manager store
  implicit val applicationKeyEnumFormat = new EnumJsonFormat(store.models.ApplicationKeyEnums.KeyType)
  implicit val applicationKeyGenerateRequestEnumFormat = new EnumJsonFormat(store.models.ApplicationKeyGenerateRequestEnums.KeyType)
  implicit val documentEnumTypeFormat = new EnumJsonFormat(store.models.DocumentEnums.`Type`)
  implicit val documentEnumSourceTypeFormat = new EnumJsonFormat(store.models.DocumentEnums.SourceType)
  implicit val subscriptionEnumFormat = new EnumJsonFormat(store.models.SubscriptionEnums.Status)
  implicit val tierLevelForomat = new EnumJsonFormat(store.models.TierEnums.TierLevel)
  implicit val tierEnumFormat = new EnumJsonFormat(store.models.TierEnums.TierPlan)

  implicit val tokenFormat = jsonFormat3(store.models.Token)
  implicit val applicationInfoFormat = jsonFormat7(store.models.ApplicationInfo)
  implicit val applicationListFormat = jsonFormat4(store.models.ApplicationList)
  implicit val applicationKeyFormat = jsonFormat6(store.models.ApplicationKey)
  implicit val applicationFormat = jsonFormat9(store.models.Application)

  // API Manager publisher
  implicit val visibilityFormat = new EnumJsonFormat(publisher.models.APIEnums.Visibility)
  implicit val subAvailFormat = new EnumJsonFormat(publisher.models.APIEnums.SubscriptionAvailability)
  implicit val endpointSecurityEnumFormat = new EnumJsonFormat(publisher.models.API_endpointSecurityEnums.`Type`)

  override implicit def productHint[T: Typeable] = new ProductHint[T] {
    override def nulls = AlwaysJsNull
  }

  implicit val apiListFormat: RootJsonFormat[publisher.models.APIList] = cachedImplicit
  implicit val apiFormat: RootJsonFormat[publisher.models.API] = cachedImplicit
}
