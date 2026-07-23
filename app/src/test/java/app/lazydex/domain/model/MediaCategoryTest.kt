package app.lazydex.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaCategoryTest {

    @Test
    fun statusLabel_returnsCorrectCategoryAdaptiveName() {
        val novel = MediaCategory.NOVEL
        assertEquals("Reading", novel.statusLabel(StatusFilter.IN_PROGRESS))
        assertEquals("Plan to Read", novel.statusLabel(StatusFilter.PLAN_TO))

        val anime = MediaCategory.ANIME
        assertEquals("Watching", anime.statusLabel(StatusFilter.IN_PROGRESS))
        assertEquals("Plan to Watch", anime.statusLabel(StatusFilter.PLAN_TO))

        val game = MediaCategory.GAME
        assertEquals("Playing", game.statusLabel(StatusFilter.IN_PROGRESS))
        assertEquals("Plan to Play", game.statusLabel(StatusFilter.PLAN_TO))

        val movie = MediaCategory.MOVIE
        assertEquals("Watching", movie.statusLabel(StatusFilter.IN_PROGRESS))

        val nullCategory: MediaCategory? = null
        assertEquals("In Progress", nullCategory.statusLabel(StatusFilter.IN_PROGRESS))
        assertEquals("Plan to", nullCategory.statusLabel(StatusFilter.PLAN_TO))
    }
}
