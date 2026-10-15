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

@Composable
fun AviRootNavHost() {
    val context = LocalContext.current
    val repository = remember { IncidentRepository.getInstance(context) }
    val sessionState by repository.sessionState.collectAsStateWithLifecycle()

    // Manejo de eventos de sesión expirada
    LaunchedEffect(Unit) {
        repository.sessionExpiredEvent.collect { mensaje ->
            Toast.makeText(context, mensaje, Toast.LENGTH_LONG).show()
        }
    }

    if (!sessionState.isLoggedIn) {
        AviLoginScreen(repository = repository)
    } else {
        AviMainDashboardScaffold(repository = repository)
    }
}

// =========================================================================
// PANTALLA 1: LOGIN (Autenticación por código de trabajador)
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AviLoginScreen(repository: IncidentRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var codigoInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var tempUrl by remember { mutableStateOf(repository.getBaseUrl()) }

    val connectionState by repository.connectionState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.avix_logo),
                contentDescription = "AVIX",
                modifier = Modifier.size(118.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Bienvenido",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Asistente operativo de voz",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Acceso",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Ingresa tu código de trabajador para continuar.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedTextField(
                        value = codigoInput,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 8) {
                                codigoInput = input
                                errorMessage = null
                            }
                        },
                        label = { Text("Código de usuario") },
                        placeholder = { Text("Ingresa tu código") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Badge,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("worker_code_input")
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFDECEC)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFD64545),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errorMessage!!,
                                    color = Color(0xFFD64545),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            val cod = codigoInput.toIntOrNull()
                            if (cod == null || cod <= 0) {
                                errorMessage = "Ingresa un código válido."
                                return@Button
                            }

                            isLoading = true
                            errorMessage = null

                            scope.launch {
                                val result = repository.login(cod)
                                isLoading = false
                                if (result.isFailure) {
                                    errorMessage = result.exceptionOrNull()?.message
                                        ?: "No se pudo iniciar sesión."
                                } else {
                                    Toast.makeText(context, "Bienvenido a AVIX", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isLoading && codigoInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("login_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verificando...")
                        } else {
                            Text("Ingresar", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (connectionState) {
                                    ApiConnectionState.ONLINE -> AviStatusOnline
                                    ApiConnectionState.OFFLINE -> AviStatusOffline
                                    ApiConnectionState.SIN_CONFIGURAR -> AviStatusPending
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (connectionState) {
                            ApiConnectionState.ONLINE -> "Servidor disponible"
                            ApiConnectionState.OFFLINE -> "Servidor sin conexión"
                            ApiConnectionState.SIN_CONFIGURAR -> "Servidor por configurar"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = { showUrlDialog = true },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Servidor", fontSize = 11.sp)
                }
            }
        }
    }

    if (showUrlDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Configurar servidor") },
            text = {
                Column {
                    Text(
                        "AVIX usa SIGO-BACK desplegado en Railway. Las opciones locales quedan solo para desarrollo.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        label = { Text("URL base") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            tempUrl = SigoApiService.PRODUCTION_BASE_URL
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Usar SIGO Producción", fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                tempUrl = SigoApiService.LOCAL_MAC_BASE_URL
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Mac local", fontSize = 10.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                tempUrl = SigoApiService.EMULATOR_BASE_URL
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Emulador", fontSize = 10.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.updateConfig(tempUrl)
                        repository.verificarConexionSigo()
                        showUrlDialog = false
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUrlDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
