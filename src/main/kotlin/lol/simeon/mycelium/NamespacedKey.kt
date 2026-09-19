package lol.simeon.mycelium

import java.util.*

data class NamespacedKey(val namespace: String, val key: String) {
    init {
        require(namespace.isNotEmpty()) { "Namespace cannot be empty" }
        require(key.isNotEmpty()) { "Key cannot be empty" }
    }

    override fun toString(): String {
        return "$namespace:$key"
    }

    companion object {
        private const val DEFAULT_NAMESPACE = "minecraft"

        fun minecraft(key: String): NamespacedKey {
            return NamespacedKey(DEFAULT_NAMESPACE, key.lowercase(Locale.ROOT))
        }
    }
}
