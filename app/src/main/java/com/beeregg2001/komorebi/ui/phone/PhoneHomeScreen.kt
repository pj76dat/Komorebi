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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beeregg2001.komorebi.data.model.Channel
import com.beeregg2001.komorebi.data.model.EpgProgram
import com.beeregg2001.komorebi.data.model.ReserveItem
import com.beeregg2001.komorebi.data.model.UiChannelState
import com.beeregg2001.komorebi.viewmodel.ChannelViewModel
import com.beeregg2001.komorebi.viewmodel.HomeViewModel
import com.beeregg2001.komorebi.viewmodel.ReserveViewModel
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PhoneHomeScreen(
    homeViewModel: HomeViewModel,
    reserveViewModel: ReserveViewModel,
    channelViewModel: ChannelViewModel,
    onNavigateToLive: () -> Unit,
    onNavigateToEpg: () -> Unit = {},
    onEpgProgramSelected: (EpgProgram) -> Unit = {},
    onNavigateToReserve: () -> Unit,
    onChannelSelected: (Channel) -> Unit = {},
    // ★ PhoneRootScreen から引き上げている — プレイヤー画面の表示・非表示に
    // 関わらずスクロール位置を維持するため（PhoneVideoScreen の同じ修正参照）。
    listState: LazyListState = rememberLazyListState()
) {
    val lastWatchedChannels by homeViewModel.lastWatchedChannelFlow.collectAsState(initial = emptyList())
    val upcomingReserves by reserveViewModel.reserves.collectAsState(initial = emptyList())
    val liveRows by channelViewModel.liveRows.collectAsState(initial = emptyList())
    val genrePickupPrograms by homeViewModel.genrePickupPrograms.collectAsState(initial = emptyList())
    val genrePickupTimeSlot by homeViewModel.genrePickupTimeSlot.collectAsState(initial = "夜")
    val pickupGenreLabel by homeViewModel.pickupGenreLabel.collectAsState(initial = "アニメ")

    // ライブ一覧から「今、盛り上がっているチャンネル」を算出
    val hotChannels = remember(liveRows) { homeViewModel.getHotChannels(liveRows) }
    // 前回視聴したチャンネルの「現在放送中」情報を最新化する — 保存されている
    // 前回視聴チャンネルのレコードはローカルDBのスナップショットで、その
    // programPresent は古くなっている場合があるため、ライブ一覧と突き合わせる。
    val liveChannelsById = remember(liveRows) {
        liveRows.flatMap { it.channels }.associateBy { it.channel.id }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {

        // ─── 前回視聴したチャンネル ───────────────────────────
        if (lastWatchedChannels.isNotEmpty()) {
            item {
                PhoneSectionHeader(
                    title = "前回視聴したチャンネル",
                    actionLabel = "ライブを開く",
                    onAction = onNavigateToLive
                )
            }
            items(lastWatchedChannels.take(10), key = { it.id }) { channel ->
                val programTitle = liveChannelsById[channel.id]?.programTitle
                    ?.takeIf { it.isNotBlank() }
                PhoneLastWatchedChannelItem(
                    channel = channel,
                    programTitle = programTitle,
                    onClick = { onChannelSelected(channel) }
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        // ─── 今、盛り上がっているチャンネル ──────────────────
        if (hotChannels.isNotEmpty()) {
            item {
                PhoneSectionHeader(
                    title = "今、盛り上がっているチャンネル",
                    actionLabel = "ライブを開く",
                    onAction = onNavigateToLive
                )
            }
            items(hotChannels) { uiChannel ->
                PhoneHotChannelItem(
                    uiChannel = uiChannel,
                    onClick = { onChannelSelected(uiChannel.channel) }
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        // ─── 今日のピックアップ ───────────────────────────────
        if (genrePickupPrograms.isNotEmpty()) {
            item {
                PhoneSectionHeader(
                    title = "今日のピックアップ ($pickupGenreLabel・$genrePickupTimeSlot)",
                    actionLabel = "番組表を開く",
                    onAction = onNavigateToEpg
                )
            }
            // ★ TV側は HomeViewModel の filterGenrePickup() で既に15件に制限された
            // genrePickupPrograms をそのまま全件表示している。Phoneだけ追加で
            // take(5) していたのを揃える。
            items(genrePickupPrograms.take(15)) { (program, channelName) ->
                PhonePickupProgramItem(
                    title = program.title,
                    channelName = channelName,
                    onClick = { onEpgProgramSelected(program) },
                    startTime = program.start_time
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        // ─── これからの録画予約 ───────────────────────────────
        val upcoming = homeViewModel.getUpcomingReserves(upcomingReserves)
        if (upcoming.isNotEmpty()) {
            item {
                PhoneSectionHeader(
                    title = "これからの録画予約",
                    actionLabel = "すべて見る",
                    onAction = onNavigateToReserve
                )
            }
            items(upcoming.take(5)) { reserve ->
                PhoneReserveItem(
                    reserve = reserve,
                    onClick = onNavigateToReserve
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

// ─── Section Header ───────────────────────────────────────────────────────────

@Composable
// ★ 非 private — PhoneVideoScreen.kt からも使う（続きから見る/最近の録画の移設先）
fun PhoneSectionHeader(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onAction) {
            Text(actionLabel, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ─── Last Watched Channel Item (前回視聴) ───────────────────────────────────

@Composable
private fun PhoneLastWatchedChannelItem(
    channel: Channel,
    programTitle: String?,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(channel.name, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(programTitle ?: "前回視聴していたチャンネルです",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Icon(Icons.Default.History, null,
                tint = MaterialTheme.colorScheme.primary)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ─── Hot Channel Item (今、盛り上がっている) ──────────────────────────────────

@Composable
private fun PhoneHotChannelItem(uiChannel: UiChannelState, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(uiChannel.name, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(uiChannel.programTitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Icon(Icons.Default.LocalFireDepartment, null,
                tint = MaterialTheme.colorScheme.error)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ─── Pickup Program Item (今日のピックアップ) ─────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PhonePickupProgramItem(title: String, channelName: String, startTime: String, onClick: () -> Unit) {
    val timeStr = remember(startTime) {
        try {
            val dt = OffsetDateTime.parse(startTime).atZoneSameInstant(ZoneId.systemDefault())
            DateTimeFormatter.ofPattern("HH:mm", Locale.JAPANESE).format(dt)
        } catch (e: Exception) { "" }
    }
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(buildString {
                if (timeStr.isNotEmpty()) append(timeStr)
                if (channelName.isNotEmpty()) { if (isNotEmpty()) append(" • "); append(channelName) }
            }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingContent = { Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ─── Reserve Item (これからの録画予約) ───────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PhoneReserveItem(reserve: ReserveItem, onClick: () -> Unit) {
    val dateStr = remember(reserve.program.startTime) {
        try {
            val dt = OffsetDateTime.parse(reserve.program.startTime)
                .atZoneSameInstant(ZoneId.systemDefault())
            DateTimeFormatter.ofPattern("MM/dd (E) HH:mm", Locale.JAPANESE).format(dt)
        } catch (e: Exception) { "" }
    }
    ListItem(
        headlineContent = {
            Text(reserve.program.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(dateStr, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingContent = {
            Icon(Icons.Default.FiberManualRecord, null,
                tint = MaterialTheme.colorScheme.error)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
