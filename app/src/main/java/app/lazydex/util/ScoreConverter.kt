package app.lazydex.util

import app.lazydex.data.anilist.model.ScoreFormat
import java.util.Locale
import kotlin.math.roundToInt

object ScoreConverter {

    /**
     * Converts a 0–100 raw integer score (from DB) into a user-facing display string
     * according to the specified ScoreFormat preference.
     */
    fun scoreToDisplay(scoreRaw: Int?, format: ScoreFormat): String = when {
        scoreRaw == null || scoreRaw == 0 -> "Unrated"
        else -> when (format) {
            ScoreFormat.POINT_100 -> "$scoreRaw / 100"
            ScoreFormat.POINT_10_DECIMAL -> String.format(Locale.US, "%.1f", scoreRaw / 10.0)
            ScoreFormat.POINT_10 -> "${(scoreRaw / 10.0).roundToInt()}"
            ScoreFormat.POINT_5 -> String.format(Locale.US, "%.1f ★", (scoreRaw / 20.0 * 2.0).roundToInt() / 2.0)
            ScoreFormat.POINT_3 -> when {
                scoreRaw <= 35 -> "😦"
                scoreRaw <= 65 -> "😐"
                else -> "😊"
            }
        }
    }

    /**
     * Converts a UI rating input (e.g. 4.5 stars, 8.5/10) back into a 0–100 internal integer score.
     */
    fun uiToScoreRaw(value: Double, format: ScoreFormat): Int = when (format) {
        ScoreFormat.POINT_100 -> value.toInt().coerceIn(1, 100)
        ScoreFormat.POINT_10_DECIMAL -> (value * 10.0).roundToInt().coerceIn(10, 100)
        ScoreFormat.POINT_10 -> (value * 10.0).roundToInt().coerceIn(10, 100)
        ScoreFormat.POINT_5 -> (value * 20.0).roundToInt().coerceIn(10, 100)
        ScoreFormat.POINT_3 -> when (value.toInt()) {
            1 -> 30  // 😦
            2 -> 50  // 😐
            3 -> 90  // 😊
            else -> 0
        }
    }

    /**
     * Snaps a 0–100 integer score to valid interval boundaries required by AniList profile ScoreFormat.
     */
    fun snapToFormatInterval(scoreRaw: Int, format: ScoreFormat): Int {
        if (scoreRaw <= 0) return 0
        return when (format) {
            ScoreFormat.POINT_100 -> scoreRaw.coerceIn(1, 100)
            ScoreFormat.POINT_10_DECIMAL -> scoreRaw.coerceIn(10, 100)
            ScoreFormat.POINT_10 -> ((scoreRaw / 10.0).roundToInt() * 10).coerceIn(10, 100)
            ScoreFormat.POINT_5 -> ((scoreRaw / 10.0).roundToInt() * 10).coerceIn(10, 100)
            ScoreFormat.POINT_3 -> when {
                scoreRaw <= 35 -> 30
                scoreRaw <= 65 -> 50
                else -> 90
            }
        }
    }
}
