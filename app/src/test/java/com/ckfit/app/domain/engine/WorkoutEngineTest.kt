package com.ckfit.app.domain.engine

import com.ckfit.app.domain.engine.AudioCue.Beep
import com.ckfit.app.domain.engine.AudioCue.Count
import com.ckfit.app.domain.engine.AudioCue.ExerciseFinished
import com.ckfit.app.domain.engine.AudioCue.Go
import com.ckfit.app.domain.engine.AudioCue.SetStart
import com.ckfit.app.domain.engine.EngineEvent.AddRest
import com.ckfit.app.domain.engine.EngineEvent.Done
import com.ckfit.app.domain.engine.EngineEvent.Pause
import com.ckfit.app.domain.engine.EngineEvent.Resume
import com.ckfit.app.domain.engine.EngineEvent.Skip
import com.ckfit.app.domain.engine.EngineEvent.Start
import com.ckfit.app.domain.model.MeasureType
import com.ckfit.app.domain.model.Side
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutEngineTest {

    /** Reloj virtual: el test lo avanza a mano; el motor nunca suma ticks. */
    private var now = 0L
    private val clock = TimeSource { now }

    private val prensa = ExerciseSpec(
        name = "Prensa",
        measureType = MeasureType.REPS,
        perSide = false,
        sets = 4,
        repsMin = 6,
        repsMax = 8,
        durationSec = null,
        restSec = 75,
        nextExerciseName = "Extensión",
    )
    private val zancada = prensa.copy(name = "Zancada", perSide = true, sets = 2, restSec = 45)
    private val plancha = prensa.copy(
        name = "Plancha", measureType = MeasureType.TIME, repsMin = null, repsMax = null,
        durationSec = 20, sets = 2, restSec = 60,
    )
    private val planchaLateral = plancha.copy(name = "Plancha lateral", perSide = true, sets = 3)

    private fun engine(
        spec: ExerciseSpec,
        config: EngineConfig = EngineConfig(),
        startSet: Int = 1,
        restoreFrom: EngineSnapshot? = null,
    ) = WorkoutEngine(spec, config, clock, startSet, restoreFrom)

    /** Avanza en pasos de 100 ms, como el bucle real. */
    private fun WorkoutEngine.advance(ms: Long) {
        val end = now + ms
        while (now < end) {
            now = minOf(now + 100, end)
            tick()
        }
    }

    private suspend fun WorkoutEngine.drainCues(): List<AudioCue> = buildList {
        while (true) add(withTimeoutOrNull(1) { cues.first() } ?: break)
    }

    private suspend fun WorkoutEngine.drainSets(): List<CompletedSet> = buildList {
        while (true) add(withTimeoutOrNull(1) { completedSets.first() } ?: break)
    }

    @Test
    fun `REPS normal - esperar, series, descansos y fin`() = runTest {
        val e = engine(prensa)
        assertEquals(EngineState.Waiting(1, 4), e.state.value)
        assertEquals(AudioCue.ExerciseIntro("Prensa", 4, MeasureType.REPS, 6, 8, null, false), e.drainCues().single())

        e.handle(Start)
        e.advance(30_000)
        assertEquals(EngineState.WorkingReps(1, null, 30_000), e.state.value)
        e.handle(Done)
        assertEquals(EngineState.Resting(2, 75_000, 75_000), e.state.value)
        assertEquals(listOf(CompletedSet(1, null, null)), e.drainSets())

        e.advance(75_000) // automático: arranca la serie 2 sola
        assertEquals(EngineState.WorkingReps(2, null, 0), e.state.value)
        assertEquals(listOf(Beep(BeepType.SHORT), Count(5), Count(4), Count(3), Count(2), Count(1), SetStart(2)), e.drainCues())

        repeat(2) {
            e.handle(Done)
            e.advance(75_000)
        }
        e.handle(Done) // serie 4: no hay descanso tras la última
        assertEquals(EngineState.ExerciseDone, e.state.value)
        assertEquals(4, e.drainSets().size + 1) // la 1 ya se drenó
        assertEquals(ExerciseFinished("Extensión"), e.drainCues().last())
    }

    @Test
    fun `REPS por lado - cambio de lado y una fila por lado`() = runTest {
        val e = engine(zancada)
        e.drainCues()
        e.handle(Start)
        assertEquals(EngineState.WorkingReps(1, Side.LEFT, 0), e.state.value)
        e.handle(Done)
        assertEquals(EngineState.SwitchSide(1, 5_000), e.state.value)
        e.advance(5_000)
        assertEquals(EngineState.WorkingReps(1, Side.RIGHT, 0), e.state.value)
        assertEquals(listOf(AudioCue.SwitchSide, Count(3), Count(2), Count(1), Go), e.drainCues())
        assertTrue(e.drainSets().isEmpty()) // la serie aún no se ha cerrado
        e.handle(Done)
        assertEquals(listOf(CompletedSet(1, Side.LEFT, null), CompletedSet(1, Side.RIGHT, null)), e.drainSets())
        assertTrue(e.state.value is EngineState.Resting)
    }

    @Test
    fun `TIME - cuenta previa, cuenta atras y fin por tiempo`() = runTest {
        val e = engine(plancha)
        e.drainCues()
        e.handle(Start)
        assertEquals(EngineState.PreCountdown(3), e.state.value)
        e.advance(3_000)
        assertEquals(EngineState.WorkingTimed(1, null, 20_000), e.state.value)
        assertEquals(listOf(Count(3), Count(2), Count(1), Go), e.drainCues())

        e.advance(20_000)
        assertEquals(EngineState.Resting(2, 60_000, 60_000), e.state.value)
        assertEquals(listOf(Count(3), Count(2), Count(1), Beep(BeepType.SHORT)), e.drainCues())
        assertEquals(listOf(CompletedSet(1, null, 20)), e.drainSets())

        e.advance(60_000) // automático: la serie 2 de TIME pasa por la cuenta previa
        assertEquals(EngineState.PreCountdown(3), e.state.value)
    }

    @Test
    fun `TIME por lado - plancha lateral 3x20 s por lado sin tocar nada`() = runTest {
        val e = engine(planchaLateral)
        e.drainCues()
        e.handle(Start)
        e.advance(3_000 + 20_000)
        assertEquals(EngineState.SwitchSide(1, 5_000), e.state.value)
        e.advance(5_000)
        assertEquals(EngineState.WorkingTimed(1, Side.RIGHT, 20_000), e.state.value)
        e.advance(20_000)
        assertTrue(e.state.value is EngineState.Resting)
        assertEquals(listOf(CompletedSet(1, Side.LEFT, 20), CompletedSet(1, Side.RIGHT, 20)), e.drainSets())

        // Serie 1 ya hecha; las otras dos se completan solas en automático.
        e.advance(60_000 + (3_000 + 20_000 + 5_000 + 20_000) * 2 + 60_000)
        assertEquals(EngineState.ExerciseDone, e.state.value)
        assertEquals(4, e.drainSets().size)
    }

    @Test
    fun `descanso 0 pasa directo a la siguiente serie`() = runTest {
        val e = engine(prensa.copy(restSec = 0))
        e.drainCues()
        e.handle(Start)
        e.handle(Done)
        assertEquals(EngineState.WorkingReps(2, null, 0), e.state.value)
        assertEquals(listOf(SetStart(2)), e.drainCues())
    }

    @Test
    fun `manual espera Empezar sin limite tras el descanso`() = runTest {
        val e = engine(prensa, EngineConfig(autoAdvance = false))
        e.drainCues()
        e.handle(Start)
        e.handle(Done)
        e.advance(75_000)
        assertEquals(EngineState.Waiting(2, 4), e.state.value)
        assertEquals(Beep(BeepType.DOUBLE), e.drainCues().last())
        e.advance(600_000)
        assertEquals(EngineState.Waiting(2, 4), e.state.value)
        e.handle(Start)
        assertEquals(EngineState.WorkingReps(2, null, 0), e.state.value)
        assertEquals(listOf(SetStart(2)), e.drainCues())
    }

    @Test
    fun `cambiar a manual durante el descanso aplica al terminarlo`() = runTest {
        val e = engine(prensa)
        e.handle(Start)
        e.handle(Done)
        e.setAutoAdvance(false)
        e.advance(75_000)
        assertEquals(EngineState.Waiting(2, 4), e.state.value)
    }

    @Test
    fun `saltar descanso y saltar serie`() = runTest {
        val e = engine(prensa)
        e.handle(Start)
        e.handle(Done)
        e.drainSets()
        e.handle(Skip) // descanso
        assertEquals(EngineState.WorkingReps(2, null, 0), e.state.value)
        e.handle(Skip) // serie 2 sin registrar → descanso
        assertEquals(EngineState.Resting(3, 75_000, 75_000), e.state.value)
        assertTrue(e.drainSets().isEmpty())
    }

    @Test
    fun `saltar desde esperando pasa a esperar la siguiente`() = runTest {
        val e = engine(prensa, startSet = 4)
        assertEquals(EngineState.Waiting(4, 4), e.state.value)
        e.handle(Skip)
        assertEquals(EngineState.ExerciseDone, e.state.value)
    }

    @Test
    fun `mas descanso suma 15 s y rehace la cuenta final`() = runTest {
        val e = engine(prensa)
        e.handle(Start)
        e.handle(Done)
        e.advance(72_000)
        e.drainCues()
        e.handle(AddRest())
        assertEquals(EngineState.Resting(2, 18_000, 90_000), e.state.value)
        e.advance(18_000)
        assertEquals(listOf(Count(5), Count(4), Count(3), Count(2), Count(1), SetStart(2)), e.drainCues())
    }

    @Test
    fun `pausa congela y seguir retoma el tiempo restante`() = runTest {
        val e = engine(plancha)
        e.handle(Start)
        e.advance(3_000 + 5_000)
        e.handle(Pause)
        assertEquals(EngineState.Paused(EngineState.WorkingTimed(1, null, 15_000)), e.state.value)
        e.advance(120_000) // en pausa no pasa el tiempo del ejercicio
        assertEquals(EngineState.Paused(EngineState.WorkingTimed(1, null, 15_000)), e.state.value)
        e.handle(Resume)
        assertEquals(EngineState.WorkingTimed(1, null, 15_000), e.state.value)
        e.advance(15_000)
        assertTrue(e.state.value is EngineState.Resting)
    }

    @Test
    fun `listo antes de tiempo en TIME registra lo trabajado`() = runTest {
        val e = engine(plancha)
        e.handle(Start)
        e.advance(3_000 + 12_400)
        e.handle(Done)
        assertEquals(listOf(CompletedSet(1, null, 12)), e.drainSets())
    }

    @Test
    fun `aviso a mitad de serie si esta activo`() = runTest {
        val e = engine(plancha, EngineConfig(halfwayCue = true))
        e.handle(Start)
        e.advance(3_000)
        e.drainCues()
        e.advance(10_000)
        assertEquals(listOf(AudioCue.Halfway(10)), e.drainCues())
    }

    @Test
    fun `descanso mas corto que la cuenta final cuenta desde su duracion`() = runTest {
        val e = engine(prensa.copy(restSec = 3))
        e.handle(Start)
        e.drainCues()
        e.handle(Done)
        e.advance(3_000)
        assertEquals(listOf(Beep(BeepType.SHORT), Count(3), Count(2), Count(1), SetStart(2)), e.drainCues())
    }

    @Test
    fun `reanudar desde la ultima serie guardada`() = runTest {
        assertEquals(EngineState.Waiting(3, 4), engine(prensa, startSet = 3).state.value)
        assertEquals(EngineState.ExerciseDone, engine(prensa, startSet = 5).state.value)
    }

    @Test
    fun `terminar ejercicio antes de tiempo - 3 de 4 series`() = runTest {
        val e = engine(prensa)
        e.handle(Start)
        repeat(3) {
            e.handle(Done)
            if (it < 2) e.handle(Skip)
        }
        assertTrue(e.state.value is EngineState.Resting)
        e.drainCues()
        e.handle(EngineEvent.EndExercise)
        assertEquals(EngineState.ExerciseDone, e.state.value)
        assertEquals(3, e.drainSets().size)
        assertEquals(listOf(ExerciseFinished("Extensión")), e.drainCues())
    }

    @Test
    fun `terminar ejercicio a mitad de serie no registra esa serie`() = runTest {
        val e = engine(plancha)
        e.handle(Start)
        e.advance(3_000 + 8_000)
        e.handle(EngineEvent.EndExercise)
        assertEquals(EngineState.ExerciseDone, e.state.value)
        assertTrue(e.drainSets().isEmpty())
    }

    @Test
    fun `restaurar en pleno descanso sigue el tiempo donde iba`() = runTest {
        val e = engine(prensa)
        e.handle(Start)
        e.handle(Done)
        e.advance(30_000)
        val snap = e.snapshot()
        // El proceso muere 20 s; al volver, el descanso sigue contando desde donde iba.
        now += 20_000
        val revived = engine(prensa, restoreFrom = snap)
        revived.tick()
        assertEquals(EngineState.Resting(2, 25_000, 75_000), revived.state.value)
        assertTrue(revived.drainCues().none { it is AudioCue.ExerciseIntro })
        revived.advance(25_000)
        assertEquals(EngineState.WorkingReps(2, null, 0), revived.state.value)
    }

    @Test
    fun `restaurar una serie por tiempo que ya habria terminado la cierra`() = runTest {
        val e = engine(planchaLateral)
        e.handle(Start)
        e.advance(3_000 + 20_000 + 5_000 + 4_000) // lado derecho, 4 s trabajados
        val snap = e.snapshot()
        assertEquals(EngineSnapshot.Phase.TIMED, snap.phase)
        assertEquals(20, snap.workedLeft)
        now += 60_000
        val revived = engine(planchaLateral, restoreFrom = snap)
        revived.tick()
        assertEquals(listOf(CompletedSet(1, Side.LEFT, 20), CompletedSet(1, Side.RIGHT, 20)), revived.drainSets())
    }

    @Test
    fun `restaurar en pausa sigue en pausa con el mismo tiempo`() = runTest {
        val e = engine(prensa, EngineConfig(autoAdvance = false))
        e.handle(Start)
        e.handle(Done)
        e.advance(10_000)
        e.handle(Pause)
        val snap = e.snapshot()
        now += 300_000
        val revived = engine(prensa, restoreFrom = snap)
        revived.tick()
        assertEquals(EngineState.Paused(EngineState.Resting(2, 65_000, 75_000)), revived.state.value)
        assertEquals(false, revived.config.autoAdvance)
        revived.handle(Resume)
        assertEquals(EngineState.Resting(2, 65_000, 75_000), revived.state.value)
    }

    @Test
    fun `el snapshot trasladado tras reiniciar el telefono conserva lo restante`() = runTest {
        val e = engine(prensa)
        e.handle(Start)
        e.handle(Done)
        e.advance(15_000)
        val snap = e.snapshot()
        // Reinicio: el reloj monotónico vuelve casi a 0; se traslada por la diferencia de reloj de pared.
        val before = now
        now = 1_000
        val revived = engine(prensa, restoreFrom = snap.shiftedBy(now - before))
        revived.tick()
        assertEquals(EngineState.Resting(2, 60_000, 75_000), revived.state.value)
    }
}
