package app.lazydex.util

import app.lazydex.data.anilist.model.ScoreFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreConverterTest {

    @Test
    fun scoreToDisplay_returnsUnratedForNullOrZero() {
        assertEquals("Unrated", ScoreConverter.scoreToDisplay(null, ScoreFormat.POINT_100))
        assertEquals("Unrated", ScoreConverter.scoreToDisplay(0, ScoreFormat.POINT_5))
    }

    @Test
    fun scoreToDisplay_formats100PointCorrectly() {
        assertEquals("85 / 100", ScoreConverter.scoreToDisplay(85, ScoreFormat.POINT_100))
    }

    @Test
    fun scoreToDisplay_formats10PointDecimalCorrectly() {
        assertEquals("8.5", ScoreConverter.scoreToDisplay(85, ScoreFormat.POINT_10_DECIMAL))
    }

    @Test
    fun scoreToDisplay_formats10PointIntegerCorrectly() {
        assertEquals("9", ScoreConverter.scoreToDisplay(85, ScoreFormat.POINT_10))
    }

    @Test
    fun scoreToDisplay_formats5StarCorrectly() {
        assertEquals("4.5 ★", ScoreConverter.scoreToDisplay(90, ScoreFormat.POINT_5))
        assertEquals("4.0 ★", ScoreConverter.scoreToDisplay(80, ScoreFormat.POINT_5))
    }

    @Test
    fun scoreToDisplay_formats3PointSmileyCorrectly() {
        assertEquals("😦", ScoreConverter.scoreToDisplay(30, ScoreFormat.POINT_3))
        assertEquals("😐", ScoreConverter.scoreToDisplay(50, ScoreFormat.POINT_3))
        assertEquals("😊", ScoreConverter.scoreToDisplay(90, ScoreFormat.POINT_3))
    }

    @Test
    fun uiToScoreRaw_convertsUiRatingsToRawScoreCorrectly() {
        assertEquals(90, ScoreConverter.uiToScoreRaw(4.5, ScoreFormat.POINT_5))
        assertEquals(85, ScoreConverter.uiToScoreRaw(8.5, ScoreFormat.POINT_10_DECIMAL))
        assertEquals(80, ScoreConverter.uiToScoreRaw(8.0, ScoreFormat.POINT_10))
        assertEquals(90, ScoreConverter.uiToScoreRaw(3.0, ScoreFormat.POINT_3))
    }

    @Test
    fun snapToFormatInterval_snapsScoresToValidBoundaries() {
        assertEquals(80, ScoreConverter.snapToFormatInterval(84, ScoreFormat.POINT_5))
        assertEquals(80, ScoreConverter.snapToFormatInterval(84, ScoreFormat.POINT_10))
        assertEquals(84, ScoreConverter.snapToFormatInterval(84, ScoreFormat.POINT_100))
        assertEquals(90, ScoreConverter.snapToFormatInterval(84, ScoreFormat.POINT_3))
    }
}
