package com.zachvlat.footballscores

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zachvlat.footballscores.data.repository.LiveScoresRepository
import com.zachvlat.footballscores.ui.components.*
import com.zachvlat.footballscores.ui.theme.FootballscoresTheme
import com.zachvlat.footballscores.ui.viewmodel.LiveScoresUiState
import com.zachvlat.footballscores.ui.viewmodel.LiveScoresViewModel
import com.zachvlat.footballscores.ui.viewmodel.LiveScoresViewModelFactory
import com.zachvlat.footballscores.ui.viewmodel.MatchDetailState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FootballscoresTheme {
                LiveScoresScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScoresScreen() {
    val repository = remember { LiveScoresRepository() }
    val viewModel: LiveScoresViewModel = viewModel(
        factory = LiveScoresViewModelFactory(repository)
    )
    
    val uiState by viewModel.uiState.collectAsState()
    val currentDate by viewModel.currentDate.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val matchDetailState by viewModel.matchDetailState.collectAsState()
    
    var showDateDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Live Soccer Scores",
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatDateForDisplay(currentDate),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isRefreshing) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "updating...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.alpha(0.7f)
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDateDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Select Date"
                            )
                        }
                        IconButton(
                            onClick = { viewModel.refresh() },
                            enabled = !isRefreshing
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                
                // Search Bar
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = { 
                        keyboardController?.hide()
                    },
                    placeholder = "Search teams...",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is LiveScoresUiState.Loading -> {
                    LoadingIndicator()
                }
                
                is LiveScoresUiState.Success -> {
                    val filteredStages = if (searchQuery.isBlank()) {
                        state.response.Stages
                    } else {
                        state.response.Stages.map { stage ->
                            val filteredEvents = stage.Events.filter { event ->
                                val team1Name = event.T1.firstOrNull()?.Nm?.lowercase() ?: ""
                                val team2Name = event.T2.firstOrNull()?.Nm?.lowercase() ?: ""
                                val query = searchQuery.lowercase()
                                
                                team1Name.contains(query) || team2Name.contains(query)
                            }
                            
                            // Create new Stage object with filtered events - use all original stage properties
                            com.zachvlat.footballscores.data.model.Stage(
                                Sid = stage.Sid ?: "",
                                Snm = stage.Snm ?: "",
                                Scd = stage.Scd ?: "",
                                Cnm = stage.Cnm ?: "",
                                CnmT = stage.CnmT ?: "",
                                Csnm = stage.Csnm ?: "",
                                Ccd = stage.Ccd ?: "",
                                CompId = stage.CompId ?: "",
                                CompN = stage.CompN ?: "",
                                CompUrlName = stage.CompUrlName ?: "",
                                CompD = stage.CompD ?: "",
                                CompST = stage.CompST ?: "",
                                Scu = stage.Scu ?: 0,
                                badgeUrl = stage.badgeUrl,
                                firstColor = stage.firstColor ?: "",
                                Events = filteredEvents
                            )
                        }.filter { it.Events.isNotEmpty() }
                    }
                    
                    if (filteredStages.isEmpty()) {
                        EmptyState(
                            message = if (searchQuery.isBlank()) 
                                "No matches found for selected date" 
                            else 
                                "No matches found for \"$searchQuery\"",
                            onRefresh = { viewModel.refresh() }
                        )
                    } else {
                        MatchList(stages = filteredStages, viewModel)
                    }
                }
                
                is LiveScoresUiState.Error -> {
                    ErrorMessage(
                        message = state.message,
                        onRefresh = { viewModel.refresh() }
                    )
                }
            }
        }
    }
    
    if (showDateDialog) {
        DateSelectionDialog(
            onDateSelected = { date ->
                viewModel.loadScoresForDate(date)
                showDateDialog = false
            },
            onDismiss = { showDateDialog = false },
            viewModel = viewModel
        )
    }
    
    // Match Detail Popup
    when (val state = matchDetailState) {
        is MatchDetailState.Success -> {
            MatchDetailPopup(
                matchDetail = state.matchDetail,
                onDismiss = { viewModel.dismissMatchDetail() }
            )
        }
        is MatchDetailState.Loading -> {
            Dialog(onDismissRequest = { viewModel.dismissMatchDetail() }) {
                Card(
                    modifier = Modifier
                        .size(100.dp)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
        is MatchDetailState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissMatchDetail() },
                title = { Text("Error") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissMatchDetail() }) {
                        Text("OK")
                    }
                }
            )
        }
        else -> { /* Hidden state - do nothing */ }
    }
}

@Composable
fun MatchList(stages: List<com.zachvlat.footballscores.data.model.Stage>, viewModel: LiveScoresViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        stages.forEach { stage ->
            if (stage.Events.isNotEmpty()) {
                // Competition Header
                item {
                    CompetitionHeader(stage = stage)
                }
                
                // Matches for this competition
                items(stage.Events) { event ->
                    MatchCard(event = event, onMatchClick = { matchId ->
                        viewModel.onMatchClick(matchId)
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(
            onSearch = { onSearch(query) }
        ),
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun EmptyState(message: String, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Refresh")
        }
    }
}

@Composable
fun DateSelectionDialog(
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: LiveScoresViewModel
) {
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Date") },
        text = {
            Column {
                TextButton(onClick = {
                    onDateSelected(viewModel.getTodayDateString())
                }) {
                    Text("Today")
                }
                TextButton(onClick = {
                    onDateSelected(viewModel.getYesterdayDateString())
                }) {
                    Text("Yesterday")
                }
                TextButton(onClick = {
                    onDateSelected(viewModel.getTomorrowDateString())
                }) {
                    Text("Tomorrow")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun formatDateForDisplay(dateString: String): String {
    return when {
        dateString.length == 8 -> {
            val year = dateString.substring(0, 4)
            val month = dateString.substring(4, 6)
            val day = dateString.substring(6, 8)
            "$year-$month-$day"
        }
        else -> dateString
    }
}