package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "simulations")
data class SimulationRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val obstacleType: String,
    val inletVelocity: Float,
    val viscosity: Float,
    val avgLift: Float,
    val avgDrag: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String
)

@Dao
interface SimulationDao {
    @Query("SELECT * FROM simulations ORDER BY timestamp DESC")
    fun getAllSimulations(): Flow<List<SimulationRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSimulation(record: SimulationRecord)

    @Query("DELETE FROM simulations WHERE id = :id")
    suspend fun deleteSimulationById(id: Int)

    @Query("DELETE FROM simulations")
    suspend fun clearAll()
}

@Database(entities = [SimulationRecord::class], version = 1, exportSchema = false)
abstract class AeroDatabase : RoomDatabase() {
    abstract fun simulationDao(): SimulationDao

    companion object {
        @Volatile
        private var INSTANCE: AeroDatabase? = null

        fun getDatabase(context: Context): AeroDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AeroDatabase::class.java,
                    "aerostream_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class SimulationRepository(private val dao: SimulationDao) {
    val allSimulations: Flow<List<SimulationRecord>> = dao.getAllSimulations()

    suspend fun insert(record: SimulationRecord) {
        dao.insertSimulation(record)
    }

    suspend fun deleteById(id: Int) {
        dao.deleteSimulationById(id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}
