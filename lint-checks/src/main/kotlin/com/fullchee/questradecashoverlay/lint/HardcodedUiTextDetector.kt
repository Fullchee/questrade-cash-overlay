package com.fullchee.questradecashoverlay.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.skipParenthesizedExprDown

private const val RESOURCE_MESSAGE = "Use a string resource for shown text"

private val textCallOwners = mapOf(
    "Text" to "androidx.compose.",
    "setContentTitle" to "android.app.Notification.Builder",
    "setContentText" to "android.app.Notification.Builder"
)

class HardcodedUiTextDetector :
    Detector(),
    SourceCodeScanner {
    override fun getApplicableMethodNames() = textCallOwners.keys.toList()

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Compose's compiled Text has a mangled JVM name, so match on the name the call site uses.
        val ownerPrefix = textCallOwners[node.methodName] ?: return
        val owner = method.containingClass?.qualifiedName ?: return
        if (!owner.startsWith(ownerPrefix)) return
        node.valueArguments
            .filter { literalText(it).any(Char::isLetter) }
            .forEach { context.report(ISSUE, it, context.getLocation(it), RESOURCE_MESSAGE) }
    }

    private fun literalText(argument: UExpression): String =
        when (val expression = argument.skipParenthesizedExprDown()) {
            is ULiteralExpression -> expression.value as? String ?: ""
            is UPolyadicExpression -> expression.operands.joinToString("") { literalText(it) }
            else -> ""
        }

    companion object {
        val ISSUE = Issue.create(
            id = "HardcodedUiText",
            briefDescription = "Hard-coded UI text",
            explanation = "Shown text needs a string resource so it can be translated.",
            category = Category.I18N,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                HardcodedUiTextDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
