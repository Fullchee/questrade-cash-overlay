package com.fullchee.questradecashoverlay.lint

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.junit.Test

class HardcodedUiTextDetectorTest {
    private fun lint() = TestLintTask.lint().issues(HardcodedUiTextDetector.ISSUE)

    private val composeText: TestFile = kotlin(
        """
        package androidx.compose.material3

        fun Text(text: String, color: Int = 0) {}
        """
    ).indented()

    private fun lintScreen(body: String) = lint()
        .files(
            composeText,
            kotlin(
                """
                package test

                import android.app.Notification
                import androidx.compose.material3.Text

                fun stringResource(id: Int): String = ""

                $body
                """
            ).indented()
        )
        .run()

    @Test
    fun `a string literal in Compose Text is an error`() {
        lintScreen("""fun Screen() { Text("Add login") }""")
            .expectErrorCount(1)
            .expectContains("Use a string resource")
    }

    @Test
    fun `a string template with words in Compose Text is an error`() {
        lintScreen("""fun Screen(name: String) { Text("Remove ${'$'}name?") }""")
            .expectErrorCount(1)
    }

    @Test
    fun `a string resource in Compose Text is clean`() {
        lintScreen("""fun Screen() { Text(stringResource(1)) }""").expectClean()
    }

    @Test
    fun `a variable in Compose Text is clean`() {
        lintScreen("""fun Screen(name: String) { Text(name) }""").expectClean()
    }

    @Test
    fun `a literal without letters in Compose Text is clean`() {
        lintScreen("""fun Screen(name: String) { Text("• ${'$'}name") }""").expectClean()
    }

    @Test
    fun `a Text function outside Compose is ignored`() {
        lint()
            .files(
                kotlin(
                    """
                    package test

                    fun Text(text: String) {}

                    fun Screen() { Text("Add login") }
                    """
                ).indented()
            ).run()
            .expectClean()
    }

    @Test
    fun `a string literal as a notification title is an error`() {
        lintScreen(
            """fun alert(builder: Notification.Builder) { builder.setContentTitle("Needs reconnecting") }"""
        ).expectErrorCount(1)
    }

    @Test
    fun `a string literal as notification text is an error`() {
        lintScreen(
            """fun alert(builder: Notification.Builder) { builder.setContentText("Paste a new token") }"""
        ).expectErrorCount(1)
    }

    @Test
    fun `a notification title from a resource is clean`() {
        lintScreen(
            """fun alert(builder: Notification.Builder) { builder.setContentTitle(stringResource(1)) }"""
        ).expectClean()
    }
}
