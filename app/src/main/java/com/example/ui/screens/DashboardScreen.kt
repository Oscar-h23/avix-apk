package com.example.ui.screens

import com.example.R
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.api.SigoApiService
import com.example.data.ApiConnectionState
import com.example.data.IncidentRepository
import com.example.domain.HistoryFilters
import com.example.domain.HistoryStatusFilter
import com.example.model.DiagnosticStage
import com.example.model.EstadoSincronizacion
import com.example.model.Incident
import com.example.model.ParsedCommand
import com.example.model.VoiceState
import com.example.parser.AviParser
import com.example.service.FloatingBubbleService
import com.example.ui.registration.PlateConfidenceHint
import com.example.ui.registration.RegistrationViewModel
import com.example.ui.theme.AviActionDerivado
import com.example.ui.theme.AviActionFuga
import com.example.ui.theme.AviBlueAccent
import com.example.ui.theme.AviNavy
import com.example.ui.theme.AviPrimaryDark
import com.example.ui.theme.AviStatusOffline
import com.example.ui.theme.AviStatusOnline
import com.example.ui.theme.AviStatusPending
import com.example.ui.theme.MyApplicationTheme
import com.example.util.AviDateUtils
import com.example.voice.AviSpeechManager
import kotlinx.coroutines.launch

enum class AviNavigationTab(val title: String) {
    INICIO("Inicio"),
    DICTADO("Dictado"),
    REVISION("Revisión"),
    HISTORIAL("Historial"),
    AJUSTES("Ajustes")
}

