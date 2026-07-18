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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.ui.Logos
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ZaiViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val primaryColorInt by viewModel.primaryColor.collectAsStateWithLifecycle()
            val bgUri by viewModel.backgroundImageUri.collectAsStateWithLifecycle()
            val bgTransparency by viewModel.backgroundTransparency.collectAsStateWithLifecycle()

            MyApplicationTheme(
                themeMode = themeMode,
                primaryColor = Color(primaryColorInt)
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (bgUri.isNotEmpty()) {
                        AsyncImage(
                            model = if (bgUri.startsWith("/")) File(bgUri) else bgUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().alpha(bgTransparency),
                            contentScale = ContentScale.Crop
                        )
                    }
                    MainApp(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: ZaiViewModel = viewModel()) {
    val onboardingDone by viewModel.onboardingCompleted.collectAsStateWithLifecycle()
    var showOnboarding by remember(onboardingDone) { mutableStateOf(!onboardingDone) }
    val hasBackground by viewModel.backgroundImageUri.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()
    val apiError by viewModel.apiError.collectAsStateWithLifecycle()
    val needsReauthForDeletion by viewModel.accountDeletionNeedsReauth.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var showToolsSheet by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showConectores by remember { mutableStateOf(false) }
    var showGitHubTools by remember { mutableStateOf(false) }

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

    if (needsReauthForDeletion) {
        AlertDialog(
            onDismissRequest = { viewModel.clearAccountDeletionReauthFlag() },
            title = { Text("Vuelve a iniciar sesión", fontWeight = FontWeight.Bold) },
            text = {
                Text("Por seguridad, Firebase requiere que hayas iniciado sesión recientemente antes de eliminar tu cuenta. Cierra sesión y vuelve a iniciarla para intentarlo de nuevo.")
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearAccountDeletionReauthFlag()
                    viewModel.logout()
                    showOnboarding = true
                }) {
                    Text("Cerrar sesión ahora")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearAccountDeletionReauthFlag() }) {
                    Text("Cancelar")
                }
            }
        )
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
                containerColor = if (hasBackground.isNotEmpty()) Color.Transparent else MaterialTheme.colorScheme.background,
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
                        .background(if (hasBackground.isNotEmpty()) Color.Transparent else MaterialTheme.colorScheme.background)
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
                    onShowGitHub = {
                        showToolsSheet = false
                        showGitHubTools = true
                    },
                    onExport = {
                        val uri = viewModel.exportConversationToFile()
                        if (uri == null) {
                            Toast.makeText(context, "Nada para exportar", Toast.LENGTH_SHORT).show()
                        } else {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/markdown"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Exportar Chat (Markdown)"))
                            viewModel.logAction("Exportar", "Conversación exportada a archivo Markdown.")
                        }
                        showToolsSheet = false
                    }
                )
            }
        }
    }

    if (showSettings) {
        SettingsScreen(viewModel = viewModel, onDismiss = { viewModel.closeSettings() })
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
    if (showGitHubTools) {
        GitHubToolsDialog(onDismiss = { showGitHubTools = false })
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
    val actionLogs by viewModel.actionLogs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()) }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
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
                Text("GroqApp", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onCloseDrawer) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        containerColor = if (chatSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (chatSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = if (chatSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Default.Chat, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("A CHAT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Button(
                    onClick = { viewModel.setCurrentTab("Agente"); onCloseDrawer() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (agenteSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (agenteSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = if (agenteSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Default.SmartToy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("AGENTE", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(20.dp))
            // Search toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HISTORIAL", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showSearch = !showSearch }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            if (showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar sesiones...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
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
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(6.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nuevo Chat", color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp)
                }
                OutlinedButton(
                    onClick = { viewModel.startNewSession("Agente") },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(6.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nuevo Agente", color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(8.dp))

            val sessionsToShow by remember(chatSessions, agentSessions, searchQuery, currentTab) {
                derivedStateOf {
                    val filtered = if (currentTab == "Chat") {
                        chatSessions.filter { it.title.contains(searchQuery, true) || searchQuery.isBlank() }
                    } else {
                        agentSessions.filter { it.title.contains(searchQuery, true) || searchQuery.isBlank() }
                    }
                    filtered
                }
            }
            val label = if (currentTab == "Chat") "Chat" else "Agente"

            if (sessionsToShow.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Sin sesiones de $label", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Icon(icon, null, tint = if (isDestructive) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = if (isDestructive) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SessionDrawerRow(session: ChatSession, dateLabel: String, isSelected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (session.sessionType == "Agente") Icons.Default.SmartToy else Icons.Default.ChatBubbleOutline,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(session.title.ifBlank { "Sin título" }, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text("$dateLabel · ${session.model}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
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
                Icon(Icons.Default.Menu, contentDescription = "Menú", tint = MaterialTheme.colorScheme.onSurface)
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
                    Text("GROQ", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("GroqApp", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(6.dp))
                    Text(selectedModel, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(currentTitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onHelpClick) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
// ONBOARDING (REDESIGNED)
// ═══════════════════════════════════════════

@Composable
fun ModernRobotMascot(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "mascot")
    
    // Hovering effect
    val translateY by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = androidx.compose.animation.core.EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hover"
    )
    
    // Glowing pulse alpha
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = androidx.compose.animation.core.EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Eye blinking
    val eyeScaleY by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3800
                1.0f at 0
                1.0f at 3600
                0.1f at 3700
                1.0f at 3800
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )

    Box(
        modifier = modifier
            .height(130.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Glowing background behind the head
        Box(
            Modifier
                .size(100.dp)
                .graphicsLayer {
                    scaleX = glowScale
                    scaleY = glowScale
                    alpha = 0.2f
                }
                .background(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary, Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        // The Floating Head
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                translationY = translateY
            }
        ) {
            // Little Antenna
            Box(
                Modifier
                    .size(5.dp, 14.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
            )
            Box(
                Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )

            Spacer(Modifier.height(3.dp))

            // Face Capsule
            Box(
                modifier = Modifier
                    .size(95.dp, 70.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(Color(0xFF27272A), Color(0xFF18181B))
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .border(1.5.dp, Color(0xFF52525B), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Glass-like screen inside the face
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(23.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(23.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Glowing Blue/Orange eyes
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Eye
                        Box(
                            Modifier
                                .size(13.dp, 13.dp)
                                .graphicsLayer {
                                    scaleY = eyeScaleY
                                }
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Box(
                                Modifier
                                    .size(3.dp)
                                    .align(Alignment.TopEnd)
                                    .padding(top = 1.dp, end = 1.dp)
                                    .background(Color.White, CircleShape)
                            )
                        }

                        // Right Eye
                        Box(
                            Modifier
                                .size(13.dp, 13.dp)
                                .graphicsLayer {
                                    scaleY = eyeScaleY
                                }
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Box(
                                Modifier
                                    .size(3.dp)
                                    .align(Alignment.TopEnd)
                                    .padding(top = 1.dp, end = 1.dp)
                                    .background(Color.White, CircleShape)
                            )
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(Modifier.size(6.dp, 3.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape))
                        Box(Modifier.size(6.dp, 3.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
fun MascotSpeechBubble(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp).padding(top = 2.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ShimmerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val gradientBrush = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.primary,
        ),
        start = androidx.compose.ui.geometry.Offset(shimmerTranslate - 250f, shimmerTranslate - 250f),
        end = androidx.compose.ui.geometry.Offset(shimmerTranslate + 250f, shimmerTranslate + 250f)
    )

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(),
        modifier = modifier
            .background(gradientBrush, RoundedCornerShape(14.dp))
            .shadow(4.dp, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun OnboardingProgressBar(currentStep: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (currentStep) {
                    0 -> "Fase 1: Bienvenida"
                    1 -> "Fase 2: Guía Rápida"
                    else -> "Fase 3: Configuración"
                },
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "${currentStep + 1} de 3",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0..2) {
                val isActive = i <= currentStep
                val barWeight = if (i == currentStep) 1.5f else 1.0f
                val color by animateColorAsState(
                    targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    animationSpec = tween(400),
                    label = "segmentColor"
                )
                
                Box(
                    modifier = Modifier
                        .weight(barWeight)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(viewModel: ZaiViewModel, onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    var selectedProvider by remember { mutableStateOf("Groq") }
    var showProviderDropdown by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf(viewModel.defaultModels["Groq"] ?: "llama-3.1-8b-instant") }
    var showModelDropdown by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isRegisterMode by remember { mutableStateOf(false) }
    var isForgotPasswordMode by remember { mutableStateOf(false) }
    val authLoading by viewModel.authLoading.collectAsStateWithLifecycle()
    val authProvider by viewModel.authProvider.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .shadow(8.dp, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top control: Skip button available on all screens except login (step 0),
                // so authentication can no longer be bypassed as a guest.
                if (step != 0) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        TextButton(
                            onClick = {
                                viewModel.completeOnboarding()
                                onFinish()
                            }
                        ) {
                            Text(
                                text = "Saltar",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                OnboardingProgressBar(currentStep = step)

                Spacer(Modifier.height(8.dp))

                Crossfade(targetState = step, label = "onboardingStep") { currentStep ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (currentStep) {
                            0 -> {
                                ModernRobotMascot()
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Bienvenido",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 28.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Inicia sesión o regístrate para continuar",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.height(24.dp))
                                
                                // Google
                                OutlinedButton(
                                    onClick = {
                                        viewModel.signInWithGoogle(context)
                                    },
                                    enabled = authProvider == null,
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                        .focusProperties { canFocus = false },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (authProvider == "google") {
                                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        androidx.compose.foundation.Image(painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_google), contentDescription = "Google", modifier = Modifier.size(24.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Text("Continuar con Google", color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                // GitHub
                                OutlinedButton(
                                    onClick = {
                                        viewModel.signInWithGitHub(context as Activity)
                                    },
                                    enabled = authProvider == null,
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                        .focusProperties { canFocus = false },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (authProvider == "github") {
                                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        androidx.compose.foundation.Image(painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_github), contentDescription = "GitHub", modifier = Modifier.size(24.dp), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
                                        Spacer(Modifier.width(12.dp))
                                        Text("Continuar con GitHub", color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                // Email
                                OutlinedButton(
                                    onClick = { showLoginDialog = true },
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                        .focusProperties { canFocus = false },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Email, "Email", tint = MaterialTheme.colorScheme.onSurface)
                                    Spacer(Modifier.width(12.dp))
                                    Text("Continuar con Correo", color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            1 -> {
                                ModernRobotMascot()
                                Spacer(Modifier.height(12.dp))
                                
                                Text(
                                    "GroqApp",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 32.sp,
                                    letterSpacing = 1.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Inteligencia autónoma a velocidad de la luz",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                                
                                Spacer(Modifier.height(12.dp))
                                MascotSpeechBubble(
                                    text = "¡Hola! Soy Groqy, tu nuevo asistente inteligente autónomo. Estoy listo para ayudarte a crear y automatizar lo que imagines a una velocidad impresionante. ¡Comencemos!"
                                )

                                Spacer(Modifier.height(24.dp))
                                ShimmerButton(
                                    text = "Comenzar",
                                    onClick = { step = 2 },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            2 -> {
                                ModernRobotMascot()
                                Spacer(Modifier.height(8.dp))
                                
                                Text(
                                    "Explora tus herramientas",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Descubre todo el poder que tienes a tu alcance",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                
                                Spacer(Modifier.height(14.dp))
                                TutorialCard(
                                    icon = Icons.Default.Chat,
                                    title = "Chat Inteligente",
                                    text = "Habla de forma fluida y natural. El asistente responde al instante integrando código de alta velocidad.",
                                    iconColor = MaterialTheme.colorScheme.primary
                                )
                                TutorialCard(
                                    icon = Icons.Default.SmartToy,
                                    title = "Agente de Razonamiento",
                                    text = "Tareas complejas resueltas con pasos lógicos transparentes y ejecución automática de Python en tiempo real.",
                                    iconColor = Color(0xFF3B82F6)
                                )
                                TutorialCard(
                                    icon = Icons.Default.Build,
                                    title = "Caja de Herramientas",
                                    text = "Optimiza prompts con un toque, busca en la web, lee con dictado de voz, exporta y regula la creatividad.",
                                    iconColor = Color(0xFF10B981)
                                )
                                TutorialCard(
                                    icon = Icons.Default.AttachFile,
                                    title = "Administrador de Archivos",
                                    text = "Adjunta múltiples documentos y códigos fuente sin límites. Descarga tus proyectos terminados en ZIP.",
                                    iconColor = Color(0xFFF59E0B)
                                )

                                Spacer(Modifier.height(20.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { step = 0 }) {
                                        Text("Atrás", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { step = 3 },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                                    ) {
                                        Text("Siguiente", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            3 -> {
                                Text(
                                    "Configura tu Proveedor",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Elige un servicio de IA para activar las respuestas",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                
                                Spacer(Modifier.height(14.dp))
                                MascotSpeechBubble(
                                    text = "¡Ya casi estamos! Selecciona tu proveedor preferido e ingresa tu clave de API. Si usas Ollama local, la clave de API es totalmente opcional."
                                )
                                
                                Spacer(Modifier.height(16.dp))
                                
                                Text("Proveedor", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start))
                                Spacer(Modifier.height(6.dp))
                                ExposedDropdownMenuBox(
                                    expanded = showProviderDropdown,
                                    onExpandedChange = { showProviderDropdown = !showProviderDropdown },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = selectedProvider,
                                        onValueChange = {},
                                        readOnly = true,
                                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                                        trailingIcon = {
                                            Icon(if (showProviderDropdown) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
                                        },
                                        colors = outlinedFieldColors(),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = showProviderDropdown,
                                        onDismissRequest = { showProviderDropdown = false },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                    ) {
                                        viewModel.providers.keys.forEach { p ->
                                            DropdownMenuItem(
                                                text = { Text(p, color = MaterialTheme.colorScheme.onSurface) },
                                                onClick = {
                                                    selectedProvider = p
                                                    viewModel.setProvider(p)
                                                    model = viewModel.defaultModels[p] ?: model
                                                    viewModel.saveSelectedModel(model)
                                                    showProviderDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(12.dp))
                                Text("Modelo de IA", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start))
                                Spacer(Modifier.height(6.dp))
                                val modelList = viewModel.getFallbackModels(selectedProvider)
                                if (modelList.isNotEmpty()) {
                                    ExposedDropdownMenuBox(
                                        expanded = showModelDropdown,
                                        onExpandedChange = { showModelDropdown = !showModelDropdown },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            value = model,
                                            onValueChange = { model = it },
                                            readOnly = true,
                                            leadingIcon = { ModelBadge(model) },
                                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                                            trailingIcon = {
                                                Icon(if (showModelDropdown) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
                                            },
                                            colors = outlinedFieldColors(),
                                            shape = RoundedCornerShape(12.dp),
                                            placeholder = { Text("Selecciona un modelo") }
                                        )
                                        ExposedDropdownMenu(
                                            expanded = showModelDropdown,
                                            onDismissRequest = { showModelDropdown = false },
                                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                        ) {
                                            modelList.forEach { m ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            ModelBadge(m, Modifier.size(20.dp))
                                                            Spacer(Modifier.width(10.dp))
                                                            Text(m, color = MaterialTheme.colorScheme.onSurface)
                                                        }
                                                    },
                                                    onClick = {
                                                        model = m
                                                        viewModel.saveSelectedModel(m)
                                                        showModelDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    OutlinedTextField(
                                        value = model,
                                        onValueChange = { 
                                            model = it
                                            viewModel.saveSelectedModel(it)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = outlinedFieldColors(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                }

                                Spacer(Modifier.height(12.dp))
                                val keyLabel = if (selectedProvider == "Ollama") "Clave de API (opcional para Ollama)" else "Clave de API"
                                Text(keyLabel, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start))
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = apiKey,
                                    onValueChange = { apiKey = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showKey = !showKey }) {
                                            Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    placeholder = { Text("gsk_...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
                                    Text("¿No tienes clave? Obtener aquí", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(Modifier.height(20.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { step = 1 }) {
                                        Text("Atrás", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.setProvider(selectedProvider)
                                            viewModel.saveApiKey(apiKey)
                                            if (model.isNotBlank()) viewModel.saveSelectedModel(model)
                                            viewModel.completeOnboarding()
                                            onFinish()
                                        },
                                        enabled = if (selectedProvider == "Ollama") true else apiKey.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                                    ) {
                                        Text("Finalizar y guardar", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLoginDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            title = {
                Text(
                    when {
                        isForgotPasswordMode -> "Recuperar contraseña"
                        isRegisterMode -> "Crear cuenta"
                        else -> "Iniciar sesión"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        when {
                            isForgotPasswordMode -> "Ingresa tu correo y te enviaremos un enlace para restablecer tu contraseña."
                            isRegisterMode -> "Crea tu cuenta con correo y contraseña."
                            else -> "Ingresa tu correo y contraseña para continuar."
                        },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Correo electrónico") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (!isForgotPasswordMode) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Contraseña") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (!isRegisterMode && !isForgotPasswordMode) {
                        TextButton(onClick = { isForgotPasswordMode = true }) {
                            Text("¿Olvidaste tu contraseña?", fontSize = 13.sp)
                        }
                    }
                    TextButton(
                        onClick = {
                            when {
                                isForgotPasswordMode -> isForgotPasswordMode = false
                                else -> isRegisterMode = !isRegisterMode
                            }
                        }
                    ) {
                        Text(
                            when {
                                isForgotPasswordMode -> "Volver a iniciar sesión"
                                isRegisterMode -> "¿Ya tienes cuenta? Inicia sesión"
                                else -> "¿No tienes cuenta? Regístrate"
                            },
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            isForgotPasswordMode -> {
                                // El envío del enlace no inicia sesión: cerramos el
                                // diálogo y el resultado se muestra mediante Toast (apiError).
                                viewModel.sendPasswordReset(emailInput)
                                showLoginDialog = false
                                emailInput = ""
                                passwordInput = ""
                                isRegisterMode = false
                                isForgotPasswordMode = false
                            }
                            isRegisterMode -> {
                                // No cerramos el diálogo aquí: si el registro falla
                                // (validación o Firebase) el usuario conserva sus datos
                                // y ve el error. Al completarse con éxito, el estado
                                // onboardingCompleted retira toda la pantalla de onboarding
                                // (y con ella este diálogo).
                                viewModel.registerWithEmail(emailInput, passwordInput)
                            }
                            else -> {
                                // Igual que el registro: el diálogo permanece abierto
                                // mientras carga y ante un error; el éxito lo cierra
                                // automáticamente al avanzar el onboarding.
                                viewModel.signInWithEmail(emailInput, passwordInput)
                            }
                        }
                    },
                    enabled = !authLoading,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (authLoading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(
                            when {
                                isForgotPasswordMode -> "Enviar enlace"
                                isRegisterMode -> "Crear cuenta"
                                else -> "Continuar"
                            }
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLoginDialog = false
                    emailInput = ""
                    passwordInput = ""
                    isRegisterMode = false
                    isForgotPasswordMode = false
                }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun TutorialCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
    iconColor: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
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

        // La lista se extrae a su propio composable para que quede en un ámbito de
        // recomposición separado del campo de texto: al escribir, ChatTab recompone
        // pero ChatMessagesList se salta (sus parámetros no cambian).
        Box(Modifier.weight(1f)) {
            ChatMessagesList(messages, isGen, loadMsg, listState)
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
private fun ChatMessagesList(
    messages: List<ChatMessage>,
    isGen: Boolean,
    loadMsg: String,
    listState: LazyListState
) {
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

@Composable
fun EmptyChatState() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ChatBubbleOutline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(14.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text("Modo chat", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Escribe un mensaje o adjunta archivos", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FractalIcon(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("Modo Agente · razonamiento ${if (reasoning) "ON" else "OFF"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
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
                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("ZIP", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
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
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface, labelColor = MaterialTheme.colorScheme.onSurface, leadingIconContentColor = MaterialTheme.colorScheme.primary),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    )
                }
            }
        }

        // Lista extraída a su propio composable (ver ChatMessagesList) para aislar
        // la recomposición del campo de texto.
        Box(Modifier.weight(1f)) {
            AgentMessagesList(messages, isGen, reasoning, steps, loadMsg, listState)
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
private fun AgentMessagesList(
    messages: List<ChatMessage>,
    isGen: Boolean,
    reasoning: Boolean,
    steps: List<String>,
    loadMsg: String,
    listState: LazyListState
) {
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

@Composable
fun EmptyAgentState() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            FractalIcon(modifier = Modifier.size(40.dp), color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(14.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text("Modo agente", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Describe una tarea compleja", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
        Text("El agente razona y puede ejecutar Python real", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
    }
}

@Composable
fun FractalIcon(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
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
            Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SmartToy, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
            shape = RoundedCornerShape(topStart = if (isUser) 16.dp else 4.dp, topEnd = if (isUser) 4.dp else 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
        ) {
            Text(
                message.content, 
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), 
                color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface, 
                fontSize = 14.sp, 
                lineHeight = 20.sp
            )
        }
        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun AgentMessageCard(message: ChatMessage) {
    var expanded by remember { mutableStateOf(true) }
    val steps = remember(message.thinkingSteps) {
        message.thinkingSteps?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.SmartToy, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.fillMaxWidth(0.9f)) {
            if (steps.isNotEmpty()) {
                Card(
                    Modifier.fillMaxWidth().animateContentSize().clickable { expanded = !expanded },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Razonamiento (${steps.size} pasos)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        if (expanded) {
                            Spacer(Modifier.height(8.dp))
                            steps.forEachIndexed { i, s ->
                                Row(Modifier.padding(vertical = 2.dp)) {
                                    Text("${i + 1}.", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(6.dp))
                                    Text(s, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
            ) {
                Text(message.content, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
fun ThinkingCard(steps: List<String>, loadMsg: String) {
    Card(
        Modifier.fillMaxWidth(0.9f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(loadMsg.ifBlank { "Agente pensando..." }, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            steps.forEach { s ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(s, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun LoadingRow(message: String) {
    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
        }
        Spacer(Modifier.width(10.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
fun PythonSandboxDialog(code: String, result: String, onResult: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 560.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Python Sandbox (Pyodide Real)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(Modifier.height(8.dp))
                Text("Código detectado:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Box(
                    Modifier.fillMaxWidth().heightIn(max = 100.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(code, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                val context = LocalContext.current
                val webView = remember {
                    SandboxWebView(context).also { web ->
                        web.loadPyodide()
                    }
                }

                LaunchedEffect(code) {
                    webView.execute(code) { res -> onResult(res) }
                }

                AndroidView(
                    factory = { webView },
                    // Libera el WebView (motor JS + runtime de Pyodide) al cerrar el diálogo.
                    // Sin esto, el WebView queda en RAM tras cerrarse: fuga de memoria seria
                    // en teléfonos de gama baja.
                    onRelease = { web ->
                        web.stopLoading()
                        web.destroy()
                    },
                    modifier = Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.height(8.dp))
                Text("Resultado:", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 200.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)).padding(10.dp).verticalScroll(rememberScrollState())
                ) {
                    Text(result.ifBlank { "Ejecutando..." }, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), modifier = Modifier.align(Alignment.End), shape = RoundedCornerShape(10.dp)) {
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
    onShowGitHub: () -> Unit,
    onExport: () -> Unit
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val reasoning by viewModel.reasoningEnabled.collectAsStateWithLifecycle()
    val webSearch by viewModel.webSearchEnabled.collectAsStateWithLifecycle()
    val tts by viewModel.ttsEnabled.collectAsStateWithLifecycle()
    val creativity by viewModel.creativity.collectAsStateWithLifecycle()
    
    val availableVoices by viewModel.availableVoices.collectAsStateWithLifecycle()
    val selectedVoiceName by viewModel.selectedVoiceName.collectAsStateWithLifecycle()
    var showVoiceMenu by remember { mutableStateOf(false) }
    
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).navigationBarsPadding()) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline))
            Spacer(Modifier.height(12.dp))
            Text(if (currentTab == "Chat") "Herramientas Chat" else "Herramientas Agente", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))

            // Pensar
            ToolsSwitchRow("Pensar", "Razonamiento paso a paso", reasoning) { viewModel.setReasoningEnabled(it) }
            // Buscar web
            ToolsSwitchRow("Buscar web", "Información actualizada", webSearch) { viewModel.setWebSearchEnabled(it) }

            if (currentTab == "Chat") {
                ToolsSwitchRow("Lector de voz", "Lee respuestas en voz", tts) {
                    viewModel.setTtsEnabled(it)
                    if (!it) viewModel.stopSpeaking()
                }

                if (tts && availableVoices.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Voz del lector", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Box {
                        OutlinedCard(
                            onClick = { showVoiceMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.RecordVoiceOver, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = selectedVoiceName ?: "Predeterminada",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        DropdownMenu(
                            expanded = showVoiceMenu,
                            onDismissRequest = { showVoiceMenu = false },
                            modifier = Modifier.fillMaxWidth(0.8f).background(MaterialTheme.colorScheme.surface)
                        ) {
                            availableVoices.forEach { voice ->
                                DropdownMenuItem(
                                    text = { Text(voice.name, fontSize = 12.sp) },
                                    onClick = {
                                        viewModel.setTtsVoice(voice.name)
                                        showVoiceMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Spacer(Modifier.height(12.dp))

                ToolsNavigationRow("Conectores", "APIs, Drive, Slack, etc", icon = Icons.Default.Power) { onShowConectores() }
                Spacer(Modifier.height(8.dp))
                ToolsNavigationRow("GitHub", "Repositorios y código", icon = R.drawable.ic_github) { onShowGitHub() }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.clearCurrentConversation()
                        onDismiss()
                        Toast.makeText(context, "Conversación limpiada", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Limpiar conversación", color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onExport() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Exportar chat", color = MaterialTheme.colorScheme.onSurface)
                }

                Spacer(Modifier.height(16.dp))
                Text("Creatividad: ${String.format("%.1f", creativity)}", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Slider(
                    value = creativity,
                    onValueChange = { viewModel.setCreativity(it) },
                    valueRange = 0f..2f,
                    steps = 19,
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.outline)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Preciso", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    Text("Equilibrado", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    Text("Creativo", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
            } else {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Spacer(Modifier.height(12.dp))

                ToolsNavigationRow("Conectores", "APIs, Drive, Slack, etc", icon = Icons.Default.Power) { onShowConectores() }
                Spacer(Modifier.height(8.dp))
                ToolsNavigationRow("GitHub", "Repositorios y código", icon = R.drawable.ic_github) { onShowGitHub() }

                Spacer(Modifier.height(16.dp))
                Text("Tip: El agente puede crear archivos con ```file:nombre", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
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
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MaterialTheme.colorScheme.primary, uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant, uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

@Composable
fun ToolsNavigationRow(title: String, subtitle: String, icon: Any? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            if (icon is androidx.compose.ui.graphics.vector.ImageVector) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            } else if (icon is Int) {
                val isDarkIcon = icon == R.drawable.ic_github || icon == R.drawable.ic_notion
                val colorFilter = if (isDarkIcon) ColorFilter.tint(MaterialTheme.colorScheme.onSurface) else null
                androidx.compose.foundation.Image(painter = androidx.compose.ui.res.painterResource(id = icon), contentDescription = null, modifier = Modifier.size(24.dp), colorFilter = colorFilter)
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CursorBottomSheet(onDismiss: () -> Unit, onSelectTemplate: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding()) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline))
            Spacer(Modifier.height(12.dp))
            Text("Mejorar Prompt (Cursor I)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(content.take(40) + "...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ZaiViewModel, onDismiss: () -> Unit) {
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
    var showModelMenu by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf(currentKey) }
    var showKey by remember { mutableStateOf(false) }
    var showProviderMenu by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }
    var showPersonalizationDialog by remember { mutableStateOf(false) }
    var currentSettingsScreen by remember { mutableStateOf("MAIN") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(48.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface) }
                Spacer(Modifier.width(8.dp))
                Text("Configuración", fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(32.dp))
            Text("Proveedor de IA", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            ExposedDropdownMenuBox(
                expanded = showProviderMenu,
                onExpandedChange = { showProviderMenu = !showProviderMenu },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = provider,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = {
                        Icon(if (showProviderMenu) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    colors = outlinedFieldColors(),
                    shape = RoundedCornerShape(16.dp)
                )
                ExposedDropdownMenu(
                    expanded = showProviderMenu,
                    onDismissRequest = { showProviderMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    viewModel.providers.keys.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                provider = p
                                url = viewModel.providers[p] ?: url
                                model = viewModel.defaultModels[p] ?: model
                                viewModel.saveSelectedModel(model)
                                showProviderMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("URL del Servidor", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedFieldColors(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                placeholder = { Text("http://localhost:11434/v1") }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Modelo Predeterminado", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                
                val isLoadingModels by viewModel.isLoadingModels.collectAsStateWithLifecycle()
                val isFetchEnabled = provider == "Ollama" || key.isNotBlank()
                if (isFetchEnabled) {
                    TextButton(
                        onClick = { viewModel.loadModelsFromApi(url, key, provider) },
                        enabled = !isLoadingModels,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        if (isLoadingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                            Spacer(Modifier.width(4.dp))
                            Text("Cargando...", fontSize = 11.sp)
                        } else {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Actualizar desde API", fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            val availableModelsList by viewModel.availableModels.collectAsStateWithLifecycle()
            val modelsLoadError by viewModel.modelsLoadError.collectAsStateWithLifecycle()

            ExposedDropdownMenuBox(
                expanded = showModelMenu,
                onExpandedChange = { showModelMenu = !showModelMenu },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = model,
                    onValueChange = { 
                        model = it
                        viewModel.saveSelectedModel(it)
                    },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = {
                        Icon(if (showModelMenu) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    colors = outlinedFieldColors(),
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("Escribe o selecciona un modelo") }
                )
                if (availableModelsList.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = showModelMenu,
                        onDismissRequest = { showModelMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        val filteredModels = availableModelsList.filter { 
                            it.contains(model, ignoreCase = true) 
                        }
                        val listToShow = if (filteredModels.isNotEmpty()) filteredModels else availableModelsList
                        listToShow.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m, color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    model = m
                                    viewModel.saveSelectedModel(m)
                                    showModelMenu = false
                                }
                            )
                        }
                    }
                }
            }
            if (!modelsLoadError.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = modelsLoadError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
            val keyLabel = if (provider == "Ollama") "API Key (opcional para Ollama)" else "Clave de API (API Key)"
            Text(keyLabel, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        IconButton(onClick = {
                            try {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val text = clip.getItemAt(0).text
                                    if (text != null) {
                                        key = text.toString()
                                        Toast.makeText(context, "Pegado desde el portapapeles", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "El portapapeles está vacío", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error al pegar", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, "Pegar", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = outlinedFieldColors(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            Spacer(Modifier.height(24.dp))
            Text("Apariencia y Estilo", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            
            // Theme selector redesigned
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.LIGHT to ("Claro" to Icons.Default.WbSunny),
                    ThemeMode.DARK to ("Oscuro" to Icons.Default.NightsStay),
                    ThemeMode.SYSTEM to ("Auto" to Icons.Default.BrightnessAuto)
                ).forEach { (mode, pair) ->
                    val (label, icon) = pair
                    val isSelected = themeMode == mode
                    Card(
                        modifier = Modifier.weight(1f).height(60.dp).clickable { viewModel.saveThemeMode(mode) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(icon, null, modifier = Modifier.size(20.dp), tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(label, fontSize = 11.sp, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showPersonalizationDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Palette, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Personalizar Colores y Fondo", color = MaterialTheme.colorScheme.onSurface)
            }
            
            Text("Datos y Almacenamiento", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.clearChatHistory(); Toast.makeText(context, "Historial borrado", Toast.LENGTH_SHORT).show() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text("Borrar Chat", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                }
                Button(onClick = { viewModel.clearLogs(); Toast.makeText(context, "Logs borrados", Toast.LENGTH_SHORT).show() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text("Borrar Logs", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                }
                Button(onClick = { viewModel.clearCache(); Toast.makeText(context, "Caché limpiada", Toast.LENGTH_SHORT).show() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text("Limpiar Caché", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                }
            }
            
            Spacer(Modifier.height(32.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(Modifier.height(20.dp))
            
            Text("Acerca de", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text("GroqApp v1.0.2 - Potenciado por Jetpack Compose y Groq Cloud API.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text("Una aplicación nativa de alto rendimiento con Room DB y Pyodide Sandbox.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(Modifier.height(40.dp))
            val isSaveEnabled = if (provider == "Ollama") true else key.isNotBlank()
            Row(Modifier.fillMaxWidth().navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { showTestDialog = true; viewModel.testConnection(url, key, model, provider) },
                    enabled = isSaveEnabled,
                    modifier = Modifier.weight(1f).height(56.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.NetworkCheck, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Probar", fontSize = 14.sp)
                }
                Button(
                    onClick = {
                        viewModel.saveAllSettings(provider, url, model, key)
                        Toast.makeText(context, "Configuración guardada con éxito", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    enabled = isSaveEnabled,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Guardar", fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (showPersonalizationDialog) {
        PersonalizationScreen(
            viewModel = viewModel,
            onBack = { showPersonalizationDialog = false },
            onDismiss = { showPersonalizationDialog = false }
        )
    }

    if (showTestDialog) {
        AlertDialog(
            onDismissRequest = { if (!isTesting) { showTestDialog = false; viewModel.clearConnectionTest() } },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Probar conexión", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (isTesting) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(testStatus ?: "Iniciando...", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                TextButton(onClick = { showTestDialog = false; viewModel.clearConnectionTest() }, enabled = !isTesting) { Text("Cerrar", color = MaterialTheme.colorScheme.primary) }
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
                colors = InputChipDefaults.inputChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, labelColor = MaterialTheme.colorScheme.onSurface, leadingIconColor = MaterialTheme.colorScheme.primary, trailingIconColor = Color(0xFFEF4444)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            )
        }
    }
}

@Composable
fun PressScaleIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.85f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pressScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
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
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else Color(0xFF52525B),
        animationSpec = tween(durationMillis = 300),
        label = "borderColorTransition"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF18181B),
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PressScaleIconButton(
                    onClick = onAttach,
                    enabled = !isGen,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "Clip - Adjuntar archivo",
                        tint = GroqTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                PressScaleIconButton(
                    onClick = onTools,
                    enabled = !isGen,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = "Llave inglesa - Herramientas",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                PressScaleIconButton(
                    onClick = onCursor,
                    enabled = !isGen,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Default.TextFields,
                        contentDescription = "Cursor I - Mejorar prompt",
                        tint = GroqTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                TextField(
                    value = value,
                    onValueChange = onValue,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focusState ->
                            isFocused = focusState.isFocused
                        },
                    placeholder = { Text("Mensaje...", color = Color(0xFFA1A1AA), fontSize = 14.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    maxLines = 5
                )

                PressScaleIconButton(
                    onClick = onDictate,
                    enabled = !isGen,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Micrófono",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                val sendBg = if (!isGen && hasText) MaterialTheme.colorScheme.primary else Color(0xFF3F3F46)
                val sendTint = if (!isGen && hasText) Color.White else Color(0xFF71717A)

                PressScaleIconButton(
                    onClick = onSend,
                    enabled = !isGen && hasText,
                    modifier = Modifier
                        .size(38.dp)
                        .background(sendBg, CircleShape)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar",
                        tint = sendTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Ayuda - GroqApp", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("• Menú izquierdo: Cambia entre Chat y Agente, historial con fechas, perfil, ajustes, búsqueda, términos, cerrar sesión.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text("• Barra inferior: Clip adjunta archivos múltiples sin límite, llave inglesa abre herramientas, cursor I mejora prompt, mic dictado, enviar.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text("• Herramientas Chat: Pensar, Buscar web, Lector voz, Limpiar, Exportar, Creatividad Slider 0-2.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text("• Herramientas Agente: Pensar, Buscar web, Conectores >, Github >.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text("• Pyodide ejecuta Python real en WebView. Código ```python se auto-ejecuta. Archivos ```file:name.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Entendido", color = MaterialTheme.colorScheme.primary) } }
    )
}

@Composable
fun TermsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Términos y Condiciones", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 300.dp)) {
                Text("Al usar GroqApp aceptas que:\n\n1. La API Key se almacena localmente.\n2. Los mensajes se guardan en Room DB local.\n3. El código Python se ejecuta en sandbox Pyodide aislado.\n4. No somos responsables del contenido generado por modelos externos.\n5. Debes cumplir con los términos de Groq, OpenAI, etc.\n\nContacto: manucarrasquel66@gmail.com", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = MaterialTheme.colorScheme.primary) } }
    )
}

@Composable
fun ProfileDialog(onDismiss: () -> Unit, viewModel: ZaiViewModel) {
    val chatCount by viewModel.chatSessions.collectAsStateWithLifecycle()
    val agentCount by viewModel.agentSessions.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUserEmail.collectAsStateWithLifecycle()
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("PERFIL", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("Usuario GroqApp", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(currentUser, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Text("Sesiones Chat: ${chatCount.size} | Agente: ${agentCount.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Spacer(Modifier.height(20.dp))
                TextButton(onClick = {
                    viewModel.logout()
                    onDismiss()
                }) {
                    Icon(Icons.Default.Logout, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cerrar sesión", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { showDeleteConfirmation = true }) {
                    Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Eliminar cuenta", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = MaterialTheme.colorScheme.primary) } }
    )

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("¿Eliminar tu cuenta?", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Esto elimina tu cuenta de $currentUser y borra permanentemente todas tus conversaciones guardadas. Esta acción no se puede deshacer.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAccount()
                        showDeleteConfirmation = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar definitivamente")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun ConectoresDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Power, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Conectores", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("Google Drive", "https://drive.google.com", R.drawable.ic_drive),
                Triple("Slack", "https://slack.com", R.drawable.ic_slack),
                Triple("Notion", "https://notion.so", R.drawable.ic_notion),
                Triple("Trello", "https://trello.com", R.drawable.ic_trello)
            ).forEach { (name, url, iconRes) ->
                Card(
                    Modifier.fillMaxWidth().clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val isDarkIcon = iconRes == R.drawable.ic_notion
                        val colorFilter = if (isDarkIcon) ColorFilter.tint(MaterialTheme.colorScheme.onSurface) else null
                        androidx.compose.foundation.Image(painter = androidx.compose.ui.res.painterResource(id = iconRes), contentDescription = name, modifier = Modifier.size(24.dp), colorFilter = colorFilter)
                        Spacer(Modifier.width(12.dp))
                        Text(name, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = MaterialTheme.colorScheme.primary) } }
    )
}

@Composable
fun GitHubToolsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_github), contentDescription = null, modifier = Modifier.size(24.dp), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
                Spacer(Modifier.width(12.dp))
                Text("GitHub Tools", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("Explorar Repos", "https://github.com/explore", R.drawable.ic_github),
                    Triple("Mis Repositorios", "https://github.com/settings/repositories", R.drawable.ic_github),
                    Triple("Pull Requests", "https://github.com/pulls", R.drawable.ic_github),
                    Triple("GitHub Gists", "https://gist.github.com", R.drawable.ic_github)
                ).forEach { (name, url, iconRes) ->
                    Card(
                        Modifier.fillMaxWidth().clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.Image(painter = androidx.compose.ui.res.painterResource(id = iconRes), contentDescription = null, modifier = Modifier.size(24.dp), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
                            Spacer(Modifier.width(12.dp))
                            Text(name, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = MaterialTheme.colorScheme.primary) } }
    )
}

/**
 * Distintivo visual por marca para cada modelo: círculo de color con la
 * inicial de la marca (L=Llama/Meta, G=Google/Gemma, D=DeepSeek, O=OpenAI,
 * M=Mistral, Q=Qwen, P=Microsoft, ?=otro). Evita depender de assets de logo.
 */
private fun modelBrand(id: String): Pair<Color, String> {
    val s = id.lowercase()
    return when {
        "llama" in s -> Color(0xFF0668E1) to "L"                    // Meta
        "gemma" in s || "gemini" in s -> Color(0xFF4285F4) to "G"  // Google
        "deepseek" in s -> Color(0xFF4D6BFE) to "D"                // DeepSeek
        "gpt" in s || "openai" in s -> Color(0xFF10A37F) to "O"    // OpenAI
        "mistral" in s || "mixtral" in s -> Color(0xFFFF7000) to "M" // Mistral
        "qwen" in s -> Color(0xFF7400CF) to "Q"                    // Qwen
        "phi" in s -> Color(0xFF0078D4) to "P"                     // Microsoft
        else -> Color(0xFF64748B) to "?"
    }
}

private fun modelLogo(id: String): String? {
    val s = id.lowercase()
    return when {
        "llama" in s -> Logos.MODEL_META
        "gemma" in s || "gemini" in s -> Logos.MODEL_GOOGLE
        "deepseek" in s -> Logos.MODEL_DEEPSEEK
        "gpt" in s || "openai" in s -> Logos.MODEL_OPENAI
        "mistral" in s || "mixtral" in s -> Logos.MODEL_MISTRAL
        "qwen" in s -> Logos.MODEL_QWEN
        "phi" in s -> Logos.MODEL_MICROSOFT
        else -> null
    }
}

@Composable
private fun ModelBadge(modelId: String, modifier: Modifier = Modifier) {
    val (bg, letter) = remember(modelId) { modelBrand(modelId) }
    val logoUrl = remember(modelId) { modelLogo(modelId) }
    Box(
        modifier = modifier.size(22.dp),
        contentAlignment = Alignment.Center
    ) {
        if (logoUrl != null) {
            SubcomposeAsyncImage(
                model = logoUrl,
                contentDescription = letter,
                modifier = Modifier.size(22.dp),
                contentScale = ContentScale.Fit,
                loading = { LetterBadge(bg, letter) },
                error = { LetterBadge(bg, letter) }
            )
        } else {
            LetterBadge(bg, letter)
        }
    }
}

@Composable
private fun LetterBadge(bg: Color, letter: String) {
    Box(
        modifier = Modifier.size(22.dp).background(bg, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalizationScreen(
    viewModel: ZaiViewModel,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setBackgroundImageFromUri(it) }
    }
    
    val bgUri by viewModel.backgroundImageUri.collectAsStateWithLifecycle()
    val bgTransparency by viewModel.backgroundTransparency.collectAsStateWithLifecycle()
    val primaryColorInt by viewModel.primaryColor.collectAsStateWithLifecycle()
    val primaryColor = Color(primaryColorInt)
    
    // Store initial values to detect changes
    val initialColor = remember { primaryColorInt }
    val initialBgUri = remember { bgUri }
    val initialTransparency = remember { bgTransparency }
    
    var showExitConfirmation by remember { mutableStateOf(false) }
    
    val hasChanges = primaryColorInt != initialColor || bgUri != initialBgUri || bgTransparency != initialTransparency

    val exitAction = {
        if (hasChanges) {
            showExitConfirmation = true
        } else {
            onDismiss()
        }
    }
    
    // Temas de colores expandidos
    val colors = listOf(
        0xFFFF5722.toInt(), 0xFFEF4444.toInt(), 0xFFEC4899.toInt(), 0xFF8B5CF6.toInt(),
        0xFF6366F1.toInt(), 0xFF3B82F6.toInt(), 0xFF0EA5E9.toInt(), 0xFF06B6D4.toInt(),
        0xFF10B981.toInt(), 0xFF84CC16.toInt(), 0xFFEAB308.toInt(), 0xFFF97316.toInt()
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(Modifier.fillMaxSize()) {
            // FULL SCREEN PREVIEW (Simulated App)
            Box(Modifier.fillMaxSize()) {
                // Background image simulation
                if (bgUri.isNotEmpty()) {
                    AsyncImage(
                        model = if (bgUri.startsWith("/")) File(bgUri) else bgUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().alpha(bgTransparency),
                        contentScale = ContentScale.Crop
                    )
                }
                
                // Chat Simulation content
                Column(Modifier.fillMaxSize()) {
                    // Simulated Top Bar
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth(),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).statusBarsPadding(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Menu, null, tint = primaryColor)
                            Spacer(Modifier.width(16.dp))
                            Text("Chat Personalizado", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Simulated Messages
                    Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Vista Previa Full-Screen", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                        
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp),
                            modifier = Modifier.widthIn(max = 280.dp).align(Alignment.Start),
                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f))
                        ) {
                            Text("¡Hola! Esta es una simulación de cómo se verá tu chat con el color y fondo seleccionados.", Modifier.padding(14.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        
                        Surface(
                            color = primaryColor,
                            shape = RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp),
                            modifier = Modifier.widthIn(max = 260.dp).align(Alignment.End),
                            shadowElevation = 2.dp
                        ) {
                            Text("¡Se ve fantástico! El color resalta perfectamente.", Modifier.padding(14.dp), fontSize = 13.sp, color = Color.White)
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp),
                            modifier = Modifier.widthIn(max = 240.dp).align(Alignment.Start),
                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f))
                        ) {
                            Text("Puedes ajustar la transparencia si el fondo es muy brillante.", Modifier.padding(14.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    
                    // Simulated Input Bar
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().height(56.dp),
                        border = BorderStroke(1.5.dp, primaryColor),
                        shadowElevation = 6.dp
                    ) {
                        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AttachFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text("Escribe un mensaje...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.Send, null, tint = primaryColor, modifier = Modifier.size(24.dp))
                        }
                    }
                    // Padding for the controls card
                    Spacer(Modifier.height(180.dp))
                }
            }

            // FLOATING CONTROLS PANEL
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Configurar Estilo", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Mini Tab-like navigation for controls if it gets too crowded? No, let's use a small Scrollable Row for colors and then sliders.
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(end = 10.dp)
                    ) {
                        items(colors) { colorInt ->
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorInt))
                                    .clickable { viewModel.setPrimaryColor(colorInt) }
                                    .border(
                                        width = if (primaryColorInt == colorInt) 3.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(20.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { imagePicker.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Fondo de pantalla", fontSize = 14.sp)
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { 
                                    onDismiss() 
                                    Toast.makeText(context, "Cambios aplicados", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = primaryColor,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text("Aplicar cambios", fontSize = 14.sp)
                            }

                            OutlinedButton(
                                onClick = exitAction,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text("Cancelar", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    
                    if (bgUri.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Opacity, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Opacidad: ${(bgTransparency * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            Slider(
                                value = bgTransparency,
                                onValueChange = { viewModel.setBackgroundTransparency(it) },
                                colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor),
                                valueRange = 0f..1f,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showExitConfirmation) {
            AlertDialog(
                onDismissRequest = { showExitConfirmation = false },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("¿Guardar cambios?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
                text = { Text("Has realizado cambios en el estilo. ¿Deseas aplicarlos o descartarlos?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                confirmButton = {
                    Button(
                        onClick = {
                            showExitConfirmation = false
                            onDismiss()
                            Toast.makeText(context, "Cambios aplicados", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Aplicar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showExitConfirmation = false
                        // Revert changes
                        viewModel.setPrimaryColor(initialColor)
                        viewModel.setBackgroundImageUri(initialBgUri)
                        viewModel.setBackgroundTransparency(initialTransparency)
                        onDismiss()
                    }) {
                        Text("Descartar", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    }
}
