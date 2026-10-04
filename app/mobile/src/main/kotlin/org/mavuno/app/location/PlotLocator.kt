package org.mavuno.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** One GPS fix for "mark my plot". Uses the platform GPS, which works with no data connection. */
object PlotLocator {

    @SuppressLint("MissingPermission") // Caller requests ACCESS_FINE_LOCATION first.
    suspend fun currentLocation(context: Context, timeoutMs: Long = 60_000): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { lm.isProviderEnabled(it) }
            ?: return null
        val fix = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(context)) { cont.resume(it) }
            }
        }
        return fix ?: lm.getLastKnownLocation(provider)
    }
}
