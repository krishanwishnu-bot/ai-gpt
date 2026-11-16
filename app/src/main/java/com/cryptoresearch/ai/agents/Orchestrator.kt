package com.cryptoresearch.ai.agents

import com.cryptoresearch.ai.data.AgentReport
import com.cryptoresearch.ai.data.AgentStatus
import com.cryptoresearch.ai.data.Coin
import com.cryptoresearch.ai.data.MarketRepository
import com.cryptoresearch.ai.data.ReportRepository
import com.cryptoresearch.ai.data.ResearchReport
import com.cryptoresearch.ai.data.SettingsStore
import com.cryptoresearch.ai.util.Fmt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Central orchestrator: runs the eight agents sequentially (low memory/CPU footprint) and
 * updates the [AgentMonitor]. Cancellation is cooperative: cancelling the calling coroutine
 * stops the run and marks the running agent as Idle ("Cancelled").
 */
class Orchestrator(
    private val market: MarketRepository,
    private val reportsRepo: ReportRepository,
    private val settings: SettingsStore,
    val monitor: AgentMonitor
) {
    private val agents: List<ResearchAgent> = listOf(
        MarketAgent(), TechnicalAgent(), VolumeAgent(), NewsAgent(),
        SentimentAgent(), BullishAgent(), BearishAgent(), RiskAgent()
    )

    suspend fun research(coin: Coin, save: Boolean = true): Result<ResearchReport> {
        val ctx = ResearchContext(coin, market, settings.value)
        ctx.onStatus = { id, st, msg ->
            monitor.update(id) { it.copy(status = st, processing = msg) }
        }
        monitor.resetForRun()
        var currentId = 0
        try {
            for (agent in agents) {
                currentId = agent.id
                ctx.currentAgent = agent.id
                monitor.update(agent.id) { it.copy(status = AgentStatus.RUNNING, processing = "Starting…", error = null) }
                try {
                    val r: AgentReport = agent.run(ctx)
                    ctx.reports[agent.id] = r
                    monitor.update(agent.id) {
                        it.copy(
                            status = AgentStatus.COMPLETED, lastRunAt = System.currentTimeMillis(),
                            sources = r.sources, processing = "Done for ${coin.symbol}",
                            summary = r.summary, details = r.details, error = null
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = if (e is AgentFailure) (e.message ?: "Agent failed") else Fmt.friendlyError(e)
                    monitor.update(agent.id) {
                        it.copy(
                            status = AgentStatus.FAILED, lastRunAt = System.currentTimeMillis(),
                            processing = "Failed", summary = "", details = emptyList(), error = msg
                        )
                    }
                    // Agent 8 builds the report from whatever the others produced; if it fails the run fails.
                    if (agent.id == 8) return Result.failure(Exception(msg))
                }
            }
            val report = ctx.finalReport ?: return Result.failure(Exception("No report was produced."))
            if (save) {
                try {
                    reportsRepo.save(report)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return Result.failure(Exception("Report generated but could not be saved: " + (e.message ?: "database error")))
                }
            }
            return Result.success(report)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                if (currentId != 0) monitor.update(currentId) { it.copy(status = AgentStatus.IDLE, processing = "Cancelled by user") }
            }
            throw e
        }
    }
}
