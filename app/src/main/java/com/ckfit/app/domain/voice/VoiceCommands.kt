package com.ckfit.app.domain.voice

import com.ckfit.app.domain.engine.EngineEvent
import java.text.Normalizer

/** Una acción y las frases que la disparan (tal como se muestran en la ayuda, con tildes). */
data class VoiceCommandGroup(
    val action: VoiceAction,
    val event: EngineEvent,
    val phrases: List<String>,
)

enum class VoiceAction { START, DONE, NEXT, SKIP, MORE_REST, PAUSE, RESUME }

/**
 * Gramática cerrada de comandos (sección 8). El reconocedor solo puede devolver estas frases o "[unk]";
 * el texto se normaliza a minúsculas sin tildes antes de buscarlo.
 *
 * Cada palabra debe existir en el vocabulario del modelo (un test lo comprueba con el modelo real) y
 * ninguna puede aparecer en los avisos hablados ([SpokenText]): el micrófono oiría a la propia app.
 */
object VoiceCommands {

    // Antes que la gramática: se usan al construirla.
    private val DIACRITICS = "\\p{Mn}+".toRegex()
    private val SPACES = "\\s+".toRegex()

    val groups: List<VoiceCommandGroup> = listOf(
        VoiceCommandGroup(
            VoiceAction.START,
            EngineEvent.Start,
            listOf(
                "empezar", "empieza", "empezamos", "comenzar", "comienza", "comenzamos",
                "vamos", "iniciar", "inicia", "arranca", "dale", "venga", "adelante",
            ),
        ),
        VoiceCommandGroup(
            VoiceAction.DONE,
            EngineEvent.Done,
            listOf("listo", "lista", "ya", "ya está", "terminé", "acabé", "hecho", "fin", "basta"),
        ),
        VoiceCommandGroup(
            VoiceAction.NEXT,
            EngineEvent.Next,
            listOf("siguiente", "próximo", "siguiente ejercicio", "otro ejercicio"),
        ),
        VoiceCommandGroup(
            VoiceAction.SKIP,
            EngineEvent.Skip,
            listOf("saltar", "salta", "salto", "omitir", "omite", "pasar", "pasa", "paso"),
        ),
        VoiceCommandGroup(
            VoiceAction.MORE_REST,
            EngineEvent.AddRest(15),
            listOf("más tiempo", "más descanso", "un poco más", "otro poco", "dame más"),
        ),
        VoiceCommandGroup(
            VoiceAction.PAUSE,
            EngineEvent.Pause,
            listOf("pausa", "pausar", "para", "parar", "espera", "esperar", "alto", "detente", "stop", "un momento"),
        ),
        VoiceCommandGroup(
            VoiceAction.RESUME,
            EngineEvent.Resume,
            listOf("seguir", "sigue", "sigamos", "continúa", "continuar", "continuamos", "reanudar", "reanuda", "vuelve"),
        ),
    )

    private val commands: Map<String, EngineEvent> = buildMap {
        groups.forEach { g ->
            g.phrases.forEach { phrase ->
                val key = normalize(phrase)
                require(key !in this) { "Frase repetida en la gramática: $phrase" }
                put(key, g.event)
            }
        }
    }

    /** Todas las frases válidas ya normalizadas (para comprobar que los avisos hablados no contienen ninguna). */
    val phrases: Set<String> get() = commands.keys

    /** Gramática JSON para `Recognizer(model, 16000f, grammar)`: las frases + "[unk]" para el resto. */
    val grammarJson: String get() = (commands.keys + UNKNOWN).joinToString(prefix = "[", postfix = "]") { "\"$it\"" }

    fun parse(text: String): EngineEvent? = commands[normalize(text)]

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .replace(SPACES, " ")
            .trim()

    const val UNKNOWN = "[unk]"
}
