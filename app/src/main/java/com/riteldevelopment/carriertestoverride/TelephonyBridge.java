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
    private static final String METHOD_NAME = "setCarrierTestOverride";

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
                method, subId, numeric, testImsi, name));
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
                method, subId, numeric, null, name));
        return "SIM identity: restored the visible operator info to the saved values"
                + "\nsubId=" + subId + ", MCC/MNC=" + valueOrNull(numeric)
                + ", SPN/PNN=" + valueOrNull(name)
                + "\nIMSI/ICCID/GID/APN/privilege rules fall back to the real SIM records"
                + "\nNote: AOSP has no separate clear API, so a reboot is the definitive restore.";
    }

    private static Object getTelephonyProxy() throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Method getService = serviceManager.getMethod("getService", String.class);
        IBinder binder = (IBinder) getService.invoke(null, "phone");
        if (binder == null) {
            throw new IllegalStateException("phone Binder service is unavailable");
        }

        Class<?> stub = Class.forName(INTERFACE_NAME + "$Stub");
        Method asInterface = stub.getMethod("asInterface", IBinder.class);
        Object telephony = asInterface.invoke(null, binder);
        if (telephony == null) {
            throw new IllegalStateException("ITelephony.Stub.asInterface returned null");
        }
        return telephony;
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

    private static Object[] buildCarrierOverrideArguments(Method method, int subId,
            String mccMnc, String imsi, String name) {
        Object[] args = new Object[method.getParameterCount()];
        args[0] = subId;
        args[1] = mccMnc;
        args[2] = imsi;
        args[3] = null; // ICCID
        args[4] = null; // GID1
        args[5] = null; // GID2
        args[6] = name; // PNN
        args[7] = name; // SPN
        if (args.length == 10) {
            args[8] = null; // carrier privilege rules
            args[9] = null; // APN
        }
        return args;
    }

    private static void invoke(Method method, Object target, Object[] args) throws Exception {
        try {
            method.invoke(target, args);
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
