package com.gemma3n.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for the bookstore inventory system.
 * 
 * This database manages all book inventory data with proper configuration
 * for production use, including migration support and singleton pattern.
 */
@Database(
    entities = [Book::class],
    version = 1,
    exportSchema = false // Set to true in production for schema versioning
)
abstract class BookstoreDatabase : RoomDatabase() {

    /**
     * Provides access to the BookDao for database operations.
     */
    abstract fun bookDao(): BookDao

    companion object {
        /**
         * Database name for the bookstore inventory.
         */
        private const val DATABASE_NAME = "bookstore_database"

        /**
         * Singleton instance of the database.
         * Volatile ensures that changes to INSTANCE are immediately visible to other threads.
         */
        @Volatile
        private var INSTANCE: BookstoreDatabase? = null

        /**
         * Gets the singleton instance of the database.
         * 
         * Uses double-checked locking pattern to ensure thread safety
         * while avoiding synchronization overhead after initialization.
         * 
         * @param context Application context for database creation
         * @return Singleton instance of BookstoreDatabase
         */
        fun getDatabase(context: Context): BookstoreDatabase {
            // Return existing instance if available
            return INSTANCE ?: synchronized(this) {
                // Double-check locking: check again inside synchronized block
                val instance = INSTANCE ?: buildDatabase(context)
                INSTANCE = instance
                instance
            }
        }

        /**
         * Builds the Room database with proper configuration.
         * 
         * @param context Application context
         * @return Configured BookstoreDatabase instance
         */
        private fun buildDatabase(context: Context): BookstoreDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                BookstoreDatabase::class.java,
                DATABASE_NAME
            )
                .addCallback(DatabaseCallback()) // Add database callback for initialization
                .addMigrations(*getAllMigrations()) // Add migration support
                .build()
        }

        /**
         * Returns all available database migrations.
         * Currently empty as we're on version 1, but ready for future migrations.
         */
        private fun getAllMigrations(): Array<Migration> {
            return arrayOf(
                // Future migrations will be added here
                // Example: MIGRATION_1_2, MIGRATION_2_3, etc.
            )
        }

        /**
         * Clears the singleton instance.
         * Useful for testing purposes.
         */
        fun clearInstance() {
            INSTANCE = null
        }
    }

    /**
     * Database callback for handling database creation and opening events.
     */
    private class DatabaseCallback : RoomDatabase.Callback() {
        
        /**
         * Called when the database is created for the first time.
         * Can be used to populate initial data if needed.
         */
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Database created successfully
            // Add any initial data population here if needed
        }

        /**
         * Called when the database is opened.
         * Can be used for database maintenance tasks.
         */
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            // Database opened successfully
            // Add any maintenance tasks here if needed
        }
    }
}

/**
 * Extension function to provide easy access to BookDao from Context.
 * 
 * Usage: context.bookDao()
 */
fun Context.bookDao(): BookDao {
    return BookstoreDatabase.getDatabase(this).bookDao()
}

/**
 * Future migration examples (for reference):
 * 
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(database: SupportSQLiteDatabase) {
 *         // Add new column example:
 *         database.execSQL("ALTER TABLE books ADD COLUMN isbn TEXT")
 *     }
 * }
 * 
 * val MIGRATION_2_3 = object : Migration(2, 3) {
 *     override fun migrate(database: SupportSQLiteDatabase) {
 *         // Create new table example:
 *         database.execSQL("""
 *             CREATE TABLE categories (
 *                 id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
 *                 name TEXT NOT NULL,
 *                 description TEXT
 *             )
 *         """)
 *     }
 * }
 */
