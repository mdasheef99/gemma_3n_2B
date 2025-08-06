package com.gemma3n.app.ai

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for BookRecognitionParser.
 * 
 * Tests structured response parsing with ##**## delimiters to ensure >95% accuracy
 * for book information extraction from AI responses.
 */
class BookRecognitionParserTest {
    
    private lateinit var parser: BookRecognitionParser
    
    @Before
    fun setUp() {
        parser = BookRecognitionParser()
    }
    
    // ==================== STANDARD FORMAT PARSING TESTS ====================
    
    @Test
    fun testStandardFormatParsing() {
        val aiResponse = """
##**##
I. 1. Atomic Habits
   2. James Clear
   3. ಪರಮಾಣು ಅಭ್ಯಾಸಗಳು
   4. ಜೇಮ್ಸ್ ಕ್ಲಿಯರ್
##**##
        """.trimIndent()
        
        val result = parser.parseResponse(aiResponse)
        
        assertTrue("Parsing should succeed", result.success)
        assertEquals("Should have 1 book", 1, result.books.size)
        
        val book = result.books[0]
        assertEquals("Atomic Habits", book.englishTitle)
        assertEquals("James Clear", book.englishAuthor)
        assertEquals("ಪರಮಾಣು ಅಭ್ಯಾಸಗಳು", book.kannadaTitle)
        assertEquals("ಜೇಮ್ಸ್ ಕ್ಲಿಯರ್", book.kannadaAuthor)
        assertEquals(BookRecognitionParser.ParsedBook.ParsingMethod.STANDARD, book.parsingMethod)
        assertTrue("Book should be valid", book.isValid())
    }
    
    @Test
    fun testMultipleBooksWithDelimiters() {
        val aiResponse = """
##**##
I. 1. The Alchemist
   2. Paulo Coelho
   3. 
   4. 
##**##
I. 1. Rich Dad Poor Dad
   2. Robert Kiyosaki
   3. ಶ್ರೀಮಂತ ತಂದೆ ಬಡ ತಂದೆ
   4. ರಾಬರ್ಟ್ ಕಿಯೋಸಾಕಿ
##**##
        """.trimIndent()
        
        val result = parser.parseResponse(aiResponse)
        
        assertTrue("Parsing should succeed", result.success)
        assertEquals("Should have 2 books", 2, result.books.size)
        
        val book1 = result.books[0]
        assertEquals("The Alchemist", book1.englishTitle)
        assertEquals("Paulo Coelho", book1.englishAuthor)
        assertNull("Kannada title should be null", book1.kannadaTitle)
        assertNull("Kannada author should be null", book1.kannadaAuthor)
        
        val book2 = result.books[1]
        assertEquals("Rich Dad Poor Dad", book2.englishTitle)
        assertEquals("Robert Kiyosaki", book2.englishAuthor)
        assertEquals("ಶ್ರೀಮಂತ ತಂದೆ ಬಡ ತಂದೆ", book2.kannadaTitle)
        assertEquals("ರಾಬರ್ಟ್ ಕಿಯೋಸಾಕಿ", book2.kannadaAuthor)
    }
    
    // ==================== ALTERNATIVE FORMAT PARSING TESTS ====================
    
    @Test
    fun testAlternativeFormat1() {
        val aiResponse = """
1. Atomic Habits
2. James Clear
3. ಪರಮಾಣು ಅಭ್ಯಾಸಗಳು
4. ಜೇಮ್ಸ್ ಕ್ಲಿಯರ್
        """.trimIndent()
        
        val result = parser.parseResponse(aiResponse)
        
        assertTrue("Parsing should succeed", result.success)
        assertEquals("Should have 1 book", 1, result.books.size)
        
        val book = result.books[0]
        assertEquals("Atomic Habits", book.englishTitle)
        assertEquals("James Clear", book.englishAuthor)
        assertEquals("ಪರಮಾಣು ಅಭ್ಯಾಸಗಳು", book.kannadaTitle)
        assertEquals("ಜೇಮ್ಸ್ ಕ್ಲಿಯರ್", book.kannadaAuthor)
        assertEquals(BookRecognitionParser.ParsedBook.ParsingMethod.ALTERNATIVE_1, book.parsingMethod)
    }
    
