package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.ChatMessage
import com.example.data.database.ChatSession
import com.example.ui.AttachedFile
import com.example.ui.SandboxWebView
import com.example.ui.ThemeMode
import com.example.ui.ZaiViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ZaiViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            MyApplicationTheme(themeMode = themeMode) {
                MainApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: ZaiViewModel = viewModel()) {
    val onboardingDone by viewModel.onboardingCompleted.collectAsStateWithLifecycle()
    var showOnboarding by remember(onboardingDone) { mutableStateOf(!onboardingDone) }
    val context = LocalContext.current
    val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()
    val apiError by viewModel.apiError.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var showToolsSheet by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showConectores by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            val name = getFileName(context, uri) ?: "archivo"
            val type = context.contentResolver.getType(uri) ?: "*/*"
            viewModel.addPendingFile(AttachedFile(uri, name, type))
        }
    }

    var speechCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            speechCallback?.invoke(spoken)
        }
    }

    LaunchedEffect(apiError) {
        apiError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearApiError()
        }
    }

    if (showOnboarding) {
        OnboardingScreen(
            viewModel = viewModel,
            onFinish = {
                showOnboarding = false
                viewModel.openSettings()
            }
        )
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                DrawerContent(
                    viewModel = viewModel,
                    onCloseDrawer = { scope.launch { drawerState.close() } },
                    onOpenSettings = {
                        scope.launch { drawerState.close() }
                        viewModel.openSettings()
                    },
                    onOpenProfile = {
                        scope.launch { drawerState.close() }
                        showProfile = true
                    },
                    onShowTerms = {
                        scope.launch { drawerState.close() }
                        showTerms = true
                    },
                    onLogout = {
                        scope.launch { drawerState.close() }
                        viewModel.logout()
                        showOnboarding = true
                    }
                )
            }
        ) {
            Scaffold(
                containerColor = GroqBackground,
                topBar = {
                    AppTopBarFixed(
                        viewModel = viewModel,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onHelpClick = { showHelp = true }
                    )
                }
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(GroqBackground)
                ) {
                    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
                    when (currentTab) {
                        "Chat" -> ChatTab(
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
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Dictado no soportado", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onOpenTools = { showToolsSheet = true }
                        )
                        "Agente" -> AgentTab(
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
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Dictado no soportado", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onOpenTools = { showToolsSheet = true }
                        )
                    }
                }
            }

            if (showToolsSheet) {
                ToolsBottomSheet(
                    viewModel = viewModel,
                    onDismiss = { showToolsSheet = false },
                    onShowConectores = {
                        showToolsSheet = false
                        showConectores = true
                    },
                    onExport = {
                        val export = viewModel.getCurrentExport()
                        if (export.isBlank()) {
                            Toast.makeText(context, "Nada para exportar", Toast.LENGTH_SHORT).show()
                        } else {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, export)
                            }
                            context.startActivity(Intent.createChooser(send, "Exportar chat"))
                        }
                        showToolsSheet = false
                    }
                )
            }
        }
    }

    if (showSettings) {
        SettingsDialog(viewModel = viewModel, onDismiss = { viewModel.closeSettings() })
    }

    if (showHelp) {
        HelpDialog(onDismiss = { showHelp = false })
    }
    if (showTerms) {
        TermsDialog(onDismiss = { showTerms = false })
    }
    if (showProfile) {
        ProfileDialog(onDismiss = { showProfile = false }, viewModel = viewModel)
    }
    if (showConectores) {
        ConectoresDialog(onDismiss = { showConectores = false })
    }
}

fun getFileName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        }
    } catch (_: Exception) {
        null
    }
}

// ═══════════════════════════════════════════
// DRAWER
// ═══════════════════════════════════════════

