package io.concentric

import io.concentric.annotations.*

/**
 * Real Kotlin data class used as a JvmContract model.
 *
 * This file is compiled and exercised by KotlinContractSpec once Kotlin
 * compilation is enabled in build.sbt (see instructions below).
 *
 * ## Enabling Kotlin compilation
 *
 * Add to project/plugins.sbt:
 * ```
 * addSbtPlugin("com.hanhuy.sbt" % "kotlin-plugin" % "2.0.0")
 * ```
 *
 * Add to the `runtime` module in build.sbt:
 * ```scala
 * .enablePlugins(KotlinPlugin)
 * .settings(
 *   kotlinVersion := "1.9.22",
 *   libraryDependencies += "org.jetbrains.kotlin" % "kotlin-stdlib" % "1.9.22" % Test
 * )
 * ```
 *
 * ## Annotation placement
 *
 * `@field:` routes each annotation to the JVM backing field.
 * Without it the annotation goes to the constructor parameter.
 * JvmContractDeriver checks *both* locations, so both styles work.
 *
 * The tests in KotlinContractSpec cover both behaviours through the
 * Java-simulated variants (TestKotlinSimUser / TestKotlinFieldUser) and,
 * once this file is compiled, through this real Kotlin class too.
 */
@contract
data class TestKotlinUser(
    @field:immutable          val id: Long,
    @field:nonEmpty
    @field:maxLength(50)      val name: String,
    @field:min(0)
    @field:max(150)           val age: Int,
    @field:email              val email: String
)
