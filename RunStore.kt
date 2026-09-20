package app.linkharvest.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Keeps the most recent runs in a small JSON file in app-private storage.
 * Writes go to a temp file first so a crash mid-write cannot corrupt existing history.
 */
class RunStore(private val file: File, private val maxRuns: Int = 20) {

    fun load(): List<Run> = try {
        if (!file.exists()) emptyList() else parse(file.readText())
    } catch (_: Exception) {
        emptyList() // Corrupt or unreadable history must never crash the app.
    }

    fun save(run: Run): List<Run> {
        val updated = (listOf(run) + load().filter { it.id != run.id }).take(maxRuns)
        write(updated)
        return updated
    }

    fun delete(id: Long): List<Run> {
        val updated = load().filter { it.id != id }
        write(updated)
        return updated
    }

    fun clear() {
        file.delete()
    }

    private fun write(runs: List<Run>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(serialize(runs))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun serialize(runs: List<Run>): String {
        val arr = JSONArray()
        for (run in runs) {
            val results = JSONObject()
            for ((platform, deals) in run.results) {
                val list = JSONArray()
                for (d in deals) {
                    list.put(
                        JSONObject().put("u", d.url).put("p", d.percent).put("c", d.computed).put("s", d.snippet)
                    )
                }
                results.put(platform, list)
            }
            val scanned = JSONObject()
            for ((platform, n) in run.scanned) scanned.put(platform, n)
            arr.put(
                JSONObject()
                    .put("id", run.id)
                    .put("query", run.query)
                    .put("ts", run.timestampMillis)
                    .put("min", run.minDiscount)
                    .put("scanned", scanned)
                    .put("results", results)
            )
        }
        return arr.toString()
    }

    private fun parse(json: String): List<Run> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)

            val resultsObj = o.getJSONObject("results")
            val results = LinkedHashMap<String, List<Deal>>()
            val resultNames = resultsObj.names()
            if (resultNames != null) {
                for (n in 0 until resultNames.length()) {
                    val key = resultNames.getString(n)
                    val list = resultsObj.getJSONArray(key)
                    results[key] = (0 until list.length()).map { j ->
                        val d = list.getJSONObject(j)
                        Deal(d.getString("u"), d.getInt("p"), d.optBoolean("c"), d.optString("s"))
                    }
                }
            }

            val scannedObj = o.optJSONObject("scanned")
            val scanned = LinkedHashMap<String, Int>()
            val scannedNames = scannedObj?.names()
            if (scannedObj != null && scannedNames != null) {
                for (n in 0 until scannedNames.length()) {
                    val key = scannedNames.getString(n)
                    scanned[key] = scannedObj.optInt(key)
                }
            }

            Run(o.getLong("id"), o.getString("query"), o.getLong("ts"), o.optInt("min", 90), scanned, results)
        }
    }
}
