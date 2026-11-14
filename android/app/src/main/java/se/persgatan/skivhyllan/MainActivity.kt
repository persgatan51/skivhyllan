package se.persgatan.skivhyllan

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
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
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Appen visar webbappen (GitHub Pages). Skalet lägger till:
 *  - kameran/bildväljaren för <input type="file">,
 *  - App.scanBarcode(): Googles streckkodsskanner (snabbskanning av många skivor),
 *  - App.saveBackup()/loadBackup(): backupfil via Androids filväljare (t.ex. Google Drive),
 *  - App.scan(): läser streckkod och text i fotot med ML Kit (gratis, i telefonen),
 *  - att Spotify-länkar öppnas i Spotify-appen,
 *  - att Spotify-inloggningen sker inne i appen så att svaret kommer tillbaka hit.
 */
class MainActivity : Activity() {

    companion object {
        const val START_URL = "https://persgatan51.github.io/skivbacken/"
        const val HOST = "persgatan51.github.io"
        const val REQ_FILE = 42
        const val REQ_SAVE = 43
        const val REQ_OPEN = 44
        // Sidor som ska visas inne i appen (Spotify-inloggningen). Allt annat öppnas utanför.
        val INSIDE = setOf(HOST, "accounts.spotify.com", "challenge.spotify.com", "accounts.scdn.co")
    }

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var photoUri: Uri? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("skivbacken", MODE_PRIVATE) }
    private var pendingSaveJson: String? = null
    private var pendingSaveId: String? = null
    private var pendingOpenId: String? = null
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
        if (requestCode == REQ_SAVE || requestCode == REQ_OPEN) { onBackupResult(requestCode, resultCode, data); return }
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

    /* ---------- Backup: en JSON-fil på valfri plats, t.ex. Google Drive ---------- */
    private fun writeTo(uri: Uri, json: String) {
        val os = try { contentResolver.openOutputStream(uri, "wt") } catch (_: Exception) { null }
            ?: contentResolver.openOutputStream(uri, "w") ?: throw Exception("Kan inte skriva filen")
        os.use { it.write(json.toByteArray()) }
    }
    private fun nameOf(uri: Uri): String = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Exception) { null } ?: "backupfilen"

    private fun keep(uri: Uri): Boolean = try {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        prefs.edit().putString("backupUri", uri.toString()).apply(); true
    } catch (_: Exception) { false }

    private fun pickSave(json: String, id: String) {
        pendingSaveJson = json; pendingSaveId = id
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json").putExtra(Intent.EXTRA_TITLE, "skivbacken-backup.json")
        try { startActivityForResult(intent, REQ_SAVE) } catch (e: Exception) { reply(id, false, "Kunde inte öppna filväljaren") }
    }
    private fun pickOpen(id: String) {
        pendingOpenId = id
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain", "application/octet-stream"))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        try { startActivityForResult(intent, REQ_OPEN) } catch (e: Exception) { reply(id, false, "Kunde inte öppna filväljaren") }
    }
    private fun onBackupResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri = data?.data
        if (requestCode == REQ_SAVE) {
            val id = pendingSaveId ?: return; val json = pendingSaveJson ?: ""
            pendingSaveId = null; pendingSaveJson = null
            if (resultCode != RESULT_OK || uri == null) { reply(id, false, "cancel"); return }
            keep(uri)
            worker.execute {
                try { writeTo(uri, json); reply(id, true, nameOf(uri)) }
                catch (e: Exception) { reply(id, false, "Kunde inte spara: ${e.message}") }
            }
        } else {
            val id = pendingOpenId ?: return
            pendingOpenId = null
            if (resultCode != RESULT_OK || uri == null) { reply(id, false, "cancel"); return }
            keep(uri)   // fortsatta backuper sparas i samma fil
            worker.execute {
                try {
                    val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    reply(id, true, text)
                } catch (e: Exception) { reply(id, false, "Kunde inte läsa filen: ${e.message}") }
            }
        }
    }

    inner class Bridge {
        @JavascriptInterface fun version(): String = "3"

        /** mode: "auto" = bara om en fil redan är vald, "save" = samma fil som förut (annars välj), "pick" = välj ny plats. */
        @JavascriptInterface fun saveBackup(json: String, id: String, mode: String) {
            val saved = prefs.getString("backupUri", null)
            if (mode != "pick" && saved != null) {
                worker.execute {
                    val uri = Uri.parse(saved)
                    try { writeTo(uri, json); reply(id, true, nameOf(uri)) }
                    catch (e: Exception) {
                        if (mode == "auto") reply(id, false, "nouri") else runOnUiThread { pickSave(json, id) }
                    }
                }
            } else if (mode == "auto") reply(id, false, "nouri")
            else runOnUiThread { pickSave(json, id) }
        }
        @JavascriptInterface fun loadBackup(id: String) = runOnUiThread { pickOpen(id) }
        @JavascriptInterface fun backupName(): String =
            prefs.getString("backupUri", null)?.let { nameOf(Uri.parse(it)) } ?: ""

        /** Googles streckkodsskanner. Svarar med siffrorna, "cancel" eller ett fel. */
        @JavascriptInterface fun scanBarcode(id: String) = runOnUiThread {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .enableAutoZoom()
                .build()
            GmsBarcodeScanning.getClient(this@MainActivity, options).startScan()
                .addOnSuccessListener { reply(id, true, it.rawValue ?: "") }
                .addOnCanceledListener { reply(id, false, "cancel") }
                .addOnFailureListener { e -> reply(id, false, "fail:" + (e.message ?: "")) }
        }
        @JavascriptInterface fun scan(b64: String, id: String) = worker.execute {
            try { reply(id, true, this@MainActivity.scan(b64)) }
            catch (e: Exception) { reply(id, false, "Kunde inte läsa av bilden: ${e.message ?: e.javaClass.simpleName}") }
        }
        @JavascriptInterface fun openExternal(url: String) = runOnUiThread { this@MainActivity.openExternal(Uri.parse(url)) }
    }
}
