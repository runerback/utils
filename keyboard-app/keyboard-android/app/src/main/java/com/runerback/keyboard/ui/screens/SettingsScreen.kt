package com.runerback.keyboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.runerback.keyboard.util.HapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runerback.keyboard.R
import com.runerback.keyboard.network.KeyboardClient
import com.runerback.keyboard.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

private data class ThemePreset(
    val name: String,
    val color: Color
)

private val themePresets = listOf(
    ThemePreset("White", Color.White),
    ThemePreset("Slate", Color(0xFF0f172a)),
    ThemePreset("Black", Color(0xFF000000)),
    ThemePreset("Dark Gray", Color(0xFF1a1a1a)),
    ThemePreset("Navy", Color(0xFF0a192f)),
    ThemePreset("Purple", Color(0xFF1a1025))
)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val host by viewModel.host.collectAsState()
    val port by viewModel.port.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val deviceToken by viewModel.deviceToken.collectAsState()
    val backgroundColor by viewModel.backgroundColor.collectAsState()
    val interceptRealKeyboard by viewModel.interceptRealKeyboard.collectAsState()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
    val vibrationIntensity by viewModel.vibrationIntensity.collectAsState()
    val fKeyOrder by viewModel.fKeyOrder.collectAsState()
    val printScreenVk by viewModel.printScreenVk.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.resetSavedFlag()
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(R.string.tab_system, R.string.tab_themes, R.string.tab_feedback, R.string.tab_keys)

    Surface(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.height(56.dp)
            ) {
                tabs.forEachIndexed { index, titleRes ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(stringResource(titleRes)) }
                    )
                }
            }

            when (selectedTab) {
                0 -> SystemTab(
                    host = host,
                    port = port,
                    saved = saved,
                    connectionState = connectionState,
                    authState = authState,
                    deviceToken = deviceToken,
                    interceptRealKeyboard = interceptRealKeyboard,
                    onHostChange = viewModel::onHostChange,
                    onPortChange = viewModel::onPortChange,
                    onSave = viewModel::save,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                    onReset = viewModel::reset,
                    onInterceptRealKeyboardChange = viewModel::onInterceptRealKeyboardChange,
                    onShowLogs = viewModel::openLogScreen
                )
                1 -> ThemesTab(
                    selectedColor = backgroundColor,
                    onColorSelected = viewModel::onBackgroundColorChange
                )
                2 -> FeedbackTab(
                    vibrationEnabled = vibrationEnabled,
                    vibrationIntensity = vibrationIntensity,
                    onVibrationToggled = viewModel::onVibrationEnabledChange,
                    onIntensityChange = viewModel::onVibrationIntensityChange
                )
                3 -> KeysTab(
                    fKeyOrder = fKeyOrder,
                    onFKeyOrderChange = viewModel::onFKeyOrderChange,
                    printScreenVk = printScreenVk,
                    onPrintScreenVkChange = viewModel::onPrintScreenVkChange
                )
            }

            if (authState is KeyboardClient.AuthState.PairingRequired) {
                PairingDialog(
                    onPair = { KeyboardClient.sendPair(it) },
                    onCancel = { KeyboardClient.disconnect() }
                )
            }
        }
    }
}

@Composable
private fun PairingDialog(
    onPair: (String) -> Unit,
    onCancel: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(R.string.pairing_title)) },
        text = {
            Column {
                Text(stringResource(R.string.pairing_message))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text(stringResource(R.string.pairing_code_label)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPair(input) },
                enabled = input.length == 6
            ) {
                Text(stringResource(R.string.pair))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    )
}

private fun KeyboardClient.AuthState.toDisplayString(deviceToken: String): String {
    return when (this) {
        is KeyboardClient.AuthState.Authenticated -> "Authenticated"
        is KeyboardClient.AuthState.Failed -> "Authentication failed"
        is KeyboardClient.AuthState.PairingRequired -> "Pairing required"
        is KeyboardClient.AuthState.Unknown ->
            if (deviceToken.isNotBlank()) "Token saved" else "Not authenticated"
    }
}

@Composable
private fun SystemTab(
    host: String,
    port: String,
    saved: Boolean,
    connectionState: KeyboardClient.State,
    authState: KeyboardClient.AuthState,
    deviceToken: String,
    interceptRealKeyboard: Boolean,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onSave: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onReset: () -> Unit,
    onInterceptRealKeyboardChange: (Boolean) -> Unit,
    onShowLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text(stringResource(R.string.host_label)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.weight(3f)
            )

            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text(stringResource(R.string.port_label)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = host.isNotBlank() && port.isNotBlank()
        ) {
            Text(stringResource(R.string.save))
        }

        if (saved) {
            Text(
                text = "Saved",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Status: ${connectionState.toDisplayString()}",
            style = MaterialTheme.typography.bodyLarge
        )

        Text(
            text = "Auth: ${authState.toDisplayString(deviceToken)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (connectionState is KeyboardClient.State.Disconnected || connectionState is KeyboardClient.State.Error) {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.weight(1f),
                    enabled = host.isNotBlank() && port.isNotBlank()
                ) {
                    Text(stringResource(R.string.connect))
                }
            }
            if (connectionState is KeyboardClient.State.Connected) {
                Button(
                    onClick = onDisconnect,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.disconnect))
                }
            }
            Button(
                onClick = onReset,
                modifier = Modifier.weight(1f),
                enabled = host.isNotBlank() && port.isNotBlank()
            ) {
                Text(stringResource(R.string.reset))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.intercept_real_keyboard_label),
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = interceptRealKeyboard,
                onCheckedChange = onInterceptRealKeyboardChange
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onShowLogs,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Show log")
        }
    }
}

