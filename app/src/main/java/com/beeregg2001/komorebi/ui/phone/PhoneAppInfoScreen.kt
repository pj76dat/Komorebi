package com.beeregg2001.komorebi.ui.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.beeregg2001.komorebi.ui.setting.OssLibrary
import com.beeregg2001.komorebi.ui.setting.ossLibraries

/**
 * TVの AppInfoContent + OpenSourceLicensesScreen に相当するPhone版
 * (ui/setting/SettingContents.kt, ui/setting/OpenSourceLicensesScreen.kt)。
 * 同じ `ossLibraries` データ（ライセンス文など）を複製せずそのまま再利用し、
 * TVの androidx.tv.material3 / D-pad前提の画面ではなく、Phoneネイティブな
 * リスト＋詳細画面のUIを組み立てている。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneAppInfoScreen(onBack: () -> Unit) {
    var selectedLibrary by remember { mutableStateOf<OssLibrary?>(null) }

    if (selectedLibrary != null) {
        PhoneOssLicenseDetailScreen(
            library = selectedLibrary!!,
            onBack = { selectedLibrary = null }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("アプリ情報") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "戻る")
                }
            }
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Komorebi", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Version ${com.beeregg2001.komorebi.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider()

        Text(
            "オープンソースライセンス",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)) {
            items(ossLibraries, key = { it.name }) { library ->
                ListItem(
                    headlineContent = { Text(library.name) },
                    supportingContent = { Text("${library.author} • ${library.licenseName}") },
                    leadingContent = {
                        Icon(Icons.Default.Info, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier = Modifier.clickable { selectedLibrary = library }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneOssLicenseDetailScreen(library: OssLibrary, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(library.name) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "戻る")
                }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(library.licenseName, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text(library.author, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                library.licenseText.trimIndent(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}
