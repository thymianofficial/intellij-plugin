package dev.thymian.client.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "ThymianSettings",
    storages = [Storage("thymian.xml")]
)
internal class ThymianSettings : PersistentStateComponent<ThymianSettings.State> {

    data class State(
        var thymianCliPath: String = "",
        var websocketPort: Int = DEFAULT_WEBSOCKET_PORT,
    )

    private var state = State()

    var thymianCliPath: String
        get() = state.thymianCliPath
        set(value) {
            state.thymianCliPath = value
        }

    var websocketPort: Int
        get() = state.websocketPort
        set(value) {
            state.websocketPort = value
        }

    override fun getState(): State {
        return state
    }

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        const val DEFAULT_WEBSOCKET_PORT = 51234
        val RUN_FILE_OPTIONS = listOf("/bin/run.js", "/bin/run.cmd", "/bin/dev.js", "/bin/dev.cmd")

        fun getInstance(): ThymianSettings = ApplicationManager.getApplication().getService(ThymianSettings::class.java)
    }
}