package app.lazydex.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.ui.theme.Rating1
import app.lazydex.ui.theme.Rating2
import app.lazydex.ui.theme.Rating3
import app.lazydex.ui.theme.Rating4
import app.lazydex.ui.theme.Rating5

@Composable
fun StarRating(
    rating: Int?,
    modifier: Modifier = Modifier,
    isEditable: Boolean = false,
    onRatingChanged: (Int?) -> Unit = {}
) {
    val displayFiveStar = (rating ?: 0) / 20.0

    val starColor = when {
        rating == null || rating == 0 -> Color.Gray
        rating >= 90 -> Rating5
        rating >= 70 -> Rating4
        rating >= 50 -> Rating3
        rating >= 30 -> Rating2
        else -> Rating1
    }

    val pointerModifier = if (isEditable) {
        Modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val totalWidth = size.width
                val starWidth = totalWidth / 5f
                val tappedStarIndex = (offset.x / starWidth).toInt() // 0 to 4
                val fraction = offset.x % starWidth
                val isHalf = fraction < starWidth / 2f
                val newFiveStar = tappedStarIndex + 1f - (if (isHalf) 0.5f else 0f)
                val clampedScore = (newFiveStar * 20.0).toInt().coerceIn(10, 100)
                onRatingChanged(clampedScore)
            }
        }
    } else {
        Modifier
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.then(pointerModifier)
    ) {
        for (i in 1..5) {
            val icon = when {
                displayFiveStar >= i -> Icons.Default.Star
                displayFiveStar >= i - 0.5 -> Icons.Default.StarHalf
                else -> Icons.Default.StarBorder
            }
            Icon(
                imageVector = icon,
                contentDescription = if (isEditable) "Rate $i stars" else null,
                tint = starColor,
                modifier = Modifier.size(20.dp)
            )
        }

        if (rating != null && rating > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = String.format("%.1f/5.0", displayFiveStar),
                color = starColor,
                fontSize = 12.sp,
                style = MaterialTheme.typography.labelSmall
            )
        } else if (isEditable) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Unrated",
                color = Color.Gray,
                fontSize = 12.sp,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
