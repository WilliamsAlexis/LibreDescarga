package com.libredescarga.core.data.repository

import android.content.Context
import com.libredescarga.core.domain.repository.MediaExtractorRepository
import com.libredescarga.core.domain.usecase.AnalysisCacheKey
import com.libredescarga.core.engine.InfoParser
import com.libredescarga.core.engine.NewPipeYoutubeExtractor
import com.libredescarga.core.engine.YtDlpEngine
import com.libredescarga.core.model.AnalisisPerf
import com.libredescarga.core.model.AnalysisResult
import com.libredescarga.core.model.AnalysisUpdate
import com.libredescarga.core.model.MediaEntry
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.model.SearchProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class YtDlpMediaExtractorRepository @Inject constructor(
    @param:ApplicationContext context: Context,
    private val engine: YtDlpEngine,
    private val youtubeNative: NewPipeYoutubeExtractor,
) : MediaExtractorRepository {

    /** Resultado de un análisis junto con el JSON del que salió (lo que se guarda en disco). */
    private class Analyzed(val json: JsonObject, val result: AnalysisResult)

    private val memory = AnalysisCache(ttlMs = CACHE_TTL_MS)
    private val disk = AnalysisDiskStore(File(context.cacheDir, "analysis"), ttlMs = CACHE_TTL_MS)

    override suspend fun analyze(url: String): AnalysisResult {
        cached(url)?.let { return it }
        val analyzed = analyzeYoutubeNatively(url) ?: analyzeFull(url)
        remember(url, analyzed)
        return analyzed.result
    }

    /**
     * YouTube: primero la vía nativa (NewPipe, ~2–3 s, todas las calidades). Si falla, se lanzan dos análisis con yt-dlp a la
     * vez: el rápido (android_vr, ~5 s, hasta 360p y audio) y el completo (~15 s). Se emite el rápido en cuanto llega, si el
     * completo aún no terminó, y luego el completo; si el completo falla pero el rápido ya se mostró, se conserva el rápido.
     * En los demás sitios solo hay un análisis.
     */
    override fun analyzeProgressive(url: String): Flow<AnalysisUpdate> = channelFlow {
        val host = AnalisisPerf.hostOf(url)
        cached(url)?.let {
            send(AnalysisUpdate(it, isComplete = true))
            return@channelFlow
        }
        if (!AnalysisCacheKey.of(url).startsWith("youtube:")) {
            send(AnalysisUpdate(analyze(url), isComplete = true))
            return@channelFlow
        }

        analyzeYoutubeNatively(url)?.let {
            remember(url, it)
            send(AnalysisUpdate(it.result, isComplete = true))
            return@channelFlow
        }

        val partialSent = AtomicBoolean(false)
        val full = async { runCatching { analyzeFull(url) } }
        val fast = launch {
            val partial = runCatching { guard { InfoParser.parse(engine.fetchInfoFast(url), url) } }.getOrNull()
            if (partial is AnalysisResult.Single && !full.isCompleted) {
                partialSent.set(true)
                AnalisisPerf.mark("análisis rápido: resultado parcial listo", host)
                send(AnalysisUpdate(partial, isComplete = false))
            }
        }

        val result = full.await()
        fast.cancel()
        fast.join()
        result.onSuccess {
            remember(url, it)
            send(AnalysisUpdate(it.result, isComplete = true))
        }.onFailure { error ->
            if (error is CancellationException) throw error
            // Con un resultado parcial ya mostrado, un fallo del completo no tira abajo la hoja: se queda con lo que hay.
            if (!partialSent.get()) throw error
            AnalisisPerf.mark("análisis completo falló; se conserva el parcial", host)
        }
    }

    override suspend fun search(query: String, provider: SearchProvider, limit: Int): List<MediaEntry> = guard {
        InfoParser.parseEntries(engine.search(provider, query, limit))
    }

    /** Un análisis reciente del mismo contenido: primero en memoria y, si la app se cerró, en disco (6 horas). */
    private suspend fun cached(url: String): AnalysisResult? {
        val host = AnalisisPerf.hostOf(url)
        memory.get(url)?.let {
            AnalisisPerf.mark("caché: resultado reutilizado", host)
            return it
        }
        val saved = withContext(Dispatchers.IO) { disk.read(AnalysisCacheKey.of(url)) } ?: return null
        val result = runCatching { InfoParser.parse(saved.json, saved.url) }.getOrNull() ?: return null
        AnalisisPerf.mark("caché en disco: resultado reutilizado", host)
        memory.put(url, result)
        return result
    }

    private suspend fun remember(url: String, analyzed: Analyzed) {
        memory.put(url, analyzed.result)
        withContext(Dispatchers.IO) { disk.write(AnalysisCacheKey.of(url), url, analyzed.json) }
    }

    /**
     * YouTube y YouTube Music con NewPipe Extractor (sin Python ni QuickJS). Devuelve null si el enlace no es un video de
     * YouTube o si NewPipe falla o tarda demasiado: entonces se usa yt-dlp, que además da el mensaje de error correcto.
     */
    private suspend fun analyzeYoutubeNatively(url: String): Analyzed? {
        val videoId = AnalysisCacheKey.of(url).takeIf { it.startsWith("youtube:") }?.removePrefix("youtube:") ?: return null
        val host = AnalisisPerf.hostOf(url)
        return try {
            AnalisisPerf.measure("YouTube nativo (NewPipe): análisis completo", host) {
                withTimeoutOrNull(NATIVE_TIMEOUT_MS) {
                    val json = youtubeNative.fetchInfo("https://www.youtube.com/watch?v=$videoId")
                    (InfoParser.parse(json, url) as? AnalysisResult.Single)?.let { Analyzed(json, it) }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("MediaExtractor", "NewPipe no pudo analizar; se usa yt-dlp", error)
            AnalisisPerf.mark("YouTube nativo falló; respaldo con yt-dlp", "$host: ${error.javaClass.simpleName}")
            null
        }
    }

    private suspend fun analyzeFull(url: String): Analyzed = guard {
        val info = engine.fetchInfo(url)
        AnalisisPerf.measure("formatos: extraer opciones y tamaños estimados (InfoParser)", AnalisisPerf.hostOf(url)) {
            Analyzed(info, InfoParser.parse(info, url))
        }
    }

    /** Cualquier error inesperado se entrega como [MediaError] para que la interfaz lo explique. */
    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: MediaError) {
        throw error
    } catch (error: Exception) {
        android.util.Log.e("MediaExtractor", "Fallo inesperado al analizar", error)
        throw MediaError.Unknown(error.message)
    }
}


/** Análisis recientes en memoria (30 enlaces): repetir o reintentar un enlace es instantáneo. */
internal class AnalysisCache(
    private val ttlMs: Long = 10L * 60 * 1000,
    private val maxEntries: Int = 30,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry(val savedAt: Long, val result: AnalysisResult)

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > maxEntries
    }

    @Synchronized
    fun get(url: String): AnalysisResult? {
        val key = AnalysisCacheKey.of(url)
        val entry = entries[key] ?: return null
        if (clock() - entry.savedAt > ttlMs) {
            entries.remove(key)
            return null
        }
        return entry.result
    }

    @Synchronized
    fun put(url: String, result: AnalysisResult) {
        entries[AnalysisCacheKey.of(url)] = Entry(clock(), result)
    }
}

/** Si NewPipe no responde en este tiempo, se usa yt-dlp. */
private const val NATIVE_TIMEOUT_MS = 12_000L

/** Un análisis guardado sirve durante 6 horas (las calidades de un video casi nunca cambian en ese tiempo). */
private const val CACHE_TTL_MS = 6L * 60 * 60 * 1000
