package com.ckfit.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ckfit.app.domain.model.Sex
import com.ckfit.app.domain.model.WeightUnit
import java.time.LocalDate

/** Una sola fila (id = 1). La edad se calcula a partir de [birthDate]. [name] (v3) es para el saludo. */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = SINGLE_ID,
    val name: String? = null,
    val heightCm: Int? = null,
    val birthDate: LocalDate? = null,
    val sex: Sex? = null,
    val unit: WeightUnit = WeightUnit.KG,
) {
    companion object {
        const val SINGLE_ID = 1
    }
}