// =========================================================================
// PANTALLAS PRINCIPALES (SCAFFOLD POST-LOGIN)
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AviMainDashboardScaffold(repository: IncidentRepository) {
    val context = LocalContext.current
    val usuario = repository.getUsuarioActual()
    val connectionState by repository.connectionState.collectAsStateWithLifecycle()
    val pendientesCount by repository.pendientesCountFlow.collectAsStateWithLifecycle(initialValue = 0)
    val allowedVias by repository.allowedVias.collectAsStateWithLifecycle()
    val speechManager = remember { AviSpeechManager.getInstance(context) }
    val registrationDraft: RegistrationViewModel = viewModel()

    LaunchedEffect(allowedVias) {
        speechManager.setAllowedVias(allowedVias)
    }

    var currentTab by rememberSaveable {
        mutableStateOf(AviNavigationTab.INICIO)
    }

    // El ViewModel conserva UUID, hora y campos ante recreaciones.
    var currentParsedCommand by remember {
        mutableStateOf(
            registrationDraft.restoreCommand()
        )
    }
    var fechaHoraEventoCapturada by remember {
        mutableStateOf(registrationDraft.eventTime)
    }

    // BackHandler para navegación natural en Compose
    BackHandler(enabled = currentTab != AviNavigationTab.INICIO) {
        currentTab = AviNavigationTab.INICIO
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "AVIX",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${usuario?.nombre ?: "Operador"} • ${usuario?.plaza ?: "Sin plaza"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Badge de conexión
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when (connectionState) {
                            ApiConnectionState.ONLINE -> Color(0x33059669)
                            ApiConnectionState.OFFLINE -> Color(0x33DC2626)
                            ApiConnectionState.SIN_CONFIGURAR -> Color(0x33D97706)
                        },
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (connectionState) {
                                            ApiConnectionState.ONLINE -> AviStatusOnline
                                            ApiConnectionState.OFFLINE -> AviStatusOffline
                                            ApiConnectionState.SIN_CONFIGURAR -> AviStatusPending
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (connectionState) {
                                    ApiConnectionState.ONLINE -> "ONLINE"
                                    ApiConnectionState.OFFLINE -> "OFFLINE"
                                    ApiConnectionState.SIN_CONFIGURAR -> "CONFIG"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (connectionState) {
                                    ApiConnectionState.ONLINE -> AviStatusOnline
                                    ApiConnectionState.OFFLINE -> AviStatusOffline
                                    ApiConnectionState.SIN_CONFIGURAR -> AviStatusPending
                                }
                            )
                        }
                    }

                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = currentTab == AviNavigationTab.INICIO,
                    onClick = { currentTab = AviNavigationTab.INICIO },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Inicio") },
                    label = { Text("Inicio") }
                )
                NavigationBarItem(
                    selected = currentTab == AviNavigationTab.DICTADO,
                    onClick = { currentTab = AviNavigationTab.DICTADO },
                    icon = { Icon(Icons.Default.Mic, contentDescription = "Dictado") },
                    label = { Text("Hablar") }
                )
                NavigationBarItem(
                    selected = currentTab == AviNavigationTab.REVISION,
                    onClick = { currentTab = AviNavigationTab.REVISION },
                    icon = { Icon(Icons.Default.Edit, contentDescription = "Revisión") },
                    label = { Text("Revisión") }
                )
                NavigationBarItem(
                    selected = currentTab == AviNavigationTab.HISTORIAL,
                    onClick = { currentTab = AviNavigationTab.HISTORIAL },
                    icon = {
                        if (pendientesCount > 0) {
                            BadgedBox(badge = { Badge { Text("$pendientesCount") } }) {
                                Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = "Historial")
                            }
                        } else {
                            Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = "Historial")
                        }
                    },
                    label = { Text("Historial") }
                )
                NavigationBarItem(
                    selected = currentTab == AviNavigationTab.AJUSTES,
                    onClick = { currentTab = AviNavigationTab.AJUSTES },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Ajustes") },
                    label = { Text("Ajustes") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AviNavigationTab.INICIO -> {
                    PantallaInicio(
                        repository = repository,
                        pendientesCount = pendientesCount,
                        onIniciarHablar = {
                            registrationDraft.newDraft(
                                repository.currentOwner()
                            )
                            fechaHoraEventoCapturada =
                                registrationDraft.eventTime
                            currentParsedCommand =
                                registrationDraft.restoreCommand()
                            currentTab = AviNavigationTab.DICTADO
                        },
                        onRegistroManual = {
                            registrationDraft.newDraft(
                                repository.currentOwner()
                            )
                            fechaHoraEventoCapturada =
                                registrationDraft.eventTime
                            currentParsedCommand = ParsedCommand(
                                placa = "",
                                via = null,
                                accion = "FUGA",
                                textoOriginal = "Ingreso manual desde panel",
                                valido = false
                            )
                            registrationDraft.updateDraft(
                                currentParsedCommand
                            )
                            currentTab = AviNavigationTab.REVISION
                        },
                        onSimularFrase = { frase ->
                            registrationDraft.newDraft(
                                repository.currentOwner()
                            )
                            fechaHoraEventoCapturada =
                                registrationDraft.eventTime
                            val parsed =
                                AviParser.parse(
                                    frase,
                                    allowedVias
                                )
                            currentParsedCommand = parsed
                            registrationDraft.updateDraft(parsed)
                            currentTab = AviNavigationTab.REVISION
                        }
                    )
                }

                AviNavigationTab.DICTADO -> {
                    PantallaEscucha(
                        speechManager = speechManager,
                        onTextoFinalizado = { textoInterpretado ->
                            val dictadoOriginal = speechManager
                                .voiceState
                                .value
                                .recognizedText
                                .ifBlank { textoInterpretado }

                            val originalPrevio =
                                currentParsedCommand.textoOriginal

                            val parsedBase = if (
                                originalPrevio.isNotBlank() &&
                                !currentParsedCommand.valido
                            ) {
                                AviParser.mergeCorrection(
                                    previous = currentParsedCommand,
                                    rawCorrection = textoInterpretado,
                                    allowedVias = allowedVias
                                )
                            } else {
                                AviParser.parse(
                                    textoInterpretado,
                                    allowedVias
                                )
                            }

                            currentParsedCommand = parsedBase.copy(
                                textoOriginal = originalPrevio.ifBlank {
                                    dictadoOriginal
                                }
                            )
                            registrationDraft.updateDraft(
                                currentParsedCommand
                            )
                            currentTab = AviNavigationTab.REVISION
                        },
                        onCancelar = {
                            currentTab = AviNavigationTab.INICIO
                        }
                    )
                }

                AviNavigationTab.REVISION -> {
                    PantallaConfirmacion(
                        repository = repository,
                        parsedCommand = currentParsedCommand,
                        fechaHoraEvento = fechaHoraEventoCapturada,
                        draft = registrationDraft,
                        voiceState = speechManager.voiceState.value,
                        onConfirmado = {
                            currentTab = AviNavigationTab.HISTORIAL
                        },
                        onCorregir = {
                            currentTab = AviNavigationTab.DICTADO
                        },
                        onCancelar = {
                            currentTab = AviNavigationTab.INICIO
                        }
                    )
                }

                AviNavigationTab.HISTORIAL -> {
                    PantallaHistorial(repository = repository)
                }

                AviNavigationTab.AJUSTES -> {
                    PantallaConfiguracion(repository = repository)
                }
            }
        }
    }
}

