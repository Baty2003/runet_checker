package ru.runetchecker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import ru.runetchecker.BuildConfig
import ru.runetchecker.RuNetCheckerApp
import ru.runetchecker.R
import ru.runetchecker.domain.NetworkState
import ru.runetchecker.domain.ProbeGroup
import ru.runetchecker.domain.ProbeResult
import ru.runetchecker.domain.VpnStatus
import ru.runetchecker.monitor.LocalConnectivity
import ru.runetchecker.monitor.requestIgnoreBatteryOptimizations
import ru.runetchecker.settings.AppLanguage
import ru.runetchecker.settings.ThemeMode
import java.util.Locale

@Composable
fun CheckScreen(
    themeMode: ThemeMode,
    language: AppLanguage,
    onThemeModeChange: (ThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    viewModel: CheckViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit) {
        val app = context.applicationContext as RuNetCheckerApp
        if (LocalConnectivity.shouldPauseChecks(context)) {
            app.repository.showInterfacesOff()
        }
        if (app.settings.autoCheckEnabled()) {
            requestNotificationsIfNeeded()
            context.requestIgnoreBatteryOptimizations()
        }
    }
    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
        return
    }
    CheckScreenContent(
        uiState = uiState,
        onCheckClick = {
            requestNotificationsIfNeeded()
            viewModel.check()
        },
        versionName = BuildConfig.VERSION_NAME,
        themeMode = themeMode,
        language = language,
        onThemeModeChange = onThemeModeChange,
        onLanguageChange = onLanguageChange,
        onSettingsClick = { showSettings = true },
    )
}

