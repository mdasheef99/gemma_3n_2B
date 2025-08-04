package com.gemma3n.app

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * ErrorHandler provides centralized error management and user-friendly error display
 * Separated from MainActivity for better maintainability and consistent error handling
 */
class ErrorHandler(private val context: Context) {
    
    companion object {
        private const val TAG = "ErrorHandler"
    }
    
    /**
     * Handle processing errors during AI inference
     * @param error The exception that occurred
     */
    fun handleProcessingError(error: Throwable) {
        Log.e(TAG, "Processing error occurred", error)
        
        val userFriendlyMessage = when {
            error.message?.contains("Model not loaded") == true -> 
                "AI model is not ready. Please wait for initialization to complete."
            
            error.message?.contains("Out of memory") == true -> 
                "Not enough memory to process this image. Try a smaller image."
            
            error.message?.contains("Invalid image") == true -> 
                "The selected image format is not supported. Please try a different image."
            
            error.message?.contains("Network") == true -> 
                "Network error occurred. Please check your connection and try again."
            
            else -> "An error occurred while processing your request. Please try again."
        }
        
        showErrorToast("Processing Error: $userFriendlyMessage")
    }
    
    /**
     * Handle model initialization errors
     * @param error The exception that occurred during model initialization
     */
    fun handleModelError(error: Throwable) {
        Log.e(TAG, "Model error occurred", error)
        
        val userFriendlyMessage = cleanUpErrorMessage(error.message ?: "Unknown error")
        showErrorDialog(
            title = "AI Model Error",
            message = "Failed to initialize the Gemma 3n AI model.\n\n" +
                    "Error: $userFriendlyMessage\n\n" +
                    "Possible solutions:\n" +
                    "• Restart the app\n" +
                    "• Re-download the model file\n" +
                    "• Check available storage space\n" +
                    "• Contact support if the issue persists"
        )
    }
    
    /**
     * Handle download errors
     * @param error The error message from download failure
     */
    fun handleDownloadError(error: String) {
        Log.e(TAG, "Download error: $error")
        
        val userFriendlyMessage = when {
            error.contains("Network") || error.contains("Connection") -> 
                "Network connection failed. Please check your internet connection and try again."
            
            error.contains("Storage") || error.contains("Space") -> 
                "Not enough storage space. Please free up at least 4GB and try again."
            
            error.contains("Permission") -> 
                "Storage permission required. Please grant storage permission and try again."
            
            error.contains("Timeout") -> 
                "Download timed out. Please check your connection and try again."
            
            else -> "Download failed: $error"
        }
        
        showErrorDialog(
            title = "Download Failed",
            message = "$userFriendlyMessage\n\n" +
                    "Troubleshooting tips:\n" +
                    "• Ensure stable WiFi connection\n" +
                    "• Check available storage (need 4GB+)\n" +
                    "• Try downloading during off-peak hours\n" +
                    "• Restart the app if needed"
        )
    }
    
    /**
     * Handle permission errors
     * @param missingPermissions List of permissions that are missing
     */
    fun handlePermissionError(missingPermissions: List<String>) {
        Log.e(TAG, "Permission error - missing: ${missingPermissions.joinToString()}")
        
        val permissionNames = missingPermissions.map { permission ->
            when (permission) {
                android.Manifest.permission.CAMERA -> "Camera"
                android.Manifest.permission.READ_EXTERNAL_STORAGE -> "Storage"
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE -> "Storage"
                else -> permission.substringAfterLast(".")
            }
        }
        
        showErrorDialog(
            title = "Permissions Required",
            message = "The following permissions are required for the app to function:\n\n" +
                    "${permissionNames.joinToString("\n") { "• $it" }}\n\n" +
                    "Please grant these permissions in your device settings:\n" +
                    "Settings > Apps > Gemma 3n Impact > Permissions"
        )
    }
    
    /**
     * Handle image processing errors
     * @param error The error that occurred during image processing
     */
    fun handleImageError(error: String) {
        Log.e(TAG, "Image error: $error")
        
        val userFriendlyMessage = when {
            error.contains("format") || error.contains("Format") -> 
                "Unsupported image format. Please use JPG, PNG, or WebP images."
            
            error.contains("size") || error.contains("large") -> 
                "Image is too large. Please use an image smaller than 10MB."
            
            error.contains("corrupted") || error.contains("invalid") -> 
                "Image file appears to be corrupted. Please try a different image."
            
            error.contains("permission") -> 
                "Cannot access image. Please check storage permissions."
            
            else -> "Image processing failed: $error"
        }
        
        showErrorToast(userFriendlyMessage)
    }
    
    /**
     * Handle validation errors (user input validation)
     * @param message The validation error message
     */
    fun showValidationError(message: String) {
        Log.w(TAG, "Validation error: $message")
        showErrorToast(message)
    }
    
    /**
     * Show a simple error toast message
     * @param message The error message to display
     */
    private fun showErrorToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    
    /**
     * Show an error dialog with detailed information
     * @param title The dialog title
     * @param message The detailed error message
     */
    private fun showErrorDialog(title: String, message: String) {
        try {
            AlertDialog.Builder(context)
                .setTitle("⚠️ $title")
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .setCancelable(true)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show error dialog", e)
            // Fallback to toast if dialog fails
            showErrorToast("$title: $message")
        }
    }
    
    /**
     * Clean up MediaPipe and system error messages for user display
     * @param message The raw error message
     * @return Cleaned up user-friendly message
     */
    private fun cleanUpErrorMessage(message: String): String {
        return when {
            message.contains("Unable to open zip archive") -> 
                "Model file is corrupted or incomplete"
            
            message.contains("Failed to initialize engine") -> 
                "Model file format is invalid"
            
            message.contains("Model file not found") -> 
                "Model file is missing"
            
            message.contains("Insufficient memory") -> 
                "Not enough device memory available"
            
            message.contains("GPU") && message.contains("failed") -> 
                "GPU acceleration failed, trying CPU mode"
            
            message.contains("MediaPipe") -> 
                "AI processing engine error"
            
            message.length > 100 -> 
                message.take(100) + "..." // Limit length for display
            
            else -> message
        }
    }
    
    /**
     * Log error for debugging purposes
     * @param tag The log tag
     * @param message The error message
     * @param throwable Optional throwable for stack trace
     */
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
    
    /**
     * Log warning for debugging purposes
     * @param tag The log tag
     * @param message The warning message
     */
    fun logWarning(tag: String, message: String) {
        Log.w(tag, message)
    }
    
    /**
     * Show a generic error state with retry option
     * @param onRetry Callback for retry action
     */
    fun showGenericErrorWithRetry(onRetry: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("⚠️ Something Went Wrong")
            .setMessage("An unexpected error occurred.\n\n" +
                    "Would you like to try again?")
            .setPositiveButton("🔄 Retry") { _, _ -> onRetry() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setCancelable(true)
            .show()
    }
    
    /**
     * Handle network-related errors
     * @param error The network error
     */
    fun handleNetworkError(error: Throwable) {
        Log.e(TAG, "Network error occurred", error)
        
        val message = when {
            error.message?.contains("timeout") == true -> 
                "Connection timed out. Please check your internet connection and try again."
            
            error.message?.contains("host") == true -> 
                "Cannot reach server. Please check your internet connection."
            
            else -> 
                "Network error occurred. Please check your connection and try again."
        }
        
        showErrorToast(message)
    }
}
