name := """poem-demo"""
organization := "edu.rpi.tw"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava).enablePlugins(SbtWeb)

Assets / LessKeys.less / includeFilter := "*.less"

Assets / LessKeys.less / excludeFilter := "_*.less"

Assets / LessKeys.compress := true

scalaVersion := "3.3.7"

libraryDependencies += guice

// https://mvnrepository.com/artifact/org.apache.jena/jena-arq
libraryDependencies += "org.apache.jena" % "jena-arq" % "5.6.0"

// https://mvnrepository.com/artifact/org.webjars/bootstrap
libraryDependencies += "org.webjars" % "bootstrap" % "5.3.8"

// https://mvnrepository.com/artifact/org.webjars/jquery
libraryDependencies += "org.webjars" % "jquery" % "3.7.1"

// https://mvnrepository.com/artifact/org.webjars/jquery-form
libraryDependencies += "org.webjars" % "jquery-form" % "4.2.2"

// https://mvnrepository.com/artifact/org.webjars.npm/choices.js
libraryDependencies += "org.webjars.npm" % "choices.js" % "11.1.0"

// https://mvnrepository.com/artifact/com.google.code.gson/gson
libraryDependencies += "com.google.code.gson" % "gson" % "2.13.2"

// https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-databind
libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.20.1"

// https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-core
libraryDependencies += "com.fasterxml.jackson.core" % "jackson-core" % "2.20.1"

// https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-annotations
libraryDependencies += "com.fasterxml.jackson.core" % "jackson-annotations" % "2.20"

// https://mvnrepository.com/artifact/com.fasterxml.jackson.module/jackson-module-scala
libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.20.1"

// https://mvnrepository.com/artifact/org.apache.poi/poi-ooxml
libraryDependencies += "org.apache.poi" % "poi-ooxml" % "5.5.0"

// https://mvnrepository.com/artifact/ca.uhn.hapi.fhir/hapi-fhir-base
libraryDependencies += "ca.uhn.hapi.fhir" % "hapi-fhir-base" % "8.4.0"

// https://mvnrepository.com/artifact/ca.uhn.hapi.fhir/hapi-fhir-structures-r5
libraryDependencies += "ca.uhn.hapi.fhir" % "hapi-fhir-structures-r5" % "8.4.0"

// https://mvnrepository.com/artifact/ca.uhn.hapi.fhir/hapi-fhir-structures-r4b
libraryDependencies += "ca.uhn.hapi.fhir" % "hapi-fhir-structures-r4b" % "8.4.0"

// https://mvnrepository.com/artifact/ca.uhn.hapi.fhir/hapi-fhir-structures-r4
libraryDependencies += "ca.uhn.hapi.fhir" % "hapi-fhir-structures-r4" % "8.4.0"

// https://mvnrepository.com/artifact/ca.uhn.hapi.fhir/hapi-fhir-structures-dstu3
libraryDependencies += "ca.uhn.hapi.fhir" % "hapi-fhir-structures-dstu3" % "8.4.0"

// https://mvnrepository.com/artifact/ca.uhn.hapi.fhir/hapi-fhir-structures-dstu2
libraryDependencies += "ca.uhn.hapi.fhir" % "hapi-fhir-structures-dstu2" % "8.4.0"

// https://mvnrepository.com/artifact/com.openai/openai-java
libraryDependencies += "com.openai" % "openai-java" % "4.8.0"

// https://mvnrepository.com/artifact/org.json/json
libraryDependencies += "org.json" % "json" % "20250517"

// https://mvnrepository.com/artifact/org.apache.logging.log4j/log4j-to-slf4j
libraryDependencies += "org.apache.logging.log4j" % "log4j-to-slf4j" % "2.25.2"