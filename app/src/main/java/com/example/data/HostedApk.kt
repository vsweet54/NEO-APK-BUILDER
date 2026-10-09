package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hosted_apks")
data class HostedApk(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val appName: String,
    val versionName: String,
    val packageName: String,
    val fileSizeBytes: Long,
    val uploadTimestamp: Long,
    val pageUrl: String,
    val directDownloadUrl: String,
    val fileName: String
)
