package com.ckfit.app.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ckfit.app.data.db.entity.PlannedSessionEntity
import com.ckfit.app.data.db.entity.SessionExerciseEntity
import com.ckfit.app.data.db.entity.SetLogEntity
import com.ckfit.app.data.db.entity.WorkoutSessionEntity
import com.ckfit.app.domain.model.PlannedStatus
import com.ckfit.app.domain.model.SessionExerciseStatus
import com.ckfit.app.domain.model.SessionStatus
import com.ckfit.app.testing.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var env: TestEnv
    private val db get() = env.db
    private val seedLoader get() = env.seedLoader
    private val clock get() = env.clock

    @Before
    fun setUp() {
        env = TestEnv(LocalDate.of(2026, 9, 24)) // jueves
    }

    @After
    fun tearDown() = env.close()

    @Test
    fun seedCreaPerfilCatalogoYPlanActivo() = runTest {
        seedLoader.seedIfEmpty()

        assertNotNull(db.profileDao().get())
        val routine = db.routineDao().getActive()!!
        assertEquals("Nivel 2", routine.name)
        val days = db.routineDao().getDays(routine.id)
        assertEquals(7, days.size)
        assertTrue(days.last().isRestDay)
        val monday = db.dayExerciseDao().getForDay(days.first().id)
        assertEquals(7, monday.size)
        assertEquals("Prensa (leg press)", monday.first().exercise.name)
        assertEquals(35, db.dayExerciseDao().count())
        // 31 del plan + catálogo adicional
        assertTrue(db.exerciseDao().getAll().size >= 31 + 40)
    }

    @Test
    fun seedEsIdempotente() = runTest {
        seedLoader.seedIfEmpty()
        seedLoader.seedIfEmpty()
        assertEquals(1, db.routineDao().count())
        assertEquals(35, db.dayExerciseDao().count())
    }

    @Test
    fun borrarPlanBorraDiasYPrescripcionesEnCascada() = runTest {
        seedLoader.seedIfEmpty()
        val routine = db.routineDao().getActive()!!
        db.routineDao().deleteRoutine(routine)
        assertEquals(0, db.routineDao().getDays(routine.id).size)
        assertEquals(0, db.dayExerciseDao().count())
    }

    @Test
    fun borrarPrescripcionNoBorraHistorial() = runTest {
        seedLoader.seedIfEmpty()
        val day = db.routineDao().getDays(db.routineDao().getActive()!!.id).first()
        val prescription = db.dayExerciseDao().getForDay(day.id).first().dayExercise

        val sessionId = db.workoutSessionDao().insert(
            WorkoutSessionEntity(
                plannedSessionId = null,
                routineDayId = day.id,
                date = LocalDate.now(clock),
                startedAt = clock.instant(),
                status = SessionStatus.DONE,
            ),
        )
        val sessionExerciseId = db.sessionExerciseDao().insert(
            SessionExerciseEntity(
                sessionId = sessionId,
                dayExerciseId = prescription.id,
                exerciseId = prescription.exerciseId,
                position = 0,
                status = SessionExerciseStatus.DONE,
            ),
        )
        db.setLogDao().insert(
            SetLogEntity(sessionExerciseId = sessionExerciseId, setNumber = 1, weightKg = 120.0, reps = 8, completedAt = clock.instant()),
        )

        db.dayExerciseDao().delete(prescription)

        val kept = db.sessionExerciseDao().get(sessionExerciseId)!!
        assertNull(kept.dayExerciseId)
        assertEquals(prescription.exerciseId, kept.exerciseId)
        assertEquals(1, db.setLogDao().getForSessionExercise(sessionExerciseId).size)
        assertNotNull(db.workoutSessionDao().get(sessionId))
    }

    @Test
    fun semanaSeGeneraYLaAnteriorSeCierra() = runTest {
        seedLoader.seedIfEmpty()
        val repo = env.weekRepository
        val days = db.routineDao().getDays(db.routineDao().getActive()!!.id)
        val lastWeek = LocalDate.of(2026, 9, 14)
        // Semana anterior: lunes hecho, resto pendiente.
        db.plannedSessionDao().insertAll(
            days.filterNot { it.isRestDay }.map {
                PlannedSessionEntity(
                    weekStart = lastWeek,
                    routineDayId = it.id,
                    plannedDate = lastWeek.plusDays(it.dayOfWeek - 1L),
                    status = if (it.dayOfWeek == 1) PlannedStatus.DONE else PlannedStatus.PENDING,
                )
            },
        )

        repo.ensureCurrentWeek(LocalDate.now(clock))
        repo.ensureCurrentWeek(LocalDate.now(clock)) // idempotente

        val all = db.plannedSessionDao().getAll()
        val previous = all.filter { it.weekStart == lastWeek }.associateBy { p -> days.first { it.id == p.routineDayId }.dayOfWeek }
        assertEquals(PlannedStatus.DONE, previous.getValue(1).status)
        (2..4).forEach { assertEquals(PlannedStatus.MISSED, previous.getValue(it).status) }
        (5..6).forEach { assertEquals(PlannedStatus.PENDING, previous.getValue(it).status) }

        val current = LocalDate.of(2026, 9, 21)
        repo.observeWeek(current).test {
            val week = awaitItem()
            assertEquals(6, week.size)
            assertTrue(week.all { it.planned.status == PlannedStatus.PENDING })
            assertEquals("Pierna A (cuádriceps) + Pantorrilla + Bíceps", week.first().dayTitle)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(6, repo.observeWeek(current).first().size)
    }
}
