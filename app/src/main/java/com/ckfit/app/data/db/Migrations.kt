package com.ckfit.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: session_exercise.exerciseId, para que el historial sepa qué ejercicio se hizo
 * aunque la prescripción (day_exercise) desaparezca del plan.
 * ADD COLUMN con REFERENCES es válido en SQLite porque el valor por defecto es NULL;
 * así no hace falta recrear la tabla (y no se tocan los set_log que cuelgan de ella).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `session_exercise` ADD COLUMN `exerciseId` INTEGER " +
                "REFERENCES `exercise`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_session_exercise_exerciseId` ON `session_exercise` (`exerciseId`)",
        )
        db.execSQL(
            "UPDATE `session_exercise` SET `exerciseId` = " +
                "(SELECT `exerciseId` FROM `day_exercise` WHERE `day_exercise`.`id` = `session_exercise`.`dayExerciseId`)",
        )
    }
}

/**
 * v3 → v4:
 * - exercise.usesWeight: los ejercicios de peso corporal y el cardio por tiempo no muestran peso.
 *   (Lista congelada aquí: una migración no debe cambiar si cambian los valores por defecto del código.)
 * - El peso actual pasa a ser por ejercicio: todas sus prescripciones comparten currentWeightKg
 *   (se unifica con el mayor que hubiera).
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `exercise` ADD COLUMN `usesWeight` INTEGER NOT NULL DEFAULT 1")
        val bodyweight = listOf(
            "Plancha isométrica", "Plancha lateral", "Bird dog (3 s arriba)", "Dead bug",
            "Cardio moderado bajo impacto", "Flexiones", "Dominadas", "Fondos en paralelas", "Hiperextensiones",
            "Elevación de piernas colgado", "Rueda abdominal", "Hollow hold", "Saltar la cuerda", "Bicicleta",
            "Caminadora", "Elíptica", "Remo ergómetro",
        )
        db.execSQL(
            "UPDATE `exercise` SET `usesWeight` = 0 WHERE `isCustom` = 0 AND " +
                "(`name` IN (${bodyweight.joinToString { "?" }}) OR " +
                "(`measureType` = 'TIME' AND lower(`muscleGroup`) IN ('core', 'cardio')))",
            bodyweight.toTypedArray(),
        )
        db.execSQL(
            "UPDATE `day_exercise` SET `currentWeightKg` = " +
                "(SELECT MAX(d2.`currentWeightKg`) FROM `day_exercise` d2 WHERE d2.`exerciseId` = `day_exercise`.`exerciseId`)",
        )
    }
}

/** v2 → v3: nombre del usuario para el saludo de Home. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `name` TEXT")
    }
}
