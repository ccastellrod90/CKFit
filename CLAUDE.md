# CKFit — Especificación para Claude Code

App Android nativa, offline, de un solo usuario, para planificar rutinas de gimnasio por día de la semana, ejecutarlas manos libres (voz + audio) y ver la progresión. Este archivo es la fuente de verdad del proyecto: léelo completo antes de cada fase.

---

## 0. Reglas de trabajo (obligatorias)

1. **No asumir.** Si algo no está en este archivo o no estás seguro (versión de una librería, API de Android, comportamiento de un permiso), verifícalo primero (documentación oficial, Maven Central, código fuente) y solo después escribe código. Si sigue sin estar claro, pregunta.
2. **Versiones:** usa las últimas versiones *estables* de AGP, Kotlin, Compose BOM, Room, Hilt, Navigation, DataStore, Vico y Reorderable. Compruébalas en Maven/Google Maven antes de escribirlas en `libs.versions.toml`. No inventes números de versión.
3. **Una fase a la vez** (sección 11). Al terminar cada fase: `./gradlew testDebugUnitTest` y `./gradlew assembleDebug` deben pasar, y resume qué hiciste y cómo probarlo en el teléfono.
4. **No cambiar decisiones** de este archivo (stack, modelo de datos, comportamiento) sin preguntar. Si detectas un problema en el diseño, propón el cambio antes de aplicarlo.
5. Comandos que le des al usuario: completos y listos para copiar y pegar.
6. Idioma: UI, textos hablados y comentarios de dominio en **español**; identificadores de código en inglés.
7. Todos los textos visibles en `strings.xml` (nada hardcodeado en Composables).

---

## 1. Stack y configuración

| Pieza | Elección |
|---|---|
| Lenguaje / UI | Kotlin, Jetpack Compose, Material 3 |
| SDK | minSdk 26; compileSdk/targetSdk = el más reciente estable (verificar) |
| Arquitectura | MVVM + Clean (paquetes `ui/`, `domain/`, `data/`, `service/`, `di/`) en un módulo `app` + módulo `models` (modelo Vosk) |
| DI | Hilt |
| Persistencia | Room (datos) + DataStore Preferences (ajustes) |
| Async | Coroutines + Flow / StateFlow |
| Navegación | Navigation Compose con rutas tipadas (kotlinx.serialization) |
| Reordenar listas | `sh.calvin.reorderable` |
| Gráficas | Vico (artefacto para Compose M3) |
| Voz | Vosk: `com.alphacephei:vosk-android:<versión>@aar` + `net.java.dev.jna:jna:<versión>@aar` (verificar últimas; la demo oficial usa 0.3.75 y 5.18.1) |
| Audio | `TextToSpeech` (Locale es) + `ToneGenerator` para bips |
| Tests | JUnit, kotlinx-coroutines-test, Turbine, Room in-memory, Compose UI test |

- `applicationId`: `com.ckfit.app` (el usuario puede cambiarlo).
- Nombre visible: **CKFit**.
- Build types: `debug` y `release` (R8 activado en release; agregar reglas ProGuard que Vosk/JNA necesiten — verificar).
- ABIs: comprobar qué ABIs trae el AAR de Vosk y no filtrar ninguna necesaria para arm64-v8a.

---

## 2. Identidad visual

Tema **solo oscuro**, sin `dynamicColor`. Crear `ui/theme/Color.kt` exactamente así:

```kotlin
val Primary = Color(0xFF9FD33F)      // verde
val Accent = Color(0xFF45D9C5)       // turquesa
val Background = Color(0xFF0F1012)
val Card = Color(0xFF1A1D22)
val NavBar = Color(0xFF15171B)
val Track = Color(0xFF2A2E35)
val TextPrimary = Color(0xFFF4F4F4)
val TextSecondary = Color(0xFF8A9099)
val OnPrimary = Color(0xFF0F1012)
val AccentSoft = Color(0x2645D9C5)   // turquesa 15 %
```

`CKFitTheme` usa `darkColorScheme(...)` con este mapeo:

| Token | Rol M3 | Uso |
|---|---|---|
| Primary | primary | Botón Empezar/Listo, estado Cumplido, chip F, gráfica de pesos |
| OnPrimary | onPrimary | Texto sobre verde |
| Accent | secondary, tertiary | Anillo de descanso, cuentas atrás, chip V, indicador de micrófono |
| AccentSoft | secondaryContainer | Fondo de pantalla en descanso, chips seleccionados |
| Background | background | Fondo |
| Card | surface, surfaceContainer | Tarjetas |
| NavBar | surfaceContainerLow | Reservado (la top bar usa `Background` para que se vea continua con el contenido) |
| Track | outlineVariant | Pistas de anillos/barras, divisores |
| TextPrimary | onBackground, onSurface | Títulos, números grandes |
| TextSecondary | onSurfaceVariant | Metadatos, chip Core |

