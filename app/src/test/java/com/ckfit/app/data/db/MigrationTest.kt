package com.ckfit.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Crea la BD con el esquema exportado de la v1 (app/schemas) y la abre con Room en la versión actual:
 * Room ejecuta las migraciones y valida que el esquema resultante coincide con las entidades.
 * (MigrationTestHelper solo lee los esquemas desde assets, que Robolectric no recibe en host tests.)
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbFile get() = context.getDatabasePath(TEST_DB)

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migracion1a2RellenaExerciseIdSinPerderHistorial() {
        createFromSchema(version = 1).apply {
            execSQL("INSERT INTO routine (id, name, isActive, createdAt) VALUES (1, 'Nivel 2', 1, 0)")
            execSQL(
                "INSERT INTO routine_day (id, routineId, dayOfWeek, title, colorHex, isOptional, isRestDay) " +
                    "VALUES (1, 1, 1, 'Pierna A', '#9FD33F', 0, 0)",
            )
            execSQL(
                "INSERT INTO exercise (id, name, muscleGroup, measureType, perSide, isCustom, archived) " +
                    "VALUES (7, 'Prensa', 'Cuádriceps', 'REPS', 0, 0, 0)",
            )
            execSQL(
                "INSERT INTO day_exercise (id, dayId, exerciseId, position, loadType, sets, repsMin, repsMax, " +
                    "restSec, weightStepKg, isIntensitySet, archived) VALUES (3, 1, 7, 0, 'STRENGTH', 4, 6, 8, 75, 2.5, 0, 0)",
            )
            execSQL(
                "INSERT INTO workout_session (id, plannedSessionId, routineDayId, date, startedAt, status) " +
                    "VALUES (1, NULL, 1, 20000, 0, 'DONE')",
            )
            execSQL("INSERT INTO session_exercise (id, sessionId, dayExerciseId, position, status) VALUES (1, 1, 3, 0, 'DONE')")
            execSQL("INSERT INTO session_exercise (id, sessionId, dayExerciseId, position, status) VALUES (2, 1, NULL, 1, 'SKIPPED')")
            execSQL(
                "INSERT INTO set_log (id, sessionExerciseId, setNumber, weightKg, reps, completedAt) VALUES (1, 1, 1, 120.0, 8, 0)",
            )
            // v4: la prensa aparece en otro día con otro peso; la plancha es de peso corporal.
            execSQL(
                "INSERT INTO routine_day (id, routineId, dayOfWeek, title, colorHex, isOptional, isRestDay) " +
                    "VALUES (2, 1, 4, 'Pierna B', '#2BA89A', 0, 0)",
            )
            execSQL(
                "INSERT INTO day_exercise (id, dayId, exerciseId, position, loadType, sets, repsMin, repsMax, " +
                    "restSec, weightStepKg, isIntensitySet, archived, currentWeightKg) VALUES (4, 2, 7, 0, 'STRENGTH', 4, 6, 8, 75, 2.5, 0, 0, 130.0)",
            )
            execSQL(
                "INSERT INTO exercise (id, name, muscleGroup, measureType, perSide, isCustom, archived) " +
                    "VALUES (8, 'Plancha lateral', 'Core', 'TIME', 1, 0, 0)",
            )
            close()
        }

        val db = Room.databaseBuilder(context, CKFitDatabase::class.java, TEST_DB)
            .addMigrations(*CKFitDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            // Abrir dispara la migración y la validación del esquema de Room.
            val sql = db.openHelper.writableDatabase
            sql.query("SELECT id, exerciseId FROM session_exercise ORDER BY id").use { c ->
                c.moveToNext()
                assertEquals(7L, c.getLong(1))
                c.moveToNext()
                assertEquals(true, c.isNull(1))
            }
            sql.query("SELECT COUNT(*) FROM set_log").use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
            sql.query("PRAGMA foreign_key_check").use { c -> assertEquals(0, c.count) }
            // v4: peso corporal sin peso y peso unificado por ejercicio (el mayor).
            sql.query("SELECT id, usesWeight FROM exercise ORDER BY id").use { c ->
                c.moveToNext(); assertEquals(1, c.getInt(1)) // Prensa
                c.moveToNext(); assertEquals(0, c.getInt(1)) // Plancha lateral
            }
            sql.query("SELECT currentWeightKg FROM day_exercise ORDER BY id").use { c ->
                while (c.moveToNext()) assertEquals(130.0, c.getDouble(0), 0.0)
            }
            // v3: el perfil gana el nombre, vacío tras migrar.
            sql.query("SELECT name FROM user_profile").use { c -> assertEquals(0, c.count) }
            assertNull(sql.query("PRAGMA user_version").use { c -> c.moveToFirst(); c.getInt(0).takeIf { it != 4 } })
        } finally {
            db.close()
        }
    }

    /** Ejecuta los createSql del esquema exportado, como hace MigrationTestHelper.createDatabase. */
    private fun createFromSchema(version: Int): SQLiteDatabase {
        val schema = Json.parseToJsonElement(File("schemas/${CKFitDatabase::class.java.name}/$version.json").readText())
            .jsonObject.getValue("database").jsonObject
        dbFile.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        schema.getValue("entities").jsonArray.forEach { e ->
            val entity = e.jsonObject
            val table = entity.getValue("tableName").jsonPrimitive.content
            db.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            entity["indices"]?.jsonArray?.forEach { i ->
                db.execSQL(i.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
        db.version = version
        return db
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