@Composable
fun DrawerContent(
    viewModel: ZaiViewModel,
    onCloseDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onShowTerms: () -> Unit,
    onLogout: () -> Unit
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val chatSessions by viewModel.chatSessions.collectAsStateWithLifecycle()
    val agentSessions by viewModel.agentSessions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()) }

    ModalDrawerSheet(
        drawerContainerColor = GroqBackground,
        drawerContentColor = Color.White,
        modifier = Modifier.width(320.dp)
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header logo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.app_logo),
                    contentDescription = "Logo",
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(10.dp))
                Text("GroqApp", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onCloseDrawer) {
                    Icon(Icons.Default.Close, null, tint = GroqTextSecondary)
                }
            }
            Spacer(Modifier.height(16.dp))

            // Tabs A CHAT / AGENTE
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chatSelected = currentTab == "Chat"
                val agenteSelected = currentTab == "Agente"
                Button(
                    onClick = { viewModel.setCurrentTab("Chat"); onCloseDrawer() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (chatSelected) GroqOrange else GroqSurfaceVariant,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = if (chatSelected) null else BorderStroke(1.dp, GroqOutline)
                ) {
                    Icon(Icons.Default.Chat, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("A CHAT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Button(
                    onClick = { viewModel.setCurrentTab("Agente"); onCloseDrawer() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (agenteSelected) GroqOrange else GroqSurfaceVariant,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = if (agenteSelected) null else BorderStroke(1.dp, GroqOutline)
                ) {
                    Icon(Icons.Default.SmartToy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("AGENTE", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(20.dp))
            // Search toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HISTORIAL", color = GroqOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showSearch = !showSearch }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Search, null, tint = GroqTextSecondary, modifier = Modifier.size(18.dp))
                }
            }
            if (showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar sesiones...", color = GroqTextSecondary, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GroqOrange,
                        unfocusedBorderColor = GroqOutline,
                        cursorColor = GroqOrange,
                        focusedContainerColor = GroqSurface,
                        unfocusedContainerColor = GroqSurface
                    ),
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = GroqTextSecondary, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, null, tint = GroqTextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            // New session buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { viewModel.startNewSession("Chat") },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, GroqOutline),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(6.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = GroqOrange, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nuevo Chat", color = Color.White, fontSize = 10.sp)
                }
                OutlinedButton(
                    onClick = { viewModel.startNewSession("Agente") },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, GroqOutline),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(6.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = GroqOrange, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nuevo Agente", color = Color.White, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(8.dp))

            val filteredChat = chatSessions.filter { it.title.contains(searchQuery, true) || searchQuery.isBlank() }
            val filteredAgent = agentSessions.filter { it.title.contains(searchQuery, true) || searchQuery.isBlank() }
            val sessionsToShow = if (currentTab == "Chat") filteredChat else filteredAgent
            val label = if (currentTab == "Chat") "Chat" else "Agente"

            if (sessionsToShow.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Sin sesiones de $label", color = GroqTextSecondary, fontSize = 12.sp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sessionsToShow.take(20).forEach { session ->
                        SessionDrawerRow(
                            session = session,
                            dateLabel = dateFmt.format(Date(session.timestamp)),
                            isSelected = false,
                            onClick = {
                                if (session.sessionType == "Agente") viewModel.selectAgentSession(session.id)
                                else viewModel.selectChatSession(session.id)
                                onCloseDrawer()
                            },
                            onDelete = { viewModel.deleteSession(session.id) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = GroqOutline.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))

            // PERFIL
            DrawerMenuItem(icon = Icons.Default.Person, label = "PERFIL (avatar)") {
                onOpenProfile()
            }
            // AJUSTES
            DrawerMenuItem(icon = Icons.Default.Settings, label = "AJUSTES") {
                onOpenSettings()
            }
            // Búsqueda
            DrawerMenuItem(icon = Icons.Default.Search, label = "Búsqueda") {
                showSearch = true
                Toast.makeText(context, "Filtra historial por título", Toast.LENGTH_SHORT).show()
            }
            // Términos
            DrawerMenuItem(icon = Icons.Default.Description, label = "Términos y Condiciones") {
                onShowTerms()
            }
            // Cerrar sesión
            DrawerMenuItem(icon = Icons.Default.Logout, label = "Cerrar Sesión", isDestructive = true) {
                onLogout()
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "GroqApp v1.0 • Groq + Pyodide",
                color = GroqTextSecondary,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun DrawerMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, isDestructive: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (isDestructive) Color(0xFFEF4444) else GroqTextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = if (isDestructive) Color(0xFFEF4444) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SessionDrawerRow(session: ChatSession, dateLabel: String, isSelected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) GroqSurfaceVariant else GroqSurface)
            .border(1.dp, if (isSelected) GroqOrange else GroqOutline.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (session.sessionType == "Agente") Icons.Default.SmartToy else Icons.Default.ChatBubbleOutline,
            null,
            tint = GroqOrange,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(session.title.ifBlank { "Sin título" }, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text("$dateLabel · ${session.model}", color = GroqTextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
            Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444).copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
        }
    }
}

// ═══════════════════════════════════════════
// TOP BAR FIJA
// ═══════════════════════════════════════════

@Composable
fun AppTopBarFixed(viewModel: ZaiViewModel, onMenuClick: () -> Unit, onHelpClick: () -> Unit) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val chatId by viewModel.currentChatSessionId.collectAsStateWithLifecycle()
    val agentId by viewModel.currentAgentSessionId.collectAsStateWithLifecycle()
    val chatSessions by viewModel.chatSessions.collectAsStateWithLifecycle()
    val agentSessions by viewModel.agentSessions.collectAsStateWithLifecycle()

    val currentTitle = remember(currentTab, chatId, agentId, chatSessions, agentSessions) {
        if (currentTab == "Chat") {
            chatSessions.find { it.id == chatId }?.title ?: "Sin título"
        } else {
            agentSessions.find { it.id == agentId }?.title ?: "Sin título"
        }
    }

    Surface(
        color = GroqSurface,
        border = BorderStroke(1.dp, GroqOutline.copy(alpha = 0.5f)),
        shadowElevation = 2.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "Menú", tint = Color.White)
            }
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = "Logo",
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("GROQ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("GroqApp", color = GroqTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(6.dp))
                    Text(selectedModel, color = GroqOrange, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(currentTitle, color = GroqTextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onHelpClick) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).border(1.dp, GroqOutline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
// ONBOARDING
// ═══════════════════════════════════════════

@Composable
fun OnboardingScreen(viewModel: ZaiViewModel, onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    var selectedProvider by remember { mutableStateOf("Groq") }
    var showProviderDropdown by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf(viewModel.defaultModels["Groq"] ?: "llama-3.1-8b-instant") }

    Box(
        Modifier.fillMaxSize().background(GroqBackground),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            colors = CardDefaults.cardColors(containerColor = GroqSurface),
            border = BorderStroke(1.dp, GroqOutline),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (step) {
                    0 -> {
                        Image(
                            painter = painterResource(R.drawable.app_logo),
                            contentDescription = "GroqApp",
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("GroqApp", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("¡Bienvenido a GroqApp!", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = GroqOrange)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Tu asistente de IA ultrarrápido con Python real (Pyodide), chat y agente autónomo. Fondo #0D0D0D, naranja #FF5722.",
                            textAlign = TextAlign.Center,
                            color = GroqTextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { step = 1 },
                            colors = ButtonDefaults.buttonColors(containerColor = GroqOrange),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Comenzar", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    1 -> {
                        Text("Tutorial rápido", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = GroqOrange)
                        Spacer(Modifier.height(12.dp))
                        TutorialCard("💬", "Chat", "Burbuja central Modo chat. Mensajes usuario derecha #242427, asistente izquierda #18181B con avatar robot. API real.")
                        TutorialCard("🤖", "Agente", "Ícono fractal + Modo Agente vacío. Pasos de razonamiento colapsables. Auto-ejecución python en Pyodide real.")
                        TutorialCard("🔧", "Herramientas", "Llave inglesa abre bottom sheet: Pensar, Buscar web, Lector voz, Limpiar, Exportar, Creatividad Slider.")
                        TutorialCard("📎", "Archivos", "Clip abre administrador archivos. Chips con X, múltiples sin límite. Agente descarga individual o ZIP.")
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { step = 0 }) { Text("Atrás", color = GroqTextSecondary) }
                            Button(
                                onClick = { step = 2 },
                                colors = ButtonDefaults.buttonColors(containerColor = GroqOrange),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Siguiente", color = Color.White) }
                        }
                    }
                    2 -> {
                        Text("Configura tu API", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = GroqOrange)
                        Spacer(Modifier.height(6.dp))
                        Text("Selecciona proveedor e ingresa API Key. Dropdowns funcionales.", textAlign = TextAlign.Center, color = GroqTextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(14.dp))

                        Text("Proveedor", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedProvider,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().clickable { showProviderDropdown = true },
                                trailingIcon = {
                                    IconButton(onClick = { showProviderDropdown = !showProviderDropdown }) {
                                        Icon(if (showProviderDropdown) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = GroqOrange)
                                    }
                                },
                                colors = outlinedFieldColors(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            DropdownMenu(
                                expanded = showProviderDropdown,
                                onDismissRequest = { showProviderDropdown = false },
                                modifier = Modifier.background(GroqSurface)
                            ) {
                                viewModel.providers.keys.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p, color = Color.White) },
                                        onClick = {
                                            selectedProvider = p
                                            viewModel.setProvider(p)
                                            model = viewModel.defaultModels[p] ?: model
                                            showProviderDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text("Modelo", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            modifier = Modifier.fillMaxWidth(),
                            colors = outlinedFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(Modifier.height(10.dp))
                        Text("API Key", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showKey = !showKey }) {
                                    Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = GroqTextSecondary)
                                }
                            },
                            placeholder = { Text("gsk_...", color = GroqTextSecondary) },
                            colors = outlinedFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                val url = when (selectedProvider) {
                                    "OpenAI" -> "https://platform.openai.com/api-keys"
                                    "OpenRouter" -> "https://openrouter.ai/keys"
                                    "Together" -> "https://api.together.xyz/settings/api-keys"
                                    else -> "https://console.groq.com/keys"
                                }
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        ) {
                            Text("¿No tienes clave? Obtener aquí", color = GroqOrange, fontSize = 13.sp)
                        }

                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = {
                                viewModel.setProvider(selectedProvider)
                                if (apiKey.isNotBlank()) viewModel.saveApiKey(apiKey)
                                if (model.isNotBlank()) viewModel.saveSelectedModel(model)
                                viewModel.completeOnboarding()
                                onFinish()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GroqOrange),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Guardar y abrir configuración", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = {
                            viewModel.completeOnboarding()
                            onFinish()
                        }) {
                            Text("Omitir por ahora", color = GroqTextSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { i ->
                        Box(
                            Modifier.size(if (i == step) 10.dp else 8.dp).clip(CircleShape)
                                .background(if (i == step) GroqOrange else GroqOutline)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TutorialCard(emoji: String, title: String, text: String) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = GroqSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
                Text(text, color = GroqTextSecondary, fontSize = 11.sp)
            }
        }
    }
}

// ═══════════════════════════════════════════
// CHAT TAB
// ═══════════════════════════════════════════

@Composable
fun ChatTab(
    viewModel: ZaiViewModel,
    onPickFile: () -> Unit,
    onStartDictation: ((String) -> Unit) -> Unit,
    onOpenTools: () -> Unit
) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isGen by viewModel.isGenerating.collectAsStateWithLifecycle()
    val loadMsg by viewModel.loadingMessage.collectAsStateWithLifecycle()
    val pendingFiles by viewModel.pendingFiles.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showCursorSheet by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size, isGen) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1 + if (isGen) 1 else 0)
        }
    }

    Column(Modifier.fillMaxSize()) {
        FileChipsRow(pendingFiles) { idx -> viewModel.removePendingFile(idx) }

        Box(Modifier.weight(1f)) {
            if (messages.isEmpty() && !isGen) {
                EmptyChatState()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatBubble(msg)
                    }
                    if (isGen) {
                        item { LoadingRow(loadMsg.ifBlank { "Pensando..." }) }
                    }
                }
            }
        }

        InputBar(
            value = text,
            onValue = { text = it },
            onSend = {
                if (text.isNotBlank() || pendingFiles.isNotEmpty()) {
                    viewModel.sendChatMessage(text)
                    text = ""
                }
            },
            isGen = isGen,
            hasText = text.isNotBlank() || pendingFiles.isNotEmpty(),
            onAttach = onPickFile,
            onTools = onOpenTools,
            onCursor = { showCursorSheet = true },
            onDictate = {
                onStartDictation { spoken ->
                    text = if (text.isBlank()) spoken else "$text $spoken"
                }
            }
        )
    }

    if (showCursorSheet) {
        CursorBottomSheet(
            onDismiss = { showCursorSheet = false },
            onSelectTemplate = { template ->
                text = if (text.isBlank()) template else "$text\n$template"
                showCursorSheet = false
            }
        )
    }
}

