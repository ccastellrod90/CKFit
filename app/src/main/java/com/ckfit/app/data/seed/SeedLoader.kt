package com.ckfit.app.data.seed

import android.content.Context
import androidx.room.withTransaction
import com.ckfit.app.data.db.CKFitDatabase
import com.ckfit.app.data.db.entity.DayExerciseEntity
import com.ckfit.app.data.db.entity.ExerciseEntity
import com.ckfit.app.data.db.entity.RoutineDayEntity
import com.ckfit.app.data.db.entity.RoutineEntity
import com.ckfit.app.data.db.entity.UserProfileEntity
import com.ckfit.app.domain.model.WeightDefaults
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

interface SeedSource {
    fun planJson(): String
    fun catalogJson(): String
}

class AssetSeedSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SeedSource {
    override fun planJson(): String = read("seed/nivel2.json")
    override fun catalogJson(): String = read("seed/catalog.json")

    private fun read(path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
}

/**
 * Datos iniciales (sección 4): perfil vacío, catálogo y el plan "Nivel 2" activo.
 * Solo actúa si la base no tiene rutinas; todo en una transacción.
 */
@Singleton
class SeedLoader @Inject constructor(
    private val db: CKFitDatabase,
    private val source: SeedSource,
    private val clock: Clock,
) {
    suspend fun seedIfEmpty() {
        db.withTransaction {
            db.profileDao().insertIfAbsent(UserProfileEntity())
            if (db.routineDao().count() > 0) return@withTransaction

            val plan = SeedParser.parsePlan(source.planJson())
            val catalog = SeedParser.parseCatalog(source.catalogJson())

            val exerciseIds = HashMap<String, Long>()
            (plan.exercises + catalog).forEach { ex ->
                if (ex.name in exerciseIds) return@forEach
                exerciseIds[ex.name] = db.exerciseDao().insert(
                    ExerciseEntity(
                        name = ex.name,
                        muscleGroup = ex.muscleGroup,
                        measureType = ex.measureType,
                        perSide = ex.perSide,
                        isCustom = false,
                        usesWeight = WeightDefaults.usesWeight(ex.name, ex.measureType, ex.muscleGroup),
                    ),
                )
            }

            val routineId = db.routineDao().insertRoutine(
                RoutineEntity(name = plan.name, isActive = true, createdAt = clock.instant()),
            )
            plan.days.forEach { day ->
                val dayId = db.routineDao().insertDay(
                    RoutineDayEntity(
                        routineId = routineId,
                        dayOfWeek = day.dayOfWeek,
                        title = day.title,
                        colorHex = day.colorHex,
                        isOptional = day.isOptional,
                        isRestDay = day.isRestDay,
                    ),
                )
                day.prescriptions.forEachIndexed { index, p ->
                    db.dayExerciseDao().insert(
                        DayExerciseEntity(
                            dayId = dayId,
                            exerciseId = exerciseIds.getValue(p.exerciseName),
                            position = index,
                            loadType = p.loadType,
                            sets = p.sets,
                            repsMin = p.repsMin,
                            repsMax = p.repsMax,
                            durationSec = p.durationSec,
                            restSec = p.restSec,
                            restMaxSec = p.restMaxSec,
                            isIntensitySet = p.isIntensitySet,
                            intensityNote = p.intensityNote,
                            notes = p.notes,
                        ),
                    )
                }
            }
        }
    }
}
