import com.typesafe.tools.mima.core._
import Dependencies._
val scala211 = "2.11.12"
val scala212 = "2.12.16"
val scala213 = "2.13.8"
val scala31 = "3.1.2"

addCommandAlias("fmt", "; scalafmtAll; scalafmtSbt")
addCommandAlias("fmtCheck", "; scalafmtCheckAll; scalafmtSbtCheck")

tlReplaceCommandAlias("prePR", "; githubWorkflowGenerate ; +fmt; bench/compile; +test")

ThisBuild / tlBaseVersion := "0.3"
ThisBuild / startYear := Some(2021)
ThisBuild / developers += tlGitHubDev("johnynek", "P. Oscar Boykin")

ThisBuild / crossScalaVersions := List(scala31, scala211, scala212, scala213)

ThisBuild / tlVersionIntroduced := Map("3" -> "0.3.4")
ThisBuild / tlSkipIrrelevantScalas := true

ThisBuild / githubWorkflowAddedJobs ++= Seq(
  WorkflowJob(
    id = "coverage",
    name = "Generate coverage report",
    scalas = List(scala213),
    steps = List(WorkflowStep.Checkout) ++ WorkflowStep.SetupJava(
      githubWorkflowJavaVersions.value.toList
    ) ++ githubWorkflowGeneratedCacheSteps.value ++ List(
      WorkflowStep.Sbt(List("coverage", "test", "coverageAggregate")),
      WorkflowStep.Run(List("bash <(curl -s https://codecov.io/bash)"))
    )
  )
)

ThisBuild / licenses := List(License.MIT)

lazy val root = tlCrossRootProject.aggregate(core, bench)

lazy val docs =
  project.in(file("site")).enablePlugins(TypelevelSitePlugin).dependsOn(core.jvm, bench)

lazy val isScala211 = Def.setting {
  scalaBinaryVersion.value == "2.11"
}

lazy val core = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .settings(
    name := "cats-parse",
    tlFatalWarningsInCi := !tlIsScala3.value,
    libraryDependencies ++= {
      if (tlIsScala3.value) Nil else Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
    },
    libraryDependencies ++= {
      val isScala211 = CrossVersion.partialVersion(scalaVersion.value).contains((2, 11))
      Seq(
        if (isScala211) cats211.value else cats.value,
        munit.value % Test,
        munitScalacheck.value % Test
      )
    },
    scalacOptions ++= {
      // this code seems to trigger a bug in 2.11 pattern analysis
      if (isScala211.value) List("-Xno-patmat-analysis") else Nil
    },
    mimaPreviousArtifacts := {
      if (isScala211.value) Set.empty else mimaPreviousArtifacts.value
    },
    mimaBinaryIssueFilters ++= {
      /*
       * It is okay to filter anything in Impl or RadixNode which are private
       */
      if (tlIsScala3.value)
        List(
          ProblemFilters.exclude[DirectMissingMethodProblem]("cats.parse.Parser#Error.fromProduct"),
          ProblemFilters.exclude[IncompatibleResultTypeProblem]("cats.parse.Parser#Error.unapply"),
          ProblemFilters.exclude[MissingTypesProblem]("cats.parse.Parser$Error$"),
          ProblemFilters.exclude[IncompatibleResultTypeProblem]("cats.parse.Parser#Error.unapply"),
          ProblemFilters.exclude[DirectMissingMethodProblem]("cats.parse.Parser#Error.fromProduct")
        )
      else Nil
    } ++ MimaExclusionRules.parserImpl ++ MimaExclusionRules.bitSetUtil
  )
  .jsSettings(
    crossScalaVersions := (ThisBuild / crossScalaVersions).value.filterNot(_.startsWith("2.11")),
    coverageEnabled := false
  )
  .nativeSettings(
    resolvers ++= Seq(
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
      "Sonatype OSS Snapshots s01" at "https://s01.oss.sonatype.org/content/repositories/snapshots"
    ),
    crossScalaVersions := (ThisBuild / crossScalaVersions).value.filterNot(_.startsWith("2.11")),
    mimaPreviousArtifacts := Set.empty,
    libraryDependencies := {
      if (scalaVersion.value.startsWith("3.1")) {
        libraryDependencies.value.filterNot(_ == Dependencies.cats) ++ Seq(
          Dependencies.catsSnapshot.value
        )
      } else { libraryDependencies.value }
    },
    coverageEnabled := false
  )

lazy val bench = project
  .enablePlugins(JmhPlugin, NoPublishPlugin)
  .settings(
    name := "bench",
    coverageEnabled := false,
    crossScalaVersions := (ThisBuild / crossScalaVersions).value.filter { v =>
      v.startsWith("2.12") || v.startsWith("2.13")
    },
    libraryDependencies ++=
      Seq(
        fastParse,
        parsley,
        jawnAst,
        parboiled,
        attoCore
      ),
    githubWorkflowArtifactUpload := false
  )
  .dependsOn(core.jvm)
