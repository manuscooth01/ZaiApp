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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import com.example.ui.theme.GroqAssistantBubble
import com.example.ui.theme.GroqBackground
import com.example.ui.theme.GroqOrange
import com.example.ui.theme.GroqOutline
import com.example.ui.theme.GroqSurface
import com.example.ui.theme.GroqSurfaceVariant
import com.example.ui.theme.GroqTextSecondary
import com.example.ui.theme.GroqUserBubble
import com.example.ui.theme.MyApplicationTheme
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

@Composable
fun MainApp(viewModel: ZaiViewModel = viewModel()) {
    val onboardingDone by viewModel.onboardingCompleted.collectAsStateWithLifecycle()
    var showOnboarding by remember { mutableStateOf(!onboardingDone) }
    val context = LocalContext.current
    val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()

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

    if (showOnboarding) {
        OnboardingScreen(
            viewModel = viewModel,
            onFinish = { showOnboarding = false }
        )
    } else {
        MainScreen(
            viewModel = viewModel,
            onPickFile = { filePicker.launch("*/*") },
            onStartDictation = { callback ->
                speechCallback = callback
                try {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Dicta tu mensaje...")
                    }
                    speechLauncher.launch(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "Dictado no soportado en este dispositivo", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showSettings) {
        SettingsDialog(viewModel = viewModel, onDismiss = { viewModel.closeSettings() })
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

// ═══════════════════════════════════════════════════════════
// ONBOARDING
// ═══════════════════════════════════════════════════════════

@Composable
fun OnboardingScreen(viewModel: ZaiViewModel, onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    var selectedProvider by remember { mutableStateOf("Groq") }
    var showProviderDropdown by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(GroqBackground),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            colors = CardDefaults.cardColors(containerColor = GroqSurface),
            border = BorderStroke(1.dp, GroqOutline),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (step) {
                    0 -> {
                        Image(
                            painter = painterResource(R.drawable.app_logo),
                            contentDescription = "GroqApp",
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "¡Bienvenido a GroqApp!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = Color.White
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Tu asistente de IA ultrarrápido con Python real, chat y agente autónomo.",
                            textAlign = TextAlign.Center,
                            color = GroqTextSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(28.dp))
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
                        Text(
                            "Tutorial rápido",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = GroqOrange
                        )
                        Spacer(Modifier.height(16.dp))
                        TutorialCard("💬", "Chat", "Conversación directa con la IA. Adjunta archivos y dicta mensajes.")
                        TutorialCard("🤖", "Agente", "Resuelve tareas complejas con pasos de razonamiento y Python real.")
                        TutorialCard("📎", "Archivos", "Adjunta múltiples archivos sin límite. El agente puede modificarlos.")
                        TutorialCard("⚙️", "Configuración", "Elige proveedor, modelo y API Key. Prueba la conexión.")
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { step = 2 },
                            colors = ButtonDefaults.buttonColors(containerColor = GroqOrange),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Siguiente", color = Color.White)
                        }
                    }

                    2 -> {
                        Text(
                            "Configura tu API",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = GroqOrange
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Selecciona el proveedor e ingresa tu API Key para empezar.",
                            textAlign = TextAlign.Center,
                            color = GroqTextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(16.dp))

                        Text("Proveedor", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedProvider,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showProviderDropdown = true },
                                trailingIcon = {
                                    IconButton(onClick = { showProviderDropdown = !showProviderDropdown }) {
                                        Icon(
                                            if (showProviderDropdown) Icons.Default.ExpandLess
                                            else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = GroqOrange
                                        )
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
                                            showProviderDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text("API Key", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (showKey) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showKey = !showKey }) {
                                    Icon(
                                        if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = GroqTextSecondary
                                    )
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
                            Text("¿No tienes clave? Obtener aquí", color = GroqOrange)
                        }

                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.setProvider(selectedProvider)
                                if (apiKey.isNotBlank()) viewModel.saveApiKey(apiKey)
                                viewModel.completeOnboarding()
                                onFinish()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GroqOrange),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Guardar y empezar", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = {
                            viewModel.completeOnboarding()
                            onFinish()
                        }) {
                            Text("Omitir por ahora", color = GroqTextSecondary)
                        }
                    }
                }

                // Step indicator
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { i ->
                        Box(
                            Modifier
                                .size(if (i == step) 10.dp else 8.dp)
                                .clip(CircleShape)
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
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = GroqSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text, color = GroqTextSecondary, fontSize = 12.sp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// MAIN SCREEN
// ═══════════════════════════════════════════════════════════

@Composable
fun MainScreen(
    viewModel: ZaiViewModel,
    onPickFile: () -> Unit,
    onStartDictation: ((String) -> Unit) -> Unit
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val apiError by viewModel.apiError.collectAsStateWithLifecycle()
    val showHistory by viewModel.showHistoryMenu.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(apiError) {
        apiError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearApiError()
        }
    }

    Scaffold(
        containerColor = GroqBackground,
        topBar = {
            AppTopBar(
                viewModel = viewModel,
                onHistoryClick = { viewModel.toggleHistoryMenu() },
                onSettingsClick = { viewModel.openSettings() }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = GroqSurface,
                contentColor = Color.White,
                tonalElevation = 0.dp
            ) {
                listOf(
                    "Chat" to Icons.Default.Chat,
                    "Agente" to Icons.Default.SmartToy
                ).forEach { (label, icon) ->
                    NavigationBarItem(
                        selected = currentTab == label,
                        onClick = { viewModel.setCurrentTab(label) },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = GroqOrange,
                            selectedTextColor = GroqOrange,
                            unselectedIconColor = GroqTextSecondary,
                            unselectedTextColor = GroqTextSecondary,
                            indicatorColor = GroqSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GroqBackground)
        ) {
            when (currentTab) {
                "Chat" -> ChatTab(viewModel, onPickFile, onStartDictation)
                "Agente" -> AgentTab(viewModel, onPickFile, onStartDictation)
            }

            if (showHistory) {
                HistoryDropdownOverlay(
                    viewModel = viewModel,
                    onDismiss = { viewModel.closeHistoryMenu() }
                )
            }
        }
    }
}

@Composable
fun AppTopBar(
    viewModel: ZaiViewModel,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val reasoning by viewModel.reasoningEnabled.collectAsStateWithLifecycle()

    Surface(
        color = GroqSurface,
        border = BorderStroke(1.dp, GroqOutline.copy(alpha = 0.4f)),
        shadowElevation = 2.dp
    ) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo + name (top-left)
                Image(
                    painter = painterResource(R.drawable.app_logo),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "GroqApp",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Spacer(Modifier.weight(1f))

                // Agent reasoning switch
                if (currentTab == "Agente") {
                    Text("Razonar", color = GroqTextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.width(4.dp))
                    Switch(
                        checked = reasoning,
                        onCheckedChange = { viewModel.setReasoningEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GroqOrange,
                            uncheckedThumbColor = GroqTextSecondary,
                            uncheckedTrackColor = GroqSurfaceVariant
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }

                // History button: half white / half black with white triangle
                HistoryIconButton(onClick = onHistoryClick)
                Spacer(Modifier.width(4.dp))

                // Settings gear
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Configuración",
                        tint = GroqTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryIconButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, GroqOutline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(28.dp)) {
            val w = size.width
            val h = size.height
            // Left half white
            drawRect(Color.White, Offset.Zero, Size(w / 2f, h))
            // Right half black
            drawRect(Color.Black, Offset(w / 2f, 0f), Size(w / 2f, h))
            // White triangle pointing right
            val path = Path().apply {
                moveTo(w * 0.58f, h * 0.30f)
                lineTo(w * 0.82f, h * 0.50f)
                lineTo(w * 0.58f, h * 0.70f)
                close()
            }
            drawPath(path, Color.White)
        }
    }
}

// ═══════════════════════════════════════════════════════════
// HISTORY DROPDOWN
// ═══════════════════════════════════════════════════════════

@Composable
fun HistoryDropdownOverlay(viewModel: ZaiViewModel, onDismiss: () -> Unit) {
    val chatSessions by viewModel.chatSessions.collectAsStateWithLifecycle()
    val agentSessions by viewModel.agentSessions.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("Chat") }
    val dateFmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }

    Box(Modifier.fillMaxSize()) {
        // Scrim
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDismiss)
        )

        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .heightIn(max = 480.dp),
            colors = CardDefaults.cardColors(containerColor = GroqSurface),
            border = BorderStroke(1.dp, GroqOutline),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Historial",
                color = GroqOrange,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(10.dp))

            // Filters Chat / Agente
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == "Chat",
                    onClick = { filter = "Chat" },
                    label = { Text("Chat") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GroqOrange,
                        selectedLabelColor = Color.White,
                        containerColor = GroqSurfaceVariant,
                        labelColor = Color.White
                    )
                )
                FilterChip(
                    selected = filter == "Agente",
                    onClick = { filter = "Agente" },
                    label = { Text("Agente") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GroqOrange,
                        selectedLabelColor = Color.White,
                        containerColor = GroqSurfaceVariant,
                        labelColor = Color.White
                    )
                )
            }

            Spacer(Modifier.height(10.dp))

            // New session buttons
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.startNewSession("Chat") },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, GroqOrange),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GroqOrange),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nuevo Chat", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { viewModel.startNewSession("Agente") },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, GroqOrange),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GroqOrange),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nuevo Agente", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = GroqOutline)

            val sessions = if (filter == "Chat") chatSessions else agentSessions
            if (sessions.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Sin sesiones aún", color = GroqTextSecondary, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            dateLabel = dateFmt.format(Date(session.timestamp)),
                            onClick = {
                                if (filter == "Agente" || session.sessionType == "Agente") {
                                    viewModel.selectAgentSession(session.id)
                                } else {
                                    viewModel.selectChatSession(session.id)
                                }
                            },
                            onDelete = { viewModel.deleteSession(session.id) }
                        )
                    }
                }
            }
        }
        } // Card
    } // outer Box
}

