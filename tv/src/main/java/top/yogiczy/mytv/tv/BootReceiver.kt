package top.yogiczy.mytv.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import top.yogiczy.mytv.core.data.utils.SP
import top.yogiczy.mytv.tv.ui.utils.Configs

/**
 * 开机自启动监听
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action ||
            ACTION_QUICKBOOT_POWERON == intent.action
        ) {
            val sp = SP.getInstance(context)
            val bootLaunch = sp.getBoolean(Configs.KEY.APP_BOOT_LAUNCH.name, false)

            if (bootLaunch) {
                // 不直接拉主界面：Android 10+ 禁止后台直接 startActivity，
                // 开机广播到达时应用在后台，请求会被系统拦掉；改走前台服务
                // 拉起，BOOT_COMPLETED 广播本身就是起前台服务的放行情形
                val serviceIntent = Intent(context, BootLaunchService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }

    companion object {
        // 部分厂商开机不发 BOOT_COMPLETED、改发 QUICKBOOT_POWERON
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
