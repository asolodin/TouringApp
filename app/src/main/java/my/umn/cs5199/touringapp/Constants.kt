package my.umn.cs5199.touringapp

object Constants {
    const val FILE_EXT = ".plan"

    const val TRIP_FILE_NAME_PROP = "tripPlanFileName"

    const val MIN_AUTO_START_SPEED = 1.35 // ~3 mph

    const val MIN_AUTO_PAUSE_TIME = 30 * 1000 // 30 sec


    //               bearing
    //               n  ne  e   se  s   sw  w   nw
    //               0  1   2   3   4   5   6   7
    //        wind   -------------------------------
    const val ARROWS = "⬇" +    // 0-n   0  7   6   5   4   3   2   1
            "⬋" +   // 1-ne  1  0   7   6   5   4   3   2
            "⬅" +   // 2-e   2  1   0   7   6   5   4   3
            "⬉" +   // 3-se  3  2   1   0   7   6   5   4
            "⬆" +   // 4-s   4
            "⬈" +   // 5-sw  5
            "➡" +   // 6-w   6
            "⬊"     // 7-nw  7  6   5   4   3   2   1   0

    /**
     * convert wind direction in degrees to arrow index above
     */
    val FUN_DIR_TO_ARROW_INDEX: (Int) -> Int = { d: Int -> (d * 10 + 225) / 450 % 8 }

    /**
     * convert wind and bearing index to wind direction index relative to bearing
     */
    val FUN_RELATIVE_WIND_ARROW_INDEX: (Int, Int) -> Int =
        { w: Int, b: Int -> if (w - b < 0) 8 + w - b else w - b }

    val COLOR_CODE = arrayOf(
        0xFF00CC00, 0xFF33CC33, 0xFF66FF33, 0xFF99FF33, 0xFFCCFF33, 0xFFFFFF00,
        0xFFFFCC00, 0xFFFF9933, 0xFFFF9900, 0xFFFF6600, 0xFFFF3300, 0xFFFF0000
    )

    const val MAX_TEMP = 100
    const val MIN_TEMP = 50
    val TEMP_RANGE = (MAX_TEMP - MIN_TEMP).toFloat() / COLOR_CODE.size

    const val MAX_HUM = 100
    const val MIN_HUM = 35
    val HUM_RANGE = (MAX_HUM - MIN_HUM).toFloat() / COLOR_CODE.size

    const val COVER_CODE = "▢▤▦▩■"
    const val DIR_CODE = "⬇⬋⬅⬉⬆⬈➡⬊"
    const val DIR_CODE2 = "▼◣◀◤▲◥▶◢"

    val FUN_WIND_SPEED_TO_COLOR: (Int) -> Int = { s: Int ->
        val min = 5
        val max = 24
        val range = (max - min).toFloat() / COLOR_CODE.size
        val speed = Math.max(Math.min(s, max), min)
        val index = Math.min(
            (speed / range).toInt(),
            COLOR_CODE.size - 1
        )
        COLOR_CODE[index].toInt()
    }
}