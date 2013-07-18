package com.cloud9ers.play2.sockjs


import play.api.Plugin
import play.api.Application
import play.api.Logger
import java.util.concurrent.TimeUnit
import play.api.mvc.Request
import play.api.Play

class SockJsPlugin(app: Application) extends Plugin {

  lazy val logger = Logger("sockJs.plugin")
  
  override def onStart() {
    logger.info("Starting SockJs Plugin.")
  }

  override def onStop() {
    logger.info("Stopping SockJS Plugin.")
  }

}
