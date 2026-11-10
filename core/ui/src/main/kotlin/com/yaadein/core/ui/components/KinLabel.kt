package com.yaadein.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yaadein.core.ui.R
import com.yaadein.domain.model.Person
import com.yaadein.domain.model.Relation
import com.yaadein.domain.people.KinTerm

/** The translated word for a kinship term ("Aaji", "Mama", "Mother-in-law"). Exhaustive over [KinTerm]. */
fun kinTermNameRes(term: KinTerm): Int = when (term) {
    KinTerm.AAI -> R.string.kin_AAI
    KinTerm.BABA -> R.string.kin_BABA
    KinTerm.AAJI -> R.string.kin_AAJI
    KinTerm.AJOBA -> R.string.kin_AJOBA
    KinTerm.MAMA -> R.string.kin_MAMA
    KinTerm.MAMI -> R.string.kin_MAMI
    KinTerm.KAKA -> R.string.kin_KAKA
    KinTerm.KAKU -> R.string.kin_KAKU
    KinTerm.MAVSHI -> R.string.kin_MAVSHI
    KinTerm.ATYA -> R.string.kin_ATYA
    KinTerm.DADA -> R.string.kin_DADA
    KinTerm.TAI -> R.string.kin_TAI
    KinTerm.MUMMY -> R.string.kin_MUMMY
    KinTerm.PAPA -> R.string.kin_PAPA
    KinTerm.NANA -> R.string.kin_NANA
    KinTerm.NANI -> R.string.kin_NANI
    KinTerm.DADAJI -> R.string.kin_DADAJI
    KinTerm.DADI -> R.string.kin_DADI
    KinTerm.CHACHA -> R.string.kin_CHACHA
    KinTerm.CHACHI -> R.string.kin_CHACHI
    KinTerm.BUA -> R.string.kin_BUA
    KinTerm.MAUSI -> R.string.kin_MAUSI
    KinTerm.BHAIYA -> R.string.kin_BHAIYA
    KinTerm.DIDI -> R.string.kin_DIDI
    KinTerm.MOTHER -> R.string.kin_MOTHER
    KinTerm.FATHER -> R.string.kin_FATHER
    KinTerm.GRANDMOTHER -> R.string.kin_GRANDMOTHER
    KinTerm.GRANDFATHER -> R.string.kin_GRANDFATHER
    KinTerm.UNCLE -> R.string.kin_UNCLE
    KinTerm.AUNT -> R.string.kin_AUNT
    KinTerm.BROTHER -> R.string.kin_BROTHER
    KinTerm.SISTER -> R.string.kin_SISTER
    KinTerm.SON -> R.string.kin_SON
    KinTerm.DAUGHTER -> R.string.kin_DAUGHTER
    KinTerm.SPOUSE -> R.string.kin_SPOUSE
    KinTerm.GRANDCHILD -> R.string.kin_GRANDCHILD
    KinTerm.MOTHER_IN_LAW -> R.string.kin_MOTHER_IN_LAW
    KinTerm.FATHER_IN_LAW -> R.string.kin_FATHER_IN_LAW
    KinTerm.FRIEND -> R.string.kin_FRIEND
    KinTerm.COLLEAGUE -> R.string.kin_COLLEAGUE
}

/** The coarse Family / Friend / Colleague / Other label. */
@Composable
fun relationLabel(relation: Relation): String = when (relation) {
    Relation.FAMILY -> stringResource(R.string.relation_family)
    Relation.FRIEND -> stringResource(R.string.relation_friend)
    Relation.COLLEAGUE -> stringResource(R.string.relation_colleague)
    Relation.OTHER -> stringResource(R.string.relation_other)
}

/**
 * What to call this person: the translated kin term when one is set, else the free-text relationship
 * ("Neighbour"), else null. Callers keep their layout stable when this is null.
 */
@Composable
fun kinLabel(person: Person): String? {
    val term = KinTerm.fromName(person.kinTerm)
    if (term != null) return stringResource(kinTermNameRes(term))
    return person.relationText?.trim()?.takeIf { it.isNotEmpty() }
}

/** [kinLabel] with the coarse relation as a fallback, so there is always something to show. */
@Composable
fun relationLabel(person: Person): String = kinLabel(person) ?: relationLabel(person.relation)
