package com.arabicjarvis.ai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.content.ComponentName
import android.provider.Settings
import android.media.AudioManager
import android.os.BatteryManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import android.content.pm.PackageManager.NameNotFoundException
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private const val LOCAL = "http://127.0.0.1:8765"
private const val DEFAULT_LAN = "https://192.168.1.228:18765"
private const val MODEL = "/opt/arabic-jarvis/models/qwen2.5-1.5b-instruct-q4_k_m.gguf"

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var widgetVoiceRequest by mutableStateOf(false)
    private val prefs by lazy { getSharedPreferences("server", MODE_PRIVATE) }
    private var currentLanguage = "ar"

    private val termuxPermission = "com.termux.permission.RUN_COMMAND"
    private fun dispatchTermux(action: String): Boolean {
        return try {
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                component = ComponentName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/home/.local/bin/jarvis-control")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf(action))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            startService(intent) != null
        } catch (e: Exception) {
            android.util.Log.e("ArabicJarvis", "Termux command request failed", e)
            false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == JarvisWidgetProvider.ACTION_VOICE) widgetVoiceRequest = true
    }

    private fun setVoice(language: String) {
        currentLanguage = language
        if (!ttsReady) return
        val locale = if (language == "en") Locale.UK else Locale("ar", "SA")
        val engine = tts ?: return
        val available = engine.voices?.filter { it.locale.language == locale.language }.orEmpty()
        val preferred = available.filter { it.locale.country == locale.country }
            .sortedWith(compareBy<android.speech.tts.Voice> { it.isNetworkConnectionRequired }
                .thenByDescending { it.quality }).firstOrNull()
        if (preferred != null) engine.voice = preferred else engine.language = locale
        engine.setSpeechRate(if (language == "en") 0.90f else 0.94f)
        engine.setPitch(0.88f)
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) setVoice(prefs.getString("language", "ar") ?: "ar")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        widgetVoiceRequest = intent?.action == JarvisWidgetProvider.ACTION_VOICE
        setContent {
            var mode by remember { mutableStateOf(prefs.getString("mode", "auto") ?: "auto") }
            var lanUrl by remember { mutableStateOf(prefs.getString("lanUrl", DEFAULT_LAN) ?: DEFAULT_LAN) }
            var lanToken by remember { mutableStateOf(prefs.getString("lanToken", prefs.getString("token", "")) ?: "") }
            var remoteUrl by remember { mutableStateOf(prefs.getString("remoteUrl", "") ?: "") }
            var remoteToken by remember { mutableStateOf(prefs.getString("remoteToken", "") ?: "") }
            var language by remember { mutableStateOf(prefs.getString("language", "ar") ?: "ar") }
            var message by remember { mutableStateOf("") }
            var reply by remember { mutableStateOf("جارفيس جاهز. اختر اللغة وطريقة الاتصال.") }
            var busy by remember { mutableStateOf(false) }
            var activeConnection by remember { mutableStateOf("—") }
            var serverState by remember { mutableStateOf("غير مفحوص") }
            var serverBusy by remember { mutableStateOf(false) }
            var confirmStop by remember { mutableStateOf(false) }
            var page by remember { mutableStateOf("home") }
            var driving by remember { mutableStateOf(false) }
            var pendingTermuxAction by remember { mutableStateOf<String?>(null) }

            fun announceServer(text: String) {
                reply = text
                setVoice(language)
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "server-result")
            }

            fun healthCheck(): Boolean = try {
                listOf(11434, 8765).all { port ->
                    val conn = URL("http://127.0.0.1:$port/health").openConnection() as HttpURLConnection
                    conn.connectTimeout = 2500
                    conn.readTimeout = 2500
                    try { conn.responseCode == 200 } finally { conn.disconnect() }
                }
            } catch (_: Exception) { false }

            fun runServerAction(action: String) {
                if (serverBusy) return
                serverBusy = true
                serverState = "جاري ${if (action == "start") "التشغيل" else if (action == "stop") "الإيقاف" else "الفحص"}..."
                if (action != "status" && !dispatchTermux(action)) {
                    serverBusy = false
                    serverState = "تعذر إرسال الأمر"
                    announceServer("تعذر إرسال الأمر إلى ترمكس، سيدي")
                    return
                }
                Thread {
                    val desired = action != "stop"
                    val deadline = System.currentTimeMillis() + if (action == "status") 1000L else 120000L
                    var ready = healthCheck()
                    while (action != "status" && ready != desired && System.currentTimeMillis() < deadline) {
                        Thread.sleep(1500)
                        ready = healthCheck()
                    }
                    val success = action == "status" || ready == desired
                    mainHandler.post {
                        serverBusy = false
                        serverState = if (ready) "يعمل" else "متوقف / غير متاح"
                        val result = when {
                            action == "status" && ready -> "السيرفر يعمل وجاهز، سيدي"
                            action == "status" -> "السيرفر غير جاهز، سيدي"
                            success && action == "start" -> "تم الأمر سيدي، جارفيس جاهز لخدمتك"
                            success && action == "stop" -> "تم الأمر سيدي، تم إيقاف السيرفر"
                            else -> "تعذر إكمال الأمر سيدي، تحقق من ترمكس"
                        }
                        announceServer(result)
                    }
                }.start()
            }

            val termuxPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                val action = pendingTermuxAction
                pendingTermuxAction = null
                if (granted && action != null) runServerAction(action)
                else announceServer("لم تُمنح صلاحية التحكم في ترمكس")
            }

            fun requestServerAction(action: String) {
                if (action == "status") { runServerAction(action); return }
                if (checkSelfPermission(termuxPermission) == PackageManager.PERMISSION_GRANTED) {
                    runServerAction(action)
                } else {
                    pendingTermuxAction = action
                    termuxPermissionLauncher.launch(termuxPermission)
                }
            }


            fun switchLanguage(next: String) {
                language = next
                prefs.edit().putString("language", next).apply()
                setVoice(next)
                reply = if (next == "en") "Certainly, sir. English mode activated." else "حاضر، تم تفعيل اللغة العربية."
                tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "language-change")
            }

            fun languageCommand(input: String): String? {
                val normalized = input.lowercase(Locale.ROOT).trim()
                val english = listOf("بالإنجليزي", "بالانجليزي", "الإنجليزية", "الانجليزية", "بالإنجليزية", "بالانجليزية", "speak english", "switch to english", "english mode", "talk in english")
                val arabic = listOf("بالعربي", "بالعربية", "العربية", "speak arabic", "switch to arabic", "arabic mode", "talk in arabic")
                return when {
                    english.any { normalized.contains(it) } -> "en"
                    arabic.any { normalized.contains(it) } -> "ar"
                    else -> null
                }
            }

            fun saveSettings() {
                prefs.edit().putString("mode", mode).putString("lanUrl", lanUrl.trim())
                    .putString("lanToken", lanToken).putString("remoteUrl", remoteUrl.trim())
                    .putString("remoteToken", remoteToken).putString("language", language).apply()
            }

            fun phoneCommand(input: String): Boolean {
                val command = input.lowercase(Locale.ROOT)
                return when {
                    listOf("افتح الإعدادات", "افتح الاعدادات", "open settings").any { command.contains(it) } -> {
                        startActivity(Intent(Settings.ACTION_SETTINGS)); true
                    }
                    listOf("حالة البطارية", "كم البطارية", "battery level").any { command.contains(it) } -> {
                        val battery = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                        announceServer(if (language == "en") "Battery level is $level percent, sir" else "مستوى البطارية $level بالمئة، سيدي")
                        true
                    }
                    listOf("ارفع الصوت", "increase volume", "volume up").any { command.contains(it) } -> {
                        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                        announceServer(if (language == "en") "Volume increased, sir" else "تم رفع الصوت سيدي")
                        true
                    }
                    listOf("اخفض الصوت", "decrease volume", "volume down").any { command.contains(it) } -> {
                        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                        announceServer(if (language == "en") "Volume decreased, sir" else "تم خفض الصوت سيدي")
                        true
                    }
                    else -> false
                }
            }

            fun send(text: String) {
                if (busy || text.isBlank()) return
                val command = text.lowercase(Locale.ROOT).trim()
                val serverMention = listOf("سيرفر", "السيرفر", "الخادم", "server").any { command.contains(it) }
                if (serverMention) {
                    when {
                        listOf("شغل", "شغّل", "ابدأ", "start", "turn on").any { command.contains(it) } -> {
                            requestServerAction("start"); return
                        }
                        listOf("أوقف", "اوقف", "وقف", "stop", "turn off").any { command.contains(it) } -> {
                            confirmStop = true; return
                        }
                        listOf("حالة", "افحص", "status", "check").any { command.contains(it) } -> {
                            requestServerAction("status"); return
                        }
                    }
                }
                if (phoneCommand(text)) return
                val change = languageCommand(text)
                if (change != null) { switchLanguage(change); return }
                saveSettings()
                val selectedMode = mode
                val selectedLanguage = language
                val endpoints = when (selectedMode) {
                    "local" -> listOf(Triple("Local", LOCAL, ""))
                    "lan" -> listOf(Triple("LAN", lanUrl.trim(), lanToken))
                    "remote" -> listOf(Triple("Remote", remoteUrl.trim(), remoteToken))
                    else -> listOf(Triple("Local", LOCAL, ""), Triple("LAN", lanUrl.trim(), lanToken), Triple("Remote", remoteUrl.trim(), remoteToken))
                }.filter { (name, url, _) -> url.isNotBlank() && (name == "Local" || url.startsWith("https://", true)) }
                busy = true
                Thread {
                    var answer = "تعذر الوصول إلى جارفيس. تحقق من الاتصال والإعدادات."
                    var used = "—"
                    for ((name, url, auth) in endpoints) {
                        // Preflight only: never retry the actual POST on another server.
                        val ready = try {
                            val health = URL(url.trimEnd('/') + "/health").openConnection() as HttpURLConnection
                            health.connectTimeout = 2500
                            health.readTimeout = 2500
                            if (name != "Local" && auth.isNotBlank()) health.setRequestProperty("Authorization", "Bearer $auth")
                            val code = health.responseCode
                            health.disconnect()
                            code == 200
                        } catch (_: Exception) { false }
                        if (!ready) continue
                        used = name
                        answer = try {
                            val conn = URL(url.trimEnd('/') + "/v1/chat/completions").openConnection() as HttpURLConnection
                            conn.requestMethod = "POST"
                            conn.connectTimeout = 12000
                            conn.readTimeout = 135000
                            conn.doOutput = true
                            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                            if (name != "Local" && auth.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $auth")
                            val instruction = if (selectedLanguage == "en")
                                "Reply in natural British English. Be concise and professional."
                            else "أجب بالعربية الفصحى الطبيعية، بإيجاز ووضوح."
                            val payload = JSONObject().put("model", MODEL)
                                .put("messages", JSONArray()
                                    .put(JSONObject().put("role", "system").put("content", instruction))
                                    .put(JSONObject().put("role", "user").put("content", text)))
                                .put("max_tokens", 256).put("stream", false)
                            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                            val code = conn.responseCode
                            val body = if (code in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else ""
                            conn.disconnect()
                            if (code in 200..299) JSONObject(body).getJSONArray("choices")
                                .getJSONObject(0).getJSONObject("message").getString("content")
                            else "خطأ من السيرفر: HTTP $code"
                        } catch (e: Exception) { "تعذر إكمال الطلب: ${e.javaClass.simpleName}" }
                        break
                    }
                    mainHandler.post {
                        activeConnection = used
                        reply = answer
                        busy = false
                        if (!answer.startsWith("خطأ") && !answer.startsWith("تعذر")) {
                            setVoice(selectedLanguage)
                            tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "answer")
                        }
                    }
                }.start()
            }

            val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                        ?.firstOrNull()?.let { message = it; send(it) }
                }
            }
            fun startVoice() {
                try {
                    recognizer.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == "en") "en-GB" else "ar-SA")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, if (language == "en") "Speak in English" else "تحدث بالعربية")
                    })
                } catch (_: Exception) { reply = "خدمة التعرف على الكلام غير متوفرة" }
            }
            val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                if (it) startVoice() else reply = "صلاحية الميكروفون مطلوبة"
            }
            LaunchedEffect(widgetVoiceRequest) {
                if (widgetVoiceRequest) {
                    widgetVoiceRequest = false
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoice()
                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF58D7FF), onPrimary = Color(0xFF061421),
                background = Color(0xFF071522), surface = Color(0xFF13283B),
                onBackground = Color(0xFFE7F7FF), onSurface = Color(0xFFE7F7FF)
            )) {
                Column(Modifier.fillMaxSize().background(Color(0xFF071522)).verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("J.A.R.V.I.S.", style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold, color = Color(0xFF67DBFF))
                            Text("PERSONAL AI  •  VERSION 4", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { page = if (page == "settings") "home" else "settings" }) {
                            Text(if (page == "settings") "الرئيسية" else "⚙ الإعدادات")
                        }
                    }
                    if (page == "settings") {
                        Text("الاتصال والصوت", style = MaterialTheme.typography.titleLarge)
                        listOf("auto" to "تلقائي", "local" to "الهاتف", "lan" to "Wi-Fi", "remote" to "خارجي").forEach { (key, title) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = mode == key, onClick = { mode = key; saveSettings() })
                                Text(title)
                            }
                        }
                        OutlinedTextField(lanUrl, { lanUrl = it }, label = { Text("LAN HTTPS") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(lanToken, { lanToken = it }, label = { Text("رمز LAN") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(remoteUrl, { remoteUrl = it }, label = { Text("Remote HTTPS") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(remoteToken, { remoteToken = it }, label = { Text("رمز Remote") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                        Button(onClick = { saveSettings(); reply = "تم حفظ إعدادات الاتصال" }, modifier = Modifier.fillMaxWidth()) { Text("حفظ الإعدادات") }
                        Text("اللغة", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { switchLanguage("ar") }) { Text(if (language == "ar") "✓ العربية" else "العربية") }
                            Button(onClick = { switchLanguage("en") }) { Text(if (language == "en") "✓ English" else "English") }
                        }
                        Text("الاستماع التلقائي لكلمة جارفيس يحتاج محرك Wake Word محليًا. غير مفعل في هذه النسخة التجريبية.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("◉", fontSize = 78.sp, color = Color(0xFF59D9FF))
                                Text(if (language == "en") "At your service, sir" else "في خدمتك سيدي", style = MaterialTheme.typography.titleLarge)
                                Text("اضغط الميكروفون للتحدث • الاستماع التلقائي قيد التطوير", style = MaterialTheme.typography.bodySmall)
                                Button(onClick = {
                                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoice()
                                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (language == "en") "🎤 Speak to Jarvis" else "🎤 تحدث مع جارفيس")
                                }
                            }
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("التحكم بالسيرفر", style = MaterialTheme.typography.titleLarge)
                                Text("الحالة: $serverState")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { requestServerAction("start") }, enabled = !serverBusy, modifier = Modifier.weight(1f)) { Text("▶ تشغيل") }
                                    Button(onClick = { confirmStop = true }, enabled = !serverBusy, modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("■ إيقاف") }
                                }
                                OutlinedButton(onClick = { requestServerAction("status") }, enabled = !serverBusy,
                                    modifier = Modifier.fillMaxWidth()) { Text("↻ فحص الحالة") }
                                Text("الاتصال: $activeConnection", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("وضع القيادة", modifier = Modifier.weight(1f))
                            Switch(checked = driving, onCheckedChange = { driving = it })
                        }
                        OutlinedTextField(message, { message = it }, label = { Text(if (language == "en") "Your command" else "اكتب أمرك") },
                            modifier = Modifier.fillMaxWidth(), minLines = if (driving) 1 else 2)
                        Button(onClick = { send(message) }, enabled = !busy && message.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Text(if (busy) "جاري المعالجة..." else "إرسال إلى جارفيس")
                        }
                        Card(Modifier.fillMaxWidth()) { Text(reply, Modifier.padding(16.dp)) }
                        if (!driving) {
                            Text("أوامر الهاتف: افتح الإعدادات، حالة البطارية، ارفع الصوت، اخفض الصوت.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (confirmStop) {
                        AlertDialog(onDismissRequest = { confirmStop = false }, title = { Text("تأكيد الإيقاف") },
                            text = { Text("سيتم إيقاف OpenJarvis وllama.cpp. هل تريد المتابعة؟") },
                            confirmButton = { TextButton(onClick = { confirmStop = false; requestServerAction("stop") }) { Text("إيقاف السيرفر") } },
                            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("إلغاء") } })
                    }
                }
            }
        }
    }
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}
