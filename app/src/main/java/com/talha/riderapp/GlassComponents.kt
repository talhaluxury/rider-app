package com.talha.riderapp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Same component names/shape/spacing language as the Restaurant POS app's glass system.
// Only the accent colour differs (Rider = blue/cyan).

@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(RiderColors.Bg0, RiderColors.Bg1, RiderColors.Bg2))
        )
    ) {
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = 80.dp, y = (-60).dp).size(280.dp).background(
                Brush.radialGradient(listOf(RiderColors.Blue.copy(alpha = 0.22f), Color.Transparent)),
                RoundedCornerShape(Radius.Pill)
            )
        )
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(Radius.ExtraLarge)
    val borderColor by animateColorAsState(
        if (accent) RiderColors.Blue.copy(alpha = 0.55f) else RiderColors.GlassBorder, tween(200), label = "cardBorder"
    )
    var m = modifier
        .clip(shape)
        .background(Brush.verticalGradient(listOf(RiderColors.Glass2, RiderColors.Glass1)))
        .border(BorderStroke(1.dp, borderColor), shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(Spacing.LG), content = content)
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    primary: Boolean = true,
    tint: Color = RiderColors.Blue,
    height: Dp = 56.dp
) {
    val shape = RoundedCornerShape(Radius.Large)
    val bg by animateColorAsState(
        if (!enabled) RiderColors.Glass1 else if (primary) tint else RiderColors.Glass2, tween(200), label = "btnBg"
    )
    val base = modifier
        .heightIn(min = height)
        .then(if (enabled && primary) Modifier.shadow(14.dp, shape, ambientColor = tint, spotColor = tint) else Modifier)
        .clip(shape)
        .background(bg)
        .border(BorderStroke(1.dp, if (primary) tint.copy(alpha = 0.9f) else RiderColors.GlassBorderActive), shape)
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = Spacing.LG, vertical = Spacing.MD)
    Row(base, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) Color.White else RiderColors.TextTertiary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Spacing.SM))
        }
        Text(
            text, color = if (enabled) Color.White else RiderColors.TextTertiary,
            fontWeight = FontWeight.Bold, fontSize = 16.sp
        )
    }
}

@Composable
fun GlassIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.Medium)
    Box(
        modifier.size(48.dp).clip(shape).background(RiderColors.Glass2)
            .border(BorderStroke(1.dp, RiderColors.GlassBorder), shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, description, tint = RiderColors.TextPrimary) }
}

@Composable
fun GlassTopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.LG, vertical = Spacing.MD),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            GlassIconButton(Icons.Default.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(Spacing.MD))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = RiderColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            if (subtitle != null) Text(subtitle, color = RiderColors.TextSecondary, fontSize = 13.sp)
        }
        actions()
    }
}

@Composable
fun GlassStatusBadge(text: String, color: Color = RiderColors.Blue) {
    Box(
        Modifier.clip(RoundedCornerShape(Radius.Pill)).background(color.copy(alpha = 0.18f))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.5f)), RoundedCornerShape(Radius.Pill))
            .padding(horizontal = Spacing.MD, vertical = 5.dp)
    ) { Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun GlassChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.Pill)
    Box(
        Modifier.clip(shape).background(if (selected) RiderColors.Blue.copy(alpha = 0.25f) else RiderColors.Glass1)
            .border(BorderStroke(1.dp, if (selected) RiderColors.Blue else RiderColors.GlassBorder), shape)
            .clickable(onClick = onClick).padding(horizontal = Spacing.LG, vertical = Spacing.SM)
    ) { Text(text, color = RiderColors.TextPrimary, fontSize = 13.sp) }
}

@Composable
fun GlassStatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, accent: Color = RiderColors.Blue) {
    GlassCard(modifier) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(Spacing.SM))
        Text(value, color = RiderColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(label, color = RiderColors.TextSecondary, fontSize = 12.sp)
    }
}

@Composable
fun GlassInput(
    value: String, onValueChange: (String) -> Unit, label: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        singleLine = true, keyboardOptions = keyboardOptions, visualTransformation = visualTransformation,
        shape = RoundedCornerShape(Radius.Large),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = RiderColors.Blue, unfocusedBorderColor = RiderColors.GlassBorderActive,
            focusedLabelColor = RiderColors.Blue, unfocusedLabelColor = RiderColors.TextSecondary,
            focusedTextColor = RiderColors.TextPrimary, unfocusedTextColor = RiderColors.TextPrimary,
            focusedContainerColor = RiderColors.Glass1, unfocusedContainerColor = RiderColors.Glass1,
            cursorColor = RiderColors.Blue
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

data class NavItem(val label: String, val icon: ImageVector)

@Composable
fun GlassBottomNavigation(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(Radius.ExtraLarge)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.LG, vertical = Spacing.MD)
            .clip(shape).background(RiderColors.Bg2.copy(alpha = 0.92f))
            .border(BorderStroke(1.dp, RiderColors.GlassBorderActive), shape)
            .padding(vertical = Spacing.SM),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        items.forEachIndexed { i, item ->
            val active = i == selected
            val tint by animateColorAsState(if (active) RiderColors.Cyan else RiderColors.TextTertiary, tween(200), label = "navTint")
            Column(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(Radius.Pill))
                        .background(if (active) RiderColors.Blue.copy(alpha = 0.22f) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) { Icon(item.icon, item.label, tint = tint, modifier = Modifier.size(24.dp)) }
                Text(item.label, color = tint, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
