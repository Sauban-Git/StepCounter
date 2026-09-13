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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.sauban.stepcounter.data.StepSession
import com.sauban.stepcounter.ui.theme.StepCounterTheme
import com.sauban.stepcounter.viewmodel.StepViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StepCounterTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val viewModel: StepViewModel = viewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF121212),
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF1E1E1E),
                contentColor = Color.White
            ) {
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
                    icon = { Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = "Home") },
                    label = { Text("Tracker") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF64FFDA),
                        unselectedIconColor = Color.Gray,
                        indicatorColor = Color(0xFF00BFA5).copy(alpha = 0.2f)
                    )
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
                    label = { Text("History") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF64FFDA),
                        unselectedIconColor = Color.Gray,
                        indicatorColor = Color(0xFF00BFA5).copy(alpha = 0.2f)
                    )
                )
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
        }
    }
}

@Composable
fun HybridStepScreen(modifier: Modifier = Modifier, viewModel: StepViewModel) {
    val context = LocalContext.current
    val uiState = viewModel.uiState
    var permissionsGranted by remember { mutableStateOf(false) }

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
        Text(
            text = "Hybrid Step Counter",
            style = MaterialTheme.typography.titleMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        when {
            !uiState.sensorPresent -> {
                WarningCard(
                    icon = Icons.Default.Error,
                    iconTint = Color(0xFFEF5350),
                    background = Color(0xFFD32F2F).copy(alpha = 0.2f),
                    text = "Hardware step sensor not found on this device."
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            !uiState.activityRecognitionAvailable -> {
                WarningCard(
                    icon = Icons.Default.Warning,
                    iconTint = Color(0xFFFFCA28),
                    background = Color(0xFFF9A825).copy(alpha = 0.15f),
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
            color = Color.White,
            letterSpacing = (-2).sp
        )
        Text(
            text = "SESSION STEPS",
            fontSize = 16.sp,
            letterSpacing = 4.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(40.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF00BFA5).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.54f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "HOW THIS COUNTS",
                    color = Color(0xFF64FFDA),
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
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Status: ${uiState.statusMessage}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFFFD54F)
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        Button(
            onClick = { viewModel.resetSession() },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00897B),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(30.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "RESET & SAVE SESSION", fontWeight = FontWeight.Bold)
        }
    }
}

private fun startStepService(context: Context) {
    val serviceIntent = Intent(context, StepCounterService::class.java)
    ContextCompat.startForegroundService(context, serviceIntent)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(viewModel: StepViewModel) {
    val history by viewModel.historySessions.collectAsState()
    var selectedSessions by remember { mutableStateOf(setOf<StepSession>()) }

    LaunchedEffect(history) {
        selectedSessions = selectedSessions.filter { it in history }.toSet()
    }

    val isSelectionMode = selectedSessions.isNotEmpty()

    // fixed: avg calculation
    val (averageSteps24h, totalStepsStr) = remember(history) {
        if (history.isEmpty()) return@remember "0" to "0"

        val totalSteps = history.sumOf { it.steps.toLong() }

        // distinct calendar dates
        val dayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val uniqueCalendarDaysCount = history
            .map { dayFormatter.format(Date(it.endTime)) }
            .toSet()
            .size

        val dailyAvg = if (uniqueCalendarDaysCount > 0) {
            (totalSteps / uniqueCalendarDaysCount).toInt()
        } else {
            0
        }

        dailyAvg.toString() to totalSteps.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (isSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedSessions = emptySet() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                    }
                    Text(
                        text = "${selectedSessions.size} Selected",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            selectedSessions = if (selectedSessions.size == history.size) emptySet() else history.toSet()
                        }
                    ) {
                        Text(
                            text = if (selectedSessions.size == history.size) "Deselect All" else "Select All",
                            color = Color(0xFF64FFDA)
                        )
                    }

                    IconButton(
                        onClick = {
                            selectedSessions.forEach { session ->
                                viewModel.deleteSession(session)
                            }
                            selectedSessions = emptySet()
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color(0xFFEF5350))
                    }
                }
            }
        } else {
            Text(
                text = "Activity History",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = "DAILY AVG",
                value = averageSteps24h,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "TOTAL STEPS",
                value = totalStepsStr,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (history.isNotEmpty()) {
            Text(
                text = "RECENT TREND",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            SessionGraph(sessions = history.take(7).reversed())
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "PREVIOUS SESSIONS",
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (history.isEmpty()) {
            Text(
                text = "No history recorded yet.\nPress 'Reset Session' to save your first record.",
                color = Color.Gray,
                modifier = Modifier.padding(top = 20.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(history, key = { it.startTime }) { session ->
                    val isSelected = selectedSessions.contains(session)
                    SessionHistoryItem(
                        session = session,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onToggleSelect = {
                            selectedSessions = if (isSelected) {
                                selectedSessions - session
                            } else {
                                selectedSessions + session
                            }
                        },
                        onDelete = {
                            viewModel.deleteSession(session)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = title, color = Color(0xFF00BFA5), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun SessionGraph(sessions: List<StepSession>) {
    val maxSteps = max(sessions.maxOfOrNull { it.steps } ?: 1, 100)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            sessions.forEach { session ->
                val heightPercentage = (session.steps.toFloat() / maxSteps).coerceIn(0f, 1f)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .fillMaxHeight(heightPercentage)
                            .background(Color(0xFF64FFDA), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionHistoryItem(
    session: StepSession,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    val dateStr = remember(session.endTime) { dateFormat.format(Date(session.endTime)) }
    val startTimeStr = remember(session.startTime) { timeFormat.format(Date(session.startTime)) }
    val endTimeStr = remember(session.endTime) { timeFormat.format(Date(session.endTime)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelect()
                    }
                },
                onLongClick = {
                    onToggleSelect()
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF264653) else Color(0xFF1E1E1E)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF64FFDA),
                            checkmarkColor = Color.Black
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column {
                    Text(text = dateStr, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "$startTimeStr - $endTimeStr", color = Color.Gray, fontSize = 12.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${session.steps}",
                    color = Color(0xFF64FFDA),
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp
                )

                if (!isSelectionMode) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Session",
                            tint = Color.Gray.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningCard(
    icon: ImageVector,
    iconTint: Color,
    background: Color,
    text: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = background),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = text, color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ActivityBadge(label: String, granted: Boolean) {
    val (bg, fg, icon) = when {
        !granted -> Triple(Color.Gray.copy(alpha = 0.2f), Color.Gray, Icons.Default.Warning)
        label == "Running" -> Triple(
            Color(0xFF00BFA5).copy(alpha = 0.2f), Color(0xFF64FFDA),
            Icons.AutoMirrored.Filled.DirectionsRun
        )
        label == "Walking" || label == "On Foot" -> Triple(
            Color(0xFF00BFA5).copy(alpha = 0.2f), Color(0xFF64FFDA),
            Icons.AutoMirrored.Filled.DirectionsWalk
        )
        else -> Triple(Color.Gray.copy(alpha = 0.2f), Color.Gray,
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