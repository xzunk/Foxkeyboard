package unicode.sinhala.keyboard.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import unicode.sinhala.com.BuildConfig
import unicode.sinhala.com.R
import unicode.sinhala.keyboard.DonateActivity
import unicode.sinhala.keyboard.clipboard.ClipboardHistoryManager
import unicode.sinhala.keyboard.ui.components.PreferenceItem
import unicode.sinhala.keyboard.ui.components.SettingsCategory
import unicode.sinhala.keyboard.ui.components.SliderPreference
import unicode.sinhala.keyboard.ui.components.SwitchPreference

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        SettingsCategory(title = "Languages")

        val layoutEnglish = rememberBooleanPreference(context, "layout_english", true)
        SwitchPreference(
            title = "English",
            checked = layoutEnglish.value,
            onCheckedChange = { layoutEnglish.value = it }
        )

        val layoutWijesekara = rememberBooleanPreference(context, "layout_wijesekara", false)
        SwitchPreference(
            title = stringResource(R.string.wijesekara),
            checked = layoutWijesekara.value,
            onCheckedChange = { layoutWijesekara.value = it }
        )

        val layoutSinglish = rememberBooleanPreference(context, "layout_singlish", true)
        SwitchPreference(
            title = stringResource(R.string.singlish),
            checked = layoutSinglish.value,
            onCheckedChange = { layoutSinglish.value = it }
        )

        SettingsCategory(title = "Appearance & Theme")

        val automaticTheme = rememberBooleanPreference(context, "automatic_theme", true)
        SwitchPreference(
            title = "ස්වයංක්‍රීය තේමාව",
            summary = "System Material You dynamic colors",
            checked = automaticTheme.value,
            onCheckedChange = { automaticTheme.value = it }
        )

        val darkTheme = rememberBooleanPreference(context, "dark_theme", false)
        if (!automaticTheme.value) {
            SwitchPreference(
                title = "අඳුරු වර්ණ",
                checked = darkTheme.value,
                onCheckedChange = { darkTheme.value = it }
            )
        }

        val keyBorders = rememberBooleanPreference(context, "key_borders", true)
        SwitchPreference(
            title = "යතුරු මායිම්",
            checked = keyBorders.value,
            onCheckedChange = { keyBorders.value = it }
        )

        SettingsCategory(title = "Keyboard Dimensions (6\"+ Phones)")

        val heightPercentage = rememberIntPreference(context, "height_percentage", 124)
        SliderPreference(
            title = "උස (Height)",
            value = heightPercentage.value,
            unit = "%",
            range = 80f..140f,
            onValueChange = { heightPercentage.value = it }
        )

        val textSize = rememberIntPreference(context, "text_size", 34)
        SliderPreference(
            title = "අකුරුවල ප්‍රමාණය (Text Size)",
            value = textSize.value,
            unit = " sp",
            range = 20f..40f,
            onValueChange = { textSize.value = it }
        )

        SettingsCategory(title = "Gestures & Feedback")

        val swipeToErase = rememberBooleanPreference(context, "swipe_to_erase", true)
        SwitchPreference(
            title = "Swipe Left on Backspace to Erase",
            summary = "Drag backspace key to delete text quickly",
            checked = swipeToErase.value,
            onCheckedChange = { swipeToErase.value = it }
        )

        val swipeToMoveCursor = rememberBooleanPreference(context, "swipe_to_move_cursor", true)
        SwitchPreference(
            title = "Swipe Spacebar to Move Cursor",
            summary = "Slide left/right on spacebar to navigate cursor",
            checked = swipeToMoveCursor.value,
            onCheckedChange = { swipeToMoveCursor.value = it }
        )

        val vibration = rememberBooleanPreference(context, "vibration", false)
        SwitchPreference(
            title = "Haptic Vibration Feedback",
            summary = "Vibrate briefly on key presses",
            checked = vibration.value,
            onCheckedChange = { vibration.value = it }
        )

        SettingsCategory(title = "Smart Clipboard")

        var isClipboardEnabled by remember {
            mutableStateOf(ClipboardHistoryManager.isEnabled(context))
        }
        SwitchPreference(
            title = "Clipboard History",
            summary = "Automatically save copied text locally",
            checked = isClipboardEnabled,
            onCheckedChange = { checked ->
                isClipboardEnabled = checked
                ClipboardHistoryManager.setEnabled(context, checked)
            }
        )

        PreferenceItem(
            title = "Clear Clipboard History",
            summary = "Delete all stored copied items",
            onClick = {
                ClipboardHistoryManager.clearAll(context)
                Toast.makeText(context, "Clipboard history cleared", Toast.LENGTH_SHORT).show()
            }
        )

        SettingsCategory(title = "Support & About")

        PreferenceItem(
            title = "Buy Me a Coffee",
            summary = "Support development",
            onClick = {
                context.startActivity(Intent(context, DonateActivity::class.java))
            }
        )

        PreferenceItem(
            title = "Source Code",
            summary = "View on GitHub",
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/xzunk/Foxkeyboard"))
                context.startActivity(intent)
            }
        )

        PreferenceItem(
            title = "Version",
            summary = BuildConfig.VERSION_NAME
        )
    }
}

@Composable
fun rememberBooleanPreference(context: Context, key: String, defaultValue: Boolean): MutableState<Boolean> {
    val prefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    val state = remember { mutableStateOf(prefs.getBoolean(key, defaultValue)) }

    DisposableEffect(key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, k ->
            if (k == key) {
                state.value = sharedPreferences.getBoolean(key, defaultValue)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return remember(state) {
        object : MutableState<Boolean> {
            override var value: Boolean
                get() = state.value
                set(newValue) {
                    state.value = newValue
                    prefs.edit().putBoolean(key, newValue).apply()
                }

            override fun component1() = value
            override fun component2(): (Boolean) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberIntPreference(context: Context, key: String, defaultValue: Int): MutableState<Int> {
    val prefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    val state = remember { mutableStateOf(prefs.getInt(key, defaultValue)) }

    DisposableEffect(key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, k ->
            if (k == key) {
                state.value = sharedPreferences.getInt(key, defaultValue)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return remember(state) {
        object : MutableState<Int> {
            override var value: Int
                get() = state.value
                set(newValue) {
                    state.value = newValue
                    prefs.edit().putInt(key, newValue).apply()
                }

            override fun component1() = value
            override fun component2(): (Int) -> Unit = { value = it }
        }
    }
}
