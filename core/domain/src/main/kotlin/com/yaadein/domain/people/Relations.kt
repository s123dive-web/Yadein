package com.yaadein.domain.people

import com.yaadein.domain.model.Relation

/** Which side of the family a relative is on; drives colours on the relationships map and Together's grouping. */
enum class KinSide { MATERNAL, PATERNAL, OWN, IN_LAW, NONE }

/** The canonical relationship behind a kinship term, independent of language. */
enum class Kin(val side: KinSide, val coarse: Relation = Relation.FAMILY) {
    MOTHER(KinSide.OWN), FATHER(KinSide.OWN),
    PATERNAL_GRANDMOTHER(KinSide.PATERNAL), PATERNAL_GRANDFATHER(KinSide.PATERNAL),
    MATERNAL_GRANDMOTHER(KinSide.MATERNAL), MATERNAL_GRANDFATHER(KinSide.MATERNAL),
    MATERNAL_UNCLE(KinSide.MATERNAL), MATERNAL_UNCLES_WIFE(KinSide.MATERNAL), MOTHERS_SISTER(KinSide.MATERNAL),
    FATHERS_BROTHER(KinSide.PATERNAL), FATHERS_BROTHERS_WIFE(KinSide.PATERNAL), FATHERS_SISTER(KinSide.PATERNAL),
    ELDER_BROTHER(KinSide.OWN), ELDER_SISTER(KinSide.OWN), YOUNGER_BROTHER(KinSide.OWN), YOUNGER_SISTER(KinSide.OWN),
    SON(KinSide.OWN), DAUGHTER(KinSide.OWN), SPOUSE(KinSide.OWN), GRANDCHILD(KinSide.OWN),
    SPOUSES_MOTHER(KinSide.IN_LAW), SPOUSES_FATHER(KinSide.IN_LAW),
    FRIEND(KinSide.NONE, Relation.FRIEND), COLLEAGUE(KinSide.NONE, Relation.COLLEAGUE), OTHER(KinSide.NONE, Relation.OTHER),
}

/**
 * The words Indian families actually use. Each term maps to one [Kin]; the UI shows the term the user picked
 * (translated per locale through string resources keyed by [name]) everywhere the person appears.
 * Stored in `persons.kin_term` by [name], so renaming a label never touches the database.
 */
enum class KinTerm(val kin: Kin, val language: String) {
    // Marathi
    AAI(Kin.MOTHER, "mr"), BABA(Kin.FATHER, "mr"), AAJI(Kin.PATERNAL_GRANDMOTHER, "mr"), AJOBA(Kin.PATERNAL_GRANDFATHER, "mr"),
    MAMA(Kin.MATERNAL_UNCLE, "mr"), MAMI(Kin.MATERNAL_UNCLES_WIFE, "mr"), KAKA(Kin.FATHERS_BROTHER, "mr"), KAKU(Kin.FATHERS_BROTHERS_WIFE, "mr"),
    MAVSHI(Kin.MOTHERS_SISTER, "mr"), ATYA(Kin.FATHERS_SISTER, "mr"), DADA(Kin.ELDER_BROTHER, "mr"), TAI(Kin.ELDER_SISTER, "mr"),
    // Hindi
    MUMMY(Kin.MOTHER, "hi"), PAPA(Kin.FATHER, "hi"), NANA(Kin.MATERNAL_GRANDFATHER, "hi"), NANI(Kin.MATERNAL_GRANDMOTHER, "hi"),
    DADAJI(Kin.PATERNAL_GRANDFATHER, "hi"), DADI(Kin.PATERNAL_GRANDMOTHER, "hi"), CHACHA(Kin.FATHERS_BROTHER, "hi"), CHACHI(Kin.FATHERS_BROTHERS_WIFE, "hi"),
    BUA(Kin.FATHERS_SISTER, "hi"), MAUSI(Kin.MOTHERS_SISTER, "hi"), BHAIYA(Kin.ELDER_BROTHER, "hi"), DIDI(Kin.ELDER_SISTER, "hi"),
    // English equivalents
    MOTHER(Kin.MOTHER, "en"), FATHER(Kin.FATHER, "en"), GRANDMOTHER(Kin.PATERNAL_GRANDMOTHER, "en"), GRANDFATHER(Kin.PATERNAL_GRANDFATHER, "en"),
    UNCLE(Kin.FATHERS_BROTHER, "en"), AUNT(Kin.FATHERS_SISTER, "en"), BROTHER(Kin.ELDER_BROTHER, "en"), SISTER(Kin.ELDER_SISTER, "en"),
    SON(Kin.SON, "en"), DAUGHTER(Kin.DAUGHTER, "en"), SPOUSE(Kin.SPOUSE, "en"), GRANDCHILD(Kin.GRANDCHILD, "en"),
    MOTHER_IN_LAW(Kin.SPOUSES_MOTHER, "en"), FATHER_IN_LAW(Kin.SPOUSES_FATHER, "en"),
    FRIEND(Kin.FRIEND, "en"), COLLEAGUE(Kin.COLLEAGUE, "en"),
    ;

    val side: KinSide get() = kin.side

    /** The coarse FAMILY / FRIEND / COLLEAGUE / OTHER bucket the rest of the app already uses. */
    val coarse: Relation get() = kin.coarse

    companion object {
        /** Lookup by stored name; unknown or null → null (free text or a plain Relation). */
        fun fromName(name: String?): KinTerm? = name?.let { n -> entries.firstOrNull { it.name == n } }

        /** Terms shown first in the picker: Marathi and Hindi family words, then English, friends last. */
        val PICKER_ORDER: List<KinTerm> = entries.filter { it.language == "mr" } + entries.filter { it.language == "hi" } +
            entries.filter { it.language == "en" && it.kin.coarse == Relation.FAMILY } + listOf(FRIEND, COLLEAGUE)

        /** Terms that mean the same relationship (for the relationships map and Together). */
        fun sameKin(a: KinTerm, b: KinTerm): Boolean = a.kin == b.kin
    }
}
