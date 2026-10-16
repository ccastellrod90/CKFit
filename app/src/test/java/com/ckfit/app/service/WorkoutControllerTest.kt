package com.ckfit.app.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ckfit.app.domain.engine.AudioCue
import com.ckfit.app.domain.engine.EngineEvent
import com.ckfit.app.domain.engine.EngineState
import com.ckfit.app.domain.engine.TimeSource
import com.ckfit.app.domain.model.SessionExerciseStatus
import com.ckfit.app.domain.model.Side
import com.ckfit.app.testing.TestEnv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Controlador real con BD real y reloj virtual. "Matar la app" = descartar el controlador y crear otro:
 * lo único que sobrevive es lo guardado en la BD.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutControllerTest {

    private lateinit var env: TestEnv
    private var now = 0L
    private val scopes = mutableListOf<CoroutineScope>()

    @Before
    fun setUp() = runBlocking {
        env = TestEnv(LocalDate.of(2026, 9, 21))
        env.seedLoader.seedIfEmpty()
        env.weekRepository.ensureCurrentWeek(env.today())
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        env.close()
    }

    private fun newController(): WorkoutController {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }
        return WorkoutController(
            context = env.context,
            sessions = env.sessionRepository,
            settings = env.settings,
            store = env.activeStore,
            timeSource = TimeSource { now },
            clock = env.clock,
            cueSink = object : CueSink {
                override fun play(cue: AudioCue) = Unit
            },
            scope = scope,
        )
    }

    private suspend fun waitUntil(condition: suspend () -> Boolean) = withTimeout(5_000) {
        while (!condition()) delay(20)
    }

    @Test
    fun seriesSeGuardanAlCerrarseYSeReanudaTrasMatarLaApp() = runBlocking {
        val monday = env.db.plannedSessionDao().getWeek(env.today()).first { it.dayOfWeek == 1 }
        val sessionId = env.sessionRepository.start(monday.planned.routineDayId!!, monday.planned.id)
        val zancada = env.sessionRepository.getExercises(sessionId)[2] // 4 series por lado, descanso 45 s

        val controller = newController()
        controller.open(zancada.item.id)
        assertEquals(EngineState.Waiting(1, 4), controller.state.value!!.engine)
        controller.setWeight(12.0)

        controller.send(EngineEvent.Start)
        waitUntil { controller.state.value!!.engine is EngineState.WorkingReps }
        controller.send(EngineEvent.Done)             // lado izquierdo
        waitUntil { controller.state.value!!.engine is EngineState.SwitchSide }
        now += 5_000                                  // cambio de lado (5 s)
        waitUntil { (controller.state.value!!.engine as? EngineState.WorkingReps)?.side == Side.RIGHT }
        controller.send(EngineEvent.Done)             // lado derecho → serie 1 cerrada
        waitUntil { controller.state.value!!.engine is EngineState.Resting }

        val logs = env.db.setLogDao().getForSessionExercise(zancada.item.id)
        assertEquals(listOf(Side.LEFT, Side.RIGHT), logs.map { it.side })
        assertEquals(listOf(12.0, 12.0), logs.map { it.weightKg })
        assertEquals(listOf(12, 12), logs.map { it.reps }) // reps objetivo (repsMax) en el MVP
        assertEquals(SessionExerciseStatus.IN_PROGRESS, env.sessionRepository.getExercise(zancada.item.id)!!.item.status)
        // El peso cambiado queda en el plan para las próximas sesiones.
        assertEquals(12.0, env.db.dayExerciseDao().get(zancada.dayExercise!!.id)!!.currentWeightKg!!, 0.0)

        // El ejercicio empezado sube arriba de la lista (antes de lo pendiente).
        assertEquals(zancada.item.id, env.sessionRepository.getExercises(sessionId).first().item.id)

        // Se mata la app en pleno descanso y pasan 20 s: al volver, el descanso sigue donde iba.
        waitUntil { env.activeStore.load()?.snapshot?.phase == com.ckfit.app.domain.engine.EngineSnapshot.Phase.REST }
        scopes.first().cancel()
        now += 20_000
        val revived = newController()
        assertEquals(true, revived.restoreIfNeeded())
        waitUntil { revived.state.value?.engine is EngineState.Resting }
        assertEquals(EngineState.Resting(2, 25_000, 45_000), revived.state.value!!.engine)
        assertEquals(12.0, revived.state.value!!.exercise.weightKg!!, 0.0)
        // Y termina solo: en automático arranca la serie 2.
        now += 25_000
        waitUntil { (revived.state.value!!.engine as? EngineState.WorkingReps)?.set == 2 }
    }

    @Test
    fun terminarEjercicioAntesYSiguientePorVoz() = runBlocking {
        val monday = env.db.plannedSessionDao().getWeek(env.today()).first { it.dayOfWeek == 1 }
        val sessionId = env.sessionRepository.start(monday.planned.routineDayId!!, monday.planned.id)
        val items = env.sessionRepository.getExercises(sessionId)
        val controller = newController()
        controller.open(items.first().item.id)
        controller.send(EngineEvent.Start)
        waitUntil { controller.state.value!!.engine is EngineState.WorkingReps }
        controller.send(EngineEvent.Done)
        waitUntil { controller.state.value!!.engine is EngineState.Resting }

        controller.send(EngineEvent.EndExercise) // 1 de 4 series y basta
        waitUntil { controller.state.value!!.engine == EngineState.ExerciseDone }
        waitUntil { env.sessionRepository.getExercise(items.first().item.id)!!.item.status == SessionExerciseStatus.DONE }
        assertEquals(1, env.db.setLogDao().lastSetNumber(items.first().item.id))
        // Terminado, el estado guardado ya no lo retoma.
        waitUntil { env.activeStore.load()?.snapshot?.phase == com.ckfit.app.domain.engine.EngineSnapshot.Phase.DONE }

        controller.send(EngineEvent.Next) // "siguiente" por voz
        waitUntil { controller.state.value!!.exercise.sessionExerciseId == items[1].item.id }
        assertEquals(EngineState.Waiting(1, 4), controller.state.value!!.engine)
    }

    @Test
    fun elPesoEsDelEjercicioYSeCompartePorTodosLosDias() = runBlocking {
        val week = env.db.plannedSessionDao().getWeek(env.today())
        val monday = week.first { it.dayOfWeek == 1 }
        val thursday = week.first { it.dayOfWeek == 4 }
        val sessionId = env.sessionRepository.start(monday.planned.routineDayId!!, monday.planned.id)
        val pantorrilla = env.sessionRepository.getExercises(sessionId).first { it.exercise!!.name.startsWith("Pantorrilla") }
        val controller = newController()
        controller.open(pantorrilla.item.id)
        controller.setWeight(40.0)
        waitUntil { env.db.dayExerciseDao().get(pantorrilla.dayExercise!!.id)!!.currentWeightKg == 40.0 }
        // La pantorrilla del jueves tiene el mismo peso.
        val thursdayRow = env.db.dayExerciseDao().getForDay(thursday.planned.routineDayId!!)
            .first { it.exercise.id == pantorrilla.exercise!!.id }
        assertEquals(40.0, thursdayRow.dayExercise.currentWeightKg!!, 0.0)
    }

    @Test
    fun ejercicioSinPesoNoRegistraPeso() = runBlocking {
        val monday = env.db.plannedSessionDao().getWeek(env.today()).first { it.dayOfWeek == 1 }
        val sessionId = env.sessionRepository.start(monday.planned.routineDayId!!, monday.planned.id)
        val plancha = env.sessionRepository.getExercises(sessionId).first { it.exercise!!.name == "Plancha isométrica" }
        val controller = newController()
        controller.open(plancha.item.id)
        assertEquals(false, controller.state.value!!.exercise.usesWeight)
        controller.send(EngineEvent.Start)
        waitUntil { controller.state.value!!.engine is EngineState.PreCountdown }
        now += 3_000
        waitUntil { controller.state.value!!.engine is EngineState.WorkingTimed }
        controller.send(EngineEvent.Done)
        waitUntil { env.db.setLogDao().lastSetNumber(plancha.item.id) == 1 }
        assertEquals(null, env.db.setLogDao().getForSessionExercise(plancha.item.id).single().weightKg)
    }

    @Test
    fun pesoDeReferenciaEsElDeLaVezAnterior() = runBlocking {
        val week = env.db.plannedSessionDao().getWeek(env.today())
        val monday = week.first { it.dayOfWeek == 1 }
        // Sesión anterior: prensa con 120 kg.
        val first = env.sessionRepository.start(monday.planned.routineDayId!!, null)
        val prensa = env.sessionRepository.getExercises(first).first()
        env.sessionRepository.logSet(prensa.item.id, listOf(com.ckfit.app.data.repo.SetToLog(1, null, 120.0, 8, null)))
        env.sessionRepository.finish(first)
        env.sessionRepository.setCurrentWeight(prensa.exercise!!.id, 100.0)

        val second = env.sessionRepository.start(monday.planned.routineDayId, monday.planned.id)
        val controller = newController()
        controller.open(env.sessionRepository.getExercises(second).first().item.id)
        val ex = controller.state.value!!.exercise
        assertEquals(100.0, ex.weightKg!!, 0.0)          // empieza con el peso guardado en el plan
        assertEquals(120.0, ex.referenceWeightKg!!, 0.0) // pero la referencia es lo que hizo la vez anterior
    }

    @Test
    fun ejercicioTerminadoQuedaCumplidoYApuntaAlSiguiente() = runBlocking {
        val monday = env.db.plannedSessionDao().getWeek(env.today()).first { it.dayOfWeek == 1 }
        val sessionId = env.sessionRepository.start(monday.planned.routineDayId!!, monday.planned.id)
        val items = env.sessionRepository.getExercises(sessionId)
        val prensa = items.first()

        val controller = newController()
        controller.open(prensa.item.id)
        assertEquals("Extensión de rodilla máquina", controller.state.value!!.exercise.nextName)
        repeat(4) { set ->
            if (set == 0) controller.send(EngineEvent.Start)
            waitUntil { controller.state.value!!.engine is EngineState.WorkingReps }
            controller.send(EngineEvent.Done)
            if (set < 3) {
                waitUntil { controller.state.value!!.engine is EngineState.Resting }
                controller.send(EngineEvent.Skip) // automático: la siguiente serie arranca sola
            }
        }
        waitUntil { controller.state.value!!.engine == EngineState.ExerciseDone }
        waitUntil { env.sessionRepository.getExercise(prensa.item.id)!!.item.status == SessionExerciseStatus.DONE }
        assertEquals(4, env.db.setLogDao().lastSetNumber(prensa.item.id))
        assertEquals(items[1].item.id, controller.state.value!!.exercise.nextSessionExerciseId)
    }
}
