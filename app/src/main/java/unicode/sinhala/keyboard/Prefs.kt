package unicode.sinhala.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration

class Prefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var layoutEnglish: Boolean
        get() = prefs.getBoolean("layout_english", true)
        set(value) = prefs.edit().putBoolean("layout_english", value).apply()

    var layoutWijesekara: Boolean
        get() = prefs.getBoolean("layout_wijesekara", false)
        set(value) = prefs.edit().putBoolean("layout_wijesekara", value).apply()

    var layoutSinglish: Boolean
        get() = prefs.getBoolean("layout_singlish", true)
        set(value) = prefs.edit().putBoolean("layout_singlish", value).apply()

    var automaticTheme: Boolean
        get() = prefs.getBoolean("automatic_theme", true)
        set(value) = prefs.edit().putBoolean("automatic_theme", value).apply()

    var darkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", false)
        set(value) = prefs.edit().putBoolean("dark_theme", value).apply()

    var keyBorders: Boolean
        get() = prefs.getBoolean("key_borders", true)
        set(value) = prefs.edit().putBoolean("key_borders", value).apply()

    var sinhalaKeyLabels: Boolean
        get() = prefs.getBoolean("sinhala_key_labels", true)
        set(value) = prefs.edit().putBoolean("sinhala_key_labels", value).apply()

    var swipeToErase: Boolean
        get() = prefs.getBoolean("swipe_to_erase", true)
        set(value) = prefs.edit().putBoolean("swipe_to_erase", value).apply()

    var swipeToMoveCursor: Boolean
        get() = prefs.getBoolean("swipe_to_move_cursor", true)
        set(value) = prefs.edit().putBoolean("swipe_to_move_cursor", value).apply()

    var heightPercentage: Int
        get() = prefs.getInt("height_percentage", 124)
        set(value) = prefs.edit().putInt("height_percentage", value).apply()

    var textSize: Int
        get() = prefs.getInt("text_size", 34)
        set(value) = prefs.edit().putInt("text_size", value).apply()

    var vibration: Boolean
        get() = prefs.getBoolean("vibration", false)
        set(value) = prefs.edit().putBoolean("vibration", value).apply()

    fun getKeyboardLayout(): KeyboardLayout {
        return when {
            layoutEnglish -> KeyboardLayout.ENGLISH
            layoutSinglish -> KeyboardLayout.SINGLISH
            layoutWijesekara -> KeyboardLayout.WIJESEKARA
            else -> KeyboardLayout.ENGLISH
        }
    }

    companion object {
        fun getRowHeight(context: Context): Int {
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val percentage = prefs.getInt("height_percentage", 124)
            val baseDp = 50f
            val density = context.resources.displayMetrics.density
            val basePx = baseDp * density
            return (basePx * (percentage / 100f)).toInt()
        }

        fun getDarkTheme(context: Context): Boolean {
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("automatic_theme", true)) {
                val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            }
            return prefs.getBoolean("dark_theme", false)
        }

        fun getAutomaticTheme(context: Context): Boolean {
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean("automatic_theme", true)
        }

        fun getKeyBorders(context: Context): Boolean {
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean("key_borders", true)
        }

        fun getSwipeToErase(context: Context): Boolean {
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean("swipe_to_erase", true)
        }

        fun getSwipeToMoveCursor(context: Context): Boolean {
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean("swipe_to_move_cursor", true)
        }

        fun getTextSize(context: Context): Int {
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            return prefs.getInt("text_size", 34)
        }

        fun getKeyboardLayout(context: Context): KeyboardLayout {
            // Prefer the first enabled layout to maintain consistent cycling order
            val enabled = getEnabledLayouts(context)
            return enabled.firstOrNull() ?: KeyboardLayout.ENGLISH
        }

        // Persisted selected layout helpers
        fun getSelectedLayout(context: Context): KeyboardLayout {
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val selected = prefs.getString("selected_layout", null)
            val enabled = getEnabledLayouts(context)
            if (selected != null) {
                try {
                    val layout = KeyboardLayout.valueOf(selected)
                    if (enabled.contains(layout)) return layout
                } catch (t: Throwable) {
                    // ignore and fall back
                }
            }
            return enabled.firstOrNull() ?: KeyboardLayout.ENGLISH
        }

        fun setSelectedLayout(context: Context, layout: KeyboardLayout) {
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("selected_layout", layout.name).apply()
        }

        fun getVibration(context: Context): Boolean {
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean("vibration", false)
        }

        // New helper: return the list of enabled layouts in priority order
        fun getEnabledLayouts(context: Context): List<KeyboardLayout> {
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val enabled = mutableListOf<KeyboardLayout>()
            if (prefs.getBoolean("layout_english", true)) enabled.add(KeyboardLayout.ENGLISH)
            if (prefs.getBoolean("layout_singlish", true)) enabled.add(KeyboardLayout.SINGLISH)
            if (prefs.getBoolean("layout_wijesekara", false)) enabled.add(KeyboardLayout.WIJESEKARA)
            // Ensure at least English is available
            if (enabled.isEmpty()) enabled.add(KeyboardLayout.ENGLISH)
            return enabled
        }
    }
}
