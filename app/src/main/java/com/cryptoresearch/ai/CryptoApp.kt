package com.cryptoresearch.ai

import android.app.Application
import com.cryptoresearch.ai.agents.AgentMonitor
import com.cryptoresearch.ai.agents.Orchestrator
import com.cryptoresearch.ai.data.MarketRepository
import com.cryptoresearch.ai.data.PortfolioRepository
import com.cryptoresearch.ai.data.ReportRepository
import com.cryptoresearch.ai.data.SettingsStore
import com.cryptoresearch.ai.data.db.AppDatabase
import com.cryptoresearch.ai.data.net.MarketApi
import com.cryptoresearch.ai.notify.Notifier
import com.cryptoresearch.ai.notify.Scheduler

/** Application class doubling as a tiny service locator (keeps dependencies light for low-end devices). */
class CryptoApp : Application() {
    lateinit var db: AppDatabase
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var market: MarketRepository
        private set
    lateinit var reports: ReportRepository
        private set
    lateinit var portfolio: PortfolioRepository
        private set
    lateinit var monitor: AgentMonitor
        private set
    lateinit var orchestrator: Orchestrator
        private set

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.create(this)
        settings = SettingsStore(this)
        val api = MarketApi { settings.apiKey() }
        market = MarketRepository(db, api, settings)
        reports = ReportRepository(db)
        portfolio = PortfolioRepository(db)
        monitor = AgentMonitor()
        orchestrator = Orchestrator(market, reports, settings, monitor)
        Notifier.createChannels(this)
        Scheduler.apply(this, settings.value)
    }
}
