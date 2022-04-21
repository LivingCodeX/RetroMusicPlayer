package code.name.monkey.retromusic.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface QueueDao {

    @Insert
    suspend fun insertQueue(queue: List<QueueEntity>)

    @Query("DELETE FROM QueueEntity")
    suspend fun deleteQueue()

    @Query("SELECT * FROM QueueEntity")
    suspend fun getQueue(): List<QueueEntity>

}
