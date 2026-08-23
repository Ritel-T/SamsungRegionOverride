package com.riteldevelopment.carriertestoverride;

interface ICarrierOverrideService {
    void destroy() = 16777114;
    String inspectRuntime() = 1;
    /**
     * Applies the selected layers, then force-stops refreshPackages so they re-read the region.
     * A null refreshPackages means "the built-in defaults"; an empty array means "stop nothing",
     * which is what a user who has deselected every target is asking for.
     */
    String applyRegionOverride(int subId, String mccMnc, String imsi, String carrierName,
            String countryIso, boolean overrideSimIdentity, boolean overrideAppCountry,
            boolean overrideCarrierName, in String[] refreshPackages) = 2;
    /**
     * Puts the layers back, restores the subscription display name if one was captured, recovers IMS,
     * then force-stops refreshPackages.
     *
     * originalDisplayNameSource is the SubscriptionInfo name source captured alongside the name.
     * Pass DISPLAY_NAME_SOURCE_NONE (-1) when nothing was captured, which skips the name restore
     * rather than guessing a source and writing over a name this tool never touched.
     */
    String restoreTransient(int subId, String originalMccMnc, String originalSpn,
            String originalCountryIso, String originalDisplayName, int originalDisplayNameSource,
            boolean networkWasLive, boolean restoreSimIdentity, boolean clearAppCountry,
            in String[] refreshPackages) = 3;
    String clearAllCarrierConfigOverrides(int subId) = 4;
    /**
     * Force-stops the given packages, optionally wiping storage first and relaunching afterwards.
     * wipeMode is one of TargetApps.WIPE_NONE / WIPE_CACHE / WIPE_DATA.
     */
    String refreshTargetApps(in String[] packages, int wipeMode, boolean relaunch) = 5;
    /**
     * Reads a subscription's display name and name source, for capture before the first override.
     * Returns {name, source} as strings, or null when this build does not expose them. Reading needs
     * READ_PHONE_STATE, which the app process does not hold and the shell UserService does — which is
     * why this is on the privileged surface at all.
     */
    String[] readDisplayName(int subId) = 6;
    /**
     * Returns a one-way fingerprint of the physical SIM/card behind subId, or null when this build
     * does not expose a stable card identifier. The raw ICCID never crosses this Binder surface.
     */
    String readSimFingerprint(int subId) = 7;
}
