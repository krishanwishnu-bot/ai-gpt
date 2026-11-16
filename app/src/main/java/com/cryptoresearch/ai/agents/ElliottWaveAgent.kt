package com.cryptoresearch.ai.agents

import com.cryptoresearch.ai.data.AgentReport
import com.cryptoresearch.ai.data.Timeframe
import com.cryptoresearch.ai.util.Fmt

/**
 * Rule-based Elliott/NEoWave research module. It reports possible structures,
 * not predictive wave counts. Rules that are not implemented are marked NOT VERIFIED.
 */
class ElliottWaveAgent : ResearchAgent {
    override val id = 8
    override val name = "Elliott Wave & NEoWave Agent"
    override val role = AgentMonitor.AGENT_INFO[7].second

    override suspend fun run(ctx: ResearchContext): AgentReport {
        ctx.running("Reviewing swing structure and wave rules")
        val details = ArrayList<String>()
        val sources = LinkedHashSet<String>()
        var checked = 0
        var possibleImpulse = 0
        var bullish = 0
        var bearish = 0

        for (tf in Timeframe.values().reversed()) {
            val snap = ctx.snapshots[tf]
            if (snap == null || !snap.ok) {
                details.add("${tf.label}: unavailable — ${snap?.note ?: ctx.seriesErrors[tf] ?: "no usable candles"}")
                continue
            }
            checked++
            val series = ctx.series[tf]?.data
            if (series != null) sources.add(series.source)
            val wave = snap.elliott
            details.add("${tf.label}: ${wave.label}. ${wave.detail}")
            val label = wave.label.lowercase()
            if (label.contains("possible 5-wave impulse")) {
                possibleImpulse++
                if (label.contains("up")) bullish++ else if (label.contains("down")) bearish++
            }
            val support = snap.supports.firstOrNull()?.price
            val resistance = snap.resistances.firstOrNull()?.price
            if (support != null || resistance != null) {
                details.add("    Nearby structure: support ${support?.let { Fmt.price(it) } ?: "n/a"} · resistance ${resistance?.let { Fmt.price(it) } ?: "n/a"}")
            }
            if (series?.approximate == true || snap.approximate) {
                details.add("    Warning: approximate candle data lowers wave-count reliability.")
            }
        }

        details.add("Fibonacci context: retracement and extension levels require a stable, confirmed swing pair; no automatic wave target is asserted.")
        details.add("NEoWave validation: alternation, time/price proportionality and complex-correction rules are not fully implemented; these checks are NOT VERIFIED.")
        details.add("Invalidation: a confirmed wave count needs a rule-specific invalidation level. No invalidation is issued when the swing structure is ambiguous.")
        details.add("Method: existing fractal swing pivots and basic impulse constraints are heuristic; Elliott wave interpretation is subjective and remains low confidence.")

        val summary = when {
            checked == 0 -> "Wave analysis unavailable: no usable timeframe candles."
            possibleImpulse == 0 -> "Reviewed $checked timeframe(s); no basic 5-wave impulse pattern passed heuristic checks."
            else -> "Reviewed $checked timeframe(s); possible impulse patterns: $possibleImpulse (up $bullish, down $bearish), low confidence."
        }
        return AgentReport(id, name, summary, details, sources.toList())
    }
}
