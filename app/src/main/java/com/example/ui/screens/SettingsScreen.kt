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

// =========================================================================
// PANTALLA 6: CONFIGURACIÓN Y PERFIL DE OPERADOR
// =========================================================================
@Composable
fun PantallaConfiguracion(repository: IncidentRepository) {
    val context = LocalContext.current
    val usuario = repository.getUsuarioActual()
    val connectionState by repository.connectionState.collectAsStateWithLifecycle()
    val allowedVias by repository.allowedVias.collectAsStateWithLifecycle()
    val unassignedCount by repository.unassignedCountFlow
        .collectAsStateWithLifecycle(initialValue = 0)
    val voiceMetrics = remember {
        AviSpeechManager.getInstance(context).getVoiceMetrics()
    }
    val voiceSuccessRate = if (voiceMetrics.attempts > 0) {
        ((voiceMetrics.validFirstPass * 100f) / voiceMetrics.attempts).toInt()
    } else {
        0
    }

    var isCheckingConnection by remember { mutableStateOf(false) }
    val isBubbleRunning by FloatingBubbleService.runningState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Configuración del Sistema",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Text(
            text = "Ajustes de AVIX y servicio flotante",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Datos del Trabajador
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Sesión Activa", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "Trabajador: ${usuario?.nombre ?: "Operador"}", fontSize = 13.sp)
                Text(text = "Código de Operador: #${usuario?.codigo ?: "---"}", fontSize = 13.sp)
                Text(
                    text = "Plaza Asignada: ${usuario?.plaza ?: usuario?.plazaId?.toString() ?: "Sin plaza"}",
                    fontSize = 13.sp
                )
                Text(text = "Rol: ${usuario?.rol ?: "OPERADOR"}", fontSize = 13.sp)
            }
        }

        if (unassignedCount > 0) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF7E6)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Color(0xFFF59E0B)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    Text(
                        text = "Registros antiguos conservados",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF92400E)
                    )
                    Text(
                        text = "$unassignedCount registro(s) provienen de una versión que no guardaba operador/plaza. Se conservaron, pero AVIX no los atribuirá ni enviará automáticamente.",
                        fontSize = 11.sp,
                        color = Color(0xFF92400E)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Métricas locales del motor de voz
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Motor de voz AVIX",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Métricas guardadas solo en este dispositivo",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Dictados completos al primer intento: $voiceSuccessRate%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Intentos: ${voiceMetrics.attempts} • Alternativa elegida: ${voiceMetrics.alternativeSelected}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Correcciones manuales: ${voiceMetrics.manualCorrections} • Placa: ${voiceMetrics.plateCorrections} • Vía: ${voiceMetrics.viaCorrections}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Fallos detectados — acción: ${voiceMetrics.missingAction}, vía: ${voiceMetrics.missingVia}, placa: ${voiceMetrics.missingPlate}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (voiceMetrics.timedRecognitionCount > 0) {
                        "Tiempo promedio del dictado: ${voiceMetrics.averageRecognitionMs} ms " +
                            "(${voiceMetrics.timedRecognitionCount} resultados)"
                    } else {
                        "Tiempo del dictado: se medirá después del primer reconocimiento."
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (allowedVias.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Vías SIGO cargadas: ${allowedVias.sorted().joinToString(", ")}",
                        fontSize = 11.sp,
                        color = AviStatusOnline
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Estado de conexión automática con SIGO-BACK
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Conexión a SIGO",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "AVIX se conecta automáticamente al servidor productivo.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when (connectionState) {
                        ApiConnectionState.ONLINE -> Color(0xFFECFDF5)
                        ApiConnectionState.OFFLINE -> Color(0xFFFEF2F2)
                        ApiConnectionState.SIN_CONFIGURAR -> Color(0xFFFFFBEB)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (connectionState == ApiConnectionState.ONLINE) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.Warning
                            },
                            contentDescription = null,
                            tint = when (connectionState) {
                                ApiConnectionState.ONLINE -> AviStatusOnline
                                ApiConnectionState.OFFLINE -> Color(0xFFDC2626)
                                ApiConnectionState.SIN_CONFIGURAR -> Color(0xFFD97706)
                            },
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = connectionState.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when (connectionState) {
                                ApiConnectionState.ONLINE -> AviStatusOnline
                                ApiConnectionState.OFFLINE -> Color(0xFFDC2626)
                                ApiConnectionState.SIN_CONFIGURAR -> Color(0xFFD97706)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        isCheckingConnection = true
                        repository.verificarConexionSigo()
                        isCheckingConnection = false
                    },
                    enabled = !isCheckingConnection,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isCheckingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        if (isCheckingConnection) {
                            "Comprobando..."
                        } else {
                            "Comprobar conexión"
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Burbuja Flotante Overlay
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Burbuja Flotante AVIX", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = "Permite dictar incidencias encima de la app de peaje de la empresa",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = isBubbleRunning,
                    onCheckedChange = { activate ->
                        if (activate) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } else {
                                val intent = Intent(context, FloatingBubbleService::class.java)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                            }
                        } else {
                            FloatingBubbleService.stop(context)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Botón CERRAR SESIÓN
        Button(
            onClick = {
                repository.logout()
                Toast.makeText(context, "Sesión cerrada correctamente.", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("CERRAR SESIÓN DE OPERADOR", fontWeight = FontWeight.Bold)
        }
    }
}
