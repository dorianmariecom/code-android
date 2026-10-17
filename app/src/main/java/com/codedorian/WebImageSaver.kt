package com.codedorian

import android.Manifest
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class WebImageSaver(private val fragment: Fragment) {
    private data class ImageDownload(val url: String, val userAgent: String, val cookie: String?)

    private var copyJob: Job? = null
    private var webView: WebView? = null
    private var dialog: AlertDialog? = null
    private var pendingDownload: ImageDownload? = null
    private val permissionLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val download = pendingDownload
            pendingDownload = null
            if (download != null) {
                if (granted) enqueue(download) else showMessage(R.string.save_image_permission_denied)
            }
        }

    fun attach(target: WebView) {
        detach()
        webView = target
        target.setOnLongClickListener {
            val hit = target.hitTestResult
            when (hit.type) {
                WebView.HitTestResult.IMAGE_TYPE -> {
                    showImageMenu(target, hit.extra)
                    true
                }
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    // Resolve the image source rather than downloading the enclosing link.
                    val handler = Handler(Looper.getMainLooper()) { message ->
                        if (webView === target && fragment.view != null) {
                            showImageMenu(target, message.data.getString("src"))
                        }
                        true
                    }
                    target.requestFocusNodeHref(handler.obtainMessage())
                    true
                }
                else -> false
            }
        }
    }

    fun detach() {
        copyJob?.cancel()
        copyJob = null
        webView?.setOnLongClickListener(null)
        webView = null
        dialog?.dismiss()
        dialog = null
        pendingDownload = null
    }

    private fun showImageMenu(target: WebView, url: String?) {
        val context = fragment.context ?: return
        if (url.isNullOrBlank()) {
            showMessage(R.string.save_image_failed)
            return
        }
        dialog?.dismiss()
        dialog = AlertDialog.Builder(context)
            .setItems(arrayOf(context.getString(R.string.copy_image), context.getString(R.string.download_image))) { _, action ->
                if (!URLUtil.isHttpUrl(url) && !URLUtil.isHttpsUrl(url)) {
                    showMessage(R.string.save_image_unsupported)
                    return@setItems
                }
                val download = ImageDownload(
                    url,
                    target.settings.userAgentString,
                    CookieManager.getInstance().getCookie(url),
                )
                if (action == 0) copyImage(download) else downloadImage(download)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun downloadImage(download: ImageDownload) {
        val context = fragment.context ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownload = download
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            enqueue(download)
        }
    }

    private fun copyImage(download: ImageDownload) {
        val context = fragment.context ?: return
        if (copyJob?.isActive == true) return
        showMessage(R.string.copy_image_started)
        copyJob = fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { fetchClipboardImage(context, download) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.images", file)
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                    ClipData.newUri(context.contentResolver, context.getString(R.string.copy_image), uri),
                )
                // Android 13 and newer display their own clipboard confirmation.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    showMessage(R.string.copy_image_completed)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showMessage(R.string.copy_image_failed)
            }
        }
    }

    private fun fetchClipboardImage(context: Context, download: ImageDownload): File {
        val directory = File(context.cacheDir, "clipboard_images").apply { mkdirs() }
        // Keep recent clipboard images available to receiving apps for a day.
        directory.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1) }
            ?.forEach { it.delete() }
        val originalUrl = download.url.toHttpUrl()
        var url = originalUrl
        repeat(6) {
            val request = Request.Builder().url(url).header("User-Agent", download.userAgent)
            if (url.scheme == originalUrl.scheme && url.host == originalUrl.host && url.port == originalUrl.port) {
                download.cookie?.takeIf { it.isNotBlank() }?.let { request.header("Cookie", it) }
            }
            clipboardClient.newCall(request.build()).execute().use { response ->
                if (response.code in listOf(301, 302, 303, 307, 308)) {
                    url = response.header("Location")?.let(url::resolve) ?: throw IOException("Missing redirect")
                    return@repeat
                }
                if (!response.isSuccessful) throw IOException("Image request failed")
                val file = File.createTempFile("image-", ".tmp", directory)
                try {
                    response.body.byteStream().use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var total = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count == -1) break
                                total += count
                                if (total > 20 * 1024 * 1024) throw IOException("Image too large")
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.path, options)
                    val extension = when (options.outMimeType) {
                        "image/jpeg" -> "jpg"
                        "image/png" -> "png"
                        "image/gif" -> "gif"
                        "image/webp" -> "webp"
                        "image/heif", "image/heic" -> "heic"
                        "image/avif" -> "avif"
                        else -> throw IOException("Unsupported image")
                    }
                    if (options.outWidth <= 0 || options.outHeight <= 0) throw IOException("Invalid image")
                    val image = File(directory, "${UUID.randomUUID()}.$extension")
                    if (!file.renameTo(image)) throw IOException("Could not store image")
                    return image
                } finally {
                    file.delete()
                }
            }
        }
        throw IOException("Too many redirects")
    }

    private fun enqueue(download: ImageDownload) {
        val context = fragment.context ?: return
        try {
            val fileName = URLUtil.guessFileName(download.url, null, null)
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .takeLast(160)
            val request = DownloadManager.Request(Uri.parse(download.url))
                .setTitle(fileName)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "${UUID.randomUUID()}-$fileName",
                )
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .addRequestHeader("User-Agent", download.userAgent)
            download.cookie?.takeIf { it.isNotBlank() }?.let {
                request.addRequestHeader("Cookie", it)
            }
            @Suppress("DEPRECATION")
            request.allowScanningByMediaScanner()
            context.getSystemService(DownloadManager::class.java).enqueue(request)
            showMessage(R.string.save_image_started)
        } catch (_: RuntimeException) {
            showMessage(R.string.save_image_failed)
        }
    }

    private companion object {
        val clipboardClient = OkHttpClient.Builder()
            .followRedirects(false)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun showMessage(message: Int) {
        fragment.context?.let { Toast.makeText(it, message, Toast.LENGTH_LONG).show() }
    }
}
