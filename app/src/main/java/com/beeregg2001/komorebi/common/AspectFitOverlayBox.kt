package com.beeregg2001.komorebi.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * [content] を、ExoPlayerのAspectRatioFrameLayoutがRESIZE_MODE_FITの時に計算する
 * のと同じ範囲——つまりレターボックス/ピラーボックスの黒帯を除いた、実際に
 * 見えている映像そのものの範囲——に配置する。コンテナ全体ではなく。
 *
 * これが必要な理由: ARIB字幕描画ライブラリ（subtitle_renderer.html /
 * aribb24.js）は forceFullWindow: true で設定されており、レターボックスの
 * 存在を意識せず、与えられたコンテナ全体に座標系を引き伸ばしてしまう。
 * TVではこれは問題にならない（RESIZE_MODE_FILLを使っており、レターボックスが
 * 無く、コンテナ＝見えている映像そのものになるため）。スマホでは、縦長画面で
 * 映像が切れたり歪んだりしないよう、両プレイヤーとも RESIZE_MODE_FIT を強制
 * しているため、コンテナが実際の映像より縦長・横長になることが多い——この
 * ラッパーが無いと、字幕の描画キャンバスがレターボックスの黒帯部分にまで
 * 引き伸ばされてしまい、横方向の位置がずれる（字幕が左寄せではなく中央寄りに
 * 見えたり、右端が切れたりする）。
 *
 * [crop] が true（RESIZE_MODE_FILL——映像が既にレターボックス無しでコンテナ
 * いっぱいに表示されている）の時、または [videoAspect] がまだ分かっていない
 * （0以下の）時は、単にBox全体を埋める——これまでの挙動と完全に同じ。
 *
 * [videoAspect] は呼び出し元（ライブ・録画それぞれのプレイヤー）が自分で
 * 計算した値（必要に応じて pixelWidthHeightRatio も加味したもの）を渡して
 * もらう形にしている。ライブとVODのプレイヤーでそれぞれ計算方法が微妙に
 * 異なるため。
 */
@Composable
fun AspectFitOverlayBox(
    videoAspect: Float,
    crop: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val fitModifier = remember(videoAspect, crop, maxWidth, maxHeight) {
            if (!crop && videoAspect > 0f && maxWidth > 0.dp && maxHeight > 0.dp) {
                val containerAspect = maxWidth.value / maxHeight.value
                if (videoAspect > containerAspect) {
                    Modifier
                        .width(maxWidth)
                        .height(maxWidth / videoAspect)
                        .align(Alignment.Center)
                } else {
                    Modifier
                        .height(maxHeight)
                        .width(maxHeight * videoAspect)
                        .align(Alignment.Center)
                }
            } else {
                Modifier.fillMaxSize()
            }
        }
        Box(modifier = fitModifier) {
            content()
        }
    }
}
