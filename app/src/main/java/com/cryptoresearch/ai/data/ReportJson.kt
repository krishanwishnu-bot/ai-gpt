package com.cryptoresearch.ai.data

import org.json.JSONArray
import org.json.JSONObject

private fun List<String>.toJsonArray(): JSONArray {
    val a = JSONArray()
    for (s in this) a.put(s)
    return a
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) out.add(optString(i, ""))
    return out
}

object ReportJson {
    fun encode(r: ResearchReport): String {
        val o = JSONObject()
        o.put("coinId", r.coinId)
        o.put("symbol", r.symbol)
        o.put("name", r.name)
        o.put("createdAt", r.createdAt)
        o.put("classification", r.classification.name)
        o.put("strength", r.strength.name)
        o.put("confidencePct", r.confidencePct)
        o.put("confidenceLabel", r.confidenceLabel)
        o.put("volatility", r.volatility)
        o.put("freshness", r.freshness)
        o.put("bullScore", r.bullScore)
        o.put("bearScore", r.bearScore)
        o.put("sources", r.sources.toJsonArray())
        val secs = JSONArray()
        for (s in r.sections) {
            val so = JSONObject()
            so.put("title", s.title)
            so.put("lines", s.lines.toJsonArray())
            secs.put(so)
        }
        o.put("sections", secs)
        val ags = JSONArray()
        for (a in r.agentReports) {
            val ao = JSONObject()
            ao.put("id", a.agentId)
            ao.put("name", a.agentName)
            ao.put("summary", a.summary)
            ao.put("details", a.details.toJsonArray())
            ao.put("sources", a.sources.toJsonArray())
            ao.put("error", a.error ?: JSONObject.NULL)
            val evs = JSONArray()
            for (e in a.evidences) {
                val eo = JSONObject()
                eo.put("text", e.text)
                eo.put("weight", e.weight)
                eo.put("source", e.source)
                evs.put(eo)
            }
            ao.put("evidences", evs)
            ags.put(ao)
        }
        o.put("agents", ags)
        return o.toString()
    }

    fun decode(json: String): ResearchReport {
        val o = JSONObject(json)
        val sections = ArrayList<ReportSection>()
        val secs = o.optJSONArray("sections")
        if (secs != null) {
            for (i in 0 until secs.length()) {
                val so = secs.getJSONObject(i)
                sections.add(ReportSection(so.optString("title", ""), so.optJSONArray("lines").toStringList()))
            }
        }
        val agents = ArrayList<AgentReport>()
        val ags = o.optJSONArray("agents")
        if (ags != null) {
            for (i in 0 until ags.length()) {
                val ao = ags.getJSONObject(i)
                val evs = ArrayList<Evidence>()
                val ea = ao.optJSONArray("evidences")
                if (ea != null) {
                    for (j in 0 until ea.length()) {
                        val eo = ea.getJSONObject(j)
                        evs.add(Evidence(eo.optString("text", ""), eo.optInt("weight", 1), eo.optString("source", "")))
                    }
                }
                agents.add(
                    AgentReport(
                        agentId = ao.optInt("id", 0),
                        agentName = ao.optString("name", ""),
                        summary = ao.optString("summary", ""),
                        details = ao.optJSONArray("details").toStringList(),
                        sources = ao.optJSONArray("sources").toStringList(),
                        evidences = evs,
                        error = if (ao.isNull("error")) null else ao.optString("error")
                    )
                )
            }
        }
        return ResearchReport(
            coinId = o.optString("coinId"),
            symbol = o.optString("symbol"),
            name = o.optString("name"),
            createdAt = o.optLong("createdAt"),
            classification = Classification.fromName(o.optString("classification")),
            strength = Strength.fromName(o.optString("strength")),
            confidencePct = o.optInt("confidencePct"),
            confidenceLabel = o.optString("confidenceLabel"),
            volatility = o.optString("volatility"),
            freshness = o.optString("freshness"),
            bullScore = o.optInt("bullScore"),
            bearScore = o.optInt("bearScore"),
            sections = sections,
            sources = o.optJSONArray("sources").toStringList(),
            agentReports = agents
        )
    }
}
