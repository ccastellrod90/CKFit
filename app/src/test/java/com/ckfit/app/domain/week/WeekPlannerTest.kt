package com.ckfit.app.domain.week

import com.ckfit.app.domain.model.PlannedStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekPlannerTest {

    private val weekStart = LocalDate.of(2026, 9, 21) // lunes

    // Plan L-M-X-J-V(opc)-S(opc), domingo descanso. routineDayId = dayOfWeek.
    private val plan = (1..7).map {
        PlanDay(routineDayId = it.toLong(), dayOfWeek = it, isOptional = it in 5..6, isRestDay = it == 7)
    }

    private fun items(vararg statuses: Pair<Int, PlannedStatus>) = statuses.map { (day, status) ->
        PlannedItem(id = day.toLong() * 10, routineDayId = day.toLong(), dayOfWeek = day, isOptional = day in 5..6, status = status)
    }

    private fun List<PlannedItem>.withStatus(day: Int, status: PlannedStatus) =
        map { if (it.dayOfWeek == day) it.copy(status = status) else it }

    @Test
    fun `weekStart es el lunes`() {
        assertEquals(weekStart, WeekPlanner.weekStartOf(LocalDate.of(2026, 9, 21)))
        assertEquals(weekStart, WeekPlanner.weekStartOf(LocalDate.of(2026, 9, 24)))
        assertEquals(weekStart, WeekPlanner.weekStartOf(LocalDate.of(2026, 9, 27)))
    }

    @Test
    fun `genera una sesion por dia no-descanso con su fecha`() {
        val week = WeekPlanner.buildWeek(weekStart, plan.shuffled())
        assertEquals((1L..6L).toList(), week.map { it.routineDayId })
        assertEquals(LocalDate.of(2026, 9, 21), week.first().plannedDate)
        assertEquals(LocalDate.of(2026, 9, 26), week.last().plannedDate)
        week.forEach { assertEquals(weekStart, it.weekStart) }
    }

    @Test
    fun `sugiere la primera obligatoria pendiente`() {
        val week = items(1 to PlannedStatus.DONE, 2 to PlannedStatus.PENDING, 3 to PlannedStatus.PENDING,
            4 to PlannedStatus.PENDING, 5 to PlannedStatus.PENDING, 6 to PlannedStatus.PENDING)
        assertEquals(2, WeekPlanner.suggest(week, null)?.dayOfWeek)
    }

    @Test
    fun `opcionales solo cuando no quedan obligatorias`() {
        val week = items(1 to PlannedStatus.DONE, 2 to PlannedStatus.DONE, 3 to PlannedStatus.SKIPPED,
            4 to PlannedStatus.DONE, 5 to PlannedStatus.PENDING, 6 to PlannedStatus.PENDING)
        assertEquals(5, WeekPlanner.suggest(week, null)?.dayOfWeek)
    }

    @Test
    fun `la sesion en curso tiene prioridad`() {
        val week = items(1 to PlannedStatus.PENDING, 2 to PlannedStatus.PENDING, 3 to PlannedStatus.PENDING)
        assertEquals(3, WeekPlanner.suggest(week, inProgressPlannedId = 30)?.dayOfWeek)
    }

    @Test
    fun `la elegida manda mientras siga pendiente`() {
        val week = items(1 to PlannedStatus.PENDING, 2 to PlannedStatus.PENDING, 5 to PlannedStatus.PENDING)
        assertEquals(5, WeekPlanner.suggest(week, null, chosenPlannedId = 50)?.dayOfWeek)
        // Si la elegida ya se hizo o se saltó, se vuelve al orden normal.
        assertEquals(1, WeekPlanner.suggest(week.withStatus(5, PlannedStatus.DONE), null, chosenPlannedId = 50)?.dayOfWeek)
        // La sesión en curso gana a la elegida.
        assertEquals(2, WeekPlanner.suggest(week, inProgressPlannedId = 20, chosenPlannedId = 50)?.dayOfWeek)
    }

    @Test
    fun `nada pendiente no sugiere nada`() {
        val week = items(1 to PlannedStatus.DONE, 5 to PlannedStatus.SKIPPED)
        assertNull(WeekPlanner.suggest(week, null))
    }

    @Test
    fun `caso del miercoles`() {
        // No se entrena el miércoles → el jueves se sugiere Jalón (X); el viernes, Pierna B (J);
        // el sábado, las opcionales.
        var week = items(1 to PlannedStatus.PENDING, 2 to PlannedStatus.PENDING, 3 to PlannedStatus.PENDING,
            4 to PlannedStatus.PENDING, 5 to PlannedStatus.PENDING, 6 to PlannedStatus.PENDING)
        assertEquals(1, WeekPlanner.suggest(week, null)?.dayOfWeek) // lunes
        week = week.withStatus(1, PlannedStatus.DONE)
        assertEquals(2, WeekPlanner.suggest(week, null)?.dayOfWeek) // martes
        week = week.withStatus(2, PlannedStatus.DONE)
        // miércoles: no se entrena
        assertEquals(3, WeekPlanner.suggest(week, null)?.dayOfWeek) // jueves → X
        week = week.withStatus(3, PlannedStatus.DONE)
        assertEquals(4, WeekPlanner.suggest(week, null)?.dayOfWeek) // viernes → J
        week = week.withStatus(4, PlannedStatus.DONE)
        assertEquals(5, WeekPlanner.suggest(week, null)?.dayOfWeek) // sábado → opcional V
        assertTrue(WeekPlanner.suggest(week, null)!!.isOptional)
        assertEquals(emptyList<Long>(), WeekPlanner.idsToMarkMissed(week)) // cierre sin faltas
    }

    @Test
    fun `cierre de semana marca MISSED solo obligatorias pendientes`() {
        val week = items(1 to PlannedStatus.DONE, 2 to PlannedStatus.PENDING, 3 to PlannedStatus.SKIPPED,
            4 to PlannedStatus.PENDING, 5 to PlannedStatus.PENDING, 6 to PlannedStatus.PENDING)
        assertEquals(listOf(20L, 40L), WeekPlanner.idsToMarkMissed(week))
    }

    @Test
    fun `sync en semana vacia genera todo`() {
        val sync = WeekPlanner.syncWeek(weekStart, emptyList(), plan)
        assertTrue(sync.deleteIds.isEmpty())
        assertEquals((1L..6L).toList(), sync.add.map { it.routineDayId })
    }

    @Test
    fun `sync sin cambios no toca nada`() {
        val week = items(1 to PlannedStatus.DONE, 2 to PlannedStatus.SKIPPED, 3 to PlannedStatus.PENDING,
            4 to PlannedStatus.PENDING, 5 to PlannedStatus.PENDING, 6 to PlannedStatus.PENDING)
        val sync = WeekPlanner.syncWeek(weekStart, week, plan)
        assertTrue(sync.deleteIds.isEmpty())
        assertTrue(sync.add.isEmpty())
    }

    @Test
    fun `dia que pasa a descanso sale de la cola y domingo que pasa a entreno entra`() {
        val week = items(1 to PlannedStatus.DONE, 2 to PlannedStatus.PENDING, 3 to PlannedStatus.PENDING,
            4 to PlannedStatus.PENDING, 5 to PlannedStatus.PENDING, 6 to PlannedStatus.PENDING)
        val newPlan = plan.map {
            when (it.dayOfWeek) {
                3 -> it.copy(isRestDay = true)
                7 -> it.copy(isRestDay = false)
                else -> it
            }
        }
        val sync = WeekPlanner.syncWeek(weekStart, week, newPlan)
        assertEquals(listOf(30L), sync.deleteIds)
        assertEquals(listOf(7L), sync.add.map { it.routineDayId })
        assertEquals(LocalDate.of(2026, 9, 27), sync.add.single().plannedDate)
    }

    @Test
    fun `lo hecho o saltado nunca se borra aunque el dia pase a descanso`() {
        val week = items(1 to PlannedStatus.DONE, 2 to PlannedStatus.SKIPPED)
        val allRest = plan.map { it.copy(isRestDay = true) }
        assertTrue(WeekPlanner.syncWeek(weekStart, week, allRest).deleteIds.isEmpty())
    }

    @Test
    fun `cambiar de plan a mitad de semana respeta los dias ya resueltos`() {
        // Plan viejo: lunes hecho, martes saltado, resto pendiente. Plan nuevo: días con ids 101..107.
        val old = items(1 to PlannedStatus.DONE, 2 to PlannedStatus.SKIPPED, 3 to PlannedStatus.PENDING,
            4 to PlannedStatus.PENDING, 5 to PlannedStatus.PENDING, 6 to PlannedStatus.PENDING)
        val newPlan = plan.map { it.copy(routineDayId = 100L + it.dayOfWeek) }
        val sync = WeekPlanner.syncWeek(weekStart, old, newPlan)
        assertEquals(listOf(30L, 40L, 50L, 60L), sync.deleteIds)
        assertEquals(listOf(103L, 104L, 105L, 106L), sync.add.map { it.routineDayId })
    }

    @Test
    fun `una pendiente con sesion en curso no se borra`() {
        val week = listOf(
            PlannedItem(10, 1, 1, false, PlannedStatus.PENDING, hasSession = true),
        )
        val allRest = plan.map { it.copy(isRestDay = true) }
        assertTrue(WeekPlanner.syncWeek(weekStart, week, allRest).deleteIds.isEmpty())
    }

    @Test
    fun `deshacer salto solo con la semana abierta`() {
        assertTrue(WeekPlanner.canUndoSkip(PlannedStatus.SKIPPED, weekStart, weekStart.plusDays(6)))
        assertFalse(WeekPlanner.canUndoSkip(PlannedStatus.SKIPPED, weekStart, weekStart.plusDays(7)))
        assertFalse(WeekPlanner.canUndoSkip(PlannedStatus.DONE, weekStart, weekStart))
    }
}
