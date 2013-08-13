package controllers

import com.cloud9ers.play2.sockjs.SockJs

import play.api.libs.iteratee.{ Concurrent, Iteratee }
import play.api.mvc.Controller
import play.api.libs.concurrent.Promise

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
    val (downEnumerator, downChannel) = Concurrent.broadcast[String]
    val upIteratee = Iteratee.foreach[String] { msg => downChannel push msg }
    Promise.pure(upIteratee, downEnumerator)
  }

  /**
   * overload to the sockJsHandler to take the url parameter
   */
  def sockJsHandler2(route: String) = sockJsHandler

}