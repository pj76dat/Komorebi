package com.beeregg2001.komorebi.ui.phone

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.beeregg2001.komorebi.data.model.RecordedProgram
import com.beeregg2001.komorebi.ui.video.components.RecordCategory
import com.beeregg2001.komorebi.ui.video.smb.SmbItem
import com.beeregg2001.komorebi.viewmodel.HomeViewModel
import com.beeregg2001.komorebi.viewmodel.RecordSortOrder
import com.beeregg2001.komorebi.viewmodel.RecordSortType
import com.beeregg2001.komorebi.viewmodel.RecordViewModel
import com.beeregg2001.komorebi.viewmodel.SmbSortOrder
import com.beeregg2001.komorebi.viewmodel.SmbSortType
import com.beeregg2001.komorebi.viewmodel.SmbViewModel
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

enum class VideoSection { ROOT, RECORD_LIST, FILE_LIBRARY }

private val DAYS = listOf("月曜日", "火曜日", "水曜日", "木曜日", "金曜日", "土曜日", "日曜日")

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PhoneVideoScreen(
    recordViewModel: RecordViewModel,
    smbViewModel: SmbViewModel,
    homeViewModel: HomeViewModel,
    currentSection: VideoSection = VideoSection.ROOT,
    onSectionChange: (VideoSection) -> Unit = {},
    onRecordedProgramSelected: (RecordedProgram) -> Unit = {},
    onSmbFileSelected: (SmbItem) -> Unit = {},
    onWatchHistorySelected: (videoId: Int, positionMs: Long) -> Unit = { _, _ -> },
    // ★ 引き上げている — プレイヤー画面の表示・非表示に関わらずスクロール
    // 位置を維持するため（PhoneRootScreen はプレイヤーが開いている間、この
    // サブツリーごと入れ替えるため、そうしないとツリーの奥で宣言している
    // rememberLazyListState()/rememberLazyGridState() がリセットされてしまう）。
    videoRootListState: LazyListState = rememberLazyListState(),
    recordListState: LazyListState = rememberLazyListState(),
    recordGridState: LazyGridState = rememberLazyGridState(),
    fileLibraryListState: LazyListState = rememberLazyListState()
) {

    when (currentSection) {
        VideoSection.ROOT -> PhoneVideoRoot(
            recordViewModel = recordViewModel,
            homeViewModel = homeViewModel,
            listState = videoRootListState,
            onOpenRecordList = { onSectionChange(VideoSection.RECORD_LIST) },
            onOpenFileLibrary = {
                smbViewModel.initSmb()
                onSectionChange(VideoSection.FILE_LIBRARY)
            },
            onRecordedProgramSelected = onRecordedProgramSelected,
            onWatchHistorySelected = onWatchHistorySelected
        )
        VideoSection.RECORD_LIST -> PhoneRecordListScreen(
            recordViewModel = recordViewModel,
            onBack = { onSectionChange(VideoSection.ROOT) },
            onProgramSelected = onRecordedProgramSelected,
            listState = recordListState,
            gridState = recordGridState
        )
        VideoSection.FILE_LIBRARY -> PhoneFileLibraryScreen(
            smbViewModel = smbViewModel,
            onBack = { onSectionChange(VideoSection.ROOT) },
            onFileSelected = onSmbFileSelected,
            listState = fileLibraryListState
        )
    }
}

// ─── Root ─────────────────────────────────────────────────────────────────────

