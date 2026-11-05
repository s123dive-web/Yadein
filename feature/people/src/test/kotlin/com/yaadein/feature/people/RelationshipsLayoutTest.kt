package com.yaadein.feature.people

import com.yaadein.domain.model.Person
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.hypot

class RelationshipsLayoutTest {
    private fun nodes() = listOf(
        GraphNode(Person(1, "A"), 10, 0f, 0f), GraphNode(Person(2, "B"), 8, 0f, 0f),
        GraphNode(Person(3, "C"), 3, 0f, 0f), GraphNode(Person(4, "D"), 1, 0f, 0f),
    )
    private val edges = listOf(GraphEdge(1, 2, 9), GraphEdge(2, 3, 2), GraphEdge(1, 3, 1))

    @Test
    fun `layout keeps every node inside the canvas and is deterministic`() {
        val a = RelationshipsViewModel.layout(nodes(), edges)
        val b = RelationshipsViewModel.layout(nodes(), edges)
        a.forEach { assertTrue(it.x in 0.08f..0.92f && it.y in 0.08f..0.92f) }
        a.zip(b).forEach { (x, y) -> assertEquals(x.x, y.x, 1e-6f); assertEquals(x.y, y.y, 1e-6f) }
    }

    @Test
    fun `strongly connected pair ends up closer than an unconnected pair`() {
        val laid = RelationshipsViewModel.layout(nodes(), edges)
        val byId = laid.associateBy { it.person.id }
        fun d(a: Long, b: Long) = hypot(byId[a]!!.x - byId[b]!!.x, byId[a]!!.y - byId[b]!!.y)
        assertTrue(d(1, 2) < d(1, 4))
        assertTrue(d(1, 2) < d(2, 4))
    }

    @Test
    fun `empty graph is a no-op`() {
        assertTrue(RelationshipsViewModel.layout(emptyList(), emptyList()).isEmpty())
    }
}
