name := """poem-demo"""
organization := "edu.rpi.tw"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava).enablePlugins(SbtWeb)

Assets / LessKeys.less / includeFilter := "*.less"

Assets / LessKeys.less / excludeFilter := "_*.less"

Assets / LessKeys.compress := true

scalaVersion := "2.13.14"

libraryDependencies += guice

// https://mvnrepository.com/artifact/org.apache.jena/jena-arq
libraryDependencies += "org.apache.jena" % "jena-arq" % "5.0.0"

// https://mvnrepository.com/artifact/org.webjars/bootstrap
libraryDependencies += "org.webjars" % "bootstrap" % "3.4.1"
