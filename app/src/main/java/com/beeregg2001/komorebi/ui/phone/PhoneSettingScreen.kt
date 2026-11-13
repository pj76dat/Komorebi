package com.beeregg2001.komorebi.ui.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.beeregg2001.komorebi.viewmodel.HomeViewModel
import com.beeregg2001.komorebi.viewmodel.RecordViewModel
import com.beeregg2001.komorebi.viewmodel.SettingsViewModel
import com.beeregg2001.komorebi.viewmodel.SmbServer
import com.beeregg2001.komorebi.ui.setting.getThemeFromModeAndSeason

// ★ プロ野球モード - フォロー球団の選択肢（TVの SettingScreen.kt と同一リスト）
// このファイルの openSelection/openMultiSelection の慣例に合わせて (保存される値, 表示ラベル) の順。
// 保存される値は球団の短縮名 — HomeViewModel の filterFavoriteBaseballGames が
// 番組タイトル・概要にこの文字列が含まれるかで判定するため、正式名称ではなく
// 短縮名を保存する必要がある（TVのSettingScreen.ktも同様）。
val BASEBALL_TEAM_OPTIONS = listOf(
    "阪神" to "阪神タイガース",
    "広島" to "広島東洋カープ",
    "DeNA" to "横浜DeNAベイスターズ",
    "巨人" to "読売ジャイアンツ",
    "ヤクルト" to "東京ヤクルトスワローズ",
    "中日" to "中日ドラゴンズ",
    "オリックス" to "オリックス・バファローズ",
    "ロッテ" to "千葉ロッテマリーンズ",
    "ソフトバンク" to "福岡ソフトバンクホークス",
    "楽天" to "東北楽天ゴールデンイーグルス",
    "西武" to "埼玉西武ライオンズ",
    "日本ハム" to "北海道日本ハムファイターズ",
    "侍ジャパン" to "侍ジャパン"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSettingScreen(
    settingsViewModel: SettingsViewModel,
    homeViewModel: HomeViewModel,
    recordViewModel: RecordViewModel
) {
    // ★ アプリ情報 / OSS ライセンス（TVの AppInfoContent 相当）
    var showAppInfo by remember { mutableStateOf(false) }
    if (showAppInfo) {
        PhoneAppInfoScreen(onBack = { showAppInfo = false })
        return
    }

    // ── Basic settings ─────────────────────────────────────────
    val receiveBetaUpdates by settingsViewModel.receiveBetaUpdates.collectAsState()
    val totalRecordCount by settingsViewModel.totalRecordCount.collectAsState()

    // ── Connection settings ────────────────────────────────────
    val backendType by settingsViewModel.backendType.collectAsState()
    val konomiIp by settingsViewModel.konomiIp.collectAsState()
    val konomiPort by settingsViewModel.konomiPort.collectAsState()
    val edcbIp by settingsViewModel.edcbIp.collectAsState()
    val edcbPort by settingsViewModel.edcbPort.collectAsState()
    val edcbRecordPlayMethod by settingsViewModel.edcbRecordPlayMethod.collectAsState()
    val preferredSource by settingsViewModel.preferredSource.collectAsState()
    val mirakurunIp by settingsViewModel.mirakurunIp.collectAsState()
    val mirakurunPort by settingsViewModel.mirakurunPort.collectAsState()
    val smbServerList by settingsViewModel.smbServerList.collectAsState()
    val cfClientId by settingsViewModel.cfAccessClientId.collectAsState()
    val cfClientSecret by settingsViewModel.cfAccessClientSecret.collectAsState()

    // ── Playback settings ──────────────────────────────────────
    val availableQualities by settingsViewModel.availableQualities.collectAsState()
    val liveQuality by settingsViewModel.liveQuality.collectAsState()
    val videoQuality by settingsViewModel.videoQuality.collectAsState()
    val liveSubtitleDefault by settingsViewModel.liveSubtitleDefault.collectAsState()
    val videoSubtitleDefault by settingsViewModel.videoSubtitleDefault.collectAsState()
    val subtitleCommentLayer by settingsViewModel.subtitleCommentLayer.collectAsState()
    val audioOutputMode by settingsViewModel.audioOutputMode.collectAsState()
    val playerUiMode by settingsViewModel.playerUiMode.collectAsState()
    val autoCmSkip by settingsViewModel.autoCmSkip.collectAsState()

    // ── Recording settings ─────────────────────────────────────
    val batchList by settingsViewModel.postRecordingBatchList.collectAsState()

    // ── Home settings ──────────────────────────────────────────
    val appTheme by settingsViewModel.appTheme.collectAsState()
    val homePickupGenre by settingsViewModel.homePickupGenre.collectAsState()
    val homePickupTime by settingsViewModel.homePickupTime.collectAsState()
    val excludePaidBroadcasts by settingsViewModel.excludePaidBroadcasts.collectAsState()

    // ── Display settings additions ─────────────────────────────
    val startupChannel by settingsViewModel.startupChannel.collectAsState()
    val timeFormat by settingsViewModel.timeFormat.collectAsState()
    val defaultRecordListView by settingsViewModel.defaultRecordListView.collectAsState()

    // ── Comment settings ───────────────────────────────────────
    val commentDefaultDisplay by settingsViewModel.commentDefaultDisplay.collectAsState()
    val commentSpeed by settingsViewModel.commentSpeed.collectAsState()
    val commentFontSize by settingsViewModel.commentFontSize.collectAsState()
    val commentOpacity by settingsViewModel.commentOpacity.collectAsState()
    val commentMaxLines by settingsViewModel.commentMaxLines.collectAsState()

    // ── Display settings ───────────────────────────────────────
    val startupTab by settingsViewModel.startupTab.collectAsState()
    val hideSubChannels by settingsViewModel.hideSubChannels.collectAsState()

    // ── Addon / Lab settings ───────────────────────────────────
    val favoriteBaseballTeams by settingsViewModel.favoriteBaseballTeams.collectAsState()

    // ── Dialog state ───────────────────────────────────────────
    var inputDialogTitle by remember { mutableStateOf("") }
    var inputDialogValue by remember { mutableStateOf("") }
    var inputDialogAction by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var showInputDialog by remember { mutableStateOf(false) }

    var showSelectionDialog by remember { mutableStateOf(false) }
    var selectionDialogTitle by remember { mutableStateOf("") }
    var selectionDialogOptions by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var selectionDialogCurrent by remember { mutableStateOf("") }
    var selectionDialogAction by remember { mutableStateOf<((String) -> Unit)?>(null) }

    // ★ プロ野球モード - フォロー球団の複数選択ダイアログ用の状態
    var showMultiSelectionDialog by remember { mutableStateOf(false) }
    var multiSelectionDialogTitle by remember { mutableStateOf("") }
    var multiSelectionDialogOptions by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var multiSelectionDialogCurrent by remember { mutableStateOf(setOf<String>()) }
    var multiSelectionDialogAction by remember { mutableStateOf<((Set<String>) -> Unit)?>(null) }

    var confirmTitle by remember { mutableStateOf("") }
    var confirmMessage by remember { mutableStateOf("") }
    var confirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    var showSmbDialog by remember { mutableStateOf(false) }
    var editingSmbServer by remember { mutableStateOf<SmbServer?>(null) }

    var showBatchDialog by remember { mutableStateOf(false) }
    var showAddBatchDialog by remember { mutableStateOf(false) }

    // ── Helpers ────────────────────────────────────────────────
    val openInput: (String, String, (String) -> Unit) -> Unit = { title, current, action ->
        inputDialogTitle = title; inputDialogValue = current
        inputDialogAction = action; showInputDialog = true
    }
    val openSelection: (String, List<Pair<String, String>>, String, (String) -> Unit) -> Unit =
        { title, options, current, action ->
            selectionDialogTitle = title; selectionDialogOptions = options
            selectionDialogCurrent = current; selectionDialogAction = action
            showSelectionDialog = true
        }
    val openConfirm: (String, String, () -> Unit) -> Unit = { title, msg, action ->
        confirmTitle = title; confirmMessage = msg
        confirmAction = action; showConfirmDialog = true
    }
    val openMultiSelection: (String, List<Pair<String, String>>, Set<String>, (Set<String>) -> Unit) -> Unit =
        { title, options, current, action ->
            multiSelectionDialogTitle = title; multiSelectionDialogOptions = options
            multiSelectionDialogCurrent = current; multiSelectionDialogAction = action
            showMultiSelectionDialog = true
        }

    // ── Main content ───────────────────────────────────────────
    LazyColumn(modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)) {

        // ══ 基本設定 ══════════════════════════════════════════
        item { PhoneSettingSectionHeader("基本設定", Icons.Default.SettingsApplications) }

        item { PhoneSettingSectionSubHeader("システム設定") }

        item {
            PhoneSettingToggleItem("ベータ版のアップデートを受け取る", Icons.Default.SystemUpdate, receiveBetaUpdates) {
                settingsViewModel.updateReceiveBetaUpdates(!receiveBetaUpdates)
            }
        }

        item { PhoneSettingSectionSubHeader("データベース情報") }

        item { PhoneSettingItem("録画データ件数", "$totalRecordCount 件", Icons.Default.Storage, null) }

        item {
            PhoneSettingActionItem("手動でフル同期を実行", "録画データを全件同期します",
                Icons.Default.Sync) {
                openConfirm("データベースの再構築",
                    "すべての録画データをサーバーから再取得します。よろしいですか？") {
                    settingsViewModel.triggerFullSync()
                }
            }
        }

        item { PhoneSettingSectionSubHeader("データ管理") }

        item {
            PhoneSettingActionItem("前回視聴したチャンネル履歴を削除", "",
                Icons.Default.DeleteOutline) {
                openConfirm("履歴の削除", "前回視聴したチャンネル履歴を削除しますか？") {
                    homeViewModel.clearLastChannelHistory()
                }
            }
        }

        item {
            PhoneSettingActionItem("録画の視聴履歴を削除", "",
                Icons.Default.DeleteOutline) {
                openConfirm("履歴の削除", "録画の視聴履歴を削除しますか？") {
                    recordViewModel.clearWatchHistory()
                }
            }
        }

        // ══ 接続設定 ══════════════════════════════════════════
        item { PhoneSettingSectionHeader("接続設定", Icons.Default.CastConnected) }

        item { PhoneSettingSectionSubHeader("メインシステム設定") }

        item {
            // ★ 修正: 保存する値は TV/SettingsRepository が isInitialized 判定等で
            // 比較に使っている "KONOMITV"（大文字）である必要があるが、誤って
            // 表示ラベルと同じ "KonomiTV"（混在ケース）を保存していたため、
            // KonomiTVを選んでいる限りIPが正しく設定されていても
            // isInitialized が常にfalseになり、初期設定ダイアログが
            // 消えなくなるバグがあった。
            PhoneSettingItem("バックエンド種別", when (backendType) {
                "KONOMITV" -> "KonomiTV"
                "EDCB" -> "EDCB (EpgTimerSrv)"
                "MIRAKURUN_ONLY" -> "Mirakurun (録画なし)"
                else -> backendType
            }, Icons.Default.Dns) {
                openSelection("バックエンド種別",
                    listOf(
                        "KONOMITV" to "KonomiTV",
                        "EDCB" to "EDCB (EpgTimerSrv)",
                        "MIRAKURUN_ONLY" to "Mirakurun (録画なし)"
                    ), backendType) {
                    settingsViewModel.updateBackendType(it)
                }
            }
        }

        item {
            PhoneSettingItem("KonomiTV IPアドレス", konomiIp.ifEmpty { "未設定" }, Icons.Default.Link) {
                openInput("KonomiTV IPアドレス", konomiIp) { settingsViewModel.updateKonomiIp(it) }
            }
        }

        item {
            PhoneSettingItem("KonomiTV ポート", konomiPort.ifEmpty { "未設定" }, Icons.Default.Numbers) {
                openInput("KonomiTV ポート", konomiPort) { settingsViewModel.updateKonomiPort(it) }
            }
        }

        item {
            PhoneSettingItem("EDCB IPアドレス", edcbIp.ifEmpty { "未設定" }, Icons.Default.Link) {
                openInput("EDCB IPアドレス", edcbIp) { settingsViewModel.updateEdcbIp(it) }
            }
        }

        item {
            PhoneSettingItem("EDCB ポート", edcbPort.ifEmpty { "未設定" }, Icons.Default.Numbers) {
                openInput("EDCB ポート", edcbPort) { settingsViewModel.updateEdcbPort(it) }
            }
        }

        // ★ 修正: 元は「HTTP/SMB」という選択肢を持つ別物の設定として実装されて
        // いたが、TVの同名項目（EDCB 録画再生設定 > 録画ファイルの再生方式）は
        // 実際には「直接アクセス(DIRECT)/API経由(API)」の2択で、
        // settingsViewModel.edcbRecordPlayMethod（値は"DIRECT"/"API"）に対応する。
        // HTTP/SMBという値はTV側のどこにも存在しないため、TVに存在しない
        // phone専用の項目ではなく、単に選択肢を誤って実装していたバグと判断し、
        // 正しい選択肢に修正した。あわせてTVと同じく「利用するシステム」が
        // EDCBの時だけ表示されるようにした。
        if (backendType == "EDCB") {
            item { PhoneSettingSectionSubHeader("EDCB 録画再生設定") }
            item {
                PhoneSettingItem("録画ファイルの再生方式",
                    if (edcbRecordPlayMethod == "DIRECT") "直接アクセス (高速シーク可)" else "API経由 (api/xcode)",
                    Icons.Default.PlayCircleOutline) {
                    openSelection("録画ファイルの再生方式",
                        listOf(
                            "API" to "API経由 (api/xcode)",
                            "DIRECT" to "直接アクセス (高速シーク可)"
                        ), edcbRecordPlayMethod) {
                        settingsViewModel.updateEdcbRecordPlayMethod(it)
                    }
                }
            }
        }

        item {
            PhoneSettingItem("IPアドレス (Mirakurun)", mirakurunIp.ifEmpty { "未設定" }, Icons.Default.Router) {
                openInput("IPアドレス (Mirakurun)", mirakurunIp) {
                    settingsViewModel.updateMirakurunIp(it)
                }
            }
        }

        item {
            PhoneSettingItem("ポート番号 (Mirakurun)", mirakurunPort.ifEmpty { "未設定" }, Icons.Default.Numbers) {
                openInput("ポート番号 (Mirakurun)", mirakurunPort) {
                    settingsViewModel.updateMirakurunPort(it)
                }
            }
        }

        // ★ 追加: TVの「ライブ視聴ソースの優先設定」に対応する項目。
        // 「利用するシステム」がMirakurun(録画なし)の時は選ぶ意味が無い
        // （メインシステム自体がMirakurunのため）のでTVと同じく非表示にする。
        // 「EDCB」を選んだ時の表示ラベルは、利用するシステム自体がEDCBかどうかで
        // 変わる（EDCBがメインシステムの時は「ダイレクトストリーミング」、
        // それ以外の時は「(TCP)を優先」）— これもTVの表示ロジックと同一。
        if (backendType != "MIRAKURUN_ONLY") {
            item { PhoneSettingSectionSubHeader("ライブ視聴ソースの優先設定") }
            item {
                val srcLabel = when {
                    preferredSource == "MIRAKURUN" -> "Mirakurunを優先"
                    preferredSource == "EDCB" && backendType == "EDCB" -> "EDCB (ダイレクトストリーミング)"
                    preferredSource == "EDCB" -> "EDCB (TCP) を優先"
                    else -> "メインシステムに従う (トランスコード)"
                }
                PhoneSettingItem("優先ソース", srcLabel, Icons.Default.PriorityHigh) {
                    val edcbLabel = if (backendType == "EDCB") "EDCB (ダイレクトストリーミング)" else "EDCB (TCP) を優先"
                    openSelection("優先ソース",
                        listOf(
                            "KONOMITV" to "メインシステムに従う (トランスコード)",
                            "MIRAKURUN" to "Mirakurunを優先",
                            "EDCB" to edcbLabel
                        ), preferredSource) {
                        settingsViewModel.updatePreferredSource(it)
                    }
                }
            }
        }

        item { PhoneSettingSectionSubHeader("ファイルライブラリ (SMB) 接続設定") }

        items(smbServerList) { server ->
            Surface(onClick = { editingSmbServer = server; showSmbDialog = true },
                modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(server.name) },
                    supportingContent = { Text("${server.ip}:${server.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingContent = { Icon(Icons.Default.Storage, null,
                        tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        IconButton(onClick = { settingsViewModel.deleteSmbServer(server.id) }) {
                            Icon(Icons.Default.Delete, null,
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }

        item {
            ListItem(
                headlineContent = { Text("SMBサーバーを追加",
                    color = MaterialTheme.colorScheme.primary) },
                leadingContent = { Icon(Icons.Default.Add, null,
                    tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { editingSmbServer = null; showSmbDialog = true }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }

        item { PhoneSettingSectionSubHeader("Cloudflare Zero TrustA(オプション)") }

        item {
            PhoneSettingItem("Access Client ID",
                if (cfClientId.isEmpty()) "未設定" else "設定済み", Icons.Default.Cloud) {
                openInput("Access Client ID", cfClientId) {
                    settingsViewModel.updateCfAccessClientId(it)
                }
            }
        }

        item {
            PhoneSettingItem("Access Client Secret",
                if (cfClientSecret.isEmpty()) "未設定" else "設定済み", Icons.Default.Key) {
                openInput("Access Client Secret", cfClientSecret) {
                    settingsViewModel.updateCfAccessClientSecret(it)
                }
            }
        }
        // ══ 再生設定 ══════════════════════════════════════════
        item { PhoneSettingSectionHeader("再生設定", Icons.Default.PlayCircle) }

        item { PhoneSettingSectionSubHeader("画質設定") }

        item {
            PhoneSettingItem("ライブ画質", liveQuality.ifEmpty { "自動" }, Icons.Default.LiveTv) {
                openSelection("ライブ画質",
                    availableQualities.map { it.value to it.label }, liveQuality) {
                    settingsViewModel.updateLiveQuality(it)
                }
            }
        }

        item {
            PhoneSettingItem("録画画質", videoQuality.ifEmpty { "自動" }, Icons.Default.VideoFile) {
                openSelection("録画画質",
                    availableQualities.map { it.value to it.label }, videoQuality) {
                    settingsViewModel.updateVideoQuality(it)
                }
            }
        }

        item { PhoneSettingSectionSubHeader("字幕・音声設定") }

        item {
            PhoneSettingItem("ライブ字幕 デフォルト表示",
                if (liveSubtitleDefault == "ON") "表示" else "非表示", Icons.Default.Subtitles) {
                openSelection("ライブ字幕 デフォルト表示",
                    listOf("ON" to "表示", "OFF" to "非表示"), liveSubtitleDefault) {
                    settingsViewModel.updateLiveSubtitleDefault(it)
                }
            }
        }

        item {
            PhoneSettingItem("ビデオ字幕 デフォルト表示",
                if (videoSubtitleDefault == "ON") "表示" else "非表示", Icons.Default.ClosedCaption) {
                openSelection("ビデオ字幕 デフォルト表示",
                    listOf("ON" to "表示", "OFF" to "非表示"), videoSubtitleDefault) {
                    settingsViewModel.updateVideoSubtitleDefault(it)
                }
            }
        }

        item {
            PhoneSettingItem("字幕/コメント表示順",
                if (subtitleCommentLayer == "CommentOnTop") "コメントを前面" else "字幕を前面", Icons.Default.Layers) {
                openSelection("字幕/コメント表示順",
                    listOf("CommentOnTop" to "コメントを前面", "SubtitleOnTop" to "字幕を前面"),
                    subtitleCommentLayer) {
                    settingsViewModel.updateSubtitleCommentLayer(it)
                }
            }
        }

        item {
            PhoneSettingItem("音声出力モード", when (audioOutputMode) {
                "DOWNMIX" -> "ダウンミックス (2ch固定・互換性優先)"
                "PASSTHROUGH" -> "パススルー (多チャンネル維持)"
                else -> audioOutputMode
            }, Icons.Default.AudioFile) {
                openSelection("音声出力モード",
                    listOf(
                        "DOWNMIX" to "ダウンミックス (2ch固定・互換性優先)",
                        "PASSTHROUGH" to "パススルー (多チャンネル維持)"
                    ), audioOutputMode) {
                    settingsViewModel.updateAudioOutputMode(it)
                }
            }
        }

        item { PhoneSettingSectionSubHeader("プレイヤー操作・UI設定") }

        item {
            PhoneSettingItem("プレイヤーUIモード",
                if (playerUiMode == "MODERN") "モダン(オンスクリーン操作)"
                else "クラシック(D-Pad完結)", Icons.Default.SettingsRemote) {
                openSelection("プレイヤーUIモード",
                    listOf(
                        "MODERN" to "モダン(オンスクリーン操作)",
                        "CLASSIC" to "クラシック(D-Pad完結)"
                    ), playerUiMode) {
                    settingsViewModel.updatePlayerUiMode(it)
                }
            }
        }

        item {
            PhoneSettingItem("自動CMスキップ",
                if (autoCmSkip == "on") "有効" else "無効", Icons.Default.FastForward) {
                openSelection("自動CMスキップ",
                    listOf("on" to "有効", "off" to "無効"), autoCmSkip) {
                    settingsViewModel.updateAutoCmSkip(it)
                }
            }
        }

        // ══ 録画設定 ══════════════════════════════════════════
        item { PhoneSettingSectionHeader("録画設定", Icons.Default.VideoSettings) }

        item { PhoneSettingSectionSubHeader("録画後実行バッチの設定") }

        items(batchList) { batch ->
            ListItem(
                headlineContent = { Text(batch.name) },
                supportingContent = { Text(batch.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingContent = { Icon(Icons.Default.Terminal, null) },
                trailingContent = {
                    IconButton(onClick = {
                        settingsViewModel.deletePostRecordingBatch(batch)
                    }) {
                        Icon(Icons.Default.Delete, null,
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }

        item {
            ListItem(
                headlineContent = { Text("新しいバッチを追加",
                    color = MaterialTheme.colorScheme.primary) },
                leadingContent = { Icon(Icons.Default.Add, null,
                    tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { showAddBatchDialog = true }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }

        // ══ ホーム設定 ════════════════════════════════════════════
        item { PhoneSettingSectionHeader("ホーム設定", Icons.Default.Home) }

        item { PhoneSettingSectionSubHeader("インターフェース設定") }

        item {
            val isTimeLinked = appTheme.startsWith("KOMOREBI") || appTheme.startsWith("KYLE")
            val isLight = appTheme.contains("LIGHT") || appTheme == "HIGHTONE"
            val currentMode = when {
                isTimeLinked -> "TIME_LINKED"
                isLight -> "LIGHT"
                else -> "DARK"
            }
            PhoneSettingItem("基本テーマ", when (currentMode) {
                "DARK" -> "ダークモード"
                "LIGHT" -> "ライトモード"
                else -> "時間連動テーマ"
            }, Icons.Default.Brightness4) {
                openSelection("基本テーマ", listOf(
                    "DARK" to "ダークモード",
                    "LIGHT" to "ライトモード",
                    "TIME_LINKED" to "時間連動テーマ"
                ), currentMode) { selected ->
                    val newTheme = when (selected) {
                        "TIME_LINKED" -> "KOMOREBI"
                        "DARK" -> getThemeFromModeAndSeason(true, "DEFAULT")
                        "LIGHT" -> getThemeFromModeAndSeason(false, "DEFAULT")
                        else -> "MONOTONE"
                    }
                    settingsViewModel.updateAppTheme(newTheme)
                }
            }
        }

        item {
            val isTimeLinked = appTheme.startsWith("KOMOREBI") || appTheme.startsWith("KYLE")
            val isLight = appTheme.contains("LIGHT") || appTheme == "HIGHTONE"
            val currentSeason = when {
                appTheme.startsWith("KOMOREBI") -> "KOMOREBI"
                appTheme.startsWith("KYLE") -> "KYLE"
                appTheme.contains("SPRING") -> "SPRING"
                appTheme.contains("SUMMER") -> "SUMMER"
                appTheme.contains("AUTUMN") -> "AUTUMN"
                appTheme.contains("WINTER") -> "WINTER"
                else -> "DEFAULT"
            }
            val title = if (isTimeLinked) "時間連動セット" else "テーマカラー"
            val currentLabel = when (currentSeason) {
                "KOMOREBI" -> "木漏れ日セット"
                "KYLE" -> "カイルセット"
                "SPRING" -> "春"
                "SUMMER" -> "夏"
                "AUTUMN" -> "秋"
                "WINTER" -> "冬"
                else -> "デフォルト"
            }
            val options = if (isTimeLinked) listOf(
                "KOMOREBI" to "木漏れ日セット",
                "KYLE" to "カイルセット"
            ) else listOf(
                "DEFAULT" to "デフォルト",
                "SPRING" to "春",
                "SUMMER" to "夏",
                "AUTUMN" to "秋",
                "WINTER" to "冬"
            )
            PhoneSettingItem(title, currentLabel, Icons.Default.ColorLens) {
                openSelection(title, options, currentSeason) { selected ->
                    val newTheme = if (selected == "KOMOREBI" || selected == "KYLE") {
                        selected
                    } else {
                        getThemeFromModeAndSeason(!isLight, selected)
                    }
                    settingsViewModel.updateAppTheme(newTheme)
                }
            }
        }

        item { PhoneSettingSectionSubHeader("ホーム画面ピックアップ設定") }

        item {
            PhoneSettingItem("対象ジャンル", homePickupGenre, Icons.Default.AutoAwesome) {
                openSelection("対象ジャンル", listOf(
                    "アニメ" to "アニメ",
                    "映画" to "映画",
                    "ドラマ" to "ドラマ",
                    "スポーツ" to "スポーツ",
                    "音楽" to "音楽",
                    "バラエティ" to "バラエティ",
                    "ドキュメンタリー" to "ドキュメンタリー"
                ), homePickupGenre) { settingsViewModel.updateHomePickupGenre(it) }
            }
        }

        item {
            PhoneSettingItem("対象時間帯", homePickupTime, Icons.Default.Schedule) {
                openSelection("対象時間帯", listOf(
                    "自動" to "自動",
                    "朝" to "朝",
                    "昼" to "昼",
                    "夜" to "夜"
                ), homePickupTime) { settingsViewModel.updateHomePickupTime(it) }
            }
        }

        item {
            PhoneSettingToggleItem("有料番組を除外", Icons.Default.Lock, excludePaidBroadcasts == "ON") {
                settingsViewModel.updateExcludePaidBroadcasts(
                    if (excludePaidBroadcasts == "ON") "OFF" else "ON"
                )
            }
        }

        // ══ 表示設定 ══════════════════════════════════════════
        item { PhoneSettingSectionHeader("表示設定", Icons.Default.Dashboard) }

        item { PhoneSettingSectionSubHeader("インターフェース設定") }

        item {
            PhoneSettingItem("起動タブ", startupTab, Icons.Default.Launch) {
                openSelection("起動タブ",
                    listOf(
                        "ホーム" to "ホーム",
                        "ライブ" to "ライブ",
                        "ビデオ" to "ビデオ",
                        "番組表" to "番組表",
                        "録画予約" to "録画予約"
                    ), startupTab) {
                    settingsViewModel.updateStartupTab(it)
                }
            }
        }

        item {
            PhoneSettingItem("起動チャンネル",
                if (startupChannel == "OFF") "設定なし" else startupChannel, Icons.Default.LiveTv, null)
        }

        item {
            PhoneSettingItem("デフォルト録画一覧表示",
                if (defaultRecordListView == "LIST") "リスト表示" else "グリッド表示", Icons.Default.GridView) {
                openSelection("デフォルト録画一覧表示", listOf(
                    "LIST" to "リスト表示",
                    "GRID" to "グリッド表示"
                ), defaultRecordListView) { settingsViewModel.updateDefaultRecordListView(it) }
            }
        }

        item {
            PhoneSettingItem("時刻表示形式",
                if (timeFormat == "12H") "12時間表示" else "24時間表示", Icons.Default.Schedule) {
                openSelection("時刻表示形式",
                    listOf("24H" to "24時間表示", "12H" to "12時間表示"), timeFormat) {
                    settingsViewModel.updateTimeFormat(it)
                }
            }
        }

        item {
            PhoneSettingToggleItem("サブチャンネルを非表示にする", Icons.Default.FilterListOff, hideSubChannels) {
                settingsViewModel.toggleHideSubChannels()
            }
        }
        // ══ コメント設定 ══════════════════════════════════════
        item { PhoneSettingSectionHeader("コメント設定", Icons.Default.Tv) }

        item { PhoneSettingSectionSubHeader("実況表示") }

        item {
            PhoneSettingItem("デフォルト表示",
                if (commentDefaultDisplay == "ON") "表示" else "非表示", Icons.Default.Visibility) {
                openSelection("コメント デフォルト表示",
                    listOf("ON" to "表示", "OFF" to "非表示"), commentDefaultDisplay) {
                    settingsViewModel.updateCommentDefaultDisplay(it)
                }
            }
        }

        item {
            PhoneSettingItem("コメント速度", commentSpeed, Icons.Default.Speed) {
                openInput("コメント速度", commentSpeed) {
                    settingsViewModel.updateCommentSpeed(it)
                }
            }
        }

        item {
            PhoneSettingItem("コメントフォントサイズ", commentFontSize, Icons.Default.TextFormat) {
                openInput("コメントフォントサイズ", commentFontSize) {
                    settingsViewModel.updateCommentFontSize(it)
                }
            }
        }

        item {
            PhoneSettingItem("コメント不透明度", commentOpacity, Icons.Default.Opacity) {
                openInput("コメント不透明度 (0.0〜1.0)", commentOpacity) {
                    settingsViewModel.updateCommentOpacity(it)
                }
            }
        }

        item {
            PhoneSettingItem("コメント最大行数",
                if (commentMaxLines == "0") "無制限" else "$commentMaxLines 行", Icons.Default.VerticalAlignTop) {
                openInput("コメント最大行数 (0=無制限)", commentMaxLines) {
                    settingsViewModel.updateCommentMaxLines(it)
                }
            }
        }

        // ══ アドオン・ラボ ════════════════════════════════════
        item { PhoneSettingSectionHeader("アドオン・ラボ", Icons.Default.Science) }

        item { PhoneSettingSectionSubHeader("プロ野球モード") }

        item {
            val teamsLabel = if (favoriteBaseballTeams.isEmpty()) "未設定"
                else "${favoriteBaseballTeams.size}球団選択中"
            PhoneSettingItem("フォロー球団の設定", teamsLabel, Icons.Default.SportsBaseball) {
                openMultiSelection(
                    "フォロー球団の選択",
                    BASEBALL_TEAM_OPTIONS,
                    favoriteBaseballTeams
                ) { settingsViewModel.updateFavoriteBaseballTeams(it) }
            }
        }

        // ══ アプリ情報 ════════════════════════════════════════
        item { PhoneSettingSectionHeader("アプリ情報", Icons.Default.Info) }

        item {
            PhoneSettingItem(
                "バージョン情報・オープンソースライセンス",
                "Komorebi ${com.beeregg2001.komorebi.BuildConfig.VERSION_NAME}",
                Icons.Default.Info
            ) { showAppInfo = true }
        }
    }

    // ── Dialogs ────────────────────────────────────────────────

    if (showInputDialog) {
        var text by remember(inputDialogValue) { mutableStateOf(inputDialogValue) }
        AlertDialog(
            onDismissRequest = { showInputDialog = false },
            title = { Text(inputDialogTitle) },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = { inputDialogAction?.invoke(text); showInputDialog = false }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInputDialog = false }) { Text("キャンセル") }
            }
        )
    }

    if (showSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showSelectionDialog = false },
            title = { Text(selectionDialogTitle) },
            text = {
                Column {
                    selectionDialogOptions.forEach { (value, label) ->
                        Row(modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectionDialogCurrent == value,
                                onClick = {
                                    selectionDialogAction?.invoke(value)
                                    showSelectionDialog = false
                                })
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSelectionDialog = false }) { Text("キャンセル") }
            }
        )
    }

    if (showMultiSelectionDialog) {
        var tempSelection by remember(multiSelectionDialogCurrent) {
            mutableStateOf(multiSelectionDialogCurrent)
        }
        AlertDialog(
            onDismissRequest = { showMultiSelectionDialog = false },
            title = { Text(multiSelectionDialogTitle) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    multiSelectionDialogOptions.forEach { (value, label) ->
                        Row(modifier = Modifier.fillMaxWidth()
                            .clickable {
                                tempSelection = if (tempSelection.contains(value))
                                    tempSelection - value else tempSelection + value
                            },
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = tempSelection.contains(value), onCheckedChange = {
                                tempSelection = if (it) tempSelection + value else tempSelection - value
                            })
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    multiSelectionDialogAction?.invoke(tempSelection)
                    showMultiSelectionDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showMultiSelectionDialog = false }) { Text("キャンセル") }
            }
        )
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(confirmTitle) },
            text = { Text(confirmMessage) },
            confirmButton = {
                TextButton(onClick = { confirmAction?.invoke(); showConfirmDialog = false }) {
                    Text("実行", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("キャンセル") }
            }
        )
    }

    if (showSmbDialog) {
        PhoneSmbServerDialog(
            server = editingSmbServer,
            onDismiss = { showSmbDialog = false },
            onSave = { server -> settingsViewModel.saveSmbServer(server); showSmbDialog = false }
        )
    }

    if (showAddBatchDialog) {
        PhoneAddBatchDialog(
            onDismiss = { showAddBatchDialog = false },
            onSave = { name, path ->
                settingsViewModel.addPostRecordingBatch(name, path)
                showAddBatchDialog = false
            }
        )
    }
}

// ─── Composables ──────────────────────────────────────────────────────────────

@Composable
private fun PhoneSettingSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    // ★ TVのサイドバーでカテゴリが選択された時と同じ「反転」配色
    // （背景=primary、文字/アイコン=onPrimary）にして、グループの区切りを
    // はっきりさせる。
    Surface(color = MaterialTheme.colorScheme.primary) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun PhoneSettingSectionSubHeader(title: String) {
    // ★ サブヘッダーはヘッダーより控えめに — 背景なし、薄い色の文字のみ
    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun PhoneSettingItem(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (() -> Unit)?
) {
    ListItem(
        headlineContent = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
        supportingContent = { Text(value, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = if (onClick != null) {
            { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun PhoneSettingToggleItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onToggle: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = { onToggle() }) },
        modifier = Modifier.clickable { onToggle() }
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun PhoneSettingActionItem(
    title: String, subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, color = MaterialTheme.colorScheme.primary) },
        supportingContent = if (subtitle.isNotEmpty()) {
            { Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ─── SMB Server Dialog ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneSmbServerDialog(
    server: SmbServer?,
    onDismiss: () -> Unit,
    onSave: (SmbServer) -> Unit
) {
    var name by remember(server) { mutableStateOf(server?.name ?: "") }
    var ip by remember(server) { mutableStateOf(server?.ip ?: "") }
    var port by remember(server) { mutableStateOf(server?.port ?: "445") }
    var user by remember(server) { mutableStateOf(server?.user ?: "") }
    var password by remember(server) { mutableStateOf(server?.password ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (server == null) "SMBサーバーを追加" else "SMBサーバーを編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("サーバー名") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = ip, onValueChange = { ip = it },
                    label = { Text("IPアドレス") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = port, onValueChange = { port = it },
                    label = { Text("ポート") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = user, onValueChange = { user = it },
                    label = { Text("ユーザー名") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it },
                    label = { Text("パスワード") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(SmbServer(id = server?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name, ip = ip, port = port, user = user, password = password))
                },
                enabled = name.isNotBlank() && ip.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

// ─── Add Batch Dialog ─────────────────────────────────────────────────────────

@Composable
private fun PhoneAddBatchDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("バッチの登録") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("バッチ名") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = path, onValueChange = { path = it },
                    label = { Text("バッチファイルパス") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, path) },
                enabled = name.isNotBlank() && path.isNotBlank()) { Text("登録") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}
