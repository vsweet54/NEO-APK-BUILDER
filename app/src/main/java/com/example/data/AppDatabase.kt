package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<Project>>

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun getProjectCount(): Int

    @Query("SELECT * FROM projects LIMIT 1")
    suspend fun getFirstProject(): Project?

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: Long): Project?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project): Long

    @Update
    suspend fun updateProject(project: Project)

    @Delete
    suspend fun deleteProject(project: Project)
}

@Dao
interface BuildHistoryDao {
    @Query("SELECT * FROM build_history ORDER BY buildTimestamp DESC")
    fun getAllBuilds(): Flow<List<BuildHistoryItem>>

    @Query("SELECT COUNT(*) FROM build_history")
    suspend fun getBuildCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuild(item: BuildHistoryItem): Long

    @Delete
    suspend fun deleteBuild(item: BuildHistoryItem)

    @Query("DELETE FROM build_history WHERE id = :id")
    suspend fun deleteBuildById(id: Long)
}

@Dao
interface HostedApkDao {
    @Query("SELECT * FROM hosted_apks ORDER BY uploadTimestamp DESC")
    fun getAllHosted(): Flow<List<HostedApk>>

    @Query("SELECT COUNT(*) FROM hosted_apks")
    suspend fun getHostedCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHosted(item: HostedApk): Long

    @Delete
    suspend fun deleteHosted(item: HostedApk)

    @Query("DELETE FROM hosted_apks WHERE id = :id")
    suspend fun deleteHostedById(id: Long)
}

@Database(entities = [Project::class, BuildHistoryItem::class, HostedApk::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun buildHistoryDao(): BuildHistoryDao
    abstract fun hostedApkDao(): HostedApkDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "neo_apk_builder.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
