package com.aont.youtubeopener

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.aont.youtubeopener.ui.theme.YouTubeOpenerThemeimport androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YouTubeOpenerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    YouTubeLauncherScreen(
                        onOpen = { inputUrl -> openUrl(inputUrl) }
                    )
                }
            }
        }
    }

    private fun openUrl(input: String) {
        lifecycleScope.launch {
            val request = normalizeUrlForLaunch(this@MainActivity, input)
                ?: throw IllegalArgumentException("Invalid supported URL")
            val uri = request.url.toUri()

            val packageIntents = request.packages.map { packageName ->
                Intent(Intent.ACTION_VIEW, uri).setPackage(packageName)
            }
            val candidates = packageIntents + Intent(Intent.ACTION_VIEW, uri)

            val pm = packageManager
            val intentToLaunch = candidates.firstOrNull { it.resolveActivity(pm) != null }
                ?: throw ActivityNotFoundException("No app can handle URL intent")

            startActivity(intentToLaunch)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun YouTubeLauncherScreen(
    onOpen: (String) -> Unit
) {
    var urlText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 900.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Enter a YouTube or Prime Video URL and press \"Open\"")

            Spacer(Modifier.height(16.dp))

            // TextField for TV
            TextField(
                value = urlText,
                onValueChange = {
                    urlText = it
                    errorText = null
                },
                placeholder = { Text("https://www.youtube.com/watch?v=... / https://watch.amazon.co.jp/detail?...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = {
                        if (!isSupportedInput(urlText)) {
                            errorText = "Please enter a valid supported URL"
                        } else {
                            errorText = null
                            onOpen(urlText)
                        }                    }
                )
            )


            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (!isSupportedInput(urlText)) {
                        errorText = "Please enter a valid supported URL"
                    } else {
                        errorText = null
                        onOpen(urlText)
                    }                }
            ) {
                Text("Open")
            }

            if (errorText != null) {
                Spacer(Modifier.height(12.dp))
                Text(errorText!!)
            }
        }
    }
}

/**
 * Roughly normalizes a YouTube URL and returns it as an https URL.
 * - Minimally covers watch?v= / youtu.be/ / shorts/
 * - If the input is only a videoId, converts it to watch?v= (optional behavior)
 */
private fun normalizeYouTubeUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    // 1) If it is already http(s)
    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        val uri = runCatching { trimmed.toUri() }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null

        // youtu.be/<id>
        if (host.contains("youtu.be")) {
            val id = uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
            return "https://www.youtube.com/watch?v=$id"
        }

        // youtube.com/watch?v=<id>
        if (host.contains("youtube.com")) {
            val segments = uri.pathSegments

            // /watch?v=
            if (segments.firstOrNull() == "watch") {
                val id = uri.getQueryParameter("v")?.takeIf { it.isNotBlank() } ?: return null
                return "https://www.youtube.com/watch?v=$id"
            }

            // /shorts/<id>
            if (segments.firstOrNull() == "shorts") {
                val id = segments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
                return "https://www.youtube.com/shorts/$id"
            }

            // /live/<id>, etc. are passed through as-is (allowed as long as the host is youtube.com)
            return trimmed
        }

        // Reject non-YouTube URLs
        return null
    }

    // 2) Fallback when only a videoId is entered (optional)
    val looksLikeId = trimmed.length in 8..20 && trimmed.all { it.isLetterOrDigit() || it == '_' || it == '-' }
    if (looksLikeId) {
        return "https://www.youtube.com/watch?v=$trimmed"
    }

    return null
}

private data class LaunchRequest(
    val url: String,
    val packages: List<String>
)

private const val YT_TV_PACKAGE_NAME = "com.google.android.youtube.tv"
private const val YT_MOBILE_PACKAGE_NAME = "com.google.android.youtube"
private const val AMAZON_VIDEO_PACKAGE_NAME = "com.amazon.amazonvideo.livingroom"

