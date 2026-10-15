package com.appmed.lembrete

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.lifecycleScope
import com.appmed.lembrete.alarm.AlarmScheduler
import com.appmed.lembrete.alarm.Notifications
import com.appmed.lembrete.ui.AddMedicationScreen
import com.appmed.lembrete.ui.AppMedTheme
import com.appmed.lembrete.ui.DetailScreen
import com.appmed.lembrete.ui.HistoryScreen
import com.appmed.lembrete.ui.HomeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private sealed interface Screen {
    data object Home : Screen
    data object Add : Screen
    data object History : Screen
    data class Detail(val medicationId: Long) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.createChannels(this)
        lifecycleScope.launch(Dispatchers.IO) { AlarmScheduler.rescheduleAll(applicationContext) }

        setContent {
            AppMedTheme {
                val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
                fun push(s: Screen) = backStack.add(s)
                fun pop() {
                    if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                }
                BackHandler(enabled = backStack.size > 1) { pop() }

                when (val screen = backStack.last()) {
                    Screen.Home -> HomeScreen(
                        warnings = { PermissionWarnings() },
                        onAdd = { push(Screen.Add) },
                        onOpen = { push(Screen.Detail(it)) },
                        onHistory = { push(Screen.History) },
                    )
                    Screen.Add -> AddMedicationScreen(
                        onBack = ::pop,
                        onSaved = { id ->
                            pop()
                            push(Screen.Detail(id))
                        },
                    )
                    Screen.History -> HistoryScreen(
                        onBack = ::pop,
                        onOpen = { push(Screen.Detail(it)) },
                    )
                    is Screen.Detail -> DetailScreen(screen.medicationId, onBack = ::pop)
                }
            }
        }
    }
}

/** Pede a permissão de notificações e avisa sobre ajustes que impedem os alarmes. */
@Composable
private fun PermissionWarnings() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    LaunchedEffect(Unit) {
        if (needsNotificationPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Relido a cada retorno à tela para refletir mudanças feitas nas configurações.
    val nm = context.getSystemService(NotificationManager::class.java)
    val notificationsOff = remember(refresh) { !nm.areNotificationsEnabled() }
    val fullScreenOff = remember(refresh) {
        Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (notificationsOff) {
            WarningCard(
                "As notificações estão desativadas. Sem elas os alarmes não tocam.",
                "Ativar notificações",
            ) {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }
        }
        if (fullScreenOff) {
            WarningCard(
                "Permita que o alarme apareça em tela cheia com o celular bloqueado.",
                "Permitir",
            ) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:${context.packageName}"),
                    )
                )
            }
        }
    }
}

@Composable
private fun WarningCard(text: String, action: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text)
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}
