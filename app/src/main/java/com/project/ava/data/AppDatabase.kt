package com.project.ava.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Category::class, Question::class], version = 9, exportSchema = false)
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

        private val MIGRATION_2_3 = object : Migration(2, 3) { override fun migrate(db: SupportSQLiteDatabase) {} }
        private val MIGRATION_3_4 = object : Migration(3, 4) { override fun migrate(db: SupportSQLiteDatabase) {} }
        private val MIGRATION_4_5 = object : Migration(4, 5) { override fun migrate(db: SupportSQLiteDatabase) {} }
        private val MIGRATION_5_6 = object : Migration(5, 6) { override fun migrate(db: SupportSQLiteDatabase) {} }
        private val MIGRATION_6_7 = object : Migration(6, 7) { override fun migrate(db: SupportSQLiteDatabase) {} }

        // Migración 8→9: igual que 7→8 pero con IDs explícitos y reset de sqlite_sequence
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM questions")
                db.execSQL("DELETE FROM categories")
                db.execSQL("DELETE FROM sqlite_sequence WHERE name='categories'")
                db.execSQL("DELETE FROM sqlite_sequence WHERE name='questions'")
                db.execSQL("INSERT INTO categories (id, title, qrCode) VALUES (1, 'Reinscripción', 'ESCOLAR_REINSCRIPCION.png')")
                db.execSQL("INSERT INTO categories (id, title, qrCode) VALUES (2, 'Titulación', 'ESCOLAR_TITULACION.png')")

                // ── REINSCRIPCIÓN (categoryId = 1) ──
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (1, '¿Cuándo inicia y termina el periodo de reinscripción?', 'Las fechas se publican en los avisos de la facultad y en SIASE. Para Ene-Jun 2026, primer ingreso fue el 15 de enero. Consulta el Calendario Académico y tu aviso de pago en SIASE.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (1, '¿Hay reinscripción anticipada?', 'La información sobre periodos anticipados se publica en la página de trámites escolares. El proceso se abre en fechas establecidas para cada semestre.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (1, '¿Qué promedio mínimo necesito?', 'No hay un promedio mínimo general publicado, pero mantener buen promedio es vital para becas como TITULA-T y trámites de titulación. Un promedio muy bajo puede dejarte como alumno irregular.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (1, '¿Puedo reinscribirme con materias reprobadas?', 'Sí, pero si agotaste oportunidades de examen o tienes abandono/suspensión, debes hacer una Inscripción de regularización de forma presencial en tu facultad.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (1, '¿El proceso es 100% en línea?', 'Principalmente sí, a través de SIASE (pre-solicitud y recibo de pago). Para casos especiales como regularización, debes acudir presencialmente al Depto. Escolar y de Archivo en la fecha asignada.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (1, '¿Cuánto cuesta la reinscripción?', 'Varía según tipo de alumno. Referencia Ene-Jun 2026: Cuota Interna 800, Servicios para enseñanza 2300, Material Didáctico 4700, Curso de Inglés desde 2590. Alumnos regulares: consulta tu aviso de pago en SIASE.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (1, '¿Dónde puedo pagar?', 'Sucursales Banorte, Seven Eleven, pago en línea o en las cajas de FIME.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (1, '¿Cómo me reinscribo si soy alumno irregular?', 'Acude directamente a tu facultad para la preinscripción y proceso de regularización. Una vez aceptado, el sistema te permite generar tu pago.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (1, '¿Qué pasa si no me reinscribo a tiempo?', 'Pierdes tu lugar como alumno regular. Si la ausencia es prolongada, aplicas como abandono y deberás solicitar reinscripción por proceso de regularización.', 0, 0)")

                // ── TITULACIÓN (categoryId = 2) ──
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Cuáles son las opciones de titulación?', 'Tesis, Curso por Materia de Posgrado, Competencia Laboral (solo egresados hasta julio 2008), Curso de Preparación para Examen Profesional (egresados 2003-2008).', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Necesito el 100% de créditos?', 'Sí. Tu Kárdex oficial completo es el primer requisito para iniciar cualquier trámite de titulación.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Necesito Servicio Social y Prácticas liberadas?', 'Sí, ambos son obligatorios. Debes presentar la carta de liberación de Servicio Social y la de Prácticas Profesionales.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Necesito comprobar un segundo idioma?', 'Sí, según tu año de egreso. 2008 en adelante: Comprobante aprobatorio (EXCI 50 pts, TOEFL 460 pts, o 6 niveles en Centro de Idiomas). 1995 a 2007: Comprobante no necesariamente aprobatorio. 1994 o antes: No es necesario.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Cuánto tiempo tengo para titularme?', 'No hay plazo único, pero para el Programa TITULA-T (becas) cuentas con 1 año después de tu egreso. Pasado ese año, ya no aplican las becas por desempeño.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Qué documentos necesito para titularme?', 'Kárdex oficial completo reciente, cartas de liberación (Servicio Social y Prácticas), comprobante de segundo idioma, encuesta de egresados contestada y pre-solicitud en SIASE.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Cuánto cuesta el trámite de titulación?', 'Costos UANL 2026: Título de Licenciatura 1237, Actas de examen y cédula 1886, Certificado de estudios 1430, Carta/diploma de pasantía 1036. Pueden existir costos adicionales propios de FIME.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Necesito un director de tesis?', 'Sí, para la modalidad de Tesis. Acude a la Coordinación de Titulación y Movilidad Académica (Edificio 1, primer piso) para su registro.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Quiénes son mis sinodales?', 'Los sinodales son asignados por la Coordinación de Titulación según la agenda, no los eliges tú.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿El examen profesional es presencial o en línea?', 'Actualmente se realiza en línea por medio de MS Teams.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Hay becas para titulación?', 'Sí, Programa TITULA-T: Sin extras y promedio mayor o igual a 85 cubre 100%, sin extras y promedio menor a 85 cubre 75%, con 2 extras y promedio mayor o igual a 85 cubre 50%, con 2 extras y promedio menor a 85 cubre 25%, Tesis 100%, Empleados UANL o hijos 100%.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Cómo contacto a la coordinación de titulación?', 'Correo: titulacion.fime@uanl.mx, MS Teams: Equipo Titulación-FIME, Egresados (CV y kárdex): egresados.fime@uanl.mx', 0, 0)")
            }
        }
        // Migración 7→8: borra todo, resetea autoincrement e inserta con IDs fijos
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM questions")
                db.execSQL("DELETE FROM categories")
                // Resetea el contador de autoincremento para que los IDs empiecen desde 1
                db.execSQL("DELETE FROM sqlite_sequence WHERE name='categories'")
                db.execSQL("DELETE FROM sqlite_sequence WHERE name='questions'")
                // Insertar categorías con IDs explícitos para garantizar el join con las preguntas
                db.execSQL("INSERT INTO categories (id, title, qrCode) VALUES (1, 'Reinscripción', 'ESCOLAR_REINSCRIPCION.png')")
                db.execSQL("INSERT INTO categories (id, title, qrCode) VALUES (2, 'Titulación', 'ESCOLAR_TITULACION.png')")

                // ── REINSCRIPCIÓN (categoryId = 1) ──
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (1, '¿Cuándo inicia y termina el periodo de reinscripción?', 'Las fechas se publican en los avisos de la facultad y en SIASE. Para Ene-Jun 2026, primer ingreso fue el 15 de enero. Consulta el Calendario Académico y tu aviso de pago en SIASE.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (1, '¿Hay reinscripción anticipada?', 'La información sobre periodos anticipados se publica en la página de trámites escolares. El proceso se abre en fechas establecidas para cada semestre.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (1, '¿Qué promedio mínimo necesito?', 'No hay un promedio mínimo general publicado, pero mantener buen promedio es vital para becas como TITULA-T y trámites de titulación. Un promedio muy bajo puede dejarte como alumno irregular.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (1, '¿Puedo reinscribirme con materias reprobadas?', 'Sí, pero si agotaste oportunidades de examen o tienes abandono/suspensión, debes hacer una \"Inscripción de regularización\" de forma presencial en tu facultad.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (1, '¿El proceso es 100% en línea?', 'Principalmente sí, a través de SIASE (pre-solicitud y recibo de pago). Para casos especiales como regularización, debes acudir presencialmente al Depto. Escolar y de Archivo en la fecha asignada.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (1, '¿Cuánto cuesta la reinscripción?', 'Varía según tipo de alumno. Referencia Ene-Jun 2026 (nuevo ingreso):\n• Cuota Interna: \$800\n• Servicios para enseñanza: \$2,300\n• Material Didáctico: \$4,700\n• Curso de Inglés (1er sem): desde \$2,590\nAlumnos regulares: consulta tu aviso de pago en SIASE.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (1, '¿Dónde puedo pagar?', 'Sucursales Banorte, Seven Eleven, pago en línea o en las cajas de FIME.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (1, '¿Cómo me reinscribo si soy alumno irregular?', 'Acude directamente a tu facultad para la preinscripción y proceso de regularización. Una vez aceptado, el sistema te permite generar tu pago.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (1, '¿Qué pasa si no me reinscribo a tiempo?', 'Pierdes tu lugar como alumno regular. Si la ausencia es prolongada, aplicas como \"abandono\" y deberás solicitar reinscripción por proceso de regularización.', 0, 0)")

                // ── TITULACIÓN (categoryId = 2) ──
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Cuáles son las opciones de titulación?', '• Tesis\n• Curso por Materia de Posgrado\n• Competencia Laboral (solo egresados hasta julio 2008)\n• Curso de Preparación para Examen Profesional (egresados 2003-2008)', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Necesito el 100% de créditos?', 'Sí. Tu Kárdex oficial completo es el primer requisito para iniciar cualquier trámite de titulación.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Necesito Servicio Social y Prácticas liberadas?', 'Sí, ambos son obligatorios. Debes presentar la carta de liberación de Servicio Social y la de Prácticas Profesionales.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Necesito comprobar un segundo idioma?', 'Sí, según tu año de egreso:\n• 2008 en adelante: Comprobante aprobatorio (EXCI: 50 pts, TOEFL: 460 pts, o 6 niveles en Centro de Idiomas).\n• 1995 a 2007: Comprobante (no necesariamente aprobatorio).\n• 1994 o antes: No es necesario.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Cuánto tiempo tengo para titularme?', 'No hay plazo único, pero para el Programa TITULA-T (becas) cuentas con 1 año después de tu egreso. Pasado ese año, ya no aplican las becas por desempeño.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Qué documentos necesito para titularme?', '• Kárdex oficial completo reciente\n• Cartas de liberación (Servicio Social y Prácticas)\n• Comprobante de segundo idioma\n• Encuesta de egresados contestada (captura)\n• Pre-solicitud en SIASE (captura)', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Cuánto cuesta el trámite de titulación?', 'Costos UANL 2026:\n• Título de Licenciatura: \$1,237\n• Actas de examen y cédula: \$1,886\n• Certificado de estudios: \$1,430\n• Carta/diploma de pasantía: \$1,036\nPueden existir costos adicionales propios de FIME.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Necesito un director de tesis?', 'Sí, para la modalidad de Tesis. Acude a la Coordinación de Titulación y Movilidad Académica (Edificio 1, primer piso) para su registro.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Quiénes son mis sinodales?', 'Los sinodales son asignados por la Coordinación de Titulación según la agenda, no los eliges tú.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿El examen profesional es presencial o en línea?', 'Actualmente se realiza en línea por medio de MS Teams.', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Hay becas para titulación?', 'Sí, Programa TITULA-T:\n• Sin extras y promedio ≥ 85: 100% beca\n• Sin extras y promedio < 85: 75%\n• 2 extras y promedio ≥ 85: 50%\n• 2 extras y promedio < 85: 25%\n• Tesis: 100% beca\n• Empleados UANL o hijos: 100% beca', 0, 0)")
                db.execSQL("INSERT INTO questions (categoryId, questionText, answerText, imageOffsetX, imageOffsetY) VALUES (2, '¿Cómo contacto a la coordinación de titulación?', '• Correo: titulacion.fime@uanl.mx\n• MS Teams: Equipo \"Titulación-FIME\"\n• Egresados (CV y kárdex): egresados.fime@uanl.mx', 0, 0)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ava_database"
                )
                    .fallbackToDestructiveMigration()
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                        MIGRATION_7_8, MIGRATION_8_9
                    )
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

            db.execSQL("INSERT INTO categories (title, qrCode) VALUES ('Reinscripción', 'ESCOLAR_REINSCRIPCION.png')")
            db.execSQL("INSERT INTO categories (title, qrCode) VALUES ('Titulación', 'ESCOLAR_TITULACION.png')")

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