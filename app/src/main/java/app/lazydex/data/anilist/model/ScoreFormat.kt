package app.lazydex.data.anilist.model

enum class ScoreFormat(val displayName: String) {
    POINT_100("100-Point (1-100)"),
    POINT_10_DECIMAL("10-Point Decimal (1.0-10.0)"),
    POINT_10("10-Point Integer (1-10)"),
    POINT_5("5-Star (0.5-5.0 ★)"),
    POINT_3("3-Point Smiley (😦 😐 😊)");

    companion object {
        fun fromString(value: String?): ScoreFormat {
            if (value == null) return POINT_5
            return entries.firstOrNull { 
                it.name.equals(value, ignoreCase = true) || it.displayName.equals(value, ignoreCase = true)
            } ?: POINT_5
        }
    }
}
