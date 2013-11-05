play-sockjs
===========

Play2 plugin for SockJS (Not ready yet for using it)
* So far, you can add only one sockjs

## How to use

 1. Clone the repo to your local machine

```
git clone https://github.com/ashihaby/play-sockjs.git
```
 2. Compile and publish the plugin to local play repo

```
cd play-sockjs/project-code
play publish-local
```
 3. Add it to your play project dependencies

```scala
val appDependencies = Seq(
  "play-sockjs" % "play-sockjs_2.10" % "1.0-SNAPSHOT"
)
```
4. Include the pluing in conf/play.plugins

```
10000:com.cloud9ers.play2.sockjs.SockJsPlugin
```
5. Write your controller and inherit from SockJsTrait

```scala
package controllers

import com.cloud9ers.play2.sockjs.SockJs

import play.api.libs.concurrent.Promise
import play.api.libs.iteratee.{Concurrent, Iteratee}
import play.api.libs.json.JsValue
import play.api.mvc.{Controller, RequestHeader}

object SockJsService extends Controller with SockJs {
  val SockJsHandler(echoAction, echoWebsocket) = SockJs async { rh =>
    val (downEnumerator, downChannel) = Concurrent.broadcast[JsValue]
    val upIteratee = Iteratee.foreach[JsValue] (msg => downChannel push msg)
    Promise.pure(upIteratee, downEnumerator)
  }
}
```
6. Add routing. Unfortunatly it requires a complex routing scheme :(

```scala
GET     /echo/:server/:session/websocket controllers.SockJsService.echoWebsocket(server, session)
GET     /echo                            controllers.SockJsService.echoAction(route="")
GET     /echo/$route<.*>                 controllers.SockJsService.echoAction(route)
OPTIONS /echo                            controllers.SockJsService.echoAction(route="")
OPTIONS /echo/$route<.*>                 controllers.SockJsService.echoAction(route)
POST    /echo/$route<.*>                 controllers.SockJsService.echoAction(route)
```
7. Finnaly, you need to add the base Url to the configuration in application.con
```
	sockjs.prefix=echo
```

* Full configurations:

```
sockjs.prefix=echo
sockjs.responseLimit=1000
sockjs.jsessionid=false
sockjs.heartbeetDelay=1000
sockjs.diconnectDelay=5000
sockjs.websocketEnabled=false
sockjs {
 akka {
  stdout-loglevel = "DEBUG"
  loglevel = "DEBUG"
    log-dead-letters = 10
    log-dead-letters-during-shutdown = on
  }
}
```
