package dev.nybroe.collector.quickFixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.PhpPsiElementFactory
import com.jetbrains.php.lang.psi.elements.ForeachStatement
import com.jetbrains.php.lang.psi.elements.GroupStatement
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.Statement
import com.jetbrains.php.lang.psi.elements.Variable

class ForeachToCollectionQuickFix : LocalQuickFix {
    companion object {
        const val QUICK_FIX_NAME = "Refactor foreach to collection"
    }

    override fun getFamilyName(): String {
        return QUICK_FIX_NAME
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val foreach = descriptor.psiElement

        if (foreach !is ForeachStatement) {
            return
        }

        val statementText = when (val statement = foreach.statement) {
            is GroupStatement -> statement.statements.joinToString("\n") { it.text }
            is Statement -> statement.text
            else -> return
        }

        val arguments = createArguments(project, foreach)

        val useList = createUseListStatement(
            foreach,
            listOf(
                foreach.key?.name,
                foreach.value?.name,
                "this"
            ).asSequence().filterNotNull().toList()
        ) ?: ""

        val psiCollectionCall = PhpPsiElementFactory.createStatement(
            project,
            "collect(${foreach.array!!.text})->each(function(${arguments.text}) $useList {$statementText});"
        )

        descriptor.psiElement.replace(
            psiCollectionCall
        )
    }

    private fun createArguments(project: Project, foreach: ForeachStatement): ParameterList {
        return PhpPsiElementFactory.createArgumentList(
            project,
            foreach.value!!.text +
                if (foreach.key != null) ", ${foreach.key!!.text}" else ""
        )
    }

    private fun createUseListStatement(foreach: ForeachStatement, ignoredNames: List<String>): String? {
        return PsiTreeUtil.collectElementsOfType(foreach.statement, Variable::class.java)
            .map { it.name }
            // Filter empty variables. (happens with expressions in string interpolation)
            .filter { it.isNotBlank() }
            // Filter all ignored names.
            .filter { !ignoredNames.contains(it) }
            // Early return if we have no variables left.
            .ifEmpty { return null }
            // Remove all duplicates
            .distinct()
            // Convert it to a use statement string.
            .joinToString(separator = ", ") { "$$it" }
            .let { "use ($it)" }
    }
}
