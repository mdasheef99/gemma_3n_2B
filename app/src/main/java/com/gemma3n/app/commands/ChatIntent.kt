package com.gemma3n.app.commands

/**
 * Sealed class representing different types of chat intents.
 * 
 * This class hierarchy defines all possible user intents that can be detected
 * from natural language input in the chat interface, specifically for the
 * bookstore inventory management system.
 */
sealed class ChatIntent {
    
    /**
     * Regular chat message - not an inventory command.
     * Should be processed by the AI model for general conversation.
     */
    data class RegularChat(val message: String) : ChatIntent()
    
    /**
     * Book cataloging from image - user wants to add books from a photo.
     * Requires image processing and AI recognition.
     */
    data class BookCataloging(
        val message: String,
        val hasImage: Boolean = true
    ) : ChatIntent()
    
    /**
     * Manual book entry - user wants to add a book by typing details.
     * Format: "Add book: Title by Author" or similar variations.
     */
    data class ManualBookEntry(
        val message: String,
        val extractedTitle: String? = null,
        val extractedAuthor: String? = null,
        val extractedPrice: Double? = null,
        val extractedQuantity: Int? = null,
        val extractedLocation: String? = null
    ) : ChatIntent()
    
    /**
     * Inventory search - user wants to find books in the inventory.
     * Format: "Find books by author", "Search for title", etc.
     */
    data class InventorySearch(
        val query: String,
        val searchType: SearchType = SearchType.GENERAL
    ) : ChatIntent() {
        
        enum class SearchType {
            GENERAL,        // Search in title and author
            BY_TITLE,       // Search specifically in titles
            BY_AUTHOR,      // Search specifically by author
            BY_LOCATION,    // Search by location/shelf
            BY_CONDITION,   // Search by condition (New, Used, Damaged)
            RECENT,         // Show recently added books
            LOW_STOCK       // Show books with low stock
        }
    }
    
    /**
     * Update book information - user wants to modify existing book details.
     * Format: "Update price of book X to Y", "Move book X to location Y", etc.
     */
    data class UpdateBook(
        val message: String,
        val updateType: UpdateType = UpdateType.GENERAL,
        val bookIdentifier: String? = null,
        val newValue: String? = null
    ) : ChatIntent() {
        
        enum class UpdateType {
            GENERAL,        // General update, needs parsing
            PRICE,          // Update price
            QUANTITY,       // Update quantity
            LOCATION,       // Update location
            CONDITION       // Update condition
        }
    }
    
    /**
     * Delete book - user wants to remove a book from inventory.
     * Format: "Remove book X", "Delete book by title", etc.
     */
    data class DeleteBook(
        val message: String,
        val bookIdentifier: String? = null
    ) : ChatIntent()
    
    /**
     * Inventory analytics - user wants statistics about the inventory.
     * Format: "Show inventory stats", "How many books do we have?", etc.
     */
    data class InventoryAnalytics(
        val message: String,
        val analyticsType: AnalyticsType = AnalyticsType.GENERAL
    ) : ChatIntent() {
        
        enum class AnalyticsType {
            GENERAL,        // General statistics
            COUNT,          // Total book count
            VALUE,          // Total inventory value
            BY_CONDITION,   // Books by condition
            BY_LOCATION,    // Books by location
            LOW_STOCK,      // Low stock report
            RECENT_ACTIVITY // Recent additions/changes
        }
    }
    
    /**
     * Help request - user wants to know available inventory commands.
     * Format: "inventory help", "what can I do?", etc.
     */
    data class InventoryHelp(val message: String) : ChatIntent()
    
    /**
     * Export/Import operations - user wants to backup or restore inventory.
     * Format: "export inventory", "backup books", etc.
     */
    data class InventoryExport(
        val message: String,
        val exportType: ExportType = ExportType.FULL
    ) : ChatIntent() {
        
        enum class ExportType {
            FULL,           // Export all books
            BY_CONDITION,   // Export books by condition
            BY_LOCATION,    // Export books by location
            RECENT          // Export recently added books
        }
    }
    
    /**
     * Batch operations - user wants to perform operations on multiple books.
     * Format: "add all books from image", "update all prices by 10%", etc.
     */
    data class BatchOperation(
        val message: String,
        val operationType: BatchType = BatchType.ADD_MULTIPLE
    ) : ChatIntent() {
        
        enum class BatchType {
            ADD_MULTIPLE,       // Add multiple books at once
            UPDATE_MULTIPLE,    // Update multiple books
            DELETE_MULTIPLE,    // Delete multiple books
            MOVE_MULTIPLE       // Move multiple books to new location
        }
    }
    
    /**
     * Get the user's original message for any intent.
     */
    fun getOriginalMessage(): String {
        return when (this) {
            is RegularChat -> message
            is BookCataloging -> message
            is ManualBookEntry -> message
            is InventorySearch -> "Search: $query"
            is UpdateBook -> message
            is DeleteBook -> message
            is InventoryAnalytics -> message
            is InventoryHelp -> message
            is InventoryExport -> message
            is BatchOperation -> message
        }
    }
    
    /**
     * Check if this intent requires image processing.
     */
    fun requiresImage(): Boolean {
        return when (this) {
            is BookCataloging -> hasImage
            else -> false
        }
    }
    
    /**
     * Check if this intent is an inventory-related command.
     */
    fun isInventoryCommand(): Boolean {
        return when (this) {
            is RegularChat -> false
            else -> true
        }
    }
    
    /**
     * Get a human-readable description of the intent.
     */
    fun getDescription(): String {
        return when (this) {
            is RegularChat -> "General conversation"
            is BookCataloging -> "Add books from image"
            is ManualBookEntry -> "Add book manually"
            is InventorySearch -> "Search inventory: ${searchType.name.lowercase()}"
            is UpdateBook -> "Update book: ${updateType.name.lowercase()}"
            is DeleteBook -> "Delete book"
            is InventoryAnalytics -> "Show analytics: ${analyticsType.name.lowercase()}"
            is InventoryHelp -> "Show inventory help"
            is InventoryExport -> "Export inventory: ${exportType.name.lowercase()}"
            is BatchOperation -> "Batch operation: ${operationType.name.lowercase()}"
        }
    }
    
    companion object {
        /**
         * Get all possible inventory command types for help documentation.
         */
        fun getAllInventoryCommandTypes(): List<String> {
            return listOf(
                "Book Cataloging (from image)",
                "Manual Book Entry",
                "Inventory Search",
                "Update Book Information",
                "Delete Book",
                "Inventory Analytics",
                "Batch Operations",
                "Export/Import",
                "Help Commands"
            )
        }
    }
}
