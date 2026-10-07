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
            // Search contact by name
            findContactPhoneNumber(trimmed)
        }

        if (phoneNumber.isNullOrBlank()) {
            return ActionResult(
                success = false,
                actionType = "CALL",
                detail = "Contact '$trimmed' not found in your contacts.",
                verified = false,
                errorReason = "CONTACT_NOT_FOUND"
            )
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        return if (hasCallPermission) {
            try {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ActionResult(
                    success = true,
                    actionType = "CALL",
                    detail = "Calling $trimmed ($phoneNumber)...",
                    verified = true
                )
            } catch (e: Exception) {
                // Fallback to dialer
                openDialer(phoneNumber, trimmed)
            }
        } else {
            // Open dialer safely without crashing
            openDialer(phoneNumber, trimmed)
        }
    }

    private fun openDialer(phoneNumber: String, name: String): ActionResult {
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(
                success = true,
                actionType = "DIAL",
                detail = "Opened dialer for $name ($phoneNumber). Direct call permission not granted.",
                verified = true
            )
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
     * Open YouTube app or browser.
     */
    fun openYouTube(query: String? = null): ActionResult {
        val pm = context.packageManager
        return try {
            val uri = if (query.isNullOrBlank()) {
                Uri.parse("https://www.youtube.com")
            } else {
                Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                ActionResult(true, "YOUTUBE", "YouTube opened.", true)
            } else {
                ActionResult(false, "YOUTUBE", "YouTube could not be opened.", false)
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
}
