import PlayKeys._

name    := "scala-demo"

version := Common.version

scalaVersion := Common.scalaVersion

scalariformSettings

libraryDependencies += "tv.kazu" %% "securesocial" % version.value

resolvers += Resolver.sonatypeRepo("snapshots")

scalacOptions := Seq("-encoding", "UTF-8", "-Xlint", "-deprecation", "-unchecked", "-feature")

routesImport ++= Seq("scala.language.reflectiveCalls")
