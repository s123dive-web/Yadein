package com.yaadein.domain.people

import com.yaadein.domain.model.Relation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RelationsTest {
    @Test
    fun `terms map to canonical kin with a side and a coarse relation`() {
        assertEquals(Kin.MATERNAL_UNCLE, KinTerm.MAMA.kin)
        assertEquals(KinSide.MATERNAL, KinTerm.MAMA.side)
        assertEquals(KinSide.PATERNAL, KinTerm.KAKA.side)
        assertEquals(Relation.FAMILY, KinTerm.AAJI.coarse)
        assertEquals(Relation.FRIEND, KinTerm.FRIEND.coarse)
        assertTrue(KinTerm.sameKin(KinTerm.AAJI, KinTerm.DADI))
        assertTrue(KinTerm.sameKin(KinTerm.NANI, KinTerm.NANI))
        assertTrue(!KinTerm.sameKin(KinTerm.NANI, KinTerm.AAJI))
    }

    @Test
    fun `stored names round-trip and unknown names are null`() {
        KinTerm.entries.forEach { assertEquals(it, KinTerm.fromName(it.name)) }
        assertNull(KinTerm.fromName("aaji"))
        assertNull(KinTerm.fromName(null))
    }

    @Test
    fun `the picker lists Marathi and Hindi words first and every term once`() {
        val order = KinTerm.PICKER_ORDER
        assertEquals(order.size, order.toSet().size)
        assertEquals(KinTerm.AAI, order.first())
        assertTrue(order.indexOf(KinTerm.NANI) < order.indexOf(KinTerm.MOTHER))
        assertEquals(KinTerm.COLLEAGUE, order.last())
        assertTrue(order.containsAll(KinTerm.entries.filter { it.language != "en" || it.coarse == Relation.FAMILY }))
    }
}
