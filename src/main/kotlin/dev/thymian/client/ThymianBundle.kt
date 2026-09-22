package dev.thymian.client

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

@NonNls
private const val BUNDLE = "messages.ThymianBundle"

internal object ThymianBundle {
    private val INSTANCE = DynamicBundle(ThymianBundle::class.java, BUNDLE)

    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): @Nls String =
        INSTANCE.getMessage(key, *params)

    @Suppress("unused")
    fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): Supplier<@Nls String> =
        INSTANCE.getLazyMessage(key, *params)
}