@Composable
fun EmptyChatState() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(80.dp).clip(CircleShape).background(GroqSurface).border(1.dp, GroqOutline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ChatBubbleOutline, null, tint = GroqOrange, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(14.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = GroqSurface),
            border = BorderStroke(1.dp, GroqOutline),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text("Modo chat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Escribe un mensaje o adjunta archivos", color = GroqTextSecondary, fontSize = 12.sp)
    }
}

// ═══════════════════════════════════════════
// AGENT TAB
// ═══════════════════════════════════════════

@Composable
fun AgentTab(
    viewModel: ZaiViewModel,
    onPickFile: () -> Unit,
    onStartDictation: ((String) -> Unit) -> Unit,
    onOpenTools: () -> Unit
) {
    val messages by viewModel.agentMessages.collectAsStateWithLifecycle()
    val isGen by viewModel.isGenerating.collectAsStateWithLifecycle()
    val steps by viewModel.thinkingSteps.collectAsStateWithLifecycle()
    val loadMsg by viewModel.loadingMessage.collectAsStateWithLifecycle()
    val pendingFiles by viewModel.pendingFiles.collectAsStateWithLifecycle()
    val pendingPython by viewModel.pendingPythonCode.collectAsStateWithLifecycle()
    val sandboxFiles by viewModel.sandboxFiles.collectAsStateWithLifecycle()
    val reasoning by viewModel.reasoningEnabled.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    var showPython by remember { mutableStateOf(false) }
    var pythonCode by remember { mutableStateOf("") }
    var pythonResult by remember { mutableStateOf("") }
    var showCursorSheet by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size, isGen, steps.size) {
        val target = messages.size + if (isGen) 1 else 0
        if (target > 0) listState.animateScrollToItem(target - 1)
    }

    LaunchedEffect(pendingPython) {
        pendingPython?.let { code ->
            if (code.isNotBlank()) {
                pythonCode = code
                pythonResult = "Cargando Pyodide..."
                showPython = true
                viewModel.clearPendingPython()
            }
        }
    }

    LaunchedEffect(messages.lastOrNull()?.id) {
        val last = messages.lastOrNull()
        if (last != null && last.role == "assistant" && last.content.contains("```python")) {
            val code = last.content.substringAfter("```python").substringBefore("```").trim()
            if (code.isNotBlank() && !showPython) {
                pythonCode = code
                pythonResult = "Cargando Pyodide..."
                showPython = true
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Surface(color = GroqSurfaceVariant.copy(alpha = 0.5f)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FractalIcon(modifier = Modifier.size(16.dp), color = GroqOrange)
                Spacer(Modifier.width(6.dp))
                Text("Modo Agente · razonamiento ${if (reasoning) "ON" else "OFF"}", color = GroqTextSecondary, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                if (sandboxFiles.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            val uri = viewModel.createSandboxZipUri()
                            if (uri != null) {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, "Descargar ZIP"))
                            } else {
                                Toast.makeText(context, "No hay archivos", Toast.LENGTH_SHORT).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Folder, null, tint = GroqOrange, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("ZIP", color = GroqOrange, fontSize = 11.sp)
                    }
                }
            }
        }

        FileChipsRow(pendingFiles) { idx -> viewModel.removePendingFile(idx) }

        if (sandboxFiles.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sandboxFiles.forEach { file ->
                    AssistChip(
                        onClick = {
                            val uri = viewModel.getDownloadUri(file.name)
                            if (uri != null) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Descargar ${file.name}"))
                            }
                        },
                        label = { Text(file.name, fontSize = 11.sp, maxLines = 1) },
                        leadingIcon = { Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = GroqSurface, labelColor = Color.White, leadingIconContentColor = GroqOrange),
                        border = BorderStroke(1.dp, GroqOutline)
                    )
                }
            }
        }

        Box(Modifier.weight(1f)) {
            if (messages.isEmpty() && !isGen) {
                EmptyAgentState()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        if (msg.role == "assistant") AgentMessageCard(msg) else ChatBubble(msg)
                    }
                    if (isGen) {
                        item {
                            if (reasoning && steps.isNotEmpty()) ThinkingCard(steps, loadMsg)
                            else LoadingRow(loadMsg.ifBlank { "Agente trabajando..." })
                        }
                    }
                }
            }
        }

        InputBar(
            value = text,
            onValue = { text = it },
            onSend = {
                if (text.isNotBlank() || pendingFiles.isNotEmpty()) {
                    viewModel.sendAgentMessage(text)
                    text = ""
                }
            },
            isGen = isGen,
            hasText = text.isNotBlank() || pendingFiles.isNotEmpty(),
            onAttach = onPickFile,
            onTools = onOpenTools,
            onCursor = { showCursorSheet = true },
            onDictate = {
                onStartDictation { spoken ->
                    text = if (text.isBlank()) spoken else "$text $spoken"
                }
            }
        )
    }

    if (showCursorSheet) {
        CursorBottomSheet(
            onDismiss = { showCursorSheet = false },
            onSelectTemplate = { template ->
                text = if (text.isBlank()) template else "$text\n$template"
                showCursorSheet = false
            }
        )
    }

    if (showPython) {
        PythonSandboxDialog(code = pythonCode, result = pythonResult, onResult = { pythonResult = it }, onDismiss = { showPython = false })
    }
}

