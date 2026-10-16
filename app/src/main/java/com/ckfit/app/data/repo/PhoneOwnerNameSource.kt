package com.ckfit.app.data.repo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Nombre del dueño del teléfono desde la tarjeta de contacto «Yo» (ContactsContract.Profile).
 * Requiere READ_CONTACTS; en muchos teléfonos la tarjeta está vacía y devuelve null.
 */
class PhoneOwnerNameSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Solo el primer nombre ("Carlos" de "Carlos Castellanos"), que es lo que usa el saludo. */
    suspend fun firstName(): String? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        runCatching {
            context.contentResolver.query(
                ContactsContract.Profile.CONTENT_URI,
                arrayOf(ContactsContract.Profile.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
    }
}
