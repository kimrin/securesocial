/**
 * Copyright 2012-2014 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package service

import akka.actor.ActorSystem
import com.google.inject.Inject
import play.api.{ Configuration, Environment }
import play.api.cache.{ AsyncCacheApi, CacheApi }
import play.api.i18n.MessagesApi
import play.api.libs.mailer.MailerClient
import play.api.libs.ws.WSClient
import securesocial.core.{ RuntimeEnvironment, ServiceInfoHelper }
import securesocial.core.authenticator.{ CookieAuthenticatorConfigurations, HttpHeaderAuthenticatorConfigurations }
import securesocial.core.providers.UsernamePasswordProviderConfigurations
import securesocial.core.services.UserService

class MyEnvironment @Inject() ()(implicit val configuration: Configuration, implicit val playEnv: Environment, val cacheApi: AsyncCacheApi, val messagesApi: MessagesApi, val WS: WSClient, val mailerClient: MailerClient, val actorSystem: ActorSystem) extends RuntimeEnvironment.Default {
  type U = DemoUser
  override val userService: UserService[U] = new InMemoryUserService()

  val cookieAuthenticatorConfigurations = new CookieAuthenticatorConfigurations.Default()
  val httpHeaderAuthenticatorConfigurations = new HttpHeaderAuthenticatorConfigurations.Default()
  val serviceInfoHelper = new ServiceInfoHelper.Default
  val usernamePasswordProviderConfigurations = new UsernamePasswordProviderConfigurations.Default

}