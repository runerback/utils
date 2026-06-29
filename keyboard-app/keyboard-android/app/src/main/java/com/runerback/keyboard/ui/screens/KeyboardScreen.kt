package com.runerback.keyboard.ui.screens

import android.app.Activity
import android.view.View
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.KeyboardTab
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.runerback.keyboard.R
import com.runerback.keyboard.data.SettingsRepository
import com.runerback.keyboard.network.KeyboardClient
import com.runerback.keyboard.util.HapticFeedback
import com.runerback.keyboard.util.LogManager

object Vk {
    const val BACK = 0x08
    const val TAB = 0x09
    const val RETURN = 0x0D
    const val ESCAPE = 0x1B
    const val F1 = 0x70
    const val F2 = 0x71
    const val F3 = 0x72
    const val F4 = 0x73
    const val F5 = 0x74
    const val F6 = 0x75
    const val F7 = 0x76
    const val F8 = 0x77
    const val F9 = 0x78
    const val F10 = 0x79
    const val F11 = 0x7A
    const val F12 = 0x7B
    const val INSERT = 0x2D
    const val SNAPSHOT = 0x2C // PrintScreen
    const val SPACE = 0x20
    const val DELETE = 0x2E
    const val LEFT = 0x25
    const val UP = 0x26
    const val RIGHT = 0x27
    const val DOWN = 0x28
    const val SHIFT = 0x10
    const val CONTROL = 0x11
    const val MENU = 0x12 // Alt
    const val WIN = 0x5B
    const val APPS = 0x5D // Context menu
    const val CAPITAL = 0x14 // Caps Lock
    const val OEM_COMMA = 0xBC // ,<
    const val OEM_PERIOD = 0xBE // .>
    const val OEM_2 = 0xBF // /?
    const val OEM_1 = 0xBA // ;:
    const val OEM_7 = 0xDE // '"'
    const val OEM_4 = 0xDB // [{
    const val OEM_5 = 0xDC // \|
    const val OEM_6 = 0xDD // }]
    const val OEM_MINUS = 0xBD // -_
    const val OEM_PLUS = 0xBB // =+
    const val OEM_3 = 0xC0 // `~
}

private data class KeyData(
    val label: String,
    val vk: Int,
    val weight: Float = 1f,
    val shiftLabel: String? = null,
    val repeat: Boolean = false
)

private val KeyData.isSquare: Boolean
    get() = vk in '0'.code..'9'.code || vk in 'A'.code..'Z'.code ||
            vk == Vk.OEM_COMMA || vk == Vk.OEM_PERIOD || vk == Vk.OEM_2 ||
            vk == Vk.LEFT || vk == Vk.UP || vk == Vk.RIGHT || vk == Vk.DOWN

