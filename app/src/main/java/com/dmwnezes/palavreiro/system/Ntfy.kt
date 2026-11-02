package com.dmwnezes.palavreiro.system

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Mensagens em tempo real pelo ntfy.sh (público, sem conta) — o mesmo canal que o site usa.
 * [events] entrega primeiro o histórico do tópico e depois as mensagens novas, cada uma uma vez só,
 * reconectando sozinho se a conexão cair.
 */
class Ntfy(http: OkHttpClient, private val base: String = "https://ntfy.sh", private val reserve: String? = null) {
    private val client = http.newBuilder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val streamClient = http.newBuilder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()
    private val sendLock = Mutex()
    private val reserveLock = Mutex()
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    sealed interface Event {
        data class Message(val id: String, val text: String) : Event
        /** true = conectado; false = caiu e está reconectando. */
        data class Connection(val online: Boolean) : Event
    }

    /** POST simples; false se o servidor recusar ou não responder. */
    private fun post(server: String, topic: String, text: String): Boolean = runCatching {
        client.newCall(Request.Builder().url("$server/$topic").post(text.toRequestBody("text/plain".toMediaType())).build())
            .execute().use { it.isSuccessful }
    }.getOrDefault(false)

    /** Com servidor reserva, a mensagem leva um "_u" único: quem trocar de servidor não recebe a mesma duas vezes. */
    private fun tagged(text: String): String {
        if (reserve == null || !text.startsWith("{")) return text
        val uid = UUID.randomUUID().toString().take(12)
        return if (text.length > 2) "{\"_u\":\"$uid\"," + text.substring(1) else text
    }

    /**
     * Publica uma mensagem nos dois servidores; tenta de novo algumas vezes no principal.
     * Envios saem na ordem em que foram pedidos, em cada servidor.
     */
    suspend fun publish(topic: String, text: String): Boolean = withContext(Dispatchers.IO) {
        val body = tagged(text)
        val second = reserve?.let { r -> background.async { reserveLock.withLock { post(r, topic, body) } } }
        val first = sendLock.withLock {
            repeat(4) { attempt ->
                if (post(base, topic, body)) return@withLock true
                delay(700L * (attempt + 1))
            }
            false
        }
        first || (second?.await() ?: false)
    }

    /** Textos guardados no tópico, do primeiro servidor que tiver alguma mensagem. */
    suspend fun history(topic: String): List<String> = withContext(Dispatchers.IO) {
        for (server in listOfNotNull(base, reserve)) {
            val lines = runCatching {
                client.newCall(Request.Builder().url("$server/$topic/json?poll=1&since=all").build()).execute().use { r ->
                    if (r.isSuccessful) r.body?.charStream()?.buffered()?.readLines() else null
                }
            }.getOrNull().orEmpty()
            val out = lines.mapNotNull { l -> runCatching { JSONObject(l) }.getOrNull()?.takeIf { it.optString("event") == "message" }?.optString("message") }
            if (out.isNotEmpty()) return@withContext out
        }
        emptyList()
    }

    fun events(topic: String): Flow<Event> = channelFlow {
        val seen = HashSet<String>()
        var last: String? = null
        // Duas falhas seguidas: passa a ouvir o outro servidor, desde o começo do histórico (repetidas são ignoradas pelo "_u").
        var server = base
        var fails = 0
        // Chamada aberta agora (para cancelar na hora em que a tela fecha, sem esperar o servidor).
        var open: okhttp3.Call? = null
        val lock = Mutex()
        suspend fun handle(line: String) {
            val o = runCatching { JSONObject(line) }.getOrNull() ?: return
            if (o.optString("event") != "message") return
            val id = o.optString("id"); if (id.isEmpty()) return
            lock.withLock {
                if (!seen.add(id)) return
                last = id
                val text = o.optString("message")
                val uid = runCatching { JSONObject(text).optString("_u") }.getOrDefault("")
                if (uid.isNotEmpty() && !seen.add(uid)) return
                send(Event.Message(id, text))
            }
        }
        // Histórico do tópico. O ntfy.sh leva ~1 s para guardar cada mensagem, então ele é lido
        // de novo logo depois de cada conexão ao vivo, para não perder nada que chegou no meio.
        suspend fun poll(): Boolean = runCatching {
            client.newCall(Request.Builder().url("$server/$topic/json?poll=1&since=all").build()).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                r.body?.charStream()?.buffered()?.readLines()
            }
        }.getOrNull()?.let { lines -> lines.forEach { handle(it) }; true } ?: false

        launch(Dispatchers.IO) {
            poll()
            while (isActive) {
                try {
                    val url = "$server/$topic/sse?since=${last ?: "all"}"
                    val call = streamClient.newCall(Request.Builder().url(url).build()).also { open = it }
                    call.execute().use { r ->
                        if (!r.isSuccessful) error("HTTP ${r.code}")
                        fails = 0
                        send(Event.Connection(true))
                        val catchUp = launch { delay(2500); poll() }
                        val reader = r.body!!.charStream().buffered()
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            if (line.startsWith("data:")) handle(line.removePrefix("data:").trim())
                        }
                        catchUp.cancel()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                if (!isActive) break
                send(Event.Connection(false))
                delay(2000)
                if (++fails >= 2 && reserve != null) {
                    fails = 0
                    server = if (server == base) reserve else base
                    last = null
                    poll()
                }
            }
        }
        awaitClose { open?.cancel() }
    }.flowOn(Dispatchers.IO)
}