- Números del temporizador: sans condensada en negrita, tamaño grande (legible a 1 m).
- Guía visual (mockup `design/ideas/CKFit_mockup_app.png`), para colores secundarios y gráficas:
  - Home: logo arriba a la izquierda en lugar del título (con la fecha al lado); tarjetas `Card` con esquinas de 24 dp.
  - Etiquetas en `TextSecondary`, valores en `TextPrimary` negrita; valor destacado secundario (racha, pendientes) en `Accent`.
  - Anillos de progreso: arco `Primary` sobre pista `Track`, número grande en el centro (`ProgressRing`).
  - Barras de progreso de sesión, enlaces ("Ver semana", "Elegir otra sesión") y píldoras de variación (+5 kg) en `Accent`; píldoras con fondo del color al 15 %.
  - CTA principal ("Iniciar entrenamiento") en `Primary`, ancho completo.
- Logo: el usuario lo pondrá en `design/logo.png` (anillo turquesa, "CK" blanco/verde, "Fit" verde, mancuerna verde, fondo `#0F1012`). Ícono adaptativo: anillo + mancuerna sobre `#0F1012`. Splash (API SplashScreen) con el logo completo. Si el archivo no está, pídelo; no dibujes un logo inventado.

---

## 3. Modelo de datos (Room, 10 tablas)

El historial nunca se sobrescribe: cambiar el plan no altera sesiones pasadas.

```kotlin
enum class MeasureType { REPS, TIME }
enum class LoadType { STRENGTH, VOLUME, CORE, CARDIO, OTHER }   // F, V, Core, Cardio
enum class WeightUnit { KG, LB }
enum class SessionStatus { IN_PROGRESS, DONE, ABANDONED }
enum class SessionExerciseStatus { PENDING, IN_PROGRESS, DONE, SKIPPED }
enum class PlannedStatus { PENDING, DONE, SKIPPED, MISSED }
enum class Side { LEFT, RIGHT }
```

| Tabla | Campos | Notas |
|---|---|---|
| `user_profile` | id=1, name? (v3), heightCm, birthDate, sex?, unit | Una fila. Edad calculada. `name` para el saludo "Hola, <nombre>" de Home (se escribe en Ajustes o se toma de la tarjeta «Yo» del teléfono con permiso de Contactos, solo al pedirlo) |
| `routine` | id, name, isActive, createdAt | Un plan ("Nivel 2"). Solo uno activo |
| `routine_day` | id, routineId, dayOfWeek(1=lunes..7), title, colorHex, isOptional, isRestDay | |
| `exercise` | id, name, muscleGroup, measureType, perSide, isCustom, archived, usesWeight (v4) | Catálogo. `usesWeight=false` en peso corporal y cardio por tiempo: no se muestra ni registra peso |
| `day_exercise` | id, dayId, exerciseId, position, loadType, sets, repsMin?, repsMax?, durationSec?, restSec, restMaxSec?, currentWeightKg? (**por ejercicio**: todas las filas del mismo exerciseId comparten el peso, v4), weightStepKg (def 2.5), isIntensitySet, intensityNote?, notes? | Prescripción |
| `planned_session` | id, weekStart(LocalDate lunes), routineDayId, plannedDate, status, skipReason?, sessionId? | Cola semanal |
| `workout_session` | id, plannedSessionId?, routineDayId, date, startedAt, endedAt?, status | |
| `session_exercise` | id, sessionId, dayExerciseId?, exerciseId?, position, status | Orden real del día. `exerciseId` (v2) conserva el ejercicio aunque la prescripción desaparezca; siempre se rellena en filas nuevas |
| `set_log` | id, sessionExerciseId, setNumber, side?, weightKg?, reps?, durationSec?, completedAt | Una fila por serie (y por lado) |
| `body_measurement` | id, date, weightKg, waistCm?, notes? | Pesajes |

