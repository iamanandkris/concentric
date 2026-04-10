import sbt.Keys._

val scala3Version    = "3.4.2"
val scalaTestVersion = "3.2.18"

lazy val commonSettings = Seq(
  organization  := "io.dsentric",
  version       := "0.1.0",
  scalaVersion  := scala3Version,
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xfatal-warnings"
  ),
  Test / scalacOptions --= Seq("-Xfatal-warnings")
)

// ── annotations ────────────────────────────────────────────────────────────
// Pure Java module — no Scala dependency.
// Defines every @interface used to annotate contract fields.
lazy val annotations = project
  .in(file("annotations"))
  .settings(commonSettings)
  .settings(moduleName := "dsentric-annotations")
  .settings(
    // Java-only: disable Scala library & cross-path handling
    crossPaths       := false,
    autoScalaLibrary := false,
    // The annotations module is pure @interface definitions that must be
    // readable by any Java 11+ JVM.  --release 11 pins the bytecode level
    // while still compiling fine on JVM 11, 17, 21, 25, etc.
    javacOptions ++= Seq("--release", "11"),
    // Disable doc generation/publishing to avoid Javadoc failures
    Compile / doc / skip := true,
    Compile / packageDoc / publishArtifact := false
  )

// ── core ───────────────────────────────────────────────────────────────────
// Scala 3 module.  Contains:
//   - Core types  (FieldPath, Violation, FieldMeta, RawDecoder, Contract)
//   - ContractImpl (runtime validation + sanitize + patch logic)
//   - Scala 3 macro  Contract.derived[T]  (compile-time annotation reading)
lazy val core = project
  .in(file("core"))
  .settings(moduleName := "dsentric-core")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalaTestVersion % Test
    )
  )
  .dependsOn(annotations)

// ── runtime ────────────────────────────────────────────────────────────────
// Scala 3 module.  Reflection-based JvmContract[T] for Java / Kotlin callers
// who cannot use the compile-time macro.
//
// Key types exposed to Java/Kotlin consumers:
//   - JvmContract[T]      — synchronous validate / validatePatch / sanitize
//   - ValidationResult[T] — isValid, getValue (Optional<T>), getErrors (List<JvmViolation>)
//   - JvmViolation        — flat string path/code/message (no Scala types)
lazy val runtime = project
  .in(file("runtime"))
  .settings(moduleName := "dsentric-runtime")
  .settings(commonSettings)
  .settings(
    // Conditionally exclude Java record test sources when running on JVM < 16.
    // Records are Java 16+ syntax; on older JVMs those files are skipped and
    // only the plain-Java Kotlin-sim tests and all Scala specs are compiled.
    // On JVM 16+ (including the developer's Java 25) all sources are included.
    Test / unmanagedSources / excludeFilter := {
      val raw = System.getProperty("java.version")
      val jvmMajor = scala.util.Try {
        if (raw.startsWith("1.")) raw.split("\\.")(1).toInt
        else                      raw.split("\\.")(0).toInt
      }.getOrElse(0)
      // On JVM < 16 exclude the Java record sources and the Scala spec that
      // references them.  KotlinInteropSpec uses only plain Java classes and
      // compiles on any JVM.  On JVM 16+ (dev machine) all sources compile.
      if (jvmMajor < 16)
        "TestJvmUser.java" || "TestJvmTicket.java" || "TestJvmProfile.java" ||
        "JvmContractSpec.scala"
      else
        NothingFilter
    },
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalaTestVersion % Test
    )
  )
  .dependsOn(core)

lazy val root = (project in file("."))
  .aggregate(annotations, core, runtime)
  .settings(
    name := "dsentric",
    publish / skip := true
  )
