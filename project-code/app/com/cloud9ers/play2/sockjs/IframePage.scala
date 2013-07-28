package com.cloud9ers.play2.sockjs

import java.security.MessageDigest

class IframePage(clientUrl: String) {
  
   val content = 
    s"""<!DOCTYPE html>
<html>
<head>
  <meta http-equiv="X-UA-Compatible" content="IE=edge" />
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
  <script>
    document.domain = document.domain;
    _sockjs_onload = function(){SockJS.bootstrap_iframe();};
  </script>
  <script src="$clientUrl"></script>
</head>
<body>
  <h2>Don't panic!</h2>
  <p>This is a SockJS hidden iframe. It's used for cross domain magic.</p>
</body>
</html>""".replaceAll("""(?m)\s+$""", "")
  
  def getEtag: String = {
    new String(MessageDigest.getInstance("SHA").digest(content.getBytes))
  }

}