- Pesos siempre en **kg** en BD; conversión a lb solo en la UI.
- `set_log.reps` = reps objetivo (`repsMax`) en el MVP: las series se marcan cumplidas, no se registran reps reales todavía.
- Índices en claves foráneas; `ON DELETE CASCADE` de routine→days→day_exercise; **nunca** cascada desde `day_exercise` hacia historial (usar `SET NULL` o soft delete: añadir `archived` a `day_exercise` y `exercise`).
- Room `exportSchema = true` y migraciones explícitas desde la versión 1.

Valores por defecto al elegir `loadType` en el editor:

| loadType | reps | restSec / restMaxSec |
|---|---|---|
| STRENGTH | 6–8 | 75 / 90 |
| VOLUME | 10–12 | 45 / 60 |
| CORE | — | 60 |
| CARDIO | TIME, 1 serie | 0 |

---

## 4. Datos iniciales

Al primer arranque: crear `user_profile` vacío, catálogo de ejercicios y el plan **"Nivel 2"** activo desde `app/src/main/assets/seed/nivel2.json` (crear ese archivo con este contenido). Descanso: `rest` = [restSec, restMaxSec]. `perSide` = por lado. `intensity` = serie ◆.

```json
{
  "routine": "Nivel 2",
  "days": [
    { "dayOfWeek": 1, "title": "Pierna A (cuádriceps) + Pantorrilla + Bíceps", "color": "#9FD33F", "optional": false,
      "exercises": [
        { "name": "Prensa (leg press)", "group": "Cuádriceps", "type": "REPS", "load": "STRENGTH", "sets": 4, "reps": [6, 8], "rest": [75, 90], "notes": "Pies a la anchura de los hombros; la espalda baja no se despega del respaldo abajo." },
        { "name": "Extensión de rodilla máquina", "group": "Cuádriceps", "type": "REPS", "load": "VOLUME", "sets": 4, "reps": [10, 12], "rest": [45, 60] },
        { "name": "Zancada lateral con mancuerna", "group": "Pierna", "type": "REPS", "perSide": true, "load": "VOLUME", "sets": 4, "reps": [10, 12], "rest": [45, 60] },
        { "name": "Pantorrilla máquina sentado / grada", "group": "Pantorrilla", "type": "REPS", "load": "VOLUME", "sets": 4, "reps": [15, 20], "rest": [45, 60] },
        { "name": "Curl martillo alterno / curl barra Z", "group": "Bíceps", "type": "REPS", "load": "VOLUME", "sets": 4, "reps": [10, 12], "rest": [45, 60], "intensity": true },
        { "name": "Plancha isométrica", "group": "Core", "type": "TIME", "load": "CORE", "sets": 4, "durationSec": 20, "rest": [60] },
        { "name": "Plancha lateral", "group": "Core", "type": "TIME", "perSide": true, "load": "CORE", "sets": 3, "durationSec": 20, "rest": [60] }
      ] },
    { "dayOfWeek": 2, "title": "Empuje: Pecho + Hombro + Tríceps", "color": "#F28C28", "optional": false,
      "exercises": [
        { "name": "Press plano máquina", "group": "Pecho", "type": "REPS", "load": "STRENGTH", "sets": 4, "reps": [6, 8], "rest": [75, 90] },
        { "name": "Press inclinado máquina", "group": "Pecho", "type": "REPS", "load": "VOLUME", "sets": 4, "reps": [10, 12], "rest": [45, 60] },
        { "name": "Aperturas peck deck", "group": "Pecho", "type": "REPS", "load": "VOLUME", "sets": 4, "reps": [10, 12], "rest": [45, 60] },
        { "name": "Press militar con mancuerna", "group": "Hombro", "type": "REPS", "load": "STRENGTH", "sets": 4, "reps": [6, 8], "rest": [75, 90] },
        { "name": "Elevaciones laterales con mancuerna", "group": "Hombro", "type": "REPS", "load": "VOLUME", "sets": 4, "reps": [12, 15], "rest": [45, 60] },
        { "name": "Copa con mancuerna sentado", "group": "Tríceps", "type": "REPS", "load": "VOLUME", "sets": 4, "reps": [10, 12], "rest": [45, 60], "intensity": true },
        { "name": "Bird dog (3 s arriba)", "group": "Core", "type": "REPS", "load": "CORE", "sets": 4, "reps": [10, 10], "rest": [60] },
        { "name": "Dead bug", "group": "Core", "type": "REPS", "perSide": true, "load": "CORE", "sets": 3, "reps": [10, 10], "rest": [60] }
      ] },
    { "dayOfWeek": 3, "title": "Jalón: Espalda + Hombro posterior + Bíceps", "color": "#45D9C5", "optional": false,
      "exercises": [
        { "name": "Jalón frontal abierto con barra", "group": "Espalda", "type": "REPS", "load": "STRENGTH", "sets": 4, "reps": [6, 8], "rest": [75, 90] },
        { "name": "Remo horizontal estribo", "group": "Espalda", "type": "REPS", "load": "VOLUME", "sets": 4, "reps": [10, 12], "rest": [45, 60] },
        { "name": "Pull over mancuerna en banca plana", "group": "Espalda", "type": "REPS", "load": "VOLUME", "sets": 4, "reps": [10, 12], "rest": [45, 60] },
        { "name": "Posterior de hombro peck deck", "group": "Hombro", "type": "REPS", "load": "VOLUME", "sets": 4, "reps": [12, 15], "rest": [45, 60] },
        { "name": "Curl inclinado con mancuerna / banco Scott", "group": "Bíceps", "type": "REPS", "load": "VOLUME", "sets": 3, "reps": [10, 12], "rest": [45, 60], "intensity": true },
        { "name": "Leñador en polea", "group": "Core", "type": "REPS", "perSide": true, "load": "CORE", "sets": 3, "reps": [12, 12], "rest": [60], "notes": "El giro sale de la cadera, no de la cintura." },
        { "name": "Pallof press", "group": "Core", "type": "REPS", "perSide": true, "load": "CORE", "sets": 3, "reps": [10, 10], "rest": [60] }
      ] },
    { "dayOfWeek": 4, "title": "Pierna B (femoral/glúteo) + Pantorrilla + Tríceps", "color": "#2BA89A", "optional": false,
      "exercises": [
        { "name": "Peso muerto rumano con barra", "group": "Femoral", "type": "REPS", "load": "STRENGTH", "sets": 4, "reps": [6, 8], "rest": [75, 90], "notes": "Barra pegada a las piernas, espalda recta, cadera atrás. Sin técnicas de intensidad." },
        { "name": "Flexión de rodilla sentado", "group": "Femoral", "type": "REPS", "load": "VOLUME", "sets": 4, "reps": [10, 12], "rest": [45, 60] },
        { "name": "Máquina abductor sentado", "group": "Glúteo", "type": "REPS", "load": "VOLUME", "sets": 4, "reps": [12, 15], "rest": [45, 60] },
        { "name": "Pantorrilla máquina sentado / grada", "group": "Pantorrilla", "type": "REPS", "load": "VOLUME", "sets": 4, "reps": [15, 20], "rest": [45, 60] },
        { "name": "Jalón en polea con cuerda", "group": "Tríceps", "type": "REPS", "load": "VOLUME", "sets": 4, "reps": [10, 12], "rest": [45, 60], "intensity": true },
        { "name": "Plancha isométrica", "group": "Core", "type": "TIME", "load": "CORE", "sets": 4, "durationSec": 20, "rest": [60] },
        { "name": "Bird dog (3 s arriba)", "group": "Core", "type": "REPS", "load": "CORE", "sets": 4, "reps": [10, 10], "rest": [60] }
      ] },
    { "dayOfWeek": 5, "title": "Opcional: Brazo dedicado", "color": "#8A9099", "optional": true,
      "exercises": [
        { "name": "Curl barra Z / polea baja", "group": "Bíceps", "type": "REPS", "load": "VOLUME", "sets": 4, "reps": [12, 12], "rest": [45, 60] },
        { "name": "Curl concentrado / araña", "group": "Bíceps", "type": "REPS", "load": "VOLUME", "sets": 3, "reps": [12, 12], "rest": [45, 60] },
        { "name": "Press francés barra Z / mancuerna", "group": "Tríceps", "type": "REPS", "load": "VOLUME", "sets": 4, "reps": [12, 12], "rest": [45, 60] },
        { "name": "Fondos en banco / extensión en polea", "group": "Tríceps", "type": "REPS", "load": "VOLUME", "sets": 3, "reps": [12, 12], "rest": [45, 60] },
        { "name": "Elevaciones laterales con mancuerna", "group": "Hombro", "type": "REPS", "load": "VOLUME", "sets": 3, "reps": [15, 15], "rest": [45, 60], "notes": "Si sobra tiempo." }
      ] },
    { "dayOfWeek": 6, "title": "Opcional: Cardio", "color": "#45D9C5", "optional": true,
      "exercises": [
        { "name": "Cardio moderado bajo impacto", "group": "Cardio", "type": "TIME", "load": "CARDIO", "sets": 1, "durationSec": 2700, "rest": [0] }
      ] },
    { "dayOfWeek": 7, "title": "Descanso", "restDay": true, "exercises": [] }
  ]
}
```

