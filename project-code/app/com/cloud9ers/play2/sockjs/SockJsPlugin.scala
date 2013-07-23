package com.cloud9ers.play2.sockjs


import play.api.Plugin
import play.api.Application
import play.api.Logger
import java.util.concurrent.TimeUnit
import play.api.mvc.Request
import play.api.Play

class SockJsPlugin(app: Application) extends Plugin {

  override def onStart() {
	val sockjsConfig = app.configuration.getConfig("sockjs")
	sockjsConfig.get.getInt("responseLimit").getOrElse(10)
	// or
	app.configuration.getInt("sockjs.responseLimit").getOrElse(10)
    Logger.info("Starting SockJs Plugin.")
  }

  override def onStop() {
    Logger.info("Stopping SockJS Plugin.")
  }

}
