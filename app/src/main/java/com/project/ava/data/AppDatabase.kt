package com.project.ava.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Category::class, Question::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun questionDao(): QuestionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions ADD COLUMN imageName TEXT")
                db.execSQL("ALTER TABLE questions ADD COLUMN imageOffsetX INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE questions ADD COLUMN imageOffsetY INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ava_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(SeedCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            db.execSQL(
                "INSERT INTO categories (title, qrCode) VALUES ('Reinscripciones', 'ESCOLAR_REINSCRIPCION')"
            )
            db.execSQL(
                "INSERT INTO categories (title, qrCode) VALUES ('Titulación', 'ESCOLAR_TITULACION')"
            )
            db.execSQL(
                "INSERT INTO questions (categoryId, questionText, answerText) VALUES (1, '¿Cuándo es el periodo de reinscripción?', 'Enero para primavera, agosto para otoño. Ver fechas en SIIU.')"
            )
            db.execSQL(
                "INSERT INTO questions (categoryId, questionText, answerText) VALUES (2, '¿Qué documentos necesito para mi carta de pasante?', 'Solicitud en SIIU, kárdex, acta de nacimiento, CURP, no adeudo y pago.')"
            )
        }
    }
}
