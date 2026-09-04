package ru.runetchecker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.runetchecker.domain.NetworkState

@Composable
fun CheckScreen(
    viewModel: CheckViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CheckScreenContent(
        uiState = uiState,
        onCheckClick = viewModel::check,
    )
}

@Composable
fun CheckScreenContent(
    uiState: CheckUiState,
    onCheckClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val result = uiState.result

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "RuNet Checker",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = statusLabel(result?.state),
            color = statusColor(result?.state),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Text(
            text = statusDescription(result?.state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(modifier = Modifier.height(40.dp))

        ResultRow(
            label = "Глобальный интернет",
            reached = result?.globalReached,
            total = result?.globalTotal,
        )
        Spacer(modifier = Modifier.height(12.dp))
        ResultRow(
            label = "Белый список",
            reached = result?.whitelistReached,
            total = result?.whitelistTotal,
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

        Spacer(modifier = Modifier.height(48.dp))

        if (uiState.isChecking) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = onCheckClick,
            enabled = !uiState.isChecking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (uiState.isChecking) "Проверка…" else "Проверить")
        }
    }
}

@Composable
private fun ResultRow(
    label: String,
    reached: Int?,
    total: Int?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = if (reached == null || total == null) "— / —" else "$reached / $total",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun statusLabel(state: NetworkState?): String = when (state) {
    NetworkState.ONLINE -> "ONLINE"
    NetworkState.WHITELIST -> "WHITELIST"
    NetworkState.OFFLINE -> "OFFLINE"
    NetworkState.UNKNOWN -> "UNKNOWN"
    null -> "—"
}

private fun statusDescription(state: NetworkState?): String = when (state) {
    NetworkState.ONLINE -> "Полноценный интернет доступен"
    NetworkState.WHITELIST -> "Доступны только разрешённые ресурсы"
    NetworkState.OFFLINE -> "Интернет фактически отсутствует"
    NetworkState.UNKNOWN -> "Не удалось завершить проверку"
    null -> "Нажмите «Проверить», чтобы узнать состояние сети"
}

private fun statusColor(state: NetworkState?): Color = when (state) {
    NetworkState.ONLINE -> Color(0xFF2E7D32)
    NetworkState.WHITELIST -> Color(0xFFF9A825)
    NetworkState.OFFLINE -> Color(0xFFC62828)
    NetworkState.UNKNOWN -> Color(0xFFEF6C00)
    null -> Color(0xFF757575)
}
