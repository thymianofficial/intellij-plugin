package dev.thymian.plugin.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.textFieldWithBrowseButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import dev.thymian.plugin.ThymianBundle
import javax.swing.JComponent

internal class ThymianSettingsConfigurable : SearchableConfigurable {
    private val settingsProvider = ThymianSettings.getInstance()

    private lateinit var executablePathInput: TextFieldWithBrowseButton
    private lateinit var websocketPortInput: JBTextField

    override fun getDisplayName(): String = "Thymian"
    override fun getId(): String = "dev.thymian.intellijplugin.settings"

    override fun createComponent(): JComponent {
        val descriptor = FileChooserDescriptorFactory.singleFile()
            .withExtensionFilter("Executables", "js", "cmd")
            .withTitle(ThymianBundle.message("settings.cliPath.dialog.title"))
        executablePathInput = textFieldWithBrowseButton(null, descriptor)

        websocketPortInput = JBTextField()

        return panel {
            group {
                row {
                    label(ThymianBundle.message("settings.cliPath.label"))
                    cell(executablePathInput)
                        .align(AlignX.FILL)
                        .comment(ThymianBundle.message("settings.cliPath.comment"))
                }.layout(RowLayout.PARENT_GRID)
                row {
                    label(ThymianBundle.message("settings.port.label"))
                    cell(websocketPortInput)
                        .align(AlignX.LEFT)
                        .comment(ThymianBundle.message("settings.port.comment"))
                }.layout(RowLayout.PARENT_GRID)
            }
        }
    }

    override fun isModified(): Boolean {
        return settingsProvider.thymianCliPath != executablePathInput.text ||
                settingsProvider.websocketPort != (websocketPortInput.text.toIntOrNull() ?: 0)
    }

    override fun apply() {
        settingsProvider.thymianCliPath = executablePathInput.text
        settingsProvider.websocketPort = websocketPortInput.text.toIntOrNull() ?: 0
    }

    override fun reset() {
        executablePathInput.text = settingsProvider.thymianCliPath
        websocketPortInput.text = settingsProvider.websocketPort.toString()
    }
}
