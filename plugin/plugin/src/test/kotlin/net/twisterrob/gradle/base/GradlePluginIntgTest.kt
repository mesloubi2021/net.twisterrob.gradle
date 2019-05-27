package net.twisterrob.gradle.base

import net.twisterrob.gradle.BaseIntgTest
import net.twisterrob.gradle.test.assertOutcome
import net.twisterrob.gradle.test.assertSuccess
import net.twisterrob.gradle.test.root
import org.gradle.testkit.runner.TaskOutcome
import org.hamcrest.Matchers.not
import org.hamcrest.io.FileMatchers.anExistingFile
import org.hamcrest.junit.MatcherAssert.assertThat
import org.hamcrest.junit.MatcherAssume.assumeThat
import org.intellij.lang.annotations.Language
import org.junit.Test

/**
 * @see GradlePlugin
 */
class GradlePluginIntgTest : BaseIntgTest() {

	@Test fun `skips debugWrapper if gradlew does not exist`() {
		assumeThat(gradle.root.resolve("gradlew.bat"), not(anExistingFile()))
		@Language("gradle")
		val script = """
			apply plugin: 'net.twisterrob.root'
		""".trimIndent()

		val result = gradle.run(script, "debugWrapper").build()

		result.assertOutcome(":debugWrapper", TaskOutcome.SKIPPED)
	}

	@Test fun `generates gradled if gradlew exists`() {
		gradle.file("", "gradlew.bat")
		@Language("gradle")
		val script = """
			apply plugin: 'net.twisterrob.root'
		""".trimIndent()

		val result = gradle.run(script, "debugWrapper").build()

		result.assertSuccess(":debugWrapper")
		assertThat(gradle.root.resolve("gradled.bat"), anExistingFile())
	}

	@Test fun `debugWrapper is incremental`() {
		gradle.file("", "gradlew.bat")
		@Language("gradle")
		val script = """
			apply plugin: 'net.twisterrob.root'
		""".trimIndent()

		gradle.run(script, "debugWrapper").build()
		assumeThat(gradle.root.resolve("gradled.bat"), anExistingFile())
		val result = gradle.run(null, "debugWrapper").build()

		result.assertOutcome(":debugWrapper", TaskOutcome.UP_TO_DATE)
	}
}
