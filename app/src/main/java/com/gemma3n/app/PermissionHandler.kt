package com.gemma3n.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * PermissionHandler manages all permission-related operations
 * Separated from MainActivity for better maintainability and testing
 */
class PermissionHandler(private val activity: AppCompatActivity) {
    
    /**
     * Interface for listening to permission events
     */
    interface PermissionListener {
        /**
         * Called when all required permissions are granted
         */
        fun onPermissionsGranted()
        
        /**
         * Called when some permissions are denied but can be requested again
         * @param deniedPermissions List of permissions that were denied
         */
        fun onPermissionsDenied(deniedPermissions: List<String>)
        
        /**
         * Called when permissions are permanently denied (user selected "Don't ask again")
         * @param permissions List of permanently denied permissions
         */
        fun onPermissionsPermanentlyDenied(permissions: List<String>)
    }
    
    private var listener: PermissionListener? = null
    
    companion object {
        private const val TAG = "PermissionHandler"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
        private const val STORAGE_PERMISSION_REQUEST_CODE = 101
        private const val ALL_PERMISSIONS_REQUEST_CODE = 102
        
        // Required permissions for the app
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }
    
    /**
     * Set the listener for permission events
     * @param listener The listener to receive permission events
     */
    fun setListener(listener: PermissionListener) {
        this.listener = listener
        Log.d(TAG, "PermissionListener set")
    }
    
    /**
     * Check if all required permissions are granted
     * @return true if all permissions are granted, false otherwise
     */
    fun arePermissionsGranted(): Boolean {
        val cameraPermission = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
        val storagePermission = ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        
        val cameraGranted = cameraPermission == PackageManager.PERMISSION_GRANTED
        val storageGranted = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q ||
                           storagePermission == PackageManager.PERMISSION_GRANTED
        
        Log.d(TAG, "Permissions check - Camera: $cameraGranted, Storage: $storageGranted")
        return cameraGranted && storageGranted
    }
    
    /**
     * Check and request permissions if needed
     * @return true if permissions already granted, false if requesting
     */
    fun checkAndRequestPermissions(): Boolean {
        Log.d(TAG, "Checking and requesting permissions...")
        
        if (arePermissionsGranted()) {
            Log.d(TAG, "All permissions already granted")
            listener?.onPermissionsGranted()
            return true
        }
        
        val permissionsToRequest = getPermissionsToRequest()
        
        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
            
            // Check if we should show rationale
            if (shouldShowPermissionRationale(permissionsToRequest)) {
                showPermissionRationaleDialog(permissionsToRequest)
            } else {
                // Request permissions directly
                requestPermissions(permissionsToRequest)
            }
            return false
        }
        
