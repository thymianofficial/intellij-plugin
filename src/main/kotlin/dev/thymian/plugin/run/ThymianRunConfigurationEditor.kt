package dev.thymian.plugin.run

import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import dev.thymian.plugin.ThymianBundle
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
            row(ThymianBundle.message("runConfiguration.fields.binary.label")) {
                thymianBinaryField = textField()
                    .comment(ThymianBundle.message("runConfiguration.fields.binary.comment"))
                    .align(AlignX.FILL)
                    .component
            }
        }
    }
}