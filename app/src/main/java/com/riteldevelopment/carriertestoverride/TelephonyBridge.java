package com.riteldevelopment.carriertestoverride;

import android.os.IBinder;
import android.os.Process;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class TelephonyBridge {
    private static final String INTERFACE_NAME = "com.android.internal.telephony.ITelephony";
    private static final String SUB_INTERFACE_NAME = "com.android.internal.telephony.ISub";
    private static final String METHOD_NAME = "setCarrierTestOverride";

    /** How long the UICC applications stay off before being switched back on. */
    private static final long UICC_CYCLE_SETTLE_MILLIS = 3000;

    /** How long the IMS stack is left down before being re-enabled. */
    private static final long IMS_RESTART_SETTLE_MILLIS = 1500;

    private TelephonyBridge() {
    }

    static String inspectRuntime() throws Exception {
        Object telephony = getTelephonyProxy();
        Class<?> iface = Class.forName(INTERFACE_NAME);
        List<String> matches = new ArrayList<>();
        for (Method method : iface.getMethods()) {
            if (METHOD_NAME.equals(method.getName()) || "refreshUiccProfile".equals(method.getName())) {
                matches.add(signature(method));
            }
        }
        matches.sort(Comparator.naturalOrder());
        return "uid=" + Process.myUid()
                + "\nproxy=" + telephony.getClass().getName()
                + "\nmethods=" + (matches.isEmpty() ? "<none>" : String.join(" | ", matches));
    }

    static String applySimOverride(int subId, String mccMnc, String imsi, String carrierName)
            throws Exception {
        requireValidSubId(subId);
        String numeric = requireMccMnc(mccMnc);
        String testImsi = requireImsi(imsi);
        String name = requireName(carrierName);
        Method method = findCarrierOverrideMethod();
        Object telephony = getTelephonyProxy();
        invoke(method, telephony, buildCarrierOverrideArguments(
                method, subId, numeric, testImsi, name, name));
        return "SIM identity: called " + signature(method)
                + "\nsubId=" + subId + ", MCC/MNC=" + numeric + ", SPN/PNN=" + name
                + "\nIMSI=" + testImsi + "; ICCID/GID/APN/privilege rules left untouched";
    }

    static String restoreOriginal(int subId, String originalMccMnc, String originalSpn)
            throws Exception {
        requireValidSubId(subId);
        String numeric = normalizeMccMnc(originalMccMnc);
        String name = normalizeName(originalSpn);

        Method method = findCarrierOverrideMethod();
        Object telephony = getTelephonyProxy();
        invoke(method, telephony, buildCarrierOverrideArguments(
                method, subId, numeric, null, null, name));
        return "SIM identity: restored the visible operator info to the saved values"
                + "\nsubId=" + subId + ", MCC/MNC=" + valueOrNull(numeric)
                + ", SPN=" + valueOrNull(name)
                + "\nPNN, IMSI, ICCID, GID, APN and privilege rules fall back to the real SIM records"
                + "\nNote: AOSP has no separate clear API, so a reboot is the definitive restore.";
    }

    /**
     * Lists methods of a hidden telephony interface whose name matches {@code pattern}, for the
     * diagnostic entry point. Hidden interfaces differ by vendor and version, so finding the available
     * recovery call on a given build is a matter of looking rather than assuming.
     *
     * @param scope {@code sub} for {@code ISub}, anything else for {@code ITelephony}
     */
    static String listMethods(String scope, String pattern) throws Exception {
        Class<?> iface = Class.forName("sub".equals(scope) ? SUB_INTERFACE_NAME : INTERFACE_NAME);
        List<String> matches = new ArrayList<>();
        for (Method method : iface.getMethods()) {
            if (method.getName().toLowerCase().contains(pattern.toLowerCase())) {
                matches.add(signature(method));
            }
        }
        matches.sort(Comparator.naturalOrder());
        return matches.isEmpty() ? "<no match for " + pattern + ">" : String.join("\n", matches);
    }

    /**
     * Sets the override with no validation, for the diagnostic entry point only.
     *
     * <p>{@link #applySimOverride} refuses a null IMSI on purpose; this exists so {@link RuntimeProbe}
     * can measure what each individual field does to the live IMS registration, which is how the
     * fake IMSI was ruled out as the cause of the voice/SMS regression.</p>
     */
    static String overrideRaw(int subId, String mccMnc, String imsi, String name) throws Exception {
        requireValidSubId(subId);
        Method method = findCarrierOverrideMethod();
        Object telephony = getTelephonyProxy();
        invoke(method, telephony, buildCarrierOverrideArguments(
                method, subId, mccMnc, imsi, name, name));
        return "raw override: subId=" + subId + ", MCC/MNC=" + valueOrNull(mccMnc)
                + ", IMSI=" + valueOrNull(imsi) + ", SPN/PNN=" + valueOrNull(name);
    }

    /**
     * Asks the framework to re-evaluate the UICC profile for one subscription.
     *
     * <p>In AOSP this only re-runs carrier privilege evaluation
     * ({@code UiccProfile.refresh()} posts {@code EVENT_CARRIER_PRIVILEGES_LOADED}); it does not
     * re-read SIM records and does not re-register IMS. It is called after an identity change because
     * it is the one documented companion to {@code setCarrierTestOverride}, not because it is
     * sufficient on its own: measured on SM-S938B it does not bring IMS back. Kept only as a probe.</p>
     */
    static String refreshUiccProfile(int subId) throws Exception {
        requireValidSubId(subId);
        Class<?> iface = Class.forName(INTERFACE_NAME);
        Method method;
        try {
            method = iface.getMethod("refreshUiccProfile", int.class);
        } catch (NoSuchMethodException missing) {
            return "refreshUiccProfile: unavailable on this build";
        }
        invoke(method, getTelephonyProxy(), new Object[]{subId});
        return "refreshUiccProfile: ok for subId=" + subId;
    }

    /**
     * Turns the UICC applications for one subscription off and on.
     *
     * <p>This is what the SIM on/off switch in Settings does, and on this hardware it is the only
     * thing observed to bring IMS back after a region override; {@code refreshUiccProfile} and
     * {@code disableIms}/{@code enableIms} both leave the stack deregistered.</p>
     */
    static String cycleUiccApplications(int subId) throws Exception {
        requireValidSubId(subId);
        Class<?> iface = Class.forName(SUB_INTERFACE_NAME);
        Method setter = null;
        for (Method candidate : iface.getMethods()) {
            if ("setUiccApplicationsEnabled".equals(candidate.getName())) {
                setter = candidate;
                break;
            }
        }
        if (setter == null) {
            return "setUiccApplicationsEnabled: unavailable on this build";
        }
        Object sub = getProxy("isub", SUB_INTERFACE_NAME);
        invoke(setter, sub, buildToggleArgs(setter, subId, false));
        try {
            Thread.sleep(UICC_CYCLE_SETTLE_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        invoke(setter, sub, buildToggleArgs(setter, subId, true));
        return "UICC applications cycled for subId=" + subId + " via " + signature(setter);
    }

    /** The two known argument orders for setUiccApplicationsEnabled differ by vendor and version. */
    private static Object[] buildToggleArgs(Method setter, int subId, boolean enabled) {
        Class<?>[] params = setter.getParameterTypes();
        if (params.length == 2 && params[0] == boolean.class) {
            return new Object[]{enabled, subId};
        }
        return new Object[]{subId, enabled};
    }

    /** Passed as the calling package for ISub reads; this code runs as uid 2000, which is that package. */
    private static final String SHELL_PACKAGE = "com.android.shell";

    /** No display name was captured, so there is nothing to restore. Mirrors the AIDL constant. */
    static final int DISPLAY_NAME_SOURCE_NONE = -1;

    /**
     * Reads a subscription's display name and the source that set it.
     *
     * <p>This is the half of "restore" that used to be missing. The app country layer's name override
     * does not only live in the transient CarrierConfig — the framework copies it into the subscription
     * database as {@code displayName} with {@code displayNameSource=CARRIER}, which is why a foreign
     * operator name survived both restore and reboot. Putting it back means knowing what it was, and
     * the only honest moment to learn that is before the first override.</p>
     *
     * <p>Read here rather than in the app process because it needs {@code READ_PHONE_STATE}, which the
     * app does not hold and this shell-uid service does.</p>
     *
     * @return {@code {name, source}}, or null when the name is unreadable on this build — in which case
     *         the caller stores nothing and restore later skips the name entirely.
     */
    static String[] readDisplayName(int subId) throws Exception {
        requireValidSubId(subId);
        Class<?> iface = Class.forName(SUB_INTERFACE_NAME);
        Method getter = null;
        for (Method candidate : iface.getMethods()) {
            if (!"getActiveSubscriptionInfo".equals(candidate.getName())) {
                continue;
            }
            Class<?>[] params = candidate.getParameterTypes();
            if (params.length >= 1 && params[0] == int.class) {
                getter = candidate;
                break;
            }
        }
        if (getter == null) {
            return null;
        }
        // The trailing parameters are the calling package and feature id, and their count varies by
        // version — filling every String slot with the shell package works for both known shapes.
        Class<?>[] params = getter.getParameterTypes();
        Object[] args = new Object[params.length];
        args[0] = subId;
        for (int i = 1; i < args.length; i++) {
            args[i] = params[i] == String.class ? SHELL_PACKAGE : null;
        }

        Object info = invokeForResult(getter, getProxy("isub", SUB_INTERFACE_NAME), args);
        if (info == null) {
            return null;
        }
        Object name = invokeForResult(info.getClass().getMethod("getDisplayName"), info, new Object[0]);
        if (name == null) {
            return null;
        }
        int source = DISPLAY_NAME_SOURCE_NONE;
        try {
            Object reported = invokeForResult(
                    info.getClass().getMethod("getDisplayNameSource"), info, new Object[0]);
            if (reported instanceof Integer) {
                source = (Integer) reported;
            }
        } catch (Throwable unavailable) {
            // getDisplayNameSource is not public API. Without it the name is still worth keeping, but
            // restore will decline to write it rather than guess a priority — see restoreDisplayName.
        }
        return new String[]{name.toString(), Integer.toString(source)};
    }

    /**
     * Writes a captured display name back onto the subscription record.
     *
     * <p>Deliberately refuses to run without the original source. The platform ranks name sources and
     * ignores a write that would downgrade one, so an invented source either silently does nothing or
     * pins the name at the wrong priority. Leaving a stale name is a cosmetic defect; writing a wrong
     * one at the wrong priority is a worse and stickier one.</p>
     *
     * <p>Never fatal — this runs inside a restore that has already succeeded.</p>
     */
    static String restoreDisplayName(int subId, String name, int source) throws Exception {
        requireValidSubId(subId);
        String normalized = normalizeName(name);
        if (normalized == null || source == DISPLAY_NAME_SOURCE_NONE) {
            return "Display name: nothing was captured for this SIM, so it was left alone";
        }
        Class<?> iface = Class.forName(SUB_INTERFACE_NAME);
        Method setter;
        try {
            setter = iface.getMethod("setDisplayNameUsingSrc", String.class, int.class, int.class);
        } catch (NoSuchMethodException missing) {
            return "Display name: setDisplayNameUsingSrc is unavailable on this build";
        }
        invoke(setter, getProxy("isub", SUB_INTERFACE_NAME),
                new Object[]{normalized, subId, source});
        return "Display name: restored to " + normalized + " (source=" + source + ")";
    }

    /** Whether IMS is registered for this subscription. False here is what "cannot call" looks like. */
    static boolean isImsRegistered(int subId) throws Exception {
        Class<?> iface = Class.forName(INTERFACE_NAME);
        Method method = iface.getMethod("isImsRegistered", int.class);
        return (Boolean) method.invoke(getTelephonyProxy(), subId);
    }

    /**
     * Tears the IMS stack down and brings it back for one slot.
     *
     * <p>Written as a candidate fix for the post-restore deregistration, on the theory that Samsung's
     * IMS service picks a per-slot MNO profile ({@code CU_CN}, {@code Vodafone_GB}, …) from the carrier
     * identity and does not re-pick it when the identity is put back. Measured on SM-S938B the theory
     * does not hold up: the stack comes back still deregistered. {@link #cycleUiccApplications} is what
     * actually recovers it. Kept only as a probe, so the negative result stays reproducible.</p>
     *
     * <p>Takes a slot index, not a subId: {@code disableIms}/{@code enableIms} are addressed per modem.</p>
     */
    static String restartIms(int slotIndex) throws Exception {
        if (slotIndex < 0) {
            throw new IllegalArgumentException("Invalid slot index: " + slotIndex);
        }
        Class<?> iface = Class.forName(INTERFACE_NAME);
        Method disable = iface.getMethod("disableIms", int.class);
        Method enable = iface.getMethod("enableIms", int.class);
        Object telephony = getTelephonyProxy();

        invoke(disable, telephony, new Object[]{slotIndex});
        // The stack needs a moment to actually tear down; re-enabling too eagerly is a no-op because
        // the disable has not been processed yet, which shows up as "the fix did nothing".
        try {
            Thread.sleep(IMS_RESTART_SETTLE_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        invoke(enable, telephony, new Object[]{slotIndex});
        return "IMS restarted for slot " + slotIndex;
    }

    private static Object getTelephonyProxy() throws Exception {
        return getProxy("phone", INTERFACE_NAME);
    }

    /** Resolves a system Binder service and wraps it in its hidden {@code $Stub} interface proxy. */
    private static Object getProxy(String serviceName, String ifaceName) throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Method getService = serviceManager.getMethod("getService", String.class);
        IBinder binder = (IBinder) getService.invoke(null, serviceName);
        if (binder == null) {
            throw new IllegalStateException(serviceName + " Binder service is unavailable");
        }

        Class<?> stub = Class.forName(ifaceName + "$Stub");
        Method asInterface = stub.getMethod("asInterface", IBinder.class);
        Object proxy = asInterface.invoke(null, binder);
        if (proxy == null) {
            throw new IllegalStateException(ifaceName + ".Stub.asInterface returned null");
        }
        return proxy;
    }

    private static Method findCarrierOverrideMethod() throws Exception {
        Class<?> iface = Class.forName(INTERFACE_NAME);
        List<String> found = new ArrayList<>();
        for (Method method : iface.getMethods()) {
            if (!METHOD_NAME.equals(method.getName())) {
                continue;
            }
            found.add(signature(method));
            Class<?>[] params = method.getParameterTypes();
            if ((params.length != 8 && params.length != 10) || params[0] != int.class) {
                continue;
            }
            boolean stringsOnly = true;
            for (int i = 1; i < params.length; i++) {
                if (params[i] != String.class) {
                    stringsOnly = false;
                    break;
                }
            }
            if (stringsOnly && Modifier.isPublic(method.getModifiers())) {
                return method;
            }
        }
        throw new NoSuchMethodException(
                "Expected setCarrierTestOverride(int + 7/9 Strings); runtime candidates=" + found);
    }

    /**
     * PNN and SPN are separate parameters because restore must treat them differently: only the SPN was
     * ever captured, so writing that same string into the PNN slot would leave a fake network name in
     * place instead of letting the SIM's own EF_PNN record answer again.
     */
    private static Object[] buildCarrierOverrideArguments(Method method, int subId,
            String mccMnc, String imsi, String pnn, String spn) {
        Object[] args = new Object[method.getParameterCount()];
        args[0] = subId;
        args[1] = mccMnc;
        args[2] = imsi;
        args[3] = null; // ICCID
        args[4] = null; // GID1
        args[5] = null; // GID2
        args[6] = pnn;
        args[7] = spn;
        if (args.length == 10) {
            args[8] = null; // carrier privilege rules
            args[9] = null; // APN
        }
        return args;
    }

    private static void invoke(Method method, Object target, Object[] args) throws Exception {
        invokeForResult(method, target, args);
    }

    /** Same call and same unwrapping as {@link #invoke}, for the reads whose return value is the point. */
    private static Object invokeForResult(Method method, Object target, Object[] args)
            throws Exception {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    private static void requireValidSubId(int subId) {
        if (subId < 0) {
            throw new IllegalArgumentException("Invalid subId: " + subId);
        }
    }

    private static String normalizeMccMnc(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.matches("[0-9]{5,6}") ? trimmed : null;
    }

    private static String normalizeName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80);
    }

    private static String requireMccMnc(String value) {
        String normalized = normalizeMccMnc(value);
        if (normalized == null) {
            throw new IllegalArgumentException("MCC/MNC must be 5 or 6 digits");
        }
        return normalized;
    }

    private static String requireImsi(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[0-9]{14,15}")) {
            throw new IllegalArgumentException("Test IMSI must be 14 or 15 digits");
        }
        return normalized;
    }

    private static String requireName(String value) {
        String normalized = normalizeName(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Carrier name must not be empty");
        }
        return normalized;
    }

    private static String valueOrNull(String value) {
        return value == null ? "<null>" : value;
    }

    private static String signature(Method method) {
        StringBuilder builder = new StringBuilder(method.getName()).append('(');
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(params[i].getSimpleName());
        }
        return builder.append(')').toString();
    }
}
