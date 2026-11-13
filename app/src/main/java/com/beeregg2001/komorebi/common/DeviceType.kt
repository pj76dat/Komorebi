package com.beeregg2001.komorebi.common

import android.content.Context
import android.content.res.Configuration

/**
 * Android TVで実行されているか、それともスマホ/タブレットで実行されているかを
 * 判定するユーティリティ。起動時にどちらのUIへルーティングするかの判定に使う。
 */
object DeviceType {
    fun isTv(context: Context): Boolean {
        val uiModeManager =
            context.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    fun isPhone(context: Context): Boolean = !isTv(context)
}
