package com.gemma3n.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException

/**
 * ImageProcessor handles all image-related operations
 * Separated from MainActivity for better maintainability and testing
 */
class ImageProcessor(private val context: Context) {
    
    /**
     * Interface for listening to image processing events
     */
    interface ImageProcessorListener {
        /**
         * Called when an image is successfully selected and processed
         * @param bitmap The processed image bitmap
         */
        fun onImageSelected(bitmap: Bitmap)
        
        /**
         * Called when an error occurs during image processing
         * @param error Human-readable error message
         */
        fun onImageError(error: String)
        
        /**
         * Called when image selection is cancelled by user
         */
        fun onImageSelectionCancelled()
    }
    
    private var listener: ImageProcessorListener? = null
    private var selectedBitmap: Bitmap? = null
    
    // Activity result launchers - will be initialized in Phase 2
    private var imagePickerLauncher: ActivityResultLauncher<String>? = null
    private var cameraLauncher: ActivityResultLauncher<Intent>? = null
    
    companion object {
        private const val TAG = "ImageProcessor"
        private const val MAX_IMAGE_SIZE = 2048 // Maximum image dimension in pixels
        private const val JPEG_QUALITY = 85 // JPEG compression quality
    }
    
    /**
     * Set the listener for image processing events
     * @param listener The listener to receive image events
     */
    fun setListener(listener: ImageProcessorListener) {
        this.listener = listener
        Log.d(TAG, "ImageProcessorListener set")
    }
    
    /**
     * Initialize the ImageProcessor with activity result launchers
     * Moved from MainActivity
     * @param activity The activity context for registering launchers
     */
    fun initialize(activity: AppCompatActivity) {
        Log.d(TAG, "Initializing ImageProcessor...")

        // Initialize image picker launcher
        imagePickerLauncher = activity.registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let { loadImageFromUri(it) }
        }

