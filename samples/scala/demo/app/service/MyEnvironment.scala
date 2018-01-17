package service

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

import akka.actor.ActorSystem
import com.google.inject.{ Inject, Singleton }
import controllers.CustomRoutesService
import play.api.i18n.{ Messages, MessagesApi }
import play.api.libs.mailer.MailerClient
import play.api.libs.ws.WSClient
import play.api.mvc.RequestHeader
import play.api.{ Configuration, Environment }
import play.api.cache.{ AsyncCacheApi }
import securesocial.core.authenticator.{ CookieAuthenticatorConfigurations, HttpHeaderAuthenticatorConfigurations }
import securesocial.core.providers.UsernamePasswordProviderConfigurations
import securesocial.core.{ BasicProfile, RuntimeEnvironment, ServiceInfoHelper }

class MyEnvironment @Inject() (implicit val configuration: Configuration, implicit val playEnv: Environment, val cacheApi: AsyncCacheApi, val messagesApi: MessagesApi, val WS: WSClient, val mailerClient: MailerClient, val actorSystem: ActorSystem) extends RuntimeEnvironment.Default {
  override type U = DemoUser
  override implicit val executionContext = play.api.libs.concurrent.Execution.defaultContext
  override lazy val routes = new CustomRoutesService()
  override lazy val userService: InMemoryUserService = new InMemoryUserService()
  override lazy val eventListeners = List(new MyEventListener())
  override val cookieAuthenticatorConfigurations = new CookieAuthenticatorConfigurations.Default()
  override val httpHeaderAuthenticatorConfigurations = new HttpHeaderAuthenticatorConfigurations.Default()
  val serviceInfoHelper = new ServiceInfoHelper.Default
  val usernamePasswordProviderConfigurations = new UsernamePasswordProviderConfigurations.Default
}

/*
class MyBasicEnvironment @Inject() (val env: MyEnvironment[U]) extends RuntimeEnvironment.Default[U] {
  override lazy val userService: InMemoryUserService = env.userService
}*/
