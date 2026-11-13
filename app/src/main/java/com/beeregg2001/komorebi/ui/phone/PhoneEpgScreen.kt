package com.beeregg2001.komorebi.ui.phone

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beeregg2001.komorebi.data.model.EpgChannelWrapper
import com.beeregg2001.komorebi.data.model.EpgProgram
import com.beeregg2001.komorebi.data.model.ReserveItem
import com.beeregg2001.komorebi.data.model.ReserveRecordSettings
import com.beeregg2001.komorebi.viewmodel.ChannelViewModel
import com.beeregg2001.komorebi.viewmodel.EpgUiState
import com.beeregg2001.komorebi.viewmodel.EpgViewModel
import com.beeregg2001.komorebi.viewmodel.ReserveViewModel
import com.beeregg2001.komorebi.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhoneEpgScreen(
    epgViewModel: EpgViewModel,
    channelViewModel: ChannelViewModel,
    reserveViewModel: ReserveViewModel,
    settingsViewModel: SettingsViewModel,
    initialProgram: EpgProgram? = null,
    onInitialProgramConsumed: () -> Unit = {}
) {
    val uiState = epgViewModel.uiState
    val selectedType by epgViewModel.selectedBroadcastingType.collectAsState()
    // ★ 予約済み番組をハイライトするため（TV番組表の赤い破線枠と同じ見た目）
    val reserves by reserveViewModel.reserves.collectAsState(initial = emptyList())
    val reserveMap = remember(reserves) { reserves.associateBy { it.program.id } }

    var selectedProgram by remember { mutableStateOf<EpgProgram?>(null) }
    var showProgramSheet by remember { mutableStateOf(false) }
    var showDetailSettings by remember { mutableStateOf(false) }
    var showEpgReserveSheet by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    // ★ 「日付を選ぶ」を追加。TVの放送日と同じ4時始まり基準で7日分の候補を作り、
    // EpgViewModel.updateTargetTime() へ渡す（同ViewModelが元々持っている、
    // 1日分に絞り込む仕組みをそのまま利用 — ViewModel側は無改修）。
    val dayOptions = remember {
        val now = OffsetDateTime.now()
        val base = if (now.hour < 4) now.minusDays(1) else now
        val todayStart = base.withHour(4).withMinute(0).withSecond(0).withNano(0)
        (0..6).map { todayStart.plusDays(it.toLong()) }
    }
    var selectedDayIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedDayIndex) {
        epgViewModel.updateTargetTime(dayOptions[selectedDayIndex])
    }

    LaunchedEffect(selectedType) {
        epgViewModel.refreshEpgData(selectedType)
    }

    val pagerState = rememberPagerState(pageCount = {
        (uiState as? EpgUiState.Success)?.data?.size ?: 0
    })

    LaunchedEffect(selectedType) {
        // ★ 種別（地デジ/BS/CS）を切り替えたらチャンネル一覧が変わるので先頭に戻す。
        // 日付を変えただけならチャンネル一覧の並びは同じなので、ページはそのまま。
        pagerState.scrollToPage(0)
    }

    LaunchedEffect(initialProgram) {
        if (initialProgram != null) {
            // ★ 指定された番組が今日以外の日に放送される場合、日付選択もそこへ合わせる
            try {
                val progTime = OffsetDateTime.parse(initialProgram.start_time)
                val dayIdx = dayOptions.indexOfLast { !progTime.isBefore(it) }
                if (dayIdx >= 0) selectedDayIndex = dayIdx
            } catch (e: Exception) { /* keep current day */ }
            selectedProgram = initialProgram
            showProgramSheet = true
            onInitialProgramConsumed()
        }
    }

    // ★ ピックアップ等からチャンネルを指定して遷移してきた場合、そのチャンネルの
    // ページまでスワイプ済みの状態にしておく
    LaunchedEffect(initialProgram?.channel_id, uiState) {
        val channelId = initialProgram?.channel_id ?: return@LaunchedEffect
        val channels = (uiState as? EpgUiState.Success)?.data ?: return@LaunchedEffect
        val index = channels.indexOfFirst { it.channel.id == channelId }
        if (index >= 0) pagerState.scrollToPage(index)
    }

    // ── Detail settings screen (full screen) ──────────────────
    if (showDetailSettings && selectedProgram != null) {
        PhoneEpgDetailSettingsScreen(
            program = selectedProgram!!,
            settingsViewModel = settingsViewModel,
            onBack = { showDetailSettings = false },
            onSave = { settings ->
                reserveViewModel.addReserveWithSettings(
                    programId = selectedProgram!!.id,
                    settings = settings,
                    onSuccess = {
                        showDetailSettings = false
                        toastMessage = "録画予約しました"
                    }
                )
            }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Day selector (7日分・4時始まりの放送日基準) ─────────
        val dayFormatter = remember { DateTimeFormatter.ofPattern("M/d(E)", Locale.JAPANESE) }
        val dayChipListState = rememberLazyListState()
        // ★ 番組欄の最下部までスクロールした時、または種別タブなどで日付が
        // プログラム的に変わった時も、選択中チップが見える位置まで追従させる
        LaunchedEffect(selectedDayIndex) {
            dayChipListState.animateScrollToItem(selectedDayIndex)
        }
        LazyRow(
            state = dayChipListState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(dayOptions) { index, day ->
                val label = when (index) {
                    0 -> "今日"
                    1 -> "明日"
                    else -> dayFormatter.format(day)
                }
                FilterChip(
                    selected = selectedDayIndex == index,
                    onClick = { selectedDayIndex = index },
                    label = { Text(label) }
                )
            }
        }
        HorizontalDivider()

        // ── Channel type tabs ──────────────────────────────────
        val types = listOf("GR" to "地デジ", "BS" to "BS", "CS" to "CS", "BS4K" to "BS4K")
        ScrollableTabRow(
            selectedTabIndex = types.indexOfFirst { it.first == selectedType }.coerceAtLeast(0),
            edgePadding = 16.dp
        ) {
            types.forEach { (typeId, label) ->
                Tab(
                    selected = selectedType == typeId,
                    onClick = { epgViewModel.updateBroadcastingType(typeId) },
                    text = { Text(label) }
                )
            }
        }

        // ── Content ────────────────────────────────────────────
        when (uiState) {
            is EpgUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("番組表を読み込み中...")
                    }
                }
            }

            is EpgUiState.Success -> {
                val channels = uiState.data
                if (channels.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("番組データがありません")
                    }
                } else {
                    // ── チャンネル選択チップ（丸角・複数表示・スクロール可能）──
                    // ★ 前のUIに戻す: 名前+矢印の単一インジケーターではなく、
                    // 複数のチャンネル名が同時に見える横スクロールのチップ列に。
                    // タップでそのチャンネルへ直接ジャンプでき、スワイプでの
                    // 切り替えも従来通り機能する（挙動はそのまま）。
                    val scope = rememberCoroutineScope()
                    val channelChipListState = rememberLazyListState()
                    LaunchedEffect(pagerState.currentPage) {
                        channelChipListState.animateScrollToItem(
                            (pagerState.currentPage - 1).coerceAtLeast(0)
                        )
                    }
                    LazyRow(
                        state = channelChipListState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(channels) { index, wrapper ->
                            FilterChip(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                                label = { Text(wrapper.channel.name, maxLines = 1) }
                            )
                        }
                    }
                    HorizontalDivider()

                    // ★ 「日付を選択 → 種別を選択 → 左右スワイプでチャンネル切替 →
                    // 上下スクロールでその日の番組」という要望の実装本体
                    //
                    // ★ スワイプでチャンネルが切り替えられることが分かりにくいとの
                    // ことで、左右の隣のチャンネルがほんの少し画面端に見えるように
                    // している（contentPadding + pageSpacing）。これにより「まだ
                    // 何かある」ことが常に視覚的に伝わる。上のチェブロンボタンは
                    // タップだけで切り替えたい人向けの代替手段。
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        pageSpacing = 8.dp
                    ) { page ->
                        Card(
                            modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
                            shape = MaterialTheme.shapes.medium,
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            PhoneEpgChannelDayPage(
                                wrapper = channels[page],
                                reserveMap = reserveMap,
                                // ★ 修正: selectedDayIndex は即座に変わるが、実際の
                                // programs データは EpgViewModel の非同期の絞り込みが
                                // 終わった後に届く。両者にズレがあると「前の日へ戻った
                                // つもりが、まだ古い日のデータを基準にスクロールして
                                // しまい、結果的に先頭に見える」ことが起きる。
                                // uiState.targetTime は絞り込み後の新しい day の
                                // データと必ず同時に更新されるので、これをキーにすれば
                                // wrapper.programs と常に整合した状態で判定できる。
                                dayKey = uiState.targetTime,
                                onProgramClick = { program ->
                                    selectedProgram = program
                                    showProgramSheet = true
                                },
                                onReachedBottom = {
                                    if (selectedDayIndex < dayOptions.lastIndex) {
                                        selectedDayIndex++
                                    }
                                },
                                onReachedTop = {
                                    if (selectedDayIndex > 0) {
                                        selectedDayIndex--
                                    }
                                }
                            )
                        }
                    }
                }
            }

            else -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("番組表を読み込めませんでした")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { epgViewModel.refreshEpgData(selectedType) }) {
                            Text("再試行")
                        }
                    }
                }
            }
        }
    }

    // ── Program detail bottom sheet ────────────────────────────
    if (showProgramSheet && selectedProgram != null) {
        ModalBottomSheet(onDismissRequest = { showProgramSheet = false }) {
            PhoneProgramDetailSheet(
                program = selectedProgram!!,
                onDismiss = { showProgramSheet = false },
                onReserve = {
                    reserveViewModel.addReserve(selectedProgram!!.id) {
                        showProgramSheet = false
                        toastMessage = "録画予約しました"
                    }
                },
                onEpgReserve = {
                    showProgramSheet = false
                    showEpgReserveSheet = true
                },
                onDetailSettings = {
                    showProgramSheet = false
                    showDetailSettings = true
                }
            )
        }
    }

    // ── EPG reserve sheet ──────────────────────────────────────
    if (showEpgReserveSheet && selectedProgram != null) {
        PhoneEpgReserveSheet(
            program = selectedProgram!!,
            onDismiss = { showEpgReserveSheet = false },
            onReserve = { keyword, daysOfWeek, priority, isEventRelay ->
                reserveViewModel.addEpgReserve(
                    keyword = keyword,
                    networkId = 0,
                    transportStreamId = 0,
                    serviceId = 0,
                    daysOfWeek = daysOfWeek,
                    startHour = selectedProgram!!.startHour(),
                    startMinute = selectedProgram!!.startMinute(),
                    endHour = selectedProgram!!.endHour(),
                    endMinute = selectedProgram!!.endMinute(),
                    excludeKeyword = "",
                    isTitleOnly = true,
                    broadcastType = "",
                    isFuzzySearch = false,
                    duplicateScope = "SameTime",
                    priority = priority,
                    isEventRelay = isEventRelay,
                    isExactRecord = false,
                    onSuccess = {
                        showEpgReserveSheet = false
                        toastMessage = "EPG予約しました"
                    }
                )
            }
        )
    }

    // ── Toast ──────────────────────────────────────────────────
    toastMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2000)
            toastMessage = null
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(modifier = Modifier.padding(16.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.inverseSurface) {
                Text(msg, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
    }
}

// ─── Program Detail Sheet ─────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PhoneProgramDetailSheet(
    program: EpgProgram,
    onDismiss: () -> Unit,
    onReserve: () -> Unit,
    onEpgReserve: () -> Unit,
    onDetailSettings: () -> Unit
) {
    val timeFmt = DateTimeFormatter.ofPattern("MM/dd (E) HH:mm", Locale.JAPANESE)
    val timeStr = remember(program.start_time, program.end_time) {
        try {
            val start = OffsetDateTime.parse(program.start_time)
                .atZoneSameInstant(ZoneId.systemDefault())
            val end = OffsetDateTime.parse(program.end_time)
                .atZoneSameInstant(ZoneId.systemDefault())
            "${timeFmt.format(start)} ～ ${DateTimeFormatter.ofPattern("HH:mm").format(end)}"
        } catch (e: Exception) { "" }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(timeStr, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text(program.title, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (program.description.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState())) {
                Text(program.description, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onReserve, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Default.FiberManualRecord, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("録画予約をする")
            }
            OutlinedButton(onClick = onEpgReserve, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Bookmarks, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("EPG予約する")
            }
            OutlinedButton(onClick = onDetailSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("録画予約（詳細設定）")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("戻る")
            }
        }
    }
}

// ─── EPG Reserve Sheet ────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneEpgReserveSheet(
    program: EpgProgram,
    onDismiss: () -> Unit,
    onReserve: (keyword: String, daysOfWeek: Set<Int>, priority: Int, isEventRelay: Boolean) -> Unit
) {
    var keyword by remember { mutableStateOf(program.title) }
    var priority by remember { mutableStateOf(3) }
    var isEventRelay by remember { mutableStateOf(true) }

    val dayOfWeek = remember(program.start_time) {
        try {
            val dt = OffsetDateTime.parse(program.start_time)
                .atZoneSameInstant(ZoneId.systemDefault())
            setOf(dt.dayOfWeek.value % 7)
        } catch (e: Exception) { setOf(0) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("EPG予約する", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)

            OutlinedTextField(value = keyword, onValueChange = { keyword = it },
                label = { Text("キーワード") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())

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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("キャンセル")
                }
                Button(
                    onClick = { onReserve(keyword, dayOfWeek, priority, isEventRelay) },
                    modifier = Modifier.weight(1f),
                    enabled = keyword.isNotBlank()
                ) {
                    Text("EPG予約する")
                }
            }
        }
    }
}

