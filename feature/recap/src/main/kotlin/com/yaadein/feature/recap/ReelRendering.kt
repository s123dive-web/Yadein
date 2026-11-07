package com.yaadein.feature.recap

import android.content.Context
import androidx.core.content.res.ResourcesCompat
import com.yaadein.data.recap.MusicSource
import com.yaadein.data.recap.RenderStyle
import com.yaadein.domain.recap.RecapPlan
import com.yaadein.domain.recap.RecapSegment
import com.yaadein.domain.recap.RecapTheme
import com.yaadein.core.ui.R as CoreR

/** Bits shared by the user's reel export and the automatic reels. */
internal object ReelRendering {
    fun style(context: Context) = RenderStyle(
        titleTypeface = ResourcesCompat.getFont(context, CoreR.font.fraunces_semibold) ?: android.graphics.Typeface.SERIF,
        bodyTypeface = ResourcesCompat.getFont(context, CoreR.font.manrope_medium) ?: android.graphics.Typeface.SANS_SERIF,
    )

    /** First photo (or clip) of the reel, shown as its cover in the library. */
    fun coverMediaId(plan: RecapPlan): Long? = plan.segments.firstNotNullOfOrNull {
        when (it) {
            is RecapSegment.Photo -> it.mediaId
            is RecapSegment.Clip -> it.mediaId
            is RecapSegment.TitleCard -> null
        }
    }

    fun bundledMusic(theme: RecapTheme): MusicSource = MusicSource.Bundled(bundledTracks.first { it.theme == theme }.rawResId)
}