@Composable
fun CheckScreenContent(
    uiState: CheckUiState,
    onCheckClick: () -> Unit,
    versionName: String,
    themeMode: ThemeMode,
    language: AppLanguage,
    onThemeModeChange: (ThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val result = uiState.result
    var globalExpanded by rememberSaveable { mutableStateOf(false) }
    var whitelistExpanded by rememberSaveable { mutableStateOf(false) }
    var logsExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        SettingsRow(
            themeMode = themeMode,
            language = language,
            onThemeClick = { onThemeModeChange(nextTheme(themeMode)) },
            onLanguageChange = onLanguageChange,
            onSettingsClick = onSettingsClick,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when {
                    uiState.airplaneMode -> stringResource(R.string.status_airplane)
                    uiState.radiosOff -> stringResource(R.string.status_radios_off)
                    else -> statusLabel(result?.state)
                },
                color = if (uiState.checksPaused) {
                    statusColor(NetworkState.OFFLINE)
                } else {
                    statusColor(result?.state)
                },
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Text(
                text = when {
                    uiState.airplaneMode -> stringResource(R.string.status_airplane_desc)
                    uiState.radiosOff -> stringResource(R.string.status_radios_off_desc)
                    else -> statusDescription(result?.state)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )

            uiState.vpn?.let { vpn ->
                Spacer(modifier = Modifier.height(16.dp))
                VpnStatusBlock(
                    vpn = vpn,
                    showOfflineVpnWarning = !uiState.checksPaused &&
                        vpn.active &&
                        result?.state == NetworkState.OFFLINE,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            ExpandableProbeGroup(
                label = stringResource(R.string.global_internet),
                reached = result?.globalReached,
                total = result?.globalTotal,
                probes = result?.probes.orEmpty().filter { it.target.group == ProbeGroup.GLOBAL },
                expanded = globalExpanded,
                onToggle = { globalExpanded = !globalExpanded },
            )

            Spacer(modifier = Modifier.height(8.dp))

            ExpandableProbeGroup(
                label = stringResource(R.string.whitelist),
                reached = result?.whitelistReached,
                total = result?.whitelistTotal,
                probes = result?.probes.orEmpty().filter { it.target.group == ProbeGroup.WHITELIST },
                expanded = whitelistExpanded,
                onToggle = { whitelistExpanded = !whitelistExpanded },
            )

            Spacer(modifier = Modifier.height(32.dp))

            ExpandableLogs(
                probes = result?.probes.orEmpty(),
                expanded = logsExpanded,
                onToggle = { logsExpanded = !logsExpanded },
            )

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (uiState.isChecking) {
                Box(
                    modifier = Modifier.size(64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp))
                    Text(
                        text = uiState.countdownSeconds.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (uiState.maxAttempts > 1) {
                    Text(
                        text = stringResource(R.string.check_attempt, uiState.checkAttempt, uiState.maxAttempts),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Text(
                    text = stringResource(
                        if (uiState.autoCheck) R.string.check_source_auto else R.string.check_source_manual,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = onCheckClick,
                enabled = !uiState.isChecking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (uiState.isChecking) stringResource(R.string.checking) else stringResource(R.string.check))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        Text(
            text = versionName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun SettingsRow(
    themeMode: ThemeMode,
    language: AppLanguage,
    onThemeClick: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings),
                )
            }
            TextButton(onClick = onThemeClick) {
                Icon(
                    imageVector = themeIcon(themeMode),
                    contentDescription = themeLabel(themeMode),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(themeLabel(themeMode))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = language == AppLanguage.RUSSIAN,
                onClick = { onLanguageChange(AppLanguage.RUSSIAN) },
                label = { Text("🇷🇺  ${stringResource(R.string.language_ru)}") },
            )
            FilterChip(
                selected = language == AppLanguage.ENGLISH,
                onClick = { onLanguageChange(AppLanguage.ENGLISH) },
                label = { Text("🇬🇧  ${stringResource(R.string.language_en)}") },
            )
        }
    }
}

@Composable
private fun VpnStatusBlock(vpn: VpnStatus, showOfflineVpnWarning: Boolean) {
    val locale = Locale.getDefault()
    val line = when {
        !vpn.active -> stringResource(R.string.vpn_off)
        vpn.countryCode.isNullOrBlank() -> stringResource(R.string.vpn_on_unknown)
        else -> {
            val countryName = Locale.Builder()
                .setRegion(vpn.countryCode)
                .build()
                .getDisplayCountry(locale)
                .ifBlank { vpn.countryCode }
            val flag = countryFlagEmoji(vpn.countryCode)
            val country = if (flag != null) "$flag  $countryName" else countryName
            stringResource(R.string.vpn_on_country, country)
        }
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (vpn.isForeign) {
                Text(
                    text = stringResource(R.string.vpn_foreign_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (showOfflineVpnWarning) {
                Text(
                    text = stringResource(R.string.vpn_offline_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private fun countryFlagEmoji(code: String): String? {
    val iso = code.trim().uppercase()
    if (iso.length != 2 || iso.any { it !in 'A'..'Z' }) return null
    val regionalIndicatorA = 0x1F1E6
    return buildString {
        iso.forEach { letter ->
            appendCodePoint(regionalIndicatorA + (letter - 'A'))
        }
    }
}

@Composable
private fun ExpandableProbeGroup(
    label: String,
    reached: Int?,
    total: Int?,
    probes: List<ProbeResult>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            ExpandableHeader(
                title = label,
                trailing = if (reached == null || total == null) {
                    stringResource(R.string.counts_placeholder)
                } else {
                    "$reached / $total"
                },
                expanded = expanded,
                onToggle = onToggle,
                enabled = true,
            )
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (probes.isEmpty()) {
                        Text(
                            text = stringResource(R.string.logs_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        probes.forEach { probe ->
                            HostStatusRow(probe = probe)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableLogs(
    probes: List<ProbeResult>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            val expandLabel = if (expanded) {
                stringResource(R.string.collapse)
            } else {
                stringResource(R.string.expand)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = expandLabel, onClick = onToggle)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (expanded) "▾  ${stringResource(R.string.logs)}"
                        else "▸  ${stringResource(R.string.logs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.technical_details_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                val logText = if (probes.isEmpty()) {
                    stringResource(R.string.logs_empty)
                } else {
                    probes.joinToString(separator = "\n\n") { it.logLines().joinToString("\n") }
                }
                Text(
                    text = logText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp, top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ExpandableHeader(
    title: String,
    trailing: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean,
) {
    val expandLabel = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = expandLabel, onClick = onToggle)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (expanded) "▾  " else "▸  ",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            text = trailing,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun HostStatusRow(probe: ProbeResult) {
    val reachable = probe.reachable
    val status = if (reachable) {
        stringResource(R.string.host_reachable)
    } else {
        stringResource(R.string.host_unreachable)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = probe.target.host,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = if (reachable) {
                Color(0xFF2E7D32)
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

private fun themeIcon(mode: ThemeMode): ImageVector = when (mode) {
    ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
    ThemeMode.LIGHT -> Icons.Filled.LightMode
    ThemeMode.DARK -> Icons.Filled.DarkMode
}

@Composable
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
    ThemeMode.DARK -> stringResource(R.string.theme_dark)
}

@Composable
private fun statusDescription(state: NetworkState?): String = when (state) {
    NetworkState.ONLINE -> stringResource(R.string.status_online_desc)
    NetworkState.WHITELIST -> stringResource(R.string.status_whitelist_desc)
    NetworkState.OFFLINE -> stringResource(R.string.status_offline_desc)
    NetworkState.UNKNOWN -> stringResource(R.string.status_unknown_desc)
    null -> stringResource(R.string.status_idle_desc)
}

private fun nextTheme(mode: ThemeMode): ThemeMode = when (mode) {
    ThemeMode.SYSTEM -> ThemeMode.LIGHT
    ThemeMode.LIGHT -> ThemeMode.DARK
    ThemeMode.DARK -> ThemeMode.SYSTEM
}

private fun statusLabel(state: NetworkState?): String = when (state) {
    NetworkState.ONLINE -> "ONLINE"
    NetworkState.WHITELIST -> "WHITELIST"
    NetworkState.OFFLINE -> "OFFLINE"
    NetworkState.UNKNOWN -> "UNKNOWN"
    null -> "—"
}

private fun statusColor(state: NetworkState?): Color = when (state) {
    NetworkState.ONLINE -> Color(0xFF2E7D32)
    NetworkState.WHITELIST -> Color(0xFFF9A825)
    NetworkState.OFFLINE -> Color(0xFFC62828)
    NetworkState.UNKNOWN -> Color(0xFFEF6C00)
    null -> Color(0xFF757575)
}
