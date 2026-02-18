package dev.thymian.client.cli

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.util.concurrent.CompletableFuture

internal interface ThymianCLISessionManager {
    fun getThymianCLI(project: Project): CompletableFuture<ThymianCLI>

    companion object {
        fun getInstance(): ThymianCLISessionManager =
            ApplicationManager.getApplication().getService(ThymianCLISessionManager::class.java)
    }
}