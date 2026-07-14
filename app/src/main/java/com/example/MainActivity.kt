package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.ChatMessage
import com.example.ui.SandboxWebView
import com.example.ui.ZaiViewModel
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        private var speechCallback: ((String) -> Unit)? = null
    }

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
            speechCallback?.invoke(spokenText)
        }
    }

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleFile(it) }
    }

    private fun handleFile(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val fileName = uri.lastPathSegment ?: "archivo_sin_nombre"
            val workspaceDir = File(filesDir, "workspace")
            if (!workspaceDir.exists()) workspaceDir.mkdirs()

            if (fileName.endsWith(".zip") || fileName.endsWith(".jar") || fileName.endsWith(".apk")) {
                val zis = ZipInputStream(inputStream)
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && !entry.name.contains("..") && !entry.name.startsWith("/")) {
                        val file = File(workspaceDir, entry.name)
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { fos -> zis.copyTo(fos) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                zis.close()
                Toast.makeText(this, "Archivo '$fileName' extraído en Espacio de Trabajo", Toast.LENGTH_LONG).show()
            } else {
                val content = inputStream.bufferedReader().readText()
                val file = File(workspaceDir, fileName)
                file.writeText(content)
                Toast.makeText(this, "Archivo '$fileName' guardado en Espacio de Trabajo", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al leer el archivo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen(
                    onStartDictation = { onTextSpoken ->
                        speechCallback = onTextSpoken
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Dicta tu mensaje...")
                            }
                            speechLauncher.launch(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this, "Dictado no soportado en tu dispositivo", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onPickFile = { filePicker.launch("*/*") }
                )
            }
        }
    }
}

enum class AppTab { CHAT, AGENT, WORKSPACE, HISTORY, MORE }
enum class MoreScreen { MENU, SETTINGS, MODELS, PROVIDERS, HELP, ABOUT, SUPPORT }

@Composable
fun MainScreen(
    onStartDictation: ((String) -> Unit) -> Unit,
    onPickFile: () -> Unit
) {
    val viewModel: ZaiViewModel = viewModel()
    var currentTab by remember { mutableStateOf(AppTab.CHAT) }
    val context = LocalContext.current
    val apiError by viewModel.apiError.collectAsStateWithLifecycle()

    LaunchedEffect(apiError) {
        apiError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearApiError()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF18181B), tonalElevation = 8.dp) {
                NavigationBarItem(
                    selected = currentTab == AppTab.CHAT,
                    onClick = { currentTab = AppTab.CHAT },
                    icon = { Icon(Icons.Default.Chat, contentDescription = null) },
                    label = { Text("Chat") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFFFF5722), unselectedIconColor = Color(0xFFA1A1AA), indicatorColor = Color(0xFF242427))
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.AGENT,
                    onClick = { currentTab = AppTab.AGENT },
                    icon = { Icon(Icons.Default.SmartToy, contentDescription = null) },
                    label = { Text("Agente") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFFFF5722), unselectedIconColor = Color(0xFFA1A1AA), indicatorColor = Color(0xFF242427))
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.WORKSPACE,
                    onClick = { currentTab = AppTab.WORKSPACE },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text("Espacio") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFFFF5722), unselectedIconColor = Color(0xFFA1A1AA), indicatorColor = Color(0xFF242427))
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.HISTORY,
                    onClick = { currentTab = AppTab.HISTORY },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("Historial") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFFFF5722), unselectedIconColor = Color(0xFFA1A1AA), indicatorColor = Color(0xFF242427))
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.MORE,
                    onClick = { currentTab = AppTab.MORE },
                    icon = { Icon(Icons.Default.MoreHoriz, contentDescription = null) },
                    label = { Text("Más") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFFFF5722), unselectedIconColor = Color(0xFFA1A1AA), indicatorColor = Color(0xFF242427))
                )
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding).background(Color(0xFF0D0D0D))) {
            when (currentTab) {
                AppTab.CHAT -> ChatTabScreen(viewModel, onStartDictation, onPickFile)
                AppTab.AGENT -> AgentTabScreen(viewModel, onStartDictation, onPickFile)
                AppTab.WORKSPACE -> WorkspaceTabScreen(viewModel)
                AppTab.HISTORY -> HistoryTabScreen(viewModel) { currentTab = AppTab.CHAT }
                AppTab.MORE -> MoreTabScreen(viewModel)
            }
        }
    }
}

