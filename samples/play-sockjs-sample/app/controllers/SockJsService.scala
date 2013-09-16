package controllers

import com.cloud9ers.play2.sockjs.SockJs
import play.api.libs.iteratee.{ Concurrent, Iteratee }
import play.api.mvc.{ Controller, WebSocket }
import play.api.libs.concurrent.Promise
import play.api.mvc.RequestHeader
import play.api.libs.json.JsValue
import play.api.libs.iteratee.Enumerator
import play.api.mvc.AnyContent
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.mvc.Action
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.Actor
import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout
import akka.actor.ActorRef
import play.api.mvc.Request
import play.api.libs.json.JsString
import akka.actor.PoisonPill

object SockJsService extends Controller with SockJs {
  /*
   * userHandler (user of the plugin) -> downEnumerator -> downIteratee -> sockjsClient (browser)
   * sockjsClient -> upEnumerator -> upIteratee -> userHandler
   */

  /**
   * The sockJs Handler that the user of the plugin will write to handle the service logic
   * It has the same interface of the websocket
   * returns (Iteratee, Enumerator):
   * Iteratee - to iterate over msgs that will be received from the sockjs client
   * Enumerator - to enumerate the msgs that will be sent to the sockjs client
   */
  def handler(rh: RequestHeader) = {
    val (downEnumerator, downChannel) = Concurrent.broadcast[JsValue]
    val upIteratee = Iteratee.foreach[JsValue] { msg => downChannel push msg; println(s"handler ::::::::::: message: $msg") }
    Promise.pure(upIteratee, downEnumerator)
  }

  def sockJsHandler = SockJs.async(handler) //TODO: Try to make it a single function and pass the complementary path instead
  //hint https://github.com/cgbystrom/sockjs-netty/blob/master/src/main/java/com/cgbystrom/sockjs/ServiceRouter.java#L94

  def sockJsHandler2(route: String) = sockJsHandler

  def websocket[String](server: String, session: String) = SockJs.websocket(handler)
}