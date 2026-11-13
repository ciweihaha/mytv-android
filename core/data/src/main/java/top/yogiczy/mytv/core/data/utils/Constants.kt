package top.yogiczy.mytv.core.data.utils

import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSourceList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList

/**
 * 常量
 */
object Constants {
    /**
     * 应用 标题
     */
    const val APP_TITLE = "电视直播"

    /**
     * 应用 代码仓库（本 fork；上游见 ORIGIN_APP_REPO）
     */
    const val ORIGIN_APP_REPO = "https://github.com/yaoxieyoulei/mytv-android"
    const val APP_REPO = "https://github.com/ciweihaha/mytv-android"

    /**
     * 交流群 telegram
     */
    const val GROUP_TELEGRAM = "https://t.me/mytv_android_channel"

    /**
     * 订阅源
     */
    val IPTV_SOURCE_LIST = IptvSourceList(
        listOf(
            IptvSource(
                name = "默认订阅源 iptv-api",
                url = "https://gitee.com/mytv-android/iptv-api/raw/master/output/result.m3u",
            ),
            IptvSource(
                name = "WebView订阅源",
                url = "https://gitee.com/mytv-android/iptv-api/raw/master/output/webview.m3u",
            )
        )
    )

    /**
     * 订阅源缓存时间（毫秒）
     */
    const val IPTV_SOURCE_CACHE_TIME = 1000 * 60 * 60L // 1小时

    /**
     * 节目单来源
     */
    val EPG_SOURCE_LIST = EpgSourceList(
        listOf(
            EpgSource(
                name = "默认节目单 综合",
                url = "https://gitee.com/mytv-android/myepg/raw/master/output/epg.gz",
            ),
        )
    )

    /**
     * 节目单刷新时间阈值（小时）
     */
    const val EPG_REFRESH_TIME_THRESHOLD = 2 // 不到2点不刷新

    /**
     * 网页源央视频Cookie
     */
    const val HYBRID_YANGSHIPIN_COOKIE = ""
    
    /**
     * 频道图标提供
     *
     * {name} 频道名称
     *
     * {name|lowercase} 转成小写
     *
     * {name|uppercase} 转成大写
     *
     */
    const val CHANNEL_LOGO_PROVIDER = "https://gitee.com/mytv-android/myTVlogo/raw/main/img/{name|uppercase}.png"

    /**
     * GitHub加速代理地址
     */
    const val GITHUB_PROXY = "https://ghp.ci/"

    /**
     * Git最新版本信息
     *
     * 查本 fork 自己的发版，不再查上游 Gitee：上游稳定版已是 V2.2.0.219，
     * 按版本号比永远大于本 fork 的 1.1.1.N，启动检查每次都报“发现新版本”，
     * 而上游包是作者签名、与本 fork 每轮构建现做的一次性签名对不上，
     * 装上去会报签名不一致装不上。本 fork 目前没有发版，查不到就走
     * “检查更新失败”，不提示、不下载；以后本 fork 发了版再按
     * GithubGitReleaseParser 的规则（附件名含 original＋all）走。
     */
    val GIT_RELEASE_LATEST_URL = mapOf(
        "stable" to "https://api.github.com/repos/ciweihaha/mytv-android/releases/latest",
        "beta" to "https://api.github.com/repos/ciweihaha/mytv-android/releases/latest",
        "dev" to "https://api.github.com/repos/ciweihaha/mytv-android/releases/latest",
    )

    /**
     * 网络请求重试次数
     */
    const val NETWORK_RETRY_COUNT = 10L

    /**
     * 网络请求重试间隔时间（毫秒）
     */
    const val NETWORK_RETRY_INTERVAL = 1000L

    /**
     * 播放器 userAgent
     */
    const val VIDEO_PLAYER_USER_AGENT = "okhttp"

    /**
     * 播放器加载超时
     */
    const val VIDEO_PLAYER_LOAD_TIMEOUT = 1000L * 15 // 15秒

    /**
     * 播放器加载缓存时间
     */
    const val VIDEO_PLAYER_BUFFER_TIME = 0L

    /**
     * 日志历史最大保留条数
     */
    const val LOG_HISTORY_MAX_SIZE = 100

    /**
     * 界面 临时频道界面显示时间
     */
    const val UI_TEMP_CHANNEL_SCREEN_SHOW_DURATION = 2000L // 2秒

    /**
     * 界面 超时未操作自动关闭界面
     */
    const val UI_SCREEN_AUTO_CLOSE_DELAY = 1000L * 15 // 15秒

    /**
     * 界面 时间显示前后范围
     */
    const val UI_TIME_SCREEN_SHOW_DURATION = 1000L * 30 // 前后30秒

    const val DEFAULT_LOCAL_SYNC_FILE_PATH =  "file:///storage/emulated/0/Download/"
}