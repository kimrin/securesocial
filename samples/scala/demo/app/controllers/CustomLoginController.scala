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
import service.DemoUser
import securesocial.core.services.RoutesService

class CustomLoginController @Inject() (override implicit val env: RuntimeEnvironment,
    override implicit val configuration: Configuration,
    override implicit val playEnv: Environment,
    override implicit val controllerComponents: ControllerComponents,
    override implicit val CSRFAddToken: CSRFAddToken,
    override implicit val parser: BodyParser[AnyContent],
    override implicit val messagesApi: MessagesApi,
    override implicit val fileMimeTypes: FileMimeTypes,
    override implicit val config: ParserConfiguration,
    override implicit val errorHandler: HttpErrorHandler,
    override implicit val materializer: Materializer,
    override implicit val temporaryFileCreator: TemporaryFileCreator) extends LoginPage {
  override def login: Action[AnyContent] = {
    Logger.debug("using CustomLoginController")
    super.login
  }
}

class CustomRoutesService(implicit override val configuration: Configuration, override val playEnv: Environment) extends RoutesService.Default {
  override def loginPageUrl(implicit req: RequestHeader): String = controllers.routes.CustomLoginController.login().absoluteURL(identityProviderConfigurations.sslEnabled)
}