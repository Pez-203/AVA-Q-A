package com.project.ava.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY title")
    suspend fun getAll(): List<Category>

    @Transaction
    @Query("""
        SELECT * FROM categories 
        WHERE UPPER(qrCode) = UPPER(:qrCode) 
           OR UPPER(qrCode) = UPPER(:qrCode || '.png') 
           OR UPPER(:qrCode) = UPPER(qrCode || '.png') 
        LIMIT 1
    """)
    suspend fun getWithQuestionsByQrCode(qrCode: String): CategoryWithQuestions?

    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)
}
