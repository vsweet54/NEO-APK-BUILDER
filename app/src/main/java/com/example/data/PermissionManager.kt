package com.example.data

data class PermissionDefinition(
    val id: String,
    val title: String,
    val description: String,
    val manifestPermissions: List<String>,
    val category: PermissionCategory,
    val isRuntime: Boolean = false,
    val isSpecialAccess: Boolean = false,
    val defaultEnabled: Boolean = false,
    val note: String? = null
)

enum class PermissionCategory(val displayName: String) {
    NETWORK("Jaringan & Konektivitas"),
    MEDIA_SENSORS("Media & Sensor Perangkat"),
    USER_DATA("Data Pengguna & Notifikasi"),
    SPECIAL_ACCESS("Akses Khusus Tingkat Lanjut")
}

object PermissionManager {

    val DEFINITIONS = listOf(
        // 1. Internet
        PermissionDefinition(
            id = "INTERNET",
            title = "Internet",
            description = "Mengizinkan aplikasi memuat halaman web, API, dan sumber daya online",
            manifestPermissions = listOf("android.permission.INTERNET"),
            category = PermissionCategory.NETWORK,
            isRuntime = false,
            defaultEnabled = true
        ),
        // 2. Status Jaringan
        PermissionDefinition(
            id = "ACCESS_NETWORK_STATE",
            title = "Status Jaringan",
            description = "Mendeteksi status koneksi internet (Wi-Fi, Data Seluler, Offline)",
            manifestPermissions = listOf("android.permission.ACCESS_NETWORK_STATE"),
            category = PermissionCategory.NETWORK,
            isRuntime = false,
            defaultEnabled = true
        ),
        // 3. Kamera
        PermissionDefinition(
            id = "CAMERA",
            title = "Kamera",
            description = "Akses kamera untuk WebRTC, pemindaian QR code, foto, dan video",
            manifestPermissions = listOf("android.permission.CAMERA"),
            category = PermissionCategory.MEDIA_SENSORS,
            isRuntime = true,
            defaultEnabled = false
        ),
        // 4. Mikrofon
        PermissionDefinition(
            id = "RECORD_AUDIO",
            title = "Mikrofon (Perekam Suara)",
            description = "Merekam suara melalui mikrofon perangkat untuk panggilan atau pesan audio",
            manifestPermissions = listOf(
                "android.permission.RECORD_AUDIO",
                "android.permission.MODIFY_AUDIO_SETTINGS"
            ),
            category = PermissionCategory.MEDIA_SENSORS,
            isRuntime = true,
            defaultEnabled = false
        ),
        // 5. Lokasi Perkiraan
        PermissionDefinition(
            id = "ACCESS_COARSE_LOCATION",
            title = "Lokasi Perkiraan (Coarse)",
            description = "Akses perkiraan posisi perangkat berbasis jaringan seluler dan Wi-Fi",
            manifestPermissions = listOf("android.permission.ACCESS_COARSE_LOCATION"),
            category = PermissionCategory.MEDIA_SENSORS,
            isRuntime = true,
            defaultEnabled = false
        ),
        // 6. Lokasi Akurat
        PermissionDefinition(
            id = "ACCESS_FINE_LOCATION",
            title = "Lokasi Akurat (GPS / Fine)",
            description = "Akses posisi koordinat GPS presisi tinggi untuk peta dan navigasi",
            manifestPermissions = listOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION"
            ),
            category = PermissionCategory.MEDIA_SENSORS,
            isRuntime = true,
            defaultEnabled = false
        ),
        // 7. Notifikasi
        PermissionDefinition(
            id = "POST_NOTIFICATIONS",
            title = "Notifikasi Sistem",
            description = "Menampilkan push notifications & status download (wajib di Android 13+)",
            manifestPermissions = listOf("android.permission.POST_NOTIFICATIONS"),
            category = PermissionCategory.USER_DATA,
            isRuntime = true,
            defaultEnabled = false
        ),
        // 8. Media Gambar
        PermissionDefinition(
            id = "READ_MEDIA_IMAGES",
            title = "Akses Gambar Galeri",
            description = "Membaca berkas foto dan gambar dari penyimpanan perangkat (Android 13+)",
            manifestPermissions = listOf("android.permission.READ_MEDIA_IMAGES"),
            category = PermissionCategory.MEDIA_SENSORS,
            isRuntime = true,
            defaultEnabled = false
        ),
        // 9. Media Video
        PermissionDefinition(
            id = "READ_MEDIA_VIDEO",
            title = "Akses Video Galeri",
            description = "Membaca berkas video dari penyimpanan perangkat (Android 13+)",
            manifestPermissions = listOf("android.permission.READ_MEDIA_VIDEO"),
            category = PermissionCategory.MEDIA_SENSORS,
            isRuntime = true,
            defaultEnabled = false
        ),
        // 10. Media Audio
        PermissionDefinition(
            id = "READ_MEDIA_AUDIO",
            title = "Akses Berkas Audio",
            description = "Membaca berkas musik dan rekaman audio dari penyimpanan (Android 13+)",
            manifestPermissions = listOf("android.permission.READ_MEDIA_AUDIO"),
            category = PermissionCategory.MEDIA_SENSORS,
            isRuntime = true,
            defaultEnabled = false
        ),
        // 11. Bluetooth
        PermissionDefinition(
            id = "BLUETOOTH",
            title = "Bluetooth & Pemindaian",
            description = "Koneksi ke perangkat Bluetooth periferal & BLE (sesuai versi Android)",
            manifestPermissions = listOf(
                "android.permission.BLUETOOTH",
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.BLUETOOTH_SCAN"
            ),
            category = PermissionCategory.NETWORK,
            isRuntime = true,
            defaultEnabled = false
        ),
        // 12. Kontak
        PermissionDefinition(
            id = "READ_CONTACTS",
            title = "Kontak Pengguna",
            description = "Membaca daftar kontak buku telepon perangkat",
            manifestPermissions = listOf("android.permission.READ_CONTACTS"),
            category = PermissionCategory.USER_DATA,
            isRuntime = true,
            defaultEnabled = false
        ),
        // 13. Kalender
        PermissionDefinition(
            id = "CALENDAR",
            title = "Kalender (Baca & Tulis)",
            description = "Membaca dan menambahkan acara agenda ke kalender perangkat",
            manifestPermissions = listOf(
                "android.permission.READ_CALENDAR",
                "android.permission.WRITE_CALENDAR"
            ),
            category = PermissionCategory.USER_DATA,
            isRuntime = true,
            defaultEnabled = false
        ),
        // 14. Akses khusus semua file
        PermissionDefinition(
            id = "MANAGE_EXTERNAL_STORAGE",
            title = "Akses Semua Berkas (Khusus)",
            description = "Hanya untuk aplikasi pengelola file tingkat lanjut (Android 11+)",
            manifestPermissions = listOf("android.permission.MANAGE_EXTERNAL_STORAGE"),
            category = PermissionCategory.SPECIAL_ACCESS,
            isRuntime = false,
            isSpecialAccess = true,
            defaultEnabled = false,
            note = "Opsi lanjutan. Jangan gunakan jika pemilih berkas SAF sudah memadai."
        ),
        // 15. Overlay Sistem (Floating Window)
        PermissionDefinition(
            id = "SYSTEM_ALERT_WINDOW",
            title = "Izin Overlay Sistem (Floating Window)",
            description = "Menampilkan jendela atau widget melayang di atas aplikasi lain (SYSTEM_ALERT_WINDOW)",
            manifestPermissions = listOf("android.permission.SYSTEM_ALERT_WINDOW"),
            category = PermissionCategory.SPECIAL_ACCESS,
            isRuntime = false,
            isSpecialAccess = true,
            defaultEnabled = false,
            note = "Izin khusus untuk floating widget atau jendela overlay. Pengguna akan diarahkan ke pengaturan sistem jika diperlukan."
        ),
        // Tambahan getar haptic
        PermissionDefinition(
            id = "VIBRATE",
            title = "Getaran (Haptic Feedback)",
            description = "Umpan balik getar pada tombol atau game",
            manifestPermissions = listOf("android.permission.VIBRATE"),
            category = PermissionCategory.USER_DATA,
            isRuntime = false,
            defaultEnabled = true
        )
    )

    fun getDefaultPermissionsString(): String {
        return DEFINITIONS
            .filter { it.defaultEnabled }
            .joinToString(",") { it.id }
    }

    fun parsePermissions(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return setOf("INTERNET", "ACCESS_NETWORK_STATE", "VIBRATE")
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun getManifestPermissions(rawPermissions: String?, targetSdk: Int = 34): Set<String> {
        val selectedIds = parsePermissions(rawPermissions)
        val result = mutableSetOf<String>()

        for (def in DEFINITIONS) {
            if (selectedIds.contains(def.id)) {
                result.addAll(def.manifestPermissions)
            }
        }

        // Always ensure INTERNET is included if present
        if (selectedIds.contains("INTERNET")) {
            result.add("android.permission.INTERNET")
        }

        return result
    }
}
