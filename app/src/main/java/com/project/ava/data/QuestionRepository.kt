package com.project.ava.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QuestionRepository(private val database: AppDatabase) {

    suspend fun getCategoryWithQuestions(qrCode: String): CategoryWithQuestions? {
        return withContext(Dispatchers.IO) {
            database.categoryDao().getWithQuestionsByQrCode(qrCode)
        }
    }
}
