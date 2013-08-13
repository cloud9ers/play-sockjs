package com.cloud9ers.play2.sockjs.transports

import com.cloud9ers.play2.sockjs.Session

import scala.collection.mutable.{Map => MutableMap}

class XhrTransport extends Transport {
  
  def handleRequest(sessionId: String)(implicit sessions : MutableMap[String, Session]) = {
    var session = sessions.get(sessionId)
    if (session.isEmpty) {
//      sessions += (sessionId -> new Session(sessionId))
      session = sessions.get(sessionId)
    }


    
  }

}