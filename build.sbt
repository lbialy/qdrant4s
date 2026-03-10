import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

val scala3Version = "3.3.7"
val sttpVersion = "4.0.7"
val jsoniterVersion = "2.38.8"
val testcontainersVersion = "0.44.1"
val munitVersion = "1.2.4"

inThisBuild(
  List(
    organization := "ma.chinespirit",
    homepage := Some(url("https://github.com/lbialy/qdrant4s")),
    licenses := List("MIT" -> url("https://opensource.org/licenses/MIT")),
    developers := List(
      Developer("lbialy", "Łukasz Biały", "", url("https://github.com/lbialy"))
    ),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/lbialy/qdrant4s"),
        "scm:git:git@github.com:lbialy/qdrant4s.git"
      )
    ),

  )
)

lazy val generated = crossProject(JVMPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("generated"))
  .settings(
    name := "qdrant4s",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client4"          %%% "core"                  % sttpVersion,
      "com.softwaremill.sttp.client4"          %%% "jsoniter"              % sttpVersion,
      "com.github.plokhotnyuk.jsoniter-scala"  %%% "jsoniter-scala-core"   % jsoniterVersion,
      "com.github.plokhotnyuk.jsoniter-scala"  %%% "jsoniter-scala-macros" % jsoniterVersion % "compile-internal",
      "com.github.plokhotnyuk.jsoniter-scala"  %%% "jsoniter-scala-circe"  % jsoniterVersion,
    ),
    scalacOptions := Seq(
      "-unchecked",
      "-deprecation",
      "-feature",
    ),
  )

lazy val generatedJVM    = generated.jvm
lazy val generatedNative = generated.native

lazy val it = project
  .in(file("it"))
  .dependsOn(generatedJVM)
  .settings(
    name := "qdrant4s-it",
    scalaVersion := scala3Version,
    publish / skip := true,
    libraryDependencies ++= Seq(
      "org.scalameta"                          %% "munit"                           % munitVersion          % Test,
      "com.dimafeng"                           %% "testcontainers-scala-munit"      % testcontainersVersion % Test,
      "com.softwaremill.sttp.client4"          %% "okhttp-backend"                  % sttpVersion           % Test,
    ),
    scalacOptions := Seq(
      "-unchecked",
      "-deprecation",
      "-feature",
    ),
  )

lazy val root = project
  .in(file("."))
  .aggregate(generatedJVM, generatedNative, it)
  .settings(
    name := "qdrant4s-root",
    scalaVersion := scala3Version,
    publish / skip := true,
  )
