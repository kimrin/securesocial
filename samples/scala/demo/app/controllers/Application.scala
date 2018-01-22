/**
 * Copyright 2012 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
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
package controllers

import javax.inject.Inject

import akka.stream.Materializer
import com.google.inject.Module
import play.api.http.{ FileMimeTypes, HttpErrorHandler, ParserConfiguration }
import play.api.i18n.MessagesApi
import play.api.libs.Files.TemporaryFileCreator
import securesocial.core._
import service.{ DemoUser, MyEnvironment, MyEventListener }
import play.api.{ Configuration, Environment }
import play.api.mvc._

class Application @Inject() (implicit val env: RuntimeEnvironment,
  val configuration: Configuration,
  val playEnv: Environment,
  val controllerComponents: ControllerComponents,
  val action: DefaultActionBuilder,
  val parser: BodyParsers.Default,
  val messagesApi: MessagesApi,
  val fileMimeTypes: FileMimeTypes,
  val config: ParserConfiguration,
  val errorHandler: HttpErrorHandler,
  val materializer: Materializer,
  val temporaryFileCreator: TemporaryFileCreator)
    extends securesocial.core.SecureSocial {
  def index = SecuredAction { implicit request =>
    Ok(views.html.index(request.user.asInstanceOf[DemoUser].main))
  }

  // a sample action using an authorization implementation
  def onlyTwitter = SecuredAction(WithProvider("twitter")) { implicit request =>
    Ok("You can see this because you logged in using Twitter")
  }

  def linkResult = SecuredAction { implicit request =>
    Ok(views.html.linkResult(request.user.asInstanceOf[DemoUser]))
  }

  /**
   * Sample use of SecureSocial.currentUser. Access the /current-user to test it
   */
  def currentUser = Action.async { implicit request =>
    SecureSocial.currentUser.map { maybeUser =>
      val userId = maybeUser.map(_.asInstanceOf[DemoUser].main.userId).getOrElse("unknown")
      Ok(s"Your id is $userId")
    }
  }

  // An Authorization implementation that only authorizes uses that logged in using twitter
  case class WithProvider(provider: String) extends Authorization[env.U] {
    def isAuthorized(user: env.U, request: RequestHeader) = {
      val demoUser = user.asInstanceOf[DemoUser]
      demoUser.main.providerId == provider
    }
  }
}
