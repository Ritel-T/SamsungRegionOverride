package com.riteldevelopment.carriertestoverride;

/**
 * Shell entry point for inspecting and exercising the privileged layers without the UI.
 *
 * <p>Run under {@code app_process} as the shell user, which has the same {@code MODIFY_PHONE_STATE}
 * the Shizuku UserService runs with:</p>
 *
 * <pre>
 * adb shell CLASSPATH=&lt;path-to-base.apk&gt; app_process / \
 *     com.riteldevelopment.carriertestoverride.RuntimeProbe sim-probe
 * </pre>
 *
 * <p>The {@code sim-*} commands take one field at a time so the effect of each on the live IMS
 * registration can be measured separately. That is how the SIM identity layer — the fake IMSI first
 * among the suspects — was ruled out as the cause of the voice/SMS regression, leaving the app country
 * layer. {@code -} means "pass null for this field".</p>
 */
public final class RuntimeProbe {
    private RuntimeProbe() {
    }

    public static void main(String[] args) {
        try {
            if (args.length > 0) {
                runCommand(args);
                return;
            }
            System.out.println(TelephonyBridge.inspectRuntime());
            System.out.println(CarrierConfigBridge.inspectRuntime());
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void runCommand(String[] args) throws Exception {
        String command = args[0];
        if ("sim-probe".equals(command) && args.length == 1) {
            // Telephony only. The CarrierConfig probe needs the instrumentation host, which does not
            // exist in an app_process shell, so the default no-arg path cannot be used here.
            System.out.println(TelephonyBridge.inspectRuntime());
            return;
        }
        if ("methods".equals(command) && args.length == 2) {
            System.out.println(TelephonyBridge.listMethods("telephony", args[1]));
            return;
        }
        if ("sim-set".equals(command) && args.length == 5) {
            System.out.println(TelephonyBridge.overrideRaw(Integer.parseInt(args[1]),
                    nullable(args[2]), nullable(args[3]), nullable(args[4])));
            return;
        }
        if ("sim-restore".equals(command) && args.length == 4) {
            System.out.println(TelephonyBridge.restoreOriginal(
                    Integer.parseInt(args[1]), nullable(args[2]), nullable(args[3])));
            return;
        }
        if ("sub-methods".equals(command) && args.length == 2) {
            System.out.println(TelephonyBridge.listMethods("sub", args[1]));
            return;
        }
        if ("uicc-cycle".equals(command) && args.length == 2) {
            System.out.println(TelephonyBridge.cycleUiccApplications(Integer.parseInt(args[1])));
            return;
        }
        if ("ims-state".equals(command) && args.length == 2) {
            System.out.println("isImsRegistered(" + args[1] + ")="
                    + TelephonyBridge.isImsRegistered(Integer.parseInt(args[1])));
            return;
        }
        if ("ims-restart".equals(command) && args.length == 2) {
            System.out.println(TelephonyBridge.restartIms(Integer.parseInt(args[1])));
            return;
        }
        if ("display-read".equals(command) && args.length == 2) {
            String[] captured = TelephonyBridge.readDisplayName(Integer.parseInt(args[1]));
            System.out.println(captured == null
                    ? "displayName: unreadable on this build"
                    : "displayName=" + captured[0] + " source=" + captured[1]);
            return;
        }
        // The repair hatch for a SIM whose name was already overridden before this tool learned to
        // capture it. Nothing in the UI can fix that case: the capture is write-once and would record
        // the overridden name as the original, so the real one has to come from the user.
        if ("display-set".equals(command) && args.length == 4) {
            System.out.println(TelephonyBridge.restoreDisplayName(
                    Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3])));
            return;
        }
        if ("uicc-refresh".equals(command) && args.length == 2) {
            System.out.println(TelephonyBridge.refreshUiccProfile(Integer.parseInt(args[1])));
            return;
        }
        if ("country-apply".equals(command) && args.length == 4) {
            System.out.println(CarrierConfigBridge.applyTransient(
                    Integer.parseInt(args[1]), args[2], args[3], true));
            return;
        }
        if ("country-clear".equals(command) && (args.length == 2 || args.length == 3)) {
            System.out.println(CarrierConfigBridge.clearTransient(
                    Integer.parseInt(args[1]), args.length == 3 ? args[2] : null));
            return;
        }
        // Every branch above is listed here. A command that works but is undocumented is a command
        // nobody finds, which is what happened to sub-methods and uicc-cycle.
        throw new IllegalArgumentException("Usage: RuntimeProbe ["
                + "sim-probe"
                + " | methods NAME_SUBSTRING | sub-methods NAME_SUBSTRING"
                + " | sim-set SUB_ID MCCMNC|- IMSI|- NAME|-"
                + " | sim-restore SUB_ID MCCMNC|- NAME|-"
                + " | ims-state SUB_ID | ims-restart SLOT_INDEX"
                + " | uicc-cycle SUB_ID | uicc-refresh SUB_ID"
                + " | display-read SUB_ID | display-set SUB_ID NAME SOURCE"
                + " | country-apply SUB_ID ISO NAME"
                + " | country-clear SUB_ID [RESTORE_ISO]]");
    }

    /** {@code -} is the shell's way of saying null, which is a meaningful value for every field here. */
    private static String nullable(String value) {
        return "-".equals(value) ? null : value;
    }
}
