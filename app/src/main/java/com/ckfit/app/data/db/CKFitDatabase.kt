package com.ckfit.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.ckfit.app.data.db.dao.BodyMeasurementDao
import com.ckfit.app.data.db.dao.DayExerciseDao
import com.ckfit.app.data.db.dao.ExerciseDao
import com.ckfit.app.data.db.dao.PlannedSessionDao
import com.ckfit.app.data.db.dao.ProfileDao
import com.ckfit.app.data.db.dao.RoutineDao
import com.ckfit.app.data.db.dao.SessionExerciseDao
import com.ckfit.app.data.db.dao.SetLogDao
import com.ckfit.app.data.db.dao.WorkoutSessionDao
import com.ckfit.app.data.db.entity.BodyMeasurementEntity
import com.ckfit.app.data.db.entity.DayExerciseEntity
import com.ckfit.app.data.db.entity.ExerciseEntity
import com.ckfit.app.data.db.entity.PlannedSessionEntity
import com.ckfit.app.data.db.entity.RoutineDayEntity
import com.ckfit.app.data.db.entity.RoutineEntity
import com.ckfit.app.data.db.entity.SessionExerciseEntity
import com.ckfit.app.data.db.entity.SetLogEntity
import com.ckfit.app.data.db.entity.UserProfileEntity
import com.ckfit.app.data.db.entity.WorkoutSessionEntity

@Database(
    entities = [
        UserProfileEntity::class,
        RoutineEntity::class,
        RoutineDayEntity::class,
        ExerciseEntity::class,
        DayExerciseEntity::class,
        PlannedSessionEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        SetLogEntity::class,
        BodyMeasurementEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CKFitDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun routineDao(): RoutineDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun dayExerciseDao(): DayExerciseDao
    abstract fun plannedSessionDao(): PlannedSessionDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun sessionExerciseDao(): SessionExerciseDao
    abstract fun setLogDao(): SetLogDao
    abstract fun bodyMeasurementDao(): BodyMeasurementDao

    companion object {
        const val NAME = "ckfit.db"

        /** Migraciones explícitas; cada cambio de esquema añade aquí la suya (nunca destructivas). */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