        // Initialize camera launcher
        cameraLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                @Suppress("DEPRECATION")
                val imageBitmap = result.data?.extras?.get("data") as? Bitmap
                processCameraBitmap(imageBitmap)
            } else {
                Log.d(TAG, "Camera capture cancelled or failed")
                listener?.onImageSelectionCancelled()
            }
        }

        Log.d(TAG, "ImageProcessor initialized successfully")
    }
    
    /**
     * Start image selection from gallery
     * Moved from MainActivity
     */
    fun selectImage() {
        Log.d(TAG, "Starting image selection from gallery...")

        if (imagePickerLauncher == null) {
            Log.w(TAG, "ImageProcessor not properly initialized - launcher is null")
            listener?.onImageError("Image picker not initialized")
            return
        }

        try {
            imagePickerLauncher?.launch("image/*")
            Log.d(TAG, "Image picker launched successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting image selection", e)
            listener?.onImageError("Failed to open gallery: ${e.message}")
        }
    }
    
    /**
     * Start photo capture from camera
     * Moved from MainActivity
     */
    fun takePhoto() {
        Log.d(TAG, "Starting photo capture from camera...")

        if (cameraLauncher == null) {
            Log.w(TAG, "ImageProcessor not properly initialized - camera launcher is null")
            listener?.onImageError("Camera not initialized")
            return
        }

        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            cameraLauncher?.launch(intent)
            Log.d(TAG, "Camera launcher started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting camera capture", e)
            listener?.onImageError("Failed to open camera: ${e.message}")
        }
    }
    
    /**
     * Load and process image from URI
     * Moved from MainActivity
     * @param uri The URI of the image to load
     */
    fun loadImageFromUri(uri: Uri) {
        Log.d(TAG, "Loading image from URI: $uri")

        try {
            @Suppress("DEPRECATION")
            val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)

            if (!isValidImage(bitmap)) {
                listener?.onImageError("Invalid image format or corrupted file")
                return
            }

            // Resize if needed
            val processedBitmap = resizeBitmapIfNeeded(bitmap)

            selectedBitmap = processedBitmap
            listener?.onImageSelected(processedBitmap)

            Log.d(TAG, "Image loaded successfully: ${processedBitmap.width}x${processedBitmap.height}")

        } catch (e: IOException) {
            Log.e(TAG, "Error loading image from URI", e)
            listener?.onImageError("Error loading image: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error loading image", e)
            listener?.onImageError("Failed to process image: ${e.message}")
        }
    }
    
    /**
     * Process camera result bitmap
     * Moved from MainActivity
     * @param bitmap The bitmap from camera capture
     */
    fun processCameraBitmap(bitmap: Bitmap?) {
        Log.d(TAG, "Processing camera bitmap...")

        if (bitmap == null) {
            Log.w(TAG, "Camera bitmap is null")
            listener?.onImageError("Camera capture failed - no image received")
            return
        }

        try {
            if (!isValidImage(bitmap)) {
                listener?.onImageError("Invalid camera image")
                return
            }

            // Resize if needed
            val processedBitmap = resizeBitmapIfNeeded(bitmap)

            selectedBitmap = processedBitmap
            listener?.onImageSelected(processedBitmap)

            Log.d(TAG, "Camera bitmap processed successfully: ${processedBitmap.width}x${processedBitmap.height}")

        } catch (e: Exception) {
            Log.e(TAG, "Error processing camera bitmap", e)
            listener?.onImageError("Error processing camera image: ${e.message}")
        }
    }
    
    /**
     * Get the currently selected bitmap
     * @return The current bitmap or null if none selected
     */
    fun getCurrentBitmap(): Bitmap? {
        return selectedBitmap
    }
    
    /**
     * Check if an image is currently selected
     * @return true if an image is selected
     */
    fun hasImage(): Boolean {
        return selectedBitmap != null
    }
    
    /**
     * Clear the currently selected image
     */
    fun clearImage() {
        Log.d(TAG, "Clearing selected image")
        selectedBitmap?.recycle()
        selectedBitmap = null
    }
    
    /**
     * Validate image dimensions and size
     * @param bitmap The bitmap to validate
     * @return true if image is valid for processing
     */
    private fun isValidImage(bitmap: Bitmap?): Boolean {
        if (bitmap == null) {
            Log.w(TAG, "Bitmap is null")
            return false
        }
        
        if (bitmap.isRecycled) {
            Log.w(TAG, "Bitmap is recycled")
            return false
        }
        
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "Invalid bitmap dimensions: ${width}x${height}")
            return false
        }
        
        if (width > MAX_IMAGE_SIZE || height > MAX_IMAGE_SIZE) {
            Log.w(TAG, "Image too large: ${width}x${height}, max: $MAX_IMAGE_SIZE")
            return false
        }
        
        Log.d(TAG, "Image validation passed: ${width}x${height}")
        return true
    }
    
    /**
     * Resize bitmap if it exceeds maximum dimensions
     * @param bitmap The bitmap to resize
     * @return Resized bitmap or original if no resize needed
     */
    private fun resizeBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= MAX_IMAGE_SIZE && height <= MAX_IMAGE_SIZE) {
            return bitmap
        }
        
        val scale = MAX_IMAGE_SIZE.toFloat() / maxOf(width, height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        Log.d(TAG, "Resizing image from ${width}x${height} to ${newWidth}x${newHeight}")
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true).also {
            // Recycle original if it's different from the new one
            if (it != bitmap) {
                bitmap.recycle()
            }
        }
    }
    
    /**
     * Get image information for debugging
     * @return String with image details
     */
    fun getImageInfo(): String {
        val bitmap = selectedBitmap
        return if (bitmap != null) {
            "Image: ${bitmap.width}x${bitmap.height}, " +
            "Config: ${bitmap.config}, " +
            "Size: ${bitmap.byteCount / 1024}KB"
        } else {
            "No image selected"
        }
    }
    
    /**
     * Clean up resources when ImageProcessor is no longer needed
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up ImageProcessor resources...")
        clearImage()
        listener = null
        imagePickerLauncher = null
        cameraLauncher = null
    }
}
