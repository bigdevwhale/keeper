package app.keeper.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** How heavy a thought sits: the size of the ship it comes in. Stored as 1..4. */
enum class Tonnage(val weight: Int) {
    BOAT(1), SCHOONER(2), FRIGATE(3), GALLEON(4);

    companion object {
        fun of(weight: Int): Tonnage = entries.firstOrNull { it.weight == weight } ?: if (weight > 4) GALLEON else BOAT
    }
}

/** Where a ship sailed in from: what set the thought off. */
enum class Port { MESSAGE, MEETING, NEWS, NIGHT, QUIET, BODY, MEMORY, OTHER }

/** What became of one sighting. */
enum class Fate {
    /** Lit and let pass, there and then. */
    PASSED,

    /** Left at anchor on the roadstead until the evening watch. */
    ANCHORED,

    /** Looked at on the watch: re-weighed, or found gone. */
    WATCHED,
}

/** What a cargo turned out to be on the day it was due. */
enum class Outcome { PENDING, ARRIVED, PARTLY, EMPTY }

/**
 * A thought that keeps coming back, given a name and a ship. [seed] picks its hull, sails and flag.
 * [shoals] is the chart, one "and then?" per line, and [lifeboat] the plan at the bottom of it.
 */
@Entity(tableName = "ships")
data class Ship(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val seed: Int,
    val shoals: String = "",
    val lifeboat: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val shoalList: List<String> get() = shoals.lines().map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * One time a ship showed up: the thought in your words, how heavy it was, where it came from and what
 * you did. [after] is its weight when looked at again (after the beam, or on the watch); 0 is gone.
 */
@Entity(tableName = "sightings", indices = [Index("shipId"), Index("at")])
data class Sighting(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shipId: Long,
    val at: Long,
    val text: String,
    val weight: Int,
    /** [Port] name, or empty. */
    val port: String = "",
    /** [Fate] name. */
    val fate: String = Fate.PASSED.name,
    val after: Int = -1,
    val watchedAt: Long = 0,
) {
    val tonnage: Tonnage get() = Tonnage.of(weight)
    val fateOf: Fate get() = runCatching { Fate.valueOf(fate) }.getOrDefault(Fate.PASSED)
    val portOf: Port? get() = Port.entries.firstOrNull { it.name == port }
    val anchored: Boolean get() = fateOf == Fate.ANCHORED

    /** Weighed again after the first time. */
    val reweighed: Boolean get() = after >= 0
}

/** What a ship says it's bringing, written as a forecast with a day to check it on. */
@Entity(tableName = "cargo", indices = [Index("shipId")])
data class Cargo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shipId: Long,
    val text: String,
    /** The epoch day it's due. */
    val due: Long,
    /** [Outcome] name. */
    val outcome: String = Outcome.PENDING.name,
    /** When it did arrive: whether you got through it. */
    val coped: Boolean = false,
    val answeredAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val outcomeOf: Outcome get() = runCatching { Outcome.valueOf(outcome) }.getOrDefault(Outcome.PENDING)
    val pending: Boolean get() = outcomeOf == Outcome.PENDING
}

@Dao
abstract class KeeperDao {
    @Query("SELECT * FROM ships ORDER BY id ASC") abstract fun observeShips(): Flow<List<Ship>>
    @Query("SELECT * FROM sightings ORDER BY at DESC") abstract fun observeSightings(): Flow<List<Sighting>>
    @Query("SELECT * FROM cargo ORDER BY due ASC, id ASC") abstract fun observeCargo(): Flow<List<Cargo>>

    @Query("SELECT * FROM ships ORDER BY id ASC") abstract suspend fun ships(): List<Ship>
    @Query("SELECT * FROM sightings ORDER BY at DESC") abstract suspend fun sightings(): List<Sighting>
    @Query("SELECT * FROM cargo ORDER BY due ASC, id ASC") abstract suspend fun cargo(): List<Cargo>
    @Query("SELECT * FROM ships WHERE id = :id") abstract suspend fun ship(id: Long): Ship?
    @Query("SELECT * FROM sightings WHERE id = :id") abstract suspend fun sighting(id: Long): Sighting?

    @Insert abstract suspend fun insert(s: Ship): Long
    @Update abstract suspend fun update(s: Ship)
    @Insert abstract suspend fun insert(s: Sighting): Long
    @Update abstract suspend fun update(s: Sighting)
    @Delete abstract suspend fun delete(s: Sighting)
    @Insert abstract suspend fun insert(c: Cargo): Long
    @Update abstract suspend fun update(c: Cargo)
    @Delete abstract suspend fun delete(c: Cargo)

    @Query("DELETE FROM ships WHERE id = :id") protected abstract suspend fun deleteShipRow(id: Long)
    @Query("DELETE FROM sightings WHERE shipId = :id") protected abstract suspend fun deleteSightingsOf(id: Long)
    @Query("DELETE FROM cargo WHERE shipId = :id") protected abstract suspend fun deleteCargoOf(id: Long)
    @Query("UPDATE sightings SET shipId = :into WHERE shipId = :from") protected abstract suspend fun moveSightings(from: Long, into: Long)
    @Query("UPDATE cargo SET shipId = :into WHERE shipId = :from") protected abstract suspend fun moveCargo(from: Long, into: Long)

    /** A ship and everything it ever brought. */
    @Transaction
    open suspend fun deleteShip(id: Long) {
        deleteSightingsOf(id); deleteCargoOf(id); deleteShipRow(id)
    }

    /** Two names for one thought: [from]'s sightings and cargo join [into]. */
    @Transaction
    open suspend fun merge(from: Long, into: Long) {
        moveSightings(from, into); moveCargo(from, into); deleteShipRow(from)
    }

    @Query("DELETE FROM ships") protected abstract suspend fun clearShips()
    @Query("DELETE FROM sightings") protected abstract suspend fun clearSightings()
    @Query("DELETE FROM cargo") protected abstract suspend fun clearCargo()
    @Insert protected abstract suspend fun insertShips(l: List<Ship>)
    @Insert protected abstract suspend fun insertSightings(l: List<Sighting>)
    @Insert protected abstract suspend fun insertCargo(l: List<Cargo>)

    @Transaction
    open suspend fun replaceAll(all: Everything) {
        clearShips(); clearSightings(); clearCargo()
        insertShips(all.ships); insertSightings(all.sightings); insertCargo(all.cargo)
    }
}

/** The whole log, for backups. */
class Everything(val ships: List<Ship>, val sightings: List<Sighting>, val cargo: List<Cargo>)

@Database(entities = [Ship::class, Sighting::class, Cargo::class], version = 1, exportSchema = true)
abstract class KeeperDatabase : RoomDatabase() {
    abstract fun dao(): KeeperDao

    companion object {
        fun create(context: Context): KeeperDatabase =
            Room.databaseBuilder(context.applicationContext, KeeperDatabase::class.java, "keeper.db").build()
    }
}
