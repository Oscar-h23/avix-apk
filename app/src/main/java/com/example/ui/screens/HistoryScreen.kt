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
import com.example.api.AvixApiService
import com.example.data.ApiConnectionState
import com.example.data.IncidentRepository
import com.example.domain.HistoryFilters
import com.example.domain.HistoryStatusFilter
import com.example.model.DiagnosticStage
import com.example.model.EstadoSincronizacion
import com.example.model.Incident
import com.example.model.ParsedCommand
import com.example.model.VoiceState
import com.example.model.ViaDto
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
// PANTALLA 5: HISTORIAL DE EVENTOS (Room Database + AVIX)
// =========================================================================
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class
)
@Composable
fun PantallaHistorial(
    repository: IncidentRepository
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val incidents by repository.incidentsFlow
        .collectAsStateWithLifecycle(
            initialValue = emptyList()
        )
    val isSyncing by repository.isSyncing
        .collectAsStateWithLifecycle()
    val lastSyncSummary by repository.lastSyncSummary
        .collectAsStateWithLifecycle()
    var filtroSeleccionado by rememberSaveable {
        mutableStateOf(
            HistoryStatusFilter.TODOS
        )
    }
    var plateQuery by rememberSaveable {
        mutableStateOf("")
    }
    var fromDate by rememberSaveable {
        mutableStateOf("")
    }
    var toDate by rememberSaveable {
        mutableStateOf("")
    }
    var currentPage by rememberSaveable {
        mutableStateOf(1)
    }
    var editingIncident by remember {
        mutableStateOf<Incident?>(null)
    }

    val filterResult = remember(
        incidents,
        filtroSeleccionado,
        plateQuery,
        fromDate,
        toDate
    ) {
        HistoryFilters.apply(
            incidents = incidents,
            status = filtroSeleccionado,
            plateQuery = plateQuery,
            fromDate = fromDate,
            toDate = toDate
        )
    }

    val incidentesFiltrados =
        filterResult.items

    LaunchedEffect(
        filtroSeleccionado,
        plateQuery,
        fromDate,
        toDate
    ) {
        currentPage = 1
    }

    val pageResult = remember(
        incidentesFiltrados,
        currentPage
    ) {
        HistoryFilters.paginate(
            incidents = incidentesFiltrados,
            page = currentPage
        )
    }

    LaunchedEffect(
        pageResult.currentPage
    ) {
        if (
            currentPage !=
            pageResult.currentPage
        ) {
            currentPage =
                pageResult.currentPage
        }
    }

    val incidentesPagina =
        pageResult.items

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Historial Operativo",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(
                    text =
                        "${incidents.size} evento(s) del operador actual",
                    fontSize = 12.sp,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    scope.launch {
                        val cant =
                            repository
                                .sincronizarPendientes()

                        Toast.makeText(
                            context,
                            "$cant registro(s) confirmados en AVIX",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                enabled = !isSyncing,
                shape = RoundedCornerShape(10.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = AviNavy
                    )
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = null,
                        modifier =
                            Modifier.size(16.dp)
                    )
                    Spacer(
                        modifier =
                            Modifier.width(6.dp)
                    )
                    Text(
                        "Sincronizar",
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = lastSyncSummary,
            fontSize = 11.sp,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        OutlinedTextField(
            value = plateQuery,
            onValueChange = {
                plateQuery =
                    it.uppercase()
                        .replace(" ", "")
                        .replace("-", "")
            },
            label = {
                Text("Buscar placa")
            },
            placeholder = {
                Text("Ej: X1Z505")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = fromDate,
                onValueChange = {
                    if (it.length <= 10) {
                        fromDate = it
                    }
                },
                label = {
                    Text("Desde")
                },
                placeholder = {
                    Text("AAAA-MM-DD")
                },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = toDate,
                onValueChange = {
                    if (it.length <= 10) {
                        toDate = it
                    }
                },
                label = {
                    Text("Hasta")
                },
                placeholder = {
                    Text("AAAA-MM-DD")
                },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        filterResult.validationMessage
            ?.let { message ->
                Spacer(
                    modifier =
                        Modifier.height(5.dp)
                )
                Text(
                    text = message,
                    color =
                        MaterialTheme
                            .colorScheme
                            .error,
                    fontSize = 11.sp
                )
            }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(6.dp),
            verticalArrangement =
                Arrangement.spacedBy(4.dp)
        ) {
            HistoryStatusFilter.entries
                .forEach { filter ->
                    FilterChip(
                        selected =
                            filtroSeleccionado ==
                            filter,
                        onClick = {
                            filtroSeleccionado =
                                filter
                        },
                        label = {
                            Text(
                                filter.label,
                                fontSize = 11.sp
                            )
                        }
                    )
                }
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        if (
            incidentesFiltrados.isEmpty()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment =
                    Alignment.Center
            ) {
                Column(
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector =
                            Icons.AutoMirrored
                                .Filled
                                .ListAlt,
                        contentDescription = null,
                        tint =
                            MaterialTheme
                                .colorScheme
                                .outline,
                        modifier =
                            Modifier.size(48.dp)
                    )
                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )
                    Text(
                        text =
                            if (
                                filterResult
                                    .validationMessage !=
                                null
                            ) {
                                "Corrige el filtro de fecha"
                            } else {
                                "No hay registros para estos filtros"
                            },
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                LazyColumn(
                    modifier =
                        Modifier.weight(1f),
                    verticalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        incidentesPagina,
                        key = { it.id }
                    ) { item ->
                        IncidenteCard(
                            item = item,
                            onEdit = {
                                editingIncident =
                                    item
                            }
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                Surface(
                    modifier =
                        Modifier.fillMaxWidth(),
                    shape =
                        RoundedCornerShape(12.dp),
                    color =
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant
                            .copy(alpha = 0.35f)
                ) {
                    Column(
                        modifier =
                            Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 8.dp
                            )
                    ) {
                        Text(
                            text =
                                "Mostrando ${pageResult.fromItem}-${pageResult.toItem} de ${pageResult.totalItems} registros",
                            modifier =
                                Modifier.fillMaxWidth(),
                            textAlign =
                                TextAlign.Center,
                            fontSize = 11.sp,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )

                        Spacer(
                            modifier =
                                Modifier.height(6.dp)
                        )

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    currentPage =
                                        (
                                            currentPage -
                                                1
                                            ).coerceAtLeast(
                                            1
                                        )
                                },
                                enabled =
                                    pageResult.currentPage >
                                    1,
                                modifier =
                                    Modifier.weight(1f)
                            ) {
                                Text(
                                    "Anterior",
                                    fontSize = 12.sp
                                )
                            }

                            Text(
                                text =
                                    "Página ${pageResult.currentPage} de ${pageResult.totalPages}",
                                modifier =
                                    Modifier.weight(1f),
                                textAlign =
                                    TextAlign.Center,
                                fontSize = 12.sp,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            OutlinedButton(
                                onClick = {
                                    currentPage =
                                        (
                                            currentPage +
                                                1
                                            ).coerceAtMost(
                                            pageResult.totalPages
                                        )
                                },
                                enabled =
                                    pageResult.currentPage <
                                    pageResult.totalPages,
                                modifier =
                                    Modifier.weight(1f)
                            ) {
                                Text(
                                    "Siguiente",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editingIncident?.let { item ->
        EditarIncidenciaDialog(
            repository = repository,
            item = item,
            onDismiss = {
                editingIncident = null
            },
            onSaved = {
                editingIncident = null
            }
        )
    }
}

@Composable
fun EditarIncidenciaDialog(
    repository: IncidentRepository,
    item: Incident,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var placaInput by remember(item.id) {
        mutableStateOf(item.placa)
    }
    var viaInput by remember(item.id) {
        mutableStateOf(item.via?.toString() ?: "")
    }
    var accionInput by remember(item.id) {
        mutableStateOf(item.accion)
    }
    var viaMenuExpanded by remember(item.id) {
        mutableStateOf(false)
    }
    var isSaving by remember(item.id) {
        mutableStateOf(false)
    }
    var errorMessage by remember(item.id) {
        mutableStateOf<String?>(null)
    }
    var viasDisponibles by remember(item.id) {
        mutableStateOf<List<ViaDto>>(emptyList())
    }
    var cargandoVias by remember(item.id) {
        mutableStateOf(true)
    }
    var errorVias by remember(item.id) {
        mutableStateOf<String?>(null)
    }
    var recargarVias by remember(item.id) {
        mutableStateOf(0)
    }

    // Refrescar desde AVIX cada vez que se abre la edición; nunca usar
    // la caché local como autorización para modificar una vía.
    LaunchedEffect(item.id, recargarVias) {
        cargandoVias = true
        errorVias = null
        viaMenuExpanded = false

        val response = repository.consultarViasHabilitadasParaEdicion()
        viasDisponibles = response.getOrDefault(emptyList())
        errorVias = response.exceptionOrNull()?.message
        cargandoVias = false
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },
        title = {
            Column {
                Text(
                    text = "Editar registro",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (
                        item.estadoSincronizacion ==
                        EstadoSincronizacion.SINCRONIZADO
                    ) {
                        "La corrección también se actualizará en AVIX."
                    } else {
                        "Se guardará localmente y quedará pendiente de sincronización."
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Acción",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("FUGA", "DERIVADO", "LIBERADO").forEach { action ->
                        FilterChip(
                            selected = accionInput == action,
                            onClick = {
                                accionInput = action
                                errorMessage = null
                            },
                            label = {
                                Text(
                                    text = action,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = placaInput,
                    onValueChange = {
                        placaInput = it
                            .uppercase()
                            .replace(" ", "")
                            .replace("-", "")
                            .take(6)
                        errorMessage = null
                    },
                    label = { Text("Placa") },
                    placeholder = { Text("Ej: I1L110") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Vía habilitada en AVIX",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (cargandoVias) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            "Consultando vías de la plaza...",
                            fontSize = 12.sp
                        )
                    }
                } else if (errorVias != null) {
                    Text(
                        text = errorVias
                            ?: "No se pudieron consultar las vías habilitadas.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { recargarVias++ },
                        enabled = !isSaving
                    ) {
                        Text("Reintentar consulta")
                    }
                } else if (viasDisponibles.isEmpty()) {
                    Text(
                        text = "No hay vías habilitadas para esta plaza. Consulte al supervisor.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { recargarVias++ },
                        enabled = !isSaving
                    ) {
                        Text("Actualizar vías")
                    }
                } else {
                    val viaSeleccionada = viaInput.toIntOrNull()
                        ?.let { numero ->
                            viasDisponibles.firstOrNull { it.numero == numero }
                        }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { viaMenuExpanded = true },
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = viaSeleccionada?.let {
                                    "Vía ${it.numero}" +
                                        (it.nombre?.takeIf(String::isNotBlank)
                                            ?.let { nombre -> " · $nombre" } ?: "")
                                } ?: "Seleccionar vía habilitada",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }

                        DropdownMenu(
                            expanded = viaMenuExpanded,
                            onDismissRequest = { viaMenuExpanded = false }
                        ) {
                            viasDisponibles.forEach { via ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Vía ${via.numero}" +
                                                (via.nombre?.takeIf(String::isNotBlank)
                                                    ?.let { nombre -> " · $nombre" } ?: "")
                                        )
                                    },
                                    onClick = {
                                        viaInput = via.numero.toString()
                                        viaMenuExpanded = false
                                        errorMessage = null
                                    }
                                )
                            }
                        }
                    }

                    if (viaSeleccionada == null && item.via != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "La vía ${item.via} del registro ya no está habilitada. Seleccione otra vía.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                    }
                }

                errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val via = viaInput.toIntOrNull()

                    if (!AviParser.isValidPeruPlate(placaInput)) {
                        errorMessage = "La placa no tiene un formato válido."
                        return@Button
                    }

                    if (via == null || viasDisponibles.none { it.numero == via }) {
                        errorMessage = "Seleccione una vía habilitada por AVIX."
                        return@Button
                    }

                    isSaving = true
                    errorMessage = null

                    scope.launch {
                        // Comprobar de nuevo antes de guardar: una vía pudo
                        // deshabilitarse mientras el diálogo estaba abierto.
                        val consulta = repository.consultarViasHabilitadasParaEdicion()
                        if (consulta.isFailure) {
                            errorVias = consulta.exceptionOrNull()?.message
                                ?: "No se pudieron verificar las vías."
                            isSaving = false
                            return@launch
                        }

                        viasDisponibles = consulta.getOrThrow()
                        if (viasDisponibles.none { it.numero == via }) {
                            errorMessage =
                                "La vía $via fue deshabilitada. Seleccione otra vía."
                            isSaving = false
                            return@launch
                        }

                        val result = repository.actualizarIncidencia(
                            id = item.id,
                            placa = placaInput,
                            via = via,
                            accion = accionInput
                        )

                        isSaving = false

                        if (result.isSuccess) {
                            Toast.makeText(
                                context,
                                "Registro actualizado",
                                Toast.LENGTH_SHORT
                            ).show()
                            onSaved()
                        } else {
                            errorMessage =
                                result.exceptionOrNull()?.message
                                    ?: "No se pudo actualizar el registro."
                        }
                    }
                },
                enabled = !isSaving && !cargandoVias &&
                    errorVias == null && viasDisponibles.isNotEmpty()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(if (isSaving) "Guardando..." else "Guardar")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun IncidenteCard(
    item: Incident,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Placa grande
                Text(
                    text = item.placa,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = AviNavy,
                    letterSpacing = 1.sp
                )

                // Badge Acción: FUGA, DERIVADO o LIBERADO
                val actionColor = when (item.accion.uppercase()) {
                    "FUGA" -> AviActionFuga
                    "LIBERADO" -> AviActionLiberado
                    else -> AviActionDerivado
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = actionColor.copy(alpha = 0.13f)
                ) {
                    Text(
                        text = item.accion,
                        fontWeight = FontWeight.Bold,
                        color = actionColor,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vía ${item.via ?: "---"} • Plaza ${item.plazaId}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color(0xFF334155)
                )

                // Estado de sincronización
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (item.estadoSincronizacion) {
                        EstadoSincronizacion.SINCRONIZADO -> Color(0x22059669)
                        EstadoSincronizacion.PENDIENTE -> Color(0x22D97706)
                        EstadoSincronizacion.SINCRONIZANDO -> Color(0x222563EB)
                        EstadoSincronizacion.REQUIERE_REVISION -> Color(0x22DC2626)
                    }
                ) {
                    Text(
                        text = item.estadoSincronizacion.label,
                        color = when (item.estadoSincronizacion) {
                            EstadoSincronizacion.SINCRONIZADO -> AviStatusOnline
                            EstadoSincronizacion.PENDIENTE -> AviStatusPending
                            EstadoSincronizacion.SINCRONIZANDO -> AviBlueAccent
                            EstadoSincronizacion.REQUIERE_REVISION -> AviStatusOffline
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Evento: ${AviDateUtils.formatIsoToDisplay(item.fechaHoraEvento)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (item.textoReconocido.isNotBlank()) {
                Text(
                    text = "🗣 \"${item.textoReconocido}\"",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            if (item.ultimoError != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Error: ${item.ultimoError}",
                    fontSize = 10.sp,
                    color = Color(0xFFDC2626)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(9.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Editar",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
