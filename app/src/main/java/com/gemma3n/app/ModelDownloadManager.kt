package com.gemma3n.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * ModelDownloadManager handles downloading Gemma 3n models from Hugging Face
 * with proper authentication and progress tracking
 */
class ModelDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloadManager"

        // Hugging Face model URLs
        private const val GEMMA_3N_E2B_URL = "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/resolve/main/gemma-3n-E2B-it-int4.task"
        private const val GEMMA_3N_E4B_URL = "https://huggingface.co/google/gemma-3n-E4B-it-litert-preview/resolve/main/gemma-3n-E4B-it-int4.task"

        // Model file names
        const val GEMMA_3N_E2B_FILENAME = "gemma-3n-E2B-it-int4.task"
        const val GEMMA_3N_E4B_FILENAME = "gemma-3n-E4B-it-int4.task"

        // Expected file sizes (approximate)
        const val GEMMA_3N_E2B_SIZE_MB = 3100L // ~3.1GB
        const val GEMMA_3N_E4B_SIZE_MB = 4200L // ~4.2GB

        // HUGGING FACE TOKEN - Integrated for automatic downloads
        private const val HF_TOKEN = "YOUR_HF_TOKEN_HERE" // Replace with your actual Hugging Face token

        // Download management constants
        private const val REQUIRED_STORAGE_BUFFER_GB = 0.5f // Extra space buffer
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L // 1 second
        private const val MAX_RETRY_DELAY_MS = 8000L // 8 seconds

        // Expected SHA256 checksums (if available from Hugging Face)
        // Note: These would need to be obtained from the model repository
        private const val GEMMA_3N_E2B_SHA256 = "" // To be filled when available
        private const val GEMMA_3N_E4B_SHA256 = "" // To be filled when available

        // Test mode settings for development/testing
        private const val TEST_MODE = false // Set to false for production
        private const val TEST_DOWNLOAD_URL = "https://httpbin.org/bytes/10485760" // 10MB test file
        private const val TEST_FILE_SIZE_MB = 10L
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Progress tracking variables
    private var downloadStartTime: Long = 0
    private var lastProgressUpdate: Long = 0
    private var lastBytesDownloaded: Long = 0
    private var isDownloadCancelled: Boolean = false
    private var isDownloadInProgress: Boolean = false

    // Retry management variables
    private var currentRetryAttempt: Int = 0
    private var lastRetryDelay: Long = INITIAL_RETRY_DELAY_MS

    /**
     * Download progress callback interface
     */
    interface DownloadCallback {
        fun onProgress(bytesDownloaded: Long, totalBytes: Long, percentage: Int, speedMBps: Float, etaSeconds: Long)
        fun onSuccess(filePath: String)
        fun onError(error: String)
        fun onStarted()
        fun onCancelled()
    }

    /**
     * Cancel ongoing download
     */
    fun cancelDownload() {
        Log.d(TAG, "Download cancellation requested")
        isDownloadCancelled = true
        isDownloadInProgress = false
    }

    /**
     * Check if device has network connectivity
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Get available storage space in bytes
     */
    private fun getAvailableStorageBytes(): Long {
        return try {
            val externalDir = context.getExternalFilesDir(null)
            val stat = StatFs(externalDir?.path ?: Environment.getExternalStorageDirectory().path)
            stat.availableBytes
        } catch (e: Exception) {
            Log.e(TAG, "Error getting storage info", e)
            0L
        }
    }

    /**
     * Check if sufficient storage is available for download
     */
    private fun hasSufficientStorage(requiredSizeMB: Long): Boolean {
        val availableBytes = getAvailableStorageBytes()
        val requiredBytes = (requiredSizeMB + (REQUIRED_STORAGE_BUFFER_GB * 1024)) * 1024 * 1024
        return availableBytes >= requiredBytes
    }

    /**
     * Validate pre-download conditions
     */
    private fun validatePreDownloadConditions(requiredSizeMB: Long): String? {
        // Check network connectivity
        if (!isNetworkAvailable()) {
            return "No internet connection available. Please check your Wi-Fi or mobile data connection."
        }

        // Check storage space
        if (!hasSufficientStorage(requiredSizeMB)) {
            val availableGB = getAvailableStorageBytes() / (1024f * 1024f * 1024f)
            val requiredGB = (requiredSizeMB + (REQUIRED_STORAGE_BUFFER_GB * 1024)) / 1024f
            return "Insufficient storage space. Available: %.1f GB, Required: %.1f GB. Please free up %.1f GB of space.".format(
                availableGB, requiredGB, requiredGB - availableGB
            )
        }

        return null // All validations passed
    }

    /**
     * Calculate SHA256 checksum of a file
     */
    private fun calculateSHA256(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating SHA256", e)
            null
        }
    }

    /**
     * Verify file integrity after download
     */
    private fun verifyFileIntegrity(file: File, expectedSizeMB: Long, expectedSHA256: String?): Boolean {
        // Check if file exists
        if (!file.exists()) {
            Log.e(TAG, "File does not exist: ${file.absolutePath}")
            return false
        }

        // Check file size
        val actualSizeBytes = file.length()
        val expectedSizeBytes = expectedSizeMB * 1024 * 1024
        val sizeTolerance = 1024 * 1024 // 1MB tolerance

        if (Math.abs(actualSizeBytes - expectedSizeBytes) > sizeTolerance) {
            Log.e(TAG, "File size mismatch. Expected: $expectedSizeBytes bytes, Actual: $actualSizeBytes bytes")
            return false
        }

        // Check SHA256 if available
        if (!expectedSHA256.isNullOrEmpty()) {
            val actualSHA256 = calculateSHA256(file)
            if (actualSHA256 == null) {
                Log.e(TAG, "Failed to calculate file checksum")
                return false
            }

            if (actualSHA256 != expectedSHA256) {
                Log.e(TAG, "SHA256 checksum mismatch. Expected: $expectedSHA256, Actual: $actualSHA256")
                return false
            }

            Log.d(TAG, "SHA256 checksum verified successfully")
        } else {
            Log.d(TAG, "SHA256 verification skipped (checksum not available)")
        }

        Log.d(TAG, "File integrity verification passed")
        return true
    }

    /**
     * Clean up corrupted or partial files
     */
    private fun cleanupCorruptedFile(file: File) {
        try {
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "Cleanup corrupted file: ${file.name}, deleted: $deleted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up corrupted file", e)
        }
    }

    /**
     * Calculate next retry delay using exponential backoff
     */
    private fun calculateRetryDelay(): Long {
        val delay = minOf(lastRetryDelay * 2, MAX_RETRY_DELAY_MS)
        lastRetryDelay = delay
        return delay
    }

    /**
     * Reset retry state for new download attempt
     */
    private fun resetRetryState() {
        currentRetryAttempt = 0
        lastRetryDelay = INITIAL_RETRY_DELAY_MS
    }

    /**
     * Determine if error is retryable
     */
    private fun isRetryableError(error: Throwable): Boolean {
        return when (error) {
            is IOException -> true // Network errors are retryable
            is java.net.SocketTimeoutException -> true
            is java.net.ConnectException -> true
            is java.net.UnknownHostException -> false // DNS issues, not retryable
            else -> false
        }
    }

    /**
     * Download the Gemma 3n E2B model (recommended for Samsung S23)
     */
    suspend fun downloadGemma3nE2B(callback: DownloadCallback) {
        // Prevent multiple simultaneous downloads
        if (isDownloadInProgress) {
            Log.w(TAG, "Download already in progress, ignoring duplicate request")
            return
        }

        isDownloadInProgress = true
        isDownloadCancelled = false
        resetRetryState()

        if (TEST_MODE) {
            Log.d(TAG, "TEST MODE: Using test download instead of full model")
            downloadModelWithRetry(
                url = TEST_DOWNLOAD_URL,
                filename = "test_model.bin",
                expectedSizeMB = TEST_FILE_SIZE_MB,
                expectedSHA256 = null, // Skip checksum for test
                callback = callback
            )
        } else {
            downloadModelWithRetry(
                url = GEMMA_3N_E2B_URL,
                filename = GEMMA_3N_E2B_FILENAME,
                expectedSizeMB = GEMMA_3N_E2B_SIZE_MB,
                expectedSHA256 = GEMMA_3N_E2B_SHA256,
                callback = callback
            )
        }
    }

    /**
     * Download the Gemma 3n E4B model (larger, more capable)
     */
    suspend fun downloadGemma3nE4B(callback: DownloadCallback) {
        // Prevent multiple simultaneous downloads
        if (isDownloadInProgress) {
            Log.w(TAG, "Download already in progress, ignoring duplicate request")
            return
        }

        isDownloadInProgress = true
        isDownloadCancelled = false
        resetRetryState()
        downloadModelWithRetry(
            url = GEMMA_3N_E4B_URL,
            filename = GEMMA_3N_E4B_FILENAME,
            expectedSizeMB = GEMMA_3N_E4B_SIZE_MB,
            expectedSHA256 = GEMMA_3N_E4B_SHA256,
            callback = callback
        )
    }

    /**
     * Download model with comprehensive retry logic and validation
     */
    private suspend fun downloadModelWithRetry(
        url: String,
        filename: String,
        expectedSizeMB: Long,
        expectedSHA256: String?,
        callback: DownloadCallback
    ) {
        // Pre-download validation
        val validationError = validatePreDownloadConditions(expectedSizeMB)
        if (validationError != null) {
            withContext(Dispatchers.Main) {
                callback.onError(validationError)
            }
            return
        }

        val targetFile = File(context.getExternalFilesDir(null), filename)
        val tempFile = File(targetFile.absolutePath + ".tmp")

        // Retry loop with exponential backoff
        while (currentRetryAttempt <= MAX_RETRY_ATTEMPTS) {
            try {
                // Clean up any existing corrupted files
                cleanupCorruptedFile(targetFile)
                // Note: Keep temp file for resume capability

                // Attempt download
                downloadModel(url, filename, expectedSizeMB, callback)

                // Verify file integrity after successful download
                if (verifyFileIntegrity(targetFile, expectedSizeMB, expectedSHA256)) {
                    Log.d(TAG, "Download completed successfully with integrity verification")
                    isDownloadInProgress = false
                    withContext(Dispatchers.Main) {
                        callback.onSuccess(targetFile.absolutePath)
                    }
                    return
                } else {
                    throw IOException("File integrity verification failed")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Download attempt ${currentRetryAttempt + 1} failed", e)

                // Clean up corrupted files
                cleanupCorruptedFile(targetFile)
                cleanupCorruptedFile(tempFile)

                // Check if cancelled
                if (isDownloadCancelled) {
                    isDownloadInProgress = false
                    withContext(Dispatchers.Main) {
                        callback.onCancelled()
                    }
                    return
                }

                // Check if error is retryable and we have attempts left
                if (isRetryableError(e) && currentRetryAttempt < MAX_RETRY_ATTEMPTS) {
                    currentRetryAttempt++
                    val retryDelay = calculateRetryDelay()

                    Log.d(TAG, "Retrying download in ${retryDelay}ms (attempt $currentRetryAttempt/$MAX_RETRY_ATTEMPTS)")
                    delay(retryDelay)
                    continue
                } else {
                    // Final failure
                    val errorMessage = when {
                        !isRetryableError(e) -> "Download failed: ${e.message}. Please check your connection and try again."
                        currentRetryAttempt >= MAX_RETRY_ATTEMPTS -> "Download failed after $MAX_RETRY_ATTEMPTS attempts. Please check your connection and try again later."
                        else -> "Download failed: ${e.message}"
                    }

                    isDownloadInProgress = false
                    withContext(Dispatchers.Main) {
                        callback.onError(errorMessage)
                    }
                    return
                }
            }
        }
    }

    /**
     * Core download method with HF token authentication and resume support
     */
    private suspend fun downloadModel(
        url: String,
        filename: String,
        expectedSizeMB: Long,
        callback: DownloadCallback
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting download of $filename from $url")
            callback.onStarted()

            // Get the target file path in external storage
            val targetFile = File(context.getExternalFilesDir(null), filename)
            val tempFile = File(targetFile.absolutePath + ".tmp")

            // Create parent directories if they don't exist
            targetFile.parentFile?.mkdirs()

            // Check for existing partial download
            var resumeFromByte = 0L
            if (tempFile.exists() && tempFile.length() > 0) {
                resumeFromByte = tempFile.length()
                Log.d(TAG, "Found partial download, resuming from byte $resumeFromByte")
            }

            // Build request with HF authentication and range header for resume
            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $HF_TOKEN")
                .addHeader("User-Agent", "Gemma3nApp/1.0 (Android)")

            // Add range header for resume if needed
            if (resumeFromByte > 0) {
                requestBuilder.addHeader("Range", "bytes=$resumeFromByte-")
            }

            val request = requestBuilder.build()

            Log.d(TAG, "Making authenticated request to Hugging Face...")
            Log.d(TAG, "Request URL: $url")
            Log.d(TAG, "Request headers: Authorization=Bearer ${HF_TOKEN.take(10)}..., User-Agent=${request.header("User-Agent")}")

            val response: Response = httpClient.newCall(request).execute()

            // COMPREHENSIVE LOGGING - HTTP Response Details
            Log.d(TAG, "HTTP Response Code: ${response.code}")
            Log.d(TAG, "HTTP Response Message: ${response.message}")
            Log.d(TAG, "Response Headers: ${response.headers}")
            Log.d(TAG, "Content-Length Header: ${response.header("Content-Length")}")
            Log.d(TAG, "Content-Type Header: ${response.header("Content-Type")}")

            if (!response.isSuccessful) {
                val errorMsg = when (response.code) {
                    401 -> "Authentication failed. Please check your Hugging Face token."
                    403 -> "Access denied. You may need to request access to this model."
                    404 -> "Model not found. Please check the model URL."
                    else -> "Download failed with HTTP ${response.code}: ${response.message}"
                }
                Log.e(TAG, "Download failed: $errorMsg")
                callback.onError(errorMsg)
                return@withContext
            }

            val responseBody = response.body
            if (responseBody == null) {
                callback.onError("Empty response from server")
                return@withContext
            }

            // Handle content length for resume
            val contentLength = responseBody.contentLength()
            val totalBytes = if (resumeFromByte > 0) {
                resumeFromByte + contentLength
            } else {
                contentLength
            }

            // COMPREHENSIVE LOGGING - Download Details
            Log.d(TAG, "Content-Length from response: $contentLength bytes")
            Log.d(TAG, "Resume from byte: $resumeFromByte")
            Log.d(TAG, "Total expected bytes: $totalBytes")
            Log.d(TAG, "Expected file size: ${expectedSizeMB}MB (${expectedSizeMB * 1024 * 1024} bytes)")

            Log.d(TAG, "Download started. Total size: ${totalBytes / 1024 / 1024} MB, Resume from: ${resumeFromByte / 1024 / 1024} MB")

            // Initialize progress tracking
            downloadStartTime = System.currentTimeMillis()
            lastProgressUpdate = downloadStartTime
            lastBytesDownloaded = resumeFromByte

            // Download with enhanced progress tracking and resume support
            responseBody.byteStream().use { inputStream ->
                FileOutputStream(tempFile, resumeFromByte > 0).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesDownloaded = resumeFromByte
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        // Check for cancellation
                        if (isDownloadCancelled) {
                            Log.d(TAG, "Download cancelled by user")
                            withContext(Dispatchers.Main) {
                                callback.onCancelled()
                            }
                            return@withContext
                        }

                        outputStream.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        val currentTime = System.currentTimeMillis()

                        // COMPREHENSIVE LOGGING - Detailed Progress Tracking
                        if (bytesDownloaded % (10 * 1024 * 1024) == 0L) { // Log every 10MB
                            Log.d(TAG, "ACTUAL BYTES DOWNLOADED: $bytesDownloaded / $totalBytes (${(bytesDownloaded * 100 / totalBytes)}%)")
                        }

                        // Update progress every 500ms or every 1MB, whichever comes first
                        if (currentTime - lastProgressUpdate >= 500 ||
                            bytesDownloaded % (1024 * 1024) == 0L ||
                            bytesRead < buffer.size) {

                            val percentage = if (totalBytes > 0) {
                                ((bytesDownloaded * 100) / totalBytes).toInt()
                            } else {
                                0
                            }

                            // Calculate download speed and ETA
                            val elapsedSeconds = (currentTime - downloadStartTime) / 1000.0
                            val speedMBps = if (elapsedSeconds > 0) {
                                (bytesDownloaded / 1024.0 / 1024.0 / elapsedSeconds).toFloat()
                            } else {
                                0f
                            }

                            val etaSeconds = if (speedMBps > 0 && totalBytes > 0) {
                                val remainingBytes = totalBytes - bytesDownloaded
                                val remainingMB = remainingBytes / 1024.0 / 1024.0
                                (remainingMB / speedMBps).toLong()
                            } else {
                                0L
                            }

                            withContext(Dispatchers.Main) {
                                callback.onProgress(bytesDownloaded, totalBytes, percentage, speedMBps, etaSeconds)
                            }

                            lastProgressUpdate = currentTime
                            lastBytesDownloaded = bytesDownloaded
                        }
                    }
                }
            }

            Log.d(TAG, "Download completed successfully")

            // Move temp file to final location
            if (tempFile.exists() && tempFile.length() > 0) {
                val moved = tempFile.renameTo(targetFile)
                if (moved) {
                    Log.d(TAG, "File moved to final location: ${targetFile.absolutePath}")
                    // Note: Integrity verification is now handled in downloadModelWithRetry
                } else {
                    throw IOException("Failed to move downloaded file to final location")
                }
            } else {
                throw IOException("Downloaded file is corrupted or empty")
            }

        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Download failed with timeout", e)
            throw IOException("Connection timeout. Please check your internet connection and try again.")
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "Download failed with connection error", e)
            throw IOException("Cannot connect to server. Please check your internet connection.")
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Download failed with DNS error", e)
            throw IOException("Cannot resolve server address. Please check your internet connection.")
        } catch (e: IOException) {
            Log.e(TAG, "Download failed with IOException", e)
            // Clean up temp file on IO error
            val tempFile = File(context.getExternalFilesDir(null), filename + ".tmp")
            cleanupCorruptedFile(tempFile)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Download failed with unexpected error", e)
            // Clean up temp file on any error
            val tempFile = File(context.getExternalFilesDir(null), filename + ".tmp")
            cleanupCorruptedFile(tempFile)
            throw IOException("Download failed: ${e.message}")
        }
    }

    /**
     * Check if a model file exists and is valid
     */
    fun isModelAvailable(filename: String): Boolean {
        val file = File(context.getExternalFilesDir(null), filename)
        return file.exists() && file.length() > 0
    }

    /**
     * Get the full path for a model file
     */
    fun getModelPath(filename: String): String {
        return File(context.getExternalFilesDir(null), filename).absolutePath
    }

    /**
     * Delete a model file
     */
    fun deleteModel(filename: String): Boolean {
        val file = File(context.getExternalFilesDir(null), filename)
        return if (file.exists()) {
            file.delete()
        } else {
            true
        }
    }
}