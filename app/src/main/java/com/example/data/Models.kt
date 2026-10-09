package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val folderName: String,
    val packageName: String,
    val versionName: String = "1.0.1",
    val versionCode: Int = 1,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val iconPath: String? = null,
    val orientation: String = "portrait",
    val fullscreen: Boolean = false,
    val hardwareAccelerated: Boolean = true,
    val minSdk: Int = 21,
    val targetSdk: Int = 34,
    val buildType: String = "release",
    val nativeBridge: Boolean = true,
    val domStorage: Boolean = true,
    val permissions: String = "INTERNET,MEDIA,DOWNLOAD,CLIPBOARD,CAMERA,AUDIO,LOCATION,VIBRATE,NOTIFICATION"
)

data class ProjectConfig(
    val appName: String,
    val packageName: String,
    val versionName: String = "1.0.1",
    val versionCode: Int = 1,
    val iconPath: String? = null,
    val orientation: String = "portrait",
    val fullscreen: Boolean = false,
    val minSdk: Int = 21,
    val targetSdk: Int = 34,
    val buildType: String = "release",
    val nativeBridge: Boolean = true,
    val domStorage: Boolean = true,
    val permissions: String = "INTERNET,MEDIA,DOWNLOAD,CLIPBOARD,CAMERA,AUDIO,LOCATION,VIBRATE,NOTIFICATION"
)

@Entity(tableName = "build_history")
data class BuildHistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val appName: String,
    val packageName: String,
    val versionName: String = "1.0.1",
    val apkPath: String,
    val fileSizeBytes: Long,
    val buildTimestamp: Long = System.currentTimeMillis(),
    val status: String = "READY",
    val iconPath: String? = null
)

data class WebProjectFile(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long
)
