package com.libredescarga.core.vault

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.libredescarga.core.domain.player.PlaybackController
import com.libredescarga.core.domain.vault.VaultSession
import com.libredescarga.core.model.VaultItem
import dagger.Lazy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estado de bloqueo en memoria. Se bloquea solo cuando la app pasa a segundo plano
 * y cada vez que se abre la app de nuevo hay que desbloquear. Al bloquear también se
 * detiene lo que esté sonando desde la carpeta privada.
 */
@Singleton
internal class AutoLockVaultSession @Inject constructor(
    private val playback: Lazy<PlaybackController>,
) : VaultSession {

    private val state = MutableStateFlow(false)
    override val unlocked: StateFlow<Boolean> = state.asStateFlow()

    init {
        // El observador se registra en el hilo principal, aunque la sesión se cree en otro.
        Handler(Looper.getMainLooper()).post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStop(owner: LifecycleOwner) = lock()
                },
            )
        }
    }

    override fun unlock() {
        state.value = true
    }

    override fun lock() {
        if (!state.value) return
        state.value = false
        val controller = playback.get()
        if (VaultItem.isVaultUri(controller.state.value.current?.uri)) controller.stop()
    }
}
