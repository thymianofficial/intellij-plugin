package dev.thymian.intellijplugin.cli

class ActionListener<S : ActionResultMessage<T>, T : Any>(
    private val onResult: (S) -> Unit,
    val onError: (Receiving.ActionErrorMessage) -> Unit
) {
    fun handleResult(result: ActionResultMessage<*>) {
        @Suppress("UNCHECKED_CAST")
        onResult(result as S)
    }
}