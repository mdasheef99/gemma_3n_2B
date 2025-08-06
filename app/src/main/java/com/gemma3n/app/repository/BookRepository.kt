package com.gemma3n.app.repository

import android.content.Context
import com.gemma3n.app.data.Book
import com.gemma3n.app.data.BookDao
import com.gemma3n.app.data.BookstoreDatabase
import com.gemma3n.app.data.ConditionCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository for Book data operations.
 * 
 * Provides a clean API for data access and implements caching strategies
 * for better performance. Acts as a single source of truth for book data.
 */
class BookRepository private constructor(private val bookDao: BookDao) {

    // Simple in-memory cache for frequently accessed data
    private val locationCache = ConcurrentHashMap<String, List<String>>()
    private val conditionCountCache = ConcurrentHashMap<String, List<ConditionCount>>()
    private var cacheTimestamp = 0L
    private val cacheValidityDuration = 5 * 60 * 1000L // 5 minutes

    // ==================== BASIC CRUD OPERATIONS ====================

    /**
     * Get all books as a Flow for reactive UI updates.
     */
    fun getAllBooks(): Flow<List<Book>> = bookDao.getAllBooks()

    /**
     * Get a specific book by ID.
     */
    suspend fun getBookById(id: String): Book? = bookDao.getBookById(id)

