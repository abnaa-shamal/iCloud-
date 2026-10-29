package jo.shajaraone.app.hasbat

import jo.shajaraone.app.R
import jo.shajaraone.app.MainActivity

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ======================================================================
// UI KIT 2026 — Glassmorphism
// ======================================================================

fun readableOn(c: Color): Color = if (c.luminance() > 0.5f) Color(0xFF07101C) else Color.White

fun neonTextStyle(color: Color, blur: Float = 22f, glowAlpha: Float = 0.65f): TextStyle =
    TextStyle(shadow = Shadow(color = color.copy(alpha = glowAlpha), offset = Offset.Zero, blurRadius = blur))

@Composable
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press_scale"
    )
    return this
        .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick
        )
}

@Composable
fun GlassCard(
    theme: AppTheme,
    modifier: Modifier = Modifier,
    corner: Dp = 24.dp,
    glow: Color = theme.accentColor,
    secondGlow: Color = theme.needleColor,
    animatedBorder: Boolean = false,
    contentPadding: Dp = 16.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(corner)
    val sweep: State<Float>? = if (animatedBorder) {
        rememberInfiniteTransition(label = "glass_border").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
            label = "glass_sweep"
        )
    } else null

    val fill = if (theme.isDark) {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.11f),
                theme.cardBg.copy(alpha = 0.72f),
                theme.cardBg.copy(alpha = 0.58f)
            )
        )
    } else {
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.94f), Color.White.copy(alpha = 0.68f)))
    }
    val edge = Brush.linearGradient(
        listOf(
            glow.copy(alpha = 0.85f),
            if (theme.isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f),
            secondGlow.copy(alpha = 0.70f)
        )
    )

    Box(
        modifier = modifier
            .shadow(14.dp, shape, ambientColor = glow.copy(alpha = 0.30f), spotColor = glow.copy(alpha = 0.45f))
            .clip(shape)
            .background(fill)
            .border(1.dp, edge, shape)
            .then(
                if (sweep != null) {
                    Modifier.drawWithContent {
                        drawContent()
                        val w = size.width
                        val shift = sweep.value * 2f * w - w
                        val s = 2.dp.toPx()
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                colors = listOf(Color.Transparent, glow, secondGlow, Color.Transparent),
                                start = Offset(shift, 0f),
                                end = Offset(shift + w, size.height)
                            ),
                            topLeft = Offset(s / 2f, s / 2f),
                            size = Size(size.width - s, size.height - s),
                            cornerRadius = CornerRadius(corner.toPx() - s / 2f),
                            style = Stroke(width = s)
                        )
                    }
                } else Modifier
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content
        )
    }
}

@Composable
fun NeonChip(
    text: String,
    selected: Boolean,
    theme: AppTheme,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
    horizontalPadding: Dp = 6.dp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val baseText = if (theme.isDark) Color.White else Color(0xFF1E293B)
    val fillAlpha by animateFloatAsState(if (selected) 1f else 0f, tween(260), label = "chip_fill")
    val labelColor by animateColorAsState(
        targetValue = if (selected) readableOn(theme.accentColor) else baseText,
        animationSpec = tween(260),
        label = "chip_label"
    )
    Box(
        modifier = modifier
            .bounceClick(onClick = onClick)
            .clip(shape)
            .background(if (theme.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        theme.accentColor.copy(alpha = 0.95f * fillAlpha),
                        theme.needleColor.copy(alpha = 0.80f * fillAlpha)
                    )
                )
            )
            .border(
                1.dp,
                if (selected) theme.accentColor.copy(alpha = 0.9f)
                else (if (theme.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)),
                shape
            )
            .padding(vertical = 10.dp, horizontal = horizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = labelColor, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun GlassIconButton(icon: String, theme: AppTheme, size: Dp = 40.dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .bounceClick(onClick = onClick)
            .clip(CircleShape)
            .background(if (theme.isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.92f))
            .border(
                1.dp,
                Brush.linearGradient(listOf(theme.accentColor.copy(alpha = 0.85f), theme.needleColor.copy(alpha = 0.30f))),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(icon, fontSize = (size.value * 0.46f).sp)
    }
}

@Composable
fun PulsingDot(color: Color, active: Boolean, size: Dp = 9.dp) {
    val dotAlpha: State<Float> = if (active) {
        rememberInfiniteTransition(label = "dot").animateFloat(
            initialValue = 0.30f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "dot_alpha"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer { this.alpha = dotAlpha.value }
            .background(color, CircleShape)
    )
}

@Composable
fun NeonActionButton(
    text: String,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    height: Dp = 58.dp,
    pulse: Boolean = false,
    textSize: TextUnit = 16.sp,
    onClick: () -> Unit
) {
    val corner = 20.dp
    val shape = RoundedCornerShape(corner)
    val glowAlpha: State<Float> = if (pulse) {
        rememberInfiniteTransition(label = "btn_pulse").animateFloat(
            initialValue = 0.20f,
            targetValue = 0.55f,
            animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "btn_glow"
        )
    } else {
        remember { mutableFloatStateOf(0.28f) }
    }
    Box(
        modifier = modifier
            .height(height)
            .bounceClick(onClick = onClick)
            .drawBehind {
                for (i in 1..3) {
                    val grow = i * 5.dp.toPx()
                    drawRoundRect(
                        color = colors.first().copy(alpha = glowAlpha.value / (i + 1)),
                        topLeft = Offset(-grow, -grow),
                        size = Size(size.width + grow * 2f, size.height + grow * 2f),
                        cornerRadius = CornerRadius(corner.toPx() + grow)
                    )
                }
            }
            .clip(shape)
            .background(Brush.horizontalGradient(colors))
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.04f))),
                shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)))
        )
        Text(
            text = text,
            fontSize = textSize,
            fontWeight = FontWeight.Black,
            color = readableOn(colors.first()),
            maxLines = 1
        )
    }
}

