package com.riteldevelopment.carriertestoverride;

interface ICarrierOverrideService {
    void destroy() = 16777114;
    String inspectRuntime() = 1;
    String applyRegionOverride(int subId, String mccMnc, String imsi, String carrierName,
            String countryIso, boolean overrideSimIdentity, boolean overrideAppCountry,
            boolean overrideCarrierName) = 2;
    String restoreTransient(int subId, String originalMccMnc, String originalSpn,
            String originalCountryIso,
            boolean restoreSimIdentity, boolean clearAppCountry) = 3;
    String clearAllCarrierConfigOverrides(int subId) = 4;
    /**
     * Force-stops the given packages, optionally wiping storage first and relaunching afterwards.
     * wipeMode is one of TargetApps.WIPE_NONE / WIPE_CACHE / WIPE_DATA.
     */
    String refreshTargetApps(in String[] packages, int wipeMode, boolean relaunch) = 5;
}
