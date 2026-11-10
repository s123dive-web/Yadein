package com.yaadein.feature.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.entity.FaceEntity
import com.yaadein.data.faces.FaceSuggester
import com.yaadein.domain.model.Person
import com.yaadein.domain.repository.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One detected face: where it is on which photo (normalised 0..1 box) plus the photo's pixel size for the crop. */
data class FaceSample(
    val faceId: Long,
    val mediaId: Long,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val mediaWidth: Int,
    val mediaHeight: Int,
)

/** Faces that look like the same unknown person. */
data class FaceGroup(val faces: List<FaceSample>) {
    val faceIds: List<Long> get() = faces.map { it.faceId }
    val photoCount: Int get() = faces.map { it.mediaId }.distinct().size
}

/** "Named N faces as X": shown in the undo snackbar. */
data class NamedFaces(val faceIds: List<Long>, val person: Person)

data class WhoIsThisUiState(
    val loading: Boolean = true,
    /** The group being named; null when every face has a name (or every group was skipped). */
    val group: FaceGroup? = null,
    /** Groups still waiting after the current one. */
    val remaining: Int = 0,
    val people: List<Person> = emptyList(),
    val selectedPersonId: Long? = null,
    val working: Boolean = false,
    /** The last naming, until the snackbar goes away. */
    val justNamed: NamedFaces? = null,
)

@HiltViewModel
class WhoIsThisViewModel @Inject constructor(
    private val suggester: FaceSuggester,
    private val db: AppDatabase,
    private val personRepository: PersonRepository,
) : ViewModel() {
    private data class Session(
        val loading: Boolean = true,
        val groups: List<FaceGroup> = emptyList(),
        val index: Int = 0,
        val selectedPersonId: Long? = null,
        val working: Boolean = false,
        val justNamed: NamedFaces? = null,
    )

    private val session = MutableStateFlow(Session())

    /** Groups the user skipped this session (by face id) are not shown again after a reload. */
    private val skipped = HashSet<Long>()

    /** Faces the user split off ("Not the same person"); after a reload they come back as groups of their own. */
    private val split = LinkedHashSet<Long>()

    val uiState: StateFlow<WhoIsThisUiState> = combine(session, personRepository.observeAll()) { s, people ->
        WhoIsThisUiState(
            loading = s.loading,
            group = s.groups.getOrNull(s.index),
            remaining = (s.groups.size - s.index - 1).coerceAtLeast(0),
            people = people,
            selectedPersonId = s.selectedPersonId?.takeIf { id -> people.any { it.id == id } },
            working = s.working,
            justNamed = s.justNamed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhoIsThisUiState())

    init {
        reload()
    }

    fun selectPerson(personId: Long?) {
        session.update { it.copy(selectedPersonId = if (it.selectedPersonId == personId) null else personId) }
    }

    /** Names every face of the current group; the snackbar offers to undo. */
    fun nameThem() {
        val s = session.value
        val group = s.groups.getOrNull(s.index) ?: return
        val personId = s.selectedPersonId ?: return
        if (s.working) return
        viewModelScope.launch {
            session.update { it.copy(working = true) }
            val person = personRepository.get(personId)
            runCatching { suggester.confirmAll(group.faceIds, personId) }
            session.update { it.copy(selectedPersonId = null, justNamed = person?.let { p -> NamedFaces(group.faceIds, p) }) }
            reload()
        }
    }

    /** Undo of the last naming: the faces become unknown again. */
    fun undoLastNaming() {
        val named = session.value.justNamed ?: return
        viewModelScope.launch {
            session.update { it.copy(working = true, justNamed = null) }
            named.faceIds.forEach { runCatching { suggester.reject(it, named.person.id) } }
            reload()
        }
    }

    fun snackbarShown() {
        session.update { it.copy(justNamed = null) }
    }

    /** The first face is not the same person as the rest: it waits as a group of its own at the end. */
    fun notTheSamePerson() {
        session.update { s ->
            val group = s.groups.getOrNull(s.index) ?: return@update s
            if (group.faces.size < 2) return@update s
            val first = group.faces.first()
            split += first.faceId
            val groups = s.groups.toMutableList()
            groups[s.index] = FaceGroup(group.faces.drop(1))
            groups += FaceGroup(listOf(first))
            s.copy(groups = groups)
        }
    }

    fun skip() {
        session.update { s ->
            val group = s.groups.getOrNull(s.index) ?: return@update s
            skipped += group.faceIds
            s.copy(index = s.index + 1, selectedPersonId = null)
        }
    }

    private fun reload() {
        viewModelScope.launch {
            val raw = runCatching { suggester.unknownGroups() }.getOrDefault(emptyList())
            val groups = arrange(raw)
            session.update { it.copy(loading = false, working = false, groups = groups, index = 0) }
        }
    }

    private suspend fun arrange(raw: List<List<FaceEntity>>): List<FaceGroup> {
        val sizes = HashMap<Long, Pair<Int, Int>>()
        suspend fun sample(f: FaceEntity): FaceSample {
            val size = sizes.getOrPut(f.mediaId) {
                val m = db.mediaDao().get(f.mediaId)
                (m?.width ?: 0) to (m?.height ?: 0)
            }
            return FaceSample(f.id, f.mediaId, f.boxLeft, f.boxTop, f.boxRight, f.boxBottom, size.first, size.second)
        }
        val groups = ArrayList<FaceGroup>()
        val singles = ArrayList<FaceGroup>()
        for (faces in raw) {
            if (faces.isNotEmpty() && faces.all { it.id in skipped }) continue
            val kept = faces.filter { it.id !in split }
            if (kept.isNotEmpty()) groups += FaceGroup(kept.map { sample(it) })
            faces.filter { it.id in split }.forEach { singles += FaceGroup(listOf(sample(it))) }
        }
        return groups + singles
    }
}