@Composable
fun SessionRow(
    session: ChatSession,
    dateLabel: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(GroqSurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (session.sessionType == "Agente") Icons.Default.SmartToy else Icons.Default.ChatBubbleOutline,
            contentDescription = null,
            tint = GroqOrange,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                session.title,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "$dateLabel · ${session.model}",
                color = GroqTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════
// CHAT TAB
// ═══════════════════════════════════════════════════════════

@Composable
fun ChatTab(
    viewModel: ZaiViewModel,
    onPickFile: () -> Unit,
    onStartDictation: ((String) -> Unit) -> Unit
) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isGen by viewModel.isGenerating.collectAsStateWithLifecycle()
    val loadMsg by viewModel.loadingMessage.collectAsStateWithLifecycle()
    val pendingFiles by viewModel.pendingFiles.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isGen) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1 + if (isGen) 1 else 0)
        }
    }

    Column(Modifier.fillMaxSize()) {
        FileChipsRow(pendingFiles) { idx -> viewModel.removePendingFile(idx) }

        Box(Modifier.weight(1f)) {
            if (messages.isEmpty() && !isGen) {
                EmptyState(
                    icon = Icons.Default.Chat,
                    title = "Conversación Directa",
                    subtitle = "Escribe un mensaje o adjunta archivos para empezar"
                )
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
                        item {
                            LoadingRow(loadMsg.ifBlank { "Pensando..." })
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
                    viewModel.sendChatMessage(text)
                    text = ""
                }
            },
            isGen = isGen,
            hasText = text.isNotBlank() || pendingFiles.isNotEmpty(),
            onAttach = onPickFile,
            onDictate = {
                onStartDictation { spoken ->
                    text = if (text.isBlank()) spoken else "$text $spoken"
                }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════
// AGENT TAB
// ═══════════════════════════════════════════════════════════

@Composable
fun AgentTab(
    viewModel: ZaiViewModel,
    onPickFile: () -> Unit,
    onStartDictation: ((String) -> Unit) -> Unit
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

    // Python execution state
    var showPython by remember { mutableStateOf(false) }
    var pythonCode by remember { mutableStateOf("") }
    var pythonResult by remember { mutableStateOf("") }

    LaunchedEffect(messages.size, isGen, steps.size) {
        val target = messages.size + if (isGen) 1 else 0
        if (target > 0) listState.animateScrollToItem(target - 1)
    }

    // Auto-run python when code is detected
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

    // Also detect python in latest assistant message if not already shown
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
        // Agent-specific header strip
        Surface(color = GroqSurfaceVariant.copy(alpha = 0.5f)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.SmartToy, null, tint = GroqOrange, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Modo Agente · pasos de razonamiento ${if (reasoning) "ON" else "OFF"}",
                    color = GroqTextSecondary,
                    fontSize = 11.sp
                )
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
                                Toast.makeText(context, "No hay archivos para exportar", Toast.LENGTH_SHORT).show()
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

        // Sandbox files chips (download individual)
        if (sandboxFiles.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
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
                        label = {
                            Text(file.name, fontSize = 11.sp, maxLines = 1)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = GroqSurface,
                            labelColor = Color.White,
                            leadingIconContentColor = GroqOrange
                        ),
                        border = BorderStroke(1.dp, GroqOutline)
                    )
                }
            }
        }

        Box(Modifier.weight(1f)) {
            if (messages.isEmpty() && !isGen) {
                EmptyState(
                    icon = Icons.Default.SmartToy,
                    title = "Agente Autónomo",
                    subtitle = "Describe una tarea compleja. El agente razona y puede ejecutar Python."
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        if (msg.role == "assistant") {
                            AgentMessageCard(msg)
                        } else {
                            ChatBubble(msg)
                        }
                    }
                    if (isGen) {
                        item {
                            if (reasoning && steps.isNotEmpty()) {
                                ThinkingCard(steps, loadMsg)
                            } else {
                                LoadingRow(loadMsg.ifBlank { "Agente trabajando..." })
                            }
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
            onDictate = {
                onStartDictation { spoken ->
                    text = if (text.isBlank()) spoken else "$text $spoken"
                }
            }
        )
    }

    if (showPython) {
        PythonSandboxDialog(
            code = pythonCode,
            result = pythonResult,
            onResult = { pythonResult = it },
            onDismiss = { showPython = false }
        )
    }
}

@Composable
fun AgentMessageCard(message: ChatMessage) {
    var expanded by remember { mutableStateOf(true) }
    val steps = message.thinkingSteps
        ?.split("\n")
        ?.filter { it.isNotBlank() }
        .orEmpty()

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(GroqOrange.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.SmartToy, null, tint = GroqOrange, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.fillMaxWidth(0.9f)) {
            if (steps.isNotEmpty()) {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .clickable { expanded = !expanded },
                    colors = CardDefaults.cardColors(containerColor = GroqSurfaceVariant),
                    border = BorderStroke(1.dp, GroqOutline.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Psychology,
                                null,
                                tint = GroqOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Razonamiento (${steps.size} pasos)",
                                color = GroqOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null,
                                tint = GroqTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
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
                Text(
                    message.content,
                    modifier = Modifier.padding(14.dp),
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
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
                CircularProgressIndicator(
                    Modifier.size(16.dp),
                    color = GroqOrange,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    loadMsg.ifBlank { "Agente pensando..." },
                    color = GroqOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            steps.forEachIndexed { i, s ->
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
fun PythonSandboxDialog(
    code: String,
    result: String,
    onResult: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 560.dp),
            colors = CardDefaults.cardColors(containerColor = GroqSurface),
            border = BorderStroke(1.dp, GroqOutline),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, null, tint = GroqOrange)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Python Sandbox (Pyodide)",
                        fontWeight = FontWeight.Bold,
                        color = GroqOrange,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = GroqTextSecondary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Código detectado:", color = GroqTextSecondary, fontSize = 12.sp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GroqBackground)
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(code, color = Color(0xFF86EFAC), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                // WebView that runs real Pyodide (Python in browser)
                AndroidView(
                    factory = { ctx ->
                        SandboxWebView(ctx).also { web ->
                            web.loadPyodide()
                            web.execute(code) { res -> onResult(res) }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GroqBackground)
                )
                Spacer(Modifier.height(8.dp))
                Text("Resultado:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GroqBackground)
                        .border(1.dp, GroqOutline, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        result.ifBlank { "Ejecutando..." },
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = GroqOrange),
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cerrar")
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// SETTINGS
// ═══════════════════════════════════════════════════════════

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

    var provider by remember { mutableStateOf(currentProvider) }
    var url by remember { mutableStateOf(currentUrl) }
    var model by remember { mutableStateOf(currentModel) }
    var key by remember { mutableStateOf(currentKey) }
    var showKey by remember { mutableStateOf(false) }
    var showProviderMenu by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 640.dp),
            colors = CardDefaults.cardColors(containerColor = GroqSurface),
            border = BorderStroke(1.dp, GroqOutline),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, null, tint = GroqOrange)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Configuración",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = GroqTextSecondary)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Provider
                Text("Proveedor", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedTextField(
                        value = provider,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showProviderMenu = true },
                        trailingIcon = {
                            IconButton(onClick = { showProviderMenu = !showProviderMenu }) {
                                Icon(
                                    if (showProviderMenu) Icons.Default.ExpandLess
                                    else Icons.Default.ExpandMore,
                                    null,
                                    tint = GroqOrange
                                )
                            }
                        },
                        colors = outlinedFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    DropdownMenu(
                        expanded = showProviderMenu,
                        onDismissRequest = { showProviderMenu = false },
                        modifier = Modifier.background(GroqSurface)
                    ) {
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
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))
                Text("Modelo", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    placeholder = { Text("llama-3.1-8b-instant", color = GroqTextSecondary) }
                )

                Spacer(Modifier.height(12.dp))
                Text("API Key", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showKey) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                null,
                                tint = GroqTextSecondary
                            )
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showThemeMenu = true },
                        trailingIcon = {
                            IconButton(onClick = { showThemeMenu = !showThemeMenu }) {
                                Icon(Icons.Default.ExpandMore, null, tint = GroqOrange)
                            }
                        },
                        colors = outlinedFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    DropdownMenu(
                        expanded = showThemeMenu,
                        onDismissRequest = { showThemeMenu = false },
                        modifier = Modifier.background(GroqSurface)
                    ) {
                        listOf(
                            ThemeMode.LIGHT to "Claro",
                            ThemeMode.DARK to "Oscuro",
                            ThemeMode.SYSTEM to "Sistema"
                        ).forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label, color = Color.White) },
                                onClick = {
                                    viewModel.saveThemeMode(mode)
                                    showThemeMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            showTestDialog = true
                            viewModel.testConnection(url, key, model)
                        },
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
            onDismissRequest = {
                if (!isTesting) {
                    showTestDialog = false
                    viewModel.clearConnectionTest()
                }
            },
            containerColor = GroqSurface,
            title = {
                Text("Probar conexión", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (isTesting) {
                        CircularProgressIndicator(color = GroqOrange, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(
                        testStatus ?: "Iniciando...",
                        color = Color.White,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTestDialog = false
                        viewModel.clearConnectionTest()
                    },
                    enabled = !isTesting
                ) {
                    Text("Cerrar", color = GroqOrange)
                }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════
// SHARED UI
// ═══════════════════════════════════════════════════════════

@Composable
fun FileChipsRow(files: List<AttachedFile>, onRemove: (Int) -> Unit) {
    if (files.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        files.forEachIndexed { idx, file ->
            InputChip(
                selected = false,
                onClick = { },
                label = {
                    Text(
                        file.name,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 140.dp)
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(14.dp))
                },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Quitar",
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { onRemove(idx) }
                    )
                },
                colors = InputChipDefaults.inputChipColors(
                    containerColor = GroqSurfaceVariant,
                    labelColor = Color.White,
                    leadingIconColor = GroqOrange,
                    trailingIconColor = Color(0xFFEF4444)
                ),
                border = BorderStroke(1.dp, GroqOutline)
            )
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(GroqOrange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SmartToy, null, tint = GroqOrange, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) GroqUserBubble else GroqAssistantBubble
            ),
            border = BorderStroke(1.dp, GroqOutline.copy(alpha = 0.7f)),
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            )
        ) {
            Text(
                message.content,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(GroqSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null, tint = GroqTextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun LoadingRow(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(GroqOrange.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(Modifier.size(16.dp), color = GroqOrange, strokeWidth = 2.dp)
        }
        Spacer(Modifier.width(10.dp))
        Text(message, color = GroqTextSecondary, fontSize = 13.sp)
    }
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(GroqOrange.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = GroqOrange, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            color = GroqTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        )
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
    onDictate: () -> Unit
) {
    Surface(
        Modifier.fillMaxWidth(),
        color = GroqSurface,
        border = BorderStroke(1.dp, GroqOutline.copy(alpha = 0.5f)),
        shadowElevation = 4.dp
    ) {
        Row(
            Modifier
                .padding(horizontal = 6.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttach, enabled = !isGen) {
                Icon(Icons.Default.AttachFile, contentDescription = "Adjuntar", tint = GroqTextSecondary)
            }
            TextField(
                value = value,
                onValueChange = onValue,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Mensaje...", color = GroqTextSecondary) },
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
            IconButton(onClick = onDictate, enabled = !isGen) {
                Icon(Icons.Default.Mic, contentDescription = "Dictar", tint = GroqOrange)
            }
            IconButton(
                onClick = onSend,
                enabled = !isGen && hasText
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar",
                    tint = if (!isGen && hasText) GroqOrange else GroqOutline
                )
            }
        }
    }
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
