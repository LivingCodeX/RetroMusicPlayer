package code.name.monkey.retromusic.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class QueueEntity(
    @PrimaryKey
    val item: Long,
    @ColumnInfo(name = "song_id")
    val songId: Long
)
