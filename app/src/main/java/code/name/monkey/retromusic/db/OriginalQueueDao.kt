package code.name.monkey.retromusic.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OriginalQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOriginalQueue(originalQueue: List<OriginalQueueEntity>)

    @Query("DELETE FROM OriginalQueueEntity")
    suspend fun deleteOriginalQueue()

    @Query("SELECT * FROM OriginalQueueEntity")
    suspend fun getOriginalQueue(): List<OriginalQueueEntity>

}
