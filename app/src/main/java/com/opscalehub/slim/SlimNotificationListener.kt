package com.opscalehub.slim

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification

class SlimNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""

        if (text.isNotEmpty() && !sbn.isOngoing) {
            val preview = if (title.isNotEmpty()) "$title: $text" else text
            NotificationRegistry.updateNotification(packageName, preview)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationRegistry.removeNotification(sbn.packageName)
    }
}

object NotificationRegistry {
    interface NotificationUpdateListener {
        fun onNotificationsChanged()
    }

    private val activeNotifications = mutableMapOf<String, String>()
    private var listener: NotificationUpdateListener? = null

    fun registerListener(l: NotificationUpdateListener) {
        listener = l
    }

    fun unregisterListener() {
        listener = null
    }

    fun updateNotification(packageName: String, previewText: String) {
        activeNotifications[packageName] = previewText
        listener?.onNotificationsChanged()
    }

    fun removeNotification(packageName: String) {
        activeNotifications.remove(packageName)
        listener?.onNotificationsChanged()
    }

    fun getNotificationPreview(packageName: String): String? {
        return activeNotifications[packageName]
    }
}
