package com.yaadein.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yaadein.core.ui.R
import com.yaadein.domain.relive.Festival

/** The string resource naming [f] (`festival_<key>`); exhaustive, so adding a festival without a string fails to compile. */
fun festivalNameRes(f: Festival): Int = when (f) {
    Festival.DIWALI -> R.string.festival_diwali
    Festival.HOLI -> R.string.festival_holi
    Festival.GANESH_CHATURTHI -> R.string.festival_ganesh_chaturthi
    Festival.DUSSEHRA -> R.string.festival_dussehra
    Festival.RAKSHA_BANDHAN -> R.string.festival_raksha_bandhan
    Festival.JANMASHTAMI -> R.string.festival_janmashtami
    Festival.GUDI_PADWA -> R.string.festival_gudi_padwa
    Festival.MAKAR_SANKRANTI -> R.string.festival_makar_sankranti
    Festival.EID_AL_FITR -> R.string.festival_eid_al_fitr
    Festival.EID_AL_ADHA -> R.string.festival_eid_al_adha
    Festival.GURU_NANAK_JAYANTI -> R.string.festival_guru_nanak_jayanti
    Festival.CHRISTMAS -> R.string.festival_christmas
    Festival.NEW_YEAR -> R.string.festival_new_year
    Festival.INDEPENDENCE_DAY -> R.string.festival_independence_day
    Festival.REPUBLIC_DAY -> R.string.festival_republic_day
}

/** The localised name of [f]. */
@Composable
fun festivalName(f: Festival): String = stringResource(festivalNameRes(f))