// ─── Markdown optimizado ─────────────────────────────────
@Composable
fun MarkdownText(text: String) {
    val annotated = remember(text) {
        buildAnnotatedString {
            var i = 0
            while (i < text.length) {
                when {
                    text.startsWith("**", i) -> {
                        val end = text.indexOf("**", i + 2)
                        if (end != -1) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) { append(text.substring(i + 2, end)) }
                            i = end + 2
                        } else { append(text[i]); i++ }
                    }
                    text.startsWith("*", i) && i + 1 < text.length && text[i + 1] != '*' -> {
                        val end = text.indexOf("*", i + 1)
                        if (end != -1) {
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Color.White)) { append(text.substring(i + 1, end)) }
                            i = end + 1
                        } else { append(text[i]); i++ }
                    }
                    text.startsWith("`", i) -> {
                        val end = text.indexOf("`", i + 1)
                        if (end != -1) {
                            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFF242427), color = Color(0xFFFF5722))) { append(text.substring(i + 1, end)) }
                            i = end + 1
                        } else { append(text[i]); i++ }
                    }
                    else -> { append(text[i]); i++ }
                }
            }
        }
    }
    Text(annotated, fontSize = 14.sp)
}

// ─── Chat Directo ────────────────────────────────────────
@Composable
fun ChatTabScreen(
    viewModel: ZaiViewModel,
    onStartDictation: ((String) -> Unit) -> Unit,
    onPickFile: () -> Unit
) {
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    var textInput by remember { mutableStateOf("") }
    var showModelDropdown by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val models = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "gemma2-9b-it", "deepseek-r1-distill-llama-70b", "llama-3.2-90b-vision-preview")

    val lastMessageId = activeMessages.lastOrNull()?.id
    LaunchedEffect(lastMessageId, isGenerating) {
        if (activeMessages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(activeMessages.size - 1)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Surface(color = Color(0xFF18181B), border = BorderStroke(1.dp, Color(0xFF52525B)), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Chat Groq", fontWeight = FontWeight.Bold, color = Color.White)
                Box {
                    Button(onClick = { showModelDropdown = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242427), contentColor = Color(0xFFFF5722)), border = BorderStroke(1.dp, Color(0xFF52525B))) {
                        Text(selectedModel, fontSize = 11.sp)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = showModelDropdown, onDismissRequest = { showModelDropdown = false }) {
                        models.forEach { model -> DropdownMenuItem(text = { Text(model) }, onClick = { viewModel.saveSelectedModel(model); showModelDropdown = false }) }
                    }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            if (activeMessages.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Chat, null, tint = Color(0xFFFF5722), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Conversación Directa", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    Text("Pregunta directamente al modelo Groq sin demoras adicionales.", fontSize = 12.sp, color = Color(0xFFA1A1AA), textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(activeMessages, key = { it.id }) { message ->
                        ChatMessageBubble(message)
                    }
                    if (isGenerating) {
                        item(key = "loading") {
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Start) {
                                CircularProgressIndicator(Modifier.size(20.dp), color = Color(0xFFFF5722), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Groq respondiendo...", color = Color(0xFFA1A1AA), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        if (apiKey.isBlank()) Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) { Text("No has configurado una API Key. Ve a Más -> Configuración.", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 11.sp, modifier = Modifier.padding(6.dp), textAlign = TextAlign.Center) }

        ChatInputBar(
            value = textInput,
            onValueChange = { textInput = it },
            onSend = { if (textInput.isNotBlank()) { viewModel.sendMessage(textInput); textInput = "" } },
            isGenerating = isGenerating,
            onAttachFile = onPickFile,
            onStartDictation = {
                onStartDictation { spoken ->
                    if (spoken.isNotBlank()) {
                        textInput = if (textInput.isBlank()) spoken else "$textInput $spoken"
                    }
                }
            },
            placeholder = "Mensaje..."
        )
    }
}

// ─── Agente ──────────────────────────────────────────────
@Composable
fun AgentTabScreen(
    viewModel: ZaiViewModel,
    onStartDictation: ((String) -> Unit) -> Unit,
    onPickFile: () -> Unit
) {
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val thinkingSteps by viewModel.currentThinkingSteps.collectAsStateWithLifecycle()
    var textInput by remember { mutableStateOf("") }
    var showModelDropdown by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val models = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "gemma2-9b-it", "deepseek-r1-distill-llama-70b", "llama-3.2-90b-vision-preview")

    val lastMessageId = activeMessages.lastOrNull()?.id
    LaunchedEffect(lastMessageId, isGenerating) {
        if (activeMessages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(activeMessages.size - 1)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Surface(color = Color(0xFF18181B), border = BorderStroke(1.dp, Color(0xFF52525B)), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Agente Inteligente", fontWeight = FontWeight.Bold, color = Color.White)
                Box {
                    Button(onClick = { showModelDropdown = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242427), contentColor = Color(0xFFFF5722)), border = BorderStroke(1.dp, Color(0xFF52525B))) {
                        Text(selectedModel, fontSize = 11.sp)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = showModelDropdown, onDismissRequest = { showModelDropdown = false }) {
                        models.forEach { model -> DropdownMenuItem(text = { Text(model) }, onClick = { viewModel.saveSelectedModel(model); showModelDropdown = false }) }
                    }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            if (activeMessages.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SmartToy, null, tint = Color(0xFFFF5722), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Agente Autónomo", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    Text("Resuelve tareas complejas con Python real y manipulación de archivos.", fontSize = 12.sp, color = Color(0xFFA1A1AA), textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(activeMessages, key = { it.id }) { message ->
                        ChatMessageBubble(message)
                    }
                    if (isGenerating) {
                        item(key = "thinking") { ThinkingStepsLoader(thinkingSteps) }
                    }
                }
            }
        }
        if (apiKey.isBlank()) Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) { Text("No has configurado una API Key.", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 11.sp, modifier = Modifier.padding(6.dp), textAlign = TextAlign.Center) }

        ChatInputBar(
            value = textInput,
            onValueChange = { textInput = it },
            onSend = { if (textInput.isNotBlank()) { viewModel.sendAgentMessage(textInput); textInput = "" } },
            isGenerating = isGenerating,
            onAttachFile = onPickFile,
            onStartDictation = {
                onStartDictation { spoken ->
                    if (spoken.isNotBlank()) {
                        textInput = if (textInput.isBlank()) spoken else "$textInput $spoken"
                    }
                }
            },
            placeholder = "Ordena una tarea al agente..."
        )
    }
}

// ─── Workspace ───────────────────────────────────────────
@Composable
fun WorkspaceTabScreen(viewModel: ZaiViewModel) {
    val files by viewModel.sandboxFiles.collectAsStateWithLifecycle()
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var fileContent by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Espacio de Trabajo", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFFF5722))
        Text("Archivos disponibles para la IA", fontSize = 12.sp, color = Color(0xFFA1A1AA))
        Spacer(Modifier.height(16.dp))
        if (files.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No hay archivos. Adjunta un ZIP, APK o archivo desde el chat.", color = Color(0xFFA1A1AA), textAlign = TextAlign.Center) }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(files, key = { it.name }) { file ->
                    Card(Modifier.fillMaxWidth().clickable {
                        selectedFileName = file.name
                        fileContent = viewModel.readSandboxFileContent(file.name) ?: ""
                        editing = false
                    }, colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)), border = BorderStroke(1.dp, Color(0xFF52525B))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.InsertDriveFile, null, tint = Color(0xFFFF5722))
                                Spacer(Modifier.width(12.dp))
                                Text(file.name, color = Color.White)
                            }
                            IconButton(onClick = { viewModel.deleteSandboxFile(file.name); if (selectedFileName == file.name) selectedFileName = null }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                        }
                    }
                }
            }
        }
        if (selectedFileName != null) {
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)), border = BorderStroke(1.dp, Color(0xFF52525B))) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedFileName ?: "", fontWeight = FontWeight.Bold, color = Color(0xFFFF5722))
                        Button(onClick = { editing = !editing }) { Text(if (editing) "Vista" else "Editar") }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (editing) {
                        OutlinedTextField(value = fileContent, onValueChange = { fileContent = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                        Button(onClick = { viewModel.createSandboxFile(selectedFileName!!, fileContent); Toast.makeText(context, "Guardado", Toast.LENGTH_SHORT).show(); editing = false }, modifier = Modifier.align(Alignment.End)) { Text("Guardar") }
                    } else {
                        Box(Modifier.fillMaxWidth().heightIn(min = 200.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0D0D0D)).padding(12.dp)) { Text(fileContent.ifBlank { "Archivo vacío." }, color = Color.White, fontFamily = FontFamily.Monospace) }
                    }
                }
            }
        }
    }
}

