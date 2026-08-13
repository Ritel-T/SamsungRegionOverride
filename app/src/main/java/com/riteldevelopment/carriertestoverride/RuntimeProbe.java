package com.riteldevelopment.carriertestoverride;

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
        if ("country-apply".equals(args[0]) && args.length == 4) {
            System.out.println(CarrierConfigBridge.applyTransient(
                    Integer.parseInt(args[1]), args[2], args[3], true));
            return;
        }
        if ("country-clear".equals(args[0]) && (args.length == 2 || args.length == 3)) {
            System.out.println(CarrierConfigBridge.clearTransient(
                    Integer.parseInt(args[1]), args.length == 3 ? args[2] : null));
            return;
        }
        throw new IllegalArgumentException(
                "Usage: RuntimeProbe [country-apply SUB_ID ISO NAME"
                        + " | country-clear SUB_ID [RESTORE_ISO]]");
    }
}
