package dev.thymian.intellijplugin.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Service(Service.Level.APP)
@State(name = "ThymianSettings", storages = [Storage("ThymianSettings.xml")])
internal class ThymianSettingsState : PersistentStateComponent<ThymianSettingsState.State> {

    data class State(
        val thymianCliPath: String = "",
        val websocketPort: Int = DEFAULT_WEBSOCKET_PORT,
    )

    private val _stateFlow = MutableStateFlow<ThymianSettingsState.State>(State())
    val stateFlow = _stateFlow.asStateFlow()

    var thymianCliPath: String
        get() = stateFlow.value.thymianCliPath
        set(path) {
            _stateFlow.apply { value = value.copy(thymianCliPath = path) }
        }

    var websocketPort: Int
        get() = stateFlow.value.websocketPort
        set(port) {
            _stateFlow.apply { value = value.copy(websocketPort = port) }
        }

    override fun getState(): State = stateFlow.value

    override fun loadState(state: State) {
        _stateFlow.value = state
    }

    companion object {
        const val DEFAULT_WEBSOCKET_PORT = 51234
        val RUN_FILE_OPTIONS = listOf("/bin/run.js", "/bin/run.cmd")

        fun getInstance(): ThymianSettingsState =
            application.getService(ThymianSettingsState::class.java)
    }
}
