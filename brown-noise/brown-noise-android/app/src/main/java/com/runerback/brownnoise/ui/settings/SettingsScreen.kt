package com.runerback.brownnoise.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.runerback.brownnoise.ui.MAX_WAVEFORM_SAMPLES
import com.runerback.brownnoise.ui.MIN_WAVEFORM_SAMPLES

private enum class SettingsTab(val title: String) {
    Sound("Sound"),
    Modulation("Modulation"),
    Visualizer("Visualizer")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by SettingsRepository.settings.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = SettingsTab.entries

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(tab.title) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                when (tabs[selectedTabIndex]) {
                    SettingsTab.Sound -> SoundTab(settings)
                    SettingsTab.Modulation -> ModulationTab(settings)
                    SettingsTab.Visualizer -> VisualizerTab(settings)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundTab(settings: Settings) {
    val noiseTypes = listOf("brown", "white", "pink", "tune")
    var noiseExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = noiseExpanded,
        onExpandedChange = { noiseExpanded = !noiseExpanded }
    ) {
        OutlinedTextField(
            value = settings.noiseType.replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            label = { Text("Noise type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = noiseExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = noiseExpanded,
            onDismissRequest = { noiseExpanded = false }
        ) {
            noiseTypes.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        SettingsRepository.setNoiseType(type)
                        noiseExpanded = false
                    }
                )
            }
        }
    }

    val randomSources = listOf("normal", "uniform", "laplace")
    var sourceExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = sourceExpanded,
        onExpandedChange = { sourceExpanded = !sourceExpanded }
    ) {
        OutlinedTextField(
            value = settings.randomSource.replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            label = { Text("Random source") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = sourceExpanded,
            onDismissRequest = { sourceExpanded = false }
        ) {
            randomSources.forEach { source ->
                DropdownMenuItem(
                    text = { Text(source.replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        SettingsRepository.setRandomSource(source)
                        sourceExpanded = false
                    }
                )
            }
        }
    }

    LabeledSlider(
        label = "Gain",
        value = settings.gain,
        onValueChange = SettingsRepository::setGain,
        valueRange = 0.1f..1.5f
    )

    LabeledSlider(
        label = "Surround",
        value = settings.surround,
        onValueChange = SettingsRepository::setSurround,
        valueRange = 0f..1f
    )

    LabeledSlider(
        label = "Reverb",
        value = settings.reverb,
        onValueChange = SettingsRepository::setReverb,
        valueRange = 0f..1f
    )

    LabeledSlider(
        label = "Softness",
        value = settings.softness,
        onValueChange = SettingsRepository::setSoftness,
        valueRange = 0f..1f
    )
}

@Composable
private fun ModulationTab(settings: Settings) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Wave modulation")
            Text(
                text = "Modulate the noise amplitude",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        }
        Checkbox(
            checked = settings.wave,
            onCheckedChange = SettingsRepository::setWave
        )
    }

    if (settings.wave) {
        LabeledSlider(
            label = "Wave rate",
            value = settings.waveRate,
            onValueChange = SettingsRepository::setWaveRate,
            valueRange = 0.1f..2f
        )
    }
}

@Composable
private fun VisualizerTab(settings: Settings) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Show waveform")
            Text(
                text = "Display the audio visualizer",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        }
        Switch(
            checked = settings.waveformEnabled,
            onCheckedChange = SettingsRepository::setWaveformEnabled
        )
    }

    LabeledSlider(
        label = "Waveform detail",
        value = settings.waveformSamples.toFloat(),
        onValueChange = { SettingsRepository.setWaveformSamples(it.toInt()) },
        valueRange = MIN_WAVEFORM_SAMPLES.toFloat()..MAX_WAVEFORM_SAMPLES.toFloat(),
        steps = 14,
        valueText = "${settings.waveformSamples} points"
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueText: String = "%.2f".format(value)
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("$label: $valueText")
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}
