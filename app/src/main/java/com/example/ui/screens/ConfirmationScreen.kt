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
import com.example.ui.theme.AviActionLiberado
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
// PANTALLA 4: CONFIRMACIÓN Y REVISIÓN (Editable antes de guardar)
// =========================================================================
@Composable
fun PantallaConfirmacion(
    repository: IncidentRepository,
    parsedCommand: ParsedCommand,
    fechaHoraEvento: String,
    draft: RegistrationViewModel,
    voiceState: VoiceState,
    onConfirmado: () -> Unit,
    onCorregir: () -> Unit,
    onCancelar: () -> Unit
) {
    val context = LocalContext.current
    val usuario = repository.getUsuarioActual()
    val scope = rememberCoroutineScope()
    val allowedVias by repository.allowedVias.collectAsStateWithLifecycle()
    val isSaving by draft.saving.collectAsStateWithLifecycle()

    LaunchedEffect(usuario?.plazaId) {
        usuario?.plazaId?.let { plazaId ->
            repository.cargarViasPermitidas(plazaId)
        }
    }
    val submitted by draft.submitted.collectAsStateWithLifecycle()

    val recognizedPlate = rememberSaveable {
        parsedCommand.placa
    }

    var placaInput by rememberSaveable {
        mutableStateOf(parsedCommand.placa)
    }
    var viaInput by rememberSaveable {
        mutableStateOf(parsedCommand.via?.toString() ?: "")
    }
    var accionInput by rememberSaveable {
        mutableStateOf(parsedCommand.accion)
    }
    var textoOriginalInput by rememberSaveable {
        mutableStateOf(parsedCommand.textoOriginal)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }
    var viaMenuExpanded by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancelar) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = "Confirmación de Incidencia",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Hora de evento: ${AviDateUtils.formatIsoToDisplay(fechaHoraEvento)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tarjeta con Texto Original
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "DICTADO ORIGINAL RECONOCIDO:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = textoOriginalInput.ifBlank { "(Entrada manual sin audio)" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!parsedCommand.valido && parsedCommand.errores.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E6))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "AVIX necesita una corrección",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color(0xFF9A6700)
                    )
                    parsedCommand.errores.take(2).forEach { error ->
                        Text(
                            text = "• $error",
                            fontSize = 11.sp,
                            color = Color(0xFF7A5A00)
                        )
                    }
                    Text(
                        text = "Puedes corregir manualmente o pulsar “Corregir por voz” y repetir solo el dato faltante.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // CAMPOS EDITABLES
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Datos Interpretados por AVIX",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AviNavy
                )
                Text(
                    text = "Revise y corrija los valores antes de confirmar",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Selección de Acción: FUGA, DERIVADO o LIBERADO
                Text("Acción Operativa", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Triple("FUGA", AviActionFuga, "FUGA"),
                        Triple("DERIVADO", AviActionDerivado, "DERIVADO"),
                        Triple("LIBERADO", AviActionLiberado, "LIBERADO")
                    ).forEach { (value, activeColor, label) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (accionInput == value) {
                                activeColor
                            } else {
                                Color(0xFFF1F5F9)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    accionInput = value
                                    errorMessage = null
                                }
                        ) {
                            Box(
                                modifier = Modifier.padding(
                                    horizontal = 4.dp,
                                    vertical = 12.dp
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontWeight = FontWeight.Bold,
                                    color = if (accionInput == value) {
                                        Color.White
                                    } else {
                                        Color(0xFF334155)
                                    },
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Campo PLACA
                OutlinedTextField(
                    value = placaInput,
                    onValueChange = {
                        placaInput = it
                            .uppercase()
                            .replace(" ", "")
                            .replace("-", "")
                        errorMessage = null
                    },
                    label = { Text("Placa del Vehículo") },
                    placeholder = { Text("Ej: BTL245 o A1B234") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("plate_input_field"),
                    trailingIcon = {
                        if (AviParser.isValidPeruPlate(placaInput)) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AviStatusOnline
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

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
                                    recognizedPlate.getOrNull(index)
                                ) {
                                    1f
                                } else {
                                    confidence
                                }
                            },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // VÍA: catálogo real de SIGO filtrado por la plaza del usuario.
                Text(
                    text = "Vía",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (allowedVias.isNotEmpty()) {
                    val viaSeleccionada =
                        viaInput.toIntOrNull()?.takeIf { it in allowedVias }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { viaMenuExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("lane_select"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = viaSeleccionada?.let { "Vía $it" }
                                    ?: "Seleccionar vía de ${usuario?.plaza ?: "la plaza"}",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start,
                                fontWeight = if (viaSeleccionada == null) {
                                    FontWeight.Normal
                                } else {
                                    FontWeight.SemiBold
                                }
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Abrir selector de vía"
                            )
                        }

                        DropdownMenu(
                            expanded = viaMenuExpanded,
                            onDismissRequest = { viaMenuExpanded = false }
                        ) {
                            allowedVias.sorted().forEach { via ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Vía $via",
                                            fontWeight = if (viaSeleccionada == via) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Normal
                                            }
                                        )
                                    },
                                    leadingIcon = {
                                        if (viaSeleccionada == via) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = AviStatusOnline
                                            )
                                        }
                                    },
                                    onClick = {
                                        viaInput = via.toString()
                                        errorMessage = null
                                        viaMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "${allowedVias.size} vía(s) activa(s) cargada(s) para ${usuario?.plaza ?: "esta plaza"}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    OutlinedButton(
                        onClick = {
                            usuario?.plazaId?.let { plazaId ->
                                scope.launch {
                                    repository.cargarViasPermitidas(plazaId)
                                }
                            }
                        },
                        enabled = usuario?.plazaId != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cargar vías de ${usuario?.plaza ?: "la plaza"}")
                    }

                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "No se encontraron vías activas registradas para esta plaza.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0x33DC2626)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = errorMessage!!, color = Color(0xFFDC2626), fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // BOTONES DE ACCIÓN
        Button(
            onClick = {
                val viaNum = viaInput.toIntOrNull()
                if (!AviParser.isValidPeruPlate(placaInput)) {
                    errorMessage = "Formato de placa inválido. Usa 1 letra, 2 caracteres alfanuméricos y 3 números."
                    return@Button
                }
                if (viaNum == null || viaNum <= 0) {
                    errorMessage = "Ingrese un número de vía válido."
                    return@Button
                }
                if (!repository.isViaPermitida(viaNum)) {
                    errorMessage = "La vía $viaNum no está habilitada para esta plaza."
                    return@Button
                }

                AviSpeechManager.getInstance(context).recordManualCorrection(
                    original = parsedCommand,
                    finalPlate = placaInput,
                    finalVia = viaNum,
                    finalAction = accionInput
                )

                errorMessage = null

                val finalCommand = ParsedCommand(
                    placa = placaInput,
                    via = viaNum,
                    accion = accionInput,
                    textoOriginal = textoOriginalInput,
                    valido = true
                )

                draft.setEventTime(fechaHoraEvento)
                draft.updateDraft(finalCommand)
                draft.submit(
                    repository = repository,
                    command = finalCommand,
                    expectedOwner =
                        draft.expectedOwner()
                            ?: repository.currentOwner()
                ) { resultado ->
                    if (resultado.isSuccess) {
                        Toast.makeText(
                            context,
                            "Guardado en dispositivo. AVIX lo enviará a SIGO en segundo plano.",
                            Toast.LENGTH_LONG
                        ).show()
                        onConfirmado()
                    } else {
                        errorMessage =
                            resultado.exceptionOrNull()?.message
                                ?: "Error al guardar la incidencia."
                    }
                }
            },
            enabled = !isSaving && !submitted,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("confirm_register_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Guardando en dispositivo...")
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (submitted) "GUARDADO" else "GUARDAR REGISTRO",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onCorregir,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Corregir por voz")
            }

            OutlinedButton(
                onClick = onCancelar,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Cancelar")
            }
        }
    }
}
