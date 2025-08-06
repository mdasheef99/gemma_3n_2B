package com.gemma3n.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Book entity for the bookstore inventory system.
 * 
 * This entity represents a book in the inventory with all necessary fields
 * for basic bookstore management including multilingual support (English/Kannada),
 * inventory tracking, and AI recognition metadata.
 */
@Entity(tableName = "books")
data class Book(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    // Core book information
    @ColumnInfo(name = "title_english")
    val titleEnglish: String,

    @ColumnInfo(name = "title_kannada")
    val titleKannada: String? = null,

    @ColumnInfo(name = "author_english")
    val authorEnglish: String,

    @ColumnInfo(name = "author_kannada")
    val authorKannada: String? = null,

    // Inventory management fields
    @ColumnInfo(name = "location")
    val location: String? = null, // "1", "2", "A-5", "Shelf-15", etc.

    @ColumnInfo(name = "price")
    val price: Double? = null, // Price in rupees

    @ColumnInfo(name = "quantity")
    val quantity: Int = 1,

    @ColumnInfo(name = "condition")
    val condition: String = "New", // "New", "Used", "Damaged"

    // Metadata fields
    @ColumnInfo(name = "date_added")
    val dateAdded: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "date_updated")
    val dateUpdated: Long = System.currentTimeMillis(),

    // AI recognition metadata
    @ColumnInfo(name = "extraction_confidence")
    val extractionConfidence: String = "MEDIUM", // "HIGH", "MEDIUM", "LOW", "NONE"

    @ColumnInfo(name = "source_image_path")
    val sourceImagePath: String? = null // Path to the source image if added via AI recognition
) {
    /**
     * Returns a formatted display string for the book title.
     * Prefers English title, falls back to Kannada if English is not available.
     */
    fun getDisplayTitle(): String {
        return titleEnglish.ifBlank { titleKannada ?: "Unknown Title" }
    }

    /**
     * Returns a formatted display string for the book author.
     * Prefers English author, falls back to Kannada if English is not available.
     */
    fun getDisplayAuthor(): String {
        return authorEnglish.ifBlank { authorKannada ?: "Unknown Author" }
    }

    /**
     * Returns a formatted string for display in the chat interface.
     * Format: "Title by Author (₹Price, Qty: X, Location: Y)"
     */
    fun getDisplayString(): String {
        val title = getDisplayTitle()
        val author = getDisplayAuthor()
        val priceStr = price?.let { "₹$it" } ?: "No price"
        val locationStr = location?.let { "Location: $it" } ?: "No location"
        
        return "$title by $author ($priceStr, Qty: $quantity, $locationStr)"
    }

    /**
     * Returns true if this book has low stock (quantity <= threshold).
     */
    fun isLowStock(threshold: Int = 2): Boolean {
        return quantity <= threshold
    }

    /**
     * Returns true if this book has multilingual information (both English and Kannada).
     */
    fun isMultilingual(): Boolean {
        return !titleKannada.isNullOrBlank() && !authorKannada.isNullOrBlank()
    }

    /**
     * Returns the total value of this book entry (price * quantity).
     */
    fun getTotalValue(): Double? {
        return price?.let { it * quantity }
    }

    companion object {
        /**
         * Valid condition values for books.
         */
        val VALID_CONDITIONS = listOf("New", "Used", "Damaged")

        /**
         * Valid extraction confidence levels.
         */
        val VALID_CONFIDENCE_LEVELS = listOf("HIGH", "MEDIUM", "LOW", "NONE")

        /**
         * Creates a Book instance from AI recognition data.
         */
        fun fromAIRecognition(
            titleEnglish: String,
            authorEnglish: String,
            titleKannada: String? = null,
            authorKannada: String? = null,
            confidence: String = "MEDIUM",
            sourceImagePath: String? = null
        ): Book {
            return Book(
                titleEnglish = titleEnglish,
                authorEnglish = authorEnglish,
                titleKannada = titleKannada,
                authorKannada = authorKannada,
                extractionConfidence = confidence,
                sourceImagePath = sourceImagePath
            )
        }

        /**
         * Creates a Book instance from manual entry.
         */
        fun fromManualEntry(
            titleEnglish: String,
            authorEnglish: String,
            price: Double? = null,
            quantity: Int = 1,
            location: String? = null,
            condition: String = "New"
        ): Book {
            return Book(
                titleEnglish = titleEnglish,
                authorEnglish = authorEnglish,
                price = price,
                quantity = quantity,
                location = location,
                condition = condition,
                extractionConfidence = "NONE" // Manual entry, no AI extraction
            )
        }
    }
}
