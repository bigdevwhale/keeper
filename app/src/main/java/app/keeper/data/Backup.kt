package app.keeper.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** Every ship, sighting and cargo in one JSON file. Nothing leaves the phone unless you export it. */
object Backup {
    private const val VERSION = 1

    fun encode(all: Everything): String = JSONObject()
        .put("app", "keeper")
        .put("version", VERSION)
        .put("ships", JSONArray(all.ships.map {
            JSONObject().put("id", it.id).put("name", it.name).put("seed", it.seed).put("shoals", it.shoals)
                .put("lifeboat", it.lifeboat).put("createdAt", it.createdAt)
        }))
        .put("sightings", JSONArray(all.sightings.map {
            JSONObject().put("id", it.id).put("shipId", it.shipId).put("at", it.at).put("text", it.text).put("weight", it.weight)
                .put("port", it.port).put("fate", it.fate).put("after", it.after).put("watchedAt", it.watchedAt)
        }))
        .put("cargo", JSONArray(all.cargo.map {
            JSONObject().put("id", it.id).put("shipId", it.shipId).put("text", it.text).put("due", it.due).put("outcome", it.outcome)
                .put("coped", it.coped).put("answeredAt", it.answeredAt).put("createdAt", it.createdAt)
        }))
        .toString(2)

    private fun JSONObject.list(key: String) = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()

    /** Throws [IllegalArgumentException] when the text is not a Keeper backup. */
    fun decode(text: String): Everything {
        val root = runCatching { JSONObject(text) }.getOrNull()
        require(root != null && root.optString("app") == "keeper") { "Not a Keeper backup" }
        return Everything(
            ships = root.list("ships").map {
                Ship(
                    id = it.getLong("id"), name = it.getString("name"), seed = it.optInt("seed"), shoals = it.optString("shoals"),
                    lifeboat = it.optString("lifeboat"), createdAt = it.optLong("createdAt"),
                )
            },
            sightings = root.list("sightings").map {
                Sighting(
                    id = it.getLong("id"), shipId = it.getLong("shipId"), at = it.getLong("at"), text = it.optString("text"),
                    weight = it.optInt("weight", 2), port = it.optString("port"), fate = it.optString("fate", Fate.PASSED.name),
                    after = it.optInt("after", -1), watchedAt = it.optLong("watchedAt"),
                )
            },
            cargo = root.list("cargo").map {
                Cargo(
                    id = it.getLong("id"), shipId = it.getLong("shipId"), text = it.optString("text"), due = it.getLong("due"),
                    outcome = it.optString("outcome", Outcome.PENDING.name), coped = it.optBoolean("coped"),
                    answeredAt = it.optLong("answeredAt"), createdAt = it.optLong("createdAt"),
                )
            },
        )
    }

    fun write(context: Context, uri: Uri, all: Everything) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(encode(all).toByteArray()) } ?: error("Can't write there")
    }

    fun read(context: Context, uri: Uri): Everything {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Can't read that")
        return decode(text)
    }
}
