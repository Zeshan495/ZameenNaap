package pk.zameennaap

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Saves measured fields on the phone (no internet, no account). */
class FieldStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("fields", Context.MODE_PRIVATE)

    data class Field(
        val name: String,
        val date: Long,
        val sqft: Double,
        val method: String,          // "GPS" or "Manual"
        val points: List<LatLon>
    )

    fun all(): List<Field> {
        val arr = JSONArray(prefs.getString("list", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val pts = o.optJSONArray("pts") ?: JSONArray()
            Field(
                o.getString("name"), o.getLong("date"), o.getDouble("sqft"), o.getString("method"),
                (0 until pts.length()).map { j ->
                    val p = pts.getJSONArray(j); LatLon(p.getDouble(0), p.getDouble(1))
                }
            )
        }
    }

    private fun write(list: List<Field>) {
        val arr = JSONArray()
        for (f in list) {
            val pts = JSONArray()
            for (p in f.points) pts.put(JSONArray().put(p.lat).put(p.lon))
            arr.put(JSONObject().put("name", f.name).put("date", f.date).put("sqft", f.sqft)
                .put("method", f.method).put("pts", pts))
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    fun add(f: Field) = write(all() + f)
    fun delete(index: Int) = write(all().filterIndexed { i, _ -> i != index })
}
