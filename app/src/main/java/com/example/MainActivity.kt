package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.api.*
import com.example.camera.CardboardKeyboardAnalyzer
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
  private val cameraPermissionRequest = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      startCamera()
    }
  }

  private var cameraProvider: ProcessCameraProvider? = null
  private val suggestions = mutableStateListOf<String>()
  private var isAnalyzing = mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
      startCamera()
    } else {
      cameraPermissionRequest.launch(Manifest.permission.CAMERA)
    }

    setContent {
      MyApplicationTheme {
        MainScreen(suggestions, isAnalyzing.value)
      }
    }
  }

  private fun startCamera() {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
    cameraProviderFuture.addListener({
      cameraProvider = cameraProviderFuture.get()
    }, ContextCompat.getMainExecutor(this))
  }

  fun analyzeImage(base64Image: String) {
    lifecycleScope.launch {
      isAnalyzing.value = true
      val prompt = """
        You are an AI keyboard assistant. The image shows a 'cardboard keyboard' (a physical surface representing a keyboard). 
        Look at the user's fingers and the context. 
        Identify what key is being pressed or what word is being formed.
        Provide exactly 3 predictive text suggestions for the next word.
        Return only the 3 words separated by commas, no other text.
      """.trimIndent()

      val request = OpenAiRequest(
        messages = listOf(
          Message(
            role = "user",
            content = listOf(
              MessageContent(type = "text", text = prompt),
              MessageContent(
                type = "image_url",
                imageUrl = ImageUrl(url = "data:image/jpeg;base64,$base64Image")
              )
            )
          )
        )
      )

      try {
        val response = withContext(Dispatchers.IO) {
          OpenAiRetrofitClient.getService(BuildConfig.OPENAI_API_KEY).getChatCompletion(request)
        }
        val text = response.choices.firstOrNull()?.message?.content
        if (text != null) {
          val newSuggestions = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
          suggestions.clear()
          suggestions.addAll(newSuggestions.take(3))
        }
      } catch (e: Exception) {
        Log.e("OpenAI", "Error: ${e.message}")
      } finally {
        isAnalyzing.value = false
      }
    }
  }
}

@Composable
fun MainScreen(suggestions: List<String>, isAnalyzing: Boolean) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var previewView by remember { mutableStateOf<PreviewView?>(null) }

  Scaffold(
    modifier = Modifier.fillMaxSize()
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      // Camera Preview
      AndroidView(
        factory = { ctx ->
          PreviewView(ctx).also {
            previewView = it
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
              val cameraProvider = cameraProviderFuture.get()
              val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView?.surfaceProvider)
              }

              val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                  it.setAnalyzer(
                    Executors.newSingleThreadExecutor(),
                    CardboardKeyboardAnalyzer { base64 ->
                      (context as? MainActivity)?.analyzeImage(base64)
                    }
                  )
                }

              try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                  lifecycleOwner,
                  CameraSelector.DEFAULT_BACK_CAMERA,
                  preview,
                  imageAnalysis
                )
              } catch (e: Exception) {
                Log.e("Camera", "Binding failed", e)
              }
            }, ContextCompat.getMainExecutor(ctx))
          }
        },
        modifier = Modifier.fillMaxSize()
      )

      // Windows-like Overlay
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp),
        verticalArrangement = Arrangement.Bottom
      ) {
        // Predictive Text Suggestions Bar (Windows Taskbar Style)
        AnimatedVisibility(
          visible = suggestions.isNotEmpty(),
          enter = slideInVertically { it } + fadeIn(),
          exit = slideOutVertically { it } + fadeOut()
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(Color.White.copy(alpha = 0.2f))
              .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
              .blur(10.dp)
              .padding(8.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth()
            ) {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "AI Suggestions",
                tint = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp)
              )
              
              LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
              ) {
                items(suggestions) { suggestion ->
                  SuggestionChip(suggestion)
                }
              }

              if (isAnalyzing) {
                CircularProgressIndicator(
                  modifier = Modifier.size(24.dp),
                  color = Color.White,
                  strokeWidth = 2.dp
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom Navigation (Launcher Style)
        WindowsTaskbar()
      }
      
      // Header Info
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp)
          .align(Alignment.TopCenter)
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "Windows AI Keyboard",
            style = MaterialTheme.typography.headlineMedium.copy(
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          )
          Text(
            text = "Pointing at cardboard keyboard...",
            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f))
          )
        }
      }
    }
  }
}

@Composable
fun SuggestionChip(text: String) {
  Surface(
    onClick = { /* Handle suggestion selection if needed */ },
    color = Color.White.copy(alpha = 0.15f),
    shape = RoundedCornerShape(8.dp),
    modifier = Modifier.testTag("suggestion_chip_$text")
  ) {
    Text(
      text = text,
      color = Color.White,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
    )
  }
}

@Composable
fun WindowsTaskbar() {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(56.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(Color.Black.copy(alpha = 0.6f))
      .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
      .padding(horizontal = 8.dp),
    contentAlignment = Alignment.Center
  ) {
    Row(
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically
    ) {
      TaskbarIcon(Icons.Default.Keyboard, "Keyboard")
      Spacer(modifier = Modifier.width(12.dp))
      // Placeholder for Launcher Icon (Start Button)
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(
            Brush.linearGradient(
              colors = listOf(Color(0xFF00A4EF), Color(0xFF0078D4))
            )
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.AutoAwesome,
          contentDescription = "Start",
          tint = Color.White,
          modifier = Modifier.size(24.dp)
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      TaskbarIcon(Icons.Default.Close, "Exit")
    }
  }
}

@Composable
fun TaskbarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String) {
  IconButton(
    onClick = { /* Action */ },
    modifier = Modifier.size(44.dp)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = description,
      tint = Color.White.copy(alpha = 0.8f),
      modifier = Modifier.size(24.dp)
    )
  }
}
