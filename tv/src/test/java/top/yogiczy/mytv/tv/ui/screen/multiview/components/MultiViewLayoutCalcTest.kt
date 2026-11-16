package top.yogiczy.mytv.tv.ui.screen.multiview.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 多画面网格布局算数测试：行列、格子尺寸、缩放比、摆放坐标。
 * 两个算数类之前是 private、单测够不着，改成 internal 后才能直接断；
 * 生产行为一字没动。期望值都是手算的整数档：宽屏 2560×1080（高宽比
 * 2.37，走高驱动分支）与竖屏 1080×1920（高宽比 0.56，走宽驱动分支），
 * 都不在 16:9 分支边界上，不踩浮点相等那档。
 */
class MultiViewLayoutCalcTest {
    @Test
    fun gridCalc_fourItems_wideScreen_twoByTwo() {
        // 4 格、2560×1080：格子 960×540，2 行 2 列，普通格缩放比 0.5，
        // 按列排：0 号左上、1 号左下、2 号右上、3 号右下，左右各留 320
        val calc = GridLayoutCalc(count = 4, maxWidth = 2560, maxHeight = 1080)
        assertEquals(2, calc.rows)
        assertEquals(2, calc.columns)
        assertEquals(960, calc.itemWidth)
        assertEquals(540, calc.itemHeight)
        assertEquals(0.5f, calc.itemScale, 0.0001f)
        assertEquals(320 to 0, calc.placeRelative(0))
        assertEquals(320 to 540, calc.placeRelative(1))
        assertEquals(1280 to 0, calc.placeRelative(2))
        assertEquals(1280 to 540, calc.placeRelative(3))
    }

    @Test
    fun gridCalc_fourItems_tallScreen_centeredVertically() {
        // 4 格、1080×1920：格子 540×303（540 / (16/9) = 303.75 取整），
        // 2 行 2 列，上下各留 657，左右贴边
        val calc = GridLayoutCalc(count = 4, maxWidth = 1080, maxHeight = 1920)
        assertEquals(2, calc.rows)
        assertEquals(2, calc.columns)
        assertEquals(540, calc.itemWidth)
        assertEquals(303, calc.itemHeight)
        assertEquals(0.5f, calc.itemScale, 0.0001f)
        assertEquals(0 to 960, calc.placeRelative(1))
        assertEquals(540 to 960, calc.placeRelative(3))
    }

    @Test
    fun gridCalc_twoItems_stackedVerticallyCentered() {
        // 2 格、2560×1080：上下叠放、只占 1 列，左右各留 800
        val calc = GridLayoutCalc(count = 2, maxWidth = 2560, maxHeight = 1080)
        assertEquals(2, calc.rows)
        assertEquals(1, calc.columns)
        assertEquals(960, calc.itemWidth)
        assertEquals(540, calc.itemHeight)
        assertEquals(800 to 0, calc.placeRelative(0))
        assertEquals(800 to 540, calc.placeRelative(1))
    }

    @Test
    fun gridCalc_singleItem_fillsScreen() {
        // 1 格、2560×1080：整格 1920×1080 不缩放，左右各留 320
        val calc = GridLayoutCalc(count = 1, maxWidth = 2560, maxHeight = 1080)
        assertEquals(1, calc.rows)
        assertEquals(1, calc.columns)
        assertEquals(1920, calc.itemWidth)
        assertEquals(1080, calc.itemHeight)
        assertEquals(1f, calc.itemScale, 0.0001f)
        assertEquals(320 to 0, calc.placeRelative(0))
    }

    @Test
    fun gridCalcWithZoomIn_fourItems_zoomCellIsThreeByThree() {
        // 4 格带放大、2560×1080：放大格占 3×3（1440×810），普通格
        // 480×270；放大格在左上、其余 3 格排在放大格下方的底行
        val calc = GridLayoutWithZoomIn(count = 4, maxWidth = 2560, maxHeight = 1080)
        assertEquals(4, calc.gridSize)
        assertEquals(480, calc.itemWidth)
        assertEquals(270, calc.itemHeight)
        assertEquals(1440, calc.zoomInItemWidth)
        assertEquals(810, calc.zoomInItemHeight)
        assertEquals(0.25f, calc.itemScale, 0.0001f)
        assertEquals(0.75f, calc.zoomInItemScale, 0.0001f)
        assertEquals(560 to 0, calc.placeRelativeZoomIn())
        assertEquals(560 to 810, calc.placeRelative(0))
        assertEquals(1520 to 810, calc.placeRelative(2))
        assertEquals(2000 to 0, calc.placeRelative(3))
    }

    @Test
    fun gridCalcWithZoomIn_nineItems_rightColumnBelowZoom() {
        // 9 格带放大、2560×1080：放大格 1536×864，普通格 384×216；
        // 前 4 格在底行，第 5 格起排到放大格右侧那列、从上往下
        val calc = GridLayoutWithZoomIn(count = 9, maxWidth = 2560, maxHeight = 1080)
        assertEquals(5, calc.gridSize)
        assertEquals(384, calc.itemWidth)
        assertEquals(216, calc.itemHeight)
        assertEquals(1536, calc.zoomInItemWidth)
        assertEquals(864, calc.zoomInItemHeight)
        assertEquals(0.2f, calc.itemScale, 0.0001f)
        assertEquals(0.8f, calc.zoomInItemScale, 0.0001f)
        assertEquals(320 to 0, calc.placeRelativeZoomIn())
        assertEquals(320 to 864, calc.placeRelative(0))
        assertEquals(1472 to 864, calc.placeRelative(3))
        assertEquals(1856 to 0, calc.placeRelative(4))
        assertEquals(1856 to 648, calc.placeRelative(7))
    }
}
