package code.name.monkey.retromusic.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class WhitelistEntity(
    @PrimaryKey
    val path: String
)