- `intensityNote` por defecto en ejercicios con `intensity`: "Última serie: rest-pause o drop set (alternar semanal)".
- Catálogo adicional (~40 ejercicios, sin plan): sentadilla, press banca, dominadas, remo con barra, hip thrust, face pull, crunch en polea, zancada, prensa de hombro máquina, farmer walk (TIME), saltar la cuerda (CARDIO, TIME), bicicleta (CARDIO, TIME), caminadora (CARDIO, TIME), etc. Mismos cuatro campos.
- Los ejercicios repetidos en varios días (p. ej. plancha, pantorrilla) son **una sola fila** en `exercise` y varias en `day_exercise`.

---

## 5. Navegación y pantallas

| Ruta | Pantalla |
|---|---|
| `Home` | Tarjetas: Rutina de hoy, Configuración de rutinas, Progresión; ⚙ arriba a la derecha |
| `Today` | Rutina de hoy |
| `ExerciseFocus(sessionExerciseId)` | Modo ejercicio |
| `Routines` → `RoutineDays(routineId)` → `DayEditor(dayId)` → `ExerciseEditor(dayExerciseId?, dayId?, exerciseId?)` | Configuración (editar una prescripción, o crear una con día + ejercicio) |
| `Progress` | Progresión (pestañas Ejercicios / Semana / Cuerpo) |
| `Settings` | Ajustes |

