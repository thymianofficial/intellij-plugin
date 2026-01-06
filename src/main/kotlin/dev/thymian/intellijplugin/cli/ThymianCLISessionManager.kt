package dev.thymian.intellijplugin.cli

import com.intellij.openapi.application.ApplicationManager
import java.util.concurrent.CompletableFuture

internal interface ThymianCLISessionManager {
    fun getThymianCLI(): CompletableFuture<ThymianCLI>

    companion object {
        fun getInstance(): ThymianCLISessionManager =
            ApplicationManager.getApplication().getService(ThymianCLISessionManager::class.java)
    }
}