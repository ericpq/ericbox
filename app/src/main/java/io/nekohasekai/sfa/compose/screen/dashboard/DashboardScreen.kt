package io.nekohasekai.sfa.compose.screen.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.LineChart
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.navigation.NewProfileArgs
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.constant.Status
import kotlinx.coroutines.launch

data class CardRenderItem(val cards: List<CardGroup>, val isRow: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    serviceStatus: Status = Status.Stopped,
    showStartFab: Boolean = false,
    showStatusBar: Boolean = false,
    onOpenNewProfile: (NewProfileArgs) -> Unit = {},
    viewModel: DashboardViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRunning = serviceStatus == Status.Started
    val isStarting = serviceStatus == Status.Starting
    val isStopping = serviceStatus == Status.Stopping
    val isTransitioning = isStarting || isStopping

    // Update service status in ViewModel
    LaunchedEffect(serviceStatus) {
        viewModel.updateServiceStatus(serviceStatus)
    }

    // Override TopBar with clean minimal style
    OverrideTopBar {
        TopAppBar(
            title = {
                Text("EricBox", fontWeight = FontWeight.Bold)
            },
            actions = {
                if (isRunning) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text("${uiState.connectionsIn.toIntOrNull() ?: 0}")
                    }
                    Spacer(Modifier.width(12.dp))
                }
                IconButton(onClick = { viewModel.toggleCardSettingsDialog() }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
            },
        )
    }

    // Show deprecated notes dialog
    if (uiState.showDeprecatedDialog && uiState.deprecatedNotes.isNotEmpty()) {
        val note = uiState.deprecatedNotes.first()
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.error_deprecated_warning)) },
            text = { Text(note.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDeprecatedNote() }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton =
            if (!note.migrationLink.isNullOrBlank()) {
                {
                    TextButton(onClick = {
                        viewModel.sendGlobalEvent(UiEvent.OpenUrl(note.migrationLink))
                        viewModel.dismissDeprecatedNote()
                    }) {
                        Text(stringResource(R.string.error_deprecated_documentation))
                    }
                }
            } else {
                null
            },
        )
    }

    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    // Dashboard settings bottom sheet
    if (uiState.showCardSettingsDialog) {
        DashboardSettingsBottomSheet(
            sheetState = sheetState,
            visibleCards = uiState.visibleCards,
            cardOrder = uiState.cardOrder,
            onToggleCard = viewModel::toggleCardVisibility,
            onReorderCards = viewModel::reorderCards,
            onResetOrder = viewModel::resetCardOrder,
            onDismiss = {
                scope.launch {
                    sheetState.hide()
                    viewModel.closeCardSettingsDialog()
                }
            },
        )
    }

    // Profile picker sheet
    if (uiState.showAddProfileSheet) {
        ProfilePickerSheet(
            profiles = uiState.profiles,
            selectedProfileId = uiState.selectedProfileId,
            onProfileSelected = { viewModel.selectProfile(it.id) },
            onProfileEdit = viewModel::editProfile,
            onProfileDelete = viewModel::deleteProfile,
            onProfileMove = viewModel::moveProfile,
            onDismiss = viewModel::hideAddProfileSheet,
        )
    }

    val scrollState = rememberScrollState()
    val bottomPadding = when {
        showStartFab -> 88.dp
        showStatusBar -> 74.dp
        else -> 0.dp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .padding(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        // === HERO STATUS CARD (Surfboard style) ===
        HeroStatusCard(
            serviceStatus = serviceStatus,
            profileName = uiState.selectedProfileName ?: stringResource(R.string.profile_empty),
            isTransitioning = isTransitioning,
            onToggleService = { viewModel.sendGlobalEvent(UiEvent.RequestStartService) },
            onProfileClick = viewModel::showAddProfileSheet,
        )

        // === TRAFFIC STATS CARD ===
        if (uiState.trafficVisible) {
            TrafficStatsCard(
                uplink = uiState.uplink,
                downlink = uiState.downlink,
                uplinkTotal = uiState.uplinkTotal,
                downlinkTotal = uiState.downlinkTotal,
                uplinkHistory = uiState.uplinkHistory,
                downlinkHistory = uiState.downlinkHistory,
            )
        }

        // === MODE SELECTOR CARD ===
        if (uiState.clashModeVisible) {
            ModeCard(
                modes = uiState.clashModes,
                selectedMode = uiState.selectedClashMode,
                onModeChange = viewModel::selectClashMode,
            )
        }

        // === QUICK INFO ROW ===
        if (uiState.trafficVisible) {
            QuickInfoRow(
                connectionsIn = uiState.connectionsIn,
                connectionsOut = uiState.connectionsOut,
                memory = uiState.memory,
                goroutines = uiState.goroutines,
            )
        }

        // === SYSTEM PROXY CARD ===
        if (uiState.systemProxyVisible) {
            SystemProxyCard(
                enabled = uiState.systemProxyEnabled,
                isSwitching = uiState.systemProxySwitching,
                onToggle = viewModel::toggleSystemProxy,
            )
        }

        // === PROFILES CARD (keep original for full functionality) ===
        if (!isRunning) {
            ProfilesCard(
                profiles = uiState.profiles,
                selectedProfileId = uiState.selectedProfileId,
                isLoading = uiState.isLoading,
                showAddProfileSheet = false,
                showProfilePickerSheet = uiState.showProfilePickerSheet,
                updatingProfileId = uiState.updatingProfileId,
                updatedProfileId = uiState.updatedProfileId,
                onProfileSelected = viewModel::selectProfile,
                onProfileEdit = viewModel::editProfile,
                onProfileDelete = viewModel::deleteProfile,
                onProfileShare = viewModel::shareProfile,
                onProfileShareURL = viewModel::shareProfileURL,
                onProfileUpdate = viewModel::updateProfile,
                onProfileMove = viewModel::moveProfile,
                onShowAddProfileSheet = viewModel::showAddProfileSheet,
                onHideAddProfileSheet = viewModel::hideAddProfileSheet,
                onShowProfilePickerSheet = viewModel::showProfilePickerSheet,
                onHideProfilePickerSheet = viewModel::hideProfilePickerSheet,
                onOpenNewProfile = onOpenNewProfile,
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ========== Surfboard-style Hero Status Card ==========

@Composable
private fun HeroStatusCard(
    serviceStatus: Status,
    profileName: String,
    isTransitioning: Boolean,
    onToggleService: () -> Unit,
    onProfileClick: () -> Unit,
) {
    val isRunning = serviceStatus == Status.Started

    val gradientColors = if (isRunning) {
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        )
    } else {
        listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isRunning) 4.dp else 1.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradientColors))
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Status Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PulsingDot(
                            color = when (serviceStatus) {
                                Status.Started -> Color(0xFF4CAF50)
                                Status.Starting, Status.Stopping -> Color(0xFFFF9800)
                                Status.Stopped -> Color(0xFFEF5350)
                            },
                            isActive = isRunning,
                        )
                        Text(
                            text = when (serviceStatus) {
                                Status.Started -> "已连接"
                                Status.Starting -> "正在连接..."
                                Status.Stopping -> "正在断开..."
                                Status.Stopped -> "未连接"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isRunning) Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Profile selector
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onProfileClick),
                    color = if (isRunning) Color.White.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Description,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (isRunning) Color.White.copy(alpha = 0.9f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = profileName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isRunning) Color.White
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isRunning) Color.White.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Start/Stop Button
                Button(
                    onClick = onToggleService,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !isTransitioning,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Color.White.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.primary,
                        contentColor = if (isRunning) Color.White
                        else MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (isTransitioning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = if (isRunning) Color.White else MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                    }

                    Icon(
                        if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when (serviceStatus) {
                            Status.Stopped -> "启动"
                            Status.Starting -> "正在启动..."
                            Status.Started -> "停止"
                            Status.Stopping -> "正在停止..."
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

// ========== Traffic Stats Card ==========

@Composable
private fun TrafficStatsCard(
    uplink: String,
    downlink: String,
    uplinkTotal: String,
    downlinkTotal: String,
    uplinkHistory: List<Float>,
    downlinkHistory: List<Float>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "流量统计",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Upload
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "上传",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = uplink,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = uplinkTotal,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(60.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )

                // Download
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "下载",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = downlink,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = downlinkTotal,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Traffic chart
            if (downlinkHistory.any { it > 0f } || uplinkHistory.any { it > 0f }) {
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    LineChart(
                        data = downlinkHistory,
                        lineColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                    )
                    LineChart(
                        data = uplinkHistory,
                        lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                    )
                }
            }
        }
    }
}

// ========== Mode Card ==========

@Composable
private fun ModeCard(
    modes: List<String>,
    selectedMode: String,
    onModeChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "代理模式",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                modes.forEach { mode ->
                    val isSelected = selectedMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { onModeChange(mode) },
                        label = {
                            Text(
                                text = mode,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else null,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            }
        }
    }
}

// ========== Quick Info Row ==========

@Composable
private fun QuickInfoRow(
    connectionsIn: String,
    connectionsOut: String,
    memory: String,
    goroutines: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuickInfoCard(
            icon = Icons.Outlined.SwapVert,
            label = "入站",
            value = connectionsIn,
            modifier = Modifier.weight(1f),
        )
        QuickInfoCard(
            icon = Icons.Outlined.CallMade,
            label = "出站",
            value = connectionsOut,
            modifier = Modifier.weight(1f),
        )
        QuickInfoCard(
            icon = Icons.Outlined.Memory,
            label = "内存",
            value = memory.ifEmpty { "-" },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickInfoCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ========== Pulsing Dot ==========

@Composable
private fun PulsingDot(
    color: Color,
    isActive: Boolean = true,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 0.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (isActive) alpha else 1f)),
    )
}

