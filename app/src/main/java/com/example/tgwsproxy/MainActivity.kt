package com.example.tgwsproxy

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            } else {
                if (darkTheme) darkColorScheme() else lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ProxyMainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyMainScreen() {
    val context = LocalContext.current
    val isRunning by ProxyState.isRunning.collectAsState()
    val activeTunnels by ProxyState.activeTunnels.collectAsState()
    val bytesUp by ProxyState.bytesUp.collectAsState()
    val bytesDown by ProxyState.bytesDown.collectAsState()

    var portText by remember { mutableStateOf(ProxyState.getPort(context).toString()) }
    var sleepOnScreenOff by remember { mutableStateOf(ProxyState.isSleepOnScreenOff(context)) }
    var autostart by remember { mutableStateOf(ProxyState.isAutostart(context)) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        startOrStopService(context, currentlyRunning = false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("TG WS Proxy", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "Zero-Drain",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { ProxyState.resetStats() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Сбросить счетчик")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val accentColor by animateColorAsState(
                targetValue = if (isRunning) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant,
                label = "powerColor"
            )
            val iconColor by animateColorAsState(
                targetValue = if (isRunning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "iconColor"
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                            .clickable {
                                if (!isRunning && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!granted) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        return@clickable
                                    }
                                }
                                startOrStopService(context, isRunning)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PowerSettingsNew,
                            contentDescription = "Включить или выключить",
                            tint = iconColor,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = if (isRunning) "Прокси запущен" else "Прокси остановлен",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (isRunning) "SOCKS5 • 127.0.0.1:${ProxyState.getPort(context)}"
                        else "Нажмите кнопку выше для запуска",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatColumn("Туннели", activeTunnels.toString())
                        StatColumn("Отправлено", formatBytes(bytesUp))
                        StatColumn("Получено", formatBytes(bytesDown))
                    }
                }
            }

            FilledTonalButton(
                onClick = { openTelegramProxyLink(context, ProxyState.getPort(context)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Send, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Подключить в Telegram",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Параметры и энергосбережение",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = portText,
                        onValueChange = { value ->
                            val clean = value.filter { it.isDigit() }.take(5)
                            portText = clean
                            val parsed = clean.toIntOrNull()
                            if (parsed != null && parsed in 1024..65535) {
                                ProxyState.setPort(context, parsed)
                            }
                        },
                        enabled = !isRunning,
                        label = { Text("Локальный порт SOCKS5") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    SwitchRow(
                        title = "Сон при выключении экрана",
                        subtitle = "Закрывать сокеты через 10 сек после блокировки (0% расхода)",
                        checked = sleepOnScreenOff,
                        onCheckedChange = {
                            sleepOnScreenOff = it
                            ProxyState.setSleepOnScreenOff(context, it)
                        }
                    )

                    SwitchRow(
                        title = "Автозапуск при включении",
                        subtitle = "Поднимать локальный порт после перезагрузки",
                        checked = autostart,
                        onCheckedChange = {
                            autostart = it
                            ProxyState.setAutostart(context, it)
                        }
                    )
                }
            }

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    ListItem(
                        headlineContent = { Text("Не ограничивать в фоне") },
                        supportingContent = { Text("Защитить локальный порт от закрытия системой") },
                        leadingContent = { Icon(Icons.Rounded.BatteryChargingFull, null) },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { requestIgnoreBatteryOptimizations(context) }
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ListItem(
                            headlineContent = { Text("Плитка в быстрых настройках") },
                            supportingContent = { Text("Добавить переключатель в верхнюю шторку") },
                            leadingContent = { Icon(Icons.Rounded.Widgets, null) },
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { requestQuickSettingsTile(context) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun startOrStopService(context: Context, currentlyRunning: Boolean) {
    val intent = Intent(context, ProxyService::class.java)
    if (currentlyRunning) {
        context.stopService(intent)
    } else {
        context.startForegroundService(intent)
    }
}

private fun openTelegramProxyLink(context: Context, port: Int) {
    val uri = Uri.parse("tg://socks?server=127.0.0.1&port=$port")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Приложение Telegram не найдено", Toast.LENGTH_SHORT).show()
    }
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    val pm = context.getSystemService(PowerManager::class.java)
    if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "Ограничения уже отключены", Toast.LENGTH_SHORT).show()
    }
}

private fun requestQuickSettingsTile(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val statusBarManager = context.getSystemService(StatusBarManager::class.java)
        statusBarManager.requestAddTileService(
            ComponentName(context, ProxyTileService::class.java),
            "TG WS Proxy",
            Icon.createWithResource(context, android.R.drawable.ic_dialog_info),
            context.mainExecutor
        ) {}
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes Б"
        bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
        bytes < 1024L * 1024 * 1024 -> String.format("%.1f МБ", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f ГБ", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
