package dev.thymian.intellijplugin.run

import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class ThymianRunConfigurationEditor : SettingsEditor<ThymianRunConfiguration>() {
    private lateinit var thymianBinaryField: JBTextField

    override fun resetEditorFrom(configuration: ThymianRunConfiguration) {
        // TODO fill UI from configuration
    }

    override fun applyEditorTo(configuration: ThymianRunConfiguration) {
        // TODO fill configuration from UI
    }

    override fun createEditor(): JComponent {
        return panel {
            row("Binary") {
                thymianBinaryField = textField()
                    .comment("Path to the Thymian binary")
                    .align(AlignX.FILL)
                    .component
            }
        }
    }
}