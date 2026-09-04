package ru.runetchecker.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ru.runetchecker.R
import ru.runetchecker.RuNetCheckerApp
import ru.runetchecker.domain.AUTO_CHECK_MIN_SECONDS
import ru.runetchecker.monitor.MonitorService
import ru.runetchecker.monitor.ignoresBatteryOptimizations
import ru.runetchecker.monitor.openBatteryOptimizationSettings
import ru.runetchecker.monitor.requestIgnoreBatteryOptimizations

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings = (context.applicationContext as RuNetCheckerApp).settings
    var autoCheck by remember { mutableStateOf(settings.autoCheckEnabled()) }
    var intervalText by remember { mutableStateOf(settings.autoCheckIntervalSeconds().toString()) }
    var unlimited by remember { mutableStateOf(settings.popupUnlimited()) }
    var cooldownText by remember { mutableStateOf(settings.popupCooldownSeconds().toString()) }
    var maxHourText by remember { mutableStateOf(settings.popupMaxPerHour().toString()) }
    var batteryUnrestricted by remember { mutableStateOf(context.ignoresBatteryOptimizations()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryUnrestricted = context.ignoresBatteryOptimizations()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* FGS already started; alerts work once granted */ }

    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back),
                )
            }
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SettingSwitchRow(
                title = stringResource(R.string.auto_check),
                hint = stringResource(R.string.auto_check_hint),
                checked = autoCheck,
                onCheckedChange = { enabled ->
                    autoCheck = enabled
                    settings.setAutoCheckEnabled(enabled)
                    if (enabled) {
                        if (Build.VERSION.SDK_INT >= 33) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        context.requestIgnoreBatteryOptimizations()
                        MonitorService.start(context)
                    } else {
                        MonitorService.stop(context)
                    }
                },
            )

            OutlinedTextField(
                value = intervalText,
                onValueChange = { value ->
                    intervalText = value.filter { it.isDigit() }
                    value.filter { it.isDigit() }.toIntOrNull()?.let { seconds ->
                        if (seconds >= AUTO_CHECK_MIN_SECONDS) {
                            settings.setAutoCheckIntervalSeconds(seconds)
                        }
                    }
                },
                label = { Text(stringResource(R.string.auto_check_interval)) },
                supportingText = {
                    Text(stringResource(R.string.auto_check_interval_hint, AUTO_CHECK_MIN_SECONDS))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = autoCheck,
                modifier = Modifier.fillMaxWidth(),
            )

            SettingSwitchRow(
                title = stringResource(R.string.battery_unrestricted),
                hint = stringResource(R.string.battery_unrestricted_hint),
                checked = batteryUnrestricted,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        context.requestIgnoreBatteryOptimizations()
                    } else {
                        context.openBatteryOptimizationSettings()
                    }
                },
            )

            SettingSwitchRow(
                title = stringResource(R.string.popup_unlimited),
                hint = stringResource(R.string.popup_unlimited_hint),
                checked = unlimited,
                onCheckedChange = { enabled ->
                    unlimited = enabled
                    settings.setPopupUnlimited(enabled)
                },
            )

            if (!unlimited) {
                OutlinedTextField(
                    value = cooldownText,
                    onValueChange = { value ->
                        cooldownText = value.filter { it.isDigit() }
                        value.filter { it.isDigit() }.toIntOrNull()?.let { seconds ->
                            settings.setPopupCooldownSeconds(seconds)
                        }
                    },
                    label = { Text(stringResource(R.string.popup_cooldown)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maxHourText,
                    onValueChange = { value ->
                        maxHourText = value.filter { it.isDigit() }
                        value.filter { it.isDigit() }.toIntOrNull()?.let { max ->
                            if (max >= 1) settings.setPopupMaxPerHour(max)
                        }
                    },
                    label = { Text(stringResource(R.string.popup_max_hour)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