    @Test
    fun testAlternativeFormat2() {
        val aiResponse = """
Title: The 7 Habits of Highly Effective People
Author: Stephen R. Covey
Kannada Title: ಅತ್ಯಂತ ಪರಿಣಾಮಕಾರಿ ಜನರ ೭ ಅಭ್ಯಾಸಗಳು
Kannada Author: ಸ್ಟೀಫನ್ ಆರ್. ಕೋವೆ
        """.trimIndent()
        
        val result = parser.parseResponse(aiResponse)
        
        assertTrue("Parsing should succeed", result.success)
        assertEquals("Should have 1 book", 1, result.books.size)
        
        val book = result.books[0]
        assertEquals("The 7 Habits of Highly Effective People", book.englishTitle)
        assertEquals("Stephen R. Covey", book.englishAuthor)
        assertEquals("ಅತ್ಯಂತ ಪರಿಣಾಮಕಾರಿ ಜನರ ೭ ಅಭ್ಯಾಸಗಳು", book.kannadaTitle)
        assertEquals("ಸ್ಟೀಫನ್ ಆರ್. ಕೋವೆ", book.kannadaAuthor)
        assertEquals(BookRecognitionParser.ParsedBook.ParsingMethod.ALTERNATIVE_2, book.parsingMethod)
    }
    
    @Test
    fun testAlternativeFormat3() {
        val aiResponse = """
Book: Think and Grow Rich by Napoleon Hill (Kannada: ಯೋಚಿಸಿ ಮತ್ತು ಶ್ರೀಮಂತರಾಗಿ by ನೆಪೋಲಿಯನ್ ಹಿಲ್)
        """.trimIndent()
        
        val result = parser.parseResponse(aiResponse)
        
        assertTrue("Parsing should succeed", result.success)
        assertEquals("Should have 1 book", 1, result.books.size)
        
        val book = result.books[0]
        assertEquals("Think and Grow Rich", book.englishTitle)
        assertEquals("Napoleon Hill", book.englishAuthor)
        assertEquals("ಯೋಚಿಸಿ ಮತ್ತು ಶ್ರೀಮಂತರಾಗಿ", book.kannadaTitle)
        assertEquals("ನೆಪೋಲಿಯನ್ ಹಿಲ್", book.kannadaAuthor)
        assertEquals(BookRecognitionParser.ParsedBook.ParsingMethod.ALTERNATIVE_3, book.parsingMethod)
    }
    
    // ==================== FALLBACK PARSING TESTS ====================
    
    @Test
    fun testFallbackParsing() {
        val aiResponse = "I can see a book titled 'The Power of Now' by Eckhart Tolle on the shelf."
        
        val result = parser.parseResponse(aiResponse)
        
        assertTrue("Parsing should succeed", result.success)
        assertEquals("Should have 1 book", 1, result.books.size)
        
        val book = result.books[0]
        assertEquals("The Power of Now", book.englishTitle)
        assertEquals("Eckhart Tolle", book.englishAuthor)
        assertNull("Kannada title should be null", book.kannadaTitle)
        assertNull("Kannada author should be null", book.kannadaAuthor)
        assertEquals(BookRecognitionParser.ParsedBook.ParsingMethod.FALLBACK, book.parsingMethod)
    }
    
    // ==================== CONFIDENCE CALCULATION TESTS ====================
    
    @Test
    fun testConfidenceCalculation() {
        // High confidence: complete information with standard format
        val highConfidenceResponse = """
##**##
I. 1. Atomic Habits
   2. James Clear
   3. ಪರಮಾಣು ಅಭ್ಯಾಸಗಳು
   4. ಜೇಮ್ಸ್ ಕ್ಲಿಯರ್
##**##
        """.trimIndent()
        
        val highResult = parser.parseResponse(highConfidenceResponse)
        val highBook = highResult.books[0]
        val highConfidence = highBook.calculateConfidence()
        
        assertTrue("High confidence should be > 0.8", highConfidence > 0.8)
        
        // Medium confidence: English only
        val mediumConfidenceResponse = """
1. The Alchemist
2. Paulo Coelho
        """.trimIndent()
        
        val mediumResult = parser.parseResponse(mediumConfidenceResponse)
        val mediumBook = mediumResult.books[0]
        val mediumConfidence = mediumBook.calculateConfidence()
        
        assertTrue("Medium confidence should be 0.6-0.8", mediumConfidence >= 0.6 && mediumConfidence <= 0.8)
        
        // Low confidence: fallback parsing
        val lowConfidenceResponse = "Book: ABC by XYZ"
        
        val lowResult = parser.parseResponse(lowConfidenceResponse)
        val lowBook = lowResult.books[0]
        val lowConfidence = lowBook.calculateConfidence()
        
        assertTrue("Low confidence should be < 0.6", lowConfidence < 0.6)
    }
    
    // ==================== BOOK ENTITY CONVERSION TESTS ====================
    
