package com.cloud9ers.play2.sockjs

import play.api.Application
import play.api.Logger
import play.api.Plugin
import play.api.PlayException
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import akka.actor.Props

class SockJsPlugin(app: Application) extends Plugin {
  lazy val prefix = app.configuration.getString("sockjs.prefix").getOrElse("/")
  lazy val maxLength = app.configuration.getInt("sockjs.maxLength").getOrElse(1024*100)
  lazy val websocketEnabled: Boolean = app.configuration.getBoolean("sockjs.websocketEnabled").getOrElse(true)
  lazy val clientUrl = app.configuration.getString("sockjs.clientUrl")
    .getOrElse("http://cdn.sockjs.org/sockjs-0.3.4.min.js")

  lazy val config = app.configuration.getConfig("sockjs").map(f => f.underlying).getOrElse(ConfigFactory.empty())
  lazy val system = ActorSystem("SockJsActorSystem", config)
  lazy val sessionManager = system.actorOf(Props[SessionManager], "sessions")

  override def enabled = app.configuration.getBoolean("play.sockjs.enabled").getOrElse(true)

  override def onStart() {
    Logger.info("Starting SockJs Plugin.")
  }

  override def onStop() {
    Logger.info("Stopping SockJS Plugin.")
  }
}

object SockJsPlugin {
  def current(implicit app: Application): SockJsPlugin =
    app.plugin[SockJsPlugin] match {
      case Some(plugin) => plugin
      case None => throw new PlayException("SyncroPlugin Error", "The SockJs has not been initialized! Please edit " +
        "your conf/play.plugins file and add the following line: '100:com.cloud9ers.play2.sockjs.SockJsPlugin' " +
        "(100 is an arbitrary priority and may be changed to match your needs).")
    }
}