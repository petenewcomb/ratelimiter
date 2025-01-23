plugins {
  // Support convention plugins written in Kotlin. Convention plugins are build scripts in
  // 'src/main' that automatically become available as plugins in the main build.
  `kotlin-dsl`

  // Use spotless for buildSrc too
  id("com.diffplug.spotless")
}

dependencies {
  val spotlessVersion: String by properties

  // Include as dependency so spotless can be applied to regular builds
  implementation("com.diffplug.spotless:spotless-plugin-gradle:$spotlessVersion")
}

repositories {
  // Use the plugin portal to apply community plugins in convention plugins.
  gradlePluginPortal()
}

spotless {
  kotlinGradle {
    ktlint()
    ktfmt()
    trimTrailingWhitespace()
    endWithNewline()
  }
}

tasks.named("jar") { dependsOn("spotlessCheck") }
