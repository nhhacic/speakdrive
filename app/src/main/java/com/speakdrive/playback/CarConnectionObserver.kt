package com.speakdrive.playback

import android.content.Context
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/** Tells the phone UI whether the phone is currently projecting to Android Auto. */
@Singleton
open class CarConnectionObserver internal constructor(
    private val context: Context?,
    @Suppress("UNUSED_PARAMETER") forTesting: Boolean
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, false)

    constructor() : this(null, true)

    open val isConnectedToCar: Flow<Boolean> = callbackFlow {
        val ctx = context ?: return@callbackFlow
        val liveData = CarConnection(ctx).type
        val observer = Observer<Int> { type -> trySend(type == CarConnection.CONNECTION_TYPE_PROJECTION) }
        liveData.observeForever(observer)
        awaitClose { liveData.removeObserver(observer) }
    }.flowOn(Dispatchers.Main).distinctUntilChanged()
}
