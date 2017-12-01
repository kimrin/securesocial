/**
 * Copyright 2015 Mikael Vallerie
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package securesocial.core.providers

import play.api.libs.json.{ Reads, Json, JsValue }
import play.api.{ Configuration, Environment }
import play.api.libs.ws.{ WSAuthScheme, WSResponse, WSClient }
import securesocial.core
import securesocial.core._
import securesocial.core.services.{ CacheService, HttpService, RoutesService }

import scala.concurrent.{ ExecutionContext, Future }
import ChatworkProvider.{ ErrorResponse, UserResponse }
import ChatworkProvider._

class ChatworkOAuth2Client(
    httpService: HttpService, settings: OAuth2Settings)(implicit executionContext: ExecutionContext) extends OAuth2Client.Default(httpService, settings)(executionContext) {
  override def exchangeCodeForToken(code: String, callBackUrl: String, builder: OAuth2InfoBuilder): Future[OAuth2Info] = {
    val params = Map(
      OAuth2Constants.GrantType -> Seq(OAuth2Constants.AuthorizationCode),
      OAuth2Constants.Code -> Seq(code),
      OAuth2Constants.RedirectUri -> Seq(callBackUrl)
    ) ++ settings.accessTokenUrlParams.mapValues(Seq(_))
    httpService.url(settings.accessTokenUrl)
      .withAuth(settings.clientId, settings.clientSecret, WSAuthScheme.BASIC)
      .post(params).map(builder)
  }

}
/**
 * A Chatwork provider
 *
 */
class ChatworkProvider(routesService: RoutesService,
  cacheService: CacheService,
  client: OAuth2Client)(implicit val configuration: Configuration, val playEnv: Environment)
    extends OAuth2Provider.Base(routesService, client, cacheService) {
  override val id = ChatworkProvider.Chatwork
  private val Logger = play.api.Logger("securesocial.core.providers.ChatworkProvider")

  override def fillProfile(info: OAuth2Info): Future[BasicProfile] = {
    val accessToken = info.accessToken
    client.httpService.url(ChatworkProvider.Api).withHeaders("Authorization" -> s"Bearer $accessToken").get().map { response =>
      response.status match {
        case 200 =>
          val data = response.json
          val userId = (data \ ChatworkId).toString
          val fullName = (data \ Name).asOpt[String]
          val email = (data \ Email).asOpt[String]
          BasicProfile(id, userId, None, None, fullName, email, None, authMethod, None, Some(info))
        case _ =>
          Logger.error("[securesocial] Chatwork account info request returned error: " + response.body)
          throw new AuthenticationException()
      }
    } recover {
      case e =>
        Logger.error("[securesocial] error retrieving profile information from Chatwork", e)
        throw new AuthenticationException()
    }
  }
}

object ChatworkProvider {
  val Api = "https://api.chatwork.com/v2/me"
  val Chatwork = "chatwork"
  val AccountId = "account_id"
  val ChatworkId = "chatwork_id"
  val Name = "name"
  val Url = "url"
  val Email = "mail"

  case class ErrorResponse(
    message: String,
    detail: String,
    id: Option[String])

  case class UserResponse(
    uuid: String,
    display_name: String,
    username: String)
}