**Home**
- Tarjeta Hoy: título de la sesión sugerida (ver sección 6), nº de ejercicios, tiempo estimado, progreso (3/6); "Continuar" si hay sesión IN_PROGRESS. Día de descanso o nada pendiente: lo dice y ofrece "Entrenar otra sesión".
- Tarjeta Rutinas: plan activo y días configurados.
- Tarjeta Progresión: último pesaje y variación 30 días.

**Rutina de hoy**
- Tarjetas por ejercicio: nombre, chip F/V/Core/Cardio, `4 × 6–8 · 75 s · 120 kg`, estado (Por hacer / En curso / Cumplido / Saltado), ◆ si `isIntensitySet`.
- Drag & drop para reordenar (guarda en `session_exercise.position`, no toca el plan).
- Cabecera: tiempo estimado total y restante; acciones "Elegir otra sesión", "Saltar sesión", "Terminar entrenamiento".
- Tiempo estimado por ejercicio = `sets × (trabajo + restSec) − restSec`. Trabajo REPS = `repsMax × 4 s` (× 2 si perSide). Trabajo TIME = `durationSec × (2 si perSide) + switchSec`.

**Modo ejercicio** (pantalla completa, `FLAG_KEEP_SCREEN_ON`)
- Arriba: nombre, "Serie 2 de 4", objetivo ("6–8 reps" / "20 s por lado").
- Centro: estado grande (Esperando / cuenta 3·2·1 / EN SERIE con cronómetro ↑ o cuenta atrás ↓ / DESCANSO con anillo turquesa sobre `Track`).
- Peso actual con − / + (`weightStepKg`) y tap para editar; al cambiar se guarda en `day_exercise.currentWeightKg` y en los siguientes `set_log`.
- Botones grandes: Empezar/Listo, Saltar, +15 s, Pausa, interruptor Auto/Manual. Indicador de micrófono (escuchando / apagado / sin permiso).
- Sugerencia de doble progresión si aplica (sección 9).
- Al terminar: "Ejercicio cumplido" + botón "Siguiente: <nombre>".

**Configuración de rutinas**
- Lista de planes (crear, duplicar, renombrar, activar; uno activo).
- Plan → 7 filas lunes–domingo (título, color, opcional, descanso).
- Día → lista reordenable + "Agregar ejercicio" (buscador de catálogo o crear nuevo).
- Editor: tipo REPS/TIME, por lado, loadType (prellena defaults de la sección 3), series, reps mín–máx o segundos, descanso (+ máx opcional), peso inicial, paso de peso, ◆ + nota, notas. Cardio se agrega a cualquier día.

