package com.riteldevelopment.carriertestoverride.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.riteldevelopment.carriertestoverride.R
import com.riteldevelopment.carriertestoverride.InstrumentationHostService
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Keeps the app's **default** process alive.
 *
 * The CarrierConfig layer works by having the shell-uid UserService ask ActivityManager to start this
 * app's own [com.riteldevelopment.carriertestoverride.CarrierConfigInstrumentation] with
 * `INSTR_FLAG_NO_RESTART`. AMS attaches that instrumentation to the default process, and the UI lives in
 * a separate `:ui` process precisely so this cannot take the interface down with it. If the default
 * process is not already running when the instrumentation starts, the flow is unreliable — so a trivial
 * bound service holds it up before any privileged work begins.
 */
class InstrumentationHost(context: Context) {

    private val appContext = context.applicationContext

    /** `bindService` was accepted, so the connection is registered and owes an `unbindService`. */
    private var registered = false

    /** `onServiceConnected` has fired, so the default process is actually up. */
    private var connected = false

    private var waiter: CancellableContinuation<Unit>? = null

    /**
     * Serialises binds. Without it, a second concurrent caller would overwrite [waiter] and the first
     * would suspend forever with no error and no timeout — which surfaces as a UI frozen mid-operation.
     */
    private val lock = Mutex()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            connected = binder != null && binder.pingBinder()
            val continuation = waiter
            waiter = null
            if (continuation == null || !continuation.isActive) return
            if (connected) {
                continuation.resume(Unit)
            } else {
                continuation.resumeWithException(
                    OverrideException(R.string.error_instrumentation_host)
                )
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connected = false
        }
    }

    suspend fun ensureBound() = lock.withLock {
        if (connected) return@withLock
        suspendCancellableCoroutine { continuation ->
            waiter = continuation
            continuation.invokeOnCancellation { waiter = null }
            val accepted = appContext.bindService(
                Intent(appContext, InstrumentationHostService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
            // Registered before the connection callback arrives: a bind that was accepted still has to be
            // unbound even if the user leaves before it connects, or the ServiceConnection leaks and keeps
            // the default process pinned.
            registered = registered || accepted
            if (!accepted) {
                waiter = null
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        OverrideException(R.string.error_instrumentation_host)
                    )
                }
            }
        }
    }

    fun release() {
        if (!registered) return
        runCatching { appContext.unbindService(connection) }
        registered = false
        connected = false
    }
}