@Composable
private fun ThemesTab(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.background_color_label),
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            themePresets.forEach { preset ->
                val selected = preset.color == selectedColor
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(preset.color)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape
                        )
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onColorSelected(preset.color) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackTab(
    vibrationEnabled: Boolean,
    vibrationIntensity: Int,
    onVibrationToggled: (Boolean) -> Unit,
    onIntensityChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.vibration_label),
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = vibrationEnabled,
                onCheckedChange = onVibrationToggled
            )
        }

        if (vibrationEnabled) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "${stringResource(R.string.vibration_intensity_label)}: $vibrationIntensity",
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = vibrationIntensity.toFloat(),
                onValueChange = { onIntensityChange(it.toInt()) },
                onValueChangeFinished = {
                    HapticFeedback.perform(context, vibrationEnabled, vibrationIntensity)
                },
                valueRange = 1f..6f,
                steps = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun MutableList<Int>.move(from: Int, to: Int) {
    if (from == to) return
    val item = removeAt(from)
    add(to, item)
}

private fun parseFKeyOrder(csv: String): List<Int> {
    val parsed = csv.split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it in 0x70..0x7B }
    val default = (0x70..0x7B).toList()
    return if (parsed.size == 12 && parsed.toSet().size == 12) parsed else default
}

private data class PrintScreenOption(
    @androidx.annotation.StringRes val labelRes: Int,
    val vk: Int
)

private val printScreenOptions = listOf(
    PrintScreenOption(R.string.print_screen_option_prtsc, Vk.SNAPSHOT),
    PrintScreenOption(R.string.print_screen_option_home, Vk.HOME),
    PrintScreenOption(R.string.print_screen_option_end, Vk.END)
)

@Composable
private fun KeysTab(
    fKeyOrder: String,
    onFKeyOrderChange: (List<Int>) -> Unit,
    printScreenVk: Int,
    onPrintScreenVkChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val parsed = remember(fKeyOrder) { parseFKeyOrder(fKeyOrder) }
    var items by remember(fKeyOrder) { mutableStateOf(parsed) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.print_screen_title),
            style = MaterialTheme.typography.headlineMedium
        )

        printScreenOptions.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = printScreenVk == option.vk,
                        role = Role.RadioButton,
                        onClick = { onPrintScreenVkChange(option.vk) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = printScreenVk == option.vk,
                    onClick = null
                )
                Text(
                    text = stringResource(option.labelRes),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.fkeys_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = stringResource(R.string.fkeys_visible_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.take(8).forEachIndexed { index, vk ->
                FKeyConfigItem(
                    label = "F${vk - 0x70 + 1}",
                    index = index,
                    items = items,
                    onMove = { from, to ->
                        val newList = items.toMutableList()
                        newList.move(from, to)
                        items = newList
                        onFKeyOrderChange(newList)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.drop(8).forEachIndexed { index, vk ->
                FKeyConfigItem(
                    label = "F${vk - 0x70 + 1}",
                    index = index + 8,
                    items = items,
                    onMove = { from, to ->
                        val newList = items.toMutableList()
                        newList.move(from, to)
                        items = newList
                        onFKeyOrderChange(newList)
                    }
                )
            }
        }
    }
}

@Composable
private fun FKeyConfigItem(
    label: String,
    index: Int,
    items: List<Int>,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val itemHeight = 48.dp
    val itemHeightPx = with(density) { itemHeight.toPx() }
    var offsetY by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight)
            .background(
                if (isDragging) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(6.dp)
            )
            .offset { IntOffset(0, offsetY.roundToInt()) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "Drag",
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            val targetIndex = (index + (offsetY / itemHeightPx).roundToInt())
                                .coerceIn(0, items.lastIndex)
                            if (targetIndex != index) {
                                onMove(index, targetIndex)
                            }
                            offsetY = 0f
                        },
                        onDragCancel = {
                            isDragging = false
                            offsetY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetY += dragAmount.y
                        }
                    )
                }
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun KeyboardClient.State.toDisplayString(): String {
    return when (this) {
        is KeyboardClient.State.Connected -> stringResource(R.string.connected) + " ($host:$port)"
        is KeyboardClient.State.Connecting -> stringResource(R.string.connecting)
        is KeyboardClient.State.Disconnected -> stringResource(R.string.disconnected)
        is KeyboardClient.State.Error -> "Error: $message"
    }
}
