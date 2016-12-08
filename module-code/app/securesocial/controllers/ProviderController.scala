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
package securesocial.controllers

import javax.inject.Inject

import play.api.{ Environment, Configuration, Application }
import play.api.i18n.Messages
import play.api.mvc._
import securesocial.core._
import securesocial.core.authenticator.CookieAuthenticator
import securesocial.core.providers.UsernamePasswordProvider
import securesocial.core.services.SaveMode
import securesocial.core.utils._

import scala.concurrent.Future

/**
 * A default controller that uses the BasicProfile as the user type
 */
class ProviderController @Inject() (implicit val env: RuntimeEnvironment, val configuration: Configuration, val playEnv: Environment)
  extends BaseProviderController

/**
 * A trait that provides the means to authenticate users for web applications
 */
trait BaseProviderController extends SecureSocial {

  import securesocial.controllers.ProviderControllerHelper.{ logger, toUrl }

  /**
   * The authentication entry point for GET requests
   *
   * @param provider The id of the provider that needs to handle the call
   */
  def authenticate(provider: String, redirectTo: Option[String] = None, scope: Option[String] = None, saveMode: Option[String], miscParam: Option[String]) = {
    handleAuth(provider, redirectTo, scope, saveMode, miscParam)
  }

  /**
   * The authentication entry point for POST requests
   *
   * @param provider The id of the provider that needs to handle the call
   */
  def authenticateByPost(provider: String, redirectTo: Option[String] = None, scope: Option[String] = None, saveMode: Option[String], miscParam: Option[String]) = {
    handleAuth(provider, redirectTo, scope, saveMode, miscParam)
  }

  /**
   * Overrides the values in the session
   *
   * @param session the current session
   * @param params map of param name -> Option of value
   * @return a session updated with the params
   */
  private def overrideSession(session: Session, params: Map[String, Option[String]]): Session = {
    params.foldLeft(session) { (resultSession, t) =>
      val (key, optVal) = t
      optVal.map(value => resultSession + (key -> value)).getOrElse(resultSession)
    }
  }

  /**
   * Find the AuthenticatorBuilder needed to start the authenticated session
   */
  private def builder() = {

    //todo: this should be configurable maybe
    env.authenticatorService.find(env.cookieAuthenticatorConfigurations.Id).getOrElse {
      logger.error(s"[securesocial] missing CookieAuthenticatorBuilder")
      throw new AuthenticationException()
    }
  }

  /**
   * @param provider e.g. "github"
   * @param scope Pass Some[String] to ask for different scopes from those in securesocial.conf
   * @param miscParam
   */
  private def getProvider(provider: String, scope: Option[String], saveMode: Option[String], miscParam: Option[String]): Option[IdentityProvider] = provider match {
    case UsernamePasswordProvider.UsernamePassword =>
      Some(env.createProvider(provider, None, miscParam))
    case _ =>
      val oauth2SettingsBuilder = new OAuth2SettingsBuilder.Default
      val settings = if (scope.isDefined) {
        oauth2SettingsBuilder.forProvider(provider).copy(scope = scope)
      } else {
        oauth2SettingsBuilder.forProvider(provider)
      }
      Some(env.createProvider(provider, Some(settings), miscParam))
  }

  private def getSaveMode(saveModeStr: Option[String], existsUser: Boolean): SaveMode = {
    saveModeStr.flatMap(SaveMode.getFromString(_)).getOrElse {
      if (existsUser) SaveMode.LoggedIn else SaveMode.SignUp
    }
  }

