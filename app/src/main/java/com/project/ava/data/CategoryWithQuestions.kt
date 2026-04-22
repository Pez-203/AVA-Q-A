package com.project.ava.data

import androidx.room.Embedded
import androidx.room.Relation

data class CategoryWithQuestions(
    @Embedded val category: Category,
    @Relation(
        parentColumn = "id",
        entityColumn = "categoryId"
    )
    val questions: List<Question>
)