// Windows-style QWERTY layout. Letter/number/punctuation keys are square (52.dp); other keys use weights.
private val keyRows: List<List<KeyData>> = listOf(
    listOf(
        KeyData("`", Vk.OEM_3, shiftLabel = "~"),
        KeyData("1", '1'.code, shiftLabel = "!"),
        KeyData("2", '2'.code, shiftLabel = "@"),
        KeyData("3", '3'.code, shiftLabel = "#"),
        KeyData("4", '4'.code, shiftLabel = "$"),
        KeyData("5", '5'.code, shiftLabel = "%"),
        KeyData("6", '6'.code, shiftLabel = "^"),
        KeyData("7", '7'.code, shiftLabel = "&"),
        KeyData("8", '8'.code, shiftLabel = "*"),
        KeyData("9", '9'.code, shiftLabel = "("),
        KeyData("0", '0'.code, shiftLabel = ")"),
        KeyData("-", Vk.OEM_MINUS, shiftLabel = "_"),
        KeyData("=", Vk.OEM_PLUS, shiftLabel = "+"),
        KeyData("⌫", Vk.BACK, repeat = true)
    ),
    listOf(
        KeyData("Tab", Vk.TAB, weight = 1.125f),
        KeyData("q", 'Q'.code, shiftLabel = "Q"),
        KeyData("w", 'W'.code, shiftLabel = "W"),
        KeyData("e", 'E'.code, shiftLabel = "E"),
        KeyData("r", 'R'.code, shiftLabel = "R"),
        KeyData("t", 'T'.code, shiftLabel = "T"),
        KeyData("y", 'Y'.code, shiftLabel = "Y"),
        KeyData("u", 'U'.code, shiftLabel = "U"),
        KeyData("i", 'I'.code, shiftLabel = "I"),
        KeyData("o", 'O'.code, shiftLabel = "O"),
        KeyData("p", 'P'.code, shiftLabel = "P"),
        KeyData("[", Vk.OEM_4, shiftLabel = "{"),
        KeyData("]", Vk.OEM_6, shiftLabel = "}"),
        KeyData("\\", Vk.OEM_5, shiftLabel = "|")
    ),
    listOf(
        KeyData("Caps", Vk.CAPITAL, weight = 1.5f),
        KeyData("a", 'A'.code, shiftLabel = "A"),
        KeyData("s", 'S'.code, shiftLabel = "S"),
        KeyData("d", 'D'.code, shiftLabel = "D"),
        KeyData("f", 'F'.code, shiftLabel = "F"),
        KeyData("g", 'G'.code, shiftLabel = "G"),
        KeyData("h", 'H'.code, shiftLabel = "H"),
        KeyData("j", 'J'.code, shiftLabel = "J"),
        KeyData("k", 'K'.code, shiftLabel = "K"),
        KeyData("l", 'L'.code, shiftLabel = "L"),
        KeyData(";", Vk.OEM_1, shiftLabel = ":"),
        KeyData("'", Vk.OEM_7, shiftLabel = "\""),
        KeyData("↵", Vk.RETURN, weight = 1.5f)
    ),
    listOf(
        KeyData("Shift", Vk.SHIFT, weight = 1.5f),
        KeyData("z", 'Z'.code, shiftLabel = "Z"),
        KeyData("x", 'X'.code, shiftLabel = "X"),
        KeyData("c", 'C'.code, shiftLabel = "C"),
        KeyData("v", 'V'.code, shiftLabel = "V"),
        KeyData("b", 'B'.code, shiftLabel = "B"),
        KeyData("n", 'N'.code, shiftLabel = "N"),
        KeyData("m", 'M'.code, shiftLabel = "M"),
        KeyData(",", Vk.OEM_COMMA, shiftLabel = "<"),
        KeyData(".", Vk.OEM_PERIOD, shiftLabel = ">"),
        KeyData("/", Vk.OEM_2, shiftLabel = "?"),
        KeyData("Shift", Vk.SHIFT, weight = 1.25f),
        KeyData("↑", Vk.UP, repeat = true)
    ),
    listOf(
        KeyData("Ctrl", Vk.CONTROL, weight = 1.25f),
        KeyData("Win", Vk.WIN, weight = 1.25f),
        KeyData("Alt", Vk.MENU, weight = 1.25f),
        KeyData("Space", Vk.SPACE, weight = 5f, repeat = true),
        KeyData("Menu", Vk.APPS),
        KeyData("←", Vk.LEFT, repeat = true),
        KeyData("→", Vk.RIGHT, repeat = true),
        KeyData("↓", Vk.DOWN, repeat = true)
    )
)

private val allFKeys: List<KeyData> = listOf(
    KeyData("F1", Vk.F1),
    KeyData("F2", Vk.F2),
    KeyData("F3", Vk.F3),
    KeyData("F4", Vk.F4),
    KeyData("F5", Vk.F5),
    KeyData("F6", Vk.F6),
    KeyData("F7", Vk.F7),
    KeyData("F8", Vk.F8),
    KeyData("F9", Vk.F9),
    KeyData("F10", Vk.F10),
    KeyData("F11", Vk.F11),
    KeyData("F12", Vk.F12)
)

private fun parseFKeyOrder(csv: String): List<Int> {
    val parsed = csv.split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it in Vk.F1..Vk.F12 }
    val default = (Vk.F1..Vk.F12).toList()
    return if (parsed.size == 12 && parsed.toSet().size == 12) parsed else default
}

private fun labelForFKey(vk: Int): String = "F${vk - Vk.F1 + 1}"

