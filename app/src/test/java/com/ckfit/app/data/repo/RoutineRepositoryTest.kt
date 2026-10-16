package com.ckfit.app.data.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ckfit.app.domain.model.LoadType
import com.ckfit.app.domain.model.MeasureType
import com.ckfit.app.domain.model.PlannedStatus
import com.ckfit.app.domain.prescription.LoadDefaults
import com.ckfit.app.testing.TestEnv
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RoutineRepositoryTest {

    private val thursday = LocalDate.of(2026, 9, 24)
    private lateinit var env: TestEnv
    private val repo get() = env.routineRepository
    private val dao get() = env.db.routineDao()
    private var nivel2 = 0L

    @Before
    fun setUp() = runTest {
        env = TestEnv(thursday)
        env.seedLoader.seedIfEmpty()
        env.weekRepository.ensureCurrentWeek(thursday)
        nivel2 = dao.getActive()!!.id
    }

    @After
    fun tearDown() = env.close()

    /** Criterio de la Fase 2: crear "Nivel 3" duplicando y editando, sin tocar código. */
    @Test
    fun duplicarCreaCopiaProfundaInactivaEIndependiente() = runTest {
        val nivel3 = repo.duplicateRoutine(nivel2, "Nivel 3")

        assertEquals("Nivel 3", dao.getRoutine(nivel3)!!.name)
        assertFalse(dao.getRoutine(nivel3)!!.isActive)
        val srcDays = dao.getDays(nivel2)
        val newDays = dao.getDays(nivel3)
        assertEquals(srcDays.map { Triple(it.dayOfWeek, it.title, it.isRestDay) }, newDays.map { Triple(it.dayOfWeek, it.title, it.isRestDay) })
        assertEquals(70, env.db.dayExerciseDao().countActive())

        // Editar la copia no toca el original.
        val newMonday = newDays.first()
        val prensaCopy = env.db.dayExerciseDao().getForDay(newMonday.id).first()
        repo.updateDayExercise(
            prensaCopy.dayExercise.id,
            LoadDefaults.apply(LoadType.STRENGTH, LoadDefaults.newDraft(MeasureType.REPS, false, "Cuádriceps"))
                .copy(sets = 5, weightKg = 140.0),
        )
        val original = env.db.dayExerciseDao().getForDay(srcDays.first().id).first().dayExercise
        assertEquals(4, original.sets)
        assertEquals(5, env.db.dayExerciseDao().get(prensaCopy.dayExercise.id)!!.sets)
        assertEquals(prensaCopy.exercise.id, env.db.dayExerciseDao().get(prensaCopy.dayExercise.id)!!.exerciseId)
    }

    @Test
    fun activarDejaUnSoloPlanActivoYAjustaLaSemana() = runTest {
        // Jueves: lunes y martes hechos con Nivel 2.
        val week = env.db.plannedSessionDao().getWeek(LocalDate.of(2026, 9, 21))
        env.db.plannedSessionDao().setStatus(week.filter { it.dayOfWeek in 1..2 }.map { it.planned.id }, PlannedStatus.DONE)

        val nivel3 = repo.duplicateRoutine(nivel2, "Nivel 3")
        repo.activateRoutine(nivel3)

        assertEquals(nivel3, dao.getActive()!!.id)
        assertFalse(dao.getRoutine(nivel2)!!.isActive)
        val after = env.db.plannedSessionDao().getWeek(LocalDate.of(2026, 9, 21))
        val nivel3Days = dao.getDays(nivel3).map { it.id }.toSet()
        // Lo hecho se queda (Nivel 2); lo pendiente pasa a ser de Nivel 3; no se duplican lunes ni martes.
        assertEquals(2, after.count { it.planned.status == PlannedStatus.DONE && it.planned.routineDayId !in nivel3Days })
        assertEquals(listOf(3, 4, 5, 6), after.filter { it.planned.routineDayId in nivel3Days }.map { it.dayOfWeek })
        assertEquals(6, after.size)
    }

    @Test
    fun diaQuePasaADescansoSaleDeLaSemana() = runTest {
        val wednesday = dao.getDays(nivel2).first { it.dayOfWeek == 3 }
        repo.updateDay(wednesday.copy(isRestDay = true))
        val week = env.db.plannedSessionDao().getWeek(LocalDate.of(2026, 9, 21))
        assertFalse(week.any { it.dayOfWeek == 3 })

        repo.updateDay(wednesday.copy(isRestDay = false, title = "  Jalón nuevo  "))
        val again = env.db.plannedSessionDao().getWeek(LocalDate.of(2026, 9, 21))
        assertEquals("Jalón nuevo", again.first { it.dayOfWeek == 3 }.dayTitle)
    }

    @Test
    fun crearPlanNuevoTieneSieteDiasDeDescanso() = runTest {
        val id = repo.createRoutine("Mi plan", listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"), "#8A9099")
        val days = dao.getDays(id)
        assertEquals(7, days.size)
        assertTrue(days.all { it.isRestDay })
        assertEquals("Lunes", days.first().title)
        assertEquals("Domingo", days.last().title)
        assertEquals(nivel2, dao.getActive()!!.id)
    }

    @Test
    fun agregarReordenarArchivarYRestaurar() = runTest {
        val sunday = dao.getDays(nivel2).first { it.dayOfWeek == 7 }
        val cuerda = env.db.exerciseDao().getByName("Saltar la cuerda")!!
        val custom = repo.createExercise(" Burpees ", "Full body", MeasureType.REPS, false)

        val a = repo.addDayExercise(sunday.id, cuerda.id, LoadDefaults.newDraft(cuerda.measureType, cuerda.perSide, cuerda.muscleGroup))
        val b = repo.addDayExercise(sunday.id, custom, LoadDefaults.newDraft(MeasureType.REPS, false, "Full body"))
        assertEquals(listOf(a, b), env.db.dayExerciseDao().getForDay(sunday.id).map { it.dayExercise.id })
        assertEquals(LoadType.CARDIO, env.db.dayExerciseDao().get(a)!!.loadType)
        assertTrue(env.db.exerciseDao().get(custom)!!.isCustom)
        assertEquals("Burpees", env.db.exerciseDao().get(custom)!!.name)

        repo.reorder(listOf(b, a))
        assertEquals(listOf(b, a), env.db.dayExerciseDao().getForDay(sunday.id).map { it.dayExercise.id })

        repo.archiveDayExercise(b)
        assertEquals(listOf(a), env.db.dayExerciseDao().getForDay(sunday.id).map { it.dayExercise.id })
        repo.restoreDayExercise(b)
        assertEquals(listOf(a, b), env.db.dayExerciseDao().getForDay(sunday.id).map { it.dayExercise.id })
    }

    @Test
    fun cambiarTipoEnElEditorCambiaElEjercicioDelCatalogo() = runTest {
        val monday = dao.getDays(nivel2).first()
        val prensa = env.db.dayExerciseDao().getForDay(monday.id).first()
        val draft = LoadDefaults.newDraft(MeasureType.TIME, true, "Cuádriceps").copy(durationSec = 40)
        repo.updateDayExercise(prensa.dayExercise.id, draft)

        val exercise = env.db.exerciseDao().get(prensa.exercise.id)!!
        assertEquals(MeasureType.TIME, exercise.measureType)
        assertTrue(exercise.perSide)
        val updated = env.db.dayExerciseDao().get(prensa.dayExercise.id)!!
        assertEquals(40, updated.durationSec)
        assertEquals(null, updated.repsMax) // lo que no aplica al tipo se limpia
    }
}
