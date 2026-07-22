package com.auraconv.pro

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AuraConvPro"
        private const val ASSET_BASE_URL = "https://appassets.android.com/assets/"
    }

    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    // ── FIX: Modern Activity Result API replaces deprecated onRequestPermissionsResult ──
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.i(TAG, "RECORD_AUDIO permission granted by user.")
                webView.reload()
            } else {
                handlePermissionDenied()
            }
        }

    // ── Audio focus change listener ──
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i(TAG, "Audio focus regained — resuming WebView.")
                hasAudioFocus = true
                webView.onResume()
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.i(TAG, "Audio focus lost ($focusChange) — pausing WebView.")
                hasAudioFocus = false
                webView.onPause()
            }
        }
    }

    // ──────────────────────────────────────────────
    //  Lifecycle
    // ──────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        // FIX: removed invalid named-argument syntax "savedInstanceState: Bundle?"
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        setupWebView()
        requestAudioFocus()
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (hasAudioFocus) {
            webView.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    // FIX: Added onDestroy to prevent Chromium renderer memory leak
    override fun onDestroy() {
        abandonAudioFocus()
        webView.destroy()
        super.onDestroy()
    }

    // ── FIX: Back-button navigation guard ──
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // ──────────────────────────────────────────────
    //  WebView Configuration
    // ──────────────────────────────────────────────

    private fun setupWebView() {
        // FIX: GPU-composited layer for canvas/WebGL rendering
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.javaScriptCanOpenWindowsAutomatically = true

        // FIX: Mixed-content policy for any external resources
        @Suppress("DEPRECATION")
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        // Custom user-agent suffix for analytics / server identification
        settings.userAgentString = settings.userAgentString + " AuraConvPro/1.0"

        // ── Secure asset loader (virtual HTTPS for AudioWorklet) ──
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        // ── WebViewClient: intercept asset requests + navigation guard ──
        webView.webViewClient = object : WebViewClientCompat() {

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            // FIX: Restrict navigation to the local asset domain only
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                return if (url.startsWith(ASSET_BASE_URL)) {
                    false // Allow internal asset navigation
                } else {
                    Log.w(TAG, "Blocked external navigation: $url")
                    true  // Block all external URLs
                }
            }

            // FIX: Error logging for failed resource loads
            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                Log.e(TAG, "WebView resource error [${error.errorCode}]: " +
                        "${error.description} → ${request.url}")
            }
        }

        // ── WebChromeClient: bridge OS mic permission → WebView JS permission ──
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val grantedResources = mutableListOf<String>()

                for (resource in request.resources) {
                    if (resource == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                        // FIX: Verify OS-level permission BEFORE granting to WebView
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            grantedResources.add(resource)
                        } else {
                            Log.w(TAG, "WebView requested mic but OS permission not granted.")
                        }
                    }
                    // Additional resources (video, protected media) are denied by default
                }

                if (grantedResources.isNotEmpty()) {
                    request.grant(grantedResources.toTypedArray())
                } else {
                    request.deny()
                }
            }
        }

        // Load the application via the secure virtual domain
        webView.loadUrl("${ASSET_BASE_URL}index.html")
    }

    // ──────────────────────────────────────────────
    //  Runtime Permission Handling
    // ──────────────────────────────────────────────

    private fun checkAndRequestPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.i(TAG, "RECORD_AUDIO already granted.")
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                // FIX: Show rationale dialog before re-requesting
                showPermissionRationale()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Microphone Access Required")
            .setMessage(
                "AuraConv processes live audio from your microphone in real time. " +
                "Without microphone access, the audio engine cannot start."
            )
            .setPositiveButton("Grant") { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            .setNegativeButton("Deny") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Microphone denied — audio engine unavailable.",
                    Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    // FIX: Handle permanent denial ("Don't ask again")
    private fun handlePermissionDenied() {
        if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            // User selected "Don't ask again" — redirect to system settings
            AlertDialog.Builder(this)
                .setTitle("Permission Permanently Denied")
                .setMessage(
                    "Microphone access has been permanently blocked. " +
                    "Please enable it manually in system Settings."
                )
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .setCancelable(false)
                .show()
        } else {
            Toast.makeText(this, "Microphone permission is required for AuraConv.",
                Toast.LENGTH_LONG).show()
        }
    }

    // ──────────────────────────────────────────────
    //  Audio Focus Management
    // ──────────────────────────────────────────────

    private fun requestAudioFocus() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()

        val result = audioManager.requestAudioFocus(audioFocusRequest!!)
        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
        }
        hasAudioFocus = false
    }
}
