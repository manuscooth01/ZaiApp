package com.example

import android.app.Application
import android.os.Environment
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GroqApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            android.util.Log.e("GroqApp", "Crash: ${throwable.message}")
            // DIAGNOSTICO (temporal): escribe el stack trace en Descargas para
            // poder leer el error real del crash sin adb. Se quitara al arreglar.
            try {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val f = File(dir, "groqapp_crash.txt")
                FileWriter(f, true).use { w ->
                    PrintWriter(w).use { pw ->
                        pw.println("==== ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} ====")
                        throwable.printStackTrace(pw)
                    }
                }
            } catch (_: Exception) {
                // si no puede escribir, al menos queda el Logcat
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // IMPORTANTE: no se borra la base de datos aquí.
        //
        // Antes, ante presión de memoria (algo muy frecuente en gama baja) se llamaba a
        // AppDatabase.getDatabase(this).clearAllTables(), lo que:
        //   1) Ejecutaba una escritura de BD SÍNCRONA en el hilo principal → ANR/tirones.
        //   2) Destruía TODOS los chats del usuario justo cuando el sistema pedía memoria.
        // También se forzaba System.gc(), un antipatrón que provoca pausas de GC completas.
        //
        // El sistema ya recupera memoria por sí mismo; la app no debe eliminar datos del
        // usuario ni forzar el recolector. Si en el futuro se quiere liberar memoria de forma
        // segura, hacerlo con cachés no críticas (p. ej. la caché en memoria de imágenes),
        // nunca con la base de datos.
    }
}