**Ajustes**
- Perfil: altura (cm), fecha de nacimiento, sexo (opcional), unidades kg/lb.
- Entrenamiento: Siguiente serie (Automático por defecto / Manual), segundos de cambio de lado (def 5).
- Voz: activar comandos, prueba de micrófono (muestra la última palabra reconocida).
- Audio: TTS on/off, volumen propio, bips on/off, ducking on/off, vibración on/off.
- Cuentas atrás: final del descanso desde N s (def 5), previa a ejercicios por tiempo N s (def 3), aviso a mitad de serie por tiempo (def off).
- Datos: exportar/importar JSON (Storage Access Framework), borrar historial (con confirmación).

---

## 6. Semana flexible

"Rutina de hoy" = **siguiente sesión pendiente de la semana**, no el día del calendario.

- Al abrir la app en una semana nueva (lunes = `weekStart`), generar una `planned_session` por día no-descanso del plan activo, `status = PENDING`.
- Sesión sugerida: la primera obligatoria PENDING por orden de `dayOfWeek`; las opcionales solo cuando no quedan obligatorias. Si hay una IN_PROGRESS, esa.
- Al cerrar la semana (primera apertura después del domingo): obligatorias PENDING → MISSED; opcionales PENDING → se dejan (no cuentan como falta). Nada se arrastra.
- Acciones: **Elegir otra sesión** (cualquier PENDING de la semana), **Saltar sesión** (SKIPPED + motivo opcional; no se vuelve a sugerir), **Deshacer salto** (si la semana sigue abierta).
- Al terminar una sesión: `planned_session.status = DONE`, `sessionId` enlazado.

Caso de aceptación: plan L-M-X-J-V(opc)-S(opc). No se entrena el miércoles → el jueves se sugiere Jalón (X); el viernes, Pierna B (J); el sábado, las opcionales.

---

## 7. Motor de entrenamiento (`domain/engine/WorkoutEngine`)

Kotlin puro (sin Android), probado con reloj virtual. Recibe eventos, emite `StateFlow<EngineState>` y `Flow<AudioCue>`.

```kotlin
sealed interface EngineEvent {
    data object Start : EngineEvent       // "empezar"
    data object Done : EngineEvent        // "listo"
    data object Skip : EngineEvent        // "saltar" (descanso o serie)
    data class AddRest(val sec: Int = 15) : EngineEvent
    data object Pause : EngineEvent
    data object Resume : EngineEvent
    data object Next : EngineEvent        // "siguiente"
    data object EndExercise : EngineEvent // "Terminar ejercicio" antes de hacer todas las series (la serie a medias no cuenta)
}

sealed interface EngineState {
    data class Waiting(val set: Int, val total: Int) : EngineState
    data class PreCountdown(val remaining: Int) : EngineState         // solo TIME
    data class WorkingReps(val set: Int, val side: Side?, val elapsedMs: Long) : EngineState
    data class WorkingTimed(val set: Int, val side: Side?, val remainingMs: Long) : EngineState
    data class SwitchSide(val set: Int, val remainingMs: Long) : EngineState
    data class Resting(val nextSet: Int, val remainingMs: Long, val totalMs: Long) : EngineState
    data class Paused(val previous: EngineState) : EngineState
    data object ExerciseDone : EngineState
}
```

Transiciones:
1. Serie 1 siempre empieza en `Waiting` → `Start`.
2. REPS: `WorkingReps` (cronómetro ↑) → `Done` → si perSide y lado 1: `SwitchSide`/"cambio, otro lado" y sigue WorkingReps lado 2 hasta `Done` → guardar `set_log` → `Resting`.
3. TIME: `PreCountdown` (3·2·1) → `WorkingTimed` (↓) → a 0: si perSide y lado 1 → `SwitchSide` ("cambio" + `switchSec` con 3·2·1) → `WorkingTimed` lado 2 → guardar `set_log` → `Resting`.
4. `Resting` termina a 0 o con `Skip`: si quedan series → **Automático**: arranca la siguiente serie (TIME pasa por PreCountdown); **Manual**: `Waiting` + bip, espera `Start` sin límite. Si no quedan → `ExerciseDone`.
5. `Resting` con restSec = 0 → pasa directo.
6. `Pause` congela cualquier estado; `Resume` vuelve con el tiempo restante.

Reglas:
- Tiempo por diferencia con un `TimeSource` inyectado (en Android: `SystemClock.elapsedRealtime()`), nunca sumando ticks.
- Cada `set_log` se persiste al cerrarse la serie. Además, el estado del motor (`EngineSnapshot`, tiempos absolutos de elapsedRealtime) se guarda en DataStore en cada transición: si el proceso muere, el servicio (START_STICKY) o la app al abrirse lo restauran y el descanso o la serie siguen donde iban (tras un reinicio del teléfono se traslada con el reloj de pared).
- Botones y voz disparan exactamente los mismos `EngineEvent`.
- Cuenta final del descanso: empieza a N s del fin (def 5); si restSec < N, desde restSec.

