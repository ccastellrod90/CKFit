package com.ckfit.app.domain.model

/**
 * Qué ejercicios llevan peso por defecto. Los de peso corporal (planchas, bird dog, dominadas…) y el
 * cardio no muestran peso en el Modo ejercicio ni en las listas; el usuario puede cambiarlo en el editor.
 */
object WeightDefaults {

    /** Ejercicios del seed que se hacen sin peso (por nombre exacto). */
    val BODYWEIGHT_NAMES: Set<String> = setOf(
        "Plancha isométrica",
        "Plancha lateral",
        "Bird dog (3 s arriba)",
        "Dead bug",
        "Cardio moderado bajo impacto",
        "Flexiones",
        "Dominadas",
        "Fondos en paralelas",
        "Hiperextensiones",
        "Elevación de piernas colgado",
        "Rueda abdominal",
        "Hollow hold",
        "Saltar la cuerda",
        "Bicicleta",
        "Caminadora",
        "Elíptica",
        "Remo ergómetro",
    )

    private val NO_WEIGHT_TIME_GROUPS = setOf("core", "cardio")

    fun usesWeight(name: String, measureType: MeasureType, muscleGroup: String): Boolean =
        name !in BODYWEIGHT_NAMES &&
            !(measureType == MeasureType.TIME && muscleGroup.trim().lowercase() in NO_WEIGHT_TIME_GROUPS)
}