// ─── Historial ───────────────────────────────────────────
@Composable
fun HistoryTabScreen(viewModel: ZaiViewModel, onSessionSelected: (Long) -> Unit) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    var editingSessionId by remember { mutableStateOf<Long?>(null) }
    var renameInput by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Historial de Sesiones", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFFF5722))
        Spacer(Modifier.height(16.dp))
        if (sessions.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No hay conversaciones guardadas.", color = Color(0xFFA1A1AA)) }
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sessions, key = { it.id }) { session ->
                val isActive = session.id == activeSessionId
                Card(Modifier.fillMaxWidth().clickable { viewModel.selectSession(session.id); onSessionSelected(session.id) }, colors = CardDefaults.cardColors(containerColor = if (isActive) Color(0xFF242427) else Color(0xFF18181B)), border = BorderStroke(1.dp, if (isActive) Color(0xFFFF5722) else Color(0xFF52525B))) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.ChatBubbleOutline, null, tint = if (isActive) Color(0xFFFF5722) else Color(0xFFA1A1AA)); Spacer(Modifier.width(12.dp)); Column { Text(session.title, fontWeight = FontWeight.Bold, color = Color.White); Text("Modelo: ${session.model}", fontSize = 11.sp, color = Color(0xFFA1A1AA)) } }
                        Row { IconButton(onClick = { editingSessionId = session.id; renameInput = session.title }) { Icon(Icons.Default.Edit, null, tint = Color(0xFFA1A1AA)) }; IconButton(onClick = { viewModel.deleteSession(session.id) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) } }
                    }
                }
            }
        }
    }
    if (editingSessionId != null) {
        Dialog(onDismissRequest = { editingSessionId = null }) {
            Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)), border = BorderStroke(1.dp, Color(0xFF52525B)), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Renombrar Sesión", fontWeight = FontWeight.Bold, color = Color(0xFFFF5722))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = renameInput, onValueChange = { renameInput = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editingSessionId = null }) { Text("Cancelar") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { editingSessionId?.let { id -> if (renameInput.isNotBlank()) { viewModel.renameSession(id, renameInput); editingSessionId = null } } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))) { Text("Guardar", color = Color.White) }
                    }
                }
            }
        }
    }
}