    /**
     * Insert a single book with validation.
     */
    suspend fun insertBook(book: Book): Result<Unit> {
        return try {
            validateBook(book)
            bookDao.insertBook(book.copy(dateUpdated = System.currentTimeMillis()))
            invalidateCache()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Insert multiple books with validation.
     * Useful for batch operations from AI recognition.
     */
    suspend fun insertBooks(books: List<Book>): Result<Int> {
        return try {
            val validBooks = books.map { book ->
                validateBook(book)
                book.copy(dateUpdated = System.currentTimeMillis())
            }
            bookDao.insertBooks(validBooks)
            invalidateCache()
            Result.success(validBooks.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update an existing book with validation.
     */
    suspend fun updateBook(book: Book): Result<Unit> {
        return try {
            validateBook(book)
            bookDao.updateBook(book.copy(dateUpdated = System.currentTimeMillis()))
            invalidateCache()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a book.
     */
    suspend fun deleteBook(book: Book): Result<Unit> {
        return try {
            bookDao.deleteBook(book)
            invalidateCache()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a book by ID.
     */
    suspend fun deleteBookById(id: String): Result<Unit> {
        return try {
            bookDao.deleteBookById(id)
            invalidateCache()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== SEARCH OPERATIONS ====================

    /**
     * Search books with query string.
     */
    suspend fun searchBooks(query: String): Result<List<Book>> {
        return try {
            val results = bookDao.searchBooks(query.trim())
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get books by location with caching.
     */
    suspend fun getBooksByLocation(location: String): Result<List<Book>> {
        return try {
            val results = bookDao.getBooksByLocation(location)
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get books by condition.
     */
    suspend fun getBooksByCondition(condition: String): Result<List<Book>> {
        return try {
            validateCondition(condition)
            val results = bookDao.getBooksByCondition(condition)
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get books by author.
     */
    suspend fun getBooksByAuthor(author: String): Result<List<Book>> {
        return try {
            val results = bookDao.getBooksByAuthor(author.trim())
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get recently added books (last 7 days by default).
     */
    suspend fun getRecentBooks(days: Int = 7): Result<List<Book>> {
        return try {
            val sinceTimestamp = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
            val results = bookDao.getRecentBooks(sinceTimestamp)
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== ANALYTICS OPERATIONS ====================

    /**
     * Get total book count.
     */
    suspend fun getTotalBookCount(): Result<Int> {
        return try {
            val count = bookDao.getTotalBookCount()
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get total quantity of all books.
     */
    suspend fun getTotalQuantity(): Result<Int> {
        return try {
            val quantity = bookDao.getTotalQuantity() ?: 0
            Result.success(quantity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get total inventory value.
     */
    suspend fun getTotalInventoryValue(): Result<Double> {
        return try {
            val value = bookDao.getTotalInventoryValue() ?: 0.0
            Result.success(value)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all locations with caching.
     */
    suspend fun getAllLocations(): Result<List<String>> {
        return try {
            val cacheKey = "all_locations"
            val cached = getCachedData(cacheKey) { locationCache[cacheKey] }
            
            if (cached != null) {
                Result.success(cached)
            } else {
                val locations = bookDao.getAllLocations()
                locationCache[cacheKey] = locations
                Result.success(locations)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get low stock books.
     */
    suspend fun getLowStockBooks(threshold: Int = 2): Result<List<Book>> {
        return try {
            val results = bookDao.getLowStockBooks(threshold)
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== QUICK UPDATE OPERATIONS ====================

    /**
     * Update book quantity quickly.
     */
    suspend fun updateBookQuantity(id: String, newQuantity: Int): Result<Unit> {
        return try {
            if (newQuantity < 0) throw IllegalArgumentException("Quantity cannot be negative")
            bookDao.updateBookQuantity(id, newQuantity)
            invalidateCache()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update book price quickly.
     */
    suspend fun updateBookPrice(id: String, newPrice: Double): Result<Unit> {
        return try {
            if (newPrice < 0) throw IllegalArgumentException("Price cannot be negative")
            bookDao.updateBookPrice(id, newPrice)
            invalidateCache()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update book location quickly.
     */
    suspend fun updateBookLocation(id: String, newLocation: String): Result<Unit> {
        return try {
            bookDao.updateBookLocation(id, newLocation.trim())
            invalidateCache()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== VALIDATION METHODS ====================

    /**
     * Validates a book before database operations.
     */
    private fun validateBook(book: Book) {
        if (book.titleEnglish.isBlank()) {
            throw IllegalArgumentException("English title cannot be blank")
        }
        if (book.authorEnglish.isBlank()) {
            throw IllegalArgumentException("English author cannot be blank")
        }
        if (book.quantity < 0) {
            throw IllegalArgumentException("Quantity cannot be negative")
        }
        if (book.price != null && book.price < 0) {
            throw IllegalArgumentException("Price cannot be negative")
        }
        validateCondition(book.condition)
        validateConfidence(book.extractionConfidence)
    }

    /**
     * Validates book condition.
     */
    private fun validateCondition(condition: String) {
        if (condition !in Book.VALID_CONDITIONS) {
            throw IllegalArgumentException("Invalid condition: $condition. Valid conditions: ${Book.VALID_CONDITIONS}")
        }
    }

    /**
     * Validates extraction confidence.
     */
    private fun validateConfidence(confidence: String) {
        if (confidence !in Book.VALID_CONFIDENCE_LEVELS) {
            throw IllegalArgumentException("Invalid confidence: $confidence. Valid levels: ${Book.VALID_CONFIDENCE_LEVELS}")
        }
    }

    // ==================== CACHE MANAGEMENT ====================

    /**
     * Gets cached data if still valid.
     */
    private fun <T> getCachedData(key: String, getter: () -> T?): T? {
        return if (isCacheValid()) getter() else null
    }

    /**
     * Checks if cache is still valid.
     */
    private fun isCacheValid(): Boolean {
        return System.currentTimeMillis() - cacheTimestamp < cacheValidityDuration
    }

    /**
     * Invalidates all caches.
     */
    private fun invalidateCache() {
        locationCache.clear()
        conditionCountCache.clear()
        cacheTimestamp = 0L
    }

    companion object {
        @Volatile
        private var INSTANCE: BookRepository? = null

        /**
         * Gets the singleton instance of BookRepository.
         */
        fun getInstance(context: Context): BookRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = INSTANCE ?: BookRepository(
                    BookstoreDatabase.getDatabase(context).bookDao()
                )
                INSTANCE = instance
                instance
            }
        }

        /**
         * Clears the singleton instance (for testing).
         */
        fun clearInstance() {
            INSTANCE = null
        }
    }
}