    @Test
    fun testBookEntityConversion() {
        val parsedBook = BookRecognitionParser.ParsedBook(
            englishTitle = "Test Title",
            englishAuthor = "Test Author",
            kannadaTitle = "ಟೆಸ್ಟ್ ಶೀರ್ಷಿಕೆ",
            kannadaAuthor = "ಟೆಸ್ಟ್ ಲೇಖಕ",
            confidence = 0.9
        )
        
        val book = parsedBook.toBook()
        
        assertEquals("Test Title", book.titleEnglish)
        assertEquals("Test Author", book.authorEnglish)
        assertEquals("ಟೆಸ್ಟ್ ಶೀರ್ಷಿಕೆ", book.titleKannada)
        assertEquals("ಟೆಸ್ಟ್ ಲೇಖಕ", book.authorKannada)
        assertEquals("HIGH", book.extractionConfidence)
        assertEquals("AI", book.sourceType)
    }
    
    // ==================== ERROR HANDLING TESTS ====================
    
    @Test
    fun testEmptyResponse() {
        val result = parser.parseResponse("")
        
        assertFalse("Parsing should fail", result.success)
        assertEquals("Should have no books", 0, result.books.size)
        assertEquals("Empty AI response", result.errorMessage)
    }
    
    @Test
    fun testInvalidResponse() {
        val result = parser.parseResponse("This is just random text with no book information.")
        
        assertFalse("Parsing should fail", result.success)
        assertEquals("Should have no books", 0, result.books.size)
    }
    
    @Test
    fun testMalformedDelimiters() {
        val aiResponse = """
##**
I. 1. Incomplete Book
   2. Unknown Author
**##
        """.trimIndent()
        
        val result = parser.parseResponse(aiResponse)
        
        // Should still try to parse without proper delimiters
        assertTrue("Should attempt parsing", result.books.isNotEmpty() || !result.success)
    }
    
    // ==================== TEXT CLEANING TESTS ====================
    
    @Test
    fun testTextCleaning() {
        val aiResponse = """
##**##
I. 1. "The Clean Coder: A Code of Conduct for Professional Programmers"
   2. 'Robert C. Martin'
   3. 
   4. 
##**##
        """.trimIndent()
        
        val result = parser.parseResponse(aiResponse)
        
        assertTrue("Parsing should succeed", result.success)
        val book = result.books[0]
        
        // Quotes should be removed
        assertFalse("Title should not contain quotes", book.englishTitle.contains("\""))
        assertFalse("Author should not contain quotes", book.englishAuthor.contains("'"))
        
        assertEquals("The Clean Coder: A Code of Conduct for Professional Programmers", book.englishTitle)
        assertEquals("Robert C. Martin", book.englishAuthor)
    }
    
    // ==================== VALIDATION TESTS ====================
    
    @Test
    fun testResultValidation() {
        val validResponse = """
##**##
I. 1. Valid Title
   2. Valid Author
   3. 
   4. 
##**##
        """.trimIndent()
        
        val validResult = parser.parseResponse(validResponse)
        val validationIssues = parser.validateResult(validResult)
        
        assertTrue("Valid result should have no issues", validationIssues.isEmpty())
        
        val invalidResponse = """
##**##
I. 1. AB
   2. XY
   3. 
   4. 
##**##
        """.trimIndent()
        
        val invalidResult = parser.parseResponse(invalidResponse)
        val invalidationIssues = parser.validateResult(invalidResult)
        
        assertTrue("Invalid result should have issues", invalidationIssues.isNotEmpty())
        assertTrue("Should flag short title", invalidationIssues.any { it.contains("Title too short") })
        assertTrue("Should flag short author", invalidationIssues.any { it.contains("Author name too short") })
    }
    
    // ==================== PARSING ACCURACY TESTS ====================
    
    @Test
    fun testParsingAccuracy() {
        val testCases = listOf(
            // Standard format
            """
##**##
I. 1. Atomic Habits
   2. James Clear
   3. 
   4. 
##**##
            """.trimIndent(),
            
            // Alternative format 1
            """
1. The Alchemist
2. Paulo Coelho
            """.trimIndent(),
            
            // Alternative format 2
            """
Title: Rich Dad Poor Dad
Author: Robert Kiyosaki
            """.trimIndent(),
            
            // Alternative format 3
            """
Book: Think and Grow Rich by Napoleon Hill
            """.trimIndent(),
            
            // Fallback format
            "I can see 'The Power of Now' by Eckhart Tolle"
        )
        
        var successfulParses = 0
        
        testCases.forEach { testCase ->
            val result = parser.parseResponse(testCase)
            if (result.success && result.getValidBooks().isNotEmpty()) {
                successfulParses++
            }
        }
        
        val accuracy = successfulParses.toDouble() / testCases.size
        println("Parsing accuracy: ${(accuracy * 100).toInt()}%")
        
        assertTrue("Parsing accuracy should be >95%", accuracy >= 0.95)
    }
}
