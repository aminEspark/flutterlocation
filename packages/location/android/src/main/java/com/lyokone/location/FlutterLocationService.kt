package com.lyokone.location

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry

const val kDefaultChannelName: String = "Location background service"
const val kDefaultNotificationTitle: String = "Location background service running"
const val kDefaultNotificationIconName: String = "navigation_empty_icon"

data class NotificationOptions(
    val channelName: String = kDefaultChannelName,
    val title: String = kDefaultNotificationTitle,
    val iconName: String = kDefaultNotificationIconName,
    val subtitle: String? = null,
    val description: String? = null,
    val color: Int? = null,
    val onTapBringToFront: Boolean = false
)

class BackgroundNotification(
    private val context: Context,
    private val channelId: String,
    private val notificationId: Int
) {
    private var options: NotificationOptions = NotificationOptions()
    private var builder: NotificationCompat.Builder = NotificationCompat.Builder(context, channelId)
        .setPriority(NotificationCompat.PRIORITY_HIGH)

    init {
        try {
            updateNotification(options, false)
        } catch (e: Exception) {
            Log.e("BackgroundNotification", "Error in init: ${e.message}")
        }
    }

    private fun getDrawableId(iconName: String): Int {
        return try {
            context.resources.getIdentifier(iconName, "drawable", context.packageName)
        } catch (e: Exception) {
            Log.e("BackgroundNotification", "Error fetching drawable ID: ${e.message}")
        }
    }

    private fun buildBringToFrontIntent(): PendingIntent? {
        return try {
            val intent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.setPackage(null)
                ?.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            if (intent != null) {
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            } else null
        } catch (e: Exception) {
            Log.e("BackgroundNotification", "Error creating bring-to-front intent: ${e.message}")
            null
        }
    }

    private fun updateChannel(channelName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val notificationManager = NotificationManagerCompat.from(context)
                val channel = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_NONE
                ).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
                notificationManager.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.e("BackgroundNotification", "Error updating notification channel: ${e.message}")
            }
        }
    }

    private fun updateNotification(options: NotificationOptions, notify: Boolean) {
        try {
            val iconId = getDrawableId(options.iconName).let {
                if (it != 0) it else getDrawableId(kDefaultNotificationIconName)
            }
            builder = builder
                .setContentTitle(options.title)
                .setSmallIcon(iconId)
                .setContentText(options.subtitle)
                .setSubText(options.description)

            builder = if (options.color != null) {
                builder.setColor(options.color).setColorized(true)
            } else {
                builder.setColor(0).setColorized(false)
            }

            builder = if (options.onTapBringToFront) {
                builder.setContentIntent(buildBringToFrontIntent())
            } else {
                builder.setContentIntent(null)
            }

            if (notify) {
                try {
                    val notificationManager = NotificationManagerCompat.from(context)
                    notificationManager.notify(notificationId, builder.build())
                } catch (e: SecurityException) {
                    Log.e("BackgroundNotification", "SecurityException when notifying: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("BackgroundNotification", "Error updating notification: ${e.message}")
        }
    }

    fun build(): Notification {
        return try {
            updateChannel(options.channelName)
            builder.build()
        } catch (e: Exception) {
            Log.e("BackgroundNotification", "Error building notification: ${e.message}")
            Notification()
        }
    }
}

class FlutterLocationService : ExceptionHandlingService(), PluginRegistry.RequestPermissionsResultListener {
    companion object {
        private const val TAG = "FlutterLocationService"
        private const val REQUEST_PERMISSIONS_REQUEST_CODE = 641
        private const val ONGOING_NOTIFICATION_ID = 75418
        private const val CHANNEL_ID = "flutter_location_channel_01"
    }

    // private val binder = LocalBinder()
    private var isForeground = false
    private var activity: Activity? = null
    private var backgroundNotification: BackgroundNotification? = null
    var location: FlutterLocation? = null
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            location = FlutterLocation(applicationContext, null)
            backgroundNotification = BackgroundNotification(applicationContext, CHANNEL_ID, ONGOING_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}")
        }
    }

     override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Binding to location service.")
        return binder
    }

    override fun onDestroy() {
        try {
            location = null
            backgroundNotification = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}")
        }
        super.onDestroy()
    }

    fun enableBackgroundMode() {
        if (!isForeground) {
            try {
                val notification = backgroundNotification!!.build()
                startForeground(ONGOING_NOTIFICATION_ID, notification)
                isForeground = true
            } catch (e: Exception) {
                Log.e(TAG, "Error enabling background mode: ${e.message}")
            }
        }
    }

    fun disableBackgroundMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
            isForeground = false
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling background mode: ${e.message}")
        }
    }
}
