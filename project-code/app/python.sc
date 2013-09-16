object python {
  println("Welcome to the Scala worksheet")       //> Welcome to the Scala worksheet
  class B
  class A extends B {
  def f(x: Int) = x * 8
  def v() = 9
  }
  val a = new A                                   //> a  : python.A = python$$anonfun$main$1$A$1@7057737a
  val methoda = a.getClass.getMethod("v").invoke(a)
                                                  //> methoda  : Object = 9
  val b = new B                                   //> b  : python.B = python$$anonfun$main$1$B$1@1e39a3dc
  val methodb = b.getClass.getMethod("v").invoke(a)
                                                  //> java.lang.NoSuchMethodException: python$$anonfun$main$1$B$1.v()
                                                  //| 	at java.lang.Class.getMethod(Class.java:1655)
                                                  //| 	at python$$anonfun$main$1.apply$mcV$sp(python.scala:11)
                                                  //| 	at org.scalaide.worksheet.runtime.library.WorksheetSupport$$anonfun$$exe
                                                  //| cute$1.apply$mcV$sp(WorksheetSupport.scala:76)
                                                  //| 	at org.scalaide.worksheet.runtime.library.WorksheetSupport$.redirected(W
                                                  //| orksheetSupport.scala:65)
                                                  //| 	at org.scalaide.worksheet.runtime.library.WorksheetSupport$.$execute(Wor
                                                  //| ksheetSupport.scala:75)
                                                  //| 	at python$.main(python.scala:1)
                                                  //| 	at python.main(python.scala)
}