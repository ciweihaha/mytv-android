package top.yogiczy.mytv.tv.ui

/**
 * 返回键按压周期守卫：一次物理按键只算一次返回
 *
 * 天光 v2.2.32（ea6d2683）回镜移植。2.2.31 待观察项：首次启动无故干净自退一次
 *（System.exit(0)，走返回键路径）。天光侧的洞在双按确认只认“两次按压间隔
 * ≥300ms”：一次物理按键若拆出两起抬起事件、间隔 ≥300ms（遥控器重复投递、
 * 杂散返回事件），第二起抬起正好落在 2 秒确认窗里，直接退出。
 *
 * 壳侧同款缺口属实，而且更暴露：[DoubleBackPressedExitState] 每次按压都重记
 * 时间、没有任何去重（见 DoubleBackPressedExitStateTest.kt 顶部注释：“壳线
 * 没有天光的 300 毫秒去重与 [300, 2000) 窗口——每次按压都重记时间，不丢弃
 * 快速重复按压”）。壳的返回回调认的是抬起事件（Modifier.backHandler 只认
 * KeyUp，AppScreen 里是 BackHandler），一次物理按键拆出两起抬起、哪怕只隔
 * 100ms，第一起把 allowExit 置 true、第二起直接走退出——连 300ms 的门槛
 * 都没有。
 *
 * 修法与天光同款：抬起必须跟在一次新鲜按下后面才算一次返回——按下（非重复）
 * 记周期开始，抬起消费掉这个周期；没有按下的抬起（重复投递、杂散事件）直接
 * 丢掉，不管隔了多久。长按的重复按下（repeatCount > 0）不重新记周期，按住
 * 不放不会刷出多次返回。
 *
 * 接线在 MainScreen 的 NavHost 根 modifier 上（onPreviewKeyEvent，预览阶段
 * 先于子树的所有返回处理，也先于 Activity 层 onBackPressedDispatcher）：
 * 按下只记周期开始、不消费；无新鲜按下的抬起直接消费掉、不漏下去；有新鲜
 * 按下的抬起才放行。注意 MainScreen 的 modifier 参数本身没被用上（NavHost
 * 用的是新起的 Modifier.fillMaxSize()），守卫必须挂在 NavHost 的 modifier
 * 上，挂在调用处传进去的 modifier 上等于没挂。
 */
class BackKeyPressCycleTracker internal constructor() {
    /** 自上次被消费的抬起以来，有没有见过一次新鲜按下 */
    private var keyDownSeen = false

    /**
     * 返回键按下：非重复按下记一次按压周期开始；长按的重复按下不重
     * 新记（按住不放只算一次）。返回 true 表示接受为一次新的按下。
     */
    fun onKeyDown(repeatCount: Int = 0): Boolean {
        if (repeatCount != 0) return false
        keyDownSeen = true
        return true
    }

    /**
     * 返回键抬起：只有跟在一次新鲜按下后面才算一次返回按压，返回
     * true；没有按下就来的抬起是重复投递或杂散事件，返回 false。
     * 算过一次后周期清掉，下一次抬起要等新的按下。
     */
    fun onKeyUp(): Boolean {
        if (!keyDownSeen) return false
        keyDownSeen = false
        return true
    }
}
