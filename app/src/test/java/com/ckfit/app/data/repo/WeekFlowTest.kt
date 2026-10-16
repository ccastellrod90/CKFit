package com.ckfit.app.data.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ckfit.app.domain.model.PlannedStatus
import com.ckfit.app.domain.usecase.TodayCard
import com.ckfit.app.domain.week.WeekPlanner
import com.ckfit.app.testing.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Semana flexible con la BD real y lo que muestra la tarjeta "Rutina de hoy". */
@RunWith(AndroidJUnit4::class)
class WeekFlowTest {

    private val monday = LocalDate.of(2026, 9, 21)
    private lateinit var env: TestEnv

    @Before
    fun setUp() = runTest {
        env = TestEnv(monday)
        env.seedLoader.seedIfEmpty()
    }

    @After
    fun tearDown() = env.close()

    private suspend fun openApp(date: LocalDate) {
        env.clock.setDate(date)
        env.weekRepository.ensureCurrentWeek(date)
    }

    private suspend fun todayTitle(): String? =
        (env.observeHome(env.today()).first().today as? TodayCard.Session)?.title

    /** Hasta la Fase 3 no hay sesiones reales: se marca la planificada como hecha. */
    private suspend fun completeSuggested() {
        val week = env.db.plannedSessionDao().getWeek(WeekPlanner.weekStartOf(env.today()))
        val suggested = WeekPlanner.suggest(week.map { it.toItem() }, null, env.settings.chosenPlannedId.first())!!
        env.db.plannedSessionDao().setStatus(listOf(suggested.id), PlannedStatus.DONE)
    }

    @Test
    fun casoDelMiercoles() = runTest {
        openApp(monday)
        assertEquals("Pierna A (cuádriceps) + Pantorrilla + Bíceps", todayTitle())
        completeSuggested()

        openApp(monday.plusDays(1))
        assertEquals("Empuje: Pecho + Hombro + Tríceps", todayTitle())
        completeSuggested()

        // Miércoles: no se entrena.
        openApp(monday.plusDays(2))

        openApp(monday.plusDays(3)) // jueves → Jalón (X)
        assertEquals("Jalón: Espalda + Hombro posterior + Bíceps", todayTitle())
        completeSuggested()

        openApp(monday.plusDays(4)) // viernes → Pierna B (J)
        assertEquals("Pierna B (femoral/glúteo) + Pantorrilla + Tríceps", todayTitle())
        completeSuggested()

        openApp(monday.plusDays(5)) // sábado → opcionales
        val card = env.observeHome(env.today()).first().today as TodayCard.Session
        assertEquals("Opcional: Brazo dedicado", card.title)
        assertTrue(card.isOptional)

        // Lunes siguiente: la semana se cierra sin faltas (las opcionales no cuentan).
        openApp(monday.plusDays(7))
        val previous = env.db.plannedSessionDao().getWeek(monday)
        assertFalse(previous.any { it.planned.status == PlannedStatus.MISSED })
        assertEquals(2, previous.count { it.planned.status == PlannedStatus.PENDING && it.dayOptional == true })
        assertEquals("Pierna A (cuádriceps) + Pantorrilla + Bíceps", todayTitle())
    }

    @Test
    fun semanaIncompletaMarcaMissedAlCerrar() = runTest {
        openApp(monday)
        completeSuggested()
        openApp(monday.plusDays(7))
        val previous = env.db.plannedSessionDao().getWeek(monday)
        assertEquals(3, previous.count { it.planned.status == PlannedStatus.MISSED })
    }

    @Test
    fun elegirSaltarYDeshacer() = runTest {
        openApp(monday)
        val week = env.db.plannedSessionDao().getWeek(monday)
        val thursday = week.first { it.dayOfWeek == 4 }.planned.id
        val tuesday = week.first { it.dayOfWeek == 2 }.planned.id

        env.weekRepository.choose(thursday)
        assertEquals("Pierna B (femoral/glúteo) + Pantorrilla + Tríceps", todayTitle())

        env.weekRepository.skip(thursday, "  sin tiempo ")
        assertEquals("Pierna A (cuádriceps) + Pantorrilla + Bíceps", todayTitle())
        assertEquals("sin tiempo", env.db.plannedSessionDao().get(thursday)!!.skipReason)

        val homeWeek = env.observeHome(env.today()).first().week
        assertTrue(homeWeek.items.first { it.plannedId == thursday }.canUndoSkip)
        assertEquals(1, homeWeek.skipped)

        env.clock.setDate(monday.plusDays(2))
        assertTrue(env.weekRepository.undoSkip(thursday, env.today()))
        assertEquals(PlannedStatus.PENDING, env.db.plannedSessionDao().get(thursday)!!.status)
        assertEquals(null, env.db.plannedSessionDao().get(thursday)!!.skipReason)

        // Un salto de una semana ya cerrada no se puede deshacer.
        env.weekRepository.skip(tuesday, null)
        env.clock.setDate(monday.plusDays(7))
        assertFalse(env.weekRepository.undoSkip(tuesday, env.today()))
    }

    @Test
    fun progresoSemanal() = runTest {
        openApp(monday)
        completeSuggested()
        val week = env.observeHome(env.today()).first().week
        assertEquals(1, week.mandatoryDone)
        assertEquals(4, week.mandatoryTotal)
        assertEquals(3, week.mandatoryPending)
        assertEquals(0 to 2, week.optionalDone to week.optionalTotal)
    }
}