// ─── Más ─────────────────────────────────────────────────
@Composable
fun MoreTabScreen(viewModel: ZaiViewModel) {
    var subScreen by remember { mutableStateOf(MoreScreen.MENU) }
    AnimatedContent(targetState = subScreen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "SubNav") { screen ->
        when (screen) {
            MoreScreen.MENU -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("GroqApp Suite", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFFF5722))
                Text("Herramientas, guías y configuraciones", fontSize = 12.sp, color = Color(0xFFA1A1AA), modifier = Modifier.padding(bottom = 20.dp))
                MoreMenuItem("Configuración", "URL Base, Claves API", Icons.Default.Settings) { subScreen = MoreScreen.SETTINGS }
                MoreMenuItem("Modelos Soportados", "Llama, Gemma, DeepSeek", Icons.Default.Memory) { subScreen = MoreScreen.MODELS }
                MoreMenuItem("Proveedores", "Groq, OpenAI, Ollama", Icons.Default.Cloud) { subScreen = MoreScreen.PROVIDERS }
                MoreMenuItem("Ayuda & Sintaxis", "Guía del sandbox y Markdown", Icons.Default.Help) { subScreen = MoreScreen.HELP }
                MoreMenuItem("Acerca de", "Versión y autores", Icons.Default.Info) { subScreen = MoreScreen.ABOUT }
                MoreMenuItem("Soporte Técnico", "Contacto y reporte de bugs", Icons.Default.Email) { subScreen = MoreScreen.SUPPORT }
            }
            MoreScreen.SETTINGS -> SubScreen("Configuración", onBack = { subScreen = MoreScreen.MENU }) { SettingsTabScreen(viewModel) }
            MoreScreen.MODELS -> SubScreen("Modelos", onBack = { subScreen = MoreScreen.MENU }) { ModelsInfoScreen() }
            MoreScreen.PROVIDERS -> SubScreen("Proveedores", onBack = { subScreen = MoreScreen.MENU }) { ProvidersScreen() }
            MoreScreen.HELP -> SubScreen("Ayuda", onBack = { subScreen = MoreScreen.MENU }) { HelpScreen() }
            MoreScreen.ABOUT -> SubScreen("Acerca de", onBack = { subScreen = MoreScreen.MENU }) { AboutTabScreen() }
            MoreScreen.SUPPORT -> SubScreen("Soporte", onBack = { subScreen = MoreScreen.MENU }) { SupportScreen() }
        }
    }
}

