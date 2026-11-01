package com.arabicjarvis.ai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var widgetVoiceRequest by mutableStateOf(false)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == JarvisWidgetProvider.ACTION_VOICE) widgetVoiceRequest = true
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        widgetVoiceRequest = intent?.action == JarvisWidgetProvider.ACTION_VOICE
        val prefs = getSharedPreferences("server", MODE_PRIVATE)
        setContent {
            var address by remember { mutableStateOf(prefs.getString("url", "") ?: "") }
            var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
            var message by remember { mutableStateOf("") }
            var reply by remember { mutableStateOf("اضبط رابط السيرفر HTTPS ورمز الوصول، ثم ابدأ المحادثة.") }
            var busy by remember { mutableStateOf(false) }
            fun send(text: String) {
                if (busy || text.isBlank()) return
                if (!address.startsWith("https://")) { reply = "يجب استخدام رابط HTTPS صالح"; return }
                prefs.edit().putString("url", address.trimEnd('/')).putString("token", token).apply()
                busy = true
                Thread {
                    val result = try {
                        val conn = URL(address.trimEnd('/') + "/v1/ask").openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.connectTimeout = 15000
                        conn.readTimeout = 135000
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        conn.setRequestProperty("Authorization", "Bearer $token")
                        conn.outputStream.use { it.write(JSONObject().put("message", text).toString().toByteArray(Charsets.UTF_8)) }
                        val status = conn.responseCode
                        val response = if (status in 200..299) conn.inputStream.bufferedReader().use { it.readText() }
                            else ""
                        conn.disconnect()
                        if (status in 200..299) JSONObject(response).getString("reply") else "خطأ من السيرفر: HTTP $status"
                    } catch (e: Exception) { "تعذر الاتصال: ${e.javaClass.simpleName}" }
                    mainHandler.post { reply = result; busy = false; if (!result.startsWith("خطأ") && !result.startsWith("تعذر")) tts?.speak(result, TextToSpeech.QUEUE_FLUSH, null, "answer") }
                }.start()
            }
            val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { message = it; send(it) }
                }
            }
            fun startVoice() {
                try {
                    recognizer.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث بالعربية")
                    })
                } catch (_: Exception) { reply = "خدمة التعرف على الكلام غير متوفرة" }
            }
            val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) startVoice() else reply = "صلاحية الميكروفون مطلوبة" }
            LaunchedEffect(widgetVoiceRequest) {
                if (widgetVoiceRequest) {
                    widgetVoiceRequest = false
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoice()
                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            val context = LocalContext.current
            MaterialTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("جارفيس العربي — OpenJarvis", style = MaterialTheme.typography.headlineSmall)
                    Text("تطبيق Android متصل بمحرك OpenJarvis على السيرفر")
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("رابط السيرفر HTTPS") }, placeholder = { Text("https://jarvis.example.com") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("رمز الوصول الخاص") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("اكتب أمرك بالعربية") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { send(message) }, enabled = !busy && message.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "جاري الاتصال..." else "إرسال إلى OpenJarvis") }
                    Button(onClick = { if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoice() else micPermission.launch(Manifest.permission.RECORD_AUDIO) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("🎤 تحدث بالعربية") }
                    Card(Modifier.fillMaxWidth()) { Text(reply, Modifier.padding(16.dp)) }
                    Text("يتطلب اتصال HTTPS موثوقًا. قدرات الوكلاء والمهارات تتحدد بإعداد OpenJarvis على السيرفر.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts?.language = Locale("ar", "SA") }
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}
