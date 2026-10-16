package com.ckfit.app.domain.voice

import com.ckfit.app.domain.engine.AudioCue
import com.ckfit.app.domain.engine.BeepType
import com.ckfit.app.domain.engine.EngineEvent
import com.ckfit.app.domain.model.MeasureType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoiceTest {

    @Test
    fun `normaliza tildes, mayusculas y espacios`() {
        assertEquals("mas tiempo", VoiceCommands.normalize("  Más   TIEMPO "))
        assertEquals("proximo", VoiceCommands.normalize("próximo"))
        assertEquals("continua", VoiceCommands.normalize("Continúa"))
    }

    @Test
    fun `cada comando y sus sinonimos`() {
        listOf("empezar", "vamos", "iniciar", "dale", "comienza", "arranca", "venga").forEach {
            assertEquals(EngineEvent.Start, VoiceCommands.parse(it))
        }
        listOf("listo", "ya", "terminé", "fin", "ya está", "hecho", "acabé").forEach {
            assertEquals(EngineEvent.Done, VoiceCommands.parse(it))
        }
        listOf("siguiente", "próximo", "otro ejercicio").forEach { assertEquals(EngineEvent.Next, VoiceCommands.parse(it)) }
        listOf("saltar", "salta", "omitir", "pasa", "paso").forEach { assertEquals(EngineEvent.Skip, VoiceCommands.parse(it)) }
        listOf("más tiempo", "más descanso", "un poco más", "otro poco").forEach {
            assertEquals(EngineEvent.AddRest(15), VoiceCommands.parse(it))
        }
        listOf("pausa", "para", "espera", "alto", "un momento", "stop").forEach {
            assertEquals(EngineEvent.Pause, VoiceCommands.parse(it))
        }
        listOf("seguir", "continúa", "reanudar", "sigue", "vuelve").forEach {
            assertEquals(EngineEvent.Resume, VoiceCommands.parse(it))
        }
    }

    /**
     * Vosk descarta en silencio las palabras de la gramática que no están en el vocabulario del modelo.
     * Se lee la tabla de símbolos de Gr.fst (formato OpenFst). Si el modelo no está descargado, se omite.
     */
    @Test
    fun `todas las palabras de la gramatica existen en el modelo`() {
        val fst = File("../models/src/main/assets/model-es/graph/Gr.fst")
        assumeTrue("Modelo de voz no descargado (ver README)", fst.exists())
        val vocabulary = readVocabulary(fst.readBytes())
        assertTrue(vocabulary.size > 50_000)
        val missing = VoiceCommands.phrases.flatMap { it.split(" ") }.filter { it !in vocabulary }
        assertEquals(emptyList<String>(), missing)
    }

    private fun readVocabulary(bytes: ByteArray): Set<String> {
        val name = "words.txt".toByteArray()
        // La tabla de palabras es la última cuyo nombre acaba en words.txt.
        var end = -1
        for (i in 0..bytes.size - name.size) {
            if ((name.indices).all { bytes[i + it] == name[it] }) end = i + name.size
        }
        check(end > 0) { "No se encontró la tabla de símbolos" }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(end)
        buf.long // available_key
        val size = buf.long.toInt()
        return buildSet {
            repeat(size) {
                val len = buf.int
                val word = ByteArray(len).also { buf.get(it) }
                buf.long // clave
                add(String(word, Charsets.UTF_8))
            }
        }
    }

    @Test
    fun `lo desconocido no dispara nada`() {
        assertNull(VoiceCommands.parse("[unk]"))
        assertNull(VoiceCommands.parse(""))
        assertNull(VoiceCommands.parse("listo ya"))
    }

    @Test
    fun `la gramatica es JSON valido con todas las frases y unk`() {
        val items = Json.parseToJsonElement(VoiceCommands.grammarJson).jsonArray.map { it.jsonPrimitive.content }
        assertEquals(VoiceCommands.phrases + VoiceCommands.UNKNOWN, items.toSet())
    }

    @Test
    fun `textos hablados`() {
        assertEquals(
            "Prensa, cuatro series de seis a ocho",
            SpokenText.of(AudioCue.ExerciseIntro("Prensa", 4, MeasureType.REPS, 6, 8, null, false)),
        )
        assertEquals(
            "Plancha lateral, tres series de veinte segundos por lado",
            SpokenText.of(AudioCue.ExerciseIntro("Plancha lateral", 3, MeasureType.TIME, null, null, 20, true)),
        )
        assertEquals(
            "Cardio, una serie de 45 minutos",
            SpokenText.of(AudioCue.ExerciseIntro("Cardio", 1, MeasureType.TIME, null, null, 2700, false)),
        )
        assertEquals("Serie 2, a tope", SpokenText.of(AudioCue.SetStart(2)))
        assertEquals("tres", SpokenText.of(AudioCue.Count(3)))
        assertEquals("Ejercicio terminado. Ahora toca Extensión", SpokenText.of(AudioCue.ExerciseFinished("Extensión")))
        assertNull(SpokenText.of(AudioCue.Beep(BeepType.SHORT)))
    }

    @Test
    fun `ningun aviso hablado contiene una palabra de comando`() {
        val cues = listOf(
            AudioCue.ExerciseIntro("Prensa", 4, MeasureType.REPS, 6, 8, null, false),
            AudioCue.ExerciseIntro("Plancha", 1, MeasureType.TIME, null, null, 60, true),
            AudioCue.SetStart(2),
            AudioCue.Go,
            AudioCue.SwitchSide,
            AudioCue.Halfway(10),
            AudioCue.ExerciseFinished("Extensión"),
            AudioCue.ExerciseFinished(null),
        ) + (0..20).map { AudioCue.Count(it) }
        cues.forEach { cue ->
            val words = VoiceCommands.normalize(SpokenText.of(cue).orEmpty()).replace(Regex("[^a-z0-9 ]"), " ")
                .split(" ").filter { it.isNotBlank() }
            val text = " " + words.joinToString(" ") + " "
            VoiceCommands.phrases.forEach { phrase ->
                assertTrue("«$phrase» aparece en el aviso $cue", !text.contains(" $phrase "))
            }
        }
    }
}
