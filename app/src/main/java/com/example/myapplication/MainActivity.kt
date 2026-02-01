package com.example.myapplication

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import com.example.myapplication.ui.theme.MyApplicationTheme
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction

class MainActivity : ComponentActivity() {

    companion object {
        private const val YT_TV_PACKAGE = "com.google.android.youtube.tv"
        private const val YT_MOBILE_PACKAGE = "com.google.android.youtube"
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    YouTubeLauncherScreen(
                        onOpen = { inputUrl ->
                            val normalized = normalizeYouTubeUrl(inputUrl)
                            if (normalized == null) {
                                // A Toast is also fine here
                                // Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show()
                                throw IllegalArgumentException("Invalid YouTube URL")
                            } else {
                                openYouTubeUrl(normalized)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun openYouTubeUrl(url: String) {
        val uri = Uri.parse(url)

        val candidates = listOf(
            // 1) YouTube app for Android TV
            Intent(Intent.ACTION_VIEW, uri).setPackage(YT_TV_PACKAGE),

            // 2) Regular YouTube app
            Intent(Intent.ACTION_VIEW, uri).setPackage(YT_MOBILE_PACKAGE),

            // 3) Browser (last resort)
            Intent(Intent.ACTION_VIEW, uri)
        )

        val pm = packageManager
        val intentToLaunch = candidates.firstOrNull { it.resolveActivity(pm) != null }
            ?: throw ActivityNotFoundException("No app can handle YouTube intent")

        startActivity(intentToLaunch)
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
            Text("Enter a YouTube URL and press \"Open\"")

            Spacer(Modifier.height(16.dp))

            // TextField for TV
            TextField(
                value = urlText,
                onValueChange = {
                    urlText = it
                    errorText = null
                },
                placeholder = { Text("https://www.youtube.com/watch?v=...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = {
                        val normalized = normalizeYouTubeUrl(urlText)
                        if (normalized == null) {
                            errorText = "Please enter a valid YouTube URL"
                        } else {
                            errorText = null
                            onOpen(normalized)
                        }
                    }
                )
            )


            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val normalized = normalizeYouTubeUrl(urlText)
                    if (normalized == null) {
                        errorText = "Please enter a valid YouTube URL"
                    } else {
                        errorText = null
                        onOpen(normalized)
                    }
                }
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
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
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
