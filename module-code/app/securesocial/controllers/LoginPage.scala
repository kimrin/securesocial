/**
 * Copyright 2012-2014 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
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
package securesocial.controllers

import javax.inject.Inject

import play.api.{ Configuration, Application, Environment }
import play.filters.csrf.CSRFAddToken
import securesocial.core._
import securesocial.core.providers.UsernamePasswordProvider
import securesocial.core.utils._

import scala.concurrent.Future

/**
 * A default Login controller that uses BasicProfile as the user type.
 *
 * @param env An environment
 */
class LoginPage @Inject() (override implicit val env: RuntimeEnvironment, override val configuration: Configuration, override val playEnv: Environment, val CSRFAddToken: CSRFAddToken) extends BaseLoginPage

/**
 * The trait that defines the login page controller
 */
trait BaseLoginPage extends SecureSocial {
  private val logger = play.api.Logger("securesocial.controllers.LoginPage")

  /**
   * The property that specifies the page the user is redirected to after logging out.
   */
  val onLogoutGoTo = "securesocial.onLogoutGoTo"

  implicit val CSRFAddToken: CSRFAddToken

  /**
   * Renders the login page
   *
   * @return
   */
  def login = CSRFAddToken {
    UserAwareAction { implicit request =>
      if (request.user.isDefined) {
        // if the user is already logged in, a referer is set and we handle the
        // referer the same way as an OriginalUrl in the session, we redirect back
        // to this URL. Otherwise, just redirect to the application's landing page
        val to = (if (enableRefererAsOriginalUrl) {
          SecureSocial.refererPathAndQuery
        } else {
          None
        }).getOrElse(ProviderControllerHelper.landingUrl)
        logger.debug("User already logged in, skipping login page. Redirecting to %s".format(to))
        Redirect(to)
      } else {
        if (enableRefererAsOriginalUrl) {
          SecureSocial.withRefererAsOriginalUrl(Ok(env.viewTemplates.getLoginPage(UsernamePasswordProvider.loginForm)))
        } else {
          Ok(env.viewTemplates.getLoginPage(UsernamePasswordProvider.loginForm))
        }
      }
    }
  }

  /**
   * Logs out the user by clearing the credentials from the session.
   * The browser is redirected either to the login page or to the page specified in the onLogoutGoTo property.
   *
   * @return
   */
  def logout = UserAwareAction.async {
    implicit request =>
      val redirectTo = Redirect(configuration.getString(onLogoutGoTo).getOrElse(env.routes.loginPageUrl))
      val result = for {
        user <- request.user
        authenticator <- request.authenticator
      } yield {
        redirectTo.discardingAuthenticator(authenticator).map {
          _.withSession(Events.fire(new LogoutEvent(user)).getOrElse(request.session))
        }
      }
      result.getOrElse {
        Future.successful(redirectTo)
      }
  }
}
