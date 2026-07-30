package com.beeregg2001.komorebi.common

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

fun Modifier.tvPhoneClickable(
    onClick: () -> Unit
): Modifier = this
    .clickable { onClick() }
    .onPreviewKeyEvent { event ->
        if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
            if (event.type == KeyEventType.KeyUp) onClick()
            true
        } else false
    }