@Composable
private fun PhoneVideoRoot(
    recordViewModel: RecordViewModel,
    homeViewModel: HomeViewModel,
    listState: LazyListState,
    onOpenRecordList: () -> Unit,
    onOpenFileLibrary: () -> Unit,
    onRecordedProgramSelected: (RecordedProgram) -> Unit,
    onWatchHistorySelected: (videoId: Int, positionMs: Long) -> Unit
) {
    val recentRecordings by recordViewModel.recentRecordings.collectAsState(initial = emptyList())
    val watchHistory by homeViewModel.watchHistory.collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ビデオ", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                PhoneVideoSectionCard("録画リスト", "録画した番組の一覧",
                    Icons.Default.VideoLibrary, onOpenRecordList)
                PhoneVideoSectionCard("ファイルライブラリ", "ネットワーク上のファイルを再生",
                    Icons.Default.Folder, onOpenFileLibrary)
            }
        }

        // ─── 続きから見る（ホームから移設）───────────────────
        if (watchHistory.isNotEmpty()) {
            item {
                PhoneSectionHeader(
                    title = "続きから見る",
                    actionLabel = "録画リストを見る",
                    onAction = onOpenRecordList
                )
            }
            items(watchHistory.take(5)) { history ->
                PhoneWatchHistoryItem(
                    title = history.program.title,
                    channelName = history.program.channel_id,
                    onClick = {
                        val videoId = history.videoId
                        if (videoId != null) {
                            onWatchHistorySelected(
                                videoId,
                                (history.playback_position * 1000).toLong()
                            )
                        } else {
                            onOpenRecordList()
                        }
                    }
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        // ─── 最近の録画（ホームから移設）─────────────────────
        if (recentRecordings.isNotEmpty()) {
            item {
                PhoneSectionHeader(
                    title = "最近の録画",
                    actionLabel = "録画リストを見る",
                    onAction = onOpenRecordList
                )
            }
            items(recentRecordings.take(5)) { program ->
                PhoneRecordedProgramItem(
                    program = program,
                    onClick = { if (!program.isRecording) onRecordedProgramSelected(program) }
                )
            }
        }
    }
}

@Composable
private fun PhoneVideoSectionCard(
    title: String, description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

// ─── Watch History Item (続きから見る) — moved from PhoneHomeScreen.kt ────────

@Composable
private fun PhoneWatchHistoryItem(title: String, channelName: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(channelName, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingContent = { Icon(Icons.Default.PlayCircle, null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ─── Recorded Program Item (最近の録画) — moved from PhoneHomeScreen.kt ──────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PhoneRecordedProgramItem(program: RecordedProgram, onClick: () -> Unit) {
    val dateStr = remember(program.startTime) {
        try {
            val dt = OffsetDateTime.parse(program.startTime)
                .atZoneSameInstant(ZoneId.systemDefault())
            DateTimeFormatter.ofPattern("MM/dd (E) HH:mm", Locale.JAPANESE).format(dt)
        } catch (e: Exception) { "" }
    }
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (program.isRecording) {
                    Surface(color = MaterialTheme.colorScheme.error,
                        shape = MaterialTheme.shapes.small) {
                        Text("録画中", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(program.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(if (program.isRecording) 0.5f else 1f))
            }
        },
        supportingContent = {
            Text(dateStr, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(if (program.isRecording) 0.5f else 1f))
        },
        leadingContent = {
            Icon(Icons.Default.VideoFile, null,
                tint = if (program.isRecording) MaterialTheme.colorScheme.onSurfaceVariant
                       else MaterialTheme.colorScheme.primary)
        },
        modifier = if (!program.isRecording) Modifier.clickable(onClick = onClick) else Modifier
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ─── 録画リスト ───────────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneRecordListScreen(
    recordViewModel: RecordViewModel,
    onBack: () -> Unit,
    onProgramSelected: (RecordedProgram) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState()
) {
    val selectedCategory by recordViewModel.selectedCategory.collectAsState()
    val sortType by recordViewModel.sortType.collectAsState()
    val sortOrder by recordViewModel.sortOrder.collectAsState()
    val isListView by recordViewModel.isListView.collectAsState()
    val searchQuery by recordViewModel.searchQuery.collectAsState()
    val pagedRecordings = recordViewModel.pagedRecordings.collectAsLazyPagingItems()

    // サブフィルターの状態
    val availableGenres by recordViewModel.availableGenres.collectAsState()
    val groupedSeries by recordViewModel.groupedSeries.collectAsState()
    val groupedChannels by recordViewModel.groupedChannels.collectAsState()
    val selectedGenre by recordViewModel.selectedGenre.collectAsState()
    val selectedChannelId by recordViewModel.selectedChannelId.collectAsState()
    val selectedDay by recordViewModel.selectedDay.collectAsState()
    val selectedSeriesGenre by recordViewModel.selectedSeriesGenre.collectAsState()

    var showSearch by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }

    // ★ Action menu state
    var menuProgram by remember { mutableStateOf<RecordedProgram?>(null) }
    var showActionMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {

        // ─ Top bar ────────────────────────────────────────────
        TopAppBar(
            title = { Text("録画リスト", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "戻る") }
            },
            actions = {
                IconButton(onClick = { showSearch = !showSearch }) {
                    Icon(Icons.Default.Search, "検索")
                }
                IconButton(onClick = { showSortSheet = true }) {
                    Icon(Icons.Default.Sort, "並び替え")
                }
                IconButton(onClick = { recordViewModel.updateListView(!isListView) }) {
                    Icon(if (isListView) Icons.Default.GridView else Icons.Default.ViewList,
                        "表示切替")
                }
            }
        )

        // ─ Search bar ─────────────────────────────────────────
        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { recordViewModel.updateSearchQuery(it) },
                placeholder = { Text("番組を検索...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { recordViewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, "クリア")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // ─ Category chips（丸角・複数表示・スクロール可能）──────
        // ★ 前のUIに戻す: 名前+矢印の単一インジケーターではなく、複数の
        // カテゴリ名が同時に見える横スクロールのチップ列に。タップで直接
        // そのカテゴリへ切り替えられ、リスト/グリッド上でのスワイプ切り替えも
        // 従来通り機能する（挙動はそのまま）。
        val categoryIndex = RecordCategory.entries.indexOf(selectedCategory).coerceAtLeast(0)
        val categoryChipListState = rememberLazyListState()
        LaunchedEffect(categoryIndex) {
            categoryChipListState.animateScrollToItem((categoryIndex - 1).coerceAtLeast(0))
        }
        LazyRow(
            state = categoryChipListState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(RecordCategory.entries) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { recordViewModel.updateCategory(category) },
                    label = { Text(category.label) },
                    leadingIcon = { Icon(category.icon, null, modifier = Modifier.size(16.dp)) }
                )
            }
        }

        // ─ Sub-filter chips ───────────────────────────────────
        when (selectedCategory) {
            RecordCategory.GENRE -> {
                if (availableGenres.isNotEmpty()) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(selected = selectedGenre == null,
                                onClick = { recordViewModel.updateGenre(null) },
                                label = { Text("全て") })
                        }
                        items(availableGenres) { genre ->
                            FilterChip(selected = selectedGenre == genre,
                                onClick = { recordViewModel.updateGenre(genre) },
                                label = { Text(genre) })
                        }
                    }
                }
            }
            RecordCategory.TIME -> {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(selected = selectedDay == null,
                            onClick = { recordViewModel.updateDay(null) },
                            label = { Text("全て") })
                    }
                    items(DAYS) { day ->
                        FilterChip(selected = selectedDay == day,
                            onClick = { recordViewModel.updateDay(day) },
                            label = { Text(day.replace("曜日", "")) })
                    }
                }
            }
            RecordCategory.CHANNEL -> {
                val allChannels = remember(groupedChannels) { groupedChannels.entries.toList() }
                if (allChannels.isNotEmpty()) {
                    allChannels.forEach { (broadcastType, channels) ->
                        Text(text = broadcastType,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(selected = selectedChannelId == null,
                                    onClick = { recordViewModel.updateChannel(null) },
                                    label = { Text("全て") })
                            }
                            items(channels) { (channelName, channelId) ->
                                FilterChip(selected = selectedChannelId == channelId,
                                    onClick = { recordViewModel.updateChannel(channelId) },
                                    label = { Text(channelName, maxLines = 1) })
                            }
                        }
                    }
                }
            }
            RecordCategory.SERIES -> {
                val seriesGenres = remember(groupedSeries) { groupedSeries.keys.toList().sorted() }
                if (seriesGenres.isNotEmpty()) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(selected = selectedSeriesGenre == null,
                                onClick = { recordViewModel.updateSeriesGenre(null) },
                                label = { Text("全て") })
                        }
                        items(seriesGenres) { genre ->
                            FilterChip(selected = selectedSeriesGenre == genre,
                                onClick = { recordViewModel.updateSeriesGenre(genre) },
                                label = { Text(genre) })
                        }
                    }
                }
                val seriesList = remember(groupedSeries, selectedSeriesGenre) {
                    if (selectedSeriesGenre != null) groupedSeries[selectedSeriesGenre] ?: emptyList()
                    else groupedSeries.values.flatten()
                }
                if (seriesList.isNotEmpty()) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seriesList) { series ->
                            FilterChip(
                                selected = searchQuery == series.searchKeyword,
                                onClick = { recordViewModel.searchRecordings(series.searchKeyword) },
                                label = {
                                    Text("${series.displayTitle} (${series.programCount})",
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            )
                        }
                    }
                }
            }
            else -> {}
        }

        // ─ Sort indicator ─────────────────────────────────────
        val sortLabel = buildString {
            append(when (sortType) {
                RecordSortType.DATE -> "日付"
                RecordSortType.TITLE -> "タイトル"
                RecordSortType.DURATION -> "長さ"
            })
            append(if (sortOrder == RecordSortOrder.DESC) " ↓" else " ↑")
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(sortLabel, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(1f))
            Text("${pagedRecordings.itemCount} 件",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        HorizontalDivider()

        // ─ Program list or grid ───────────────────────────────
        // ★ 左右スワイプでカテゴリ切替（EPGのチャンネル切替と同じ操作感）。
        // サブフィルター（ジャンル別/シリーズ別などのチップ行）は対象外 — この
        // ジェスチャーはリスト/グリッド本体にだけ付けている。横方向のドラッグ量が
        // しきい値を超えたら次/前のカテゴリへ切り替え、消費したら量をリセットする。
        val density = LocalDensity.current
        val categorySwipeThresholdPx = remember(density) { with(density) { 96.dp.toPx() } }
        var categoryDragAccum by remember { mutableFloatStateOf(0f) }
        val categorySwipeModifier = Modifier.pointerInput(categoryIndex) {
            detectHorizontalDragGestures(
                onDragEnd = { categoryDragAccum = 0f },
                onDragCancel = { categoryDragAccum = 0f }
            ) { change, dragAmount ->
                categoryDragAccum += dragAmount
                if (categoryDragAccum <= -categorySwipeThresholdPx) {
                    if (categoryIndex < RecordCategory.entries.lastIndex) {
                        recordViewModel.updateCategory(RecordCategory.entries[categoryIndex + 1])
                    }
                    categoryDragAccum = 0f
                } else if (categoryDragAccum >= categorySwipeThresholdPx) {
                    if (categoryIndex > 0) {
                        recordViewModel.updateCategory(RecordCategory.entries[categoryIndex - 1])
                    }
                    categoryDragAccum = 0f
                }
                change.consume()
            }
        }

        if (isListView) {
            LazyColumn(modifier = Modifier.fillMaxSize().then(categorySwipeModifier),
                state = listState,
                contentPadding = PaddingValues(bottom = 16.dp)) {
                items(count = pagedRecordings.itemCount,
                    key = pagedRecordings.itemKey { it.id }) { index ->
                    val program = pagedRecordings[index]
                    if (program != null) {
                        PhoneRecordedProgramRow(
                            program = program,
                            onClick = { onProgramSelected(program) },
                            onMenuClick = { menuProgram = program; showActionMenu = true }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        } else {
            val allItems = remember(pagedRecordings.itemCount) {
                (0 until pagedRecordings.itemCount).mapNotNull { pagedRecordings[it] }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxSize().then(categorySwipeModifier),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allItems, key = { it.id }) { program ->
                    PhoneRecordedProgramCard(program = program,
                        onClick = { onProgramSelected(program) })
                }
            }
        }
    }

    // ─ Sort bottom sheet ──────────────────────────────────────
    if (showSortSheet) {
        ModalBottomSheet(onDismissRequest = { showSortSheet = false }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text("並び替え", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                listOf(
                    Triple(RecordSortType.DATE, RecordSortOrder.DESC, "新しい順"),
                    Triple(RecordSortType.DATE, RecordSortOrder.ASC, "古い順"),
                    Triple(RecordSortType.TITLE, RecordSortOrder.ASC, "タイトル昇順"),
                    Triple(RecordSortType.TITLE, RecordSortOrder.DESC, "タイトル降順"),
                    Triple(RecordSortType.DURATION, RecordSortOrder.DESC, "長い順"),
                    Triple(RecordSortType.DURATION, RecordSortOrder.ASC, "短い順"),
                ).forEach { (type, order, label) ->
                    val isSelected = sortType == type && sortOrder == order
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = {
                            if (isSelected) Icon(Icons.Default.Check, null,
                                tint = MaterialTheme.colorScheme.primary)
                            else Spacer(modifier = Modifier.size(24.dp))
                        },
                        modifier = Modifier.clickable {
                            recordViewModel.setSort(type, order)
                            showSortSheet = false
                        }
                    )
                }
            }
        }
    }

    // ─ Action menu bottom sheet ───────────────────────────────
    if (showActionMenu && menuProgram != null) {
        ModalBottomSheet(onDismissRequest = { showActionMenu = false }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(menuProgram!!.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                HorizontalDivider()

                // 再生する
                ListItem(
                    headlineContent = { Text("再生する") },
                    leadingContent = { Icon(Icons.Default.PlayArrow, null,
                        tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        showActionMenu = false
                        onProgramSelected(menuProgram!!)
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // 最初から再生
                ListItem(
                    headlineContent = { Text("最初から再生") },
                    leadingContent = { Icon(Icons.Default.Replay, null,
                        tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        showActionMenu = false
                        onProgramSelected(menuProgram!!) // same as 再生する for now
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // シリーズ検索
                ListItem(
                    headlineContent = { Text("シリーズ検索") },
                    leadingContent = { Icon(Icons.Default.LibraryBooks, null,
                        tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        showActionMenu = false
                        recordViewModel.searchRecordings(menuProgram!!.title)
                        recordViewModel.updateCategory(
                            com.beeregg2001.komorebi.ui.video.components.RecordCategory.ALL
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // 削除する (disabled — same as TV)
                ListItem(
                    headlineContent = {
                        Text("削除する",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                    },
                    leadingContent = {
                        Icon(Icons.Default.Delete, null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // 戻る
                ListItem(
                    headlineContent = { Text("戻る") },
                    leadingContent = { Icon(Icons.Default.ArrowBack, null) },
                    modifier = Modifier.clickable { showActionMenu = false }
                )
            }
        }
    }
}

// ─── Recorded Program Row ─────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PhoneRecordedProgramRow(
    program: RecordedProgram,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val dateStr = remember(program.startTime) {
        try {
            val dt = OffsetDateTime.parse(program.startTime)
                .atZoneSameInstant(ZoneId.systemDefault())
            DateTimeFormatter.ofPattern("MM/dd (E) HH:mm", Locale.JAPANESE).format(dt)
        } catch (e: Exception) { "" }
    }
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (program.isRecording) {
                    Surface(color = MaterialTheme.colorScheme.error,
                        shape = MaterialTheme.shapes.small) {
                        Text("録画中", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(program.title, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(if (program.isRecording) 0.5f else 1f))
            }
        },
        supportingContent = {
            Text(buildString {
                if (dateStr.isNotEmpty()) append(dateStr)
                val ch = program.channel?.name
                if (!ch.isNullOrEmpty()) { if (isNotEmpty()) append(" • "); append(ch) }
            }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(if (program.isRecording) 0.5f else 1f))
        },
        leadingContent = {
            Icon(Icons.Default.PlayCircle, null,
                tint = if (program.isRecording) MaterialTheme.colorScheme.onSurfaceVariant
                       else MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.ChevronRight, "メニュー",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        modifier = if (!program.isRecording) Modifier.clickable(onClick = onClick) else Modifier
    )
}

// ─── Recorded Program Card (grid) ────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PhoneRecordedProgramCard(program: RecordedProgram, onClick: () -> Unit) {
    val dateStr = remember(program.startTime) {
        try {
            val dt = OffsetDateTime.parse(program.startTime)
                .atZoneSameInstant(ZoneId.systemDefault())
            DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.JAPANESE).format(dt)
        } catch (e: Exception) { "" }
    }
    Card(
        onClick = onClick,
        enabled = !program.isRecording,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val thumbnailUrl = program.directThumbnailUrl ?: program.apiThumbnailUrl
            Box(modifier = Modifier.fillMaxWidth().height(90.dp)) {
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                            .alpha(if (program.isRecording) 0.5f else 1f),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.VideoFile, null,
                            modifier = Modifier.size(40.dp)
                                .alpha(if (program.isRecording) 0.5f else 1f),
                            tint = if (program.isRecording) MaterialTheme.colorScheme.onSurfaceVariant
                                   else MaterialTheme.colorScheme.primary)
                    }
                }
                if (program.isRecording) {
                    Surface(color = MaterialTheme.colorScheme.error,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                        Text("録画中", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(program.title, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold, maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(if (program.isRecording) 0.5f else 1f))
            if (dateStr.isNotEmpty()) {
                Text(dateStr, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(if (program.isRecording) 0.5f else 1f))
            }
        }
    }
}

// ─── ファイルライブラリ ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneFileLibraryScreen(
    smbViewModel: SmbViewModel,
    onBack: () -> Unit,
    onFileSelected: (SmbItem) -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val fileList by smbViewModel.fileList.collectAsState()
    val drives by smbViewModel.drives.collectAsState()
    val currentPath by smbViewModel.currentPath.collectAsState()
    val isLoading by smbViewModel.isLoading.collectAsState()
    val sortType by smbViewModel.sortType.collectAsState()
    val sortOrder by smbViewModel.sortOrder.collectAsState()
    val searchQuery by smbViewModel.searchQuery.collectAsState()

    var pathStack by remember { mutableStateOf(listOf<String>()) }
    var showSearch by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }

    val isAtRoot = currentPath.isEmpty()
    val items = if (isAtRoot) drives else fileList

    Column(modifier = Modifier.fillMaxSize()) {

        TopAppBar(
            title = {
                Text(if (isAtRoot) "ファイルライブラリ"
                else currentPath.substringAfterLast("/").substringAfterLast("\\"),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            navigationIcon = {
                IconButton(onClick = {
                    if (pathStack.isEmpty()) onBack()
                    else {
                        val previous = pathStack.last()
                        pathStack = pathStack.dropLast(1)
                        smbViewModel.loadDirectory(previous)
                    }
                }) { Icon(Icons.Default.ArrowBack, "戻る") }
            },
            actions = {
                if (!isAtRoot) {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, "検索")
                    }
                    IconButton(onClick = { showSortSheet = true }) {
                        Icon(Icons.Default.Sort, "並び替え")
                    }
                }
            }
        )

        if (showSearch && !isAtRoot) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { smbViewModel.updateSearchQuery(it) },
                placeholder = { Text("ファイルを検索...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { smbViewModel.clearSearch() }) {
                            Icon(Icons.Default.Clear, "クリア")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (!isAtRoot) {
            val sortLabel = buildString {
                append(when (sortType) {
                    SmbSortType.NAME -> "名前"
                    SmbSortType.DATE -> "日付"
                    SmbSortType.SIZE -> "サイズ"
                })
                append(if (sortOrder == SmbSortOrder.ASC) " ↑" else " ↓")
            }
            Text(sortLabel, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 16.dp)) {
            items(items, key = { it.path }) { item ->
                PhoneSmbItem(item = item, onClick = {
                    if (item.isDirectory) {
                        pathStack = pathStack + currentPath
                        smbViewModel.loadDirectory(item.path)
                    } else onFileSelected(item)
                })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }

    if (showSortSheet) {
        ModalBottomSheet(onDismissRequest = { showSortSheet = false }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text("並び替え", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                listOf(
                    Triple(SmbSortType.NAME, SmbSortOrder.ASC, "名前 昇順"),
                    Triple(SmbSortType.NAME, SmbSortOrder.DESC, "名前 降順"),
                    Triple(SmbSortType.DATE, SmbSortOrder.DESC, "日付 新しい順"),
                    Triple(SmbSortType.DATE, SmbSortOrder.ASC, "日付 古い順"),
                    Triple(SmbSortType.SIZE, SmbSortOrder.DESC, "サイズ 大きい順"),
                    Triple(SmbSortType.SIZE, SmbSortOrder.ASC, "サイズ 小さい順"),
                ).forEach { (type, order, label) ->
                    val isSelected = sortType == type && sortOrder == order
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = {
                            if (isSelected) Icon(Icons.Default.Check, null,
                                tint = MaterialTheme.colorScheme.primary)
                            else Spacer(modifier = Modifier.size(24.dp))
                        },
                        modifier = Modifier.clickable {
                            smbViewModel.setSort(type, order)
                            showSortSheet = false
                        }
                    )
                }
            }
        }
    }
}

// ─── SMB Item ─────────────────────────────────────────────────────────────────

@Composable
private fun PhoneSmbItem(item: SmbItem, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (!item.isDirectory && item.size > 0) {
            { Text("%.1f MB".format(item.size / (1024.0 * 1024.0)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        leadingContent = {
            Icon(if (item.isDirectory) Icons.Default.Folder else Icons.Default.VideoFile, null,
                tint = if (item.isDirectory) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = if (item.isDirectory) {
            { Icon(Icons.Default.ChevronRight, null) }
        } else null,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

// ─── Shared Top Bar ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "戻る") }
        }
    )
}
