package com.sauban.stepcounter

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sauban.stepcounter.data.StepSession
import com.sauban.stepcounter.viewmodel.StepViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(viewModel: StepViewModel) {
    val history by viewModel.historySessions.collectAsState()
    val uiState = viewModel.uiState
    var selectedSessions by remember { mutableStateOf(setOf<StepSession>()) }

    // State for retractable graph
    var graphExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(history) {
        selectedSessions = selectedSessions.filter { it in history }.toSet()
    }

    val isSelectionMode = selectedSessions.isNotEmpty()

    val (averageSteps24h, totalStepsStr) = remember(history, uiState.sessionSteps) {
        val totalSteps = history.sumOf { it.steps.toLong() } + uiState.sessionSteps

        val dayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val historyDays = history.map { dayFormatter.format(Date(it.endTime))}.toSet()
        val todayStr = dayFormatter.format(Date())
        val uniqueDaysCount = (historyDays + todayStr).size

        val dailyAvg = if (uniqueDaysCount > 0) {
            (totalSteps / uniqueDaysCount).toInt()
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
            // Selection Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedSessions = emptySet() }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "${selectedSessions.size} Selected",
                        color = MaterialTheme.colorScheme.onBackground,
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
                            text = if (selectedSessions.size == history.size) "Deselect All" else "Select All"
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
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Selected",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        } else {
            // Normal Title
            Text(
                text = "Activity History",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        // Stats Cards
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

        // Retractable Graph Section
        if (history.isNotEmpty()) {
            val arrowRotation by animateFloatAsState(if (graphExpanded) 180f else 0f)

            Surface(
                onClick = { graphExpanded = !graphExpanded },
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "RECENT TREND",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (graphExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(arrowRotation)
                    )
                }
            }

            // Animated retractable container
            Column(modifier = Modifier.animateContentSize()) {
                if (graphExpanded) {
                    SessionGraph(sessions = history.take(7).reversed())
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        Text(
            text = "SESSIONS",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (history.isEmpty() && uiState.sessionSteps == 0) {
            Text(
                text = "No history recorded yet.\nStart walking or press 'Reset & Save' to record a session.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (uiState.sessionSteps > 0) {
                    item {
                        OngoingSessionCard(currentSteps = uiState.sessionSteps)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
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
fun OngoingSessionCard(currentSteps: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
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
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ONGOING SESSION",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "In Progress...",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 13.sp
                )
            }
            Text (
                text = "$currentSteps",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp
            )
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .fillMaxHeight(heightPercentage)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                            )
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
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
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
                        onCheckedChange = { onToggleSelect() }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column {
                    Text(
                        text = dateStr,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$startTimeStr - $endTimeStr",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${session.steps}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp
                )

                if (!isSelectionMode) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Session",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}