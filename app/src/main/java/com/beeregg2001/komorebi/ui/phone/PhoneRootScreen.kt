package com.beeregg2001.komorebi.ui.phone

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme as M3Theme
import androidx.compose.material3.darkColorScheme as m3DarkColorScheme
import androidx.compose.material3.lightColorScheme as m3LightColorScheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.beeregg2001.komorebi.data.model.RecordedProgram
import com.beeregg2001.komorebi.ui.video.smb.SmbItem
import com.beeregg2001.komorebi.ui.video.player.VideoPlayerScreen
import com.beeregg2001.komorebi.ui.video.smb.player.SmbVlcPlayerScreen
import com.beeregg2001.komorebi.viewmodel.*
import com.beeregg2001.komorebi.data.model.Channel
import com.beeregg2001.komorebi.data.model.EpgProgram
import com.beeregg2001.komorebi.ui.live.LivePlayerScreen
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.beeregg2001.komorebi.ui.theme.AppTheme
import com.beeregg2001.komorebi.ui.theme.KomorebiTheme
import com.beeregg2001.komorebi.common.AppStrings

/**
 * ボトムナビゲーションバーを持つ、Phone専用のルート画面。
 * TVのUIとは完全に独立している — 元の MainRootScreen は一切変更していない。
 */

private enum class PhoneTab(val label: String, val icon: ImageVector) {
    HOME("ホーム", Icons.Default.Home),
    LIVE("ライブ", Icons.Default.Tv),
    VIDEO("ビデオ", Icons.Default.VideoLibrary),
    EPG("番組表", Icons.Default.GridView),
    RESERVE("録画予約", Icons.Default.BookmarkBorder),
    // ★ プロ野球モード - フォロー球団未設定時は非表示（TVの HomeLauncherScreen が
    // favoriteBaseballTeams.isNotEmpty() の時だけ "プロ野球" タブを足すのと同じ）
    BASEBALL("プロ野球", Icons.Default.SportsBaseball),
    SETTINGS("設定", Icons.Default.Settings)
}

