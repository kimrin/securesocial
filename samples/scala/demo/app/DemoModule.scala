import com.google.inject.{ TypeLiteral, Scopes, AbstractModule }
import net.codingwell.scalaguice.ScalaModule
import play.api.{ Environment, Configuration }
import securesocial.core.{ BasicProfile, RuntimeEnvironment }
import service.{ MyEnvironment, DemoUser }

class DemoModule(implicit val environment: Environment, val configuration: Configuration) extends AbstractModule with ScalaModule {
  override def configure() {
    val environment: MyEnvironment = new MyEnvironment()
    bind(new TypeLiteral[RuntimeEnvironment] {}).toInstance(environment)

  }
}
