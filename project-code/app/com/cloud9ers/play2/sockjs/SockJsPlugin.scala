package com.cloud9ers.play2.sockjs

import play.api.Plugin
import play.api.Application
import play.api.Logger
import java.util.concurrent.TimeUnit
import play.api.mvc.Request
import play.api.Play
import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.mvc.WebSocket
import play.api.Play.current
import play.api.mvc.RequestHeader
import play.api.libs.json.Json
import scala.util.Random
import java.text.SimpleDateFormat
import java.util.Date
import play.api.mvc.Result
import java.util.Date
import com.cloud9ers.play2.sockjs.transports.Transport
import com.cloud9ers.play2.sockjs.transports.WebSocketTransport
import com.cloud9ers.play2.sockjs.transports.XhrTransport
import scala.collection.mutable.{ Map => MutableMap }
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee.{ Concurrent, Iteratee, Enumerator }
import scala.concurrent.Future
import play.api.mvc.Handler
import play.api.mvc.Headers
import play.api.mvc.AnyContent
import play.api.mvc.BodyParser

class SockJsPlugin(app: Application) extends Plugin {
  lazy val prefix = app.configuration.getString("sockjs.prefix").getOrElse("/")
  lazy val maxLength = app.configuration.getInt("sockjs.maxLength").getOrElse(1024*100)
  lazy val clientUrl = app.configuration.getString("sockjs.clientUrl")
    .getOrElse("http://cdn.sockjs.org/sockjs-0.3.4.min.js")

  override def enabled = app.configuration.getBoolean("play.sockjs.enabled").getOrElse(true)

  override def onStart() {
    Logger.info("Starting SockJs Plugin.")
  }

  override def onStop() {
    Logger.info("Stopping SockJS Plugin.")
  }

}

trait SockJs { self: Controller =>
  def randomNumber() = 2L << 30 + Random.nextInt
  lazy val prefix = current.plugin[SockJsPlugin].map(_.prefix).getOrElse("")
  lazy val maxLength: Int = current.plugin[SockJsPlugin].map(_.maxLength).getOrElse(1024*100)

  val greatingRoute = s"^/$prefix/?".r
  val infoRoute = s"^/$prefix/info/?".r
  val infoDisabledWebsocketRoute = s"^/disabled_websocket_$prefix/info".r
  val iframeUrl = s"^/$prefix/iframe[0-9-.a-z_]*.html(\\?t=[0-9-.a-z_]*)?".r
  val sessionUrl = s"^/$prefix/[^.]+/[^.]+/[^.]+".r
  val sessions: MutableMap[String, Session] = MutableMap()

  lazy val iframePage = new IframePage(current.plugin[SockJsPlugin].map(_.clientUrl).getOrElse(""))

  def cors(implicit req: Request[AnyContent]) = Seq(
    ACCESS_CONTROL_ALLOW_CREDENTIALS -> "true",
    ACCESS_CONTROL_ALLOW_ORIGIN -> req.headers.get("origin").map(o => if (o != "null") o else "*").getOrElse("*"))
    .union(
      (for (acrh <- req.headers.get(ACCESS_CONTROL_REQUEST_HEADERS))
        yield (ACCESS_CONTROL_ALLOW_HEADERS -> acrh)).toSeq)