`AudioCue` emitidos (el servicio los traduce a TTS/bips):

| Momento | Cue |
|---|---|
| Entrar a Waiting (serie 1) | Speak("Prensa, 4 series de 6 a 8") |
| Inicio serie REPS (2+) | Speak("Serie 2, vamos") |
| PreCountdown | Speak("tres", "dos", "uno", "ya") |
| Serie TIME a mitad (si activo) | Speak("diez") (la mitad en s) |
| Últimos 3 s de serie TIME | Speak("tres", "dos", "uno") |
| Cambio de lado | Speak("cambio") + 3·2·1 |
| Inicio descanso | Beep(short) |
| Últimos N s de descanso | Speak("cinco", … "uno") |
| Fin descanso en Manual | Beep(double) |
| ExerciseDone | Speak("Ejercicio terminado. Siguiente: <nombre>") |

**Nunca** pronunciar palabras de comando ni sus sinónimos de la gramática ("listo", "ya", "empezar", "vamos", "siguiente", "saltar", "pausa", "para", "seguir"…) en los cues. Textos usados (`domain/voice/SpokenText`): "Serie 2, a tope" (no "vamos"), "Arriba" (no "ya"), "Ejercicio terminado. Ahora toca <nombre>" (no "Siguiente"). Un test lo verifica.

---

## 8. Voz (Vosk) y audio

Comandos (gramática cerrada, texto normalizado a minúsculas sin tildes):

La lista completa de frases por acción vive en `domain/voice/VoiceCommands` (se muestra en la ayuda de la app; un test verifica que todas existen en el vocabulario del modelo). Resumen:

| Comando | Sinónimos | Evento |
|---|---|---|
| empezar | vamos, iniciar, dale | Start |
| listo | ya, termine, fin | Done |
| siguiente | proximo | Next |
| saltar | salta, omitir | Skip |
| mas tiempo | mas descanso | AddRest(15) |
| pausa | para, espera | Pause |
| seguir | continua, reanudar | Resume |

