package com.drosocode.myphone.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drosocode.myphone.ui.theme.CyberpunkColors

@Composable
fun GlowText(
    text: String,
    color: Color = CyberpunkColors.NeonCyan,
    fontSize: Int = 16,
    fontWeight: FontWeight = FontWeight.Bold,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize.sp,
            fontWeight = fontWeight,
            shadow = Shadow(
                color = color.copy(alpha = 0.5f),
                offset = Offset(0f, 0f),
                blurRadius = 10f
            )
        )
    )
}

@Composable
fun ScanlineOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val lineSpacing = 8.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color.Black.copy(alpha = 0.15f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
            y += lineSpacing
        }
    }
}

@Composable
fun CyberButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CyberpunkColors.NeonCyan,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .border(BorderStroke(1.dp, color), RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        GlowText(text = text, color = color, fontSize = 16)
    }
}

@Composable
fun CyberpunkCard(
    modifier: Modifier = Modifier,
    accentColor: Color = CyberpunkColors.NeonCyan,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .drawBehind {
                val cornerSize = 12.dp.toPx()
                val path = Path().apply {
                    moveTo(cornerSize, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height - cornerSize)
                    lineTo(size.width - cornerSize, size.height)
                    lineTo(0f, size.height)
                    lineTo(0f, cornerSize)
                    close()
                }
                
                // Background with subtle glass effect
                drawPath(
                    path = path,
                    color = CyberpunkColors.DarkGray.copy(alpha = 0.85f)
                )
                
                // Animated Gradient Border
                val borderBrush = Brush.linearGradient(
                    colors = listOf(accentColor, accentColor.copy(alpha = 0.3f)),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
                drawPath(
                    path = path,
                    brush = borderBrush,
                    style = Stroke(width = 1.dp.toPx())
                )
                
                // Tech decorations (Static)
                val techColor = accentColor.copy(alpha = 0.75f)
                drawLine(
                    color = techColor,
                    start = Offset(0f, cornerSize),
                    end = Offset(cornerSize, 0f),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = techColor,
                    start = Offset(size.width - cornerSize, size.height),
                    end = Offset(size.width, size.height - cornerSize),
                    strokeWidth = 2.dp.toPx()
                )
                
                // Subtle corner dot
                drawCircle(
                    color = techColor,
                    radius = 2.dp.toPx(),
                    center = Offset(size.width - 4.dp.toPx(), 4.dp.toPx())
                )
            }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun TechBar(
    progress: Float,
    color: Color = CyberpunkColors.NeonCyan,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.height(4.dp).fillMaxWidth()) {
        val width = size.width
        val height = size.height
        
        drawRect(
            color = color.copy(alpha = 0.2f),
            size = size
        )
        
        drawRect(
            color = color,
            size = size.copy(width = width * progress)
        )
        
        // Add ticks
        val tickCount = 10
        val tickWidth = 2.dp.toPx()
        for (i in 0..tickCount) {
            val x = (width / tickCount) * i
            drawLine(
                color = color.copy(alpha = 0.5f),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = tickWidth
            )
        }
    }
}

@Composable
fun DialerButton(
    number: String,
    label: String = "",
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(8.dp)
            .drawBehind {
                val cs = 12.dp.toPx()
                val path = Path().apply {
                    moveTo(cs, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height - cs)
                    lineTo(size.width - cs, size.height)
                    lineTo(0f, size.height)
                    lineTo(0f, cs)
                    close()
                }
                // Glass-like button background
                drawPath(path, color = Color.White.copy(alpha = 0.05f))
                drawPath(path, color = CyberpunkColors.NeonCyan, style = Stroke(width = 1.dp.toPx()))
                
                // Corner accent
                drawLine(
                    color = CyberpunkColors.NeonCyan,
                    start = Offset(0f, cs),
                    end = Offset(cs, 0f),
                    strokeWidth = 2.dp.toPx()
                )
            }
            .clickable { onClick(number) },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlowText(text = number, fontSize = 32)
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    color = CyberpunkColors.NeonCyan.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .drawBehind {
                drawCircle(
                    color = color.copy(alpha = 0.1f),
                    radius = (32.dp.toPx() + 4.dp.toPx()) // Static glow
                )
            }
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.2f))
            .border(2.dp, color, RoundedCornerShape(50))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(32.dp)
        )
    }
}
