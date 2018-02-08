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

import play.api.{ Configuration, Environment }
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{ Langs, LangImplicits }
import play.api.mvc._
import play.filters.csrf.{ CSRFCheck, _ }
import securesocial.core._
import securesocial.core.providers.UsernamePasswordProvider
import securesocial.core.providers.utils.PasswordValidator
import securesocial.core.services.SaveMode

import scala.concurrent.Future

/**
 * A default controller the uses the BasicProfile as the user type
 *
 * @param env an environment
 */

/**
 * The trait that provides the Password Reset functionality
 *
 */
class PasswordReset @Inject() (implicit val langs: Langs,
    implicit val env: RuntimeEnvironment,
    val configuration: Configuration,
    override val playEnv: Environment,
    override val controllerComponents: ControllerComponents,
    val CSRFAddToken: CSRFAddToken,
    val CSRFCheck: CSRFCheck) extends MailTokenBasedOperations with LangImplicits {

  private val logger = play.api.Logger("securesocial.controllers.BasePasswordReset")
  val PasswordUpdated = "securesocial.password.passwordUpdated"
  val ErrorUpdatingPassword = "securesocial.password.error"
  val changePasswordForm = Form(
    BaseRegistration.Password ->
      tuple(
        BaseRegistration.Password1 -> nonEmptyText.verifying(PasswordValidator.constraint),
        BaseRegistration.Password2 -> nonEmptyText
      ).verifying(messagesApi(BaseRegistration.PasswordsDoNotMatch)(lang), passwords => passwords._1 == passwords._2)
  )

  /**
   * Renders the page that starts the password reset flow
   */
  def startResetPassword = CSRFAddToken {
    Action {
      implicit request =>
        Ok(env.viewTemplates.getStartResetPasswordPage(startForm)(request, lang))
    }
  }

  /**
   * Handles form submission for the start page
   */
  def handleStartResetPassword = CSRFCheck {
    Action.async {
      implicit request =>
        startForm.bindFromRequest.fold(
          errors => Future.successful(BadRequest(env.viewTemplates.getStartResetPasswordPage(errors)(request, lang))),
          e => {
            val email = e.toLowerCase
            env.userService.findByEmailAndProvider(email, UsernamePasswordProvider.UsernamePassword).map {
              maybeUser =>
                maybeUser match {
                  case Some(user) =>
                    createToken(email, isSignUp = false).map { token =>
                      env.mailer.sendPasswordResetEmail(user, token.uuid)(request, lang)
                      env.userService.saveToken(token)
                    }
                  case None =>
                    env.mailer.sendUnkownEmailNotice(email)(request, lang)
                }
                handleStartResult().flashing(Success -> messagesApi(BaseRegistration.ThankYouCheckEmail)(lang))
            }
          }
        )
    }
  }

  /**
   * Renders the reset password page
   *
   * @param token the token that identifies the user request
   */
  def resetPassword(token: String) = CSRFAddToken {
    Action.async {
      implicit request =>
        executeForToken(token, false, {
          t =>
            Future.successful(Ok(env.viewTemplates.getResetPasswordPage(changePasswordForm, token)(request, lang)))
        })
    }
  }

  /**
   * Handles the reset password page submission
   *
   * @param token the token that identifies the user request
   */
  def handleResetPassword(token: String) = CSRFCheck {
    Action.async { implicit request =>
      import scala.concurrent.ExecutionContext.Implicits.global
      executeForToken(token, false, {
        t =>
          changePasswordForm.bindFromRequest.fold(errors =>
            Future.successful(BadRequest(env.viewTemplates.getResetPasswordPage(errors, token)(request, lang))),
            p =>
              env.userService.findByEmailAndProvider(t.email, UsernamePasswordProvider.UsernamePassword).flatMap {
                case Some(profile) =>
                  val hashed = env.currentHasher.hash(p._1)
                  for (
                    updated <- env.userService.save(profile.copy(passwordInfo = Some(hashed)), SaveMode.PasswordChange);
                    deleted <- env.userService.deleteToken(token)
                  ) yield {
                    env.mailer.sendPasswordChangedNotice(profile)(request, lang)
                    val eventSession = Events.fire(new PasswordResetEvent(updated)).getOrElse(request.session)
                    confirmationResult().withSession(eventSession).flashing(Success -> messagesApi(PasswordUpdated)(lang))
                  }
                case _ =>
                  logger.error("[securesocial] could not find user with email %s during password reset".format(t.email))
                  Future.successful(confirmationResult().flashing(Error -> messagesApi(ErrorUpdatingPassword)(lang)))
              }
          )
      })
    }
  }
}
