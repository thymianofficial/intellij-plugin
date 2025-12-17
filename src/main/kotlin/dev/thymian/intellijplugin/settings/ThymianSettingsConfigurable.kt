package dev.thymian.intellijplugin.settings

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import dev.thymian.intellijplugin.ThymianBundle

class ThymianSettingsConfigurable : BoundSearchableConfigurable(
    ThymianBundle.message("settings.title"),
    "dev.thymian.intellijplugin.settings"
) {
    override fun createPanel() = panel {
        val settings = ThymianSettingsState.getInstance()
        group {
            row {
                textFieldWithBrowseButton(ThymianBundle.message("settings.cliPath.dialog.title"))
                    .label(ThymianBundle.message("settings.cliPath.label"))
                    .bindText(
                        getter = { settings.thymianCliPath },
                        setter = {
                            if (ThymianSettingsState.RUN_FILE_OPTIONS.none { opt -> it.endsWith(opt) }) {
                                settings.thymianCliPath = ""
                            } else {
                                settings.thymianCliPath = it
                            }
                        }
                    )
                    .align(AlignX.FILL)
                    .comment(ThymianBundle.message("settings.cliPath.comment"))
            }
            row {
                textField()
                    .label(ThymianBundle.message("settings.port.label"))
                    .bindIntText(
                        getter = { settings.websocketPort },
                        setter = { settings.websocketPort = it }
                    )
                    .align(AlignX.LEFT)
                    .comment(ThymianBundle.message("settings.port.comment"))
            }
        }
    }
}
