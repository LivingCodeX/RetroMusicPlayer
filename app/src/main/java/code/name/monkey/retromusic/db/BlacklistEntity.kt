package code.name.monkey.retromusic.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class BlacklistEntity(
    @PrimaryKey
    val path: String
)
