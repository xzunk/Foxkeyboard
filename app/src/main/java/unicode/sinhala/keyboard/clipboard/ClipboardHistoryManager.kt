package unicode.sinhala.keyboard.clipboard

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ClipboardItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)

object ClipboardHistoryManager {

    private const val PREF_NAME = "fox_clipboard_prefs"
    private const val KEY_ITEMS = "clipboard_items_json"
    private const val KEY_ENABLED = "clipboard_history_enabled"
    private const val MAX_UNPINNED_ITEMS = 30
    private const val UNPINNED_EXPIRATION_MS = 60 * 60 * 1000L // 1 hour like Gboard

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) {
            // Option to purge unpinned items when user disables clipboard history for privacy
            clearUnpinned(context)
        }
    }

    @Synchronized
    fun getItems(context: Context): List<ClipboardItem> {
        if (!isEnabled(context)) return emptyList()

        val jsonStr = getPrefs(context).getString(KEY_ITEMS, null) ?: return emptyList()
        val items = mutableListOf<ClipboardItem>()
        val now = System.currentTimeMillis()
        var needsSave = false

        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val text = obj.optString("text", "")
                val timestamp = obj.optLong("timestamp", now)
                val isPinned = obj.optBoolean("isPinned", false)

                if (text.isBlank()) continue

                // Check expiration for unpinned clips (1 hour)
                if (!isPinned && (now - timestamp > UNPINNED_EXPIRATION_MS)) {
                    needsSave = true
                    continue
                }

                items.add(ClipboardItem(id, text, timestamp, isPinned))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (needsSave) {
            saveItemsInternal(context, items)
        }

        // Return sorted: pinned items first, then newer items first
        return items.sortedWith(compareByDescending<ClipboardItem> { it.isPinned }.thenByDescending { it.timestamp })
    }

    @Synchronized
    fun addClip(context: Context, text: String): ClipboardItem? {
        if (!isEnabled(context)) return null
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val currentItems = getItems(context).toMutableList()

        // If duplicate item exists, remove old instance so we bump it to the top
        val existingIndex = currentItems.indexOfFirst { it.text == trimmed }
        val wasPinned = if (existingIndex != -1) currentItems[existingIndex].isPinned else false
        if (existingIndex != -1) {
            currentItems.removeAt(existingIndex)
        }

        val newItem = ClipboardItem(
            text = trimmed,
            timestamp = System.currentTimeMillis(),
            isPinned = wasPinned
        )

        currentItems.add(0, newItem)

        // Limit number of unpinned items
        val unpinned = currentItems.filter { !it.isPinned }
        if (unpinned.size > MAX_UNPINNED_ITEMS) {
            val overflowCount = unpinned.size - MAX_UNPINNED_ITEMS
            var removed = 0
            val iterator = currentItems.iterator()
            while (iterator.hasNext() && removed < overflowCount) {
                val item = iterator.next()
                if (!item.isPinned) {
                    iterator.remove()
                    removed++
                }
            }
        }

        saveItemsInternal(context, currentItems)
        return newItem
    }

    @Synchronized
    fun togglePin(context: Context, id: String) {
        val currentItems = getItems(context).toMutableList()
        val index = currentItems.indexOfFirst { it.id == id }
        if (index != -1) {
            val old = currentItems[index]
            currentItems[index] = old.copy(isPinned = !old.isPinned)
            saveItemsInternal(context, currentItems)
        }
    }

    @Synchronized
    fun deleteItem(context: Context, id: String) {
        val currentItems = getItems(context).toMutableList()
        if (currentItems.removeAll { it.id == id }) {
            saveItemsInternal(context, currentItems)
        }
    }

    @Synchronized
    fun clearUnpinned(context: Context) {
        val currentItems = getItems(context).filter { it.isPinned }
        saveItemsInternal(context, currentItems)
    }

    @Synchronized
    fun clearAll(context: Context) {
        saveItemsInternal(context, emptyList())
    }

    fun getLatestClipIfRecent(context: Context, maxAgeMs: Long = 60_000L): ClipboardItem? {
        val items = getItems(context)
        val latest = items.maxByOrNull { it.timestamp } ?: return null
        val now = System.currentTimeMillis()
        if (now - latest.timestamp <= maxAgeMs) {
            return latest
        }
        return null
    }

    private fun saveItemsInternal(context: Context, items: List<ClipboardItem>) {
        val jsonArray = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("text", item.text)
            obj.put("timestamp", item.timestamp)
            obj.put("isPinned", item.isPinned)
            jsonArray.put(obj)
        }
        getPrefs(context).edit().putString(KEY_ITEMS, jsonArray.toString()).apply()
    }
}
