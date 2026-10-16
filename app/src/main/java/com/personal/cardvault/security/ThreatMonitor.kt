package com.personal.cardvault.security

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class ThreatKind { SCREEN_READING_APP, DEBUGGING, SCREEN_RECORDING, ROOTED }

data class Threat(val kind: ThreatKind, val apps: List<String> = emptyList())

/**
 * Watches for anything that would let another app or a connected computer see Coffer's screen.
 * While any threat is present, the vault stays locked and only the protection screen is shown.
 */
class ThreatMonitor(private val context: Context, private val onThreat: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val isDebugBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val _threats = MutableStateFlow<List<Threat>>(emptyList())
    val threats: StateFlow<List<Threat>> = _threats.asStateFlow()

    @Volatile private var recording = false

    fun start() {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        listOf(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED),
            Settings.Global.getUriFor(Settings.Global.ADB_ENABLED),
            Settings.Global.getUriFor(ADB_WIFI_ENABLED),
        ).forEach { resolver.registerContentObserver(it, false, observer) }

        val am = context.getSystemService(AccessibilityManager::class.java)
        am?.addAccessibilityStateChangeListener { refresh() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            am?.addAccessibilityServicesStateChangeListener { refresh() }
        }
        refresh()
    }

    fun setScreenRecording(active: Boolean) {
        recording = active
        refresh()
    }

    fun refresh() {
        val found = buildList {
            untrustedAccessibilityApps().takeIf { it.isNotEmpty() }?.let { add(Threat(ThreatKind.SCREEN_READING_APP, it)) }
            // Debug builds are for development over USB, so they skip this one check.
            if (!isDebugBuild && debuggingEnabled()) add(Threat(ThreatKind.DEBUGGING))
            if (recording) add(Threat(ThreatKind.SCREEN_RECORDING))
            if (looksRooted()) add(Threat(ThreatKind.ROOTED))
        }
        _threats.value = found
        if (found.isNotEmpty()) onThreat()
    }

    /**
     * Enabled accessibility services that don't come preinstalled on the phone. Built-in services
     * such as TalkBack are trusted; anything installed later could be remote-control malware.
     */
    private fun untrustedAccessibilityApps(): List<String> {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty()
            .split(':')
            .mapNotNull { ComponentName.unflattenFromString(it.trim())?.packageName }
            .toMutableSet()
        context.getSystemService(AccessibilityManager::class.java)
            ?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            ?.mapNotNullTo(enabled) { it.resolveInfo?.serviceInfo?.packageName }

        val pm = context.packageManager
        return enabled.filterNot { pkg -> isPreinstalled(pm, pkg) }.map { pkg -> appLabel(pm, pkg) }.sorted()
    }

    private fun isPreinstalled(pm: PackageManager, pkg: String): Boolean = try {
        val flags = pm.getApplicationInfo(pkg, 0).flags
        flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    } catch (e: PackageManager.NameNotFoundException) {
        false // can't verify it, so don't trust it
    }

    private fun appLabel(pm: PackageManager, pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    private fun debuggingEnabled(): Boolean {
        val resolver = context.contentResolver
        return Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, 0) == 1 ||
            Settings.Global.getInt(resolver, ADB_WIFI_ENABLED, 0) == 1
    }

    private fun looksRooted(): Boolean {
        if (Build.TAGS?.contains("test-keys") == true) return true
        return ROOT_PATHS.any { File(it).exists() }
    }

    private companion object {
        const val ADB_WIFI_ENABLED = "adb_wifi_enabled"
        val ROOT_PATHS = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su", "/data/local/bin/su", "/data/local/xbin/su",
            "/system/app/Superuser.apk", "/system/app/SuperSU.apk", "/data/adb/magisk", "/sbin/.magisk",
            "/system/bin/magisk", "/data/adb/ksu", "/data/adb/ap",
        )
    }
}
