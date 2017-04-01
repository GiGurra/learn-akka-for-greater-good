val versions = new {
  val scalatest = "3.0.1"
  val mockito = "2.7.19"
  val akka = "2.4.17"
}

val commonSettings = Seq(
  organization := "com.github.gigurra",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.12.1",
  scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation"),
  libraryDependencies ++= Seq(
    "com.typesafe.akka"  %% "akka-actor"          %   versions.akka,
    "org.scalatest"      %% "scalatest"           %   versions.scalatest     % "test",
    "org.mockito"        %  "mockito-core"        %   versions.mockito       % "test"
  ),
  resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
)

def module(name: String, dependencies: sbt.ClasspathDep[sbt.ProjectReference]*): Project = Project(
  id = name,
  base = file(name),
  settings = commonSettings,
  dependencies = dependencies
)

val core = module("core")

val aggregate = (project in file(".")).aggregate(core)
