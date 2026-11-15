package com.github.mytv.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.tv.ui.screen.search.VoiceSearchBus
import top.yogiczy.mytv.tv.ui.screen.search.pickVoiceText

/**
 * 厂商语音服务（Peasun 小豚）回调接收器。
 *
 * 遥控器语音键说话后，厂商服务把识别出的文字以广播发过来
 * （com.peasun.aispeech.action.live）；收到后经 VoiceSearchBus
 * 递给搜索页填进搜索框。
 *
 * 类名与 3.x 保持一致（com.github.mytv.android.SharJeckOpenReceiver）：
 * 厂商服务可能按类名显式寻址，改名就收不到——本 fork 的 applicationId
 * 本来就是 com.github.mytv.android，所以这个类名可以直接用。
 * （本模块其余代码在 top.yogiczy.mytv.tv 包下，这个类是例外。）
 *
 * 注意：厂商侧的握手协议没有公开文档，这里只按 3.x 里出现过的
 * action 名接线；真机上以荣耀电视实测为准。
 */
class SharJeckOpenReceiver : BroadcastReceiver() {
    private val log = Logger.create("SharJeckOpenReceiver")

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_LIVE -> {
                val text = pickVoiceText(intent.toExtrasMap())
                if (text.isNullOrBlank()) {
                    log.i("厂商语音广播没有带识别文本")
                    return
                }
                log.i("收到厂商语音识别结果: $text")
                VoiceSearchBus.emit(text)
            }

            ACTION_REGISTER_REQUIRE -> {
                // 厂商服务要求重新注册：回发注册广播（尽力而为）
                context.sendBroadcast(Intent(ACTION_REGISTER).apply {
                    putExtra("package", context.packageName)
                })
            }
        }
    }

    private fun Intent.toExtrasMap(): Map<String, Any?> {
        val bundle = extras ?: return emptyMap()
        return bundle.keySet().associateWith { bundle.get(it) }
    }

    companion object {
        const val ACTION_LIVE = "com.peasun.aispeech.action.live"
        const val ACTION_REGISTER = "com.peasun.aispeech.action.app.register"
        const val ACTION_REGISTER_REQUIRE = "com.peasun.aispeech.action.app.register.require"
    }
}
