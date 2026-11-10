package com.autocrop.scanner

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var filter: Spinner

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pages = scan?.pages.orEmpty()
            if (pages.isEmpty()) {
                message("No pages were returned. Please scan again.")
            } else {
                processPages(pages.map { it.imageUri })
            }
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            message("Scan cancelled.")
        } else {
            message("Scanner could not complete. Please try again.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(Color.rgb(246, 248, 252))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val heading = TextView(this).apply {
            text = "AutoCrop Scanner"
            textSize = 28f
            setTextColor(Color.rgb(20, 35, 60))
            gravity = Gravity.CENTER
            setPadding(0, 22, 0, 8)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val subtitle = TextView(this).apply {
            text = "Scan paper documents, receipts and notes"
            textSize = 15f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(8, 0, 8, 24)
        }
        content.addView(heading, matchWrap())
        content.addView(subtitle, matchWrap())

        val scanButton = actionButton("Scan document automatically") { startScanner() }
        content.addView(scanButton, matchWrap())

        val filterTitle = TextView(this).apply {
            text = "Enhancement after scan"
            textSize = 16f
            setTextColor(Color.rgb(35, 48, 70))
            setPadding(4, 24, 4, 8)
        }
        content.addView(filterTitle, matchWrap())
        filter = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Auto clean", "Original colour", "Grayscale", "Black & white")
            )
        }
        content.addView(filter, matchWrap())

        val info = TextView(this).apply {
            text = "Automatic page-edge detection • Perspective correction • Multi-page PDF • Local processing"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(4, 20, 4, 18)
        }
        content.addView(info, matchWrap())
        status = TextView(this).apply {
            text = "Ready to scan"
            textSize = 14f
            setTextColor(Color.rgb(35, 90, 65))
            setPadding(4, 16, 4, 8)
        }
        content.addView(status, matchWrap())
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun actionButton(label: String, action: () -> Unit) =
        Button(this).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            setPadding(12, 18, 12, 18)
            setOnClickListener { action() }
        }

    private fun startScanner() {
        status.text = "Opening document camera…"
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setGalleryImportAllowed(true)
            .setPageLimit(50)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { error ->
                message("Could not start scanner: ${error.localizedMessage ?: "Unknown error"}")
            }
    }

    private fun processPages(uris: List<Uri>) {
        status.text = "Enhancing ${uris.size} page(s)…"
        Thread {
            try {
                val bitmaps = uris.mapNotNull { uri ->
                    contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input)?.let { enhance(it, filter.selectedItemPosition) }
                    }
                }
                if (bitmaps.isEmpty()) throw IllegalStateException("Could not read scanned pages")
                val savedImages = bitmaps.mapIndexed { index, bitmap ->
                    saveJpeg(bitmap, "AutoCrop_${timestamp()}_${index + 1}.jpg")
                }
                val pdfUri = savePdf(bitmaps, "AutoCrop_${timestamp()}.pdf")
                runOnUiThread {
                    status.text = "Done: ${savedImages.size} enhanced image(s) and 1 PDF saved."
                    message("Saved ${savedImages.size} image(s) and PDF to Documents/Pictures > AutoCrop.")
                }
            } catch (error: Exception) {
                runOnUiThread { message("Enhancement failed: ${error.localizedMessage}") }
            }
        }.start()
    }

    private fun enhance(source: Bitmap, mode: Int): Bitmap {
        val maxSide = 2400
        val scale = min(1f, maxSide.toFloat() / max(source.width, source.height))
        val input = if (scale < 1f) Bitmap.createScaledBitmap(
            source, (source.width * scale).toInt(), (source.height * scale).toInt(), true
        ) else source
        val output = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(input.width * input.height)
        input.getPixels(pixels, 0, input.width, 0, 0, input.width, input.height)
        val auto = mode == 0
        for (i in pixels.indices) {
            val c = pixels[i]
            var r = Color.red(c).toFloat()
            var g = Color.green(c).toFloat()
            var b = Color.blue(c).toFloat()
            if (mode == 2 || mode == 3 || auto) {
                val y = 0.299f * r + 0.587f * g + 0.114f * b
                if (mode == 3) {
                    val v = if (y > 176f) 255 else 0
                    r = v.toFloat(); g = v.toFloat(); b = v.toFloat()
                } else {
                    val contrast = if (auto) 1.22f else 1.08f
                    val v = ((y - 128f) * contrast + 138f).coerceIn(0f, 255f)
                    r = v; g = v; b = v
                }
            } else {
                val contrast = 1.12f
                r = ((r - 128f) * contrast + 136f).coerceIn(0f, 255f)
                g = ((g - 128f) * contrast + 136f).coerceIn(0f, 255f)
                b = ((b - 128f) * contrast + 136f).coerceIn(0f, 255f)
            }
            pixels[i] = Color.rgb(r.toInt(), g.toInt(), b.toInt())
        }
        output.setPixels(pixels, 0, input.width, 0, 0, input.width, input.height)
        if (input !== source) input.recycle()
        return output
    }

    private fun saveJpeg(bitmap: Bitmap, name: String): Uri {
        val values = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AutoCrop")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create image file")
        contentResolver.openOutputStream(uri)?.use {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it)) {
                throw IllegalStateException("Could not write image")
            }
        } ?: throw IllegalStateException("Could not open image output")
        return uri
    }

    private fun savePdf(bitmaps: List<Bitmap>, name: String): Uri {
        val document = PdfDocument()
        try {
            bitmaps.forEachIndexed { index, bitmap ->
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                document.finishPage(page)
            }
            val values = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/AutoCrop")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create PDF file")
            contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
                ?: throw IllegalStateException("Could not open PDF output")
            return uri
        } finally {
            document.close()
            bitmaps.forEach { it.recycle() }
        }
    }

    private fun timestamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun message(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        if (::status.isInitialized) status.text = text
    }
}
