package neth.iecal.curbox.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ViewTrackerStatsDao {

    @Query("SELECT * FROM view_tracker_stats WHERE date = :date AND packageName = :packageName ORDER BY count DESC")
    suspend fun getStatsForApp(date: String, packageName: String): List<ViewTrackerStatsEntity>

    @Query("SELECT * FROM view_tracker_stats WHERE date = :date AND packageName = :packageName ORDER BY count DESC")
    fun getStatsForAppFlow(date: String, packageName: String): Flow<List<ViewTrackerStatsEntity>>

    @Query("SELECT * FROM view_tracker_stats WHERE date = :date AND packageName = :packageName AND ruleId = :ruleId")
    suspend fun getStat(date: String, packageName: String, ruleId: String): ViewTrackerStatsEntity?

    @Upsert
    suspend fun upsert(entity: ViewTrackerStatsEntity)
}
