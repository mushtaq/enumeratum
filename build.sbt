import com.typesafe.sbt.SbtGit.{GitKeys => git}
import sbtbuildinfo.BuildInfoPlugin.autoImport._
import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

lazy val scala_2_13Version = "2.13.1"
lazy val scalaVersionsAll  = Seq(scala_2_13Version)

lazy val theScalaVersion = scala_2_13Version

lazy val scalaTestVersion  = "3.1.1"
lazy val scalacheckVersion = "1.14.0"

lazy val baseProjectRefs =
  Seq(macrosJS, macrosJVM, coreJS, coreJVM, coreJVMTests).map(Project.projectToRef)

lazy val scala213ProjectRefs = Seq(
  ).map(Project.projectToRef)

lazy val scala_2_13 = Project(id = "scala_2_13", base = file("scala_2_13"))
  .settings(
    commonSettings ++ publishSettings,
    name := "enumeratum-scala_2_13",
    scalaVersion := scala_2_13Version, // not sure if this and below are needed
    crossScalaVersions := Seq(scala_2_13Version),
    crossVersion := CrossVersion.binary,
    // Do not publish this  project (it just serves as an aggregate)
    publishArtifact := false,
    publishLocal := {},
    //doctestWithDependencies := false, // sbt-doctest is not yet compatible with this 2.13
    aggregate in publish := false,
    aggregate in PgpKeys.publishSigned := false
  )
  .aggregate((baseProjectRefs ++ scala213ProjectRefs): _*)

lazy val integrationProjectRefs = Seq(
  ).map(Project.projectToRef)

lazy val root =
  Project(id = "enumeratum-root", base = file("."))
    .settings(commonWithPublishSettings: _*)
    .settings(
      name := "enumeratum-root",
      crossVersion := CrossVersion.binary,
      git.gitRemoteRepo := "git@github.com:lloydmeta/enumeratum.git",
      // Do not publish the root project (it just serves as an aggregate)
      publishArtifact := false,
      publishLocal := {},
      aggregate in publish := false,
      aggregate in PgpKeys.publishSigned := false
    )
    .aggregate(baseProjectRefs ++ integrationProjectRefs: _*)

lazy val macrosAggregate = aggregateProject("macros", macrosJS, macrosJVM)
lazy val macros = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("macros"))
  .settings(testSettings: _*)
  .settings(commonWithPublishSettings: _*)
  .settings(withCompatUnmanagedSources(jsJvmCrossProject = true, includeTestSrcs = false): _*)
  .settings(
    name := "enumeratum-macros",
    version := Versions.Macros.head,
    crossScalaVersions := scalaVersionsAll, // eventually move this to aggregateProject once more 2.13 libs are out
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    )
  )

lazy val macrosJS  = macros.js
lazy val macrosJVM = macros.jvm

// Aggregates core
lazy val coreAggregate = aggregateProject("core", coreJS, coreJVM)
lazy val core = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("enumeratum-core"))
  .settings(testSettings: _*)
  .settings(commonWithPublishSettings: _*)
  .settings(
    name := "enumeratum",
    version := Versions.Core.head,
    crossScalaVersions := scalaVersionsAll,
    libraryDependencies += "com.beachape" %% "enumeratum-macros" % Versions.Macros.stable
  )
//  .dependsOn(macros) // used for testing macros
lazy val coreJS  = core.js
lazy val coreJVM = core.jvm

lazy val testsAggregate = aggregateProject("test", enumeratumTestJs, enumeratumTestJvm)
// Project models used in test for some subprojects
lazy val enumeratumTest = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("enumeratum-test"))
  .settings(testSettings: _*)
  .settings(commonWithPublishSettings: _*)
  .settings(
    name := "enumeratum-test",
    version := Versions.Core.head,
    crossScalaVersions := scalaVersionsAll,
    libraryDependencies += {
      "com.beachape" %%% "enumeratum" % Versions.Core.stable
    }
  )
lazy val enumeratumTestJs  = enumeratumTest.js
lazy val enumeratumTestJvm = enumeratumTest.jvm

lazy val coreJVMTests = Project(id = "coreJVMTests", base = file("enumeratum-core-jvm-tests"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name,
                                       version,
                                       scalaVersion,
                                       sbtVersion,
                                       BuildInfoKey.action("macrosJVMClassesDir") {
                                         ((macrosJVM / classDirectory) in Compile).value
                                       }),
    buildInfoPackage := "enumeratum"
  )
  .settings(commonWithPublishSettings: _*)
  .settings(testSettings: _*)
  .settings(
    name := "coreJVMTests",
    version := Versions.Core.stable,
    crossScalaVersions := scalaVersionsAll,
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % Test
    ),
    publishArtifact := false,
    publishLocal := {}
  )
  .dependsOn(coreJVM, macrosJVM)

lazy val commonSettings = Seq(
  organization := "com.beachape",
  scalafmtOnCompile := true,
  scalaVersion := theScalaVersion,
  crossScalaVersions := scalaVersionsAll
) ++
  compilerSettings ++
  resolverSettings ++
  ideSettings

lazy val commonSettingsWithTrimmings =
  commonSettings ++
    scoverageSettings

lazy val commonWithPublishSettings =
  commonSettingsWithTrimmings ++
    publishSettings

lazy val resolverSettings = Seq(
  resolvers ++= Seq(
    "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/",
    "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    "Sonatype releases" at "https://oss.sonatype.org/content/repositories/releases"
  )
)

