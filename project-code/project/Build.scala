import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "play-sockjs"
  val appVersion      = "1.0-SNAPSHOT"
  val appScalaVersion = "2.10.0"
  val appScalaBinaryVersion = "2.10"
  val appScalaCrossVersions = Seq("2.10.0")

  val appDependencies = Seq(
    jdbc,
    anorm
  )


  val main = play.Project(appName, appVersion, appDependencies).settings(
  )

}
