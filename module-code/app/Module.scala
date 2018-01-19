import javax.inject.Singleton

import securesocial.controllers._
import com.google.inject._

/**
 * This class is a Guice module that tells Guice how to bind several
 * different types. This Guice module is created when the Play
 * application starts.
 * Play will automatically use any class called `Module` that is in
 * the root package. You can create modules in other locations by
 * adding `play.modules.enabled` settings to the `application.conf`
 * configuration file.
 */
class Module extends com.google.inject.AbstractModule {

  override def configure() = {
    bind(classOf[AssetsMetadata]).toProvider(classOf[AssetsMetadataProvider])
    bind(classOf[AssetsFinder]).toProvider(classOf[AssetsFinderProvider])
    bind(classOf[AssetsConfiguration]).toProvider(classOf[AssetsConfigurationProvider])
    bind(classOf[Assets]).in(classOf[Singleton])

    /*
    bind[AssetsMetadata].toProvider[AssetsMetadataProvider],
    bind[AssetsFinder].toProvider[AssetsFinderProvider],
    bind[AssetsConfiguration].toProvider[AssetsConfigurationProvider],
    bind[Assets].toSelf
    */

  }

}