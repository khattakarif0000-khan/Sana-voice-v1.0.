package com.example.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

data class SanaNotificationItem(
    val id: Int,
    val packageName: String,
    val appTitle: String,
    val title: String,
    val text: String,
    val timestamp: Long
)

class SanaNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        try {
            val extras = sbn.notification.extras
            val title = extras.getString("android.title") ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            val pkg = sbn.packageName

            if (title.isNotBlank() || text.isNotBlank()) {
                val item = SanaNotificationItem(
                    id = sbn.id,
                    packageName = pkg,
                    appTitle = getAppLabel(pkg),
                    title = title,
                    text = text,
                    timestamp = sbn.postTime
                )
                synchronized(recentNotifications) {
                    recentNotifications.removeAll { it.id == sbn.id && it.packageName == pkg }
                    recentNotifications.add(0, item)
                    if (recentNotifications.size > 20) {
                        recentNotifications.removeLast()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SanaNotificationListener", "Error reading notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        synchronized(recentNotifications) {
            recentNotifications.removeAll { it.id == sbn.id && it.packageName == sbn.packageName }
        }
    }

    private fun getAppLabel(pkg: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            pkg.substringAfterLast('.')
        }
    }

    companion object {
        private val recentNotifications = mutableListOf<SanaNotificationItem>()

        fun getActiveNotifications(): List<SanaNotificationItem> {
            synchronized(recentNotifications) {
                return recentNotifications.toList()
            }
        }

        fun getSummary(): String {
            val list = getActiveNotifications()
            if (list.isEmpty()) {
                return "No new notifications found."
            }
            val grouped = list.take(5).joinToString("\n") { n ->
                "${n.appTitle}: ${n.title} - ${n.text}"
            }
            return "You have ${list.size} notification(s):\n$grouped"
        }
    }
}
