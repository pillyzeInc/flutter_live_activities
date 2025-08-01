package com.example.live_activities

import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class LiveActivityFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "LiveActivityFCMService"
    }

    private fun jsonDecode(json: String): Map<String, Any> {
        val jsonObject = JSONObject(json)
        val map = mutableMapOf<String, Any>()
        jsonObject.keys().forEach { key ->
            map[key] = jsonObject.get(key)
        }
        return map
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        // manager null 체크
        val liveActivityManager = LiveActivityManagerHolder.instance
        if (liveActivityManager == null) {
            Log.e(TAG, "LiveActivityManagerHolder.instance is null → skip FCM")
            return
        }

        // data payload 비어 있으면 스킵
        val args = remoteMessage.data
        if (args.isEmpty()) {
            Log.w(TAG, "FCM data payload is empty → skip")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            // event 키 체크
            val event = args["event"] ?: run {
                Log.e(TAG, "FCM payload missing 'event' → skip")
                return@launch
            }

            // activity-id 키 체크
            val id = args["activity-id"] ?: run {
                Log.e(TAG, "FCM payload missing 'activity-id' → skip")
                return@launch
            }

            // content-state 파싱 (기본 "{}")
            val contentState = args["content-state"].takeIf { !it.isNullOrBlank() } ?: "{}"
            val dataMap = try {
                jsonDecode(contentState)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse content-state JSON → empty map", e)
                emptyMap<String, Any>()
            }

            // timestamp 파싱 (없으면 0L)
            val timestamp = args["timestamp"]?.toLongOrNull() ?: 0L

            when (event) {
                "update" -> liveActivityManager.updateActivity(id, timestamp, dataMap)
                "end"    -> liveActivityManager.endActivity(id, dataMap)
                else     -> Log.e(TAG, "Unknown event type: $event")
            }
        }
    }
}
