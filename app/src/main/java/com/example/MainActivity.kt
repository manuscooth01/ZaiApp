package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.ChatMessage
import com.example.ui.AttachedFile
import com.example.ui.SandboxWebView
import com.example.ui.ZaiViewModel
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val viewModel: ZaiViewModel = viewModel()
    val onboardingDone by viewModel.onboardingCompleted.collectAsStateWithLifecycle()
    var showOnboarding by remember { mutableStateOf(!onboardingDone) }
    val context = LocalContext.current

    // Lanzador de archivos
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            val name = getFileName(context, uri) ?: "archivo"
            val type = context.contentResolver.getType(uri) ?: "*/*"
            viewModel.addPendingFile(AttachedFile(uri, name, type))
        }
    }

    // Lanzador de dictado
    var speechCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
            speechCallback?.invoke(spoken)
        }
    }

    if (showOnboarding) {
        OnboardingScreen(viewModel) { showOnboarding = false }
    } else {
        MainScreen(
            viewModel = viewModel,
            onPickFile = { filePicker.launch("*/*") },
            onStartDictation = { callback ->
                speechCallback = callback
                try {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Dicta tu mensaje...")
                    }
                    speechLauncher.launch(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Dictado no soportado", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

// Función auxiliar para obtener nombre de archivo
fun getFileName(context: android.content.Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) it.getString(idx) else null
        } else null
    }
}

@Composable
fun OnboardingScreen(viewModel: ZaiViewModel, onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    Dialog(onDismissRequest = {}) {
        Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)), border = BorderStroke(1.dp, Color(0xFF52525B)), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when (step) {
                    0 -> {
                        Icon(Icons.Default.Bolt, null, tint = Color(0xFFFF5722), modifier = Modifier.size(80.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("¡Bienvenido a GroqApp!", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text("Tu asistente de IA ultrarrápido con Python real y espacio de trabajo.", textAlign = TextAlign.Center, color = Color(0xFFA1A1AA))
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { step = 1 }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))) { Text("Comenzar", color = Color.White) }
                    }
                    1 -> {
                        Text("Tutorial rápido", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFFF5722))
                        Spacer(Modifier.height(16.dp))
                        TutorialCard("💬 Chat", "Conversación directa con la IA. Adjunta imágenes y texto.")
                        TutorialCard("🤖 Agente", "Resuelve tareas complejas. Ejecuta Python automáticamente.")
                        TutorialCard("📁 Espacio", "Tu sandbox de archivos. La IA puede leer y modificar.")
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { step = 2 }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))) { Text("Siguiente") }
                    }
                    2 -> {
                        Text("Configura tu API", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFFF5722))
                        Spacer(Modifier.height(16.dp))
                        Text("Selecciona el proveedor e ingresa tu API Key.", textAlign = TextAlign.Center, color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        var selected by remember { mutableStateOf("Groq") }
                        Box {
                            OutlinedTextField(value = selected, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White))
                            DropdownMenu(expanded = true, onDismissRequest = {}) {
                                viewModel.providers.keys.forEach { p -> DropdownMenuItem(text = { Text(p) }, onClick = { selected = p; viewModel.setProvider(p) }) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        var key by remember { mutableStateOf("") }
                        OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White))
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys")); context.startActivity(i) }) { Text("¿No tienes clave? Obtener aquí") }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            viewModel.saveApiKey(key)
                            viewModel.completeOnboarding()
                            onFinish()
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))) { Text("Guardar y empezar") }
                    }
                }
            }
        }
    }
}

@Composable
fun TutorialCard(icon: String, text: String) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF242427))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Text(text, color = Color.White)
        }
    }
}

@Composable
fun MainScreen(viewModel: ZaiViewModel, onPickFile: () -> Unit, onStartDictation: ((String) -> Unit) -> Unit) {
    var currentTab by remember { mutableStateOf("Chat") }
    val apiError by viewModel.apiError.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(apiError) {
        apiError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.clearApiError() }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF18181B)) {
                listOf("Chat" to Icons.Default.Chat, "Agente" to Icons.Default.SmartToy, "Espacio" to Icons.Default.Folder, "Historial" to Icons.Default.History, "Más" to Icons.Default.MoreHoriz).forEach { (label, icon) ->
                    NavigationBarItem(
                        selected = currentTab == label,
                        onClick = { currentTab = label },
                        icon = { Icon(icon, null) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFFFF5722), unselectedIconColor = Color(0xFFA1A1AA), indicatorColor = Color(0xFF242427))
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color(0xFF0D0D0D))) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(24.dp)) {
                    drawCircle(Color(0xFFFF5722), radius = size.minDimension / 2)
                }
                Spacer(Modifier.width(8.dp))
                Text("GroqApp", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            when (currentTab) {
                "Chat" -> ChatTab(viewModel, onPickFile, onStartDictation)
                "Agente" -> AgentTab(viewModel, onPickFile, onStartDictation)
                "Espacio" -> WorkspaceTab(viewModel)
                "Historial" -> HistoryTab(viewModel) { currentTab = "Chat" }
                "Más" -> MoreTab(viewModel)
            }
        }
    }
}