// ========== Keep these functions for backward compatibility ==========

fun processCardsForRendering(
    cardOrder: List<CardGroup>,
    visibleCards: Set<CardGroup>,
    cardWidths: Map<CardGroup, CardWidth>,
): List<CardRenderItem> {
    val renderItems = mutableListOf<CardRenderItem>()
    val visibleOrderedCards = cardOrder.filter { visibleCards.contains(it) }
    var i = 0
    while (i < visibleOrderedCards.size) {
        val currentCard = visibleOrderedCards[i]
        val currentWidth = cardWidths[currentCard] ?: CardWidth.Full
        if (currentWidth == CardWidth.Half) {
            if (i + 1 < visibleOrderedCards.size) {
                val nextCard = visibleOrderedCards[i + 1]
                val nextWidth = cardWidths[nextCard] ?: CardWidth.Full
                if (nextWidth == CardWidth.Half) {
                    renderItems.add(CardRenderItem(cards = listOf(currentCard, nextCard), isRow = true))
                    i += 2
                    continue
                }
            }
            renderItems.add(CardRenderItem(cards = listOf(currentCard), isRow = false))
        } else {
            renderItems.add(CardRenderItem(cards = listOf(currentCard), isRow = false))
        }
        i++
    }
    return renderItems
}

fun isCardAvailableWhenServiceRunning(cardGroup: CardGroup, uiState: DashboardUiState): Boolean = when (cardGroup) {
    CardGroup.ClashMode -> uiState.clashModeVisible
    CardGroup.UploadTraffic -> uiState.trafficVisible
    CardGroup.DownloadTraffic -> uiState.trafficVisible
    CardGroup.Debug -> true
    CardGroup.Connections -> uiState.trafficVisible
    CardGroup.SystemProxy -> uiState.systemProxyVisible
    CardGroup.Profiles -> true
}
