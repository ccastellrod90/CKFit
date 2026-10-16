package com.ckfit.app.domain.model

enum class MeasureType { REPS, TIME }

/** F, V, Core, Cardio y otros. */
enum class LoadType { STRENGTH, VOLUME, CORE, CARDIO, OTHER }

enum class WeightUnit { KG, LB }

enum class Sex { MALE, FEMALE }

enum class SessionStatus { IN_PROGRESS, DONE, ABANDONED }

enum class SessionExerciseStatus { PENDING, IN_PROGRESS, DONE, SKIPPED }

enum class PlannedStatus { PENDING, DONE, SKIPPED, MISSED }

enum class Side { LEFT, RIGHT }