@Composable
fun MoreMenuItem(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)), border = BorderStroke(1.dp, Color(0xFF52525B)), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Color(0xFFFF5722), modifier = Modifier.size(24.dp)); Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, color = Color.White); Text(subtitle, fontSize = 11.sp, color = Color(0xFFA1A1AA)) }; Icon(Icons.Default.ArrowRight, null, tint = Color(0xFFA1A1AA), modifier = Modifier.size(20.dp)) }
    }
}

@Composable
fun SubScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }; Spacer(Modifier.width(8.dp)); Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val formattedTime = remember(message.timestamp) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)) }
    var showSandbox by remember { mutableStateOf(false) }
    var pythonCode by remember { mutableStateOf("") }
    var sandboxOutput by remember { mutableStateOf("") }

    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
        if (!isUser) { Box(Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF18181B)), contentAlignment = Alignment.Center) { Icon(Icons.Default.SmartToy, null, tint = Color(0xFFFF5722), modifier = Modifier.size(18.dp)) }; Spacer(Modifier.width(8.dp)) }
        Column(Modifier.fillMaxWidth(if (isUser) 0.85f else 0.78f), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            Card(colors = CardDefaults.cardColors(containerColor = if (isUser) Color(0xFF242427) else Color(0xFF18181B)), border = BorderStroke(1.dp, Color(0xFF52525B)), shape = RoundedCornerShape(16.dp).let { if (isUser) RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp) else RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp) }) {
                Column(Modifier.padding(12.dp)) {
                    MarkdownText(message.content)
                    
                    // Botón para ejecutar código Python si existe un bloque ```python
                    if (!isUser && message.content.contains("```python")) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                // Extraer el primer bloque de código Python
                                val start = message.content.indexOf("```python")
                                if (start != -1) {
                                    val end = message.content.indexOf("```", start + 9)
                                    if (end != -1) {
                                        pythonCode = message.content.substring(start + 9, end).trim()
                                        showSandbox = true
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Ejecutar en Sandbox")
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)) { Text(if (isUser) "Tú" else "Groq", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (isUser) Color(0xFFFF5722) else Color(0xFFA1A1AA)); Spacer(Modifier.width(4.dp)); Text("• $formattedTime", fontSize = 9.sp, color = Color(0xFFA1A1AA)) }
        }
        if (isUser) { Spacer(Modifier.width(8.dp)); Box(Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFFF5722).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color(0xFFFF5722), modifier = Modifier.size(18.dp)) } }
    }

    // Diálogo del Sandbox Python
    if (showSandbox) {
        Dialog(onDismissRequest = { showSandbox = false }) {
            Card(Modifier.fillMaxWidth().heightIn(max = 500.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)), border = BorderStroke(1.dp, Color(0xFF52525B)), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Sandbox Python", fontWeight = FontWeight.Bold, color = Color(0xFFFF5722))
                    Spacer(Modifier.height(8.dp))
                    AndroidView(
                        factory = { ctx ->
                            SandboxWebView(ctx).also {
                                it.loadPyodide()
                                it.execute(pythonCode) { result ->
                                    sandboxOutput = result
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(350.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Salida:", color = Color.White, fontWeight = FontWeight.Bold)
                    Box(Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0D0D0D)).padding(8.dp)) {
                        Text(sandboxOutput.ifBlank { "Ejecutando..." }, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isGenerating: Boolean,
    onAttachFile: () -> Unit,
    onStartDictation: () -> Unit,
    placeholder: String
) {
    Surface(color = Color(0xFF18181B), border = BorderStroke(1.dp, Color(0xFF52525B)), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAttachFile, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.AttachFile, null, tint = Color(0xFFA1A1AA)) }
            IconButton(onClick = onStartDictation, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Mic, null, tint = Color(0xFFFF5722)) }
            TextField(value = value, onValueChange = onValueChange, placeholder = { Text(placeholder, color = Color(0xFFA1A1AA), fontSize = 14.sp) }, modifier = Modifier.weight(1f), maxLines = 4, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
            IconButton(onClick = onSend, enabled = !isGenerating && value.isNotBlank(), modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(if (value.isNotBlank() && !isGenerating) Color(0xFFFF5722) else Color(0xFF242427))) { Icon(Icons.Default.Send, null, tint = if (value.isNotBlank() && !isGenerating) Color.White else Color(0xFFA1A1AA), modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
fun ThinkingStepsLoader(steps: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)), border = BorderStroke(1.dp, Color(0xFF52525B)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 8.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), color = Color(0xFFFF5722), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Agente Pensando...", fontWeight = FontWeight.Bold, color = Color(0xFFFF5722)) }
            Spacer(Modifier.height(8.dp))
            steps.takeLast(3).forEachIndexed { index, step -> Row(Modifier.padding(start = 4.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (index == steps.lastIndex) Icons.Default.HourglassEmpty else Icons.Default.CheckCircle, null, tint = if (index == steps.lastIndex) Color(0xFFFF5722) else Color.Green, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(6.dp)); Text(step, fontSize = 11.sp, color = Color.White) } }
        }
    }
}

// ─── Settings, Models, Help, etc. (sin cambios) ──────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTabScreen(viewModel: ZaiViewModel) {
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    var keyInput by remember { mutableStateOf(apiKey) }
    var urlInput by remember { mutableStateOf(baseUrl) }
    var modelSelection by remember { mutableStateOf(selectedModel) }
    var showModelDropdown by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val models = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "gemma2-9b-it", "deepseek-r1-distill-llama-70b", "llama-3.2-90b-vision-preview")

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Configuración Avanzada", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFFF5722)) }
        item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B))) { Column(Modifier.padding(16.dp)) { Text("URL Base", fontWeight = FontWeight.Bold, color = Color.White); Spacer(Modifier.height(8.dp)); OutlinedTextField(value = urlInput, onValueChange = { urlInput = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)) } } }
        item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B))) { Column(Modifier.padding(16.dp)) { Text("API Key", fontWeight = FontWeight.Bold, color = Color.White); Spacer(Modifier.height(8.dp)); OutlinedTextField(value = keyInput, onValueChange = { keyInput = it }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)) } } }
        item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B))) { Column(Modifier.padding(16.dp)) { Text("Modelo", fontWeight = FontWeight.Bold, color = Color.White); Spacer(Modifier.height(8.dp)); Box { OutlinedTextField(value = modelSelection, onValueChange = {}, readOnly = true, trailingIcon = { IconButton(onClick = { showModelDropdown = true }) { Icon(Icons.Default.ArrowDropDown, null, tint = Color.White) } }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)); DropdownMenu(expanded = showModelDropdown, onDismissRequest = { showModelDropdown = false }) { models.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { modelSelection = it; showModelDropdown = false }) } } } } } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedButton(onClick = { viewModel.testConnection(urlInput, keyInput, modelSelection) { success, msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() } }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color(0xFFFF5722))) { Text("Probar") }; Button(onClick = { viewModel.saveBaseUrl(urlInput); viewModel.saveApiKey(keyInput); viewModel.saveSelectedModel(modelSelection); Toast.makeText(context, "Guardado", Toast.LENGTH_SHORT).show() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))) { Text("Guardar", color = Color.White) } } }
    }
}

