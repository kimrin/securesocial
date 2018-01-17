import play.api.inject.{ Binding, Module }
import play.api.{ Environment, Configuration }
import securesocial.core.{ BasicProfile, RuntimeEnvironment }
import service.{ MyEnvironment, DemoUser }

class DemoModule extends play.api.inject.Module {
  def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[RuntimeEnvironment].to[MyEnvironment]
    )
  }
}
