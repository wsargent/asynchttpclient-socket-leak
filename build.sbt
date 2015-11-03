
scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ws" % "2.4.3",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "com.typesafe.akka" %% "akka-actor" % "2.3.13"
)

fork in run := true

//val profilerDirectory = "/Applications/YourKit_Java_Profiler_2015_build_15076.app/Contents/Resources"
//
//javaOptions in run ++= Seq(s"-agentpath:$profilerDirectory/bin/mac/libyjpagent.jnilib", "-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005")
