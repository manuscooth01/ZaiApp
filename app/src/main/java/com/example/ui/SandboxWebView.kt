package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
class SandboxWebView(context: Context) : WebView(context) {
    @Volatile
    private var pyodideReady = false
    private var resultCallback: ((String) -> Unit)? = null
    private var pendingCode: String? = null

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // page loaded; pyodide still boots async
            }
        }
        addJavascriptInterface(PythonBridge(), "androidBridge")
    }

    fun loadPyodide() {
        val html = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <style>
            body { background:#0D0D0D; color:#A1A1AA; font-family: monospace; margin:12px; font-size:12px; }
            #status { color:#FF5722; }
          </style>
        </head>
        <body>
        <div id="status">Cargando Pyodide...</div>
        <pre id="out"></pre>
        <script src="https://cdn.jsdelivr.net/pyodide/v0.25.0/full/pyodide.js"></script>
        <script>
            let pyodide;
            async function main() {
                try {
                    document.getElementById('status').innerText = 'Descargando runtime Python...';
                    pyodide = await loadPyodide();
                    document.getElementById('status').innerText = 'Pyodide listo';
                    androidBridge.onReady();
                } catch (e) {
                    document.getElementById('status').innerText = 'Error: ' + e;
                    androidBridge.onError(String(e));
                }
            }
            main();
            async function run(code) {
                try {
                    document.getElementById('status').innerText = 'Ejecutando...';
                    pyodide.runPython(`
import sys
from io import StringIO
sys.stdout = StringIO()
sys.stderr = StringIO()
`);
                    let result = await pyodide.runPythonAsync(code);
                    let stdout = pyodide.runPython("sys.stdout.getvalue()");
                    let stderr = pyodide.runPython("sys.stderr.getvalue()");
                    pyodide.runPython("sys.stdout = sys.__stdout__; sys.stderr = sys.__stderr__");
                    let out = "";
                    if (stdout) out += stdout;
                    if (stderr) out += (out ? "\n" : "") + stderr;
                    if (result !== undefined && result !== null && String(result) !== "undefined") {
                        out += (out ? "\n" : "") + String(result);
                    }
                    if (!out) out = "(sin salida)";
                    document.getElementById('out').innerText = out;
                    document.getElementById('status').innerText = 'Listo';
                    androidBridge.onResult(out);
                } catch (err) {
                    const msg = "Error: " + (err.message || err);
                    document.getElementById('out').innerText = msg;
                    document.getElementById('status').innerText = 'Error';
                    androidBridge.onError(msg);
                }
            }
        </script>
        </body>
        </html>
        """.trimIndent()
        val tempFile = File(context.filesDir, "sandbox.html")
        tempFile.writeText(html)
        loadUrl("file://${tempFile.absolutePath}")
    }

    inner class PythonBridge {
        @JavascriptInterface
        fun onReady() {
            pyodideReady = true
            val code = pendingCode
            if (code != null) {
                pendingCode = null
                post { runJs(code) }
            }
        }

        @JavascriptInterface
        fun onResult(result: String) {
            post {
                resultCallback?.invoke(result)
                resultCallback = null
            }
        }

        @JavascriptInterface
        fun onError(error: String) {
            post {
                resultCallback?.invoke(error)
                resultCallback = null
            }
        }
    }

    private fun runJs(code: String) {
        val escaped = code
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("$", "\\$")
            .replace("\r", "")
        evaluateJavascript("run(`$escaped`)", null)
    }

    fun execute(code: String, callback: (String) -> Unit) {
        resultCallback = callback
        if (pyodideReady) {
            runJs(code)
        } else {
            pendingCode = code
            // retry a few times in case onReady raced
            postDelayed({
                if (pyodideReady && pendingCode != null) {
                    val c = pendingCode!!
                    pendingCode = null
                    runJs(c)
                }
            }, 800)
        }
    }
}
