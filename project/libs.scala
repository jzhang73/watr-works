import sbt._
import Keys._


trait LibVersions {
  val scalazVersion       = "7.2.25"
  val scalaTagsVersion    = "0.6.7"
  val scalatestVersion    = "3.0.5"
  val logbackVersion      = "1.7.25"
  val scrimageVersion     = "2.1.8"
  val acyclicVersion      = "0.1.7"
  val doobieVersion       = "0.5.3"
  val matryoshkaCoreV     = "0.21.3"
  val sourcecodeV         = "0.1.4"
  val fansiV              = "0.2.5"
  val shapelessV          = "2.3.3"
  val scaladgetV          = "0.9.5"
  val http4sVersion       = "0.18.15"
  val fs2Version          = "0.10.5"
  val circeJsonVersion    = "0.9.3"

  val ammoniteVersion     = "1.1.2"

  val catsV               = "1.1.0"
  val catsEffectV         = "0.10.1"
  val postgresqlV         = "42.2.4"
  val freestyleV          = "0.7.0"
  val guavaV              = "23.0"
}


object LibVersions extends LibVersions

object TestLibs extends LibVersions {
  val scalatest = Seq(
    "org.scalatest" %% "scalatest" % scalatestVersion % "test"
  )

  val scalacheck = Seq(
    // "org.scalaz"     %% "scalaz-scalacheck-binding" % scalazVersion  % "test",
    "org.scalaz"        %% "scalaz-scalacheck-binding" % "7.2.22-scalacheck-1.14" % Test,
    "org.scalacheck" %% "scalacheck"                % "1.14.0"       % "test" //  force()
  )

  val testAndCheck = scalatest ++ scalacheck
}

object LogLibs extends LibVersions {
  val logback = Seq(
    "org.log4s"      %% "log4s"            % "1.6.1",
    "ch.qos.logback"  % "logback-classic"  % "1.2.3",
    "org.slf4j"       % "slf4j-api"        % logbackVersion,
    "org.slf4j"       % "jul-to-slf4j"     % logbackVersion,
    "org.slf4j"       % "jcl-over-slf4j"   % logbackVersion
  )
}

object DatabaseLibs extends LibVersions {

  val doobieDb = Seq(
    "org.tpolecat"      %% "doobie-core"       % doobieVersion,
    "org.tpolecat"      %% "doobie-postgres"   % doobieVersion,
    "org.tpolecat"      %% "doobie-hikari"     % doobieVersion,
    "org.tpolecat"      %% "doobie-specs2"     % doobieVersion % "test",
    "org.postgresql"     % "postgresql"        % postgresqlV,
    "org.javassist"      % "javassist"         % "3.23.1-GA",
    "com.impossibl.pgjdbc-ng" % "pgjdbc-ng"  % "0.7.1"
  )

}

trait CommonLibs extends LibVersions {

  val ammonite         = "com.lihaoyi"             % "ammonite"          % ammoniteVersion cross CrossVersion.full
  val ammoniteOps      = "com.lihaoyi"             %% "ammonite-ops"     % ammoniteVersion
  val scopt            = "com.github.scopt"        %% "scopt"            % "3.7.0"
  val shapeless        = "com.chuusai"             %% "shapeless"        % shapelessV
  val acyclic          = "com.lihaoyi"             %% "acyclic"          % acyclicVersion % "provided"
  val guava            = "com.google.guava"         % "guava"            % guavaV

  val fs2 = Seq(
    "co.fs2" %% "fs2-core" % fs2Version,
    "org.typelevel" %% "cats-effect" % catsEffectV
  )


  val scrimageCore = "com.sksamuel.scrimage"   %% "scrimage-core"    % scrimageVersion

  val scrimageAll = Seq(
    scrimageCore,
    "com.sksamuel.scrimage" %% "scrimage-io-extra" % scrimageVersion,
    "com.sksamuel.scrimage" %% "scrimage-filters"  % scrimageVersion
  )

  val circeJson = Seq(
    "io.circe" %% "circe-generic"  % circeJsonVersion,
    "io.circe" %% "circe-parser"   % circeJsonVersion,
    "io.circe" %% "circe-literal"  % circeJsonVersion
  )

  val http4s = Seq(
    "org.reactormonk" %% "cryptobits"           % "1.2",
    "org.http4s"      %% "http4s-dsl"           % http4sVersion,
    "org.http4s"      %% "http4s-blaze-server"  % http4sVersion,
    "org.http4s"      %% "http4s-blaze-client"  % http4sVersion,
    "org.http4s"      %% "http4s-circe"         % http4sVersion,
    "org.typelevel"   %% "cats-core"            % catsV
  )

  val jwtCirce = Seq(
    "com.pauldijou" %% "jwt-core" % "0.14.1",
    "com.pauldijou" %% "jwt-circe" % "0.14.1"
  )

  val tsecV = "0.0.1-M9"
  val tsec = Seq(
    "io.github.jmcardon" %% "tsec-common" % tsecV,
    "io.github.jmcardon" %% "tsec-password" % tsecV,
    "io.github.jmcardon" %% "tsec-symmetric-cipher" % tsecV,
    "io.github.jmcardon" %% "tsec-mac" % tsecV,
    "io.github.jmcardon" %% "tsec-signatures" % tsecV,
    "io.github.jmcardon" %% "tsec-md" % tsecV,
    "io.github.jmcardon" %% "tsec-jwt-mac" % tsecV,
    "io.github.jmcardon" %% "tsec-jwt-sig" % tsecV,
    "io.github.jmcardon" %% "tsec-http4s" % tsecV
  )
}

object CommonLibs extends CommonLibs
