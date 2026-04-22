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
        private fun insertQ(db: SupportSQLiteDatabase, catId: Int, q: String, a: String) {
            db.execSQL(
                "INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (?, ?, ?, 0, 0)",
                arrayOf(catId, q, a)
            )
        }

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)

            db.execSQL("INSERT INTO categories (title, qrCode) VALUES ('Reinscripción', 'ESCOLAR_REINSCRIPCION')")
            db.execSQL("INSERT INTO categories (title, qrCode) VALUES ('Titulación', 'ESCOLAR_TITULACION')")

            // ── REINSCRIPCIÓN (categoryId = 1) ──

            insertQ(db, 1,
                "¿Cuándo inicia y termina el periodo de reinscripción?",
                "Las fechas se publican en los avisos de la facultad y en SIASE. Para Ene-Jun 2026, primer ingreso fue el 15 de enero. Consulta el Calendario Académico y tu aviso de pago en SIASE.")

            insertQ(db, 1,
                "¿Hay reinscripción anticipada?",
                "La información sobre periodos anticipados se publica en la página de trámites escolares. El proceso se abre en fechas establecidas para cada semestre.")

            insertQ(db, 1,
                "¿Qué promedio mínimo necesito?",
                "No hay un promedio mínimo general publicado, pero mantener buen promedio es vital para becas como TITULA-T y trámites de titulación. Un promedio muy bajo puede dejarte como alumno irregular.")

            insertQ(db, 1,
                "¿Puedo reinscribirme con materias reprobadas?",
                "Sí, pero si agotaste oportunidades de examen o tienes abandono/suspensión, debes hacer una \"Inscripción de regularización\" de forma presencial en tu facultad.")

            insertQ(db, 1,
                "¿El proceso es 100% en línea?",
                "Principalmente sí, a través de SIASE (pre-solicitud y recibo de pago). Para casos especiales como regularización, debes acudir presencialmente al Depto. Escolar y de Archivo en la fecha asignada.")

            insertQ(db, 1,
                "¿Cuánto cuesta la reinscripción?",
                "Varía según tipo de alumno. Referencia Ene-Jun 2026 (nuevo ingreso):\n• Cuota Interna: \$800\n• Servicios para enseñanza: \$2,300\n• Material Didáctico: \$4,700\n• Curso de Inglés (1er sem): desde \$2,590\nAlumnos regulares: consulta tu aviso de pago en SIASE.")

            insertQ(db, 1,
                "¿Dónde puedo pagar?",
                "Sucursales Banorte, Seven Eleven, pago en línea o en las cajas de FIME.")

            insertQ(db, 1,
                "¿Cómo me reinscribo si soy alumno irregular?",
                "Acude directamente a tu facultad para la preinscripción y proceso de regularización. Una vez aceptado, el sistema te permite generar tu pago.")

            insertQ(db, 1,
                "¿Qué pasa si no me reinscribo a tiempo?",
                "Pierdes tu lugar como alumno regular. Si la ausencia es prolongada, aplicas como \"abandono\" y deberás solicitar reinscripción por proceso de regularización.")

            // ── TITULACIÓN (categoryId = 2) ──

            insertQ(db, 2,
                "¿Cuáles son las opciones de titulación?",
                "• Tesis\n• Curso por Materia de Posgrado\n• Competencia Laboral (solo egresados hasta julio 2008)\n• Curso de Preparación para Examen Profesional (egresados 2003-2008)")

            insertQ(db, 2,
                "¿Necesito el 100% de créditos?",
                "Sí. Tu Kárdex oficial completo es el primer requisito para iniciar cualquier trámite de titulación.")

            insertQ(db, 2,
                "¿Necesito Servicio Social y Prácticas liberadas?",
                "Sí, ambos son obligatorios. Debes presentar la carta de liberación de Servicio Social y la de Prácticas Profesionales.")

            insertQ(db, 2,
                "¿Necesito comprobar un segundo idioma?",
                "Sí, según tu año de egreso:\n• 2008 en adelante: Comprobante aprobatorio (EXCI: 50 pts, TOEFL: 460 pts, o 6 niveles en Centro de Idiomas).\n• 1995 a 2007: Comprobante (no necesariamente aprobatorio).\n• 1994 o antes: No es necesario.")

            insertQ(db, 2,
                "¿Cuánto tiempo tengo para titularme?",
                "No hay plazo único, pero para el Programa TITULA-T (becas) cuentas con 1 año después de tu egreso. Pasado ese año, ya no aplican las becas por desempeño.")

            insertQ(db, 2,
                "¿Qué documentos necesito para titularme?",
                "• Kárdex oficial completo reciente\n• Cartas de liberación (Servicio Social y Prácticas)\n• Comprobante de segundo idioma\n• Encuesta de egresados contestada (captura)\n• Pre-solicitud en SIASE (captura)")

            insertQ(db, 2,
                "¿Cuánto cuesta el trámite de titulación?",
                "Costos UANL 2026:\n• Título de Licenciatura: \$1,237\n• Actas de examen y cédula: \$1,886\n• Certificado de estudios: \$1,430\n• Carta/diploma de pasantía: \$1,036\nPueden existir costos adicionales propios de FIME.")

            insertQ(db, 2,
                "¿Necesito un director de tesis?",
                "Sí, para la modalidad de Tesis. Acude a la Coordinación de Titulación y Movilidad Académica (Edificio 1, primer piso) para su registro.")

            insertQ(db, 2,
                "¿Quiénes son mis sinodales?",
                "Los sinodales son asignados por la Coordinación de Titulación según la agenda, no los eliges tú.")

            insertQ(db, 2,
                "¿El examen profesional es presencial o en línea?",
                "Actualmente se realiza en línea por medio de MS Teams.")

            insertQ(db, 2,
                "¿Hay becas para titulación?",
                "Sí, Programa TITULA-T:\n• Sin extras y promedio ≥ 85: 100% beca\n• Sin extras y promedio < 85: 75%\n• 2 extras y promedio ≥ 85: 50%\n• 2 extras y promedio < 85: 25%\n• Tesis: 100% beca\n• Empleados UANL o hijos: 100% beca")

            insertQ(db, 2,
                "¿Cómo contacto a la coordinación de titulación?",
                "• Correo: titulacion.fime@uanl.mx\n• MS Teams: Equipo \"Titulación-FIME\"\n• Egresados (CV y kárdex): egresados.fime@uanl.mx")
        }
    }
}
