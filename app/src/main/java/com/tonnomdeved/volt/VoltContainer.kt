package com.tonnomdeved.volt

import android.app.usage.UsageStatsManager
import android.content.Context
import com.tonnomdeved.volt.data.forensics.ForensicsAnalyzer
import com.tonnomdeved.volt.data.hibernation.AutoHibernationRunner
import com.tonnomdeved.volt.data.hibernation.HibernationController
import com.tonnomdeved.volt.data.hibernation.HibernationDecisionEngine
import com.tonnomdeved.volt.data.hibernation.HibernationRepository
import com.tonnomdeved.volt.data.hibernation.nocivity.NocivityScorer
import com.tonnomdeved.volt.data.hibernation.persistence.HibernationDatabase
import com.tonnomdeved.volt.data.hibernation.shizuku.ShizukuGateway
import com.tonnomdeved.volt.data.hibernation.whitelist.WhitelistResolver
import com.tonnomdeved.volt.data.push.PushRegistrationRepository
import com.tonnomdeved.volt.data.push.persistence.PushDatabase

/**
 * Service locator minimaliste — pas de Hilt, pas de Koin.
 *
 * Toute instanciation est `by lazy` → thread-safe et zéro coût au boot.
 *
 * **Pourquoi ce choix** :
 *  - F-Droid friendly (un plugin KSP en moins).
 *  - Petit arbre de dépendances (~10 composants).
 *  - Tests : injection manuelle d'un Context custom (Robolectric).
 */
class VoltContainer(applicationContext: Context) {

    private val appContext: Context = applicationContext.applicationContext

    // ---------- Persistance ---------- //
    val hibernationDatabase: HibernationDatabase by lazy {
        HibernationDatabase.build(appContext)
    }
    val hibernationRepository: HibernationRepository by lazy {
        HibernationRepository(hibernationDatabase.hibernationDao())
    }

    // ---------- Persistance UnifiedPush ---------- //
    val pushDatabase: PushDatabase by lazy { PushDatabase.build(appContext) }
    val pushRegistrationRepository: PushRegistrationRepository by lazy {
        PushRegistrationRepository(pushDatabase.pushRegistrationDao())
    }

    // ---------- Services système ---------- //
    private val usageStatsManager: UsageStatsManager by lazy {
        appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    }

    // ---------- Couche whitelist + shizuku ---------- //
    val whitelistResolver: WhitelistResolver by lazy { WhitelistResolver(appContext) }
    val shizukuGateway: ShizukuGateway by lazy { ShizukuGateway(appContext) }

    // ---------- Scoring + décision (P2) ---------- //
    val nocivityScorer: NocivityScorer by lazy { NocivityScorer(appContext) }
    val decisionEngine: HibernationDecisionEngine by lazy {
        HibernationDecisionEngine(nocivityScorer, whitelistResolver)
    }

    // ---------- Façade publique ---------- //
    val hibernationController: HibernationController by lazy {
        HibernationController(
            context = appContext,
            repository = hibernationRepository,
            whitelist = whitelistResolver,
            shizuku = shizukuGateway,
            usageStatsManager = usageStatsManager
        )
    }

    // ---------- Auto-hibernation (chaînon DecisionEngine → Controller) ---------- //
    val autoHibernationRunner: AutoHibernationRunner by lazy {
        AutoHibernationRunner(appContext, decisionEngine, hibernationController)
    }

    // ---------- Forensics (analyseur de réveils nocturnes) ---------- //
    val forensicsAnalyzer: ForensicsAnalyzer by lazy {
        ForensicsAnalyzer(appContext, shizukuGateway)
    }
}