private fun isSupportedInput(input: String): Boolean {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return false

    val uri = runCatching { trimmed.toUri() }.getOrNull()
    if (uri != null && uri.scheme.equals("https", ignoreCase = true)) {
        val host = uri.host?.lowercase()
        if (host == "watch.amazon.co.jp" && uri.path == "/detail") return true
        if ((host == "www.amazon.co.jp" || host == "amazon.co.jp") && extractAsinFromAmazonUri(uri) != null) {
            return true
        }
    }
    return normalizeYouTubeUrl(trimmed) != null
}

private suspend fun normalizeUrlForLaunch(activity: ComponentActivity, input: String): LaunchRequest? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    val uri = runCatching { trimmed.toUri() }.getOrNull()
    if (uri != null && uri.scheme.equals("https", ignoreCase = true)) {        val host = uri.host?.lowercase()
        if (host == "watch.amazon.co.jp" && uri.path == "/detail") {
            return LaunchRequest(
                url = trimmed,
                packages = listOf(AMAZON_VIDEO_PACKAGE_NAME)
            )
        }
        if (host == "www.amazon.co.jp" || host == "amazon.co.jp") {
            val asin = extractAsinFromAmazonUri(uri) ?: return null
            val gti = resolveAmazonGtiFromWebView(activity, trimmed, asin) ?: return null
            return LaunchRequest(
                url = "https://watch.amazon.co.jp/detail?gti=$gti",
                packages = listOf(AMAZON_VIDEO_PACKAGE_NAME)
            )
        }
    }

    val normalizedYouTube = normalizeYouTubeUrl(trimmed) ?: return null
    return LaunchRequest(
        url = normalizedYouTube,
        packages = listOf(YT_TV_PACKAGE_NAME, YT_MOBILE_PACKAGE_NAME)
    )
}

private fun extractAsinFromAmazonUri(uri: android.net.Uri): String? {
    val segments = uri.pathSegments
    val dpIndex = segments.indexOf("dp")
    val asin = segments.getOrNull(dpIndex + 1)?.trim()?.uppercase()
    return asin?.takeIf { it.matches(Regex("[A-Z0-9]{10}")) }
}

private suspend fun resolveAmazonGtiFromWebView(
    activity: ComponentActivity,
    productUrl: String,
    asin: String
): String? = suspendCancellableCoroutine { continuation ->
    val webView = WebView(activity)
    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true

    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            view.evaluateJavascript(
                "(function(){var el=document.querySelector('script#dv-web-page-hydration-data'); return el?el.innerHTML:null;})()"
            ) { scriptContent ->
                val hydrationJson = decodeEvaluateJavascriptResult(scriptContent)
                val gti = hydrationJson?.let { extractGtiFromHydrationJson(it, asin) }
                if (continuation.isActive) {
                    continuation.resume(gti)
                }
                view.destroy()
            }
        }
    }

    continuation.invokeOnCancellation { webView.destroy() }
    webView.loadUrl(productUrl)
}

private fun decodeEvaluateJavascriptResult(result: String?): String? {
    if (result == null || result == "null") return null
    return runCatching { JSONArray("[$result]").getString(0) }.getOrNull()
}

private fun extractGtiFromHydrationJson(rawJson: String, asin: String): String? {
    val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
    val atf = root.optJSONObject("init")
        ?.optJSONObject("preparations")
        ?.optJSONObject("body")
        ?.optJSONObject("atf")
        ?.optJSONObject("state")
    val btf = root.optJSONObject("init")
        ?.optJSONObject("preparations")
        ?.optJSONObject("body")
        ?.optJSONObject("btf")
        ?.optJSONObject("state")

    val headerCatalogId = atf
        ?.optJSONObject("detail")
        ?.optJSONObject("headerDetail")
        ?.optJSONObject(asin)
        ?.optString("catalogId")
        ?.takeIf { !it.isNullOrBlank() }

    val selfGti = atf
        ?.optJSONObject("self")
        ?.optJSONObject(asin)
        ?.optString("gti")
        ?.takeIf { !it.isNullOrBlank() }

    val btfCatalogId = btf
        ?.optJSONObject("detail")
        ?.optJSONObject("btfMoreDetails")
        ?.optJSONObject(asin)
        ?.optString("catalogId")
        ?.takeIf { !it.isNullOrBlank() }

    return headerCatalogId ?: selfGti ?: btfCatalogId
}