// =========================================================================
// PANTALLA 2: INICIO / DASHBOARD
// =========================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaInicio(
    repository: IncidentRepository,
    pendientesCount: Int,
    onIniciarHablar: () -> Unit,
    onRegistroManual: () -> Unit,
    onSimularFrase: (String) -> Unit
) {
    val context = LocalContext.current
    val usuario = repository.getUsuarioActual()
    val connectionState by repository.connectionState.collectAsStateWithLifecycle()
    val bubbleRunning by FloatingBubbleService.runningState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    fun startBubbleNow() {
        FloatingBubbleService.start(context)
        Toast.makeText(
            context,
            "Burbuja AVIX activada. Tócala para dictar.",
            Toast.LENGTH_SHORT
        ).show()
    }

    val overlayPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            val allowed =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                    Settings.canDrawOverlays(context)

            if (allowed) {
                startBubbleNow()
            } else {
                Toast.makeText(
                    context,
                    "AVIX necesita permiso para mostrarse sobre otras apps.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    val audioPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) {
                Toast.makeText(
                    context,
                    "El micrófono es necesario para usar la burbuja AVIX.",
                    Toast.LENGTH_LONG
                ).show()
            } else if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !Settings.canDrawOverlays(context)
            ) {
                overlayPermissionLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            } else {
                startBubbleNow()
            }
        }

    val toggleBubble: () -> Unit = {
        if (bubbleRunning) {
            FloatingBubbleService.stop(context)
            Toast.makeText(
                context,
                "Burbuja AVIX desactivada.",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            val hasAudioPermission =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

            when {
                !hasAudioPermission -> {
                    audioPermissionLauncher.launch(
                        Manifest.permission.RECORD_AUDIO
                    )
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !Settings.canDrawOverlays(context) -> {
                    overlayPermissionLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }

                else -> startBubbleNow()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Inicio",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Registro rápido de eventos operativos",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))


        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (bubbleRunning) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (bubbleRunning) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.outline
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (bubbleRunning) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = if (bubbleRunning) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Burbuja AVIX",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (bubbleRunning) {
                            "Activa · toca la burbuja para dictar"
                        } else {
                            "Actívala para dictar sobre otras apps"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = bubbleRunning,
                    onCheckedChange = {
                        toggleBubble()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Nuevo registro",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onIniciarHablar,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Registrar por voz",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onRegistroManual,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Registro manual")
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sincronización",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = if (pendientesCount == 0)
                            "Todos los registros están sincronizados"
                        else
                            "$pendientesCount registro(s) pendiente(s)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (pendientesCount > 0) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                repository.sincronizarPendientes()
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Sincronizar", fontSize = 11.sp)
                    }
                } else {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AviStatusOnline,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