// ─── Detail Settings Screen ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneEpgDetailSettingsScreen(
    program: EpgProgram,
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onSave: (ReserveRecordSettings) -> Unit
) {
    var isEnabled by remember { mutableStateOf(true) }
    var priority by remember { mutableStateOf(3) }
    var isEventRelay by remember { mutableStateOf(true) }
    val batchList by settingsViewModel.postRecordingBatchList.collectAsState()
    var selectedBatPath by remember { mutableStateOf<String?>(null) }
    var showBatchDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("録画予約（詳細設定）") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "戻る")
                }
            }
        )

        val listState = rememberLazyListState()
        LazyColumn(modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // 番組情報
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(program.title, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 予約を有効にする
            item {
                Row(modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("予約を有効にする", modifier = Modifier.weight(1f))
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }
                HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
            }

            // 優先度
            item {
                Column {
                    Text("優先度: $priority", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)) {
                        (1..5).forEach { p ->
                            FilterChip(selected = priority == p, onClick = { priority = p },
                                label = { Text("$p") })
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
            }

            // 録画後実行バッチ
            item {
                Row(modifier = Modifier.fillMaxWidth().clickable { showBatchDialog = true },
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("録画後実行バッチ",
                            style = MaterialTheme.typography.bodyMedium)
                        Text(
                            batchList.find { it.path == selectedBatPath }?.name ?: "なし",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null)
                }
                HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
            }

            // イベントリレー設定
            item {
                Row(modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("イベントリレー設定", modifier = Modifier.weight(1f))
                    Switch(checked = isEventRelay, onCheckedChange = { isEventRelay = it })
                }
            }
        }

        // 下部のボタン
        HorizontalDivider()
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("キャンセル")
            }
            Button(
                onClick = {
                    onSave(ReserveRecordSettings(
                        isEnabled = isEnabled,
                        priority = priority,
                        isEventRelayFollowEnabled = isEventRelay,
                        postRecordingBatFilePath = selectedBatPath
                    ))
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error)
            ) { Text("録画予約する") }
        }
    }

    // 一括登録ダイアログ
    if (showBatchDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDialog = false },
            title = { Text("実行するバッチを選択") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("なし") },
                        leadingContent = {
                            if (selectedBatPath == null)
                                Icon(Icons.Default.Check, null,
                                    tint = MaterialTheme.colorScheme.primary)
                            else Spacer(modifier = Modifier.size(24.dp))
                        },
                        modifier = Modifier.clickable {
                            selectedBatPath = null
                            showBatchDialog = false
                        }
                    )
                    batchList.forEach { batch ->
                        ListItem(
                            headlineContent = { Text(batch.name) },
                            supportingContent = {
                                Text(batch.path, style = MaterialTheme.typography.bodySmall)
                            },
                            leadingContent = {
                                if (selectedBatPath == batch.path)
                                    Icon(Icons.Default.Check, null,
                                        tint = MaterialTheme.colorScheme.primary)
                                else Spacer(modifier = Modifier.size(24.dp))
                            },
                            modifier = Modifier.clickable {
                                selectedBatPath = batch.path
                                showBatchDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBatchDialog = false }) { Text("キャンセル") }
            }
        )
    }
}

