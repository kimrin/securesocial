import play.api.inject.Binding
import play.api.{ Configuration, Environment }
import securesocial.core.{ BasicProfile, RuntimeEnvironment }
import service.{ DemoUser, MyEnvironment }
import play.api.libs.mailer._

class DemoModule extends play.api.inject.Module {
  def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[RuntimeEnvironment].to[MyEnvironment],
      bind[MailerClient].to[SMTPMailer],
      bind[SMTPConfiguration].toProvider[SMTPConfigurationProvider]
    )
  }
}
