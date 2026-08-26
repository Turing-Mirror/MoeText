package com.turingmirror.moetext.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF1F242D).copy(alpha = 0.58f) else Color.White.copy(alpha = 0.62f)
    val border = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f)
    val shadow = if (isDark) Color.Black.copy(alpha = 0.32f) else Color.Black.copy(alpha = 0.07f)
    Box(
        modifier = modifier
            .shadow(6.dp, shape, clip = false, ambientColor = shadow, spotColor = shadow)
            .clip(shape)
            .background(bg, shape)
            .border(BorderStroke(0.5.dp, border), shape)
    ) {
        content()
    }
}

@Composable
fun GlassBottomBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val containerBg = if (isDark) Color(0xFF1F242D).copy(alpha = 0.48f) else Color.White.copy(alpha = 0.52f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.42f)
    val thumbBg = if (isDark) Color(0xFF2B323E) else Color.White

    Box(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(28.dp), clip = false)
            .clip(RoundedCornerShape(28.dp))
            .background(containerBg, RoundedCornerShape(28.dp))
            .border(BorderStroke(0.5.dp, borderColor), RoundedCornerShape(28.dp))
            .padding(4.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(44.dp)) {
            val segW = maxWidth / 3
            val offset by animateDpAsState(
                targetValue = segW * selected,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
            )
            Box(
                modifier = Modifier
                    .offset(x = offset)
                    .width(segW)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(22.dp))
                    .background(thumbBg, RoundedCornerShape(22.dp))
                    .border(BorderStroke(0.5.dp, borderColor), RoundedCornerShape(22.dp))
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                val labels = listOf("状态", "规则", "测试")
                labels.forEachIndexed { idx, label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(22.dp))
                            .clickable(
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
    val thumbBg = if (isDark) Color(0xFF2B323E) else Color.White
    val borderColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.45f)
    val containerBorder = if (isDark) Color.White.copy(alpha = 0.06f) else Color(0xFF1E242B).copy(alpha = 0.07f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(containerBg, RoundedCornerShape(999.dp))
            .border(BorderStroke(0.5.dp, containerBorder), RoundedCornerShape(999.dp))
            .padding(3.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(36.dp)
        ) {
            val segW = maxWidth / options.size
            val offset by animateDpAsState(
                targetValue = segW * selectedIndex,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f)
            )
            Box(
                modifier = Modifier
                    .offset(x = offset)
                    .width(segW)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(thumbBg, RoundedCornerShape(999.dp))
                    .border(BorderStroke(0.5.dp, borderColor), RoundedCornerShape(999.dp))
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { idx, label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
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
