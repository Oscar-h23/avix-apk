package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.data.IncidentRepository
import com.example.model.ParsedCommand
import com.example.parser.AviParser
import com.example.ui.registration.PlateConfidenceHint
import com.example.util.AviDateUtils
import com.example.voice.AviSpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

// Acciones disponibles en el panel flotante y sus colores de selección.
private val popupActions = listOf(
    "FUGA" to Color(0xFFDC2626),
    "DERIVADO" to Color(0xFFD97706),
    "LIBERADO" to Color(0xFF059669)
)

class FloatingBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null

    private lateinit var speechManager: AviSpeechManager
    private lateinit var repository: IncidentRepository

    // Estado para controlar si está en modo burbuja pequeña o panel flotante expandido
    private val isExpanded = MutableStateFlow(false)

    // Zona de cierre estilo Messenger: al arrastrar AVIX hacia abajo y soltar, se cierra.
    private val isInDismissZone = MutableStateFlow(false)

    // Coordenadas flotantes
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private val serviceLifecycleOwner = CustomServiceLifecycleOwner()

    override fun onCreate() {
        super.onCreate()
        serviceLifecycleOwner.onCreate()

        speechManager = AviSpeechManager.getInstance(this)
        repository = IncidentRepository.getInstance(this)

        startForegroundNotification()
        setupFloatingOverlay()

        setRunningState(true)
    }

    private fun startForegroundNotification() {
        val channelId = "avi_floating_service"
        val channelName = "AVIX Asistente Operativo"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Burbuja flotante activa de AVIX para captura de incidencias"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AVIX activo sobre otras aplicaciones")
            .setContentText("Burbuja flotante lista para registrar incidencias por voz")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun setupFloatingOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        windowLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(serviceLifecycleOwner)
            setViewTreeViewModelStoreOwner(serviceLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(serviceLifecycleOwner)

            setContent {
                MaterialTheme(
                    colorScheme = lightColorScheme(
                        primary = Color(0xFF0B5CAB),
                        secondary = Color(0xFF2563EB),
                        background = Color(0xFFF8FAFC),
                        surface = Color.White,
                        surfaceVariant = Color(0xFFF1F5F9),
                        onSurface = Color(0xFF132033),
                        onSurfaceVariant = Color(0xFF64748B),
                        outline = Color(0xFFDFE5EE)
                    )
                ) {
                    FloatingOverlayContent(
                        isExpandedFlow = isExpanded,
                        isInDismissZoneFlow = isInDismissZone,
                        speechManager = speechManager,
                        repository = repository,
                        onExpandToggle = {
                            val newExpanded = !isExpanded.value
                            isExpanded.value = newExpanded
                            updateLayoutParamsForExpansion(newExpanded)
                        },
                        onDrag = { dx, dy ->
                            windowLayoutParams?.let { params ->
                                params.x += dx.toInt()
                                params.y += dy.toInt()

                                val screenHeight = resources.displayMetrics.heightPixels
                                val overlayHeight = overlayView?.height ?: 0
                                val overlayCenterY = params.y + (overlayHeight / 2)
                                val dismissThreshold = (screenHeight * 0.78f).toInt()

                                isInDismissZone.value = overlayCenterY >= dismissThreshold
                                windowManager?.updateViewLayout(overlayView, params)
                            }
                        },
                        onDragEnd = {
                            if (isInDismissZone.value) {
                                stopSelf()
                            } else {
                                isInDismissZone.value = false
                            }
                        },
                        onDragCancel = {
                            isInDismissZone.value = false
                        }
                    )
                }
            }
        }

        try {
            windowManager?.addView(overlayView, windowLayoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Permiso de superposición necesario para AVIX", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun updateLayoutParamsForExpansion(expanded: Boolean) {
        windowLayoutParams?.let { params ->
            if (expanded) {
                // Permitir que el panel tome foco para escribir en campos de texto si es necesario
                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                params.width = WindowManager.LayoutParams.WRAP_CONTENT
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
            } else {
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                params.width = WindowManager.LayoutParams.WRAP_CONTENT
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
            }
            windowManager?.updateViewLayout(overlayView, params)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        setRunningState(false)
        speechManager.destroy()
        if (overlayView != null) {
            windowManager?.removeView(overlayView)
            overlayView = null
        }
        serviceLifecycleOwner.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 1001

        private val _runningState = MutableStateFlow(false)
        val runningState: StateFlow<Boolean> = _runningState.asStateFlow()

        var isRunning = false
            private set

        private fun setRunningState(running: Boolean) {
            isRunning = running
            _runningState.value = running
        }

        fun start(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            context.stopService(intent)
        }
    }
}

/**
 * Contenido Compose renderizado dentro de la ventana de WindowManager
 */
@Composable
fun FloatingOverlayContent(
    isExpandedFlow: MutableStateFlow<Boolean>,
    isInDismissZoneFlow: MutableStateFlow<Boolean>,
    speechManager: AviSpeechManager,
    repository: IncidentRepository,
    onExpandToggle: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val expanded by isExpandedFlow.collectAsState()
    val isInDismissZone by isInDismissZoneFlow.collectAsState()
    val voiceState by speechManager.voiceState.collectAsState()
    val allowedVias by repository.allowedVias.collectAsState()
    val session by repository.sessionState.collectAsState()
    val scope = rememberCoroutineScope()
    var loadingVias by remember { mutableStateOf(false) }
    var viasError by remember { mutableStateOf<String?>(null) }

    // A diferencia del diálogo principal, el overlay vive en un Service.
    // Al expandirlo debe recargar el catálogo asociado al operador actual.
    suspend fun recargarViasDePlaza() {
        val plazaId = repository.getUsuarioActual()?.plazaId
        if (plazaId == null || !repository.isUsuarioAutenticado()) {
            viasError = "Inicia sesión en AVIX con una plaza asignada."
            return
        }

        loadingVias = true
        viasError = null
        try {
            val vias = repository.cargarViasPermitidas(plazaId)
            if (vias.isEmpty()) {
                viasError = "No se pudieron obtener vías habilitadas para esta plaza. " +
                    "Comprueba la conexión o reintenta la carga."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            viasError = "No se pudo cargar el catálogo de vías. Intenta nuevamente."
        } finally {
            loadingVias = false
        }
    }

    LaunchedEffect(expanded, session.usuario?.plazaId, session.token) {
        if (expanded) recargarViasDePlaza()
    }

    DisposableEffect(allowedVias) {
        speechManager.setAllowedVias(allowedVias)
        onDispose { }
    }

    var accionInput by remember { mutableStateOf("") }
    var viaInput by remember { mutableStateOf("") }
    var placaInput by remember { mutableStateOf("") }
    var textoOriginalInput by remember { mutableStateOf("") }
    var fechaHoraEventoCapturada by remember { mutableStateOf(AviDateUtils.nowLimaIso()) }
    var mensajeRegistro by remember { mutableStateOf<String?>(null) }
    var mensajeVoz by remember { mutableStateOf<String?>(null) }
    var viaMenuExpanded by remember { mutableStateOf(false) }
    var operationId by remember {
        mutableStateOf(UUID.randomUUID().toString())
    }
    var expectedOwner by remember {
        mutableStateOf(repository.currentOwner())
    }
    var isSaving by remember {
        mutableStateOf(false)
    }
    var lastParsedCommand by remember {
        mutableStateOf(
            ParsedCommand(
                placa = "",
                via = null,
                accion = "FUGA",
                textoOriginal = "",
                valido = false
            )
        )
    }

    // La voz puede llegar antes que la respuesta de AVIX. Cuando se
    // actualiza el catálogo, resolver "51" -> "151" sin borrar la placa.
    LaunchedEffect(allowedVias) {
        if (allowedVias.isNotEmpty()) {
            val numeroReal = AviParser.normalizeViaNumber(viaInput, allowedVias)
            if (numeroReal != null && numeroReal in allowedVias &&
                viaInput != numeroReal.toString()
            ) {
                viaInput = numeroReal.toString()
                lastParsedCommand = lastParsedCommand.copy(via = numeroReal)
            }
        }
    }

    // Conectar el resultado de voz con los campos.
    // Si el primer intento quedó incompleto, el siguiente dictado puede corregir
    // solo acción, vía o placa sin perder los datos ya reconocidos.
    DisposableEffect(speechManager, allowedVias, isSaving) {
        val listener: (String) -> Unit = listener@{ textoInterpretado ->
            if (
                !isExpandedFlow.value ||
                isSaving
            ) {
                return@listener
            }

            fechaHoraEventoCapturada = AviDateUtils.nowLimaIso()

            val dictadoOriginal = speechManager
                .voiceState
                .value
                .recognizedText
                .ifBlank { textoInterpretado }

            val originalPrevio = lastParsedCommand.textoOriginal

            val parsedBase = if (
                originalPrevio.isNotBlank()
            ) {
                AviParser.mergeCorrection(
                    previous = lastParsedCommand,
                    rawCorrection = textoInterpretado,
                    allowedVias = allowedVias
                )
            } else {
                AviParser.parse(
                    textoInterpretado,
                    allowedVias
                )
            }

            val parsed = parsedBase.copy(
                textoOriginal = originalPrevio.ifBlank {
                    dictadoOriginal
                }
            )

            lastParsedCommand = parsed
            accionInput = parsed.accion
            viaInput = parsed.via?.toString() ?: ""
            placaInput = parsed.placa
            textoOriginalInput = parsed.textoOriginal

            mensajeVoz = if (parsed.valido) {
                null
            } else {
                parsed.errores.firstOrNull()
                    ?: "Repita únicamente el dato que falta."
            }
        }

        speechManager.addResultListener(listener)
        onDispose {
            speechManager.removeResultListener(listener)
        }
    }

    if (!expanded) {
        // BURBUJA COLAPSADA
        Box(
            modifier = Modifier
                .wrapContentSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel
                    ) { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .clickable {
                    // Un toque en la burbuja ya no solo abre el panel:
                    // también deja AVIX escuchando inmediatamente.
                    onExpandToggle()

                    if (!voiceState.isListening) {
                        if (
                            placaInput.isBlank() &&
                            viaInput.isBlank() &&
                            accionInput.isBlank()
                        ) {
                            operationId =
                                UUID.randomUUID().toString()
                            expectedOwner =
                                repository.currentOwner()
                            fechaHoraEventoCapturada =
                                AviDateUtils.nowLimaIso()
                        }

                        mensajeRegistro = null
                        speechManager.startListening()
                    }
                }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(62.dp)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(if (isInDismissZone) Color(0xFFD64545) else Color(0xFF0B2342))
                    .border(
                        2.5.dp,
                        if (isInDismissZone) Color(0xFFFCA5A5) else Color(0xFF1688E8),
                        CircleShape
                    )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Abrir AVIX",
                        tint = if (isInDismissZone) {
                            Color.White
                        } else if (voiceState.isListening) {
                            Color(0xFFEF4444)
                        } else {
                            Color(0xFF60A5FA)
                        },
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = if (isInDismissZone) "SOLTAR" else "AVIX",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    } else {
        // PANEL EXPANDIDO FLOTANTE
        Card(
            modifier = Modifier
                .width(340.dp)
                .padding(4.dp)
                .shadow(16.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (isInDismissZone) Color(0xFFD64545) else Color(0xFFDFE5EE)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Cabecera arrastrable
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragCancel
                            ) { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (voiceState.isListening) Color(0xFFEF4444) else Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isInDismissZone) "Suelta para cerrar AVIX" else "AVIX",
                            color = if (isInDismissZone) Color(0xFFD64545) else Color(0xFF132033),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    IconButton(
                        onClick = onExpandToggle,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Minimizar",
                            tint = Color(0xFF64748B)
                        )
                    }
                }

                Text(
                    text = if (isInDismissZone)
                        "Suelta aquí para cerrar la burbuja"
                    else
                        "Arrastra hacia abajo para cerrar",
                    color = if (isInDismissZone) Color(0xFFD64545) else Color(0xFF94A3B8),
                    fontSize = 9.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Guía breve del orden de dictado
                Text(
                    text = "ACCIÓN  →  VÍA  →  PLACA",
                    color = Color(0xFF0B5CAB),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Captura principal por voz
                Button(
                    onClick = {
                        if (voiceState.isListening) {
                            speechManager.stopListening()
                        } else {
                            speechManager.startListening()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (voiceState.isListening) Color(0xFFD64545) else Color(0xFF0B5CAB)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (voiceState.isListening) "DETENER ESCUCHA" else "PULSAR PARA HABLAR",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                if (voiceState.isListening) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Escuchando...",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (voiceState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "⚠ ${voiceState.errorMessage}",
                        color = Color(0xFFF87171),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Selector de acción: FUGA, DERIVADO y LIBERADO.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    popupActions.forEach { (action, selectedColor) ->
                        FilterChip(
                            selected = accionInput == action,
                            onClick = {
                                accionInput = action
                                mensajeVoz = null
                            },
                            label = {
                                Text(
                                    text = action,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = selectedColor,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Campos interpretados (Placa y Vía)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = placaInput,
                        onValueChange = {
                            placaInput = it.uppercase()
                                .replace(" ", "")
                                .replace("-", "")
                        },
                        label = { Text("Placa", fontSize = 11.sp) },
                        modifier = Modifier.weight(1.2f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0B5CAB),
                            unfocusedBorderColor = Color(0xFFDFE5EE),
                            focusedTextColor = Color(0xFF132033),
                            unfocusedTextColor = Color(0xFF132033)
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    if (allowedVias.isNotEmpty()) {
                        Box(modifier = Modifier.weight(0.8f)) {
                            OutlinedButton(
                                onClick = { viaMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = viaInput.toIntOrNull()?.let { "Vía $it" }
                                        ?: "Vía",
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Seleccionar vía",
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = viaMenuExpanded,
                                onDismissRequest = { viaMenuExpanded = false }
                            ) {
                                allowedVias.sorted().forEach { via ->
                                    DropdownMenuItem(
                                        text = { Text("Vía $via", fontSize = 12.sp) },
                                        leadingIcon = {
                                            if (viaInput == via.toString()) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color(0xFF059669),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        },
                                        onClick = {
                                            viaInput = via.toString()
                                            mensajeVoz = null
                                            viaMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = viaInput,
                            onValueChange = {
                                if (it.all { ch -> ch.isDigit() }) {
                                    viaInput = it
                                }
                            },
                            label = { Text("Vía", fontSize = 11.sp) },
                            modifier = Modifier.weight(0.8f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF0B5CAB),
                                unfocusedBorderColor = Color(0xFFDFE5EE),
                                focusedTextColor = Color(0xFF132033),
                                unfocusedTextColor = Color(0xFF132033)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                if (allowedVias.isNotEmpty()) {
                    Text(
                        text = "Vías de ${session.usuario?.plaza ?: "tu plaza"}: " +
                            allowedVias.sorted().joinToString(", "),
                        fontSize = 10.sp,
                        color = Color(0xFF047857)
                    )
                } else {
                    Text(
                        text = if (loadingVias) {
                            "Cargando vías de ${session.usuario?.plaza ?: "la plaza"}..."
                        } else {
                            viasError ?: "No hay vías disponibles para el registro."
                        },
                        fontSize = 10.sp,
                        color = if (loadingVias) Color(0xFF2563EB) else Color(0xFFB45309)
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    OutlinedButton(
                        onClick = { scope.launch { recargarViasDePlaza() } },
                        enabled = !loadingVias && session.usuario?.plazaId != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (loadingVias) "CARGANDO VÍAS..." else "RECARGAR VÍAS DE AVIX",
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Hora evento (Lima): ${AviDateUtils.formatIsoToDisplay(fechaHoraEventoCapturada)}",
                    fontSize = 10.sp,
                    color = Color.LightGray
                )

                if (textoOriginalInput.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Texto: \"$textoOriginalInput\"",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }

                if (mensajeVoz != null) {
                    PlateConfidenceHint(
                    plate = placaInput,
                    confidence =
                        voiceState
                            .platePositionConfidence
                            .mapIndexed {
                                    index,
                                    confidence ->
                                if (
                                    placaInput.getOrNull(index) !=
                                    lastParsedCommand.placa.getOrNull(index)
                                ) {
                                    1f
                                } else {
                                    confidence
                                }
                            },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "⚠ $mensajeVoz",
                        color = Color(0xFFD64545),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (mensajeRegistro != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = mensajeRegistro!!,
                        color = Color(0xFF10B981),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Botón REGISTRAR
                Button(
                    onClick = {
                        val viaNum = AviParser.normalizeViaNumber(
                            viaInput, allowedVias
                        )
                        if (viaNum != null && viaNum in allowedVias) {
                            viaInput = viaNum.toString()
                        }

                        speechManager.recordManualCorrection(
                            original = lastParsedCommand,
                            finalPlate = placaInput,
                            finalVia = viaNum,
                            finalAction = accionInput
                        )

                        if (isSaving) {
                            return@Button
                        }

                        isSaving = true
                        mensajeRegistro = "Guardando en dispositivo..."
                        mensajeVoz = null

                        scope.launch {
                            val result =
                                repository.registrarIncidencia(
                                    placa = placaInput.uppercase(),
                                    via = viaNum,
                                    accion = accionInput,
                                    fechaHoraEvento =
                                        fechaHoraEventoCapturada,
                                    textoReconocido =
                                        textoOriginalInput,
                                    operationId = operationId,
                                    expectedOwner = expectedOwner
                                        ?: repository.currentOwner()
                                )

                            isSaving = false

                            if (result.isSuccess) {
                                mensajeVoz = null
                                mensajeRegistro =
                                    "✓ Guardado en dispositivo"
                                kotlinx.coroutines.delay(900)
                                mensajeRegistro = null

                                // El siguiente vehículo recibe un UUID nuevo.
                                lastParsedCommand = ParsedCommand()
                                accionInput = ""
                                viaInput = ""
                                placaInput = ""
                                textoOriginalInput = ""
                                operationId =
                                    UUID.randomUUID().toString()
                                expectedOwner =
                                    repository.currentOwner()
                                fechaHoraEventoCapturada =
                                    AviDateUtils.nowLimaIso()

                                onExpandToggle()
                            } else {
                                mensajeRegistro = null
                                mensajeVoz =
                                    result.exceptionOrNull()?.message
                                        ?: "No se pudo guardar el evento."
                            }
                        }
                    },
                    enabled = !isSaving &&
                        AviParser.isValidPeruPlate(
                            placaInput.uppercase()
                                .replace(" ", "")
                                .replace("-", "")
                        ) &&
                        AviParser.normalizeViaNumber(viaInput, allowedVias)
                            ?.let { repository.isViaPermitida(it) } == true &&
                        popupActions.any { (action, _) -> action == accionInput },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0B5CAB)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSaving) {
                            "GUARDANDO..."
                        } else {
                            "GUARDAR REGISTRO"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * Provee un ciclo de vida para ComposeView cuando se ejecuta en un Service con WindowManager
 */
class CustomServiceLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
}
