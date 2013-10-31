package controllers

import com.cloud9ers.play2.sockjs.SockJs
import play.api.libs.concurrent.Promise
import play.api.libs.iteratee.{ Concurrent, Iteratee }
import play.api.libs.json.JsValue
import play.api.mvc.{ Controller, RequestHeader }
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Input
import scala.concurrent.Future
import com.cloud9ers.play2.sockjs.SockJsHandler

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

  val SockJsHandler(echoAction, echoWebsocket) = SockJs async { rh =>
    val (downEnumerator, downChannel) = Concurrent.broadcast[JsValue]
    val upIteratee = Iteratee.foreach[JsValue] { msg => downChannel push msg; println(s"handler1 ::::::::::: message: $msg") }
    Promise.pure(upIteratee, downEnumerator)
  }

  val SockJsHandler(closeAction, _) = SockJs async { rh =>
    val (downEnumerator, downChannel) = Concurrent.broadcast[JsValue]
    val upIteratee = Iteratee.foreach[JsValue] { msg => downChannel push Input.EOF }
    Promise.pure(upIteratee, downEnumerator)
  }
  
  //TODO: disabled websocket app
}