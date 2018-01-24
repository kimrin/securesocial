package securesocial.controllers

import javax.inject.{ Inject, Singleton }

import controllers.AssetsMetadata
import play.api.http.HttpErrorHandler

@Singleton
class Assets @Inject() (errorHandler: HttpErrorHandler, meta: AssetsMetadata) extends controllers.Assets(errorHandler, meta) {}

