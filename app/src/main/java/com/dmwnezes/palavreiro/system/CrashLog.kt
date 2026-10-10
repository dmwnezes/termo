package com.dmwnezes.palavreiro.system

import android.content.Context
import android.os.Build
import com.dmwnezes.palavreiro.update.Updater

/**
 * Se o app fechar por um erro, guarda o motivo para mostrar na próxima abertura
 * (com um botão para copiar e mandar para o desenvolvedor).
 */
object CrashLog {
    private const val PREFS = "palavreiro_crash"
    private const val KEY = "last"
    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val text = buildString {
                    append("Palavreiro ").append(runCatching { Updater.currentName }.getOrDefault("?"))
                    append(" · Android ").append(Build.VERSION.RELEASE).append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
                    append(error.stackTraceToString().take(6000))
                }
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, text).commit()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Último erro guardado (e apaga), ou null. */
    fun take(context: Context): String? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getString(KEY, null)?.also { p.edit().remove(KEY).apply() }
    }
}
