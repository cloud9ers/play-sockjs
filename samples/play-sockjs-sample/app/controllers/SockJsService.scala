package controllers

import play.api.mvc.Controller
import com.cloud9ers.play2.sockjs.SockJs
import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.Iteratee
import scala.concurrent.{ Future, Promise }
import play.api.libs.iteratee.Enumerator
import play.api.mvc.RequestHeader
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.mvc.Handler
import play.api.mvc.Action
import play.api.mvc.WebSocket
import play.api.mvc.BodyParser
import play.api.libs.iteratee._
import play.api.mvc.Results
import play.api.mvc.Request
import play.api.mvc.AnyContent
import com.cloud9ers.play2.sockjs.SockJs

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
  def sockJsHandler = async { rh =>
    import play.api.libs.concurrent.Promise
    val (downEnumerator, downChannel) = Concurrent.broadcast[String]
    val upIteratee = Iteratee.foreach[String] { msg => downChannel push msg }
    play.api.libs.concurrent.Promise.pure(upIteratee, downEnumerator)
  }

  /**
   * overload to the sockJsHandler to take the url parameter
   */
  def sockJsHandler2(route: String) = sockJsHandler

}