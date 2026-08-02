package com.beeregg2001.komorebi.common

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Androidスマートフォン向けのタップ操作を追加しつつ、
 * AndroidTVのD-Pad（OK/Enterボタン）操作を維持するModifier拡張。
 *
 * 使い方: タッチ操作に対応させたいUI要素に .tvPhoneClickable { yourAction() } を追加する。
 *
 * - スマートフォン: 要素をタップするとonClick()が実行される
 * - AndroidTV: D-Padの中央ボタンまたはEnterを押すとonClick()が実行される
 */
fun Modifier.tvPhoneClickable(
    onClick: () -> Unit
): Modifier = this
    .pointerInput(Unit) {
        detectTapGestures(onTap = { onClick() })
    }
    .onPreviewKeyEvent { event ->
        if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
            if (event.type == KeyEventType.KeyUp) onClick()
            true
        } else false
    }