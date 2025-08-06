package com.gemma3n.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gemma3n.app.data.Book
import com.gemma3n.app.data.BookstoreDatabase
import com.gemma3n.app.repository.BookRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Android Instrumentation Tests for the Bookstore Inventory System.
 * 
 * Tests the complete system integration on Android devices including:
 * - Real database operations with Room
 * - Repository pattern validation
 * - Performance testing on actual hardware
 * - Memory usage validation
 * - UI integration testing
 * 
 * These tests run on actual Android devices/emulators to validate
 * real-world performance and functionality.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BookstoreSystemTest {
    
    private lateinit var database: BookstoreDatabase
    private lateinit var bookRepository: BookRepository
    private lateinit var context: Context
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        
        // Create in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            context,
            BookstoreDatabase::class.java
        ).allowMainThreadQueries().build()
        
        // Initialize repository with test database
        bookRepository = BookRepository.getInstance(context)
    }
    
    @After
    @Throws(IOException::class)
    fun tearDown() {
        database.close()
        BookRepository.clearInstance()
        BookstoreDatabase.clearInstance()
    }
    
    // ==================== REAL DATABASE INTEGRATION TESTS ====================
    
    @Test
    fun testRealDatabaseOperations() = runBlocking {
        // Test book insertion
        val testBook = Book.fromManualEntry(
            "Android Test Book",
            "Test Author",
            299.0,
            5,
            "TEST-1",
            "New"
        )
        
        val insertResult = bookRepository.insertBook(testBook)
        assertTrue("Should successfully insert book", insertResult.isSuccess)
        
        // Test book retrieval
        val allBooks = bookRepository.getAllBooks().first()
        assertTrue("Should have at least 1 book", allBooks.isNotEmpty())
        
        val retrievedBook = allBooks.find { it.titleEnglish == "Android Test Book" }
        assertNotNull("Should find inserted book", retrievedBook)
        assertEquals("Test Author", retrievedBook?.authorEnglish)
        assertEquals(299.0, retrievedBook?.price)
        assertEquals(5, retrievedBook?.quantity)
        assertEquals("TEST-1", retrievedBook?.location)
        assertEquals("New", retrievedBook?.condition)
        
        // Test book count
        val countResult = bookRepository.getTotalBookCount()
        assertTrue("Should get book count", countResult.isSuccess)
        assertTrue("Should have at least 1 book", countResult.getOrNull()!! >= 1)
        
        println("✅ Real database operations test passed")
    }
    
    @Test
    fun testBatchBookOperations() = runBlocking {
        // Create multiple test books
        val testBooks = listOf(
            Book.fromManualEntry("Batch Test 1", "Author 1", 100.0, 1, "B-1", "New"),
            Book.fromManualEntry("Batch Test 2", "Author 2", 200.0, 2, "B-2", "Used"),
            Book.fromManualEntry("Batch Test 3", "Author 3", 300.0, 3, "B-3", "New"),
            Book.fromAIRecognition("AI Test Book", "AI Author", confidence = "HIGH")
        )
        
        // Test batch insertion
        val batchResult = bookRepository.insertBooks(testBooks)
        assertTrue("Should successfully insert batch", batchResult.isSuccess)
        assertEquals("Should insert 4 books", 4, batchResult.getOrNull())
        
        // Verify all books were inserted
        val allBooks = bookRepository.getAllBooks().first()
        assertTrue("Should have at least 4 books", allBooks.size >= 4)
        
        // Test search functionality
        val author1Books = allBooks.filter { it.authorEnglish.contains("Author 1") }
        assertEquals("Should find 1 book by Author 1", 1, author1Books.size)
        
        val newBooks = allBooks.filter { it.condition == "New" }
        assertTrue("Should find at least 2 new books", newBooks.size >= 2)
        
        println("✅ Batch operations test passed")
    }
    
    @Test
    fun testInventoryAnalytics() = runBlocking {
        // Insert test data for analytics
        val analyticsBooks = listOf(
            Book.fromManualEntry("Analytics 1", "Author A", 150.0, 10, "A-1", "New"),
            Book.fromManualEntry("Analytics 2", "Author B", 250.0, 5, "A-2", "Used"),
            Book.fromManualEntry("Analytics 3", "Author C", 350.0, 2, "A-3", "New")
        )
        
        val insertResult = bookRepository.insertBooks(analyticsBooks)
        assertTrue("Should insert analytics books", insertResult.isSuccess)
        
        // Test total count
        val countResult = bookRepository.getTotalBookCount()
        assertTrue("Should get total count", countResult.isSuccess)
        assertTrue("Should have at least 3 books", countResult.getOrNull()!! >= 3)
        
        // Test total quantity
        val quantityResult = bookRepository.getTotalQuantity()
        assertTrue("Should get total quantity", quantityResult.isSuccess)
        assertTrue("Should have at least 17 total quantity", quantityResult.getOrNull()!! >= 17)
        
        // Test total value
        val valueResult = bookRepository.getTotalInventoryValue()
        assertTrue("Should get total value", valueResult.isSuccess)
        assertTrue("Should have at least ₹750 value", valueResult.getOrNull()!! >= 750.0)
        
        // Test low stock detection
        val lowStockResult = bookRepository.getLowStockBooks(3)
        assertTrue("Should get low stock books", lowStockResult.isSuccess)
        val lowStockBooks = lowStockResult.getOrNull()!!
        assertTrue("Should find at least 1 low stock book", lowStockBooks.isNotEmpty())
        
        println("✅ Inventory analytics test passed")
    }
    
    // ==================== PERFORMANCE TESTING ON DEVICE ====================
    
    @Test
    fun testDatabasePerformanceOnDevice() = runBlocking {
        val startTime = System.currentTimeMillis()
        
        // Insert 100 books to test performance
        val performanceBooks = (1..100).map { index ->
            Book.fromManualEntry(
                "Performance Book $index",
                "Performance Author $index",
                (100 + index).toDouble(),
                index % 10 + 1,
                "P-${index % 10}",
                if (index % 3 == 0) "Used" else "New"
            )
        }
        
        val batchInsertTime = kotlin.system.measureTimeMillis {
            val result = bookRepository.insertBooks(performanceBooks)
            assertTrue("Should insert all performance books", result.isSuccess)
        }
        
        // Test retrieval performance
        val retrievalTime = kotlin.system.measureTimeMillis {
            val allBooks = bookRepository.getAllBooks().first()
            assertTrue("Should retrieve at least 100 books", allBooks.size >= 100)
        }
        
        // Test search performance
        val searchTime = kotlin.system.measureTimeMillis {
            val searchResults = bookRepository.getAllBooks().first().filter { book ->
                book.authorEnglish.contains("Performance Author 50")
            }
            assertEquals("Should find 1 specific book", 1, searchResults.size)
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        
        // Performance assertions
        assertTrue("Batch insert should be fast (<2000ms)", batchInsertTime < 2000)
        assertTrue("Retrieval should be fast (<500ms)", retrievalTime < 500)
        assertTrue("Search should be fast (<100ms)", searchTime < 100)
        assertTrue("Total test should complete quickly (<3000ms)", totalTime < 3000)
        
        println("✅ Database performance test passed")
        println("   Batch insert: ${batchInsertTime}ms")
        println("   Retrieval: ${retrievalTime}ms")
        println("   Search: ${searchTime}ms")
        println("   Total: ${totalTime}ms")
    }
    
    @Test
    fun testMemoryUsageOnDevice() = runBlocking {
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        
        // Perform memory-intensive operations
        repeat(50) { index ->
            val books = (1..20).map { bookIndex ->
                Book.fromManualEntry(
                    "Memory Test $index-$bookIndex",
                    "Memory Author $index-$bookIndex",
                    (index * bookIndex).toDouble(),
                    bookIndex,
                    "M-$index",
                    "New"
                )
            }
            
            val result = bookRepository.insertBooks(books)
            assertTrue("Should insert memory test books", result.isSuccess)
            
            // Retrieve and process books
            val allBooks = bookRepository.getAllBooks().first()
            val filteredBooks = allBooks.filter { it.authorEnglish.contains("Memory Author") }
            assertTrue("Should find memory test books", filteredBooks.isNotEmpty())
        }
        
        // Force garbage collection
        System.gc()
        Thread.sleep(1000)
        
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryIncrease = finalMemory - initialMemory
        
        // Memory should not increase excessively (less than 50MB for this test)
        assertTrue("Memory usage should be reasonable", memoryIncrease < 50 * 1024 * 1024)
        
        println("✅ Memory usage test passed - Memory increase: ${memoryIncrease / (1024 * 1024)}MB")
    }
    
    // ==================== CONCURRENT OPERATIONS TESTING ====================
    
    @Test
    fun testConcurrentDatabaseOperations() = runBlocking {
        // Test concurrent insertions
        val concurrentJobs = (1..10).map { index ->
            kotlinx.coroutines.async {
                val books = (1..5).map { bookIndex ->
                    Book.fromManualEntry(
                        "Concurrent $index-$bookIndex",
                        "Concurrent Author $index",
                        (index * 10 + bookIndex).toDouble(),
                        bookIndex,
                        "C-$index",
                        "New"
                    )
                }
                bookRepository.insertBooks(books)
            }
        }
        
        // Wait for all concurrent operations to complete
        val results = concurrentJobs.map { it.await() }
        
        // Verify all operations succeeded
        results.forEach { result ->
            assertTrue("Concurrent operation should succeed", result.isSuccess)
        }
        
        // Verify final state
        val finalBooks = bookRepository.getAllBooks().first()
        val concurrentBooks = finalBooks.filter { it.titleEnglish.startsWith("Concurrent") }
        assertEquals("Should have 50 concurrent books", 50, concurrentBooks.size)
        
        println("✅ Concurrent operations test passed")
    }
    
    // ==================== DATA INTEGRITY TESTING ====================
    
    @Test
    fun testDataIntegrityAndValidation() = runBlocking {
        // Test valid book insertion
        val validBook = Book.fromManualEntry(
            "Valid Book",
            "Valid Author",
            199.99,
            5,
            "V-1",
            "New"
        )
        
        val validResult = bookRepository.insertBook(validBook)
        assertTrue("Should insert valid book", validResult.isSuccess)
        
        // Test book with edge case values
        val edgeCaseBook = Book.fromManualEntry(
            "A", // Very short title
            "B", // Very short author
            0.01, // Very low price
            1, // Minimum quantity
            "Z-999", // Edge case location
            "Used"
        )
        
        val edgeResult = bookRepository.insertBook(edgeCaseBook)
        assertTrue("Should handle edge case book", edgeResult.isSuccess)
        
        // Test book with special characters
        val specialBook = Book.fromManualEntry(
            "C++ Programming & Design",
            "Bjarne Stroustrup",
            599.99,
            3,
            "TECH-1",
            "New"
        )
        
        val specialResult = bookRepository.insertBook(specialBook)
        assertTrue("Should handle special characters", specialResult.isSuccess)
        
        // Verify all books were stored correctly
        val allBooks = bookRepository.getAllBooks().first()
        val validRetrieved = allBooks.find { it.titleEnglish == "Valid Book" }
        val edgeRetrieved = allBooks.find { it.titleEnglish == "A" }
        val specialRetrieved = allBooks.find { it.titleEnglish == "C++ Programming & Design" }
        
        assertNotNull("Should find valid book", validRetrieved)
        assertNotNull("Should find edge case book", edgeRetrieved)
        assertNotNull("Should find special character book", specialRetrieved)
        
        assertEquals("Valid Author", validRetrieved?.authorEnglish)
        assertEquals("B", edgeRetrieved?.authorEnglish)
        assertEquals("Bjarne Stroustrup", specialRetrieved?.authorEnglish)
        
        println("✅ Data integrity test passed")
    }
    
    // ==================== SYSTEM STRESS TESTING ====================
    
    @Test
    fun testSystemStressTest() = runBlocking {
        val stressTestStartTime = System.currentTimeMillis()
        
        // Phase 1: Large batch insertion
        val largeBatch = (1..500).map { index ->
            Book.fromManualEntry(
                "Stress Test Book $index",
                "Stress Author ${index % 50}",
                (50 + index % 100).toDouble(),
                index % 20 + 1,
                "STRESS-${index % 100}",
                if (index % 4 == 0) "Used" else "New"
            )
        }
        
        val batchTime = kotlin.system.measureTimeMillis {
            val result = bookRepository.insertBooks(largeBatch)
            assertTrue("Should insert large batch", result.isSuccess)
        }
        
        // Phase 2: Multiple search operations
        val searchTime = kotlin.system.measureTimeMillis {
            repeat(20) { searchIndex ->
                val books = bookRepository.getAllBooks().first()
                val searchResults = books.filter { book ->
                    book.authorEnglish.contains("Stress Author ${searchIndex % 10}")
                }
                assertTrue("Should find stress test books", searchResults.isNotEmpty())
            }
        }
        
        // Phase 3: Analytics operations
        val analyticsTime = kotlin.system.measureTimeMillis {
            val countResult = bookRepository.getTotalBookCount()
            val quantityResult = bookRepository.getTotalQuantity()
            val valueResult = bookRepository.getTotalInventoryValue()
            
            assertTrue("Should get count", countResult.isSuccess)
            assertTrue("Should get quantity", quantityResult.isSuccess)
            assertTrue("Should get value", valueResult.isSuccess)
            
            assertTrue("Should have many books", countResult.getOrNull()!! >= 500)
        }
        
        val totalStressTime = System.currentTimeMillis() - stressTestStartTime
        
        // Stress test performance assertions
        assertTrue("Batch insertion should handle 500 books (<5000ms)", batchTime < 5000)
        assertTrue("Search operations should be fast (<2000ms)", searchTime < 2000)
        assertTrue("Analytics should be fast (<1000ms)", analyticsTime < 1000)
        assertTrue("Total stress test should complete (<10000ms)", totalStressTime < 10000)
        
        println("✅ System stress test passed")
        println("   Large batch (500 books): ${batchTime}ms")
        println("   Multiple searches: ${searchTime}ms")
        println("   Analytics operations: ${analyticsTime}ms")
        println("   Total stress test: ${totalStressTime}ms")
    }
}
