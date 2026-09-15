package com.sauban.stepcounter

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sauban.stepcounter.ui.theme.StepCounterTheme
import com.sauban.stepcounter.viewmodel.StepViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            StepCounterTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val viewModel: StepViewModel = viewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    val settingsRepository = remember { com.sauban.stepcounter.data.SettingsRepository(context) }
    val settingsViewModel: com.sauban.stepcounter.viewmodel.SettingsViewModel = viewModel(
        factory = com.sauban.stepcounter.viewmodel.SettingsViewModelFactory(settingsRepository)
    )

    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (currentRoute != "settings") {
                TopAppBar(
                    title = {
                        Text(
                            text = if (currentRoute == "home") "Step Counter" else "History",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (currentRoute != "settings") {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == "home",
                        onClick = {
                            navController.navigate("home") {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.DirectionsRun,
                                contentDescription = "Home"
                            )
                        },
                        label = { Text("Tracker") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "history",
                        onClick = {
                            navController.navigate("history") {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.BarChart, contentDescription = "History") },
                        label = { Text("History") }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HybridStepScreen(viewModel = viewModel)
            }
            composable("history") {
                HistoryScreen(viewModel = viewModel)
            }
            composable("settings") {
                SettingsScreen(
                    onBackClick = { navController.popBackStack() },
                    viewModel = settingsViewModel
                )
            }
        }
    }
}

@Composable
fun HybridStepScreen(modifier: Modifier = Modifier, viewModel: StepViewModel) {
    val context = LocalContext.current
    val uiState = viewModel.uiState
    var permissionsGranted by remember { mutableStateOf(false) }

    var isPaused = uiState.isPaused

    val permissionsList = remember {
        mutableListOf<String>().apply {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val activityGranted = permissions[Manifest.permission.ACTIVITY_RECOGNITION] ?: true
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: true
        } else true

        if (activityGranted && notificationGranted) {
            permissionsGranted = true
            startStepService(context)
        }
    }

    LaunchedEffect(Unit) {
        val hasActivity = ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

        val hasNotification = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        if (hasActivity && hasNotification) {
            permissionsGranted = true
            startStepService(context)
        } else if (permissionsList.isNotEmpty()) {
            permissionLauncher.launch(permissionsList.toTypedArray())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            !uiState.sensorPresent -> {
                WarningCard(
                    icon = Icons.Default.Error,
                    iconTint = MaterialTheme.colorScheme.error,
                    background = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    text = "Hardware step sensor not found on this device."
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            !uiState.activityRecognitionAvailable -> {
                WarningCard(
                    icon = Icons.Default.Warning,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    background = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    text = "Activity Recognition unavailable — counting raw hardware steps."
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        ActivityBadge(label = uiState.activityLabel, granted = permissionsGranted)
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "${uiState.sessionSteps}",
            fontSize = 90.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = (-2).sp
        )
        Text(
            text = "SESSION STEPS",
            fontSize = 16.sp,
            letterSpacing = 4.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(40.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "HOW THIS COUNTS",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Base count from hardware sensor.\n" +
                            "• Google API confirms Walking/Running.\n" +
                            "• Filtered while Still or In Vehicle.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text (
                    text = "Status: ${if (isPaused) "Paused" else uiState.statusMessage}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play / Pause Button
            Button(
                onClick = {
                    isPaused = !isPaused
                    viewModel.togglePauseResume()
                },
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPaused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isPaused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) "Play" else "Pause"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPaused) "RESUME" else "PAUSE",
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = { viewModel.resetSession() },
                shape = RoundedCornerShape(30.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "SAVE & RESET", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun startStepService(context: Context) {
    val serviceIntent = Intent(context, StepCounterService::class.java)
    ContextCompat.startForegroundService(context, serviceIntent)
}

@Composable
private fun WarningCard(
    icon: ImageVector,
    iconTint: Color,
    background: Color,
    contentColor: Color,
    text: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = background,
            contentColor = contentColor
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = text, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ActivityBadge(label: String, granted: Boolean) {
    val (bg, fg, icon) = when {
        !granted -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.Warning
        )
        label == "Running" -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary,
            Icons.AutoMirrored.Filled.DirectionsRun
        )
        label == "Walking" || label == "On Foot" -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary,
            Icons.AutoMirrored.Filled.DirectionsWalk
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.AutoMirrored.Filled.DirectionsWalk
        )
    }

    Box(
        modifier = Modifier
            .background(color = bg, shape = RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = fg)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (!granted) "Permission Required" else label,
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}