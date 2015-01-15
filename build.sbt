buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)

buildInfoPackage := "org.mackler.hdkeygen"

name := "hdkeygen"

version := "0.1.0"

scalaVersion := "2.10.3"

scalacOptions in Compile ++= Seq(
  "-deprecation",
  "-feature",
  "-language:implicitConversions",
  "-unchecked"
)

resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

libraryDependencies ++= Seq(
  "org.slf4j"        % "slf4j-simple" % "1.7.7",
  "org.slf4j"        % "slf4j-api"    % "1.7.7",
  "org.bitcoinj"       % "bitcoinj-core"     % "0.12.2",
  "com.github.scopt" % "scopt_2.10" % "3.2.0"
)

com.typesafe.sbt.SbtNativePackager.packageArchetype.java_application