@Composable fun ModelsInfoScreen() = LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item { Text("Modelos Soportados", fontWeight = FontWeight.Bold, color = Color(0xFFFF5722), fontSize = 16.sp) }
    item { ModelInfoCard("llama-3.3-70b-versatile", "Meta, lógica compleja") }
    item { ModelInfoCard("llama-3.1-8b-instant", "Meta, rápido") }
    item { ModelInfoCard("gemma2-9b-it", "Google, español natural") }
    item { ModelInfoCard("deepseek-r1-distill-llama-70b", "DeepSeek, razonamiento") }
    item { ModelInfoCard("llama-3.2-90b-vision-preview", "Meta, visión") }
}

@Composable fun ModelInfoCard(name: String, desc: String) = Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B))) {
    Column(Modifier.padding(14.dp)) { Text(name, fontWeight = FontWeight.Bold, color = Color(0xFFFF5722), fontFamily = FontFamily.Monospace); Text(desc, color = Color.White) }
}

@Composable fun ProvidersScreen() = Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("Proveedores", fontWeight = FontWeight.Bold, color = Color(0xFFFF5722), fontSize = 16.sp)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B))) { Column(Modifier.padding(16.dp)) { Text("Groq Cloud", fontWeight = FontWeight.Bold, color = Color.White); Text("https://api.groq.com/openai/v1/", color = Color(0xFFFF5722), fontFamily = FontFamily.Monospace, fontSize = 11.sp) } }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B))) { Column(Modifier.padding(16.dp)) { Text("OpenAI", fontWeight = FontWeight.Bold, color = Color.White); Text("https://api.openai.com/v1/", color = Color(0xFFFF5722), fontFamily = FontFamily.Monospace, fontSize = 11.sp) } }
}

