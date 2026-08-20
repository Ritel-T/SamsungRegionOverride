package com.riteldevelopment.carriertestoverride.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.riteldevelopment.carriertestoverride.MainActivity
import com.riteldevelopment.carriertestoverride.R

/**
 * The standing reminder that a SIM is currently lying about where it is.
 *
 * An override here is transient and invisible: nothing in the system UI says the phone is reporting a
 * foreign network, and the one consequence that matters — calls and SMS stopping — looks exactly like
 * poor coverage. Someone who applies a region, closes the app and picks the phone up an hour later has
 * no way to tell the two apart. So while any layer is live there is an ongoing notification saying so,
 * and it carries the undo, because the undo is what the user will be reaching for.
 *
 * On Android 16 it asks to be a Live Update, which is what puts the country code in the status bar
 * chip. That request is not a guarantee: the platform decides, the user can turn promotion off per app,
 * and older releases have no such concept — so the notification is written to be complete and useful as
 * an ordinary ongoing notification, with promotion as an addition rather than the mechanism.
 *
 * One notification per subscription, keyed by subId, because two SIMs can be disguised independently
 * and a merged one could not carry a Restore button that means anything.
 */
class OverrideNotifier(context: Context) {

    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)

    /**
     * What was last posted for each subId.
     *
     * The caller re-reads the SIMs seven times over about five seconds after every operation, so
     * without this every apply would post the same notification seven times over. Re-posting is not
     * merely wasteful: it re-triggers the heads-up, so the user would get the same banner repeatedly
     * for one action.
     */
    private val posted = mutableMapOf<Int, String>()

    /** The channel has been registered in this process; see [ensureChannel]. */
    private var channelReady = false

    /**
     * Brings the notifications in line with what the SIMs actually report.
     *
     * Takes the whole scan rather than one subscription, so a SIM that stopped being disguised — or was
     * removed from the phone between scans — has its notification withdrawn instead of being left up
     * describing an override that is no longer there.
     */
    fun sync(sims: List<SimInfo>) {
        val live = sims.filter { it.disguised }
        val liveIds = live.mapTo(HashSet()) { it.subId }

        // Read back from the system rather than trusting `posted`: this object is created per view
        // model, so a notification left up by a previous instance of the screen is not in the local map
        // and would otherwise sit on the shade forever after the last SIM was restored.
        val active = activeIds()
        active.filterNot { it in liveIds }.forEach { subId ->
            manager.cancel(NOTIFICATION_TAG, subId)
            posted.remove(subId)
        }
        // A notice that is live but no longer on the shade has to be posted again, not deduplicated
        // against what this object last sent. Forgetting it here is what lets that happen.
        posted.keys.retainAll(active.toSet())

        if (live.isEmpty()) return
        if (!canPost()) {
            // The one failure here that is otherwise completely invisible: everything succeeds, no
            // exception is thrown, and the user simply never learns their phone is disguised. Worth a
            // line in the log, and the screen asks for the permission when this is why.
            Log.w(
                TAG,
                "Not posting for ${live.size} disguised SIM(s): " +
                    "enabled=${manager.areNotificationsEnabled()}, granted=${permissionGranted()}",
            )
            return
        }
        ensureChannel()
        live.forEach { sim ->
            val fingerprint = fingerprint(sim)
            if (posted[sim.subId] == fingerprint) return@forEach
            post(sim)
            posted[sim.subId] = fingerprint
        }
    }

    /** Whether the app may post at all — false until the user grants notifications on API 33 and up. */
    fun canPost(): Boolean = manager.areNotificationsEnabled() && permissionGranted()

    private fun permissionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Everything that would change the text. Compared rather than the [SimInfo] itself, which carries
     * fields — SIM state, for one — that move without changing a word of what is displayed.
     */
    private fun fingerprint(sim: SimInfo): String = listOf(
        sim.slotIndex,
        sim.operatorNumeric,
        sim.operatorName,
        sim.countryIso,
        sim.realOperatorNumeric,
        sim.realOperatorName,
        sim.realCountryIso,
        sim.simLayerLive,
        sim.countryLayerLive,
    ).joinToString("|")

    /**
     * `canPost` is checked by every caller of this method, immediately before the call, which is the
     * guarantee lint cannot see. Posting without the permission is in any case a silent no-op rather
     * than a throw, so the check is about not pretending the notification is up.
     */
    @SuppressLint("MissingPermission")
    private fun post(sim: SimInfo) {
        val disguise = describeRegion(sim.countryIso, sim.operatorName).ifEmpty { UNKNOWN_REGION }
        val real = describeRegion(sim.realCountryIso, sim.realOperatorName).ifEmpty { UNKNOWN_REGION }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Pretending to be $disguise")
            .setContentText("${sim.displayName} is really $real")
            .setSubText(layerSummary(sim))
            // The status bar chip, which is all most people will ever see of this. Two letters and a
            // flag is the whole message: your phone is currently claiming to be in this country.
            .setShortCriticalText(chipText(sim))
            .setStyle(transformBar())
            .setRequestPromotedOngoing(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setColor(DISGUISE_COLOR)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(activityIntent(sim.subId, restore = false))
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_notification,
                    "Restore",
                    activityIntent(sim.subId, restore = true),
                ).build()
            )
            .build()

        runCatching { manager.notify(NOTIFICATION_TAG, sim.subId, notification) }
            .onFailure { Log.w(TAG, "notify failed for sub ${sim.subId}", it) }
    }

    /**
     * The same left-to-right transform the screen draws, in the one widget a notification has for it.
     *
     * `ProgressStyle` is what makes an ongoing notification eligible for promotion on Android 16, so
     * something had to fill it; a two-segment bar sitting at its far end happens to say exactly the
     * right thing — you started at the identity on the left and you are now all the way over at the one
     * on the right. `setStyledByProgress(false)` keeps each segment its own colour rather than having
     * the platform grey out whatever the tracker has not reached yet, which would defeat the point.
     */
    private fun transformBar(): NotificationCompat.ProgressStyle = NotificationCompat.ProgressStyle()
        .addProgressSegment(NotificationCompat.ProgressStyle.Segment(HALF).setColor(REAL_COLOR))
        .addProgressSegment(NotificationCompat.ProgressStyle.Segment(HALF).setColor(DISGUISE_COLOR))
        .setStyledByProgress(false)
        .setProgress(HALF * 2)

    /** Which switches are in force, so the reader knows whether calls are at risk without opening the app. */
    private fun layerSummary(sim: SimInfo): String = when {
        sim.countryLayerLive && sim.simLayerLive -> "Country + Network"
        sim.countryLayerLive -> "Country"
        else -> "Network"
    }

    /**
     * The promoted chip has room for a handful of characters. The country the phone is claiming is the
     * most useful thing to spend them on — except when only the Network layer is live, where the country
     * is still the real one and saying it would be actively misleading.
     */
    private fun chipText(sim: SimInfo): String {
        if (!sim.countryLayerLive) return sim.operatorNumeric.ifBlank { "SIM" }
        val iso = sim.countryIso.uppercase()
        val flag = flagEmoji(sim.countryIso)
        return if (flag.isEmpty()) iso.ifBlank { "SIM" } else "$flag $iso"
    }

    /**
     * Restore runs in the app rather than headlessly from a receiver.
     *
     * It needs Shizuku, and Shizuku's binder and its permission prompt live in the `:ui` process behind
     * an activity; a background receiver would have to stand up that whole path with nowhere to show a
     * prompt and nowhere to report a failure. It is still one tap — the screen opens with the restore
     * already running — and the user ends up looking at the result, which for an operation that can fail
     * is where they should be looking anyway.
     */
    private fun activityIntent(subId: Int, restore: Boolean): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java)
            .setAction(if (restore) MainActivity.ACTION_RESTORE else Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_SUB_ID, subId)
        // Distinct request codes, or the two intents for one SIM would be treated as the same pending
        // intent and the second would silently reuse the first one's extras and action.
        val requestCode = subId * 2 + if (restore) 1 else 0
        return PendingIntent.getActivity(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Subscription ids this notifier currently has a notice up for. Empty if the system will not say.
     *
     * Filtered by tag rather than taking every notification this app has posted. The ids here are
     * subscription ids chosen by the platform, so nothing stops one colliding with an id some later
     * feature picks for a notification of its own — and the caller withdraws everything it finds here
     * that is no longer disguised.
     */
    private fun activeIds(): List<Int> = runCatching {
        appContext.getSystemService(NotificationManager::class.java)
            ?.activeNotifications
            ?.filter { it.tag == NOTIFICATION_TAG }
            ?.map { it.id }
            .orEmpty()
    }.getOrDefault(emptyList())

    /**
     * Default importance, muted.
     *
     * It has to be at least default for the platform to consider promoting it, and a chip that appears
     * without the user noticing would defeat the purpose — but this fires on an action the user just
     * took, in an app they are looking at, so a sound would be startling and tell them nothing.
     *
     * Called only on the way to a post, so a phone that has never had an override does not carry a
     * settings entry for a notification it has never seen. Once per process from there: the caller runs
     * seven scans after every operation, and re-registering an identical channel each time is six
     * pointless binder round trips.
     */
    private fun ensureChannel() {
        if (channelReady) return
        channelReady = true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active region override",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Shown while a SIM is reporting an overridden region."
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        runCatching {
            appContext.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private companion object {
        const val TAG = "OverrideNotifier"

        /** Both halves missing means the platform reported nothing, not that the region is blank. */
        const val UNKNOWN_REGION = "an unknown region"

        const val CHANNEL_ID = "override_live"

        /** Marks the notifications this class owns, so it withdraws only its own. */
        const val NOTIFICATION_TAG = "override_live"

        /** Halves, so the two segments meet in the middle and the bar's maximum is their sum. */
        const val HALF = 50

        /**
         * Fixed, unlike the in-app palette. A notification is drawn on the system's shade in whichever
         * theme that is set to, so these have to hold up on both; the app's own light-mode amber and
         * green are too dark to read on a dark shade.
         */
        const val DISGUISE_COLOR = 0xFFE8B057.toInt()
        const val REAL_COLOR = 0xFF8A9497.toInt()
    }
}

