package app.lazydex.domain.model

enum class MediaFormat(val displayName: String) {
    TV_SERIES("TV Series"),
    TV_SHORT("TV Short"),
    ANIME_MOVIE("Movie"),
    SPECIAL("Special"),
    OVA("OVA"),
    ONA("ONA"),
    MUSIC("Music"),
    MANGA("Manga"),
    NOVEL("Novel"),
    ONE_SHOT("One-Shot"),
    LIGHT_NOVEL("Light Novel"),
    VISUAL_NOVEL("Visual Novel"),
    VIDEO_GAME("Video Game"),
    LIVE_ACTION_TV("Live-Action TV"),
    LIVE_ACTION_MOVIE("Live-Action Movie");

    companion object {
        fun fromString(value: String?): MediaFormat? {
            if (value.isNullOfBlank()) return null
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) || it.displayName.equals(value, ignoreCase = true)
            }
        }

        private fun String?.isNullOfBlank(): Boolean = this == null || this.trim().isEmpty()
    }
}