@RequiresApi(Build.VERSION_CODES.O)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PhoneRootScreen(
    channelViewModel: ChannelViewModel,
    epgViewModel: EpgViewModel,
    homeViewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    recordViewModel: RecordViewModel,
    reserveViewModel: ReserveViewModel,
    smbViewModel: SmbViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    onExitApp: () -> Unit
) {
    val themeName by settingsViewModel.appTheme.collectAsState(initial = "MONOTONE")
    val currentTheme = remember(themeName) {
        runCatching { AppTheme.valueOf(themeName) }.getOrDefault(AppTheme.MONOTONE)
    }

    var selectedTab by remember { mutableStateOf(PhoneTab.HOME) }
    var selectedProgram by remember { mutableStateOf<RecordedProgram?>(null) }
    var videoSection by remember { mutableStateOf(VideoSection.ROOT) }
    // ★ Hoisted here (alongside videoSection) so ビデオ list/grid scroll position
    // survives the player screen opening and closing — see PhoneVideoScreen.
    val recordListState = rememberLazyListState()
    val recordGridState = rememberLazyGridState()
    val fileLibraryListState = rememberLazyListState()
    val videoRootListState = rememberLazyListState()
    val homeListState = rememberLazyListState()
    var selectedSmbItem by remember { mutableStateOf<SmbItem?>(null) }
    var showPlayerControls by remember { mutableStateOf(true) }
    var isPlayerSubMenuOpen by remember { mutableStateOf(false) }
    var isSceneSearchOpen by remember { mutableStateOf(false) }
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }
    var playerShowOverlay by remember { mutableStateOf(true) }
    var playerIsManualOverlay by remember { mutableStateOf(false) }
    var playerIsPinnedOverlay by remember { mutableStateOf(false) }
    var playerIsSubMenuOpen by remember { mutableStateOf(false) }
    var isPlayerMiniListOpen by remember { mutableStateOf(false) }
    val defaultLiveQuality by settingsViewModel.liveQuality.collectAsState()
    val timeFormat by settingsViewModel.timeFormat.collectAsState()
    val defaultVideoQuality by settingsViewModel.videoQuality.collectAsState()
    var pendingPositionMs by remember { mutableStateOf(0L) }
    val programDetail by recordViewModel.programDetail.collectAsState()
    var selectedEpgProgram by remember { mutableStateOf<EpgProgram?>(null) }
    val favoriteBaseballTeams by settingsViewModel.favoriteBaseballTeams.collectAsState()
    val baseballListState = rememberLazyListState()

    // ★ スマホ対応 - 初期設定・接続エラーのガイダンス（TVの InitialSetupDialog /
    // ConnectionErrorDialog に相当）。MainRootScreen は Phone の場合ここへ即座に
    // routing して return するため、TV側のこのロジックは一切実行されない。
    val isSettingsInitialized by settingsViewModel.isSettingsInitialized.collectAsState()
    val channelConnectionError by channelViewModel.connectionError.collectAsState()
    var connectionErrorDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(channelConnectionError) {
        if (!channelConnectionError) connectionErrorDismissed = false
    }

    KomorebiTheme(theme = currentTheme) {
        // ── Bridge KomorebiTheme colors into standard Material 3 ──
        val colors = KomorebiTheme.colors

        // ★ スマホ対応 - ステータスバー（時刻/電波/バッテリー表示部分）の色を
        // アプリの背景色（基本テーマ設定に連動）に合わせる。TV は元々ステータス
        // バーを表示しないため影響なし。
        val context = LocalContext.current
        val view = LocalView.current
        SideEffect {
            val window = (context as? android.app.Activity)?.window
            if (window != null && !view.isInEditMode) {
                window.statusBarColor = colors.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                    !colors.isDark
            }
        }

        val m3ColorScheme = if (colors.isDark) {
            m3DarkColorScheme(
                primary = colors.accent,
                onPrimary = if (colors.isDark) Color.Black else Color.White,
                secondary = colors.accent,
                onSecondary = if (colors.isDark) Color.Black else Color.White,
                tertiary = colors.accent,
                background = colors.background,
                onBackground = colors.textPrimary,
                surface = colors.surface,
                onSurface = colors.textPrimary,
                surfaceVariant = colors.surface,
                onSurfaceVariant = colors.textSecondary,
                error = Color(0xFFF44336)
            )
        } else {
            m3LightColorScheme(
                primary = colors.accent,
                onPrimary = if (colors.isDark) Color.Black else Color.White,
                secondary = colors.accent,
                onSecondary = if (colors.isDark) Color.Black else Color.White,
                tertiary = colors.accent,
                background = colors.background,
                onBackground = colors.textPrimary,
                surface = colors.surface,
                onSurface = colors.textPrimary,
                surfaceVariant = colors.surface,
                onSurfaceVariant = colors.textSecondary,
                error = Color(0xFFD32F2F)
            )
        }

        M3Theme(colorScheme = m3ColorScheme) {
            // ★ プロ野球タブが消えた場合（フォロー球団をゼロにした場合）に選択中
            // タブが無効にならないようホームへ戻す
            LaunchedEffect(favoriteBaseballTeams.isEmpty(), selectedTab) {
                if (favoriteBaseballTeams.isEmpty() && selectedTab == PhoneTab.BASEBALL) {
                    selectedTab = PhoneTab.HOME
                }
            }

            // ★ When programDetail arrives, launch the player with resume position
            LaunchedEffect(programDetail) {
                val detail = programDetail
                if (detail != null && selectedProgram == null) {
                    selectedProgram = detail
                    showPlayerControls = true
                    recordViewModel.clearProgramDetail()
                }
            }

            // ★ ボトムナビのUIは常にコンポーズされたままにし、プレイヤーは
            // （`when` で置き換えるのではなく）その上に全画面オーバーレイとして
            // 重ねて描画する。PhoneHomeScreen/PhoneVideoScreen のようなタブ画面は
            // プレイヤーが開いている間も一切破棄されないため、それらの
            // LazyColumn/LazyGrid のスクロール状態やFlow購読（collectAsState、
            // ページング等）が失われることがない — 引き上げた LazyListState を
            // 単に再利用するだけでは、それを保持しているコンポーザブル自体が
            // 完全にアンマウント・再マウントされてしまう場合、それだけでは
            // 不十分。
            Box(modifier = Modifier.fillMaxSize()) {
                // ── ボトムナビ付きのメインUI ────────────────────────
                // ★ プロ野球タブはフォロー球団が設定されている時だけ出す
                val visibleTabs = remember(favoriteBaseballTeams.isEmpty()) {
                    PhoneTab.entries.filter { it != PhoneTab.BASEBALL || favoriteBaseballTeams.isNotEmpty() }
                }
                Scaffold(
                    bottomBar = {
                        ScrollableTabRow(
                            selectedTabIndex = visibleTabs.indexOf(selectedTab).coerceAtLeast(0),
                            edgePadding = 0.dp,
                            containerColor = M3Theme.colorScheme.surface,
                            contentColor = M3Theme.colorScheme.onSurface,
                            indicator = {},
                            divider = {}
                        ) {
                            visibleTabs.forEach { tab ->
                                Tab(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    text = { Text(tab.label, style = M3Theme.typography.labelSmall) },
                                    icon = {
                                        Icon(tab.icon, tab.label, modifier = Modifier.size(24.dp))
                                    },
                                    selectedContentColor = M3Theme.colorScheme.primary,
                                    unselectedContentColor = M3Theme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (selectedTab) {
                            PhoneTab.HOME -> PhoneHomeScreen(
                                homeViewModel = homeViewModel,
                                reserveViewModel = reserveViewModel,
                                channelViewModel = channelViewModel,
                                onNavigateToLive = { selectedTab = PhoneTab.LIVE },
                                onNavigateToEpg = { selectedTab = PhoneTab.EPG },
                                onEpgProgramSelected = { program ->
                                    selectedEpgProgram = program
                                    selectedTab = PhoneTab.EPG
                                },
                                onNavigateToReserve = { selectedTab = PhoneTab.RESERVE },
                                onChannelSelected = { channel ->
                                    selectedChannel = channel
                                    playerShowOverlay = true
                                    homeViewModel.saveLastChannel(channel)
                                },
                                listState = homeListState
                            )
                            PhoneTab.LIVE -> PhoneLiveScreen(
                                channelViewModel = channelViewModel,
                                settingsViewModel = settingsViewModel,
                                onChannelSelected = { uiChannel ->
                                    selectedChannel = uiChannel.channel
                                    playerShowOverlay = true
                                    homeViewModel.saveLastChannel(uiChannel.channel)
                                }
                            )
                            PhoneTab.VIDEO -> PhoneVideoScreen(
                                recordViewModel = recordViewModel,
                                smbViewModel = smbViewModel,
                                homeViewModel = homeViewModel,
                                currentSection = videoSection,
                                onSectionChange = { videoSection = it },
                                onRecordedProgramSelected = { program ->
                                    selectedProgram = program
                                    showPlayerControls = true
                                },
                                onSmbFileSelected = { item ->
                                    selectedSmbItem = item
                                    showPlayerControls = true
                                },
                                onWatchHistorySelected = { videoId, positionMs ->
                                    pendingPositionMs = positionMs
                                    recordViewModel.fetchProgramDetail(videoId)
                                },
                                videoRootListState = videoRootListState,
                                recordListState = recordListState,
                                recordGridState = recordGridState,
                                fileLibraryListState = fileLibraryListState
                            )
                            PhoneTab.EPG -> PhoneEpgScreen(
                                epgViewModel = epgViewModel,
                                channelViewModel = channelViewModel,
                                reserveViewModel = reserveViewModel,
                                settingsViewModel = settingsViewModel,
                                initialProgram = selectedEpgProgram,
                                onInitialProgramConsumed = { selectedEpgProgram = null }
                            )
                            PhoneTab.RESERVE -> PhoneReserveScreen(
                                reserveViewModel = reserveViewModel
                            )
                            PhoneTab.BASEBALL -> PhoneBaseballScreen(
                                homeViewModel = homeViewModel,
                                timeFormat = timeFormat,
                                listState = baseballListState,
                                onChannelSelected = { channel ->
                                    selectedChannel = channel
                                    playerShowOverlay = true
                                    homeViewModel.saveLastChannel(channel)
                                },
                                onProgramSelected = { program ->
                                    selectedEpgProgram = program
                                    selectedTab = PhoneTab.EPG
                                }
                            )
                            PhoneTab.SETTINGS -> PhoneSettingScreen(
                                settingsViewModel = settingsViewModel,
                                homeViewModel = homeViewModel,
                                recordViewModel = recordViewModel
                            )
                        }
                    }
                }

                // ── Player overlays (drawn on top of the bottom-nav UI above) ──
                when {
                    // ── Video player ───────────────────────────────
                    selectedProgram != null -> {
                        VideoPlayerScreen(
                            program = selectedProgram!!,
                            initialPositionMs = pendingPositionMs,
                            initialQuality = defaultVideoQuality,
                            showControls = showPlayerControls,
                            onShowControlsChange = { showPlayerControls = it },
                            isSubMenuOpen = isPlayerSubMenuOpen,
                            onSubMenuToggle = { isPlayerSubMenuOpen = it },
                            isSceneSearchOpen = isSceneSearchOpen,
                            onSceneSearchToggle = { isSceneSearchOpen = it },
                            onBackPressed = {
                                selectedProgram = null
                                showPlayerControls = true
                                isPlayerSubMenuOpen = false
                                pendingPositionMs = 0L
                            },
                            onShowToast = {},
                            isPiPMode = false,
                            onPiPRequested = {},
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                            onTap = { showPlayerControls = !showPlayerControls }
                        )
                    }

                    // ── SMB player ─────────────────────────────────
                    selectedSmbItem != null -> {
                        val recentRecordings by recordViewModel.recentRecordings.collectAsState(initial = emptyList())
                        val baseProgram = recentRecordings.firstOrNull()
                        if (baseProgram != null) {
                            val dummyProgram = baseProgram.copy(
                                id = selectedSmbItem!!.path.hashCode(),
                                title = selectedSmbItem!!.name,
                                description = "SMBネットワーク再生: ${selectedSmbItem!!.path}"
                            )
                            SmbVlcPlayerScreen(
                                program = dummyProgram,
                                smbItem = selectedSmbItem!!,
                                initialPositionMs = 0L,
                                showControls = showPlayerControls,
                                onShowControlsChange = { showPlayerControls = it },
                                isSubMenuOpen = isPlayerSubMenuOpen,
                                onSubMenuToggle = { isPlayerSubMenuOpen = it },
                                isSceneSearchOpen = isSceneSearchOpen,
                                onSceneSearchToggle = { isSceneSearchOpen = it },
                                onBackPressed = {
                                    selectedSmbItem = null
                                    showPlayerControls = true
                                    isPlayerSubMenuOpen = false
                                },
                                onShowToast = {},
                                isPiPMode = false,
                                onPiPRequested = {},
                                resizeMode = true,
                                onTap = { showPlayerControls = !showPlayerControls }
                            )
                        }
                    }

                    // ── Live player ────────────────────────────────
                    selectedChannel != null -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LivePlayerScreen(
                                channel = selectedChannel!!,
                                initialQuality = defaultLiveQuality,
                                isBaseballMode = false,
                                isMiniListOpen = isPlayerMiniListOpen,
                                onMiniListToggle = { isPlayerMiniListOpen = it },
                                showOverlay = playerShowOverlay,
                                onShowOverlayChange = { playerShowOverlay = it },
                                isManualOverlay = playerIsManualOverlay,
                                onManualOverlayChange = { playerIsManualOverlay = it },
                                isPinnedOverlay = playerIsPinnedOverlay,
                                onPinnedOverlayChange = { playerIsPinnedOverlay = it },
                                isSubMenuOpen = playerIsSubMenuOpen,
                                onSubMenuToggle = { playerIsSubMenuOpen = it },
                                onChannelSelect = { newChannel ->
                                selectedChannel = newChannel
                                homeViewModel.saveLastChannel(newChannel)
                            },
                                onBackPressed = {
                                    selectedChannel = null
                                    playerShowOverlay = true
                                },
                                onShowToast = {},
                                isPiPMode = false,
                                onPiPRequested = {},
                                timeFormat = timeFormat,
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                                onTap = {
                                    if (playerShowOverlay) {
                                        playerShowOverlay = false
                                        playerIsManualOverlay = false
                                    } else {
                                        playerShowOverlay = true
                                        playerIsManualOverlay = false
                                    }
                                }
                            )
                            if (playerShowOverlay && !playerIsSubMenuOpen) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(onClick = { playerIsSubMenuOpen = true }) {
                                        Icon(Icons.Default.Settings, "設定", tint = Color.White)
                                    }
                                    IconButton(onClick = { isPlayerMiniListOpen = true }) {
                                        Icon(Icons.Default.List, "チャンネル", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 初期設定 / 接続案内 ─────────────────────────────
                // プレイヤーが表示されていない時だけ関係する。再生UIと
                // 競合しないように。
                val isPlayerActive = selectedProgram != null || selectedSmbItem != null || selectedChannel != null
                if (!isPlayerActive) {
                    // ★ バグ修正: 元々 selectedTab に関係なく !isSettingsInitialized
                    // だけで表示していたため、「設定画面へ移動」ボタンを押して
                    // selectedTab を SETTINGS に変えても、このオーバーレイ自体が
                    // 毎フレーム即座に再表示され、設定画面そのものが一切操作
                    // できない状態になっていた（ボタンが反応しないように見え、
                    // かつアプリを再起動しても同じ状態から抜け出せなかった）。
                    // PhoneConnectionErrorOverlay と同じく selectedTab !=
                    // PhoneTab.SETTINGS を条件に加えることで、設定タブに移動した
                    // 後はオーバーレイが退いて実際に設定を編集できるようにする。
                    if (!isSettingsInitialized && selectedTab != PhoneTab.SETTINGS) {
                        PhoneSetupRequiredOverlay(
                            onGoToSettings = { selectedTab = PhoneTab.SETTINGS }
                        )
                    } else if (isSettingsInitialized && channelConnectionError && selectedTab != PhoneTab.SETTINGS && !connectionErrorDismissed) {
                        PhoneConnectionErrorOverlay(
                            onGoToSettings = { selectedTab = PhoneTab.SETTINGS },
                            onDismiss = { connectionErrorDismissed = true },
                            onExit = onExitApp
                        )
                    }
                }
            }
        }
    }
}

// ─── 初期設定 / 接続案内オーバーレイ ────────────────────────────────────────
// TVの InitialSetupDialog / ConnectionErrorDialog (ui/main/MainDialogs.kt) に
// 相当するPhone専用版。MainRootScreen はPhoneをそのまま PhoneRootScreen へ
// ルーティングして、TV専用のそのロジックが動く前にreturnしてしまうため、これが
// 無いと、Phoneでの新規インストール直後はライブタブ自身の空状態スピナー以外、
// 案内が一切無い状態になってしまう。

@Composable
private fun PhoneSetupRequiredOverlay(onGoToSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = M3Theme.colorScheme.surface,
            modifier = Modifier
                .padding(32.dp)
                .widthIn(max = 400.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = AppStrings.SETUP_REQUIRED_TITLE,
                    style = M3Theme.typography.headlineSmall,
                    color = M3Theme.colorScheme.onSurface,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = AppStrings.SETUP_REQUIRED_MESSAGE,
                    style = M3Theme.typography.bodyMedium,
                    color = M3Theme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onGoToSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(AppStrings.GO_TO_SETTINGS)
                }
            }
        }
    }
}

@Composable
private fun PhoneConnectionErrorOverlay(
    onGoToSettings: () -> Unit,
    onDismiss: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = M3Theme.colorScheme.surface,
            modifier = Modifier
                .padding(32.dp)
                .widthIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = AppStrings.CONNECTION_ERROR_TITLE,
                    style = M3Theme.typography.headlineSmall,
                    color = Color(0xFFFF5252),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = AppStrings.CONNECTION_ERROR_MESSAGE,
                    style = M3Theme.typography.bodyMedium,
                    color = M3Theme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(onClick = onExit, modifier = Modifier.weight(1f)) {
                        Text(AppStrings.EXIT_APP)
                    }
                    Button(onClick = onGoToSettings, modifier = Modifier.weight(1f)) {
                        Text(AppStrings.GO_TO_SETTINGS_SHORT)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // ★ スマホ対応 - TVのダイアログと異なり、下部ナビは常に表示されて
                // いて他のタブは操作できるため、「閉じる」で一時的に引っ込められる
                // ようにしておく（再発時は再度表示される）
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("閉じる")
                }
            }
        }
    }
}
