package top.yogiczy.mytv.tv.ui.screen.loading

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import top.yogiczy.mytv.tv.ui.material.SnackbarUICustom
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import top.yogiczy.mytv.tv.ui.screen.main.MainUiState

@Composable
fun LoadingScreen(
    modifier: Modifier = Modifier,
    onShowDialog: () -> Unit = {},
    toDashboardScreen: () -> Unit = {},
) {
    // 导航/弹窗属于副作用，不能在组合期间直接执行（组合重跑会重复导航）；
    // LaunchedEffect(Unit) 只在进入该目的地时跑一次，行为与原来一致
    LaunchedEffect(Unit) {
        onShowDialog()
        toDashboardScreen()
    }
}

@Composable
fun LoadingBar(
    modifier: Modifier = Modifier,
    mainUiState: MainUiState,
    visibleProvider: () -> Boolean = { true },
) {
    val text = remember(mainUiState) {
        when (mainUiState) {
            is MainUiState.Ready -> "就绪"
            is MainUiState.Loading -> mainUiState.message ?: "加载中..."
            is MainUiState.Error -> mainUiState.message ?: "加载失败"
        }
    }
    val snackbarType = remember(mainUiState) {
        when (mainUiState) {
            is MainUiState.Ready -> SnackbarType.DEFAULT
            is MainUiState.Loading -> SnackbarType.DEFAULT
            is MainUiState.Error -> SnackbarType.ERROR
        }
    }
    val icon = Icons.Default.Error
    val isLoading = remember(mainUiState) {
        when (mainUiState) {
            is MainUiState.Ready -> true
            is MainUiState.Loading -> true
            is MainUiState.Error -> false
        }
    }
    SnackbarUICustom(
        modifier = modifier,
        visibleProvider = visibleProvider,
        textProvider = {text},
        positionProvider = {Alignment.BottomEnd},
        showLeadingIconProvider = {true},
        leadingIconProvider = {icon},
        leadingLoadingProvider = {isLoading},
        typeProvider = {snackbarType}
    )
}