  def handleSession[A](f: RequestHeader => (Enumerator[A], Iteratee[A, Unit]) => Unit)(implicit request: Request[AnyContent]) = {
    val pathList = request.path.split("/").reverse
    val (transport, sessionId, serverId) = (pathList(0), pathList(1), pathList(2))
    transport match {
      case Transport.XHR =>
        Async(SessionManager.getOrCreateSession(sessionId).dequeue().map(m =>
          Ok(m.toString)
            .withHeaders(
              CONTENT_TYPE -> "application/javascript;charset=UTF-8",
              CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
            .withHeaders(cors: _*)))
      case Transport.XHR_STREAMING =>
        val (enum, channel) = Concurrent.broadcast[Array[Byte]]
        //          val enum1 = Enumerator(H_BLOCK.toArray.map(_.toByte), "o\n".toArray.map(_.toByte), "a[\"x\"]\n".toArray.map(_.toByte))
        val result = Ok.stream(enum)
          .withHeaders(
            CONTENT_TYPE -> "application/javascript;charset=UTF-8",
            CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
          .withHeaders(cors: _*)
        Future {
          Thread.sleep(100)
          channel push H_BLOCK
          //            channel push "o\n".toArray.map(_.toByte)
          //            channel push "a[\"x\"]\n".toArray.map(_.toByte)
          SessionManager.getOrCreateSession(sessionId).send { ms =>
            println(ms)
            channel push ms.toArray.map(_.toByte)
            true
          }
        }
        //          channel push H_BLOCK.toArray.map(_.toByte)
        result
      case "xhr_send" =>
        val (upEnumerator, upChannel) = Concurrent.broadcast[A]
        val downIteratee = Iteratee.foreach[A] { userMsg =>
          SessionManager.getOrCreateSession(sessionId).enqueue(userMsg.asInstanceOf[String])
        }
        // calls the user function and passes the sockjs Enumerator/Iteratee
        f(request)(upEnumerator, downIteratee)
        val contentType = request.headers.get(CONTENT_TYPE).getOrElse(Transport.CONTENT_TYPE_PLAIN)
        contentType match {
          case Transport.CONTENT_TYPE_PLAIN =>
            val sockjsMessage = new String(SockJsFrames.messageFrame(request.body.asRaw.get.asBytes(maxLength).get, true)
                                           .toArray, request.charset.getOrElse("utf-8"))
            upChannel push sockjsMessage.asInstanceOf[A]
            NoContent
              .withHeaders(
                CONTENT_TYPE -> contentType,
                CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
              .withHeaders(cors: _*)
          

        }
    }
  }

  def handleIframe(implicit request: Request[AnyContent]) = {
    if (request.headers.toMap.contains(IF_NONE_MATCH)) {
      NotModified
    } else {
      Ok(iframePage.content).withHeaders(
        CONTENT_TYPE -> "text/html; charset=UTF-8", CACHE_CONTROL -> "max-age=31536000, public",
        ETAG -> iframePage.getEtag,
        EXPIRES -> (new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz"))
          .format(new Date(System.currentTimeMillis() + (365 * 24 * 60 * 60 * 1000))))
    }
  }
  def info(websocket: Boolean = true)(implicit request: Request[AnyContent]) = request.method match {
    case "GET" =>
      Ok(Json.obj(
        "websocket" -> websocket,
        "cookie_needed" -> true,
        "origins" -> List("*:*"),
        "entropy" -> randomNumber))
        .withHeaders(
          CONTENT_TYPE -> "application/json;charset=UTF-8",
          CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
        .withHeaders(cors: _*)
    case "OPTIONS" =>
      val oneYearSeconds = 365 * 24 * 60 * 60
      val oneYearms = oneYearSeconds * 1000
      val expires = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz")
        .format(new Date(System.currentTimeMillis() + oneYearms))
      NoContent
        .withHeaders(
          EXPIRES -> expires,
          CACHE_CONTROL -> "public,max-age=31536000",
          ACCESS_CONTROL_ALLOW_METHODS -> "OPTIONS, GET",
          ACCESS_CONTROL_MAX_AGE -> oneYearSeconds.toString)
        .withHeaders(cors: _*)
  }

  /**
   * The same as Websocket.async
   * @param f - user function that takes the request header and return Future of the user's Iteratee and Enumerator
   */
  def async[A](f: RequestHeader => Future[(Iteratee[A, _], Enumerator[A])]): play.api.mvc.Action[AnyContent] = {
    using { rh =>
      val p = f(rh)
      val upIteratee = Iteratee.flatten(p.map(_._1))
      val downEnumerator = Enumerator.flatten(p.map(_._2))
      (upIteratee, downEnumerator)
    }
  }

  /**
   * returns Handler and passes a function that pipes the user Enumerator to the sockjs Iteratee
   * and pipes the sockjs Enumerator to the user Iteratee
   */
  def using[A](f: RequestHeader => (Iteratee[A, _], Enumerator[A])): play.api.mvc.Action[AnyContent] = {
    handler { rh =>
      (upEnumerator: Enumerator[A], downIteratee: Iteratee[A, Unit]) =>
        // call the user function and holds the user's Iteratee (in) and Enumerator (out)
        val (upIteratee, downEnumerator) = f(rh)

        // pipes the msgs from the sockjs client to the user's Iteratee
        upEnumerator |>> upIteratee

        // pipes the msgs from the user's Enumerator to the sockjs client
        downEnumerator |>> downIteratee
    }
  }

  /**
   * Mainly passes the sockjs Enumerator/Iteratee to the function that associate them with the user's Iteratee/Enumerator respectively
   * According to the transport, it creates the sockjs Enumerator/Iteratee and return Handler in each path
   * calls enqueue/dequeue of the session to handle msg queue between send and receive
   */
  val H_BLOCK = ((for (i <- 0 to 2047) yield "h").toArray :+ "\n").reduceLeft(_ + _).toArray.map(_.toByte)

  def handler[A](f: RequestHeader => (Enumerator[A], Iteratee[A, Unit]) => Unit) = {
    Action { implicit request => // Should match handler type (Action, Websocket .. etc)
      request.path match {
        case greatingRoute() => Ok("Welcome to SockJS!\n").withHeaders(CONTENT_TYPE -> "text/plain;charset=UTF-8")
        case iframeUrl(_) => handleIframe
        case infoRoute() => info()
        case infoDisabledWebsocketRoute() => info(websocket = false)
        case sessionUrl() => handleSession(f)
        case _ => NotFound("Notfound")
      }
    }
  }
}
