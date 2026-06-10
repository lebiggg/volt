package com.tonnomdeved.volt.ui.screens.hibernate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tonnomdeved.volt.VoltApplication
import com.tonnomdeved.volt.data.VoltStateBus
import com.tonnomdeved.volt.data.hibernation.HibernationLevel
import com.tonnomdeved.volt.data.hibernation.HibernationPolicy
import com.tonnomdeved.volt.data.hibernation.HibernationResult
import com.tonnomdeved.volt.data.hibernation.SavingsEstimator
import com.tonnomdeved.volt.data.hibernation.nocivity.NocivityBreakdown
import com.tonnomdeved.volt.data.hibernation.shizuku.ShizukuGateway
import com.tonnomdeved.volt.data.hibernation.whitelist.WhitelistReason
import com.tonnomdeved.volt.ui.screens.apps.AppInfo
import com.tonnomdeved.volt.ui.screens.apps.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel de l'écran Hibernate.
 *
 * Charge la liste des apps installées, calcule leur score de nocivité,
 * les croise avec la politique persistée et avec la whitelist, et
 * expose le tout sous forme de [StateFlow] consommables par Compose.
 *
 * Performance : chargement initial ~3-5s pour ~80 apps (queries
 * UsageStatsManager + NetworkStatsManager par app). Le résultat est
 * mémorisé jusqu'au prochain [loadAll].
 */
class HibernateViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as VoltApplication).container
    private val controller = container.hibernationController
    private val whitelist = container.whitelistResolver
    private val scorer = container.nocivityScorer
    private val shizuku = container.shizukuGateway
    private val appRepository = AppRepository(application)

    // ---------- État UI brut ---------- //
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _items = MutableStateFlow<List<AppHibernationItem>>(emptyList())
    val items: StateFlow<List<AppHibernationItem>> = _items.asStateFlow()

    private val _filter = MutableStateFlow(Filter.ALL)
    val filter: StateFlow<Filter> = _filter.asStateFlow()

    private val _sort = MutableStateFlow(Sort.SCORE_DESC)
    val sort: StateFlow<Sort> = _sort.asStateFlow()

    private val _shizukuAvailability = MutableStateFlow(ShizukuGateway.Availability.NOT_INSTALLED)
    val shizukuAvailability: StateFlow<ShizukuGateway.Availability> = _shizukuAvailability.asStateFlow()

    val hibernatedCount: StateFlow<Int> = VoltStateBus.hibernatedAppsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ---------- Dérivés combinés ---------- //
    val visibleItems: StateFlow<List<AppHibernationItem>> =
        combine(items, filter, sort) { list, f, s -> applyFilterSort(list, f, s) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val suggestions: StateFlow<List<AppHibernationItem>> =
        items.map { list -> deriveSuggestions(list) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Estimation d'économie batterie, recalculée à chaque changement de politique. */
    val savings: StateFlow<SavingsEstimator.Savings> =
        items.map { list ->
            SavingsEstimator.estimate(
                list.filter { it.currentLevel.isActive() }
                    .map { it.currentLevel to it.score }
            )
        }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            SavingsEstimator.estimate(emptyList())
        )

    init {
        refreshShizukuAvailability()
        loadAll()
    }

    // ============================================================== //
    // Chargement
    // ============================================================== //
    fun loadAll() {
        viewModelScope.launch {
            _loading.value = true
            val apps = appRepository.loadInstalledApps()
            val policies = controller.policies.firstOrNull().orEmpty()

            val items = withContext(Dispatchers.Default) {
                apps.map { info ->
                    val policy = policies.firstOrNull { it.packageName == info.packageName }
                    val score = scorer.scoreOf(info.packageName)
                    val protection = whitelist.isProtected(
                        info.packageName,
                        userPinned = policy?.userPinned == true,
                        userForceHibernate = policy?.userForceHibernate == true
                    )
                    AppHibernationItem(
                        app = info,
                        currentLevel = policy?.level ?: HibernationLevel.OFF,
                        score = score,
                        protection = protection,
                        userPinned = policy?.userPinned == true,
                        userForceHibernate = policy?.userForceHibernate == true
                    )
                }
            }
            _items.value = items
            _loading.value = false
        }
    }

    fun refreshShizukuAvailability() {
        _shizukuAvailability.value = shizuku.checkAvailability()
    }

    fun setFilter(f: Filter) { _filter.value = f }
    fun setSort(s: Sort) { _sort.value = s }

    // ============================================================== //
    // Actions
    // ============================================================== //

    /**
     * Applique le niveau demandé. Le callback reçoit le [HibernationResult]
     * pour que la UI puisse afficher un Snackbar contextuel.
     */
    fun applyLevel(
        packageName: String,
        level: HibernationLevel,
        onResult: (HibernationResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = controller.hibernate(packageName, level)
            if (result is HibernationResult.Success || result is HibernationResult.Unchanged) {
                replaceItem(packageName) { it.copy(currentLevel = level) }
            }
            onResult(result)
        }
    }

    /**
     * Applique les niveaux recommandés sur les 5 suggestions, avec fallback
     * SOFT si Shizuku indisponible pour MEDIUM/HARD.
     */
    fun applySuggestedAll(onSummary: (applied: Int, blocked: Int) -> Unit) {
        viewModelScope.launch {
            var applied = 0
            var blocked = 0
            suggestions.value.forEach { item ->
                val recommended = recommendedLevelFor(item.score.total)
                val res = controller.hibernate(item.app.packageName, recommended)
                when (res) {
                    is HibernationResult.Success, is HibernationResult.Unchanged -> applied++
                    is HibernationResult.ShizukuUnavailable -> {
                        if (controller.hibernate(item.app.packageName, HibernationLevel.SOFT)
                                is HibernationResult.Success) applied++
                    }
                    is HibernationResult.Blocked -> blocked++
                    is HibernationResult.Failed  -> { /* loggé en amont */ }
                }
            }
            loadAll()
            onSummary(applied, blocked)
        }
    }

    /** Réveille toutes les apps hibernées d'un coup (panic button). */
    fun wakeAll(onDone: (woke: Int) -> Unit) {
        viewModelScope.launch {
            val woke = controller.wakeAll()
            loadAll()
            onDone(woke)
        }
    }

    /** Pin = userForceProtect. Mutuellement exclusif avec userForceHibernate. */
    fun togglePin(packageName: String, pinned: Boolean) {
        viewModelScope.launch {
            val existing = controller.getPolicy(packageName)
            val now = System.currentTimeMillis()
            val updated = (existing ?: HibernationPolicy(
                packageName = packageName,
                level = HibernationLevel.OFF,
                createdAt = now
            )).copy(
                userPinned = pinned,
                userForceHibernate = if (pinned) false else existing?.userForceHibernate == true,
                lastAppliedAt = if (existing == null) now else existing.lastAppliedAt
            )
            container.hibernationRepository.upsert(updated)
            replaceItem(packageName) {
                it.copy(
                    userPinned = pinned,
                    protection = if (pinned) WhitelistReason.UserPinned else null
                )
            }
        }
    }

    // ============================================================== //
    // Helpers internes
    // ============================================================== //

    private fun replaceItem(
        packageName: String,
        transform: (AppHibernationItem) -> AppHibernationItem
    ) {
        _items.value = _items.value.map { item ->
            if (item.app.packageName == packageName) transform(item) else item
        }
    }

    private fun applyFilterSort(
        list: List<AppHibernationItem>,
        f: Filter,
        s: Sort
    ): List<AppHibernationItem> = list.asSequence()
        .filter { item ->
            when (f) {
                Filter.ALL         -> true
                Filter.SUGGESTED   -> item.protection == null &&
                                      item.currentLevel == HibernationLevel.OFF &&
                                      item.score.total >= 60
                Filter.HIBERNATED  -> item.currentLevel.isActive()
                Filter.PROTECTED   -> item.protection != null
            }
        }
        .sortedWith(when (s) {
            Sort.SCORE_DESC    -> compareByDescending<AppHibernationItem> { it.score.total }
            Sort.LAST_USED_ASC -> compareBy<AppHibernationItem> { it.score.daysSinceLastUse }
            Sort.NAME_ASC      -> compareBy<AppHibernationItem> { it.app.label.lowercase() }
        })
        .toList()

    private fun deriveSuggestions(list: List<AppHibernationItem>): List<AppHibernationItem> =
        list.asSequence()
            .filter { it.protection == null && it.currentLevel == HibernationLevel.OFF }
            .filter { it.score.total >= 50 }
            .sortedByDescending { it.score.total }
            .take(5)
            .toList()

    private fun recommendedLevelFor(score: Int): HibernationLevel = when {
        score >= 85 -> HibernationLevel.HARD
        score >= 60 -> HibernationLevel.MEDIUM
        score >= 30 -> HibernationLevel.SOFT
        else        -> HibernationLevel.OFF
    }

    // ============================================================== //
    // Types
    // ============================================================== //
    data class AppHibernationItem(
        val app: AppInfo,
        val currentLevel: HibernationLevel,
        val score: NocivityBreakdown,
        val protection: WhitelistReason?,
        val userPinned: Boolean,
        val userForceHibernate: Boolean
    ) {
        val isProtected: Boolean get() = protection != null
    }

    enum class Filter { ALL, SUGGESTED, HIBERNATED, PROTECTED }
    enum class Sort   { SCORE_DESC, LAST_USED_ASC, NAME_ASC }
}
