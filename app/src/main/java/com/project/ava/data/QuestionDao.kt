package com.project.ava.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions WHERE categoryId = :categoryId ORDER BY id")
    suspend fun getByCategoryId(categoryId: Long): List<Question>

    @Insert
    suspend fun insert(question: Question): Long

    @Update
    suspend fun update(question: Question)

    @Delete
    suspend fun delete(question: Question)
}
