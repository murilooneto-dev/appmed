package com.appmed.lembrete.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.appmed.lembrete.data.MedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun rememberRepository(): MedRepository {
    val context = LocalContext.current
    return remember { MedRepository.get(context) }
}

/**
 * Executa [query] em segundo plano e refaz a consulta sempre que o banco muda
 * (inclusive por ações na notificação) ou quando a tela volta ao primeiro plano.
 */
@Composable
fun <T> rememberRepoQuery(vararg keys: Any?, query: (MedRepository) -> T): State<T?> {
    val repo = rememberRepository()
    val version by repo.changes.collectAsState()
    var resumeTick by remember { mutableLongStateOf(0L) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    return produceState<T?>(null, version, resumeTick, *keys) {
        value = withContext(Dispatchers.IO) { query(repo) }
    }
}

/** Hora atual, atualizada a cada 30 segundos para manter os status das doses em dia. */
@Composable
fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    return now
}