@Composable
fun EmptyAgentState() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(80.dp).clip(CircleShape).background(GroqSurface).border(1.dp, GroqOutline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            FractalIcon(modifier = Modifier.size(40.dp), color = GroqOrange)
        }
        Spacer(Modifier.height(14.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = GroqSurface),
            border = BorderStroke(1.dp, GroqOutline),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text("Modo Agente", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Describe una tarea compleja", color = GroqTextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
        Text("El agente razona y puede ejecutar Python real", color = GroqTextSecondary, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
    }
}

@Composable
fun FractalIcon(modifier: Modifier = Modifier, color: Color = GroqOrange) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        drawCircle(color = color, radius = w * 0.45f, style = Stroke(width = w * 0.06f))
        drawCircle(color = color, radius = w * 0.30f, style = Stroke(width = w * 0.05f))
        drawCircle(color = color, radius = w * 0.15f, style = Stroke(width = w * 0.04f))
        drawLine(color, start = androidx.compose.ui.geometry.Offset(cx - w * 0.35f, cy - h * 0.25f), end = androidx.compose.ui.geometry.Offset(cx + w * 0.35f, cy + h * 0.25f), strokeWidth = w * 0.04f)
        drawLine(color, start = androidx.compose.ui.geometry.Offset(cx, cy - h * 0.4f), end = androidx.compose.ui.geometry.Offset(cx, cy + h * 0.4f), strokeWidth = w * 0.04f)
        drawLine(color, start = androidx.compose.ui.geometry.Offset(cx - w * 0.4f, cy), end = androidx.compose.ui.geometry.Offset(cx + w * 0.4f, cy), strokeWidth = w * 0.04f)
    }
}

