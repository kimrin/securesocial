package controllers

import javax.inject.Inject

import akka.stream.Materializer
import play.api.http.{ FileMimeTypes, HttpErrorHandler, ParserConfiguration }
import play.api.i18n.MessagesApi
import play.api.libs.Files.TemporaryFileCreator
import play.filters.csrf.CSRFAddToken
import securesocial.controllers.LoginPage
import play.api.mvc._
import play.api.{ Configuration, Environment, Logger }
import securesocial.core.{ IdentityProvider, RuntimeEnvironment }
import securesocial.core.services.RoutesService

class CustomLoginController @Inject() (implicit override val env: RuntimeEnvironment,
    override val configuration: Configuration,
    override val playEnv: Environment,
    override val controllerComponents: ControllerComponents,
    override val CSRFAddToken: CSRFAddToken,
    override val parser: BodyParsers.Default,
    override val messagesApi: MessagesApi,
    override val fileMimeTypes: FileMimeTypes,
    override val config: ParserConfiguration,
    override val errorHandler: HttpErrorHandler,
    override val materializer: Materializer,
    override val temporaryFileCreator: TemporaryFileCreator) extends LoginPage {
  override def login: Action[AnyContent] = {
    Logger.debug("using CustomLoginController")
    super.login
  }
}

class CustomRoutesService(implicit override val configuration: Configuration, override val playEnv: Environment) extends RoutesService.Default {
  override def loginPageUrl(implicit req: RequestHeader): String = controllers.routes.CustomLoginController.login().absoluteURL(identityProviderConfigurations.sslEnabled)
}