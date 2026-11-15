package top.yogiczy.mytv.tv.ui.screen.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import java.util.Locale

/**
 * 厂商语音服务（Peasun 小豚）推过来的识别结果总线：遥控器语音键说话后，
 * 厂商服务以广播把文字发给 SharJeckOpenReceiver，再经这里递给搜索页。
 * 用 SharedFlow（不回放）：只在广播到达那一刻发一次，搜索页重组时不重复填。
 */
object VoiceSearchBus {
    private val _voiceText = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val voiceText: SharedFlow<String> = _voiceText.asSharedFlow()

    fun emit(text: String) {
        _voiceText.tryEmit(text)
    }
}

/**
 * 系统语音识别启动器：点搜索页的麦克风键后拉起系统识别器，
 * 识别出的第一条结果回调给 onResult（填进搜索框）。
 * 设备上没有识别器时直接提示，不抛 ActivityNotFoundException。
 * 不用麦克风录音：识别器是系统自带的（电视厂商语音服务或谷歌识别），
 * 本应用不申请 RECORD_AUDIO 权限，和 3.x 的语音搜索同一条路。
 */
@Composable
fun rememberVoiceSearchLauncher(onResult: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull { it.isNotBlank() }
        if (text != null) onResult(text)
    }

    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出要搜索的节目")
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            Snackbar.show("当前设备不支持语音搜索", type = SnackbarType.ERROR)
        } else {
            runCatching { launcher.launch(intent) }.onFailure {
                Snackbar.show("无法启动语音搜索", type = SnackbarType.ERROR)
            }
        }
    }
}

/**
 * 从厂商语音广播的 extras 里挑识别文本：厂商服务用的 extra 键没有公开文档，
 * 按 3.x 里出现过的键和常见写法依次试，第一个非空的即结果。
 * 抽成纯函数，方便单测锁住优先级。
 */
internal fun pickVoiceText(extras: Map<String, Any?>): String? {
    // 系统识别器的结果列表优先
    val listKeys = listOf(
        RecognizerIntent.EXTRA_RESULTS,
        "results",
    )
    for (key in listKeys) {
        val list = extras[key] as? List<*> ?: continue
        val text = list.firstOrNull() as? String
        if (!text.isNullOrBlank()) return text
    }
    // 厂商服务常用的文本键
    val stringKeys = listOf("text", "result", "voice_text", "keyword", "content")
    for (key in stringKeys) {
        val text = extras[key] as? String
        if (!text.isNullOrBlank()) return text
    }
    return null
}
