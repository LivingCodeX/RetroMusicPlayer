package code.name.monkey.retromusic.db

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface WhitelistDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(whitelistEntity: WhitelistEntity)

    @Delete
    suspend fun delete(whitelistEntity: WhitelistEntity)

    @Query("DELETE FROM WhitelistEntity")
    suspend fun deleteAll()

    @Query("SELECT * FROM WhitelistEntity")
    suspend fun getAll(): List<WhitelistEntity>

    @Query("SELECT * FROM WhitelistEntity")
    fun getLiveData(): LiveData<List<WhitelistEntity>>

}
