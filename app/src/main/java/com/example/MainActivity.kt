package com.example

import android.content.Intent
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.ChatMessage
import com.example.data.database.ChatSession
import com.example.ui.FileItem
import com.example.ui.ZaiViewModel
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*

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
                    }
                )
            }
        }
    }
}

enum class AppTab {
    CHAT, AGENT, FILES, HISTORY, MORE
}

enum class MoreScreen {
    MENU, SETTINGS, MODELS, PROVIDERS, HELP, ABOUT, SUPPORT
}

@Composable
fun MainScreen(onStartDictation: ((String) -> Unit) -> Unit) {
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
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF18181B),
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == AppTab.CHAT,
                    onClick = { currentTab = AppTab.CHAT },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
                    label = { Text("Chat") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = Color(0xFFA1A1AA),
                        indicatorColor = Color(0xFF242427)
                    ),
                    modifier = Modifier.testTag("nav_chat")
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.AGENT,
                    onClick = { currentTab = AppTab.AGENT },
                    icon = { Icon(Icons.Default.SmartToy, contentDescription = "Agente") },
                    label = { Text("Agente") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = Color(0xFFA1A1AA),
                        indicatorColor = Color(0xFF242427)
                    ),
                    modifier = Modifier.testTag("nav_agent")
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.FILES,
                    onClick = {
                        currentTab = AppTab.FILES
                        viewModel.loadSandboxFiles()
                    },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Archivos") },
                    label = { Text("Archivos") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = Color(0xFFA1A1AA),
                        indicatorColor = Color(0xFF242427)
                    ),
                    modifier = Modifier.testTag("nav_files")
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.HISTORY,
                    onClick = { currentTab = AppTab.HISTORY },
                    icon = { Icon(Icons.Default.History, contentDescription = "Historial") },
                    label = { Text("Historial") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = Color(0xFFA1A1AA),
                        indicatorColor = Color(0xFF242427)
                    ),
                    modifier = Modifier.testTag("nav_history")
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.MORE,
                    onClick = { currentTab = AppTab.MORE },
                    icon = { Icon(Icons.Default.MoreHoriz, contentDescription = "Más") },
                    label = { Text("Más") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = Color(0xFFA1A1AA),
                        indicatorColor = Color(0xFF242427)
                    ),
                    modifier = Modifier.testTag("nav_more")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0D0D0D))
        ) {
            when (currentTab) {
                AppTab.CHAT -> ChatTabScreen(viewModel, onNavigateToSandbox = {
                    currentTab = AppTab.FILES
                    viewModel.loadSandboxFiles()
                }, onStartDictation = onStartDictation)

                AppTab.AGENT -> AgentTabScreen(viewModel, onNavigateToSandbox = {
                    currentTab = AppTab.FILES
                    viewModel.loadSandboxFiles()
                }, onStartDictation = onStartDictation)

                AppTab.FILES -> FilesTabScreen(viewModel)
                AppTab.HISTORY -> HistoryTabScreen(viewModel, onSessionSelected = {
                    currentTab = AppTab.CHAT
                })

                AppTab.MORE -> MoreTabScreen(viewModel)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// 1. CHAT DIRECTO
// ─────────────────────────────────────────────────────────
@Composable
fun ChatTabScreen(
    viewModel: ZaiViewModel,
    onNavigateToSandbox: () -> Unit,
    onStartDictation: ((String) -> Unit) -> Unit
) {
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()

    var textInput by remember { mutableStateOf("") }
    var showModelDropdown by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val models = listOf(
        "mixtral-8x7b-32768",
        "llama-3.3-70b-versatile",
        "llama-3.1-8b-instant",
        "gemma2-9b-it"
    )

    LaunchedEffect(activeMessages.size, isGenerating) {
        if (activeMessages.isNotEmpty()) {
            listState.animateScrollToItem(activeMessages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = Color(0xFF18181B),
            border = BorderStroke(1.dp, Color(0xFF52525B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("GroqApp Chat", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Inferencia ultra-rápida LPU", fontSize = 11.sp, color = Color(0xFFA1A1AA))
                }

                Box {
                    Button(
                        onClick = { showModelDropdown = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242427), contentColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, Color(0xFF52525B))
                    ) {
                        Text(selectedModel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
                    }

                    DropdownMenu(
                        expanded = showModelDropdown,
                        onDismissRequest = { showModelDropdown = false }
                    ) {
                        models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = {
                                    viewModel.saveSelectedModel(model)
                                    showModelDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (activeMessages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Conversación Directa", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Pregunta directamente al modelo Groq sin demoras adicionales.", fontSize = 12.sp, color = Color(0xFFA1A1AA), textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(activeMessages) { message ->
                        ChatMessageBubble(message)
                    }

                    if (isGenerating) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF18181B)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Groq está respondiendo...", fontSize = 11.sp, color = Color(0xFFA1A1AA))
                            }
                        }
                    }
                }
            }
        }

        if (apiKey.isBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No has configurado una API Key. Ve a Más -> Configuración.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(6.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        ChatInputBar(
            value = textInput,
            onValueChange = { textInput = it },
            onSendClick = {
                if (textInput.isNotBlank()) {
                    viewModel.sendMessage(textInput)
                    textInput = ""
                }
            },
            isGenerating = isGenerating,
            onNavigateToSandbox = onNavigateToSandbox,
            onStartDictation = {
                onStartDictation { spoken ->
                    if (spoken.isNotBlank()) {
                        textInput = if (textInput.isBlank()) spoken else "$textInput $spoken"
                    }
                }
            },
            placeholderText = "Haz una consulta al chat..."
        )
    }
}

// ─────────────────────────────────────────────────────────
// 2. AGENTE AUTÓNOMO
// ─────────────────────────────────────────────────────────
@Composable
fun AgentTabScreen(
    viewModel: ZaiViewModel,
    onNavigateToSandbox: () -> Unit,
    onStartDictation: ((String) -> Unit) -> Unit
) {
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val currentThinkingSteps by viewModel.currentThinkingSteps.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()

    var textInput by remember { mutableStateOf("") }
    var showModelDropdown by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val models = listOf(
        "mixtral-8x7b-32768",
        "llama-3.3-70b-versatile",
        "llama-3.1-8b-instant",
        "gemma2-9b-it"
    )

    LaunchedEffect(activeMessages.size, isGenerating) {
        if (activeMessages.isNotEmpty()) {
            listState.animateScrollToItem(activeMessages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = Color(0xFF18181B),
            border = BorderStroke(1.dp, Color(0xFF52525B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Agente Inteligente", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Sandbox local y web integrados", fontSize = 11.sp, color = Color(0xFFA1A1AA))
                    }
                }

                Box {
                    Button(
                        onClick = { showModelDropdown = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242427), contentColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, Color(0xFF52525B))
                    ) {
                        Text(selectedModel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
                    }

                    DropdownMenu(
                        expanded = showModelDropdown,
                        onDismissRequest = { showModelDropdown = false }
                    ) {
                        models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = {
                                    viewModel.saveSelectedModel(model)
                                    showModelDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (activeMessages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Agente Autónomo", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Resuelve tareas complejas con pasos detallados de pensamiento cognitivo.", fontSize = 12.sp, color = Color(0xFFA1A1AA), textAlign = TextAlign.Center)

                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Sugerencias rápidas:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))

                    val suggestions = listOf(
                        "Crear el archivo 'notas.txt' con apuntes de desarrollo.",
                        "¿Quién es Groq y qué ventajas tiene su API?",
                        "Lee el archivo local 'notas.txt' y resume su contenido."
                    )

                    suggestions.forEach { suggestion ->
                        Card(
                            onClick = { textInput = suggestion },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
                            border = BorderStroke(1.dp, Color(0xFF52525B)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(suggestion, fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(activeMessages) { message ->
                        ChatMessageBubble(message)
                    }

                    if (isGenerating) {
                        item {
                            ThinkingStepsLoader(currentThinkingSteps)
                        }
                    }
                }
            }
        }

        if (apiKey.isBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No has configurado una API Key. Ve a Más -> Configuración.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(6.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        ChatInputBar(
            value = textInput,
            onValueChange = { textInput = it },
            onSendClick = {
                if (textInput.isNotBlank()) {
                    viewModel.sendAgentMessage(textInput)
                    textInput = ""
                }
            },
            isGenerating = isGenerating,
            onNavigateToSandbox = onNavigateToSandbox,
            onStartDictation = {
                onStartDictation { spoken ->
                    if (spoken.isNotBlank()) {
                        textInput = if (textInput.isBlank()) spoken else "$textInput $spoken"
                    }
                }
            },
            placeholderText = "Ordena una tarea al agente..."
        )
    }
}

// ─────────────────────────────────────────────────────────
// 3. ARCHIVOS (SANDBOX)
// ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesTabScreen(viewModel: ZaiViewModel) {
    val files by viewModel.sandboxFiles.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedFileItem by remember { mutableStateOf<FileItem?>(null) }
    var fileContentToShow by remember { mutableStateOf<String?>(null) }

    var fileNameInput by remember { mutableStateOf("") }
    var fileContentInput by remember { mutableStateOf("") }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Explorador de Archivos", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Documentos legibles en el Sandbox por el Agente", fontSize = 12.sp, color = Color(0xFFA1A1AA))
            }

            Button(
                onClick = {
                    fileNameInput = ""
                    fileContentInput = ""
                    showCreateDialog = true
                },
                modifier = Modifier.testTag("create_file_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Crear")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
            border = BorderStroke(1.dp, Color(0xFF52525B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Cualquier archivo de texto aquí guardado se inyectará en el contexto del Agente automáticamente si lo mencionas por su nombre en tu prompt.",
                    fontSize = 11.sp,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (files.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No hay archivos en el sandbox.", color = Color(0xFFA1A1AA), fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files) { file ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val content = viewModel.readSandboxFileContent(file.name)
                                fileContentToShow = content ?: "Error al leer archivo."
                                selectedFileItem = file
                            }
                            .testTag("file_item_${file.name}"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
                        border = BorderStroke(1.dp, Color(0xFF52525B))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(file.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                    Text("${file.sizeBytes} bytes • modificado hace poco", fontSize = 11.sp, color = Color(0xFFA1A1AA))
                                }
                            }

                            IconButton(
                                onClick = {
                                    viewModel.deleteSandboxFile(file.name)
                                    Toast.makeText(context, "Archivo eliminado", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
                border = BorderStroke(1.dp, Color(0xFF52525B)),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Crear Nuevo Archivo", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))

                    OutlinedTextField(
                        value = fileNameInput,
                        onValueChange = { fileNameInput = it },
                        label = { Text("Nombre (ej: notas.txt)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("dialog_file_name"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFF52525B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = fileContentInput,
                        onValueChange = { fileContentInput = it },
                        label = { Text("Contenido") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth().testTag("dialog_file_content"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFF52525B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showCreateDialog = false }) { Text("Cancelar") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (fileNameInput.isBlank()) {
                                    Toast.makeText(context, "Nombre requerido", Toast.LENGTH_SHORT).show()
                                } else {
                                    val finalName = if (!fileNameInput.contains(".")) "$fileNameInput.txt" else fileNameInput
                                    if (viewModel.createSandboxFile(finalName, fileContentInput)) {
                                        Toast.makeText(context, "Archivo guardado", Toast.LENGTH_SHORT).show()
                                        showCreateDialog = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("dialog_create_submit")
                        ) {
                            Text("Crear", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (selectedFileItem != null && fileContentToShow != null) {
        Dialog(onDismissRequest = { selectedFileItem = null; fileContentToShow = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
                border = BorderStroke(1.dp, Color(0xFF52525B)),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(selectedFileItem?.name ?: "Archivo", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))

                    Surface(
                        color = Color(0xFF0D0D0D),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF52525B)),
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false).padding(bottom = 12.dp)
                    ) {
                        Box(modifier = Modifier.padding(12.dp)) {
                            Text(fileContentToShow ?: "", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                        }
                    }

                    Button(
                        onClick = { selectedFileItem = null; fileContentToShow = null },
                        modifier = Modifier.align(Alignment.End).testTag("view_dialog_close")
                    ) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// 4. HISTORIAL DE SESIONES
// ─────────────────────────────────────────────────────────
@Composable
fun HistoryTabScreen(viewModel: ZaiViewModel, onSessionSelected: (Long) -> Unit) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()

    var editingSessionId by remember { mutableStateOf<Long?>(null) }
    var renameInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Historial de Sesiones", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Conversaciones registradas en la base de datos Room", fontSize = 12.sp, color = Color(0xFFA1A1AA))
            }

            Button(
                onClick = {
                    viewModel.startNewSession()
                    onSessionSelected(0)
                },
                modifier = Modifier.testTag("new_chat_session_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Nuevo")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (sessions.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No hay conversaciones anteriores guardadas.", color = Color(0xFFA1A1AA), fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions) { session ->
                    val isActive = session.id == activeSessionId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectSession(session.id)
                                onSessionSelected(session.id)
                            }
                            .testTag("session_item_${session.id}"),
                        colors = CardDefaults.cardColors(containerColor = if (isActive) Color(0xFF242427) else Color(0xFF18181B)),
                        border = BorderStroke(1.dp, if (isActive) MaterialTheme.colorScheme.primary else Color(0xFF52525B))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = if (isActive) MaterialTheme.colorScheme.primary else Color(0xFFA1A1AA))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(session.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                    Text("Modelo: ${session.model}", fontSize = 11.sp, color = Color(0xFFA1A1AA))
                                }
                            }

                            Row {
                                IconButton(onClick = { editingSessionId = session.id; renameInput = session.title }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Color(0xFFA1A1AA))
                                }
                                IconButton(onClick = { viewModel.deleteSession(session.id); Toast.makeText(context, "Sesión eliminada", Toast.LENGTH_SHORT).show() }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingSessionId != null) {
        Dialog(onDismissRequest = { editingSessionId = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
                border = BorderStroke(1.dp, Color(0xFF52525B)),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Renombrar Sesión", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))

                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        label = { Text("Nuevo Título") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("dialog_rename_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFF52525B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editingSessionId = null }) { Text("Cancelar") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                editingSessionId?.let { id ->
                                    if (renameInput.isNotBlank()) {
                                        viewModel.renameSession(id, renameInput)
                                        editingSessionId = null
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("dialog_rename_submit")
                        ) {
                            Text("Guardar", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// 5. MÁS (SUB-NAVEGACIÓN DE SERVICIOS)
// ─────────────────────────────────────────────────────────
@Composable
fun MoreTabScreen(viewModel: ZaiViewModel) {
    var subScreen by remember { mutableStateOf(MoreScreen.MENU) }

    AnimatedContent(
        targetState = subScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "SubNavigation"
    ) { screen ->
        when (screen) {
            MoreScreen.MENU -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text("GroqApp Suite", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Explora herramientas, guías y configuraciones", fontSize = 12.sp, color = Color(0xFFA1A1AA), modifier = Modifier.padding(bottom = 20.dp))

                    MoreMenuItem("Configuración", "URL Base, Claves API de Groq y modo de visualización", Icons.Default.Settings, onClick = { subScreen = MoreScreen.SETTINGS })
                    MoreMenuItem("Modelos Soportados", "Modelos de Meta Llama, Google Gemma y Mistral Mixtral", Icons.Default.Memory, onClick = { subScreen = MoreScreen.MODELS })
                    MoreMenuItem("Proveedores", "Ajustes de compatibilidad de API OpenAI y local Ollama", Icons.Default.Cloud, onClick = { subScreen = MoreScreen.PROVIDERS })
                    MoreMenuItem("Ayuda & Sintaxis", "Guía del sandbox, comandos y renderizado de código", Icons.Default.Help, onClick = { subScreen = MoreScreen.HELP })
                    MoreMenuItem("Acerca de", "Detalles del sistema, versión y autores", Icons.Default.Info, onClick = { subScreen = MoreScreen.ABOUT })
                    MoreMenuItem("Soporte Técnico", "Contacto de soporte por correo y reporte de problemas", Icons.Default.Email, onClick = { subScreen = MoreScreen.SUPPORT })
                }
            }
            MoreScreen.SETTINGS -> SubScreenContainer("Configuración", onBack = { subScreen = MoreScreen.MENU }) { SettingsTabScreen(viewModel) }
            MoreScreen.MODELS -> SubScreenContainer("Modelos Soportados", onBack = { subScreen = MoreScreen.MENU }) { ModelsInfoScreen() }
            MoreScreen.PROVIDERS -> SubScreenContainer("Proveedores", onBack = { subScreen = MoreScreen.MENU }) { ProvidersScreen() }
            MoreScreen.HELP -> SubScreenContainer("Ayuda", onBack = { subScreen = MoreScreen.MENU }) { HelpScreen() }
            MoreScreen.ABOUT -> SubScreenContainer("Acerca de", onBack = { subScreen = MoreScreen.MENU }) { AboutTabScreen() }
            MoreScreen.SUPPORT -> SubScreenContainer("Soporte Técnico", onBack = { subScreen = MoreScreen.MENU }) { SupportScreen() }
        }
    }
}

@Composable
fun MoreMenuItem(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
        border = BorderStroke(1.dp, Color(0xFF52525B)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                Text(subtitle, fontSize = 11.sp, color = Color(0xFFA1A1AA))
            }
            Icon(Icons.Default.ArrowRight, contentDescription = null, tint = Color(0xFFA1A1AA), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun SubScreenContainer(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

// ─────────────────────────────────────────────────────────
// COMPONENTES AUXILIARES
// ─────────────────────────────────────────────────────────
@Composable
fun ChatMessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    var showThinkingSteps by remember { mutableStateOf(false) }

    val formattedTime = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF18181B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SmartToy, contentDescription = "Agente", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.78f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isUser) Color(0xFF242427) else Color(0xFF18181B)),
                border = BorderStroke(1.dp, Color(0xFF52525B)),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isUser) 16.dp else 0.dp, bottomEnd = if (isUser) 0.dp else 16.dp),
                modifier = Modifier.testTag(if (isUser) "user_message_bubble" else "assistant_message_bubble")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (!isUser && !message.thinkingSteps.isNullOrBlank()) {
                        Surface(
                            color = Color(0xFF0F0F10),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF52525B)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { showThinkingSteps = !showThinkingSteps }
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pensamiento Autónomo", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Icon(if (showThinkingSteps) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                }

                                AnimatedVisibility(visible = showThinkingSteps) {
                                    Column(modifier = Modifier.padding(top = 8.dp)) {
                                        message.thinkingSteps.split("\n").forEach { step ->
                                            if (step.isNotBlank()) {
                                                Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                                                    Text("• ", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                                                    Text(step, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFA1A1AA))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    FormattedMessageText(message.content)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)) {
                Text(if (isUser) "Tú" else "Groq", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (isUser) MaterialTheme.colorScheme.primary else Color(0xFFA1A1AA))
                Spacer(modifier = Modifier.width(4.dp))
                Text("• $formattedTime", fontSize = 9.sp, color = Color(0xFFA1A1AA))
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = "Tú", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun FormattedMessageText(text: String) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val blocks = remember(text) {
        val parts = mutableListOf<MessageBlock>()
        var currentIndex = 0
        while (currentIndex < text.length) {
            val startCode = text.indexOf("```", currentIndex)
            if (startCode == -1) {
                parts.add(MessageBlock.Text(text.substring(currentIndex)))
                break
            } else {
                if (startCode > currentIndex) {
                    parts.add(MessageBlock.Text(text.substring(currentIndex, startCode)))
                }
                val endCode = text.indexOf("```", startCode + 3)
                if (endCode == -1) {
                    parts.add(MessageBlock.Code(text.substring(startCode + 3)))
                    break
                } else {
                    parts.add(MessageBlock.Code(text.substring(startCode + 3, endCode)))
                    currentIndex = endCode + 3
                }
            }
        }
        parts
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MessageBlock.Text -> {
                    Text(block.content.trim(), fontSize = 14.sp, color = Color.White)
                }
                is MessageBlock.Code -> {
                    val rawContent = block.content
                    val lines = rawContent.split("\n")
                    val hasLanguage = lines.firstOrNull()?.let { firstLine ->
                        firstLine.isNotBlank() && !firstLine.contains(" ") && firstLine.length < 15
                    } ?: false

                    val language = if (hasLanguage) lines.first() else "CÓDIGO"
                    val codeBody = if (hasLanguage) lines.drop(1).joinToString("\n") else rawContent

                    Surface(
                        color = Color(0xFF0F0F10),
                        border = BorderStroke(1.dp, Color(0xFF52525B)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color(0xFF242427)).padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(language.uppercase(Locale.ROOT), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFFA1A1AA))
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(codeBody))
                                        Toast.makeText(context, "Código copiado", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = Color(0xFFA1A1AA), modifier = Modifier.size(14.dp))
                                }
                            }
                            Text(
                                text = codeBody.trim(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color.White,
                                modifier = Modifier.padding(12.dp).horizontalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        }
    }
}

sealed class MessageBlock {
    data class Text(val content: String) : MessageBlock()
    data class Code(val content: String) : MessageBlock()
}

@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isGenerating: Boolean,
    onNavigateToSandbox: () -> Unit,
    onStartDictation: () -> Unit,
    placeholderText: String
) {
    Surface(
        color = Color(0xFF18181B),
        border = BorderStroke(1.dp, Color(0xFF52525B)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateToSandbox, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.AttachFile, contentDescription = "Sandbox", tint = Color(0xFFA1A1AA))
            }

            IconButton(onClick = onStartDictation, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Mic, contentDescription = "Dictar", tint = MaterialTheme.colorScheme.primary)
            }

            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholderText, color = Color(0xFFA1A1AA), fontSize = 14.sp) },
                modifier = Modifier.weight(1f).testTag("chat_input_text_field"),
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            IconButton(
                onClick = onSendClick,
                enabled = !isGenerating && value.isNotBlank(),
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (value.isNotBlank() && !isGenerating) MaterialTheme.colorScheme.primary else Color(0xFF242427))
            ) {
                Icon(Icons.Default.Send, contentDescription = "Enviar", tint = if (value.isNotBlank() && !isGenerating) Color.White else Color(0xFFA1A1AA), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun ThinkingStepsLoader(steps: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
        border = BorderStroke(1.dp, Color(0xFF52525B)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 8.dp).testTag("thinking_steps_loader")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Agente Groq Pensando...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                steps.takeLast(3).forEachIndexed { index, step ->
                    val isLast = index == steps.takeLast(3).size - 1
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                        Icon(
                            imageVector = if (isLast) Icons.Default.HourglassEmpty else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isLast) MaterialTheme.colorScheme.primary else Color.Green,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(step, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = if (isLast) Color.White else Color(0xFFA1A1AA))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// SECCIONES DE CONFIGURACIÓN Y MÁS
// ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTabScreen(viewModel: ZaiViewModel) {
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()

    var keyInput by remember { mutableStateOf(apiKey) }
    var urlInput by remember { mutableStateOf(baseUrl) }
    var modelSelection by remember { mutableStateOf(selectedModel) }
    var darkModeEnabled by remember { mutableStateOf(true) }
    var hideKey by remember { mutableStateOf(true) }
    var showModelDropdown by remember { mutableStateOf(false) }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf<Boolean?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LaunchedEffect(apiKey, baseUrl, selectedModel) {
        keyInput = apiKey
        urlInput = baseUrl
        modelSelection = selectedModel
    }

    val models = listOf(
        "mixtral-8x7b-32768",
        "llama-3.3-70b-versatile",
        "llama-3.1-8b-instant",
        "gemma2-9b-it"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Configuración Avanzada", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Personaliza tus credenciales, URL de API y modelos", fontSize = 12.sp, color = Color(0xFFA1A1AA))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
                border = BorderStroke(1.dp, Color(0xFF52525B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("URL Base de la API", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Modifica la URL de Groq o un proxy compatible.", fontSize = 11.sp, color = Color(0xFFA1A1AA))
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFF52525B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
                border = BorderStroke(1.dp, Color(0xFF52525B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Groq API Key (gsk_...)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Tu token se guarda localmente de forma segura.", fontSize = 11.sp, color = Color(0xFFA1A1AA))
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        visualTransformation = if (hideKey) PasswordVisualTransformation() else VisualTransformation.None,
                        trailingIcon = {
                            IconButton(onClick = { hideKey = !hideKey }) {
                                Icon(imageVector = if (hideKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = Color(0xFFA1A1AA))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFF52525B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { keyInput = "YOUR_GROQ_API_KEY_HERE" },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text("Cargar Key de Prueba", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
                border = BorderStroke(1.dp, Color(0xFF52525B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Modelo Seleccionado", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = modelSelection,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { showModelDropdown = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().clickable { showModelDropdown = true },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color(0xFF52525B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        DropdownMenu(
                            expanded = showModelDropdown,
                            onDismissRequest = { showModelDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            models.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        modelSelection = model
                                        showModelDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
                border = BorderStroke(1.dp, Color(0xFF52525B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Modo Oscuro", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Text("Enfoca tu vista con tonos oscuros de alto contraste.", fontSize = 11.sp, color = Color(0xFFA1A1AA))
                    }
                    Switch(
                        checked = darkModeEnabled,
                        onCheckedChange = { darkModeEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    )
                }
            }
        }

        testStatus?.let { status ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (testSuccess == true) Color(0xFF14532D).copy(alpha = 0.3f) else Color(0xFF7F1D1D).copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, if (testSuccess == true) Color.Green else Color.Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = if (testSuccess == true) Icons.Default.CheckCircle else Icons.Default.Error, contentDescription = null, tint = if (testSuccess == true) Color.Green else Color.Red)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(status, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        isTesting = true
                        testStatus = "Probando conexión con Groq LPU..."
                        testSuccess = null
                        viewModel.testConnection(urlInput, keyInput, modelSelection) { success, msg ->
                            isTesting = false
                            testSuccess = success
                            testStatus = msg
                        }
                    },
                    enabled = !isTesting,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    } else {
                        Text("Probar Conexión")
                    }
                }

                Button(
                    onClick = {
                        viewModel.saveBaseUrl(urlInput)
                        viewModel.saveApiKey(keyInput)
                        viewModel.saveSelectedModel(modelSelection)
                        Toast.makeText(context, "Configuración guardada correctamente", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Guardar Cambios", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ModelsInfoScreen() {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("LLMs Optimizados en Groq LPU", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Nuestra pasarela de procesamiento de lenguaje natural aprovecha unidades LPU dedicadas para ofrecer velocidades superiores a 500 tokens por segundo.", fontSize = 12.sp, color = Color(0xFFA1A1AA))
        }
        item { ModelDetailCard("mixtral-8x7b-32768", "Inferencia MoE (Mixture of Experts). Tareas generales con excelente ventana de contexto de 32k tokens.") }
        item { ModelDetailCard("llama-3.3-70b-versatile", "Modelo de Meta de última generación optimizado para lógica compleja y razonamiento profundo.") }
        item { ModelDetailCard("llama-3.1-8b-instant", "Modelo ligero e instantáneo optimizado para chat interactivo rápido y respuestas en milisegundos.") }
        item { ModelDetailCard("gemma2-9b-it", "Modelo abierto de Google refinado para seguir instrucciones precisas de forma muy natural en español.") }
    }
}

@Composable
fun ModelDetailCard(name: String, desc: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)), border = BorderStroke(1.dp, Color(0xFF52525B)), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(desc, fontSize = 12.sp, color = Color.White)
        }
    }
}

@Composable
fun ProvidersScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Proveedores de Inferencia", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
        Text("GroqApp admite pasarelas compatibles con el formato de API de OpenAI. Redirige el tráfico cambiando la URL base.", fontSize = 12.sp, color = Color(0xFFA1A1AA))

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)), border = BorderStroke(1.dp, Color(0xFF52525B)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Groq Cloud (Por Defecto)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                Text("https://api.groq.com/openai/v1/", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Velocidades extremas con LPU y acceso gratuito a modelos de Meta y Google.", fontSize = 12.sp, color = Color(0xFFA1A1AA))
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)), border = BorderStroke(1.dp, Color(0xFF52525B)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("OpenAI API", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                Text("https://api.openai.com/v1/", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Compatible configurando tu clave sk_... y seleccionando un modelo de GPT válido.", fontSize = 12.sp, color = Color(0xFFA1A1AA))
            }
        }
    }
}

@Composable
fun HelpScreen() {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Ayuda & Guías de Uso", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Aprende a sacarle el máximo partido al Agente y al Chat de GroqApp.", fontSize = 12.sp, color = Color(0xFFA1A1AA))
        }
        item { HelpCard("Modo Chat Directo", "Te comunica directamente con el LLM sin demoras. Ideal para preguntas inmediatas o depuración rápida de código.") }
        item { HelpCard("Modo Agente Autónomo", "El Agente sigue un proceso cognitivo: analiza tu instrucción, examina el sandbox de archivos locales para encontrar referencias y simula búsquedas web antes de componer la respuesta final.") }
        item { HelpCard("Sandbox de Archivos", "En la pestaña 'Archivos' puedes crear documentos locales (ej: notas.txt). Pídele al Agente 'lee notas.txt y resume' para que lo inyecte automáticamente en el contexto.") }
    }
}

@Composable
fun HelpCard(title: String, desc: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)), border = BorderStroke(1.dp, Color(0xFF52525B)), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(desc, fontSize = 12.sp, color = Color.White)
        }
    }
}

@Composable
fun SupportScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Soporte Técnico", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
        Text("¿Tienes sugerencias, dudas o has encontrado un bug?", fontSize = 12.sp, color = Color(0xFFA1A1AA))

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)), border = BorderStroke(1.dp, Color(0xFF52525B)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Contacto por Correo", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                Text("manucarrasquel66@gmail.com", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Escríbenos directamente y te daremos respuesta en un plazo menor a 24 horas.", fontSize = 12.sp, color = Color(0xFFA1A1AA))
            }
        }
    }
}

@Composable
fun AboutTabScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("GroqApp", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Text("Desarrollado con Groq API LPU v2", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp).testTag("about_api_label"))

        Spacer(modifier = Modifier.height(24.dp))

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)), border = BorderStroke(1.dp, Color(0xFF52525B)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Esta aplicación ha sido completamente optimizada para interactuar de forma nativa con los aceleradores de inferencia LPU de Groq, soportando un entorno local (sandbox) de archivos y un potente asistente cognitivo.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF52525B), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    InfoStat("Plataforma", "Android")
                    InfoStat("Lenguaje", "Kotlin")
                    InfoStat("UI", "Compose")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Versión 2.0.0 (Groq Edition)", fontSize = 11.sp, color = Color(0xFFA1A1AA))
    }
}

@Composable
fun InfoStat(label: String, valText: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = Color(0xFFA1A1AA))
        Text(valText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}
