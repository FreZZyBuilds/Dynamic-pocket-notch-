package com.frezzybuilds.devnotch.service

import android.service.notification.NotificationListenerService

/**
 * Durch den Benachrichtigungszugriff darf die App über
 * MediaSessionManager.getActiveSessions(ComponentName(this, MediaNotificationListener::class.java))
 * die aktiven Mediaplayer-Sessions auslesen und steuern.
 */
class MediaNotificationListener : NotificationListenerService()