@Composable fun HelpScreen() = LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item { Text("Ayuda", fontWeight = FontWeight.Bold, color = Color(0xFFFF5722), fontSize = 16.sp) }
    item { HelpCard("Markdown", "**negrita** *cursiva* `codigo`") }
    item { HelpCard("Python Sandbox", "La IA puede ejecutar código Python real para manipular archivos.") }
}

@Composable fun HelpCard(title: String, desc: String) = Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B))) { Column(Modifier.padding(14.dp)) { Text(title, fontWeight = FontWeight.Bold, color = Color(0xFFFF5722)); Text(desc, color = Color.White) } }

@Composable fun SupportScreen() = Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("Soporte", fontWeight = FontWeight.Bold, color = Color(0xFFFF5722), fontSize = 16.sp)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B))) { Column(Modifier.padding(16.dp)) { Text("Contacto", fontWeight = FontWeight.Bold, color = Color.White); Text("manucarrasquel66@gmail.com", color = Color(0xFFFF5722), fontWeight = FontWeight.Bold) } }
}

@Composable fun AboutTabScreen() = Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Icon(Icons.Default.Bolt, null, tint = Color(0xFFFF5722), modifier = Modifier.size(96.dp))
    Spacer(Modifier.height(16.dp))
    Text("GroqApp", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF5722))
    Text("Sandbox Python real • LPU v2", color = Color(0xFFA1A1AA))
    Spacer(Modifier.height(24.dp))
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B))) { Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("Herramientas reales para IA sin límites", textAlign = TextAlign.Center, color = Color.White); Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color(0xFF52525B)); Spacer(Modifier.height(16.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) { Text("Android", color = Color(0xFFFF5722)); Text("Kotlin", color = Color(0xFFFF5722)); Text("Compose", color = Color(0xFFFF5722)) } } }
    Spacer(Modifier.height(16.dp))
    Text("Versión 3.0.1", color = Color(0xFFA1A1AA))
}
