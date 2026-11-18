package top.yogiczy.mytv.tv.ui.screen.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelFavorite
import top.yogiczy.mytv.core.data.entities.channel.ChannelFavoriteList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.chanelGroup
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.tv.BuildConfig
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.PopupContent
import top.yogiczy.mytv.tv.ui.BackKeyPressCycleTracker
import top.yogiczy.mytv.tv.ui.rememberDoubleBackPressedExitState
import top.yogiczy.mytv.tv.ui.screen.Screens
import top.yogiczy.mytv.tv.ui.screen.about.AboutScreen
import top.yogiczy.mytv.tv.ui.screen.agreement.AgreementScreen
import top.yogiczy.mytv.tv.ui.screen.channels.ChannelsScreen
import top.yogiczy.mytv.tv.ui.screen.dashboard.DashboardScreen
import top.yogiczy.mytv.tv.ui.screen.favorites.FavoritesScreen
import top.yogiczy.mytv.tv.ui.screen.loading.LoadingScreen
import top.yogiczy.mytv.tv.ui.screen.loading.LoadingBar
import top.yogiczy.mytv.tv.ui.screen.multiview.MultiViewScreen
import top.yogiczy.mytv.tv.ui.screen.push.PushScreen
import top.yogiczy.mytv.tv.ui.screen.search.SearchScreen
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsScreen
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsSubCategories
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.screen.update.UpdateScreen
import top.yogiczy.mytv.tv.ui.screen.update.UpdateViewModel
import top.yogiczy.mytv.tv.ui.screen.update.updateVM
import top.yogiczy.mytv.tv.ui.utils.navigateSingleTop

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = settingsVM,
    updateViewModel: UpdateViewModel = updateVM,
    mainViewModel: MainViewModel = mainVM,
    onBackPressed: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    
    mainViewModel.needRefresh = { settingsViewModel.refresh() }

    val channelGroupListProvider = {
        (uiState as? MainUiState.Ready)?.channelGroupList ?: ChannelGroupList()
    }
    val filteredChannelGroupListProvider = {
        (uiState as? MainUiState.Ready)?.filteredChannelGroupList ?: ChannelGroupList()
    }
    val epgListProvider = { (uiState as? MainUiState.Ready)?.epgList ?: EpgList() }
    val favoriteChannelListProvider = {
        ChannelList(settingsViewModel.iptvChannelFavoriteList.map { it.channel.copy(index = -1) })
    }

    val navController = rememberNavController()

    // 返回键按压周期守卫：一次物理按键只算一次返回（天光 v2.2.32 回镜）。
    // 挂在 NavHost 根 modifier 上，预览阶段先于子树的所有返回处理（直播页
    // Modifier.backHandler、各屏 BackHandler）与 Activity 层分发：按下只记
    // 周期开始、不消费；无新鲜按下的抬起直接消费掉、不漏下去；有新鲜按下的
    // 抬起才放行。调用处传进来的 modifier 参数贴在链头（3017 轮核过它之
    // 前是死参数、没贴到任何节点上），fillMaxSize 与守卫接在后面。
    val backKeyPressCycleTracker = remember { BackKeyPressCycleTracker() }

    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if(!isLoading)
            return@LaunchedEffect
        if (uiState is MainUiState.Loading)
            return@LaunchedEffect
        if (uiState is MainUiState.Ready)
            isLoading = false
        if (uiState is MainUiState.Error) {
            kotlinx.coroutines.delay(2000) // 延迟2秒
            isLoading = false
        }
    }

    fun onChannelSelected(channel: Channel) {
        settingsViewModel.iptvChannelLastPlay = channel
        navController.navigateSingleTop(Screens.Live())
    }

    fun onChannelFavoriteToggle(channel: Channel) {
        if (!settingsViewModel.iptvChannelFavoriteEnable) return
        if (settingsViewModel.iptvChannelFavoriteList.any { it.channel == channel }) {
            settingsViewModel.iptvChannelFavoriteList =
                ChannelFavoriteList(settingsViewModel.iptvChannelFavoriteList.filter {
                    it.channel != channel
                })

            Snackbar.show("取消收藏：${channel.name}")
        } else {
            val favoriteChannel = ChannelFavorite(
                channel = channel,
                iptvSourceName = settingsViewModel.iptvSourceCurrent.name,
                groupName = filteredChannelGroupListProvider().chanelGroup(channel)?.name ?: "其他",
            )

            settingsViewModel.iptvChannelFavoriteList =
                ChannelFavoriteList(settingsViewModel.iptvChannelFavoriteList + favoriteChannel)

            Snackbar.show("已收藏：${channel.name}")
        }
    }

    fun onChannelFavoriteClear() {
        if (!settingsViewModel.iptvChannelFavoriteEnable) return

        settingsViewModel.iptvChannelFavoriteList = ChannelFavoriteList()
        Snackbar.show("已清空所有收藏")
    }

    fun checkUpdate(quiet: Boolean = true) {
        coroutineScope.launch {
            if (!quiet) Snackbar.show("正在检查更新...", leadingLoading = true, duration = 5000)

            delay(3000)
            updateViewModel.checkUpdate(
                BuildConfig.VERSION_NAME,
                settingsViewModel.updateChannel,
                releaseTag = BuildConfig.RELEASE_TAG,
            )

            if (!quiet) {
                when {
                    updateViewModel.checkFailed -> Snackbar.show("检查更新失败")
                    // 手动点“检测更新”查到新版：直接进更新页（有“立即更新”按钮），
                    // 别只弹一条提示——只弹提示时用户永远找不到下载入口。
                    updateViewModel.isUpdateAvailable ->
                        navController.navigateSingleTop(Screens.Update())
                    // 另一次检查还在跑：别把“没查完”说成“已是最新”，等它出结果（877 轮 P3-2）。
                    updateViewModel.isChecking -> Snackbar.show("正在检查更新...")
                    else -> Snackbar.show("当前已是最新版本")
                }
            }

            if (!shouldRemindUpdate(
                    updateViewModel.isUpdateAvailable,
                    updateViewModel.latestRelease.version,
                    settingsViewModel.appLastLatestVersion,
                )
            ) return@launch

            settingsViewModel.appLastLatestVersion = updateViewModel.latestRelease.version
            if (settingsViewModel.updateForceRemind) {
                navController.navigateSingleTop(Screens.Update())
            } else {
                if (quiet) Snackbar.show("发现新版本: v${updateViewModel.latestRelease.version}")
            }
        }
    }

    fun reload() {
        settingsViewModel.refresh()
        mainViewModel.init()
        navController.navigateUp()
        navController.navigateSingleTop(Screens.Loading())
    }
    NavHost(
        modifier = modifier.fillMaxSize().onPreviewKeyEvent {
            when {
                it.key == Key.Back && it.type == KeyEventType.KeyDown -> {
                    // 只记周期开始，不消费按下事件：按下本身不触发任何动作，
                    // 抬起时才看有没有跟在新鲜按下后面
                    backKeyPressCycleTracker.onKeyDown(it.nativeKeyEvent.repeatCount)
                    false
                }

                it.key == Key.Back && it.type == KeyEventType.KeyUp -> {
                    // 没有新鲜按下就来的抬起是重复投递或杂散事件：消费掉
                    // 不漏下去，也不算一次返回——一次物理按键拆出两起
                    // 间隔 ≥300ms 的抬起也不能再直接退出（天光 2.2.31 自退）
                    if (!backKeyPressCycleTracker.onKeyUp()) return@onPreviewKeyEvent true
                    false
                }

                else -> {
                    false
                }
            }
        },
        navController = navController,
        startDestination = if (settingsViewModel.appAgreementAgreed) Screens.Loading() else Screens.Agreement(),
        builder = {
            composable(Screens.Agreement()) {
                AgreementScreen(
                    onAgree = {
                        settingsViewModel.appAgreementAgreed = true
                        navController.navigateSingleTop(Screens.Loading())
                    },
                    onDisagree = onBackPressed,
                    onTouchTested = { settingsViewModel.uiFocusOptimize = false },
                )
            }

            composable(Screens.Loading()) {
                LoadingScreen(
                    onShowDialog = {
                        if (uiState is MainUiState.Loading)
                            isLoading = true
                    },
                    toDashboardScreen = {
                        navController.navigateUp()
                        navController.navigateSingleTop(settingsViewModel.appStartupScreen)
                        checkUpdate()
                    },
                    onBackPressed = onBackPressed,
                )
            }

            composable(Screens.Dashboard()) {
                DashboardScreen(
                    currentIptvSourceProvider = { settingsViewModel.iptvSourceCurrent },
                    channelFavoriteListProvider = { settingsViewModel.iptvChannelFavoriteList },
                    onChannelSelected = { onChannelSelected(it) },
                    epgListProvider = epgListProvider,
                    toLiveScreen = { navController.navigateSingleTop(Screens.Live()) },
                    toChannelsScreen = { navController.navigateSingleTop(Screens.Channels()) },
                    toFavoritesScreen = { navController.navigateSingleTop(Screens.Favorites()) },
                    toSearchScreen = { navController.navigateSingleTop(Screens.Search()) },
                    toMultiViewScreen = { navController.navigateSingleTop(Screens.MultiView()) },
                    toPushScreen = { navController.navigateSingleTop(Screens.Push()) },
                    toSettingsScreen = { navController.navigateSingleTop(Screens.Settings()) },
                    toAboutScreen = { navController.navigateSingleTop(Screens.About()) },
                    toSettingsIptvSourceScreen = {
                        navController.navigateSingleTop(
                            Screens.Settings.withArgs(SettingsSubCategories.IPTV_SOURCE)
                        )
                    },
                    onReload = { reload() },
                    onBackPressed = onBackPressed,
                )
            }

            composable(Screens.Live()) {
                val doubleBackPressedExitState = rememberDoubleBackPressedExitState()

                key(settingsViewModel.videoPlayerCore, settingsViewModel.videoPlayerForceSoftDecode) {
                    top.yogiczy.mytv.tv.ui.screensold.main.components.MainContent(
                        isLoadingProvider = { isLoading },
                        filteredChannelGroupListProvider = filteredChannelGroupListProvider,
                        favoriteChannelListProvider = favoriteChannelListProvider,
                        epgListProvider = epgListProvider,
                        onChannelFavoriteToggle = { onChannelFavoriteToggle(it) },
                        toSettingsScreen = {
                            if (it != null) {
                                navController.navigateSingleTop(Screens.Settings.withArgs(it))
                            } else {
                                navController.navigateSingleTop(Screens.Settings())
                            }
                        },
                        toDashboardScreen = {
                            navController.navigateSingleTop(Screens.Dashboard())
                        },
                        onReload = { reload() },
                        onBackPressed = {
                            if (settingsViewModel.appStartupScreen == Screens.Live.name) {
                                onBackPressed()
                            } else {
                                if (doubleBackPressedExitState.allowExit) {
                                    navController.navigateUp()
                                } else {
                                    doubleBackPressedExitState.backPress()
                                    Snackbar.show("再按一次退出直播")
                                }
                            }
                        },
                    )
                }
            }

            composable(Screens.Channels()) {
                ChannelsScreen(
                    channelGroupListProvider = filteredChannelGroupListProvider,
                    onChannelSelected = { onChannelSelected(it) },
                    onChannelFavoriteToggle = { onChannelFavoriteToggle(it) },
                    epgListProvider = epgListProvider,
                    onBackPressed = { navController.navigateUp() },
                )
            }

            composable(Screens.Favorites()) {
                FavoritesScreen(
                    channelFavoriteListProvider = { settingsViewModel.iptvChannelFavoriteList },
                    onChannelSelected = { onChannelSelected(it) },
                    onChannelFavoriteToggle = { onChannelFavoriteToggle(it) },
                    onChannelFavoriteClear = { onChannelFavoriteClear() },
                    epgListProvider = epgListProvider,
                    onBackPressed = { navController.navigateUp() },
                )
            }

            composable(Screens.Search()) {
                SearchScreen(
                    channelGroupListProvider = filteredChannelGroupListProvider,
                    onChannelSelected = { onChannelSelected(it) },
                    onChannelFavoriteToggle = { onChannelFavoriteToggle(it) },
                    epgListProvider = epgListProvider,
                    onBackPressed = { navController.navigateUp() },
                )
            }

            composable(Screens.Push()) {
                PushScreen(
                    onBackPressed = { navController.navigateUp() },
                )
            }

            composable(
                Screens.Settings(),
                arguments = listOf(
                    navArgument(SettingsScreen.START_DESTINATION) { type = NavType.StringType }
                ),
            ) { backStackEntry ->
                SettingsScreen(
                    startDestinationProvider = {
                        val startDestination =
                            backStackEntry.arguments?.getString(SettingsScreen.START_DESTINATION)

                        if (startDestination == "{${SettingsScreen.START_DESTINATION}}") null
                        else startDestination
                    },
                    channelGroupListProvider = channelGroupListProvider,
                    onCheckUpdate = { checkUpdate(false) },
                    onReload = { reload() },
                    onBackPressed = { navController.navigateUp() },
                )
            }

            composable(Screens.About()) {
                AboutScreen(
                    latestVersionProvider = { updateViewModel.latestRelease.version },
                    checkFailedProvider = { updateViewModel.checkFailed },
                    toUpdateScreen = { navController.navigateSingleTop(Screens.Update()) },
                    onBackPressed = { navController.navigateUp() },
                )
            }

            composable(Screens.Update()) {
                UpdateScreen(
                    onBackPressed = { navController.navigateUp() },
                )
            }

            composable(Screens.MultiView()) {
                MultiViewScreen(
                    channelGroupListProvider = filteredChannelGroupListProvider,
                    epgListProvider = epgListProvider,
                    onBackPressed = { navController.navigateUp() },
                )
            }
        },
    )
    PopupContent(
        visibleProvider = { isLoading },
        onDismissRequest = {},
    ) {
        LoadingBar(
            mainUiState = uiState,
            visibleProvider = { isLoading },
        )
    }
}

/**
 * 启动 3 秒后查到新版时要不要提醒：有更新、且这个版本号不是上次已经提醒
 * 过的，才提醒——否则同一版每次启动都弹一次。`appLastLatestVersion`
 * 为空是从没提醒过。只抽判断本身，3 秒延迟与弹框/提示仍在界面里。
 */
internal fun shouldRemindUpdate(
    isUpdateAvailable: Boolean,
    latestVersion: String,
    lastLatestVersion: String,
): Boolean = isUpdateAvailable && latestVersion != lastLatestVersion
