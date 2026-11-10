package com.yaadein.feature.people

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.yaadein.domain.people.KinSide
import com.yaadein.domain.people.KinTerm
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yaadein.core.ui.components.EmptyState
import com.yaadein.core.ui.illustrations.IllustrationKind
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.model.Person
import com.yaadein.domain.repository.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.sqrt
import com.yaadein.core.ui.R as CoreR

data class GraphNode(val person: Person, val weight: Int, var x: Float, var y: Float)
data class GraphEdge(val a: Long, val b: Long, val weight: Int)

data class RelationshipsUiState(
    val loading: Boolean = true,
    val nodes: List<GraphNode> = emptyList(),
    val edges: List<GraphEdge> = emptyList(),
)

@HiltViewModel
class RelationshipsViewModel @Inject constructor(private val personRepository: PersonRepository) : ViewModel() {
    private val _state = MutableStateFlow(RelationshipsUiState())
    val state: StateFlow<RelationshipsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val people = personRepository.observeAll().first()
            val counts = personRepository.memoryCounts()
            val pairs = personRepository.coAppearances()
            val nodes = people.filter { (counts[it.id] ?: 0) > 0 }.map { GraphNode(it, counts[it.id] ?: 0, 0f, 0f) }
            val ids = nodes.map { it.person.id }.toSet()
            val edges = pairs.filter { it.first in ids && it.second in ids }.map { GraphEdge(it.first, it.second, it.third) }
            _state.value = RelationshipsUiState(loading = false, nodes = layout(nodes, edges), edges = edges)
        }
    }

    companion object {
        /** Deterministic force-directed layout in unit space [0,1]² (Fruchterman–Reingold style, fixed iterations). */
        fun layout(nodes: List<GraphNode>, edges: List<GraphEdge>, iterations: Int = 300): List<GraphNode> {
            if (nodes.isEmpty()) return nodes
            val n = nodes.size
            // Seed on a circle so the result is stable.
            nodes.forEachIndexed { i, node ->
                val a = 2 * Math.PI * i / n
                node.x = 0.5f + 0.35f * Math.cos(a).toFloat()
                node.y = 0.5f + 0.35f * Math.sin(a).toFloat()
            }
            val index = nodes.withIndex().associate { it.value.person.id to it.index }
            val k = 0.9f / sqrt(n.toFloat())
            val maxWeight = max(1, edges.maxOfOrNull { it.weight } ?: 1)
            var temp = 0.1f
            val dx = FloatArray(n); val dy = FloatArray(n)
            repeat(iterations) {
                dx.fill(0f); dy.fill(0f)
                for (i in 0 until n) for (j in i + 1 until n) {
                    var ddx = nodes[i].x - nodes[j].x; var ddy = nodes[i].y - nodes[j].y
                    var d = sqrt(ddx * ddx + ddy * ddy); if (d < 1e-3f) { ddx = 1e-3f; d = 1e-3f }
                    val f = k * k / d
                    dx[i] += ddx / d * f; dy[i] += ddy / d * f; dx[j] -= ddx / d * f; dy[j] -= ddy / d * f
                }
                for (e in edges) {
                    val i = index[e.a] ?: continue; val j = index[e.b] ?: continue
                    val ddx = nodes[i].x - nodes[j].x; val ddy = nodes[i].y - nodes[j].y
                    val d = max(1e-3f, sqrt(ddx * ddx + ddy * ddy))
                    val f = d * d / k * (0.3f + 0.7f * e.weight / maxWeight)
                    dx[i] -= ddx / d * f; dy[i] -= ddy / d * f; dx[j] += ddx / d * f; dy[j] += ddy / d * f
                }
                for (i in 0 until n) {
                    // gravity to centre
                    dx[i] += (0.5f - nodes[i].x) * 0.05f; dy[i] += (0.5f - nodes[i].y) * 0.05f
                    val d = max(1e-3f, sqrt(dx[i] * dx[i] + dy[i] * dy[i]))
                    val step = minOf(d, temp)
                    nodes[i].x = (nodes[i].x + dx[i] / d * step).coerceIn(0.08f, 0.92f)
                    nodes[i].y = (nodes[i].y + dy[i] / d * step).coerceIn(0.08f, 0.92f)
                }
                temp *= 0.985f
            }
            return nodes
        }
    }
}

@Composable
fun RelationshipsRoute(onBack: () -> Unit, onOpenPerson: (Long) -> Unit, modifier: Modifier = Modifier, viewModel: RelationshipsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RelationshipsScreen(state = state, onBack = onBack, onOpenPerson = onOpenPerson, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelationshipsScreen(state: RelationshipsUiState, onBack: () -> Unit, onOpenPerson: (Long) -> Unit, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.relationships_title), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        if (!state.loading && state.edges.isEmpty()) {
            EmptyState(illustration = IllustrationKind.PEOPLE_CIRCLE, title = stringResource(R.string.relationships_empty_title), message = stringResource(R.string.relationships_empty_message), modifier = Modifier.fillMaxSize())
            return
        }
        Text(stringResource(R.string.relationships_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = spacing.gutter))
        KinSideLegend(modifier = Modifier.padding(horizontal = spacing.gutter, vertical = spacing.sm))
        BubbleGraph(state = state, onOpenPerson = onOpenPerson, modifier = Modifier.fillMaxSize().padding(spacing.md))
    }
}

