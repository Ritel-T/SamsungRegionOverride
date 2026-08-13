package com.riteldevelopment.carriertestoverride.data

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.IBinder
import android.content.ServiceConnection
import com.riteldevelopment.carriertestoverride.BuildConfig
import com.riteldevelopment.carriertestoverride.CarrierOverrideUserService
import com.riteldevelopment.carriertestoverride.ICarrierOverrideService
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A failure whose message is already written for the user. */
class OverrideException(message: String) : Exception(message)

/** What the UI shows in the Shizuku status slot. */
sealed interface ShizukuStatus {
    /** Shizuku's binder is not available — the service is not running, or was never started. */
    data object NotRunning : ShizukuStatus

    data class Connected(val uid: Int, val granted: Boolean) : ShizukuStatus {
        /** Shizuku normally runs as shell (2000); root (0) also works. Anything else is suspicious. */
        val privileged: Boolean get() = uid == UID_SHELL || uid == UID_ROOT
    }

    data class Unavailable(val reason: String) : ShizukuStatus

    companion object {
        const val UID_SHELL = 2000
        const val UID_ROOT = 0
    }
}

/**
 * Owns every interaction with Shizuku: liveness, permission, and the privileged UserService.
 *
 * The 2.x implementation drove this with three listeners that each re-entered a shared
 * `pendingAction` field, so "apply" was really a state machine spread across five callbacks. Here the
 * callbacks only ever push into a [StateFlow] or resume a suspended caller, which lets an operation be
 * written as one straight-line coroutine — and lets the user cancel it, which was impossible before.
 */
class ShizukuController {

    private val _status = MutableStateFlow<ShizukuStatus>(ShizukuStatus.NotRunning)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    /** Continuations parked in [awaitBinder], resumed when Shizuku's binder shows up. */
    private val binderWaiters = mutableListOf<CancellableContinuation<Unit>>()

    /** Continuations parked in [requestPermission]; at most one request is in flight. */
    private var permissionWaiter: CancellableContinuation<Boolean>? = null

    private var service: ICarrierOverrideService? = null
    private var serviceWaiter: CancellableContinuation<ICarrierOverrideService>? = null
    private var bound = false

    /** Serialises bind attempts so two concurrent operations cannot both start a UserService. */
    private val bindLock = Mutex()

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, CarrierOverrideUserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("carrier_override")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        refreshStatus()
        synchronized(binderWaiters) {
            binderWaiters.forEach { if (it.isActive) it.resume(Unit) }
            binderWaiters.clear()
        }
    }

    private val binderDead = Shizuku.OnBinderDeadListener {
        service = null
        bound = false
        _status.value = ShizukuStatus.NotRunning
        serviceWaiter?.takeIf { it.isActive }
            ?.resumeWithException(OverrideException("Shizuku disconnected. Restart it and try again."))
        serviceWaiter = null
    }

    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != PERMISSION_REQUEST_CODE) return@OnRequestPermissionResultListener
            refreshStatus()
            val waiter = permissionWaiter
            permissionWaiter = null
            waiter?.takeIf { it.isActive }
                ?.resume(grantResult == PackageManager.PERMISSION_GRANTED)
        }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            bound = true
            val remote = binder
                ?.takeIf { it.pingBinder() }
                ?.let { ICarrierOverrideService.Stub.asInterface(it) }
            service = remote
            val waiter = serviceWaiter
            serviceWaiter = null
            if (waiter == null || !waiter.isActive) return
            if (remote == null) {
                waiter.resumeWithException(
                    OverrideException("Shizuku UserService returned an invalid binder.")
                )
            } else {
                waiter.resume(remote)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            serviceWaiter?.takeIf { it.isActive }
                ?.resumeWithException(OverrideException("The Carrier Override UserService disconnected."))
            serviceWaiter = null
        }
    }

    fun start() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refreshStatus()
    }

    fun stop() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
        if (service != null || bound) {
            runCatching { Shizuku.unbindUserService(userServiceArgs, connection, false) }
        }
        service = null
        bound = false
    }

    fun refreshStatus() {
        _status.value = try {
            if (!Shizuku.pingBinder()) {
                ShizukuStatus.NotRunning
            } else {
                ShizukuStatus.Connected(
                    uid = Shizuku.getUid(),
                    granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED,
                )
            }
        } catch (throwable: Throwable) {
            ShizukuStatus.Unavailable(throwable.javaClass.simpleName)
        }
    }

    /**
     * Suspends until Shizuku is running. The 2.x build achieved this implicitly by leaving `pendingAction`
     * set so the sticky binder listener would re-enter it later; making it an explicit suspension point
     * means the UI can say it is waiting, and the user can cancel.
     */
    suspend fun awaitBinder() {
        if (Shizuku.pingBinder()) return
        suspendCancellableCoroutine { continuation ->
            synchronized(binderWaiters) { binderWaiters.add(continuation) }
            continuation.invokeOnCancellation {
                synchronized(binderWaiters) { binderWaiters.remove(continuation) }
            }
            // Shizuku may have connected between the check and the registration.
            if (Shizuku.pingBinder() && continuation.isActive) {
                synchronized(binderWaiters) { binderWaiters.remove(continuation) }
                continuation.resume(Unit)
            }
        }
    }

    /** Throws if this Shizuku build predates the API 11 UserService support this app requires. */
    fun requireModernApi() {
        if (Shizuku.isPreV11()) {
            throw OverrideException("Shizuku API 11 or newer is required.")
        }
    }

    suspend fun requestPermission(): Boolean {
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            throw OverrideException(
                "Shizuku permission was denied. Re-allow this app in Shizuku's authorised apps."
            )
        }
        return suspendCancellableCoroutine { continuation ->
            permissionWaiter = continuation
            continuation.invokeOnCancellation { permissionWaiter = null }
            try {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            } catch (throwable: Throwable) {
                permissionWaiter = null
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        OverrideException(
                            "Could not reach Shizuku: ${throwable.javaClass.simpleName}: " +
                                throwable.message.orEmpty()
                        )
                    )
                }
            }
        }
    }

    /** Binds — or reuses — the shell-identity UserService that performs the privileged calls. */
    suspend fun bindService(): ICarrierOverrideService = bindLock.withLock {
        service?.let { if (it.asBinder().pingBinder()) return@withLock it }
        service = null
        suspendCancellableCoroutine { continuation ->
            serviceWaiter = continuation
            continuation.invokeOnCancellation { serviceWaiter = null }
            try {
                Shizuku.bindUserService(userServiceArgs, connection)
            } catch (throwable: Throwable) {
                serviceWaiter = null
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        OverrideException(
                            "UserService failed to start: ${throwable.javaClass.simpleName}: " +
                                throwable.message.orEmpty()
                        )
                    )
                }
            }
        }
    }

    private companion object {
        const val PERMISSION_REQUEST_CODE = 41
    }
}
