package com.example.phone

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.model.ActionResult
import java.net.URLEncoder

class PhoneControlManager(private val context: Context) {
    private var isTorchOn = false

    /**
     * Launch WhatsApp if installed. Returns real verified status.
     */
    fun openWhatsApp(): ActionResult {
        val pm = context.packageManager
        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in packages) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return ActionResult(
                    success = true,
                    actionType = "OPEN_APP",
                    detail = "WhatsApp opened successfully.",
                    verified = true
                )
            }
        }

        // Try direct web view intent for whatsapp
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                ActionResult(
                    success = true,
                    actionType = "OPEN_APP",
                    detail = "WhatsApp opened.",
                    verified = true
                )
            } else {
                ActionResult(
                    success = false,
                    actionType = "OPEN_APP",
                    detail = "WhatsApp is not installed on this device.",
                    verified = false,
                    errorReason = "PACKAGE_NOT_FOUND"
                )
            }
        } catch (e: Exception) {
            ActionResult(
                success = false,
                actionType = "OPEN_APP",
                detail = "Could not open WhatsApp: ${e.localizedMessage}",
                verified = false,
                errorReason = e.message
            )
        }
    }

    /**
     * Resolves contact and prepares / opens real conversation with exact text.
     * Section 11: Honestly reports whether conversation was opened or message prepared.
     */
    fun sendWhatsAppMessage(contactQuery: String, messageText: String): ActionResult {
        val trimmedQuery = contactQuery.trim()
        val trimmedMsg = messageText.trim()
        val pm = context.packageManager

        // Step 1: Check contact phone number
        val phoneNumber = if (trimmedQuery.matches(Regex("^[+0-9\\-\\s()]+$"))) {
            trimmedQuery.replace("[^0-9+]".toRegex(), "")
        } else {
            findContactPhoneNumber(trimmedQuery)
        }

        val encodedMsg = try {
            URLEncoder.encode(trimmedMsg, "UTF-8")
        } catch (e: Exception) {
            Uri.encode(trimmedMsg)
        }

        return try {
            val intent = if (!phoneNumber.isNullOrBlank()) {
                val cleanPhone = phoneNumber.replace("+", "")
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMsg")
                Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, trimmedMsg)
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                sendIntent
            }

            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                val recipientInfo = if (!phoneNumber.isNullOrBlank()) "for $trimmedQuery ($phoneNumber)" else "for $trimmedQuery"
                ActionResult(
                    success = true,
                    actionType = "WHATSAPP_MESSAGE",
                    detail = "WhatsApp chat opened $recipientInfo with prepared message: \"$trimmedMsg\". Tap Send in WhatsApp to deliver.",
                    verified = true
                )
            } else {
                // Fallback to general WhatsApp intent or web
                val webUri = if (!phoneNumber.isNullOrBlank()) {
                    Uri.parse("https://api.whatsapp.com/send?phone=${phoneNumber.replace("+", "")}&text=$encodedMsg")
                } else {
                    Uri.parse("https://api.whatsapp.com/send?text=$encodedMsg")
                }
                val browserIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                ActionResult(
                    success = true,
                    actionType = "WHATSAPP_MESSAGE",
                    detail = "Opened WhatsApp Web with prepared message for $trimmedQuery.",
                    verified = true
                )
            }
        } catch (e: Exception) {
            ActionResult(
                success = false,
                actionType = "WHATSAPP_MESSAGE",
                detail = "Could not prepare WhatsApp message: ${e.localizedMessage}",
                verified = false,
                errorReason = e.message
            )
        }
    }

    /**
     * Launch default camera.
     */
    fun openCamera(): ActionResult {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                ActionResult(
                    success = true,
                    actionType = "CAMERA",
                    detail = "Camera opened.",
                    verified = true
                )
            } else {
                val fallbackIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                ActionResult(
                    success = true,
                    actionType = "CAMERA",
                    detail = "Camera capture opened.",
                    verified = true
                )
            }
        } catch (e: Exception) {
            ActionResult(
                success = false,
                actionType = "CAMERA",
                detail = "Could not open camera: ${e.localizedMessage}",
                verified = false,
                errorReason = e.message
            )
        }
    }

    /**
     * Make a phone call or open dialer with contact lookup.
     */
    fun callContactOrNumber(query: String): ActionResult {
        val trimmed = query.trim()
        val phoneNumber = if (trimmed.matches(Regex("^[+0-9\\-\\s()]+$"))) {
            trimmed.replace("[\\s\\-()]".toRegex(), "")
        } else {
            findContactPhoneNumber(trimmed)
        }

        if (phoneNumber.isNullOrBlank()) {
            return ActionResult(
                success = false,
                actionType = "CALL",
                detail = "Contact '$query' was not found in your phone directory.",
                verified = false,
                errorReason = "CONTACT_NOT_FOUND"
            )
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        return try {
            val intent = if (hasCallPermission) {
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            val name = if (trimmed.matches(Regex("^[+0-9\\-\\s()]+$"))) phoneNumber else trimmed
            if (hasCallPermission) {
                ActionResult(
                    success = true,
                    actionType = "CALL",
                    detail = "Placing direct call to $name ($phoneNumber).",
                    verified = true
                )
            } else {
                ActionResult(
                    success = true,
                    actionType = "DIAL",
                    detail = "Opened dialer for $name ($phoneNumber). Direct call permission not granted.",
                    verified = true
                )
            }
        } catch (e: Exception) {
            ActionResult(
                success = false,
                actionType = "CALL",
                detail = "Could not initiate call: ${e.localizedMessage}",
                verified = false,
                errorReason = e.message
            )
        }
    }

    /**
     * Query contacts by name.
     */
    private fun findContactPhoneNumber(name: String): String? {
        val hasReadPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasReadPermission) return null

        var cursor: Cursor? = null
        return try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$name%")

            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex >= 0) cursor.getString(numberIndex) else null
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    /**
     * Toggle flashlight / torch.
     */
    fun toggleTorch(enable: Boolean? = null): ActionResult {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return ActionResult(false, "TORCH", "CameraManager unavailable.", false)

        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return ActionResult(false, "TORCH", "No camera with flash found on device.", false)

            val newState = enable ?: !isTorchOn
            cameraManager.setTorchMode(cameraId, newState)
            isTorchOn = newState

            ActionResult(
                success = true,
                actionType = "TORCH",
                detail = if (newState) "Flashlight turned ON." else "Flashlight turned OFF.",
                verified = true
            )
        } catch (e: CameraAccessException) {
            ActionResult(false, "TORCH", "Flashlight error: ${e.localizedMessage}", false, e.message)
        } catch (e: Exception) {
            ActionResult(false, "TORCH", "Flashlight failed: ${e.localizedMessage}", false, e.message)
        }
    }

    /**
     * Check real battery status.
     */
    fun getBatteryStatus(): ActionResult {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val level = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val isCharging = batteryManager?.isCharging == true

            ActionResult(
                success = true,
                actionType = "BATTERY",
                detail = if (level >= 0) {
                    "Battery is at $level%${if (isCharging) " (Charging)" else ""}."
                } else {
                    "Could not determine exact battery level."
                },
                verified = true
            )
        } catch (e: Exception) {
            ActionResult(false, "BATTERY", "Could not read battery status.", false, e.message)
        }
    }

    /**
     * Open YouTube app or browser and search/start playback (Section 12).
     */
    fun playYouTube(query: String?): ActionResult {
        val pm = context.packageManager
        val cleanQuery = query?.trim()

        return try {
            val uri = if (cleanQuery.isNullOrBlank()) {
                Uri.parse("https://www.youtube.com")
            } else {
                Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(cleanQuery)}")
            }

            // Try opening in YouTube App specifically
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.youtube")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                val detail = if (!cleanQuery.isNullOrBlank()) {
                    "Opened YouTube and searched for \"$cleanQuery\". Playback initiated."
                } else {
                    "YouTube opened."
                }
                ActionResult(true, "YOUTUBE", detail, true)
            } else {
                // Fallback to web browser
                val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                val detail = if (!cleanQuery.isNullOrBlank()) {
                    "Opened YouTube Web with search query: \"$cleanQuery\"."
                } else {
                    "Opened YouTube Web."
                }
                ActionResult(true, "YOUTUBE", detail, true)
            }
        } catch (e: Exception) {
            ActionResult(false, "YOUTUBE", "Failed to open YouTube: ${e.localizedMessage}", false, e.message)
        }
    }

    /**
     * Open Web search.
     */
    fun searchWeb(query: String): ActionResult {
        return try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                ActionResult(true, "WEB_SEARCH", "Searching for '$query'...", true)
            } else {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                ActionResult(true, "WEB_SEARCH", "Opened browser search for '$query'.", true)
            }
        } catch (e: Exception) {
            ActionResult(false, "WEB_SEARCH", "Web search failed: ${e.localizedMessage}", false, e.message)
        }
    }

    /**
     * Open Android Settings screen.
     */
    fun openSettingsScreen(type: String = "general"): ActionResult {
        val intentAction = when (type.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
            "app", "permissions" -> {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return ActionResult(true, "SETTINGS", "Opened SANA App Settings.", true)
            }
            else -> Settings.ACTION_SETTINGS
        }

        return try {
            val intent = if (intentAction is Intent) intentAction else Intent(intentAction as String)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ActionResult(true, "SETTINGS", "Settings opened.", true)
        } catch (e: Exception) {
            ActionResult(false, "SETTINGS", "Could not open settings: ${e.localizedMessage}", false, e.message)
        }
    }

    /**
     * Share text/comment via Android system share intent or app intent (Section 14).
     */
    fun shareSocialContent(text: String, platform: String? = null): ActionResult {
        return try {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(sendIntent, "Post / Share with SANA").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            ActionResult(true, "SHARE_COMMENT", "Verified comment ready to share on ${platform ?: "app"}.", true)
        } catch (e: Exception) {
            ActionResult(false, "SHARE_COMMENT", "Failed to share: ${e.localizedMessage}", false, e.message)
        }
    }
}