@Composable
fun BubbleGraph(state: RelationshipsUiState, onOpenPerson: (Long) -> Unit, modifier: Modifier = Modifier) {
    val palette = Yaadein.colors.avatarPalette
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val sideColors = kinSideColors()
    val textSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { 12.sp.toPx() }
    val maxWeight = max(1, state.nodes.maxOfOrNull { it.weight } ?: 1)
    val maxEdge = max(1, state.edges.maxOfOrNull { it.weight } ?: 1)
    val description = stringResource(R.string.relationships_title) + ": " + state.nodes.joinToString { "${it.person.displayName} (${it.weight})" }
    val labelPaint = remember(labelColor, textSizePx) {
        android.graphics.Paint().apply { color = labelColor.toArgb(); textSize = textSizePx; textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true }
    }
    Box(modifier = modifier.semantics { contentDescription = description }) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.nodes) {
                    detectTapGestures { tap ->
                        val w = size.width.toFloat(); val h = size.height.toFloat()
                        state.nodes.minByOrNull { n -> val dx = n.x * w - tap.x; val dy = n.y * h - tap.y; dx * dx + dy * dy }
                            ?.let { n ->
                                val r = radiusFor(n.weight, maxWeight, minOf(w, h))
                                val dx = n.x * w - tap.x; val dy = n.y * h - tap.y
                                if (dx * dx + dy * dy <= r * r * 1.4f) onOpenPerson(n.person.id)
                            }
                    }
                },
        ) {
            val w = size.width; val h = size.height
            val byId = state.nodes.associateBy { it.person.id }
            state.edges.forEach { e ->
                val a = byId[e.a] ?: return@forEach; val b = byId[e.b] ?: return@forEach
                drawLine(lineColor, Offset(a.x * w, a.y * h), Offset(b.x * w, b.y * h), strokeWidth = 1.5f + 10f * e.weight / maxEdge)
            }
            state.nodes.forEach { n ->
                val r = radiusFor(n.weight, maxWeight, minOf(w, h))
                val c = Offset(n.x * w, n.y * h)
                drawCircle(palette[Math.floorMod(n.person.avatarColorIndex, palette.size)], radius = r, center = c)
                val side = KinTerm.fromName(n.person.kinTerm)?.side ?: KinSide.NONE
                val ring = sideColors[side] ?: surface
                drawCircle(ring, radius = r, center = c, style = Stroke(width = if (side == KinSide.NONE) 3f else 6f))
                drawContext.canvas.nativeCanvas.drawText(n.person.displayName, c.x, c.y + r + textSizePx * 1.1f, labelPaint)
                val initialsPaint = android.graphics.Paint(labelPaint).apply { color = Color.White.toArgb(); textSize = r * 0.8f }
                drawContext.canvas.nativeCanvas.drawText(n.person.initials, c.x, c.y + r * 0.3f, initialsPaint)
            }
        }
    }
}

private fun radiusFor(weight: Int, maxWeight: Int, minDim: Float): Float = minDim * (0.035f + 0.07f * weight / maxWeight)

/** Ring colour per side of the family; [KinSide.NONE] (friends, colleagues, no kin term) keeps the plain surface ring. */
@Composable
private fun kinSideColors(): Map<KinSide, Color> {
    val scheme = MaterialTheme.colorScheme
    return mapOf(
        KinSide.MATERNAL to scheme.primary,
        KinSide.PATERNAL to scheme.secondary,
        KinSide.OWN to scheme.tertiary,
        KinSide.IN_LAW to scheme.outline,
    )
}

@Composable
private fun kinSideLabel(side: KinSide): String? = when (side) {
    KinSide.MATERNAL -> stringResource(R.string.kin_side_maternal)
    KinSide.PATERNAL -> stringResource(R.string.kin_side_paternal)
    KinSide.OWN -> stringResource(R.string.kin_side_own)
    KinSide.IN_LAW -> stringResource(R.string.kin_side_in_law)
    KinSide.NONE -> null
}

/** Tiny legend: a coloured dot and the side name for each family side. */
@Composable
private fun KinSideLegend(modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    val colors = kinSideColors()
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        KinSide.entries.forEach { side ->
            val label = kinSideLabel(side) ?: return@forEach
            val color = colors[side] ?: return@forEach
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Box(modifier = Modifier.size(Yaadein.sizes.timelineDot).clip(CircleShape).background(color))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

