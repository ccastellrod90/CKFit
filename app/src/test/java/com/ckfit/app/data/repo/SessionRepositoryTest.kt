package com.ckfit.app.data.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ckfit.app.domain.model.PlannedStatus
import com.ckfit.app.domain.model.SessionExerciseStatus
import com.ckfit.app.domain.model.SessionStatus
import com.ckfit.app.domain.model.Side
import com.ckfit.app.domain.usecase.TodayCard
import com.ckfit.app.testing.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SessionRepositoryTest {

    private val monday = LocalDate.of(2026, 9, 21)
    private lateinit var env: TestEnv
    private val repo get() = env.sessionRepository
    private var mondayDayId = 0L
    private var mondayPlannedId = 0L

    @Before
    fun setUp() = runTest {
        env = TestEnv(monday)
        env.seedLoader.seedIfEmpty()
        env.weekRepository.ensureCurrentWeek(monday)
        val week = env.db.plannedSessionDao().getWeek(monday)
        mondayPlannedId = week.first { it.dayOfWeek == 1 }.planned.id
        mondayDayId = week.first { it.dayOfWeek == 1 }.planned.routineDayId!!
    }

    @After
    fun tearDown() = env.close()

    @Test
    fun empezarCreaLaSesionConLosEjerciciosDelDiaEnOrden() = runTest {
        val id = repo.start(mondayDayId, mondayPlannedId)
        val items = repo.getExercises(id)
        assertEquals(7, items.size)
        assertEquals("Prensa (leg press)", items.first().exercise!!.name)
        assertTrue(items.all { it.item.status == SessionExerciseStatus.PENDING && it.item.exerciseId != null })
        assertEquals(id, env.db.plannedSessionDao().get(mondayPlannedId)!!.sessionId)
        // Una segunda llamada devuelve la sesión en curso en lugar de crear otra.
        assertEquals(id, repo.start(mondayDayId, mondayPlannedId))
        // Home pasa a "Continuar" con su progreso.
        val card = env.observeHome(monday).first().today as TodayCard.Session
        assertTrue(card.inProgress)
        assertEquals(0 to 7, card.finished to card.total)
    }

    @Test
    fun seriesSeGuardanYSeReanudaDesdeLaUltima() = runTest {
        val id = repo.start(mondayDayId, mondayPlannedId)
        val zancada = repo.getExercises(id)[2]
        assertEquals(0, repo.lastSetNumber(zancada.item.id))
        repo.logSet(
            zancada.item.id,
            listOf(SetToLog(1, Side.LEFT, 12.0, 12, null), SetToLog(1, Side.RIGHT, 12.0, 12, null)),
        )
        assertEquals(1, repo.lastSetNumber(zancada.item.id))
        // Las dos filas de una serie por lado cuentan como una serie.
        assertEquals(1, repo.observeSetCounts(id).first().single().sets)
    }

    @Test
    fun terminarMarcaLaPlanificadaComoHechaYLoPendienteComoSaltado() = runTest {
        val id = repo.start(mondayDayId, mondayPlannedId)
        val first = repo.getExercises(id).first()
        repo.setExerciseStatus(first.item.id, SessionExerciseStatus.DONE)
        repo.finish(id)

        val session = env.db.workoutSessionDao().get(id)!!
        assertEquals(SessionStatus.DONE, session.status)
        assertTrue(session.endedAt != null)
        assertEquals(PlannedStatus.DONE, env.db.plannedSessionDao().get(mondayPlannedId)!!.status)
        val statuses = repo.getExercises(id).map { it.item.status }
        assertEquals(SessionExerciseStatus.DONE, statuses.first())
        assertTrue(statuses.drop(1).all { it == SessionExerciseStatus.SKIPPED })
        // La semana avanza: ahora toca el martes.
        assertEquals("Empuje: Pecho + Hombro + Tríceps", (env.observeHome(monday).first().today as TodayCard.Session).title)
    }

    @Test
    fun abandonarSinSeriesBorraLaSesionYLaDiaVuelveAPendiente() = runTest {
        val id = repo.start(mondayDayId, mondayPlannedId)
        repo.abandon(id, skipPlanned = false, skipReason = null)
        assertNull(env.db.workoutSessionDao().get(id))
        val planned = env.db.plannedSessionDao().get(mondayPlannedId)!!
        assertEquals(PlannedStatus.PENDING, planned.status)
        assertNull(planned.sessionId)
    }

    @Test
    fun saltarConSeriesGuardaElHistorialYSaltaLaPlanificada() = runTest {
        val id = repo.start(mondayDayId, mondayPlannedId)
        repo.logSet(repo.getExercises(id).first().item.id, listOf(SetToLog(1, null, 100.0, 8, null)))
        repo.abandon(id, skipPlanned = true, skipReason = " lesión ")
        assertEquals(SessionStatus.ABANDONED, env.db.workoutSessionDao().get(id)!!.status)
        val planned = env.db.plannedSessionDao().get(mondayPlannedId)!!
        assertEquals(PlannedStatus.SKIPPED, planned.status)
        assertEquals("lesión", planned.skipReason)
        assertEquals(1, env.db.setLogDao().countForSession(id))
    }

    @Test
    fun sesionExtraNoTocaLaSemana() = runTest {
        val sunday = env.db.routineDao().getDays(env.db.routineDao().getActive()!!.id).first { it.dayOfWeek == 6 }
        val id = repo.start(sunday.id, plannedId = null)
        repo.finish(id)
        assertTrue(env.db.plannedSessionDao().getWeek(monday).all { it.planned.status == PlannedStatus.PENDING })
    }

    @Test
    fun reordenarNoTocaElPlan() = runTest {
        val id = repo.start(mondayDayId, mondayPlannedId)
        val ids = repo.getExercises(id).map { it.item.id }
        repo.reorder(ids.reversed())
        assertEquals(ids.reversed(), repo.getExercises(id).map { it.item.id })
        assertEquals("Prensa (leg press)", env.db.dayExerciseDao().getForDay(mondayDayId).first().exercise.name)
    }
}
