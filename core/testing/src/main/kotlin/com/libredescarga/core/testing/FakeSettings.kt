package com.libredescarga.core.testing

import com.libredescarga.core.domain.settings.AppUpdateRepository
import com.libredescarga.core.domain.settings.BackupRepository
import com.libredescarga.core.domain.settings.BackupSummary
import com.libredescarga.core.domain.settings.BlocklistUpdater
import com.libredescarga.core.domain.settings.CacheRepository
import com.libredescarga.core.domain.settings.EngineRepository
import com.libredescarga.core.model.AppUpdate
import com.libredescarga.core.model.BlocklistRefresh
import com.libredescarga.core.model.EngineUpdateOutcome
import com.libredescarga.core.model.InstallOutcome
import com.libredescarga.core.model.UpdateCheck
import com.libredescarga.core.domain.settings.UpdateReminder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Aviso de actualizaciones de mentira, solo para las pruebas. */
class FakeUpdateReminder : UpdateReminder {
    private val state = MutableStateFlow(true)
    override val enabled: StateFlow<Boolean> = state
    override fun setEnabled(value: Boolean) {
        state.value = value
    }
}

/** Versión de mentira del actualizador de la app, solo para las pruebas automáticas (no se usa en la app real). */
class FakeAppUpdateRepository : AppUpdateRepository {
    override var installedVersionName = "1.0.0"
    override var installedVersionCode = 1L

    var checkResult: UpdateCheck = UpdateCheck.UpToDate
    var checkError: Throwable? = null
    var downloadError: Throwable? = null
    var installOutcome = InstallOutcome.STARTED
    val installed = mutableListOf<File>()

    // Devuelve el resultado preparado por la prueba, o lanza el error que se quiera simular.
    override suspend fun check(): UpdateCheck {
        checkError?.let { throw it }
        return checkResult
    }

    // Simula una descarga: avisa 50 % y 100 % y devuelve un archivo falso.
    override suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): File {
        downloadError?.let { throw it }
        onProgress(0.5f)
        onProgress(1f)
        return File("update-${update.versionName}.apk")
    }

    // Anota qué archivo se habría instalado, sin instalar nada.
    override fun install(apk: File): InstallOutcome {
        installed += apk
        return installOutcome
    }
}

/** Motor de descarga de mentira para las pruebas: se puede configurar su versión y su resultado. */
class FakeEngineRepository : EngineRepository {
    var version: String? = "2026.01.01"
    var outcome = EngineUpdateOutcome.UPDATED
    var error: Throwable? = null

    override suspend fun version(): String? = version

    override suspend fun update(): EngineUpdateOutcome {
        error?.let { throw it }
        if (outcome == EngineUpdateOutcome.UPDATED) version = "2026.02.02"
        return outcome
    }
}

/** Actualizador de lista de bloqueo de mentira para las pruebas. */
class FakeBlocklistUpdater : BlocklistUpdater {
    var result: BlocklistRefresh = BlocklistRefresh.AlreadyCurrent
    var error: Throwable? = null

    override suspend fun refresh(): BlocklistRefresh {
        error?.let { throw it }
        return result
    }
}

/** Memoria temporal de mentira para las pruebas: "limpiar" la deja en cero y devuelve lo liberado. */
class FakeCacheRepository(var size: Long = 5_000_000) : CacheRepository {
    override suspend fun sizeBytes(): Long = size

    override suspend fun clear(): Long {
        val freed = size
        size = 0
        return freed
    }
}

/** Copia de seguridad de mentira para las pruebas: guarda el texto recibido sin tocar ningún archivo. */
class FakeBackupRepository : BackupRepository {
    var exported = "{}"
    var importError: Exception? = null
    var imported: String? = null

    override suspend fun export(): String = exported

    override suspend fun import(json: String): BackupSummary {
        importError?.let { throw it }
        imported = json
        return BackupSummary(true, 1, 2, 3, 4, 5, 6, 7)
    }
}
