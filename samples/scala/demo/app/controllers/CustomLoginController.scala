package controllers

import javax.inject.Inject

import play.api.i18n.{ Langs }
import play.filters.csrf.CSRFAddToken
import securesocial.controllers.LoginPage
import play.api.mvc._
import play.api.{ Configuration, Environment, Logger }
import securesocial.core.{ RuntimeEnvironment }
import securesocial.core.services.RoutesService

class CustomLoginController @Inject() (implicit override val env: RuntimeEnvironment,
    override val configuration: Configuration,
    override val playEnv: Environment,
    val csrfAddToken: CSRFAddToken,
    override val controllerComponents: ControllerComponents,
    override val langs: Langs) extends LoginPage {
  override def login: Action[AnyContent] = {
    Logger.debug("using CustomLoginController")
    super.login
  }
}

class CustomRoutesService(implicit override val configuration: Configuration, override val playEnv: Environment) extends RoutesService.Default {
  override def loginPageUrl(implicit req: RequestHeader): String = controllers.routes.CustomLoginController.login().absoluteURL(identityProviderConfigurations.sslEnabled)
}