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
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Appen visar webbappen (GitHub Pages). Skalet lägger till fyra saker:
 *  - kameran/bildväljaren för <input type="file">,
 *  - App.scan(): läser streckkod och text i fotot med ML Kit (gratis, i telefonen),
 *  - att Spotify-länkar öppnas i Spotify-appen,
 *  - att Spotify-inloggningen sker inne i appen så att svaret kommer tillbaka hit.
 */
class MainActivity : Activity() {

    companion object {
        const val START_URL = "https://persgatan51.github.io/skivbacken/"
        const val HOST = "persgatan51.github.io"
        const val REQ_FILE = 42
        // Sidor som ska visas inne i appen (Spotify-inloggningen). Allt annat öppnas utanför.
        val INSIDE = setOf(HOST, "accounts.spotify.com", "challenge.spotify.com", "accounts.scdn.co")
    }

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var photoUri: Uri? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val barcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E
            ).build()
        )
    }
    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

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

    private fun reply(id: String, ok: Boolean, data: String) = runOnUiThread {
        web.evaluateJavascript(
            "window.__cb && window.__cb(${JSONObject.quote(id)}, $ok, ${JSONObject.quote(data)})", null
        )
    }

    /** Läser streckkoder och textrader (med texthöjd, så att webbappen vet vad som är störst). */
    private fun scan(b64: String): String {
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw Exception("Kunde inte läsa bilden")
        val image = InputImage.fromBitmap(bmp, 0)
        val codes = Tasks.await(barcodeScanner.process(image))
        val text = Tasks.await(textRecognizer.process(image))
        val barcodes = JSONArray()
        codes.mapNotNull { it.rawValue }.distinct().forEach { barcodes.put(it) }
        val lines = JSONArray()
        for (block in text.textBlocks) for (line in block.lines) {
            lines.put(JSONObject().put("t", line.text).put("h", line.boundingBox?.height() ?: 0))
        }
        return JSONObject().put("barcodes", barcodes).put("lines", lines).toString()
    }

    inner class Bridge {
        @JavascriptInterface fun version(): String = "2"
        @JavascriptInterface fun scan(b64: String, id: String) = worker.execute {
            try { reply(id, true, this@MainActivity.scan(b64)) }
            catch (e: Exception) { reply(id, false, "Kunde inte läsa av bilden: ${e.message ?: e.javaClass.simpleName}") }
        }
        @JavascriptInterface fun openExternal(url: String) = runOnUiThread { this@MainActivity.openExternal(Uri.parse(url)) }
    }
}
