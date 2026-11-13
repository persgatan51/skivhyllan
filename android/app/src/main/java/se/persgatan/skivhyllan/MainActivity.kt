package se.persgatan.skivhyllan

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import java.io.File

/**
 * Appen visar webbappen (GitHub Pages). Skalet lägger till tre saker:
 *  - kameran/bildväljaren för <input type="file">,
 *  - att Spotify-länkar öppnas i Spotify-appen,
 *  - att Spotify-inloggningen sker inne i appen så att svaret kommer tillbaka hit.
 */
class MainActivity : Activity() {

    companion object {
        const val START_URL = "https://persgatan51.github.io/skivhyllan/"
        const val HOST = "persgatan51.github.io"
        const val REQ_FILE = 42
        // Sidor som ska visas inne i appen (Spotify-inloggningen). Allt annat öppnas utanför.
        val INSIDE = setOf(HOST, "accounts.spotify.com", "challenge.spotify.com", "accounts.scdn.co")
    }

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var photoUri: Uri? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.host in INSIDE) return false
                openExternal(uri)
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val intent = if (params.isCaptureEnabled) {
                    val dir = File(cacheDir, "photos").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val file = File(dir, "skiva-${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
                    photoUri = uri
                    Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        .putExtra(MediaStore.EXTRA_OUTPUT, uri)
                        .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    photoUri = null
                    params.createIntent()
                }
                try {
                    startActivityForResult(intent, REQ_FILE)
                } catch (_: Exception) {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = null
                }
                return true
            }
        }

        web.addJavascriptInterface(Bridge(), "App")
        // ?v= gör att sidan alltid hämtas färsk vid start (GitHub Pages cachar annars i upp till 10 min).
        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl(START_URL + "?v=" + System.currentTimeMillis())
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_FILE) return
        val cb = fileCallback ?: return
        fileCallback = null
        val result: Array<Uri>? = when {
            resultCode != RESULT_OK -> null
            photoUri != null -> arrayOf(photoUri!!)
            else -> WebChromeClient.FileChooserParams.parseResult(resultCode, data)
        }
        cb.onReceiveValue(result)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Låt webbappen stänga öppna rutor först; annars vanlig bakåt.
        web.evaluateJavascript("(window.__back && window.__back()) ? 'handled' : 'no'") { r ->
            if (r?.contains("handled") != true) {
                if (web.canGoBack()) web.goBack() else finishAfterTransition()
            }
        }
    }

    private fun openExternal(uri: Uri) {
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {}
    }

    inner class Bridge {
        @JavascriptInterface fun version(): String = "1"
        @JavascriptInterface fun openExternal(url: String) = runOnUiThread { this@MainActivity.openExternal(Uri.parse(url)) }
    }
}
