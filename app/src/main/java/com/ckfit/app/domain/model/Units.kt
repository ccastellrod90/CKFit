package com.ckfit.app.domain.model

import java.time.LocalDate
import java.time.Period

const val KG_PER_LB = 0.45359237

/** Los pesos se guardan siempre en kg; esta conversión es solo para mostrar. */
fun Double.kgTo(unit: WeightUnit): Double = when (unit) {
    WeightUnit.KG -> this
    WeightUnit.LB -> this / KG_PER_LB
}

fun Double.toKg(from: WeightUnit): Double = when (from) {
    WeightUnit.KG -> this
    WeightUnit.LB -> this * KG_PER_LB
}

fun ageOn(birthDate: LocalDate, today: LocalDate): Int = Period.between(birthDate, today).years
