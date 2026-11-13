package com.beeregg2001.komorebi.ui.phone

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import com.beeregg2001.komorebi.data.model.Channel
import com.beeregg2001.komorebi.data.model.EpgProgram
import com.beeregg2001.komorebi.viewmodel.BaseballGameInfo
import com.beeregg2001.komorebi.viewmodel.HomeViewModel
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * TVの BaseballDashboardScreen (ui/home/BaseballDashboard.kt) に相当するPhone版。
 * 設定 > アドオン・ラボ > プロ野球モード > フォロー球団の設定 でお気に入り球団を
 * 選択した時だけ到達できる — この画面をホストするボトムナビのタブ自体が同じ
 * 設定を条件にしており、TVが favoriteBaseballTeams が空でない時だけ「プロ野球」
 * タブを表示するのと同じ仕組みになっている。
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PhoneBaseballScreen(
    homeViewModel: HomeViewModel,
    timeFormat: String,
    listState: LazyListState,
    onChannelSelected: (Channel) -> Unit,
    onProgramSelected: (EpgProgram) -> Unit
) {
    val groupedGames by homeViewModel.favoriteBaseballGames.collectAsState()
    val dateOffset by homeViewModel.baseballDateOffset.collectAsState()

    val dateStr = remember(dateOffset) {
        val now = OffsetDateTime.now()
        val base = if (now.hour < 4) now.minusDays(1) else now
        val target = base.plusDays(dateOffset.toLong())
        DateTimeFormatter.ofPattern("M月d日 (E)", Locale.JAPANESE).format(target)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Date navigation ─────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { homeViewModel.updateBaseballDateOffset(dateOffset - 1) },
                enabled = dateOffset > 0
            ) {
                Icon(Icons.Default.ChevronLeft, "前日")
            }
            Text(
                text = dateStr,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = { homeViewModel.updateBaseballDateOffset(dateOffset + 1) },
                enabled = dateOffset < 2
            ) {
                Icon(Icons.Default.ChevronRight, "翌日")
            }
        }
        HorizontalDivider()

        if (groupedGames.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.EventBusy, null,
                        modifier = Modifier.size(64.dp).padding(bottom = 16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        "この日の放送予定はありません",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                groupedGames.forEach { (teamName, games) ->
                    item(key = "team_$teamName") {
                        Text(
                            text = teamName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(games, key = { "game_${it.program.id}" }) { game ->
                        PhoneBaseballGameItem(
                            game = game,
                            timeFormat = timeFormat,
                            onClick = {
                                val now = OffsetDateTime.now()
                                val start = runCatching { OffsetDateTime.parse(game.program.start_time) }
                                    .getOrNull() ?: now
                                val end = runCatching { OffsetDateTime.parse(game.program.end_time) }
                                    .getOrNull() ?: now
                                if (now.isAfter(start) && now.isBefore(end)) {
                                    onChannelSelected(
                                        Channel(
                                            id = game.channel.id,
                                            displayChannelId = game.channel.display_channel_id,
                                            name = game.channel.name,
                                            channelNumber = game.channel.channel_number,
                                            networkId = game.channel.network_id.toLong(),
                                            serviceId = game.channel.service_id.toLong(),
                                            transportStreamId = game.channel.transport_stream_id.toLong(),
                                            type = game.channel.type,
                                            isWatchable = true,
                                            isDisplay = true,
                                            programPresent = null,
                                            programFollowing = null,
                                            remocon_Id = game.channel.remocon_id,
                                            is_subchannel = game.channel.is_subchannel
                                        )
                                    )
                                } else {
                                    onProgramSelected(game.program)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PhoneBaseballGameItem(
    game: BaseballGameInfo,
    timeFormat: String,
    onClick: () -> Unit
) {
    val now = OffsetDateTime.now()
    val start = remember(game.program.start_time) {
        runCatching { OffsetDateTime.parse(game.program.start_time) }.getOrNull()
    }
    val end = remember(game.program.end_time) {
        runCatching { OffsetDateTime.parse(game.program.end_time) }.getOrNull()
    }
    val isLive = start != null && end != null && now.isAfter(start) && now.isBefore(end)
    val isFinished = end != null && now.isAfter(end)

    val statusText = when {
        isLive -> "放送中"
        isFinished -> "放送終了"
        else -> "放送予定"
    }
    val statusColor = if (isLive) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant

    val timeStr = remember(start, end, timeFormat) {
        if (start == null || end == null) "" else {
            val pattern = if (timeFormat == "12H") "a h:mm" else "HH:mm"
            val formatter = DateTimeFormatter.ofPattern(pattern, Locale.JAPANESE)
            "${formatter.format(start)} - ${formatter.format(end)}"
        }
    }

    ListItem(
        headlineContent = {
            Text(game.program.title, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(statusText, style = MaterialTheme.typography.bodySmall,
                    color = statusColor, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(timeStr, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(8.dp))
                Text(game.channel.name, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = {
            Icon(
                Icons.Default.SportsBaseball, null,
                tint = if (isLive) statusColor else MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