        return true
    }
    
    /**
     * Get list of permissions that need to be requested
     * @return List of permission strings that are not granted
     */
    private fun getPermissionsToRequest(): List<String> {
        val permissionsToRequest = mutableListOf<String>()
        
        // Check camera permission
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        
        // Check storage permission (only for Android < 10)
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        
        return permissionsToRequest
    }
    
    /**
     * Check if we should show permission rationale for any of the permissions
     * @param permissions List of permissions to check
     * @return true if rationale should be shown
     */
    private fun shouldShowPermissionRationale(permissions: List<String>): Boolean {
        return permissions.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }
    
    /**
     * Request permissions from the system
     * @param permissions List of permissions to request
     */
    private fun requestPermissions(permissions: List<String>) {
        ActivityCompat.requestPermissions(
            activity,
            permissions.toTypedArray(),
            ALL_PERMISSIONS_REQUEST_CODE
        )
    }
    
    /**
     * Show permission rationale dialog with clear explanation
     * Moved from MainActivity
     * @param permissions List of permissions that need rationale
     */
    private fun showPermissionRationaleDialog(permissions: List<String>) {
        Log.d(TAG, "Showing permission rationale dialog for: ${permissions.joinToString()}")

        val permissionNames = mutableListOf<String>()
        val permissionReasons = mutableListOf<String>()

        if (permissions.contains(Manifest.permission.CAMERA)) {
            permissionNames.add("📸 Camera")
            permissionReasons.add("• Take photos to analyze with Gemma 3n AI")
        }

        if (permissions.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            permissionNames.add("📁 Storage")
            permissionReasons.add("• Save and access the AI model file (3.1GB)")
        }

        val message = "🤖 Gemma 3n Impact needs these permissions:\n\n" +
                "${permissionNames.joinToString("\n")}\n\n" +
                "📋 Why we need them:\n" +
                "${permissionReasons.joinToString("\n")}\n\n" +
                "🔒 Your privacy is protected:\n" +
                "• All AI processing happens on your device\n" +
                "• No data is sent to external servers\n" +
                "• You control what images to analyze"

        AlertDialog.Builder(activity)
            .setTitle("🔑 Permissions Required")
            .setMessage(message)
            .setPositiveButton("✅ Grant Permissions") { _, _ ->
                requestPermissions(permissions)
            }
            .setNegativeButton("❌ Cancel") { _, _ ->
                listener?.onPermissionsDenied(permissions)
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Handle permission request results from the system
     * @param requestCode The request code passed to requestPermissions
     * @param permissions The requested permissions
     * @param grantResults The grant results for the corresponding permissions
     */
    fun handlePermissionResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Log.d(TAG, "Handling permission result for request code: $requestCode")
        
        when (requestCode) {
            ALL_PERMISSIONS_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.d(TAG, "All permissions granted")
                    listener?.onPermissionsGranted()
                } else {
                    Log.d(TAG, "Some permissions denied")
                    handlePermissionDenial(permissions, grantResults)
                }
            }
            CAMERA_PERMISSION_REQUEST_CODE -> {
                // TODO: Handle individual camera permission result in Phase 2
                Log.d(TAG, "Camera permission result handled")
            }
            STORAGE_PERMISSION_REQUEST_CODE -> {
                // TODO: Handle individual storage permission result in Phase 2
                Log.d(TAG, "Storage permission result handled")
            }
        }
    }
    
    /**
     * Handle permission denial scenarios
     * Implementation will be completed in Phase 2
     * @param permissions The permissions that were requested
     * @param grantResults The results of the permission request
     */
    private fun handlePermissionDenial(permissions: Array<out String>, grantResults: IntArray) {
        Log.d(TAG, "Handling permission denial...")
        
        val deniedPermissions = mutableListOf<String>()
        val permanentlyDeniedPermissions = mutableListOf<String>()
        
        permissions.forEachIndexed { index, permission ->
            if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                    deniedPermissions.add(permission)
                } else {
                    permanentlyDeniedPermissions.add(permission)
                }
            }
        }
        
        when {
            permanentlyDeniedPermissions.isNotEmpty() -> {
                Log.d(TAG, "Permanently denied permissions: ${permanentlyDeniedPermissions.joinToString()}")
                listener?.onPermissionsPermanentlyDenied(permanentlyDeniedPermissions)
            }
            deniedPermissions.isNotEmpty() -> {
                Log.d(TAG, "Denied permissions: ${deniedPermissions.joinToString()}")
                listener?.onPermissionsDenied(deniedPermissions)
            }
        }
    }
    
    /**
     * Check if specific permission is granted
     * @param permission The permission to check
     * @return true if permission is granted
     */
    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Show dialog when permissions are temporarily denied
     * Moved from MainActivity
     */
    fun showPermissionDeniedDialog() {
        AlertDialog.Builder(activity)
            .setTitle("⚠️ Permissions Required")
            .setMessage("🤖 Gemma 3n Impact needs camera and storage permissions to function properly.\n\n" +
                    "📋 Without these permissions:\n" +
                    "• ❌ Cannot take photos for AI analysis\n" +
                    "• ❌ Cannot download or access AI model\n" +
                    "• ❌ App functionality will be limited\n\n" +
                    "💡 Please grant permissions to continue.")
            .setPositiveButton("🔄 Try Again") { _, _ ->
                checkAndRequestPermissions()
            }
            .setNegativeButton("❌ Continue Without") { _, _ ->
                listener?.onPermissionsDenied(REQUIRED_PERMISSIONS.toList())
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Show dialog when permissions are permanently denied
     * Moved from MainActivity
     */
    fun showPermanentPermissionDenialDialog() {
        AlertDialog.Builder(activity)
            .setTitle("🔧 Settings Required")
            .setMessage("🤖 Gemma 3n Impact needs permissions that were permanently denied.\n\n" +
                    "📋 To enable full functionality:\n" +
                    "1. 🔧 Open App Settings\n" +
                    "2. 🔑 Go to Permissions\n" +
                    "3. ✅ Enable Camera and Storage\n" +
                    "4. 🔄 Return to the app\n\n" +
                    "💡 This ensures the best AI experience!")
            .setPositiveButton("🔧 Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("❌ Continue Limited") { _, _ ->
                listener?.onPermissionsPermanentlyDenied(REQUIRED_PERMISSIONS.toList())
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Open app settings for manual permission management
     */
    fun openAppSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${activity.packageName}")
            activity.startActivity(intent)
            Log.d(TAG, "Opened app settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings", e)
        }
    }
}
