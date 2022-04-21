package code.name.monkey.retromusic.db

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface BlacklistDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(blacklistEntity: BlacklistEntity)

    @Delete
    suspend fun delete(blacklistEntity: BlacklistEntity)

    @Query("DELETE FROM BlacklistEntity")
    suspend fun deleteAll()

    @Query("SELECT * FROM BlacklistEntity")
    suspend fun getAll(): List<BlacklistEntity>

    @Query("SELECT * FROM BlacklistEntity")
    fun getLiveData(): LiveData<List<BlacklistEntity>>

}