  /**
   * Common method to handle GET and POST authentication requests
   *
   * @param provider the provider that needs to handle the flow
   * @param redirectTo the url the user needs to be redirected to after being authenticated
   * @param scope OAuth2 scope
   * @param saveModeStr
   * @param miscParam miscellaneous information necessary for providers
   */
  private def handleAuth(provider: String, redirectTo: Option[String], scope: Option[String], saveModeStr: Option[String] = None, miscParam: Option[String] = None) = UserAwareAction.async { implicit request =>
    val authenticationFlow = request.user.isEmpty
    val paramsForSession: Map[String, Option[String]] = Map(SecureSocial.OriginalUrlKey -> redirectTo, SecureSocial.SaveModeKey -> saveModeStr)

    getProvider(provider, scope, saveModeStr, miscParam).map {
      _.authenticate().flatMap {
        case denied: AuthenticationResult.AccessDenied =>
          Future.successful(Redirect(env.routes.accessDeniedUrl).flashing("error" -> Messages("securesocial.login.accessDenied")))
        case failed: AuthenticationResult.Failed =>
          logger.error(s"[securesocial] authentication failed, reason: ${failed.error}")
          throw new AuthenticationException()
        case flow: AuthenticationResult.NavigationFlow => Future.successful {
          flow.result.addToSession(paramsForSession.toList.filter(_._2.isDefined).map(t => t._1 -> t._2.get): _*)
        }
        case authenticated: AuthenticationResult.Authenticated =>
          if (authenticationFlow) {
            val profile = authenticated.profile
            env.userService.find(profile.providerId, profile.userId).flatMap { maybeExisting =>
              val saveMode = getSaveMode(request.session.get(SecureSocial.SaveModeKey), maybeExisting.isDefined)
              env.userService.save(authenticated.profile, saveMode).flatMap { userForAction =>
                logger.debug(s"[securesocial] user completed authentication: provider = ${profile.providerId}, userId: ${profile.userId}, mode = $saveMode")
                val evt = if (saveMode == SaveMode.LoggedIn) new LoginEvent(userForAction) else new SignUpEvent(userForAction)
                val sessionAfterEvents = Events.fire(evt).getOrElse(request.session)
                builder().fromUser(userForAction).flatMap { authenticator =>
                  Redirect(toUrl(sessionAfterEvents)).withSession(sessionAfterEvents -
                    SecureSocial.OriginalUrlKey -
                    IdentityProvider.SessionId -
                    OAuth1Provider.CacheKey).startingAuthenticator(authenticator)
                }
              }
            }
          } else {
            request.user match {
              case Some(currentUser) =>
                val modifiedSession = overrideSession(request.session, paramsForSession)
                for (
                  linked <- env.userService.link(currentUser, authenticated.profile);
                  updatedAuthenticator <- request.authenticator.get.updateUser(linked);
                  result <- Redirect(toUrl(modifiedSession)).withSession(modifiedSession -
                    SecureSocial.OriginalUrlKey -
                    SecureSocial.SaveModeKey -
                    IdentityProvider.SessionId -
                    OAuth1Provider.CacheKey).touchingAuthenticator(updatedAuthenticator)
                ) yield {
                  logger.debug(s"[securesocial] linked $currentUser to: providerId = ${authenticated.profile.providerId}")
                  result
                }
              case _ =>
                Future.successful(Unauthorized)
            }
          }
      } recover {
        case e =>
          logger.error("Unable to log user in. An exception was thrown", e)
          Redirect(env.routes.loginPageUrl).flashing("error" -> Messages("securesocial.login.errorLoggingIn"))
      }
    } getOrElse {
      Future.successful(NotFound)
    }
  }
}

object ProviderControllerHelper {
  val logger = play.api.Logger("securesocial.controllers.ProviderController")

  /**
   * The property that specifies the page the user is redirected to if there is no original URL saved in
   * the session.
   */
  val onLoginGoTo = "securesocial.onLoginGoTo"

  /**
   * The root path
   */
  val Root = "/"

  /**
   * The application context
   */
  val ApplicationContext = "application.context"

  /**
   * The url where the user needs to be redirected after succesful authentication.
   *
   * @return
   */
  def landingUrl(implicit configuration: Configuration) = configuration.getString(onLoginGoTo).getOrElse(
    configuration.getString(ApplicationContext).getOrElse(Root)
  )

  /**
   * Returns the url that the user should be redirected to after login
   *
   * @param session
   * @return
   */
  def toUrl(session: Session)(implicit configuration: Configuration) = session.get(SecureSocial.OriginalUrlKey).getOrElse(ProviderControllerHelper.landingUrl)
}
