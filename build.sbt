ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

ThisBuild / scalacOptions ++=
  Seq(
    "-deprecation",
    "-feature",
    "-language:implicitConversions",
    "-unchecked",
    "-Xfatal-warnings",
  )

lazy val root =
  project
    .in(file("."))
    .settings(name := "StackTome")
    .settings(commonSettings)
    .settings(dependencies)
    .settings(
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
    )

lazy val commonSettings = {
  lazy val commonScalacOptions = Seq(
    Compile / console / scalacOptions --= Seq(
      "-Wunused:_",
      "-Xfatal-warnings",
    ),
    Test / console / scalacOptions :=
      (Compile / console / scalacOptions).value,
  )

  lazy val otherCommonSettings = Seq(
    update / evictionWarningOptions := EvictionWarningOptions.empty
  )

  Seq(
    commonScalacOptions,
    otherCommonSettings,
  ).reduceLeft(_ ++ _)
}

lazy val zioVersion = "2.0.8"
lazy val configVersion = "3.0.7"
lazy val circeVersion = "0.14.4"
lazy val http4sVersion = "0.23.18"

lazy val dependencies = Seq(
  libraryDependencies ++= Seq(
    "dev.zio" %% "zio" % zioVersion,
    "dev.zio" %% "zio-streams" % zioVersion,
    "dev.zio" %% "zio-concurrent" % zioVersion,
    "dev.zio" %% "zio-test" % zioVersion % Test,
    "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
    "dev.zio" %% "zio-test-magnolia" % zioVersion % Test,
    "dev.zio" %% "zio-logging" % "2.1.9",
    "dev.zio" %% "zio-kafka" % "2.0.7",
    "dev.zio" %% "zio-interop-cats" % "23.0.0.1",
    "dev.zio" %% "zio-config" % configVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-literal" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,
    "org.http4s" %% "http4s-dsl" % http4sVersion,
    "org.http4s" %% "http4s-ember-server" % http4sVersion,
    "org.http4s" %% "http4s-ember-client" % http4sVersion,
    "dev.profunktor" %% "redis4cats-effects" % "1.4.0",
  )
)