// ─── Channel Day Page (1チャンネル分・選択中の1日を縦スクロール) ───────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PhoneEpgChannelDayPage(
    wrapper: EpgChannelWrapper,
    reserveMap: Map<String, ReserveItem>,
    dayKey: OffsetDateTime,
    onProgramClick: (EpgProgram) -> Unit,
    onReachedBottom: () -> Unit,
    onReachedTop: () -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    val listState = rememberLazyListState()
    val programs = remember(wrapper.programs) {
        wrapper.programs.sortedBy { it.start_time }
    }

    // ★ 日付が変わったら（このページ＝チャンネル固定なので、変わるのは日付だけ）
    // スクロール位置を合わせる。前の日へ戻った時はその日の最後（下から上へ続けて
    // 読める向き）、次の日へ進んだ時は先頭に。ページが新しく（再）マウントされた
    // 時は「変化なし」扱いで先頭になる（害はない）。
    // dayKey は uiState.targetTime そのものなので、この効果が実行される時点で
    // wrapper.programs は必ずこの dayKey に対応した中身になっている（同じ
    // EpgUiState.Success から同時に導出されるため）。
    var previousDayKey by remember { mutableStateOf(dayKey) }
    LaunchedEffect(dayKey) {
        if (dayKey != previousDayKey) {
            if (dayKey.isBefore(previousDayKey)) {
                val lastIndex = (programs.size - 1).coerceAtLeast(0)
                listState.scrollToItem(lastIndex)
            } else {
                listState.scrollToItem(0)
            }
        }
        previousDayKey = dayKey
    }

    // ★ 「はっきりした操作」でのみ日付を切り替える。
    // canScrollForward/Backward の true→false 遷移は、レイアウト確定やフリングの
    // 減速中にも一瞬だけ起こりうるため、それだけで切り替えると「今日」「明日」の
    // 境界でチャタリングする。そこで、実際に指でドラッグして端を超えようとした
    // 分（NestedScrollConnection の未消費スクロール量）を一定量ため込んだ時だけ
    // 切り替える方式にする。プログラムによる scrollToItem（上の LaunchedEffect）
    // は Drag ソースではないため、これと干渉しない。
    //
    // ★ 追加: 1回切り替えたら、その場でジェスチャーの指がまだ動いていて
    // （同じドラッグの続きで）もう一度しきい値を超えてしまい、2日分進んで
    // しまうことがあった。そこで「1ジェスチャーにつき1回」に制限する — 発火後は
    // スクロールが完全に止まる（isScrollInProgress が false になる＝指を離して
    // 慣性も収まった）まで再発火しないようにする。
    var canTrigger by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { inProgress ->
            if (!inProgress) canTrigger = true
        }
    }

    val density = LocalDensity.current
    val triggerThresholdPx = remember(density) { with(density) { 64.dp.toPx() } }
    var topOverscroll by remember { mutableFloatStateOf(0f) }
    var bottomOverscroll by remember { mutableFloatStateOf(0f) }
    val nestedScrollConnection = remember(onReachedTop, onReachedBottom, triggerThresholdPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.Drag) return Offset.Zero
                if (!canTrigger) return Offset.Zero
                when {
                    // 上端を超えてさらに下方向へ引っ張ろうとしている（前の日へ）
                    available.y > 0.5f -> {
                        bottomOverscroll = 0f
                        topOverscroll += available.y
                        if (topOverscroll >= triggerThresholdPx) {
                            topOverscroll = 0f
                            canTrigger = false
                            onReachedTop()
                        }
                    }
                    // 下端を超えてさらに上方向へ引っ張ろうとしている（次の日へ）
                    available.y < -0.5f -> {
                        topOverscroll = 0f
                        bottomOverscroll += -available.y
                        if (bottomOverscroll >= triggerThresholdPx) {
                            bottomOverscroll = 0f
                            canTrigger = false
                            onReachedBottom()
                        }
                    }
                    else -> {
                        topOverscroll = 0f
                        bottomOverscroll = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
        state = listState,
        contentPadding = PaddingValues(bottom = 16.dp)) {
        if (programs.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center) {
                    Text("放送情報なし", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(programs, key = { "prog_${it.id}" }) { program ->
                PhoneEpgProgramItem(program = program,
                    isNow = isNowPlaying(program, now),
                    reserve = reserveMap[program.id],
                    onClick = { onProgramClick(program) })
            }
        }
    }
}

// ─── Program Item ─────────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PhoneEpgProgramItem(
    program: EpgProgram,
    isNow: Boolean,
    reserve: ReserveItem?,
    onClick: () -> Unit
) {
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.JAPANESE)
    val timeStr = remember(program.start_time, program.end_time) {
        try {
            val start = OffsetDateTime.parse(program.start_time)
                .atZoneSameInstant(ZoneId.systemDefault())
            val end = OffsetDateTime.parse(program.end_time)
                .atZoneSameInstant(ZoneId.systemDefault())
            "${timeFmt.format(start)} ～ ${timeFmt.format(end)}"
        } catch (e: Exception) { "" }
    }

    // ★ 予約済みハイライト — TV番組表と同じ赤い破線枠（録画可否が部分的な場合は黄色）
    val isPartial = reserve != null &&
        (reserve.recordingAvailability == "Partial" || reserve.recordingAvailability == "Partially")
    val reserveBorderColor = if (isPartial) Color(0xFFFFCA28) else Color(0xFFFF5252)

    Box(
        modifier = if (reserve != null) {
            Modifier.drawWithContent {
                drawContent()
                val strokeWidthPx = 2.dp.toPx()
                val inset = strokeWidthPx / 2f
                drawRoundRect(
                    color = reserveBorderColor,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - strokeWidthPx,
                        size.height - strokeWidthPx
                    ),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(
                        width = strokeWidthPx,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 0f)
                    )
                )
            }
        } else Modifier
    ) {
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isNow) {
                        Surface(color = MaterialTheme.colorScheme.error,
                            shape = MaterialTheme.shapes.small) {
                            Text("ON AIR", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(program.title,
                        fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            },
            supportingContent = {
                Text(timeStr, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = {
                if (reserve != null) {
                    Icon(Icons.Default.FiberManualRecord, "予約済み",
                        modifier = Modifier.size(14.dp), tint = reserveBorderColor)
                } else {
                    Icon(Icons.Default.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            modifier = Modifier.clickable(onClick = onClick)
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
private fun isNowPlaying(program: EpgProgram, now: Long): Boolean {
    return try {
        val start = OffsetDateTime.parse(program.start_time)
            .atZoneSameInstant(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = OffsetDateTime.parse(program.end_time)
            .atZoneSameInstant(ZoneId.systemDefault()).toInstant().toEpochMilli()
        now in start..end
    } catch (e: Exception) { false }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun EpgProgram.startHour(): Int = try {
    OffsetDateTime.parse(start_time).atZoneSameInstant(ZoneId.systemDefault()).hour
} catch (e: Exception) { 0 }

@RequiresApi(Build.VERSION_CODES.O)
private fun EpgProgram.startMinute(): Int = try {
    OffsetDateTime.parse(start_time).atZoneSameInstant(ZoneId.systemDefault()).minute
} catch (e: Exception) { 0 }

@RequiresApi(Build.VERSION_CODES.O)
private fun EpgProgram.endHour(): Int = try {
    OffsetDateTime.parse(end_time).atZoneSameInstant(ZoneId.systemDefault()).hour
} catch (e: Exception) { 0 }

@RequiresApi(Build.VERSION_CODES.O)
private fun EpgProgram.endMinute(): Int = try {
    OffsetDateTime.parse(end_time).atZoneSameInstant(ZoneId.systemDefault()).minute
} catch (e: Exception) { 0 }
