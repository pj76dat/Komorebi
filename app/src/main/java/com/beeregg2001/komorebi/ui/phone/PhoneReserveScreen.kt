package com.beeregg2001.komorebi.ui.phone

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.scale
import com.beeregg2001.komorebi.data.model.ReservationCondition
import com.beeregg2001.komorebi.data.model.ReserveItem
import com.beeregg2001.komorebi.data.model.ReserveRecordSettings
import com.beeregg2001.komorebi.viewmodel.ReserveViewModel
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PhoneReserveScreen(
    reserveViewModel: ReserveViewModel
) {
    val reserves by reserveViewModel.reserves.collectAsState()
    val normalReserves by reserveViewModel.normalReserves.collectAsState()
    val conditions by reserveViewModel.conditions.collectAsState()
    val isLoading by reserveViewModel.isLoading.collectAsState()
    val selectedTabIndex by reserveViewModel.selectedTabIndex.collectAsState()

    // アクションメニューの状態
    var selectedReserve by remember { mutableStateOf<ReserveItem?>(null) }
    var showActionMenu by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // ★ 条件編集用の状態
    var selectedCondition by remember { mutableStateOf<ReservationCondition?>(null) }
    var showConditionEditSheet by remember { mutableStateOf(false) }
    var showConditionDeleteConfirm by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    val reserveListState = rememberLazyListState()
    val normalReserveListState = rememberLazyListState()

    // ★ 全ての予約タブ用: チャンネル別・ジャンル別フィルター（録画リストの
    // ジャンル別と同じ、genres の先頭要素の major を使う方式）。
    // 予約データは既に画面内にあるので、ViewModel には手を入れずクライアント側で
    // 完結させている。
    var selectedReserveGenre by remember { mutableStateOf<String?>(null) }
    var selectedReserveChannelId by remember { mutableStateOf<String?>(null) }

    val reserveGenreOf: (ReserveItem) -> String = remember {
        { it.program.genres?.firstOrNull()?.major ?: "その他" }
    }
    val availableReserveGenres = remember(reserves) {
        reserves.map(reserveGenreOf).distinct().sorted()
    }
    val availableReserveChannels = remember(reserves) {
        reserves.map { it.channel.name to it.channel.id }.distinct().sortedBy { it.first }
    }
    val filteredReserves = remember(reserves, selectedReserveGenre, selectedReserveChannelId) {
        reserves.filter { r ->
            (selectedReserveGenre == null || reserveGenreOf(r) == selectedReserveGenre) &&
                (selectedReserveChannelId == null || r.channel.id == selectedReserveChannelId)
        }
    }

    LaunchedEffect(Unit) { reserveViewModel.refreshAll() }

    val tabs = listOf("全ての予約", "通常予約", "キーワード自動予約")

    Column(modifier = Modifier.fillMaxSize()) {

        // ─── Tabs ──────────────────────────────────────────────
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = {
                        reserveViewModel.updateTabIndex(index)
                        reserveViewModel.clearFocusMemory()
                    },
                    text = { Text(title, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        when (selectedTabIndex) {
            0 -> Column(modifier = Modifier.fillMaxSize()) {
                // ─ Channel filter ──────────────────────────────
                if (availableReserveChannels.isNotEmpty()) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(selected = selectedReserveChannelId == null,
                                onClick = { selectedReserveChannelId = null },
                                label = { Text("全チャンネル") })
                        }
                        items(availableReserveChannels) { (name, id) ->
                            FilterChip(selected = selectedReserveChannelId == id,
                                onClick = { selectedReserveChannelId = id },
                                label = { Text(name, maxLines = 1) })
                        }
                    }
                }
                // ─ Genre filter ────────────────────────────────
                if (availableReserveGenres.isNotEmpty()) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(selected = selectedReserveGenre == null,
                                onClick = { selectedReserveGenre = null },
                                label = { Text("全ジャンル") })
                        }
                        items(availableReserveGenres) { genre ->
                            FilterChip(selected = selectedReserveGenre == genre,
                                onClick = { selectedReserveGenre = genre },
                                label = { Text(genre) })
                        }
                    }
                }
                if (availableReserveChannels.isNotEmpty() || availableReserveGenres.isNotEmpty()) {
                    HorizontalDivider()
                }

                PhoneReserveList(
                    items = filteredReserves,
                    emptyMessage = if (selectedReserveGenre != null || selectedReserveChannelId != null)
                        "条件に一致する予約がありません" else "予約がありません",
                    reserveViewModel = reserveViewModel,
                    listState = reserveListState,
                    onItemClick = { reserve ->
                        selectedReserve = reserve
                        showActionMenu = true
                    }
                )
            }
            1 -> PhoneReserveList(
                items = normalReserves,
                emptyMessage = "通常予約がありません",
                reserveViewModel = reserveViewModel,
                listState = normalReserveListState,
                onItemClick = { reserve ->
                    selectedReserve = reserve
                    showActionMenu = true
                }
            )
            2 -> PhoneConditionList(
                conditions = conditions,
                emptyMessage = "キーワード自動予約がありません",
                onItemClick = { condition ->
                    selectedCondition = condition
                    showConditionEditSheet = true
                }
            )
        }
    }

    // ─── Action menu bottom sheet ─────────────────────────────
    if (showActionMenu && selectedReserve != null) {
        ModalBottomSheet(onDismissRequest = { showActionMenu = false }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                // タイトル
                Text(
                    text = selectedReserve!!.program.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                HorizontalDivider()

                // 予約設定変更
                ListItem(
                    headlineContent = { Text("予約設定変更") },
                    leadingContent = {
                        Icon(Icons.Default.Edit, null,
                            tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable {
                        showActionMenu = false
                        showSettingsDialog = true
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // 予約を削除
                ListItem(
                    headlineContent = {
                        Text("予約を削除", color = MaterialTheme.colorScheme.error)
                    },
                    leadingContent = {
                        Icon(Icons.Default.Delete, null,
                            tint = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.clickable {
                        showActionMenu = false
                        showDeleteConfirm = true
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // 戻る
                ListItem(
                    headlineContent = { Text("戻る") },
                    leadingContent = {
                        Icon(Icons.Default.ArrowBack, null)
                    },
                    modifier = Modifier.clickable { showActionMenu = false }
                )
            }
        }
    }

    // ─── Delete confirmation dialog ───────────────────────────
    if (showDeleteConfirm && selectedReserve != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("予約の削除") },
            text = { Text("「${selectedReserve!!.program.title}」の予約を削除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        reserveViewModel.deleteReservation(selectedReserve!!.id) {
                            showDeleteConfirm = false
                            selectedReserve = null
                        }
                    }
                ) { Text("削除する", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("キャンセル") }
            }
        )
    }

    // ─── Settings dialog ──────────────────────────────────────
    if (showSettingsDialog && selectedReserve != null) {
        PhoneReserveSettingsDialog(
            reserve = selectedReserve!!,
            onDismiss = { showSettingsDialog = false },
            onSave = { newSettings ->
                reserveViewModel.updateReservation(selectedReserve!!, newSettings) {
                    showSettingsDialog = false
                    selectedReserve = null
                }
            }
        )
    }

    // ─── Condition edit sheet ──────────────────────────────────
    if (showConditionEditSheet && selectedCondition != null) {
        PhoneConditionEditSheet(
            condition = selectedCondition!!,
            onDismiss = { showConditionEditSheet = false },
            onSave = { isEnabled, keyword, daysOfWeek, startHour, endHour, priority, isEventRelay ->
                val c = selectedCondition!!
                val psc = c.programSearchCondition
                val firstRange = psc.dateRanges?.firstOrNull()
                reserveViewModel.updateEpgReserve(
                    originalCondition = c,
                    isEnabled = isEnabled,
                    keyword = keyword,
                    daysOfWeek = daysOfWeek,
                    startHour = startHour,
                    startMinute = firstRange?.startMinute ?: 0,
                    endHour = endHour,
                    endMinute = firstRange?.endMinute ?: 0,
                    // ★ このダイアログでは扱わない詳細設定は、既存の値をそのまま維持する
                    excludeKeyword = psc.excludeKeyword,
                    isTitleOnly = psc.isTitleOnly,
                    broadcastType = psc.broadcastType,
                    isFuzzySearch = psc.isFuzzySearchEnabled,
                    duplicateScope = psc.duplicateTitleCheckScope,
                    priority = priority,
                    isEventRelay = isEventRelay,
                    isExactRecord = c.recordSettings.isExactRecordingEnabled,
                    onSuccess = {
                        showConditionEditSheet = false
                        selectedCondition = null
                        toastMessage = "予約条件を更新しました"
                    }
                )
            },
            onDeleteRequested = {
                showConditionEditSheet = false
                showConditionDeleteConfirm = true
            }
        )
    }

    // ─── Condition delete confirmation ─────────────────────────
    if (showConditionDeleteConfirm && selectedCondition != null) {
        var deleteRelated by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showConditionDeleteConfirm = false },
            title = { Text("予約条件の削除") },
            text = {
                Column {
                    Text("「${selectedCondition!!.programSearchCondition.keyword}」の予約条件を削除しますか？")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { deleteRelated = !deleteRelated }
                    ) {
                        Checkbox(checked = deleteRelated, onCheckedChange = { deleteRelated = it })
                        Text("この条件で作られた予約もすべて削除する")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        reserveViewModel.deleteConditionWithCleanup(
                            condition = selectedCondition!!,
                            deleteRelatedReserves = deleteRelated,
                            onSuccess = {
                                showConditionDeleteConfirm = false
                                selectedCondition = null
                                toastMessage = if (deleteRelated)
                                    "条件と関連する予約をすべて削除しました" else "予約条件を削除しました"
                            }
                        )
                    }
                ) { Text("削除する", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showConditionDeleteConfirm = false }) { Text("キャンセル") }
            }
        )
    }

    // ─── Toast ──────────────────────────────────────────────────
    toastMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2000)
            toastMessage = null
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(modifier = Modifier.padding(16.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.inverseSurface) {
                Text(msg, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
    }
}

// ─── Reserve List ─────────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PhoneReserveList(
    items: List<ReserveItem>,
    emptyMessage: String,
    reserveViewModel: ReserveViewModel,
    listState: LazyListState,
    onItemClick: (ReserveItem) -> Unit
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 16.dp)) {
            items(items, key = { it.id }) { reserve ->
                PhoneReserveListItem(
                    reserve = reserve,
                    reserveViewModel = reserveViewModel,
                    onClick = { onItemClick(reserve) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PhoneReserveListItem(
    reserve: ReserveItem,
    reserveViewModel: ReserveViewModel,
    onClick: () -> Unit ) {
    val timeFmt = DateTimeFormatter.ofPattern("MM/dd (E) HH:mm", Locale.JAPANESE)
    val dateStr = remember(reserve.program.startTime) {
        try {
            val dt = OffsetDateTime.parse(reserve.program.startTime)
                .atZoneSameInstant(ZoneId.systemDefault())
            timeFmt.format(dt)
        } catch (e: Exception) { "" }
    }

    val statusColor = when {
        reserve.isRecordingInProgress -> MaterialTheme.colorScheme.error
        reserve.recordingAvailability == "Full" -> MaterialTheme.colorScheme.primary
        reserve.recordingAvailability == "Partial" -> MaterialTheme.colorScheme.tertiary
        reserve.recordingAvailability == "None" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusLabel = when {
        reserve.isRecordingInProgress -> "録画中"
        reserve.recordingAvailability == "Full" -> "録画可能"
        reserve.recordingAvailability == "Partial" -> "一部のみ"
        reserve.recordingAvailability == "None" -> "録画重複"
        else -> reserve.recordingAvailability
    }

    ListItem(
        headlineContent = {
            Text(reserve.program.title, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    buildString {
                        if (dateStr.isNotEmpty()) append(dateStr)
                        if (reserve.channel.name.isNotEmpty()) {
                            if (isNotEmpty()) append(" • ")
                            append(reserve.channel.name)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Surface(shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.15f)) {
                    Text(statusLabel, style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        },
        leadingContent = {
            if (reserve.isRecordingInProgress) {
                Icon(Icons.Default.FiberManualRecord, null,
                    tint = MaterialTheme.colorScheme.error)
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(48.dp)
                ) {
                    // ★ 「有効」の時は「録画中」の赤と紛れないよう、薄いピンクで
                    // 目立たせる。「無効」の配色は変更しない。
                    val isEnabled = reserve.recordSettings?.isEnabled ?: true
                    val enabledPink = Color(0xFFF48FB1)
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { enabled ->
                            val newSettings = (reserve.recordSettings ?: ReserveRecordSettings())
                                .copy(isEnabled = enabled)
                            reserveViewModel.updateReservation(reserve, newSettings) {}
                        },
                        colors = if (isEnabled) {
                            SwitchDefaults.colors(
                                checkedThumbColor = enabledPink,
                                checkedTrackColor = enabledPink.copy(alpha = 0.5f),
                                checkedBorderColor = enabledPink
                            )
                        } else {
                            SwitchDefaults.colors()
                        },
                        modifier = Modifier.scale(0.8f)
                    )
                    Text(
                        text = if (isEnabled) "有効" else "無効",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isEnabled) enabledPink
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

// ─── Reserve Settings Dialog ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneReserveSettingsDialog(
    reserve: ReserveItem,
    onDismiss: () -> Unit,
    onSave: (ReserveRecordSettings) -> Unit
) {
    val current = reserve.recordSettings
    var isEnabled by remember { mutableStateOf(current?.isEnabled ?: true) }
    var priority by remember { mutableStateOf(current?.priority ?: 3) }
    var isEventRelay by remember {
        mutableStateOf(current?.isEventRelayFollowEnabled ?: true)
    }
    val batFilePath = current?.postRecordingBatFilePath ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("予約設定の変更") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 予約を有効にする
                Row(modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("予約を有効にする", modifier = Modifier.weight(1f))
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }

                HorizontalDivider()

                // 優先度 (1-5)
                Column {
                    Text("優先度: $priority",
                        style = MaterialTheme.typography.bodyMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        (1..5).forEach { p ->
                            FilterChip(
                                selected = priority == p,
                                onClick = { priority = p },
                                label = { Text("$p") }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // 録画後実行バッチ
                Row(modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("録画後実行バッチ",
                            style = MaterialTheme.typography.bodyMedium)
                        Text(
                            batFilePath.ifEmpty { "なし" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                // イベントリレー設定
                Row(modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("イベントリレー設定", modifier = Modifier.weight(1f))
                    Switch(checked = isEventRelay,
                        onCheckedChange = { isEventRelay = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    (current ?: ReserveRecordSettings()).copy(
                        isEnabled = isEnabled,
                        priority = priority,
                        isEventRelayFollowEnabled = isEventRelay
                    )
                )
            }) { Text("設定を更新") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

// ─── Condition List ───────────────────────────────────────────────────────────

@Composable
private fun PhoneConditionList(
    conditions: List<ReservationCondition>,
    emptyMessage: String,
    onItemClick: (ReservationCondition) -> Unit
) {
    if (conditions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)) {
        items(conditions, key = { it.id }) { condition ->
            val psc = condition.programSearchCondition
            ListItem(
                headlineContent = {
                    Text(psc.keyword,
                        color = if (psc.isEnabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                },
                supportingContent = {
                    Text(if (psc.isEnabled) "有効・${condition.reservationCount}件の予約"
                         else "無効",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                leadingContent = {
                    Icon(Icons.Default.Search, null,
                        tint = if (psc.isEnabled) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingContent = {
                    Icon(Icons.Default.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                modifier = Modifier.clickable { onItemClick(condition) }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

// ─── Condition Edit Sheet ───────────────────────────────────────────────────

private val CONDITION_DAYS = listOf("日", "月", "火", "水", "木", "金", "土")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneConditionEditSheet(
    condition: ReservationCondition,
    onDismiss: () -> Unit,
    onSave: (
        isEnabled: Boolean, keyword: String, daysOfWeek: Set<Int>,
        startHour: Int, endHour: Int, priority: Int, isEventRelay: Boolean
    ) -> Unit,
    onDeleteRequested: () -> Unit
) {
    val psc = condition.programSearchCondition
    val firstRange = psc.dateRanges?.firstOrNull()

    var isEnabled by remember { mutableStateOf(psc.isEnabled) }
    var keyword by remember { mutableStateOf(psc.keyword) }
    var selectedDays by remember {
        mutableStateOf(psc.dateRanges?.map { it.startDayOfWeek }?.toSet() ?: setOf(0))
    }
    var startHour by remember { mutableIntStateOf(firstRange?.startHour ?: 0) }
    var endHour by remember { mutableIntStateOf(firstRange?.endHour ?: 23) }
    var priority by remember { mutableStateOf(condition.recordSettings.priority) }
    var isEventRelay by remember { mutableStateOf(condition.recordSettings.isEventRelayFollowEnabled) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("予約条件を編集", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)

            Row(modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically) {
                Text("この条件を有効にする", modifier = Modifier.weight(1f))
                Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
            }

            OutlinedTextField(value = keyword, onValueChange = { keyword = it },
                label = { Text("キーワード") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())

            Column {
                Text("対象の曜日")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)) {
                    items(CONDITION_DAYS.indices.toList()) { day ->
                        FilterChip(
                            selected = selectedDays.contains(day),
                            onClick = {
                                selectedDays = if (selectedDays.contains(day)) {
                                    (selectedDays - day).ifEmpty { setOf(day) }
                                } else {
                                    selectedDays + day
                                }
                            },
                            label = { Text(CONDITION_DAYS[day]) }
                        )
                    }
                }
            }

            Column {
                Text("時間帯: ${"%02d".format(startHour)}:00 〜 ${"%02d".format(endHour)}:59")
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)) {
                    Text("開始", modifier = Modifier.width(40.dp))
                    IconButton(onClick = { startHour = (startHour - 1 + 24) % 24 }) {
                        Icon(Icons.Default.Remove, null)
                    }
                    Text("${startHour}時", modifier = Modifier.width(40.dp))
                    IconButton(onClick = { startHour = (startHour + 1) % 24 }) {
                        Icon(Icons.Default.Add, null)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("終了", modifier = Modifier.width(40.dp))
                    IconButton(onClick = { endHour = (endHour - 1 + 24) % 24 }) {
                        Icon(Icons.Default.Remove, null)
                    }
                    Text("${endHour}時", modifier = Modifier.width(40.dp))
                    IconButton(onClick = { endHour = (endHour + 1) % 24 }) {
                        Icon(Icons.Default.Add, null)
                    }
                }
            }

            Column {
                Text("優先度: $priority")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)) {
                    (1..5).forEach { p ->
                        FilterChip(selected = priority == p, onClick = { priority = p },
                            label = { Text("$p") })
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically) {
                Text("イベントリレー設定", modifier = Modifier.weight(1f))
                Switch(checked = isEventRelay, onCheckedChange = { isEventRelay = it })
            }

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDeleteRequested,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("この条件を削除")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("キャンセル")
                }
                Button(
                    onClick = {
                        onSave(isEnabled, keyword, selectedDays, startHour, endHour, priority, isEventRelay)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = keyword.isNotBlank()
                ) {
                    Text("保存する")
                }
            }
        }
    }
}
