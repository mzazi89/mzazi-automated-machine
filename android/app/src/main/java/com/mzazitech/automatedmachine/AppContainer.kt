package com.mzazitech.automatedmachine

import android.content.Context
import com.mzazitech.automatedmachine.data.local.MzaziDatabase
import com.mzazitech.automatedmachine.data.remote.ApiFactory
import com.mzazitech.automatedmachine.data.repository.AuditRepository
import com.mzazitech.automatedmachine.data.repository.AuthRepository
import com.mzazitech.automatedmachine.data.repository.BundleRepository
import com.mzazitech.automatedmachine.data.repository.SettingsRepository
import com.mzazitech.automatedmachine.data.repository.SyncRepository
import com.mzazitech.automatedmachine.data.repository.TransactionRepository
import com.mzazitech.automatedmachine.data.repository.UssdJobRunner
import com.mzazitech.automatedmachine.data.repository.WorkflowRepository
import com.mzazitech.automatedmachine.data.session.SecureStore
import com.mzazitech.automatedmachine.data.session.SessionManager
import com.mzazitech.automatedmachine.data.ussd.AndroidTelephonyUssdExecutor
import com.mzazitech.automatedmachine.data.ussd.SimulatorUssdExecutor

/**
 * Manual dependency container.
 *
 * A hand-written container is used instead of an annotation-processor DI framework so
 * the release build stays small, fast to compile and easy to audit — every dependency
 * is visible in one file.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val database: MzaziDatabase = MzaziDatabase.get(appContext)

    val secureStore = SecureStore(appContext)
    val session = SessionManager(secureStore)

    val api = ApiFactory.create(session)

    val settings = SettingsRepository(database.settingsDao())
    val audit = AuditRepository(database.auditLogDao())
    val bundles = BundleRepository(database.bundleDao())
    val workflows = WorkflowRepository(database.workflowDao())
    val transactions = TransactionRepository(
        transactionDao = database.transactionDao(),
        jobDao = database.ussdJobDao(),
        api = api,
        session = session,
        settings = settings
    )
    val auth = AuthRepository(api, session, audit)
    val sync = SyncRepository(api, session, bundles, workflows, audit)

    val simulator = SimulatorUssdExecutor()
    val telephony = AndroidTelephonyUssdExecutor(appContext)

    val jobRunner = UssdJobRunner(
        jobDao = database.ussdJobDao(),
        transactionDao = database.transactionDao(),
        workflowRepository = workflows,
        settings = settings,
        realExecutor = telephony,
        simExecutor = simulator,
        audit = audit
    )

    /** Idempotent first-run setup; safe to call on every process start. */
    suspend fun bootstrap() {
        runCatching { bundles.ensureDefaults() }
        runCatching { workflows.ensureDefaults() }
    }
}