@Composable
fun neonFieldColors(theme: AppTheme): TextFieldColors {
    val tc = if (theme.isDark) Color.White else Color(0xFF1E293B)
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = tc,
        unfocusedTextColor = tc,
        focusedBorderColor = theme.accentColor,
        unfocusedBorderColor = tc.copy(alpha = 0.18f),
        cursorColor = theme.accentColor,
        focusedLabelColor = theme.accentColor,
        unfocusedLabelColor = tc.copy(alpha = 0.60f),
        focusedContainerColor = tc.copy(alpha = 0.05f),
        unfocusedContainerColor = tc.copy(alpha = 0.03f)
    )
}

/** خلفية ثابتة (بدون حركة مستمرة) لتوفير البطارية أثناء الشفت الطويل. */
@Composable
fun CyberBackdrop(theme: AppTheme) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val orbA = if (theme.isDark) 0.26f else 0.15f
        val orbB = if (theme.isDark) 0.18f else 0.11f
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(theme.accentColor.copy(alpha = orbA), Color.Transparent),
                center = Offset(size.width * 0.80f, size.height * 0.12f),
                radius = size.width * 0.85f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(theme.needleColor.copy(alpha = orbB), Color.Transparent),
                center = Offset(size.width * 0.20f, size.height * 0.86f),
                radius = size.width * 0.80f
            )
        )
        val gridStep = 44.dp.toPx()
        val gridColor = (if (theme.isDark) Color.White else Color.Black).copy(alpha = 0.035f)
        var gx = 0f
        while (gx <= size.width) {
            drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
            gx += gridStep
        }
        var gy = 0f
        while (gy <= size.height) {
            drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
            gy += gridStep
        }
    }
}

@Composable
fun ThinDivider(theme: AppTheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        if (theme.isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.12f),
                        Color.Transparent
                    )
                )
            )
    )
}


fun textColorFor(theme: AppTheme): Color = if (theme.isDark) Color.White else Color(0xFF1E293B)
fun subTextColorFor(theme: AppTheme): Color = if (theme.isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

/** صف أزرار اختيار قابل للتمرير أفقياً حتى لا تُقص الكلمات على الشاشات الصغيرة. */
@Composable
fun ScrollableChipRow(
    items: List<String>,
    selectedIndex: Int,
    theme: AppTheme,
    fontSize: TextUnit = 12.sp,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { index, title ->
            PillChip(text = title, selected = index == selectedIndex, theme = theme) { onSelect(index) }
        }
    }
}

/** نافذة تأكيد موحّدة لكل عمليات الحذف. */
@Composable
fun ConfirmDialog(
    theme: AppTheme,
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    destructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val tc = textColorFor(theme)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.cardBg,
        titleContentColor = tc,
        textContentColor = tc.copy(alpha = 0.85f),
        title = { Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp) },
        text = { Text(message, fontSize = 14.sp, lineHeight = 22.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (destructive) Color(0xFFEF4444) else theme.accentColor, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText, color = tc.copy(alpha = 0.8f), fontWeight = FontWeight.Bold) }
        }
    )
}

// ======================================================================
// النظام الموحّد: زر التنقل الكبير، والخيار الصغير
// ======================================================================

/** زر التنقل الرئيسي في أعلى الشاشة: مفتاح واحد بخلفية واحدة، والمختار بلون الثيم. */
@Composable
fun SegmentedTabs(items: List<String>, selectedIndex: Int, theme: AppTheme, onSelect: (Int) -> Unit) {
    val track = if (theme.isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.06f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            val bg by animateColorAsState(if (selected) theme.accentColor else Color.Transparent, tween(220), label = "seg_bg")
            val fg by animateColorAsState(if (selected) readableOn(theme.accentColor) else subTextColorFor(theme), tween(220), label = "seg_fg")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .bounceClick { onSelect(i) }
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = fg,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** خيار صغير مدوّر (الفترة، المبالغ، التعرفات، الفئات). المختار ممتلئ بلون النص. */
@Composable
fun PillChip(text: String, selected: Boolean, theme: AppTheme, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val tc = textColorFor(theme)
    val fill = if (selected) tc else Color.Transparent
    val label = if (selected) (if (theme.isDark) Color(0xFF0B0B0C) else Color.White) else tc.copy(alpha = 0.85f)
    Box(
        modifier = modifier
            .bounceClick(onClick = onClick)
            .clip(shape)
            .background(fill)
            .border(1.dp, if (selected) tc else theme.accentColor.copy(alpha = 0.45f), shape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Black else FontWeight.Bold, color = label, maxLines = 1)
    }
}
