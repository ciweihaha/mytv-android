package top.yogiczy.mytv.tv

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * 开机拉起服务：收到 BOOT_COMPLETED 后由 BootReceiver 起这个前台服务，
 * 再由服务拉起主界面。Android 10+ 禁止后台直接 startActivity，带前台
 * 服务拉起是系统放行的路子（BOOT_COMPLETED 广播本身也是起前台服务
 * 的放行情形）；服务只在开机那一刻跑一下，拉起后立即自停，不常驻。
 */
class BootLaunchService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "boot_launch"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "开机自启",
                NotificationManager.IMPORTANCE_LOW,
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("正在启动 TV Live")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

        // 拉起请求已发出，但开机那一刻系统正忙、启动可能被延迟处理；
        // 服务别立刻停，留 3 秒让系统在前台服务还活着的情况下把启动
        // 落定，立刻停服务的话，启动可能还没来得及受理
        Handler(Looper.getMainLooper()).postDelayed({
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }, STOP_DELAY_MILLIS)
        return START_NOT_STICKY
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val STOP_DELAY_MILLIS = 3_000L
    }
}