Implementación:
- Interfaz `VoiceCommandSource { fun start(); fun stop(); val commands: Flow<EngineEvent> }`, implementación `VoskCommandSource`.
- Modelo `vosk-model-small-es-0.42` (39 MB, Apache 2.0, https://alphacephei.com/vosk/models) dentro del módulo `models` como en la demo oficial (https://github.com/alphacep/vosk-android-demo): primera vez `StorageService.unpack(context, "<carpeta-en-assets>", "model", onOk, onError)`. Verifica en la demo la estructura exacta del módulo `models` (incluido el archivo `uuid` que usa `StorageService`) antes de replicarla. Descarga del zip: dar al usuario el comando exacto para bajarlo y descomprimirlo en la carpeta correcta; **no** commitear el modelo si el repo es git (añadir a `.gitignore` y documentar el paso).
- `Recognizer(model, 16000f, grammarJson)` con todas las palabras/frases de la tabla + `"[unk]"`.
- `SpeechService(recognizer, 16000f).startListening(listener)` al iniciar la sesión; `stop()` + `shutdown()` al terminar, pausar la sesión o tras 10 min sin actividad.
- Disparar solo en `onResult`/`onFinalResult` (parsear JSON `text`); ignorar `onPartialResult`. Debounce 800 ms para el mismo comando.
- Mientras el TTS habla (`UtteranceProgressListener`), ignorar resultados (o `setPause(true)` si la API lo permite — verificar).
- Sin permiso `RECORD_AUDIO`: la app funciona completa con botones; ocultar indicador de voz.

Audio:
- `TextToSpeech` con Locale `es` (preferir es-US/es-419 si existe; verificar voces disponibles y ofrecer instalar datos si falta).
- `AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` + foco `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` (si ducking activo) para que la música baje mientras habla.
- Bips con `ToneGenerator`. Vibración opcional en cada cambio de estado.

Servicio:
- `WorkoutService` foreground, declarado `mediaPlayback|microphone`: arranca siempre como `mediaPlayback` (los avisos hablados son audio) y añade `microphone` solo si hay permiso RECORD_AUDIO y voz activada (Android 14+ exige el permiso concedido antes de `startForeground` con ese tipo; sin permiso la app debe funcionar igual). Iniciado desde la UI visible (requisito de Android 12+). Wake lock parcial mientras hay entrenamiento. Mantiene el `WorkoutEngine`, `VoskCommandSource` y el reproductor de cues. Notificación persistente con estado + acciones Listo / Pausa.
- Permisos: `RECORD_AUDIO` (pedir al primer entrenamiento, no al instalar), `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS` (13+), `VIBRATE`, `WAKE_LOCK`. Verificar si hace falta algún otro según el targetSdk elegido.
- En Ajustes, enlace para excluir la app de la optimización de batería (Xiaomi/Samsung).

---

## 9. Progresión y estadísticas

Todo con consultas sobre `set_log`, `planned_session` y `body_measurement` (sin tablas de agregados).

- **Ejercicios:** selector (plan activo primero); gráfica de línea del peso de trabajo por sesión (máx. `weightKg` de la sesión), rangos 1/3/6 meses/todo; tarjetas: mejor serie, 1RM estimado, volumen última sesión, variación vs hace 4 semanas. En TIME se grafica la duración.
- **Doble progresión:** si en la última sesión todas las series del ejercicio se completaron con el mismo peso y `repsMax`, mostrar "Subir a X kg" (X = peso + `weightStepKg`) aquí y en Modo ejercicio; aceptar actualiza `currentWeightKg`. (En el MVP, como las series se marcan al objetivo, la sugerencia aparece tras cada sesión completa; el usuario decide.)
- **Semana:** sesiones hechas vs planificadas obligatorias (SKIPPED y MISSED por separado), series, volumen, minutos, racha de semanas completas, semanas desde que se activó el plan con aviso de deload a las 6–8 semanas.
- **Cuerpo:** registrar pesaje (fecha hoy por defecto, peso, cintura opcional), gráfica de peso con media móvil de 7 registros, gráfica de cintura, IMC y edad.

Fórmulas: `volumen = Σ peso × reps`; `1RM (Epley) = peso × (1 + reps/30)` solo si reps ≤ 12; `IMC = kg / m²`.

---

## 10. Exportar / importar

JSON versionado (`{"schemaVersion":1, ...}`) con todas las tablas. Importar reemplaza todo tras confirmación. Nombre: `ckfit-backup-YYYYMMDD.json`.

---

## 11. Fases (una a la vez, con criterio de "listo")

| Fase | Entrega | Listo cuando |
|---|---|---|
| 1. Base | Proyecto Gradle (version catalog), Hilt, tema CKFit, Room con 10 tablas + migración base, seed `nivel2.json`, Home con tarjetas (datos reales), Ajustes de perfil | Al instalar, Home muestra la sesión de hoy del plan Nivel 2 con su tiempo estimado |
| 2. Rutinas y semana | CRUD de planes/días/ejercicios, duplicar plan, reordenar, defaults por loadType, cola semanal (`planned_session`), elegir/saltar/deshacer sesión | Se puede crear "Nivel 3" duplicando y editando sin tocar código; el caso del miércoles (sección 6) pasa en un test |
| 3. Entrenar con botones | Rutina de hoy, `WorkoutEngine` + tests, Modo ejercicio, `WorkoutService` + notificación, `set_log`, actualizar peso, Auto/Manual | Una sesión completa del lunes se registra con la pantalla apagada entre series y sobrevive a matar la app |
| 4. Audio y voz | Cues TTS + bips + ducking, cuentas atrás configurables, Vosk con gramática, permisos, prueba de micrófono | Plancha lateral 3 × 20 s por lado se completa sin tocar el teléfono; "empezar"/"listo" funcionan con música de fondo |
| 5. Progresión | Pestañas Ejercicios/Semana/Cuerpo con Vico, doble progresión, pesajes, IMC, exportar/importar | Con datos de prueba de 4 semanas se ven las curvas y la sugerencia de subir peso |

Tests mínimos:
- `WorkoutEngine`: REPS normal, REPS perSide, TIME, TIME perSide, descanso 0, Auto vs Manual, Skip, AddRest, Pause/Resume, cues emitidos en orden (reloj virtual).
- Semana flexible: generación, sugerencia, cierre de semana (MISSED), saltar/deshacer, caso del miércoles.
- DAOs con Room in-memory; parser del seed; normalizador de comandos de voz.

---

## 12. Fuera del MVP

Sincronización en la nube, varios perfiles, Wear OS, videos de técnica, reps reales por serie, micrófono de audífonos Bluetooth (SCO), planes generados automáticamente.
