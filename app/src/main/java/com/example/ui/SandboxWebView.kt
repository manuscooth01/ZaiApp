package com.example.ui

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File

class SandboxWebView(context: Context) : WebView(context) {
    private var pyodideReady = false
    private var resultCallback: ((String) -> Unit)? = null

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webChromeClient = WebChromeClient()
        webViewClient = WebViewClient()
        addJavascriptInterface(PythonBridge(), "androidBridge")
    }

    fun loadPyodide() {
        val html = """
        <!DOCTYPE html>
        <html>
        <head><meta charset="utf-8"></head>
        <body>
        <script src="https://cdn.jsdelivr.net/pyodide/v0.25.0/full/pyodide.js"></script>
        <script>
            let pyodide;
            async function main() {
                pyodide = await loadPyodide();
                await pyodide.loadPackage("micropip");
                androidBridge.onReady();
            }
            main();
            async function run(code) {
                try {
                    pyodide.runPython("import sys; from io import StringIO; sys.stdout = StringIO()");
                    await pyodide.runPythonAsync(code);
                    const stdout = pyodide.runPython("sys.stdout.getvalue()");
                    pyodide.runPython("sys.stdout = sys.__stdout__");
                    androidBridge.onResult(stdout || "(sin salida)");
                } catch (err) {
                    androidBridge.onError(err.message);
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
        }
        @JavascriptInterface
        fun onResult(result: String) {
            resultCallback?.invoke(result)
            resultCallback = null
        }
        @JavascriptInterface
        fun onError(error: String) {
            resultCallback?.invoke("Error: $error")
            resultCallback = null
        }
    }

    fun execute(code: String, callback: (String) -> Unit) {
        val escaped = code.replace("\\", "\\\\").replace("`", "\\`")
        resultCallback = callback
        if (pyodideReady) {
            evaluateJavascript("run(`$escaped`)", null)
        } else {
            postDelayed({ execute(code, callback) }, 500)
        }
    }
}