@Composable
fun ChatTab(viewModel: ZaiViewModel, onPickFile: () -> Unit, onStartDictation: ((String) -> Unit) -> Unit) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isGen by viewModel.isGenerating.collectAsStateWithLifecycle()
    val loadMsg by viewModel.loadingMessage.collectAsStateWithLifecycle()
    val pendingFiles by viewModel.pendingFiles.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isGen) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    Column(Modifier.fillMaxSize()) {
        if (pendingFiles.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pendingFiles.forEachIndexed { idx, file ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(file.name, fontSize = 10.sp, color = Color.White)
                        IconButton(onClick = { viewModel.removePendingFile(idx) }, modifier = Modifier.size(16.dp)) {
                            Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Chat, null, tint = Color(0xFFFF5722), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Conversación Directa", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(messages, key = { it.id }) { ChatBubble(it) }
                    if (isGen) { item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) { CircularProgressIndicator(Modifier.size(20.dp), color = Color(0xFFFF5722), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text(loadMsg, color = Color.White, fontSize = 12.sp) } } }
                }
            }
        }
        InputBar(
            value = text,
            onValue = { text = it },
            onSend = {
                if (text.isNotBlank() || pendingFiles.isNotEmpty()) {
                    viewModel.sendChatMessage(text); text = ""
                }
            },
            isGen = isGen,
            onAttach = onPickFile,
            onDictate = { onStartDictation { spoken -> text = if (text.isBlank()) spoken else "$text $spoken" } }
        )
    }
}

@Composable
fun AgentTab(viewModel: ZaiViewModel, onPickFile: () -> Unit, onStartDictation: ((String) -> Unit) -> Unit) {
    val messages by viewModel.agentMessages.collectAsStateWithLifecycle()
    val isGen by viewModel.isGenerating.collectAsStateWithLifecycle()
    val steps by viewModel.thinkingSteps.collectAsStateWithLifecycle()
    val pendingFiles by viewModel.pendingFiles.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var sandbox by remember { mutableStateOf(false) }
    var pythonCode by remember { mutableStateOf("") }
    var pythonResult by remember { mutableStateOf("") }

    LaunchedEffect(messages.size, isGen) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    LaunchedEffect(messages.lastOrNull()?.content) {
        val last = messages.lastOrNull()
        if (last != null && last.role == "assistant" && last.content.contains("```python")) {
            pythonCode = last.content.substringAfter("```python").substringBefore("```").trim()
            sandbox = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (pendingFiles.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pendingFiles.forEachIndexed { idx, file ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(file.name, fontSize = 10.sp, color = Color.White)
                        IconButton(onClick = { viewModel.removePendingFile(idx) }, modifier = Modifier.size(16.dp)) {
                            Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SmartToy, null, tint = Color(0xFFFF5722), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Agente Autónomo", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(messages, key = { it.id }) { ChatBubble(it) }
                    if (isGen) { item { ThinkingCard(steps) } }
                }
            }
        }
        InputBar(
            value = text,
            onValue = { text = it },
            onSend = {
                if (text.isNotBlank() || pendingFiles.isNotEmpty()) {
                    viewModel.sendAgentMessage(text); text = ""
                }
            },
            isGen = isGen,
            onAttach = onPickFile,
            onDictate = { onStartDictation { spoken -> text = if (text.isBlank()) spoken else "$text $spoken" } }
        )
    }

    if (sandbox) {
        Dialog(onDismissRequest = { sandbox = false }) {
            Card(Modifier.fillMaxWidth().heightIn(max = 500.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)), border = BorderStroke(1.dp, Color(0xFF52525B)), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Python Sandbox", fontWeight = FontWeight.Bold, color = Color(0xFFFF5722))
                    Spacer(Modifier.height(8.dp))
                    AndroidView(factory = { ctx ->
                        SandboxWebView(ctx).also {
                            it.loadPyodide()
                            it.execute(pythonCode) { res -> pythonResult = res }
                        }
                    }, modifier = Modifier.fillMaxWidth().height(350.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Resultado:", color = Color.White, fontWeight = FontWeight.Bold)
                    Box(Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0D0D0D)).padding(8.dp)) {
                        Text(pythonResult.ifBlank { "Ejecutando..." }, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun WorkspaceTab(viewModel: ZaiViewModel) {
    val files by viewModel.sandboxFiles.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Espacio de Trabajo", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFFF5722))
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(files, key = { it.name }) { file ->
                Card(Modifier.fillMaxWidth().clickable { selected = file.name }, colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B))) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.InsertDriveFile, null, tint = Color(0xFFFF5722))
                        Spacer(Modifier.width(12.dp))
                        Text(file.name, color = Color.White)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { viewModel.deleteSandboxFile(file.name); selected = null }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                    }
                }
            }
        }
        if (selected != null) {
            Button(onClick = {
                val uri = viewModel.getDownloadUri(selected!!)
                if (uri != null) {
                    val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/octet-stream").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(i)
                }
            }) { Text("Descargar $selected") }
        }
    }
}

