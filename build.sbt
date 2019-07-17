name := "coop"
organization := "io.rebelapps"
crossScalaVersions := Seq("2.11.12", "2.12.8")

val catsVersion = "1.6.0"
val shapelessVersion = "2.3.3"
val scalaTestVersion = "3.0.5"
val scalaCheckVersion = "1.14.0"

lazy val testLibs = Seq(
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.0",
  "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
    "org.scalacheck" %% "scalacheck" % scalaCheckVersion % Test
)

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % catsVersion,
  "org.typelevel" %% "cats-mtl-core" % "0.5.0",
  "co.fs2" %% "fs2-core" % "1.0.4",
  "com.chuusai" %% "shapeless" % shapelessVersion
) ++ testLibs

lazy val `coop` = project in file(".")

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.1")

scalacOptions ++= Seq(
    "-feature",
    "-unchecked",
    "-deprecation",
    "-Ypartial-unification",
    "-encoding", "utf8",
    "-language:implicitConversions",
    "-language:higherKinds",
    "-language:existentials",
    "-language:postfixOps",
    "-Xfatal-warnings",
    "-Yno-adapted-args",
    "-Ywarn-value-discard"
)

