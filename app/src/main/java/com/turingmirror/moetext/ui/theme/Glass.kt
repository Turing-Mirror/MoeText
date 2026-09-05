package com.turingmirror.moetext.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/** 底栏胶囊本体高度（不含外边距与系统导航栏）。 */
private val BarInnerHeight = 48.dp
private val BarPadding = 5.dp
private val BarSideMargin = 22.dp
private val BarBottomMargin = 10.dp

/**
 * 页面内容的内边距：底部要给悬浮底栏让出空间，
 * 否则最后一条内容会被压在玻璃底下滑不出来。
 */
@Composable
fun glassContentPadding(horizontal: Dp = 20.dp, top: Dp = 20.dp): PaddingValues {
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return PaddingValues(
        start = horizontal,
        end = horizontal,
        top = top + status,
        bottom = BarInnerHeight + BarPadding * 2 + BarBottomMargin + nav + 16.dp
    )
}

/** 内容层：底栏的模糊素材从这里采样。 */
fun Modifier.glassBackdrop(state: HazeState): Modifier = this.hazeSource(state)

@Composable
private fun specularBorder(isDark: Boolean, strong: Boolean = false): Brush {
    val top = if (isDark) (if (strong) 0.30f else 0.22f) else (if (strong) 0.98f else 0.85f)
    val mid = if (isDark) 0.06f else 0.24f
    val bottom = if (isDark) (if (strong) 0.16f else 0.11f) else (if (strong) 0.62f else 0.45f)
    return Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = top),
        0.5f to Color.White.copy(alpha = mid),
        1.0f to Color.White.copy(alpha = bottom)
    )
}

/** 玻璃内部由上而下的一层柔光，模拟光从上方打进介质。 */
@Composable
private fun sheen(isDark: Boolean): Brush = Brush.verticalGradient(
    0.0f to Color.White.copy(alpha = if (isDark) 0.09f else 0.34f),
    0.55f to Color.Transparent,
    1.0f to Color.White.copy(alpha = if (isDark) 0.03f else 0.10f)
)

@Composable
fun rememberGlassStyle(blurRadius: Dp = 24.dp): HazeStyle {
    val isDark = isSystemInDarkTheme()
    val background = MaterialTheme.colorScheme.background
    return HazeStyle(
        backgroundColor = background,
        tints = listOf(
            HazeTint(
                if (isDark) Color(0xFF262C36).copy(alpha = 0.40f)
                else Color.White.copy(alpha = 0.38f)
            )
        ),
        blurRadius = blurRadius,
        noiseFactor = 0.04f,
        fallbackTint = HazeTint(
            if (isDark) Color(0xFF262C36).copy(alpha = 0.86f)
            else Color.White.copy(alpha = 0.84f)
        )
    )
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF1F242D).copy(alpha = 0.58f) else Color.White.copy(alpha = 0.62f)
    val shadow = if (isDark) Color.Black.copy(alpha = 0.32f) else Color.Black.copy(alpha = 0.07f)
    Box(
        modifier = modifier
            .shadow(6.dp, shape, clip = false, ambientColor = shadow, spotColor = shadow)
            .clip(shape)
            .background(bg, shape)
            .border(0.7.dp, specularBorder(isDark), shape)
    ) {
        Box(Modifier.matchParentSize().background(sheen(isDark), shape))
        content()
    }
}

/**
 * 悬浮玻璃底栏。它不占布局空间，内容从它下面穿过去 ——
 * 玻璃背后必须有东西在动，否则就只是一块半透明的板。
 */
@Composable
fun GlassBottomBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val style = rememberGlassStyle(blurRadius = 26.dp)
    val shape = RoundedCornerShape(percent = 50)
    val shadow = if (isDark) Color.Black.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.13f)
    val thumbShape = RoundedCornerShape(percent = 50)
    val thumbBg = if (isDark) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.62f)

    Box(
        modifier = modifier
            .padding(horizontal = BarSideMargin)
            .padding(bottom = BarBottomMargin)
            .shadow(14.dp, shape, clip = false, ambientColor = shadow, spotColor = shadow)
            .clip(shape)
            .hazeEffect(hazeState, style)
            .background(sheen(isDark), shape)
            .border(0.8.dp, specularBorder(isDark, strong = true), shape)
            .padding(BarPadding)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(BarInnerHeight)) {
            val segW = maxWidth / 4
            val offset by animateDpAsState(
                targetValue = segW * selected,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
                label = "tabThumb"
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset(offset.roundToPx(), 0) }
                    .width(segW)
                    .fillMaxHeight()
                    .clip(thumbShape)
                    .background(thumbBg, thumbShape)
                    .border(0.8.dp, specularBorder(isDark, strong = true), thumbShape)
            ) {
                Box(Modifier.matchParentSize().background(sheen(isDark), thumbShape))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                val labels = listOf("状态", "规则", "测试", "Unicode")
                labels.forEachIndexed { idx, label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(thumbShape)
                            .selectable(
                                selected = selected == idx,
                                role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(idx) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = if (selected == idx) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected == idx) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GlassSegmentRow(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val containerBg = if (isDark) Color(0xFFE8F0F8).copy(alpha = 0.07f) else Color(0xFF1E242B).copy(alpha = 0.05f)
    val thumbBg = if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.92f)
    val containerBorder = if (isDark) Color.White.copy(alpha = 0.06f) else Color(0xFF1E242B).copy(alpha = 0.07f)
    val shape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .clip(shape)
            .background(containerBg, shape)
            .border(0.5.dp, containerBorder, shape)
            .padding(3.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(36.dp)
        ) {
            val segW = maxWidth / options.size
            val offset by animateDpAsState(
                targetValue = segW * selectedIndex,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
                label = "segThumb"
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset(offset.roundToPx(), 0) }
                    .width(segW)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(thumbBg, shape)
                    .border(0.7.dp, specularBorder(isDark), shape)
            ) {
                Box(Modifier.matchParentSize().background(sheen(isDark), shape))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { idx, label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(shape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelected(idx) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = if (selectedIndex == idx) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedIndex == idx) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isDark = isSystemInDarkTheme()
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = if (isDark) Color(0xFF4AA3F0).copy(alpha = 0.92f) else Color(0xFF1289F0),
            checkedBorderColor = Color.Transparent,
            checkedIconColor = Color.White,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = if (isDark) Color.White.copy(alpha = 0.18f) else Color(0xFF1E242B).copy(alpha = 0.08f),
            uncheckedBorderColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color(0xFF1E242B).copy(alpha = 0.14f),
            uncheckedIconColor = Color.White,
            disabledCheckedThumbColor = Color.White.copy(alpha = 0.6f),
            disabledCheckedTrackColor = Color(0xFF1289F0).copy(alpha = 0.35f),
            disabledUncheckedThumbColor = Color.White.copy(alpha = 0.6f),
            disabledUncheckedTrackColor = Color.Gray.copy(alpha = 0.12f)
        )
    )
}