@Composable
fun HistoryTab(viewModel: ZaiViewModel, onSelect: () -> Unit) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("Chat") }
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Historial", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFFF5722))
            Box {
                Canvas(Modifier.size(32.dp).clickable { expanded = true }) {
                    drawRect(Color.White, Offset.Zero, size.copy(width = size.width / 2, height = size.height))
                    drawRect(Color.Black, Offset(size.width / 2, 0f), size.copy(width = size.width / 2, height = size.height))
                    val path = Path().apply {
                        moveTo(size.width * 0.65f, size.height * 0.35f)
                        lineTo(size.width * 0.85f, size.height * 0.5f)
                        lineTo(size.width * 0.65f, size.height * 0.65f)
                    }
                    drawPath(path, Color.White)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Chat") }, onClick = { filter = "Chat"; expanded = false })
                    DropdownMenuItem(text = { Text("Agente") }, onClick = { filter = "Agente"; expanded = false })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        val filtered = sessions // Simplificación, luego añadiremos campo de tipo
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { session ->
                Card(Modifier.fillMaxWidth().clickable {
                    if (filter == "Chat") viewModel.selectChatSession(session.id) else viewModel.selectAgentSession(session.id)
                    onSelect()
                }, colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B))) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ChatBubbleOutline, null, tint = Color(0xFFFF5722))
                        Spacer(Modifier.width(12.dp))
                        Column { Text(session.title, fontWeight = FontWeight.Bold, color = Color.White); Text(session.model, fontSize = 11.sp, color = Color(0xFFA1A1AA)) }
                    }
                }
            }
        }
    }
}

@Composable
fun MoreTab(viewModel: ZaiViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Configuración", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFFF5722))
        Spacer(Modifier.height(16.dp))
        var prov by remember { mutableStateOf(viewModel.selectedProvider.value) }
        var url by remember { mutableStateOf(viewModel.baseUrl.value) }
        var model by remember { mutableStateOf(viewModel.selectedModel.value) }
        var key by remember { mutableStateOf(viewModel.apiKey.value) }
        val context = LocalContext.current

        Text("Proveedor", color = Color.White)
        Box {
            OutlinedTextField(prov, {}, readOnly = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White))
            DropdownMenu(expanded = true, onDismissRequest = {}) {
                viewModel.providers.keys.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { prov = it; viewModel.setProvider(it); url = viewModel.baseUrl.value }) }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(url, { url = it }, label = { Text("URL Base") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(model, { model = it }, label = { Text("Modelo") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(key, { key = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { viewModel.saveApiKey(key); viewModel.saveBaseUrl(url); viewModel.saveSelectedModel(model); Toast.makeText(context, "Guardado", Toast.LENGTH_SHORT).show() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))) { Text("Guardar") }
            Button(onClick = { /* Probar */ }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242427))) { Text("Probar") }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        if (!isUser) { Icon(Icons.Default.SmartToy, null, tint = Color(0xFFFF5722), modifier = Modifier.size(24.dp)); Spacer(Modifier.width(8.dp)) }
        Column(Modifier.fillMaxWidth(0.8f)) {
            Card(colors = CardDefaults.cardColors(containerColor = if (isUser) Color(0xFF242427) else Color(0xFF18181B)), border = BorderStroke(1.dp, Color(0xFF52525B))) {
                Text(message.content, modifier = Modifier.padding(12.dp), color = Color.White)
            }
        }
        if (isUser) { Spacer(Modifier.width(8.dp)); Icon(Icons.Default.Person, null, tint = Color(0xFFFF5722), modifier = Modifier.size(24.dp)) }
    }
}

@Composable
fun ThinkingCard(steps: List<String>) {
    Card(Modifier.fillMaxWidth().padding(4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)), border = BorderStroke(1.dp, Color(0xFF52525B))) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(16.dp), color = Color(0xFFFF5722), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Agente pensando...", color = Color(0xFFFF5722), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
            steps.takeLast(3).forEach { Text(it, color = Color.White, fontSize = 12.sp) }
        }
    }
}

@Composable
fun InputBar(value: String, onValue: (String) -> Unit, onSend: () -> Unit, isGen: Boolean, onAttach: () -> Unit, onDictate: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Color(0xFF18181B), border = BorderStroke(1.dp, Color(0xFF52525B))) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAttach) { Icon(Icons.Default.AttachFile, null, tint = Color(0xFFA1A1AA)) }
            TextField(value, onValue, Modifier.weight(1f), placeholder = { Text("Mensaje...") }, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
            IconButton(onClick = onDictate) { Icon(Icons.Default.Mic, null, tint = Color(0xFFFF5722)) }
            IconButton(onClick = onSend, enabled = !isGen && (value.isNotBlank() || true)) { Icon(Icons.Default.Send, null, tint = Color(0xFFFF5722)) }
        }
    }
}
