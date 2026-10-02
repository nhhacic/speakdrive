package com.speakdrive.ai.session

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

fun interface MicPermissionChecker {
    fun hasMicPermission(): Boolean
}

class AndroidMicPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context
) : MicPermissionChecker {
    override fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}
