package com.sambhavdwivedi.callin.core.ringtone

import android.content.Context

data class RingtoneOption(val fileName: String, val label: String)

/** Lists every mp3 under assets/ringtones/, sorted, and labels them
 * "Ringtone 1", "Ringtone 2"... purely by position — never by the
 * actual file name, so any file can be dropped in with any name. */
object RingtoneAssets {
    private const val FOLDER = "ringtones"

    fun list(context: Context): List<RingtoneOption> {
        val names = runCatching {
            context.assets.list(FOLDER)?.filter { it.endsWith(".mp3", ignoreCase = true) }
        }.getOrNull().orEmpty().sorted()

        return names.mapIndexed { index, fileName ->
            RingtoneOption(fileName = fileName, label = "Ringtone ${index + 1}")
        }
    }

    fun assetPath(fileName: String): String = "$FOLDER/$fileName"
}
