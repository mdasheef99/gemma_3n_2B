package com.gemma3n.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for Book entity.
 * 
 * Provides all database operations for the bookstore inventory system including
 * CRUD operations, search functionality, and analytics queries.
 */
@Dao
interface BookDao {

    // ==================== BASIC CRUD OPERATIONS ====================

    /**
     * Get all books ordered by date added (newest first).
     * Returns a Flow for reactive UI updates.
     */
    @Query("SELECT * FROM books ORDER BY date_added DESC")
    fun getAllBooks(): Flow<List<Book>>

    /**
     * Get a specific book by its ID.
     */
    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: String): Book?

    /**
     * Insert a single book into the database.
     * Replaces existing book if ID conflicts.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book)

    /**
     * Insert multiple books into the database.
     * Useful for batch operations from AI recognition.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<Book>)

    /**
     * Update an existing book.
     */
    @Update
    suspend fun updateBook(book: Book)

    /**
     * Delete a book from the database.
     */
    @Delete
    suspend fun deleteBook(book: Book)

    /**
     * Delete a book by its ID.
     */
    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: String)

    /**
     * Delete all books from the database.
     * Use with caution - for testing purposes mainly.
     */
    @Query("DELETE FROM books")
    suspend fun deleteAllBooks()

    // ==================== SEARCH OPERATIONS ====================

    /**
     * Search books by title or author (both English and Kannada).
     * Case-insensitive partial matching.
     */
    @Query("""
        SELECT * FROM books
        WHERE title_english LIKE '%' || :query || '%'
           OR title_kannada LIKE '%' || :query || '%'
           OR author_english LIKE '%' || :query || '%'
           OR author_kannada LIKE '%' || :query || '%'
        ORDER BY date_added DESC
    """)
    suspend fun searchBooks(query: String): List<Book>

    /**
     * Get books by specific location.
     */
    @Query("SELECT * FROM books WHERE location = :location ORDER BY title_english ASC")
    suspend fun getBooksByLocation(location: String): List<Book>

    /**
     * Get books by condition (New, Used, Damaged).
     */
    @Query("SELECT * FROM books WHERE condition = :condition ORDER BY date_added DESC")
    suspend fun getBooksByCondition(condition: String): List<Book>

    /**
     * Get books by specific author (searches both English and Kannada).
     */
    @Query("""
        SELECT * FROM books 
        WHERE author_english LIKE '%' || :author || '%' 
           OR author_kannada LIKE '%' || :author || '%'
        ORDER BY title_english ASC
    """)
    suspend fun getBooksByAuthor(author: String): List<Book>

    /**
     * Get recently added books (within last N days).
     */
    @Query("""
        SELECT * FROM books 
        WHERE date_added >= :sinceTimestamp 
        ORDER BY date_added DESC
    """)
    suspend fun getRecentBooks(sinceTimestamp: Long): List<Book>

    /**
     * Search books with price range filter.
     */
    @Query("""
        SELECT * FROM books
        WHERE price >= :minPrice AND price <= :maxPrice
        ORDER BY price ASC
    """)
    suspend fun getBooksByPriceRange(minPrice: Double, maxPrice: Double): List<Book>

    // ==================== ANALYTICS QUERIES ====================

    /**
     * Get total number of books in inventory.
     */
    @Query("SELECT COUNT(*) FROM books")
    suspend fun getTotalBookCount(): Int

    /**
     * Get total quantity of all books (sum of individual quantities).
     */
    @Query("SELECT SUM(quantity) FROM books")
    suspend fun getTotalQuantity(): Int?

    /**
     * Get total inventory value (sum of price * quantity for all books).
     */
    @Query("SELECT SUM(price * quantity) FROM books WHERE price IS NOT NULL")
    suspend fun getTotalInventoryValue(): Double?

    /**
     * Get all unique locations in the inventory.
     */
    @Query("SELECT DISTINCT location FROM books WHERE location IS NOT NULL ORDER BY location")
    suspend fun getAllLocations(): List<String>

    /**
     * Get books with low stock (quantity <= threshold).
     */
    @Query("SELECT * FROM books WHERE quantity <= :threshold ORDER BY quantity ASC")
    suspend fun getLowStockBooks(threshold: Int = 2): List<Book>

    /**
     * Get books by extraction confidence level.
     */
    @Query("SELECT * FROM books WHERE extraction_confidence = :confidence ORDER BY date_added DESC")
    suspend fun getBooksByConfidence(confidence: String): List<Book>

    /**
     * Get count of books by condition.
     */
    @Query("SELECT condition, COUNT(*) as count FROM books GROUP BY condition")
    suspend fun getBookCountByCondition(): List<ConditionCount>

    /**
     * Get books that have source images (added via AI recognition).
     */
    @Query("SELECT * FROM books WHERE source_image_path IS NOT NULL ORDER BY date_added DESC")
    suspend fun getBooksWithImages(): List<Book>

    // ==================== ADVANCED QUERIES ====================

    /**
     * Get books with multilingual information (both English and Kannada).
     */
    @Query("""
        SELECT * FROM books 
        WHERE title_kannada IS NOT NULL 
          AND title_kannada != '' 
          AND author_kannada IS NOT NULL 
          AND author_kannada != ''
        ORDER BY title_english ASC
    """)
    suspend fun getMultilingualBooks(): List<Book>

    /**
     * Get duplicate books (same title and author).
     * Useful for inventory cleanup.
     */
    @Query("""
        SELECT * FROM books 
        WHERE (title_english, author_english) IN (
            SELECT title_english, author_english 
            FROM books 
            GROUP BY title_english, author_english 
            HAVING COUNT(*) > 1
        )
        ORDER BY title_english, author_english, date_added
    """)
    suspend fun getDuplicateBooks(): List<Book>

    /**
     * Update book quantity by ID.
     * Useful for quick inventory adjustments.
     */
    @Query("UPDATE books SET quantity = :newQuantity, date_updated = :timestamp WHERE id = :id")
    suspend fun updateBookQuantity(id: String, newQuantity: Int, timestamp: Long = System.currentTimeMillis())

    /**
     * Update book price by ID.
     */
    @Query("UPDATE books SET price = :newPrice, date_updated = :timestamp WHERE id = :id")
    suspend fun updateBookPrice(id: String, newPrice: Double, timestamp: Long = System.currentTimeMillis())

    /**
     * Update book location by ID.
     */
    @Query("UPDATE books SET location = :newLocation, date_updated = :timestamp WHERE id = :id")
    suspend fun updateBookLocation(id: String, newLocation: String, timestamp: Long = System.currentTimeMillis())
}

/**
 * Data class for condition count query results.
 */
data class ConditionCount(
    val condition: String,
    val count: Int
)
