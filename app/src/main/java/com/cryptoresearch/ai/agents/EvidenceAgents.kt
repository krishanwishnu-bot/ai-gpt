package com.cryptoresearch.ai.agents

import com.cryptoresearch.ai.analysis.Signal
import com.cryptoresearch.ai.data.AgentReport
import com.cryptoresearch.ai.data.Evidence
import com.cryptoresearch.ai.data.Strength
import com.cryptoresearch.ai.data.Timeframe

internal fun strengthOf(score: Int, otherScore: Int): Strength = when {
    score <= 0 -> Strength.NONE
    score >= 8 && score >= otherScore * 2 -> Strength.STRONG
    score >= 4 && score > otherScore -> Strength.MEDIUM
    else -> Strength.WEAK
}

private fun evidenceReport(id: Int, name: String, ctx: ResearchContext, dir: Int): AgentReport {
    val word = if (dir > 0) "bullish" else "bearish"
    val mine = ctx.signals.filter { it.direction == dir }
    val other = ctx.signals.filter { it.direction == -dir }
    val myScore = mine.sumOf { it.weight }
    val otherScore = other.sumOf { it.weight }
    val strength = strengthOf(myScore, otherScore)

    val details = ArrayList<String>()
    if (mine.isEmpty()) {
        details.add("No $word evidence was found in the available data.")
    } else {
        details.add("Found ${mine.size} $word indicators (weighted score $myScore vs $otherScore for the opposite side).")
        for (s in mine.sortedByDescending { it.weight }) details.add("• ${s.text}  [w${s.weight} · ${s.source}]")
    }
    val tfCount = ctx.snapshots.values.count { it.ok }
    val uncertainty = ArrayList<String>()
    if (tfCount < 4) uncertainty.add("only $tfCount of 4 timeframes had usable data")
    if (ctx.news.isEmpty()) uncertainty.add("no news data")
    if (ctx.fng == null) uncertainty.add("no Fear & Greed data")
    if (ctx.snapshots.values.any { it.ok && it.approximate }) uncertainty.add("some candles are approximated")
    if (ctx.snapshots.values.any { it.ok && it.stale }) uncertainty.add("some data is stale")
    details.add(
        "Uncertainty: " + (if (uncertainty.isEmpty()) "no major data gaps, but indicators are lagging and cannot predict future prices."
        else uncertainty.joinToString("; ") + ". Indicators are also lagging and cannot predict future prices.")
    )
    val summary = if (mine.isEmpty()) "No $word evidence found." else "${strength.label} $word evidence: ${mine.size} indicators, score $myScore (opposite $otherScore)."
    val sources = mine.map { it.source }.distinct()
    val evs = mine.map { Evidence(it.text, it.weight, it.source) }
    return AgentReport(id, name, summary, details, sources, evs)
}

// Agent 6
class BullishAgent : ResearchAgent {
    override val id = 6
    override val name = "Bullish Evidence Agent"
    override val role = AgentMonitor.AGENT_INFO[5].second
    override suspend fun run(ctx: ResearchContext): AgentReport {
        ctx.running("Collecting bullish evidence")
        return evidenceReport(id, name, ctx, 1)
    }
}

// Agent 7
class BearishAgent : ResearchAgent {
    override val id = 7
    override val name = "Bearish Evidence Agent"
    override val role = AgentMonitor.AGENT_INFO[6].second
    override suspend fun run(ctx: ResearchContext): AgentReport {
        ctx.running("Collecting bearish evidence")
        return evidenceReport(id, name, ctx, -1)
    }
}

internal fun List<Signal>.score(dir: Int): Int = filter { it.direction == dir }.sumOf { it.weight }

internal fun tfOrder(): List<Timeframe> = Timeframe.values().reversed()
