package unicode.sinhala.keyboard.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
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

        val layoutWijesekara = rememberBooleanPreference(context, "layout_wijesekara", true)
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

        SettingsCategory(title = "Appearance")

        val automaticTheme = rememberBooleanPreference(context, "automatic_theme", true)
        SwitchPreference(
            title = "ස්වයංක්‍රීය තේමාව",
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

        SettingsCategory(title = "Layout")

        val heightPercentage = rememberIntPreference(context, "height_percentage", 100)
        SliderPreference(
            title = "උස",
            value = heightPercentage.value,
            range = 70f..130f,
            onValueChange = { heightPercentage.value = it }
        )

        val textSize = rememberIntPreference(context, "text_size", 28)
        SliderPreference(
            title = "අකුරුවල ප්‍රමාණය",
            value = textSize.value,
            range = 20f..40f,
            onValueChange = { textSize.value = it }
        )

        SettingsCategory(title = "Support")

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
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, k ->
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
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, k ->
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
