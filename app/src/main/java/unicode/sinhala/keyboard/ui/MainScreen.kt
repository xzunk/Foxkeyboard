package unicode.sinhala.keyboard.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import unicode.sinhala.com.BuildConfig
import unicode.sinhala.com.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isKeyboardEnabled: Boolean,
    isKeyboardSelected: Boolean,
    onEnableKeyboard: () -> Unit,
    onSelectKeyboard: () -> Unit
) {
    Scaffold(
        topBar = {
            if (isKeyboardEnabled && isKeyboardSelected) {
                TopAppBar(
                    title = { Text("Fox Keyboard") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (isKeyboardEnabled && isKeyboardSelected) {
                SettingsScreen()
            } else {
                SetupScreen(
                    isKeyboardEnabled = isKeyboardEnabled,
                    onEnableKeyboard = onEnableKeyboard,
                    onSelectKeyboard = onSelectKeyboard
                )
            }
        }
    }
}

@Composable
fun SetupScreen(
    isKeyboardEnabled: Boolean,
    onEnableKeyboard: () -> Unit,
    onSelectKeyboard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Use R.drawable.ic_launcher_foreground instead of mipmap resource which might be causing issues
        // or just use a generic icon if that fails. For now, assuming ic_launcher_foreground exists 
        // as it is referenced in ic_launcher.xml
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 24.dp)
        )

        Text(
            text = stringResource(id = R.string.app_name),
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 64.dp)
        )

        Text(
            text = if (!isKeyboardEnabled) 
                "Fox Keyboard is not enabled. Please enable it in settings to continue."
            else 
                "Fox Keyboard is enabled but not selected. Please select it to start typing.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Button(
            onClick = {
                if (!isKeyboardEnabled) {
                    onEnableKeyboard()
                } else {
                    onSelectKeyboard()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(
                text = if (!isKeyboardEnabled) "Enable Keyboard" else "Select Keyboard",
                fontSize = 16.sp
            )
        }
    }
}
