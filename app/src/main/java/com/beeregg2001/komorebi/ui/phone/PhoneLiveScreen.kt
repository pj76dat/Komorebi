package com.beeregg2001.komorebi.ui.phone

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.beeregg2001.komorebi.data.model.UiChannelState
import com.beeregg2001.komorebi.ui.components.getLogoUrl
import com.beeregg2001.komorebi.viewmodel.ChannelViewModel
import com.beeregg2001.komorebi.viewmodel.SettingsViewModel

/**
 * Phone専用のライブ画面。
 * チャンネルを種別（地デジ、BS、CS等）ごとにグループ化し、スクロール可能な
 * リストで表示する。チャンネルをタップすると再生が始まる。
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PhoneLiveScreen(
    channelViewModel: ChannelViewModel,
    settingsViewModel: SettingsViewModel,
    onChannelSelected: (UiChannelState) -> Unit = {}
) {
    val liveRows by channelViewModel.liveRows.collectAsState(initial = emptyList())
    val connectionError by channelViewModel.connectionError.collectAsState()

    // ── チャンネルロゴ表示用（TVの HomeSections.kt 等と同じ仕組み） ──
    val backendType by settingsViewModel.backendType.collectAsState()
    val shouldCropLogo = remember(backendType) { backendType == "KONOMITV" }
    val mirakurunIp by settingsViewModel.mirakurunIp.collectAsState()
    val mirakurunPort by settingsViewModel.mirakurunPort.collectAsState()
    val konomiIp by settingsViewModel.konomiIp.collectAsState()
    val konomiPort by settingsViewModel.konomiPort.collectAsState()

    val tabs = remember(liveRows) { liveRows.map { it.genreLabel } }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // ★ タブごとにlistStateを分けて、それぞれのスクロール位置を記憶する
    val listStates = remember(tabs.size) {
        List(maxOf(tabs.size, 6)) { LazyListState() }
    }

    LaunchedEffect(liveRows) {
        if (selectedTabIndex >= liveRows.size) selectedTabIndex = 0
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ─── チャンネル種別タブ ────────────────────────────────
        if (tabs.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(label) }
                    )
                }
            }
        }

        // ─── 表示内容 ──────────────────────────────────────────
        // ★ TVの LiveContents.kt と同じ考え方: liveRows.isEmpty() だけを見て
        // （isLoading/connectionError すら見ない）、それ以外はスピナーを出す —
        // 独立した「見つかりません」メッセージは一切出さない。これが重要な
        // 理由: ChannelViewModel の定期バックグラウンドポーリング（約60秒毎）は
        // fetchChannelsInternal() を直接呼んでおり、fetchChannels() ラッパーが
        // 通常先にセットする isLoading=true を経由しない — そのため、遅い/
        // 一時的に空の再取得（例えばデバイスがスリープから復帰した直後など）で
        // isLoading=false のまま liveRows が瞬間的/稀に空になることがある。
        // データが少しでもあればリストを表示することを優先し、本当にデータが
        // 無く、かつ実際に取得エラーが起きている時だけ、明示的なエラー＋再試行
        // 表示にフォールバックする。
        when {
            liveRows.isNotEmpty() -> {
                val currentRow = liveRows.getOrNull(selectedTabIndex)
                val channels = currentRow?.channels ?: emptyList()
                val currentListState = listStates.getOrElse(selectedTabIndex) {
                    rememberLazyListState()
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = currentListState,
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(channels, key = { it.channel.id }) { uiChannel ->
                        PhoneLiveChannelItem(
                            uiChannel = uiChannel,
                            logoUrl = remember(uiChannel.channel.id, mirakurunIp, mirakurunPort, konomiIp, konomiPort) {
                                uiChannel.channel.getLogoUrl(mirakurunIp, mirakurunPort, konomiIp, konomiPort)
                            },
                            shouldCropLogo = shouldCropLogo,
                            onClick = { onChannelSelected(uiChannel) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }

            connectionError -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("接続エラー", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { channelViewModel.fetchChannels() }) {
                            Text("再試行")
                        }
                    }
                }
            }

            else -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

// ─── Channel List Item ────────────────────────────────────────────────────────

@Composable
private fun PhoneLiveChannelItem(
    uiChannel: UiChannelState,
    logoUrl: String,
    shouldCropLogo: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = uiChannel.name,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = uiChannel.programTitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (uiChannel.hasProgram && uiChannel.progress > 0f) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { uiChannel.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                }
            }
        },
        leadingContent = {
            // ★ チャンネルロゴ表示（TVの HomeCards.kt と同じ表示方法）。
            Box(
                modifier = Modifier
                    .size(56.dp, 32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = if (shouldCropLogo) ContentScale.Crop else ContentScale.Fit
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
