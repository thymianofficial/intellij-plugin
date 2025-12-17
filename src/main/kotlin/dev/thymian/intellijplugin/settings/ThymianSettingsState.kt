package dev.thymian.intellijplugin.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.application

@Service(Service.Level.APP)
@State(name = "ThymianSettings", storages = [Storage("ThymianSettings.xml")])
internal class ThymianSettingsState : PersistentStateComponent<ThymianSettingsState.State> {

    data class State(
        var thymianCliPath: String = "",
        var websocketPort: Int = DEFAULT_WEBSOCKET_PORT,
    )

    private var state = State()

    var thymianCliPath: String by state::thymianCliPath
    var websocketPort: Int by state::websocketPort

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        const val DEFAULT_WEBSOCKET_PORT = 51234
        val RUN_FILE_OPTIONS = listOf("/bin/run.js", "/bin/run.cmd")

        fun getInstance(): ThymianSettingsState =
            application.getService(ThymianSettingsState::class.java)
    }
}