// ═══════════════════════════════════════════
// MESSAGES
// ═══════════════════════════════════════════

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
        if (!isUser) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(GroqOrange.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SmartToy, null, tint = GroqOrange, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            colors = CardDefaults.cardColors(containerColor = if (isUser) GroqUserBubble else GroqAssistantBubble),
            border = BorderStroke(1.dp, GroqOutline.copy(alpha = 0.7f)),
            shape = RoundedCornerShape(topStart = if (isUser) 16.dp else 4.dp, topEnd = if (isUser) 4.dp else 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
        ) {
            Text(message.content, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
        }
        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(32.dp).clip(CircleShape).background(GroqSurfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, null, tint = GroqTextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun AgentMessageCard(message: ChatMessage) {
    var expanded by remember { mutableStateOf(true) }
    val steps = message.thinkingSteps?.split("\n")?.filter { it.isNotBlank() }.orEmpty()

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(GroqOrange.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.SmartToy, null, tint = GroqOrange, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.fillMaxWidth(0.9f)) {
            if (steps.isNotEmpty()) {
                Card(
                    Modifier.fillMaxWidth().animateContentSize().clickable { expanded = !expanded },
                    colors = CardDefaults.cardColors(containerColor = GroqSurfaceVariant),
                    border = BorderStroke(1.dp, GroqOutline.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lightbulb, null, tint = GroqOrange, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Razonamiento (${steps.size} pasos)", color = GroqOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = GroqTextSecondary, modifier = Modifier.size(18.dp))
                        }
                        if (expanded) {
                            Spacer(Modifier.height(8.dp))
                            steps.forEachIndexed { i, s ->
                                Row(Modifier.padding(vertical = 2.dp)) {
                                    Text("${i + 1}.", color = GroqOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(6.dp))
                                    Text(s, color = GroqTextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = GroqAssistantBubble),
                border = BorderStroke(1.dp, GroqOutline),
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
            ) {
                Text(message.content, modifier = Modifier.padding(14.dp), color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
fun ThinkingCard(steps: List<String>, loadMsg: String) {
    Card(
        Modifier.fillMaxWidth(0.9f),
        colors = CardDefaults.cardColors(containerColor = GroqSurface),
        border = BorderStroke(1.dp, GroqOrange.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), color = GroqOrange, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(loadMsg.ifBlank { "Agente pensando..." }, color = GroqOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            steps.forEach { s ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text("✓", color = GroqOrange, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(s, color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun LoadingRow(message: String) {
    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(GroqOrange.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(16.dp), color = GroqOrange, strokeWidth = 2.dp)
        }
        Spacer(Modifier.width(10.dp))
        Text(message, color = GroqTextSecondary, fontSize = 13.sp)
    }
}

@Composable
fun PythonSandboxDialog(code: String, result: String, onResult: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 560.dp),
            colors = CardDefaults.cardColors(containerColor = GroqSurface),
            border = BorderStroke(1.dp, GroqOutline),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, null, tint = GroqOrange)
                    Spacer(Modifier.width(8.dp))
                    Text("Python Sandbox (Pyodide Real)", fontWeight = FontWeight.Bold, color = GroqOrange, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = GroqTextSecondary) }
                }
                Spacer(Modifier.height(8.dp))
                Text("Código detectado:", color = GroqTextSecondary, fontSize = 12.sp)
                Box(
                    Modifier.fillMaxWidth().heightIn(max = 100.dp).clip(RoundedCornerShape(8.dp)).background(GroqBackground).padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(code, color = Color(0xFF86EFAC), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                AndroidView(
                    factory = { ctx ->
                        SandboxWebView(ctx).also { web ->
                            web.loadPyodide()
                            web.execute(code) { res -> onResult(res) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(8.dp)).background(GroqBackground)
                )
                Spacer(Modifier.height(8.dp))
                Text("Resultado:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 200.dp).clip(RoundedCornerShape(8.dp)).background(GroqBackground)
                        .border(1.dp, GroqOutline, RoundedCornerShape(8.dp)).padding(10.dp).verticalScroll(rememberScrollState())
                ) {
                    Text(result.ifBlank { "Ejecutando..." }, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = GroqOrange), modifier = Modifier.align(Alignment.End), shape = RoundedCornerShape(10.dp)) {
                    Text("Cerrar")
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
// TOOLS BOTTOM SHEET
// ═══════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsBottomSheet(
    viewModel: ZaiViewModel,
    onDismiss: () -> Unit,
    onShowConectores: () -> Unit,
    onExport: () -> Unit
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val reasoning by viewModel.reasoningEnabled.collectAsStateWithLifecycle()
    val webSearch by viewModel.webSearchEnabled.collectAsStateWithLifecycle()
    val tts by viewModel.ttsEnabled.collectAsStateWithLifecycle()
    val creativity by viewModel.creativity.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GroqSurface,
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).navigationBarsPadding()) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(GroqOutline))
            Spacer(Modifier.height(12.dp))
            Text(if (currentTab == "Chat") "Herramientas Chat" else "Herramientas Agente", color = GroqOrange, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))

            // Pensar
            ToolsSwitchRow("Pensar", "Razonamiento paso a paso", reasoning) { viewModel.setReasoningEnabled(it) }
            // Buscar web
            ToolsSwitchRow("Buscar web", "Información actualizada", webSearch) { viewModel.setWebSearchEnabled(it) }

            if (currentTab == "Chat") {
                ToolsSwitchRow("Lector de voz", "Lee respuestas en voz", tts) { viewModel.setTtsEnabled(it) }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = GroqOutline.copy(alpha = 0.4f))
                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        viewModel.clearCurrentConversation()
                        onDismiss()
                        Toast.makeText(context, "Conversación limpiada", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = GroqSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GroqOutline)
                ) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Limpiar conversación", color = Color.White)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onExport() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = GroqSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GroqOutline)
                ) {
                    Icon(Icons.Default.Share, null, tint = GroqOrange, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Exportar chat", color = Color.White)
                }

                Spacer(Modifier.height(16.dp))
                Text("Creatividad: ${String.format("%.1f", creativity)}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Slider(
                    value = creativity,
                    onValueChange = { viewModel.setCreativity(it) },
                    valueRange = 0f..2f,
                    steps = 19,
                    colors = SliderDefaults.colors(thumbColor = GroqOrange, activeTrackColor = GroqOrange, inactiveTrackColor = GroqOutline)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Preciso", color = GroqTextSecondary, fontSize = 10.sp)
                    Text("Equilibrado", color = GroqTextSecondary, fontSize = 10.sp)
                    Text("Creativo", color = GroqTextSecondary, fontSize = 10.sp)
                }
            } else {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = GroqOutline.copy(alpha = 0.4f))
                Spacer(Modifier.height(12.dp))

                ToolsNavigationRow("Conectores", "APIs, Drive, Slack, etc") { onShowConectores() }
                ToolsNavigationRow("Github", "Repositorios y código") {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com"))
                    context.startActivity(intent)
                }

                Spacer(Modifier.height(16.dp))
                Text("Tip: El agente puede crear archivos con ```file:nombre", color = GroqTextSecondary, fontSize = 11.sp)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun ToolsSwitchRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, color = GroqTextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = GroqOrange, uncheckedThumbColor = GroqTextSecondary, uncheckedTrackColor = GroqSurfaceVariant)
        )
    }
}

@Composable
fun ToolsNavigationRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).background(GroqSurfaceVariant).border(1.dp, GroqOutline, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, color = GroqTextSecondary, fontSize = 11.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = GroqTextSecondary)
    }
    Spacer(Modifier.height(8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CursorBottomSheet(onDismiss: () -> Unit, onSelectTemplate: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = GroqSurface, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding()) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(GroqOutline))
            Spacer(Modifier.height(12.dp))
            Text("Mejorar Prompt (Cursor I)", color = GroqOrange, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            val templates = listOf(
                "Actúa como experto en..." to "Actúa como un experto en [tema] y explica paso a paso...",
                "Resumir texto" to "Resume el siguiente texto de forma clara y concisa:\n\n",
                "Explicar código" to "Explica este código línea por línea y sugiere mejoras:\n\n",
                "Mejorar escritura" to "Mejora la redacción de este texto manteniendo el significado:\n\n",
                "Crear tabla" to "Crea una tabla comparativa sobre:\n\n",
                "Generar ideas" to "Genera 5 ideas creativas sobre:\n\n"
            )
            templates.forEach { (title, content) ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSelectTemplate(content) },
                    colors = CardDefaults.cardColors(containerColor = GroqSurfaceVariant),
                    border = BorderStroke(1.dp, GroqOutline),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, null, tint = GroqOrange, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(content.take(40) + "...", color = GroqTextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ═══════════════════════════════════════════
// SETTINGS
// ═══════════════════════════════════════════

@Composable
fun SettingsDialog(viewModel: ZaiViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val currentProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val currentUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val currentModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val currentKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTestingConnection.collectAsStateWithLifecycle()
    val testStatus by viewModel.connectionTestStatus.collectAsStateWithLifecycle()
    val antiSpam by viewModel.antiSpamEnabled.collectAsStateWithLifecycle()

    var provider by remember { mutableStateOf(currentProvider) }
    var url by remember { mutableStateOf(currentUrl) }
    var model by remember { mutableStateOf(currentModel) }
    var key by remember { mutableStateOf(currentKey) }
    var showKey by remember { mutableStateOf(false) }
    var showProviderMenu by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 640.dp),
            colors = CardDefaults.cardColors(containerColor = GroqSurface),
            border = BorderStroke(1.dp, GroqOutline),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, null, tint = GroqOrange)
                    Spacer(Modifier.width(8.dp))
                    Text("Configuración", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = GroqTextSecondary) }
                }

                Spacer(Modifier.height(16.dp))
                Text("Proveedor", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedTextField(
                        value = provider,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().clickable { showProviderMenu = true },
                        trailingIcon = {
                            IconButton(onClick = { showProviderMenu = !showProviderMenu }) {
                                Icon(if (showProviderMenu) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = GroqOrange)
                            }
                        },
                        colors = outlinedFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    DropdownMenu(expanded = showProviderMenu, onDismissRequest = { showProviderMenu = false }, modifier = Modifier.background(GroqSurface)) {
                        viewModel.providers.keys.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p, color = Color.White) },
                                onClick = {
                                    provider = p
                                    url = viewModel.providers[p] ?: url
                                    model = viewModel.defaultModels[p] ?: model
                                    showProviderMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("URL Base", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(), colors = outlinedFieldColors(), shape = RoundedCornerShape(12.dp), singleLine = true)

                Spacer(Modifier.height(12.dp))
                Text("Modelo", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = model, onValueChange = { model = it }, modifier = Modifier.fillMaxWidth(), colors = outlinedFieldColors(), shape = RoundedCornerShape(12.dp), singleLine = true, placeholder = { Text("llama-3.1-8b-instant", color = GroqTextSecondary) })

                Spacer(Modifier.height(12.dp))
                Text("API Key", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = GroqTextSecondary)
                        }
                    },
                    colors = outlinedFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(Modifier.height(16.dp))
                Text("Tema", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Box {
                    val themeLabel = when (themeMode) {
                        ThemeMode.LIGHT -> "Claro"
                        ThemeMode.DARK -> "Oscuro"
                        ThemeMode.SYSTEM -> "Sistema"
                    }
                    OutlinedTextField(
                        value = themeLabel,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().clickable { showThemeMenu = true },
                        trailingIcon = { IconButton(onClick = { showThemeMenu = !showThemeMenu }) { Icon(Icons.Default.ExpandMore, null, tint = GroqOrange) } },
                        colors = outlinedFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    DropdownMenu(expanded = showThemeMenu, onDismissRequest = { showThemeMenu = false }, modifier = Modifier.background(GroqSurface)) {
                        listOf(ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Oscuro", ThemeMode.SYSTEM to "Sistema").forEach { (mode, label) ->
                            DropdownMenuItem(text = { Text(label, color = Color.White) }, onClick = { viewModel.saveThemeMode(mode); showThemeMenu = false })
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Anti-spam", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Evita mensajes duplicados rápidos", color = GroqTextSecondary, fontSize = 11.sp)
                    }
                    Switch(checked = antiSpam, onCheckedChange = { viewModel.setAntiSpamEnabled(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = GroqOrange, uncheckedThumbColor = GroqTextSecondary, uncheckedTrackColor = GroqSurfaceVariant))
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = GroqOutline.copy(alpha = 0.4f))
                Spacer(Modifier.height(12.dp))
                Text("Acerca de", color = GroqOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text("GroqApp v1.0 - App nativa Kotlin + Compose + Material 3. API Groq (Retrofit + Moshi), Room DB, Pyodide real en WebView. Colores #0D0D0D, #FF5722, #18181B, #52525B.", color = GroqTextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Text("Proveedores: Groq, OpenAI, OpenRouter, Together. Modelos Llama 3.1, GPT-4o, etc.", color = GroqTextSecondary, fontSize = 11.sp)

                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { showTestDialog = true; viewModel.testConnection(url, key, model) },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, GroqOutline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.NetworkCheck, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Probar conexión", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            viewModel.saveAllSettings(provider, url, model, key)
                            Toast.makeText(context, "Configuración guardada", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = GroqOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Guardar", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showTestDialog) {
        AlertDialog(
            onDismissRequest = { if (!isTesting) { showTestDialog = false; viewModel.clearConnectionTest() } },
            containerColor = GroqSurface,
            title = { Text("Probar conexión", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (isTesting) {
                        CircularProgressIndicator(color = GroqOrange, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(testStatus ?: "Iniciando...", color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                TextButton(onClick = { showTestDialog = false; viewModel.clearConnectionTest() }, enabled = !isTesting) { Text("Cerrar", color = GroqOrange) }
            }
        )
    }
}

// ═══════════════════════════════════════════
// SHARED UI
// ═══════════════════════════════════════════

@Composable
fun FileChipsRow(files: List<AttachedFile>, onRemove: (Int) -> Unit) {
    if (files.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        files.forEachIndexed { idx, file ->
            InputChip(
                selected = false,
                onClick = { },
                label = { Text(file.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp)) },
                leadingIcon = { Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(14.dp)) },
                trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp).clickable { onRemove(idx) }) },
                colors = InputChipDefaults.inputChipColors(containerColor = GroqSurfaceVariant, labelColor = Color.White, leadingIconColor = GroqOrange, trailingIconColor = Color(0xFFEF4444)),
                border = BorderStroke(1.dp, GroqOutline)
            )
        }
    }
}

@Composable
fun InputBar(
    value: String,
    onValue: (String) -> Unit,
    onSend: () -> Unit,
    isGen: Boolean,
    hasText: Boolean,
    onAttach: () -> Unit,
    onTools: () -> Unit,
    onCursor: () -> Unit,
    onDictate: () -> Unit
) {
    Surface(Modifier.fillMaxWidth(), color = GroqSurface, border = BorderStroke(1.dp, GroqOutline.copy(alpha = 0.5f)), shadowElevation = 4.dp) {
        Row(
            Modifier.padding(horizontal = 4.dp, vertical = 6.dp).navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttach, enabled = !isGen, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.AttachFile, contentDescription = "Clip - Adjuntar archivo", tint = GroqTextSecondary, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onTools, enabled = !isGen, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Build, contentDescription = "Llave inglesa - Herramientas", tint = GroqOrange, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onCursor, enabled = !isGen, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.TextFields, contentDescription = "Cursor I - Mejorar prompt", tint = GroqTextSecondary, modifier = Modifier.size(20.dp))
            }
            TextField(
                value = value,
                onValueChange = onValue,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Mensaje...", color = GroqTextSecondary, fontSize = 14.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = GroqOrange
                ),
                maxLines = 5
            )
            IconButton(onClick = onDictate, enabled = !isGen, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Mic, contentDescription = "Micrófono", tint = GroqOrange, modifier = Modifier.size(22.dp))
            }
            IconButton(
                onClick = onSend,
                enabled = !isGen && hasText,
                modifier = Modifier.size(38.dp).clip(CircleShape).background(if (!isGen && hasText) GroqOrange else GroqSurfaceVariant)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", tint = if (!isGen && hasText) Color.White else GroqOutline, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GroqSurface,
        title = { Text("Ayuda - GroqApp", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("• Menú izquierdo: Cambia entre Chat y Agente, historial con fechas, perfil, ajustes, búsqueda, términos, cerrar sesión.", color = GroqTextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text("• Barra inferior: Clip adjunta archivos múltiples sin límite, llave inglesa abre herramientas, cursor I mejora prompt, mic dictado, enviar.", color = GroqTextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text("• Herramientas Chat: Pensar, Buscar web, Lector voz, Limpiar, Exportar, Creatividad Slider 0-2.", color = GroqTextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text("• Herramientas Agente: Pensar, Buscar web, Conectores >, Github >.", color = GroqTextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text("• Pyodide ejecuta Python real en WebView. Código ```python se auto-ejecuta. Archivos ```file:name.", color = GroqTextSecondary, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Entendido", color = GroqOrange) } }
    )
}

@Composable
fun TermsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GroqSurface,
        title = { Text("Términos y Condiciones", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 300.dp)) {
                Text("Al usar GroqApp aceptas que:\n\n1. La API Key se almacena localmente.\n2. Los mensajes se guardan en Room DB local.\n3. El código Python se ejecuta en sandbox Pyodide aislado.\n4. No somos responsables del contenido generado por modelos externos.\n5. Debes cumplir con los términos de Groq, OpenAI, etc.\n\nContacto: manucarrasquel66@gmail.com", color = GroqTextSecondary, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = GroqOrange) } }
    )
}

@Composable
fun ProfileDialog(onDismiss: () -> Unit, viewModel: ZaiViewModel) {
    val chatCount by viewModel.chatSessions.collectAsStateWithLifecycle()
    val agentCount by viewModel.agentSessions.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GroqSurface,
        title = { Text("PERFIL", color = GroqOrange, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(GroqOrange.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = GroqOrange, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("Usuario GroqApp", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("usuario@groqapp.local", color = GroqTextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Text("Sesiones Chat: ${chatCount.size} | Agente: ${agentCount.size}", color = GroqTextSecondary, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = GroqOrange) } }
    )
}

@Composable
fun ConectoresDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GroqSurface,
        title = { Text("Conectores", color = GroqOrange, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("GitHub (código)" to "https://github.com", "Google Drive (archivos)" to "https://drive.google.com", "Slack (notificaciones)" to "https://slack.com", "Notion (docs)" to "https://notion.so").forEach { (name, url) ->
                    Card(
                        Modifier.fillMaxWidth().clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        colors = CardDefaults.cardColors(containerColor = GroqSurfaceVariant),
                        border = BorderStroke(1.dp, GroqOutline),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Link, null, tint = GroqOrange, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(name, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = GroqOrange) } }
    )
}

@Composable
private fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = GroqOrange,
    unfocusedBorderColor = GroqOutline,
    focusedLabelColor = GroqOrange,
    unfocusedLabelColor = GroqTextSecondary,
    cursorColor = GroqOrange,
    focusedContainerColor = GroqBackground,
    unfocusedContainerColor = GroqBackground
)