lazy val ideSettings = Seq(
  // Faster "sbt gen-idea"
  transitiveClassifiers in Global := Seq(Artifact.SourceClassifier)
)

lazy val compilerSettings = Seq(
  scalaJSStage in ThisBuild := {
    sys.props.get("sbt.scalajs.testOpt").orElse(sys.env.get("SCALAJS_TEST_OPT")) match {
      case Some("full") => FullOptStage
      case _            => FastOptStage
    }
  },
  scalacOptions in (Compile, compile) ++= {
    val base = Seq(
      "-Xlog-free-terms",
      "-encoding",
      "UTF-8", // yes, this is 2 args
      "-feature",
      "-language:existentials",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-unchecked",
      "-Xfatal-warnings",
//      "-Ywarn-adapted-args",
      "-Ywarn-dead-code", // N.B. doesn't work well with the ??? hole
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard",
      "-Xfuture"
    )
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, m)) if m >= 13 =>
        base.filterNot(flag => flag == "-Xfatal-warnings" || flag == "-Xfuture") ++ // todo see how to disable deprecations in 2.13.x
          Seq( /*"-deprecation:false", */ "-Xlint:-unused,_") // unused-import breaks Circe Either shim
      case Some((2, m)) if m >= 12 =>
        base ++ Seq("-deprecation:false", "-Xlint:-unused,_") // unused-import breaks Circe Either shim
      case Some((2, 11)) => base ++ Seq("-deprecation:false", "-Xlint", "-Ywarn-unused-import")
      case _             => base ++ Seq("-Xlint")
    }
  }
)

lazy val scoverageSettings = Seq(
  coverageExcludedPackages := """enumeratum\.EnumMacros;enumeratum\.ContextUtils;enumeratum\.ValueEnumMacros""",
  coverageHighlighting := true
)

// Settings for publishing to Maven Central
lazy val publishSettings = Seq(
  pomExtra :=
    <url>https://github.com/lloydmeta/enumeratum</url>
      <licenses>
        <license>
          <name>MIT</name>
          <url>http://opensource.org/licenses/MIT</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <developers>
        <developer>
          <id>lloydmeta</id>
          <name>Lloyd Chan</name>
          <url>https://beachape.com</url>
        </developer>
      </developers>,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (version.value.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  pgpPassphrase := sys.env.get("PGP_PASSPHRASE").map(_.toCharArray),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  PgpKeys.pgpPassphrase := sys.env.get("PGP_PASSPHRASE").map(_.toCharArray),
  pomIncludeRepository := { _ =>
    false
  }
)

val testSettings = {
  Seq(
    libraryDependencies ++= {
      Seq(
        "org.scalatest" %%% "scalatest" % scalaTestVersion % Test
      )
    },
    doctestGenTests := {
      Seq.empty
    },
    doctestTestFramework := DoctestTestFramework.ScalaTest
  )
}

lazy val benchmarking =
  Project(id = "benchmarking", base = file("benchmarking"))
    .settings(commonWithPublishSettings: _*)
    .settings(
      name := "benchmarking",
      crossVersion := CrossVersion.binary,
      // Do not publish
      publishArtifact := false,
      publishLocal := {}
    )
    .dependsOn((baseProjectRefs ++ integrationProjectRefs).map(ClasspathDependency(_, None)): _*)
    .enablePlugins(JmhPlugin)
    .settings(libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.21")

/**
  * Helper function to add unmanaged source compat directories for different scala versions
  */
def withCompatUnmanagedSources(jsJvmCrossProject: Boolean,
                               includeTestSrcs: Boolean): Seq[Setting[_]] = {
  def compatDirs(projectbase: File, scalaVersion: String, isMain: Boolean) = {
    val base = if (jsJvmCrossProject) projectbase / ".." else projectbase
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, scalaMajor)) if scalaMajor >= 13 =>
        Seq(base / "compat" / "src" / (if (isMain) "main" else "test") / "scala-2.13")
          .map(_.getCanonicalFile)
      case Some((2, scalaMajor)) if scalaMajor >= 11 =>
        Seq(base / "compat" / "src" / (if (isMain) "main" else "test") / "scala-2.11")
          .map(_.getCanonicalFile)
      case _ => Nil
    }
  }

  val unmanagedMainDirsSetting = Seq(
    unmanagedSourceDirectories in Compile ++= {
      compatDirs(projectbase = baseDirectory.value,
                 scalaVersion = scalaVersion.value,
                 isMain = true)
    }
  )
  if (includeTestSrcs) {
    unmanagedMainDirsSetting ++ {
      unmanagedSourceDirectories in Test ++= {
        compatDirs(projectbase = baseDirectory.value,
                   scalaVersion = scalaVersion.value,
                   isMain = false)
      }
    }
  } else {
    unmanagedMainDirsSetting
  }
}

/**
  * Assumes that
  *
  *   - a corresponding directory exists under ./aggregates.
  *   - publishing 2.11.x, 2.12.x, 2.13.x
  */
def aggregateProject(id: String, projects: ProjectReference*): Project =
  Project(id = s"$id-aggregate", base = file(s"./aggregates/$id"))
    .settings(commonWithPublishSettings: _*)
    .settings(
      crossScalaVersions := scalaVersionsAll,
      crossVersion := CrossVersion.binary,
      // Do not publish the aggregate project (it just serves as an aggregate)
      libraryDependencies += {
        "org.scalatest" %% "scalatest" % scalaTestVersion % Test
      },
      publishArtifact := false,
      publishLocal := {}
    )
    .aggregate(projects: _*)
