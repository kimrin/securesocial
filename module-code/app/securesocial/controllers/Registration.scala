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

import akka.stream.Materializer
import play.api.{ Configuration, Environment }
import play.api.data.Forms._
import play.api.data._
import play.api.http.{ FileMimeTypes, HttpErrorHandler, ParserConfiguration }
import play.api.i18n.{ Lang, LangImplicits, MessagesApi }
import play.api.libs.Files.TemporaryFileCreator
import play.api.mvc._
import play.filters.csrf.{ CSRFCheck, _ }
import securesocial.core._
import securesocial.core.providers.UsernamePasswordProvider
import securesocial.core.providers.utils._
import securesocial.core.services.SaveMode

import scala.concurrent.{ Await, Future }

/**
 * A default Registration controller that uses the BasicProfile as the user type
 *
 * @param env the environment
 */
class Registration @Inject() (implicit val env: RuntimeEnvironment,
    val configuration: Configuration,
    val playEnv: Environment,
    val CSRFAddToken: CSRFAddToken,
    val CSRFCheck: CSRFCheck,
    val controllerComponents: ControllerComponents,
    val parser: BodyParsers.Default,
    val messagesApi: MessagesApi,
    val fileMimeTypes: FileMimeTypes,
    val config: ParserConfiguration,
    val errorHandler: HttpErrorHandler,
    val materializer: Materializer,
    val temporaryFileCreator: TemporaryFileCreator) extends MailTokenBasedOperations with LangImplicits {

  import securesocial.controllers.BaseRegistration._

  private val logger = play.api.Logger("securesocial.controllers.Registration")

  val providerId = UsernamePasswordProvider.UsernamePassword

  val UserName = "userName"
  val FirstName = "firstName"
  val LastName = "lastName"
  implicit val lang = Lang("en")

  val formWithUsername = Form[RegistrationInfo](
    mapping(
      UserName -> nonEmptyText.verifying(messagesApi(UserNameAlreadyTaken)(lang), userName => {
        // todo: see if there's a way to avoid waiting here :-\
        import scala.concurrent.duration._
        Await.result(env.userService.find(providerId, userName), 20.seconds).isEmpty
      }),
      FirstName -> nonEmptyText,
      LastName -> nonEmptyText,
      Password ->
        tuple(
          Password1 -> nonEmptyText.verifying(PasswordValidator.constraint),
          Password2 -> nonEmptyText
        ).verifying(messagesApi(PasswordsDoNotMatch)(lang), passwords => passwords._1 == passwords._2)
    ) // binding
    ((userName, firstName, lastName, password) => RegistrationInfo(Some(userName), firstName, lastName, password._1)) // unbinding
    (info => Some((info.userName.getOrElse(""), info.firstName, info.lastName, ("", ""))))
  )

  val formWithoutUsername = Form[RegistrationInfo](
    mapping(
      FirstName -> nonEmptyText,
      LastName -> nonEmptyText,
      Password ->
        tuple(
          Password1 -> nonEmptyText.verifying(PasswordValidator.constraint),
          Password2 -> nonEmptyText
        ).verifying(messagesApi(PasswordsDoNotMatch)(lang), passwords => passwords._1 == passwords._2)
    ) // binding
    ((firstName, lastName, password) => RegistrationInfo(None, firstName, lastName, password._1)) // unbinding
    (info => Some((info.firstName, info.lastName, ("", ""))))
  )

  val form = if (env.usernamePasswordProviderConfigurations.withUserNameSupport) formWithUsername else formWithoutUsername

  /**
   * Starts the sign up process
   */
  def startSignUp = CSRFAddToken {
    Action {
      implicit request =>
        if (enableRefererAsOriginalUrl) {
          SecureSocial.withRefererAsOriginalUrl(Ok(env.viewTemplates.getStartSignUpPage(startForm)))
        } else {
          Ok(env.viewTemplates.getStartSignUpPage(startForm))
        }
    }
  }

  def handleStartSignUp = CSRFCheck {
    Action.async {
      implicit request =>
        implicit val lang = request.lang
        startForm.bindFromRequest.fold(
          errors => {
            Future.successful(BadRequest(env.viewTemplates.getStartSignUpPage(errors)))
          },
          e => {
            val email = e.toLowerCase
            // check if there is already an account for this email address
            env.userService.findByEmailAndProvider(email, UsernamePasswordProvider.UsernamePassword).map {
              maybeUser =>
                maybeUser match {
                  case Some(user) =>
                    // user signed up already, send an email offering to login/recover password
                    env.mailer.sendAlreadyRegisteredEmail(user)
                  case None =>
                    createToken(email, isSignUp = true).flatMap { token =>
                      env.mailer.sendSignUpEmail(email, token.uuid)
                      env.userService.saveToken(token)
                    }
                }
                handleStartResult().flashing(Success -> messagesApi(ThankYouCheckEmail)(lang), Email -> email)
            }
          }
        )
    }
  }

  /**
   * Renders the sign up page
   *
   * @return
   */
  def signUp(token: String) = CSRFAddToken {
    Action.async {
      implicit request =>
        logger.debug("[securesocial] trying sign up with token %s".format(token))
        executeForToken(token, true, {
          _ =>
            Future.successful(Ok(env.viewTemplates.getSignUpPage(form, token)))
        })
    }
  }

  /**
   * Handles posts from the sign up page
   */
  def handleSignUp(token: String) = CSRFCheck {
    Action.async {
      implicit request =>
        implicit val lang = request.lang
        executeForToken(token, true, {
          t =>
            form.bindFromRequest.fold(
              errors => {
                logger.debug("[securesocial] errors " + errors)
                Future.successful(BadRequest(env.viewTemplates.getSignUpPage(errors, t.uuid)))
              },
              info => {
                val id = if (env.usernamePasswordProviderConfigurations.withUserNameSupport) info.userName.get else t.email
                val newUser = BasicProfile(
                  providerId,
                  id,
                  Some(info.firstName),
                  Some(info.lastName),
                  Some("%s %s".format(info.firstName, info.lastName)),
                  Some(t.email),
                  None,
                  AuthenticationMethod.UserPassword,
                  passwordInfo = Some(env.currentHasher.hash(info.password))
                )

                val withAvatar = env.avatarService.map {
                  _.urlFor(t.email).map { url =>
                    if (url != newUser.avatarUrl) newUser.copy(avatarUrl = url) else newUser
                  }
                }.getOrElse(Future.successful(newUser))

                import securesocial.core.utils._
                val result = for (
                  toSave <- withAvatar;
                  saved <- env.userService.save(toSave, SaveMode.SignUp);
                  deleted <- env.userService.deleteToken(t.uuid)
                ) yield {
                  if (env.usernamePasswordProviderConfigurations.sendWelcomeEmail)
                    env.mailer.sendWelcomeEmail(newUser)
                  val eventSession = Events.fire(new SignUpEvent(saved)).getOrElse(request.session)
                  if (env.usernamePasswordProviderConfigurations.signupSkipLogin) {
                    env.authenticatorService.find(env.cookieAuthenticatorConfigurations.Id).map {
                      _.fromUser(saved).flatMap { authenticator =>
                        confirmationResult()
                          .flashing(Success -> messagesApi(SignUpDone)(lang))
                          .withSession(eventSession - SecureSocial.OriginalUrlKey - IdentityProvider.SessionId)
                          .startingAuthenticator(authenticator)
                      }
                    } getOrElse {
                      logger.error("[securesocial] There isn't CookieAuthenticator registered in the RuntimeEnvironment")
                      Future.successful(confirmationResult().flashing(Error -> messagesApi("There was an error signing you up")(lang)))
                    }
                  } else {
                    Future.successful(confirmationResult().flashing(Success -> messagesApi(SignUpDone)(lang)).withSession(eventSession))
                  }
                }
                result.flatMap(f => f)
              })
        })
    }
  }
}

object BaseRegistration {
  val UserNameAlreadyTaken = "securesocial.signup.userNameAlreadyTaken"
  val ThankYouCheckEmail = "securesocial.signup.thankYouCheckEmail"
  val InvalidLink = "securesocial.signup.invalidLink"
  val SignUpDone = "securesocial.signup.signUpDone"
  val Password = "password"
  val Password1 = "password1"
  val Password2 = "password2"

  val PasswordsDoNotMatch = "securesocial.signup.passwordsDoNotMatch"
}

/**
 * The data collected during the registration process
 *
 * @param userName the username
 * @param firstName the first name
 * @param lastName the last name
 * @param password the password
 */
case class RegistrationInfo(userName: Option[String], firstName: String, lastName: String, password: String)
