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

object SockJsService2 extends Controller {
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

  /**
   * just returns some headers
   */
  def cors(implicit req: Request[AnyContent]) = Seq(
    ACCESS_CONTROL_ALLOW_CREDENTIALS -> "true",
    ACCESS_CONTROL_ALLOW_ORIGIN -> req.headers.get("origin").map(o => if (o != "null") o else "*").getOrElse("*"))
    .union(
      (for (acrh <- req.headers.get(ACCESS_CONTROL_REQUEST_HEADERS))
        yield (ACCESS_CONTROL_ALLOW_HEADERS -> acrh)).toSeq)

  /**
   * The same as Websocket.async
   * @param f - user function that takes the request header and return Future of the user's Iteratee and Enumerator
   */
  def async[A](f: RequestHeader => Future[(Iteratee[A, _], Enumerator[A])]): Handler = {
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
  def using[A](f: RequestHeader => (Iteratee[A, _], Enumerator[A])): Handler = {
    handler { h =>
      (upEnumerator: Enumerator[A], downIteratee: Iteratee[A, Unit]) =>
        // call the user function and holds the user's Iteratee (in) and Enumerator (out)
        val (upIteratee, downEnumerator) = f(h)

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
    if (true) Action { implicit request => // Should match handler type (Action, Websocket .. etc)
      println(request.path)
      val Array(_, _, serverId, sessionId, transport) = request.path.split("/")
      transport match {
        case "xhr" =>
          Async(getOrCreateSession(sessionId).dequeue().map(m =>
            Ok(m.toString)
              .withHeaders(
                CONTENT_TYPE -> "application/javascript;charset=UTF-8",
                CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
              .withHeaders(cors: _*)))
        case "xhr_streaming" =>
          val (enum, channel) = Concurrent.broadcast[Array[Byte]]
          val enum1 = Enumerator(H_BLOCK.toArray.map(_.toByte), "o\n".toArray.map(_.toByte), "a[\"x\"]\n".toArray.map(_.toByte))
          import java.io.InputStream
          val enum2 = Enumerator.fromStream(new InputStream() { def read = ??? }, 100)
          val result = Ok.stream(enum)
            .withHeaders(
              CONTENT_TYPE -> "application/javascript;charset=UTF-8",
              CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
            .withHeaders(cors: _*)
          Future {
            Thread.sleep(1000)
            channel push H_BLOCK
//            channel push "o\n".toArray.map(_.toByte)
//            channel push "a[\"x\"]\n".toArray.map(_.toByte)
            getOrCreateSession(sessionId).send { ms =>
              println(ms)
              channel push ms.toArray.map(_.toByte)
            }
          }
          //          channel push H_BLOCK.toArray.map(_.toByte)
          result
        case "xhr_send" =>
          val (upEnumerator, upChannel) = Concurrent.broadcast[A]
          val downIteratee = Iteratee.foreach[A] { userMsg =>
            getOrCreateSession(sessionId).enqueue(userMsg.asInstanceOf[String])
          }
          // calls the user function and passes the sockjs Enumerator/Iteratee
          f(request)(upEnumerator, downIteratee)
          //request.body.asText.map { m => println(m); (upChannel push m.asInstanceOf[A]) }
          //FIXME map doesn't happen, the body is not text/plain, 7asbia Allah w ne3ma el wakel :@
          upChannel push "a[\"x\"]\n".asInstanceOf[A]
          NoContent
            .withHeaders(
              CONTENT_TYPE -> "text/plain;charset=UTF-8",
              CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
            .withHeaders(cors: _*)
      }
    }
    else {
      Action(Ok) // FIXME change to websocket
      //      WebSocket.using[A] { rh =>
      //        f(rh)(enumerator, iteratee)
      //        (iteratee, enumerator)
      //      }
    }
  }

  /**
   * Session class to queue messages over multiple connection like xhr and xhr_send
   */
  class Session(sessionId: String) {
    trait Event
    case class Msg(msg: String) extends Event
    type Writer = String => Unit
    case class ReadyToWrite(writer: Writer) extends Event
    /**
     * Structure to keep track of iteration state
     * @param ms - list of queued messages waiting to be sent to the sockjs client
     * @param writablePromise - promise will be used to send data on it's onsuccess callback
     */
    case class Accumulator(ms: List[String], writer: Option[Writer])
    // for queuing the messages to be flushed when the downstream connection is ready 
    private[this] val (msgEnumerator, msgChannel) = Concurrent.broadcast[Event]

    def encodeMsgs(ms: Seq[String]): String = ms.reduceLeft(_ + _) //TODO write the sockjs encoding function
    // iterate over the msgEnumertor and keep the context/state of the msg queue
    def msgIteratee: Iteratee[Event, Accumulator] = {
      def step(m: Event, acc: Accumulator)(implicit i: Input[Event]): Iteratee[Event, Accumulator] = i match {
        case Input.EOF | Input.Empty => Done(acc, Input.EOF)
        case Input.El(e) => e match {
          // send the msg to the next step or flush if the downstream connection is ready
          case Msg(msg) =>
            println(msg)
            acc.writer match {
              case Some(writer) => sendFrame(acc.ms :+ msg, writer) //TODO test for: check if the down channel is ready and a new msg received
              case None => Cont(i => step(Msg(msg), Accumulator(acc.ms :+ msg, None))(i)) //TODO optimize append to the right
            }
          // takes a promise and fulfill that promise if the msg queue has items or send ready state to the next step otherwise
          case ReadyToWrite(writer) => //TODO test for: check if the ms is not empty and send the p to next step
            acc.ms match {
              case Nil => Cont(i => step(Msg(""), acc)(i))
              case ms => sendFrame(ms, writer)
            }
        }
      }
      def sendFrame(ms: List[String], writer: String => Unit)(implicit i: Input[Event]): Iteratee[Event, Accumulator] = {
        writer(encodeMsgs(ms.filterNot(_ == "")))
        Cont(i => step(Msg(""), Accumulator(Nil, None))(i)) //FIXME: None resets the writer each time which is not suitable for streaming
      }
      Cont(i => step(Msg(""), Accumulator(Nil, None))(i))
    }
    // run the msgIteratee over the msgEnumerator
    msgEnumerator run msgIteratee

    /**
     * returns a Future of the messages queue that should be sent to the sockjs client in one string
     * clear the message queue if the downstream connection is ready to write
     */
    def dequeue(): Future[String] = {
      val p = Promise[String]()
      msgChannel push ReadyToWrite(ms => p success ms) // send ReadyToWrite to the msgIteratee with the promise that will be used to return the msgs
      p.future
    }
    /**
     * adds a message to message queue
     */
    def enqueue(msg: String) = {
      msgChannel push Msg(msg) // pushes a message to the message queue
      this
    }
    /**
     * send the queued messages using the writer function when the downstream channel is ready
     * @param writer - function takes the msg and write it according to the transport
     */
    def send(writer: String => Unit) = {
      msgChannel push ReadyToWrite(writer)
      this
    }

  }

  //TODO find a more decent way to store sessions
  val sessions = scala.collection.mutable.Map[String, Session]()
  /**
   * returns the session. If the session is not created, it creates it and send the first message: "o\n"
   */
  def getOrCreateSession(sessionId: String): Session = sessions.get(sessionId).getOrElse {
    val session = new Session(sessionId)
    sessions put (sessionId, session)
    session
//    	.enqueue(H_BLOCK)
    	.enqueue("o\n")
    session
  }
}