@Composable
fun KeyboardScreen(
    onKeyEvent: (vk: Int, action: String) -> Unit,
    modifier: Modifier = Modifier,
    connectionState: KeyboardClient.State? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isActivity = context is Activity
    var capsActive by remember { mutableStateOf(false) }
    var shiftActive by remember { mutableStateOf(false) }

    val fKeyOrder by SettingsRepository.fKeyOrder.collectAsState()
    val visibleFKeys = remember(fKeyOrder) {
        parseFKeyOrder(fKeyOrder)
            .take(8)
            .map { vk -> KeyData(labelForFKey(vk), vk) }
    }

    val handleKeyEvent = { vk: Int, action: String ->
        if (vk == Vk.CAPITAL && action == "down") {
            capsActive = !capsActive
        }
        if (vk == Vk.SHIFT) {
            shiftActive = action == "down"
        }
        onKeyEvent(vk, action)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(if (isActivity) Modifier.statusBarsPadding() else Modifier)
            .padding(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyButton(
                key = KeyData("Esc", Vk.ESCAPE),
                onKeyEvent = handleKeyEvent,
                capsActive = capsActive,
                shiftActive = shiftActive,
                connected = connectionState is KeyboardClient.State.Connected,
                modifier = Modifier.width(56.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            visibleFKeys.forEach { key ->
                KeyButton(
                    key = key,
                    onKeyEvent = handleKeyEvent,
                    capsActive = capsActive,
                    shiftActive = shiftActive,
                    connected = connectionState is KeyboardClient.State.Connected,
                    modifier = Modifier.width(56.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            KeyButton(
                key = KeyData("Ins", Vk.INSERT),
                onKeyEvent = handleKeyEvent,
                capsActive = capsActive,
                shiftActive = shiftActive,
                connected = connectionState is KeyboardClient.State.Connected,
                modifier = Modifier.weight(1f)
            )
            KeyButton(
                key = KeyData("Del", Vk.DELETE),
                onKeyEvent = handleKeyEvent,
                capsActive = capsActive,
                shiftActive = shiftActive,
                connected = connectionState is KeyboardClient.State.Connected,
                modifier = Modifier.weight(1f)
            )
            KeyButton(
                key = KeyData("PrtSc", Vk.SNAPSHOT),
                onKeyEvent = handleKeyEvent,
                capsActive = capsActive,
                shiftActive = shiftActive,
                connected = connectionState is KeyboardClient.State.Connected,
                modifier = Modifier.weight(1f)
            )

            if (onOpenSettings != null) {
                Surface(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .height(52.dp)
                        .width(64.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = Color.White
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            keyRows.forEachIndexed { rowIndex, row ->
                val isLastRow = rowIndex == keyRows.lastIndex
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.forEach { key ->
                        val keyModifier = when {
                            key.vk == Vk.SPACE -> Modifier.weight(key.weight)
                            key.vk == Vk.CONTROL || key.vk == Vk.WIN || key.vk == Vk.MENU || key.vk == Vk.APPS -> Modifier.width(78.dp)
                            isLastRow -> Modifier.width(52.dp)
                            key.isSquare -> Modifier.width(52.dp)
                            else -> Modifier.weight(key.weight)
                        }
                        KeyButton(
                            key = key,
                            onKeyEvent = handleKeyEvent,
                            capsActive = capsActive,
                            shiftActive = shiftActive,
                            connected = connectionState is KeyboardClient.State.Connected,
                            modifier = keyModifier
                        )
                    }
                }
            }
        }
    }
}

private fun performKeyHaptic(view: View, enabled: Boolean, intensity: Int) {
    LogManager.d("KeyHaptic", "performKeyHaptic called enabled=$enabled intensity=$intensity")
    if (!enabled) {
        LogManager.d("KeyHaptic", "Vibration disabled in settings")
        return
    }
    HapticFeedback.perform(view.context, enabled, intensity)
}

@Composable
private fun KeyButton(
    key: KeyData,
    onKeyEvent: (vk: Int, action: String) -> Unit,
    capsActive: Boolean,
    shiftActive: Boolean,
    connected: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var repeatJob by remember { mutableStateOf<Job?>(null) }
    val isCapsKey = key.vk == Vk.CAPITAL
    val isShiftKey = key.vk == Vk.SHIFT
    val isLetter = key.vk in 'A'.code..'Z'.code

    val vibrationEnabled by SettingsRepository.vibrationEnabled.collectAsState()
    val vibrationIntensity by SettingsRepository.vibrationIntensity.collectAsState()
    val currentVibrationEnabled = rememberUpdatedState(vibrationEnabled)
    val currentVibrationIntensity = rememberUpdatedState(vibrationIntensity)

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    onKeyEvent(key.vk, "down")
                    performKeyHaptic(view, currentVibrationEnabled.value, currentVibrationIntensity.value)
                    if (key.repeat) {
                        repeatJob?.cancel()
                        repeatJob = scope.launch {
                            delay(400)
                            while (true) {
                                onKeyEvent(key.vk, "up")
                                delay(30)
                                onKeyEvent(key.vk, "down")
                                performKeyHaptic(view, currentVibrationEnabled.value, currentVibrationIntensity.value)
                                delay(70)
                            }
                        }
                    }
                }
                is PressInteraction.Release -> {
                    repeatJob?.cancel()
                    repeatJob = null
                    onKeyEvent(key.vk, "up")
                }
                is PressInteraction.Cancel -> {
                    repeatJob?.cancel()
                    repeatJob = null
                    onKeyEvent(key.vk, "up")
                }
            }
        }
    }

    val isCapsActive = isCapsKey && capsActive

    val backgroundColor = when {
        !connected -> Color(0xFF374151)
        isCapsActive || (isShiftKey && shiftActive) -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val buttonModifier = if (isCapsActive) {
        modifier
            .height(52.dp)
            .border(2.dp, Color.White, RoundedCornerShape(6.dp))
    } else {
        modifier.height(52.dp)
    }

    Surface(
        onClick = { /* handled by interaction source */ },
        interactionSource = interactionSource,
        modifier = buttonModifier,
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor,
        contentColor = Color.White
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (key.vk) {
                Vk.RETURN -> Icon(
                    imageVector = Icons.Filled.KeyboardReturn,
                    contentDescription = key.label,
                    modifier = Modifier.size(28.dp),
                    tint = Color.White
                )
                Vk.WIN -> Icon(
                    imageVector = Icons.Filled.Window,
                    contentDescription = key.label,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
                Vk.APPS -> Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = key.label,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
                Vk.SNAPSHOT -> Icon(
                    painter = painterResource(R.drawable.ic_print_screen),
                    contentDescription = key.label,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
                Vk.ESCAPE -> KeyText(key.label)
                Vk.TAB -> Icon(
                    imageVector = Icons.Filled.KeyboardTab,
                    contentDescription = key.label,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
                Vk.SPACE -> Icon(
                    imageVector = Icons.Filled.SpaceBar,
                    contentDescription = key.label,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
                Vk.BACK -> Icon(
                    imageVector = Icons.Filled.Backspace,
                    contentDescription = key.label,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
                Vk.DELETE -> KeyText(key.label)
                Vk.INSERT -> KeyText(key.label)
                Vk.SHIFT -> Icon(
                    painter = painterResource(R.drawable.ic_shift),
                    contentDescription = key.label,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
                Vk.CONTROL -> KeyText(key.label)
                Vk.MENU -> KeyText(key.label)
                Vk.CAPITAL -> Icon(
                    painter = painterResource(
                        if (capsActive) R.drawable.ic_caps_lock else R.drawable.ic_caps_lock_open
                    ),
                    contentDescription = key.label,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
                Vk.LEFT -> Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = key.label,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
                Vk.RIGHT -> Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = key.label,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
                Vk.UP -> Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = key.label,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
                Vk.DOWN -> Icon(
                    imageVector = Icons.Filled.ArrowDownward,
                    contentDescription = key.label,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
                else -> {
                    when {
                        isLetter -> KeyText(
                            text = if (shiftActive || capsActive) key.label.uppercase() else key.label
                        )
                        key.shiftLabel != null -> {
                            val dim = Color.White.copy(alpha = 0.35f)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = key.label,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (shiftActive) dim else Color.White,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = key.shiftLabel,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (shiftActive) Color.White else dim,
                                    maxLines = 1
                                )
                            }
                        }
                        else -> KeyText(key.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyText(text: String) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White,
        maxLines = 1
    )
}

