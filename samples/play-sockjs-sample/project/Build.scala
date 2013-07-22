import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "play-sockjs-sample"
  val appVersion      = "1.0-SNAPSHOT"

  val sockjs = "play-sockjs" % "play-sockjs_2.10" % "1.0-SNAPSHOT"

  val appDependencies = Seq(
    sockjs,
    jdbc,
    anorm
  )


  val main = play.Project(appName, appVersion, appDependencies).settings(
    // Add your own project settings here      
  )

}
