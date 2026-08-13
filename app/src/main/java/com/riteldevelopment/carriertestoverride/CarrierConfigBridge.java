package com.riteldevelopment.carriertestoverride;

import android.content.ComponentName;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Starts the registered CarrierConfig instrumentation from the shell UserService. */
final class CarrierConfigBridge {
    private static final String ACTIVITY_INTERFACE = "android.app.IActivityManager";
    private static final String WATCHER_INTERFACE = "android.app.IInstrumentationWatcher";
    private static final String WATCHER_DESCRIPTOR = "android.app.IInstrumentationWatcher";
    private static final String INSTRUMENTATION_CLASS =
            "com.riteldevelopment.carriertestoverride.CarrierConfigInstrumentation";

    // android.app.ActivityManager hidden constants. Values are stable from Android 12 through 16.
    private static final int INSTR_FLAG_DISABLE_HIDDEN_API_CHECKS = 1 << 0;
    private static final int INSTR_FLAG_NO_RESTART = 1 << 3;
    private static final int USER_CURRENT = -2;
    private static final long TIMEOUT_SECONDS = 20;

    private CarrierConfigBridge() {
    }

    static String inspectRuntime() throws Exception {
        Bundle arguments = new Bundle();
        arguments.putString(CarrierConfigInstrumentation.ARG_ACTION,
                CarrierConfigInstrumentation.ACTION_PROBE);
        return runInstrumentation(arguments);
    }

    static String applyTransient(int subId, String countryIso, String carrierName,
            boolean overrideCarrierName) throws Exception {
        requireValidSubId(subId);
        Bundle arguments = new Bundle();
        arguments.putString(CarrierConfigInstrumentation.ARG_ACTION,
                CarrierConfigInstrumentation.ACTION_APPLY_TRANSIENT);
        arguments.putInt(CarrierConfigInstrumentation.ARG_SUB_ID, subId);
        arguments.putString(CarrierConfigInstrumentation.ARG_COUNTRY_ISO, countryIso);
        arguments.putString(CarrierConfigInstrumentation.ARG_CARRIER_NAME, carrierName);
        arguments.putBoolean(CarrierConfigInstrumentation.ARG_OVERRIDE_NAME,
                overrideCarrierName);
        return runInstrumentation(arguments);
    }

    static String clearTransient(int subId, String restoreCountryIso) throws Exception {
        requireValidSubId(subId);
        Bundle arguments = new Bundle();
        arguments.putString(CarrierConfigInstrumentation.ARG_ACTION,
                CarrierConfigInstrumentation.ACTION_CLEAR_TRANSIENT);
        arguments.putInt(CarrierConfigInstrumentation.ARG_SUB_ID, subId);
        if (restoreCountryIso != null) {
            arguments.putString(CarrierConfigInstrumentation.ARG_COUNTRY_ISO,
                    restoreCountryIso);
        }
        return runInstrumentation(arguments);
    }

    static String clearAll(int subId) throws Exception {
        return runForSubscription(CarrierConfigInstrumentation.ACTION_CLEAR_ALL, subId);
    }

    private static String runForSubscription(String action, int subId) throws Exception {
        requireValidSubId(subId);
        Bundle arguments = new Bundle();
        arguments.putString(CarrierConfigInstrumentation.ARG_ACTION, action);
        arguments.putInt(CarrierConfigInstrumentation.ARG_SUB_ID, subId);
        return runInstrumentation(arguments);
    }

    private static String runInstrumentation(Bundle arguments) throws Exception {
        Object activityManager = getActivityManagerProxy();
        Method start = findStartInstrumentationMethod();
        InstrumentationWatcher watcher = new InstrumentationWatcher();
        Object watcherProxy = getWatcherProxy(watcher);
        Object automationConnection = newUiAutomationConnection();
        ComponentName component = new ComponentName(
                BuildConfig.APPLICATION_ID, INSTRUMENTATION_CLASS);

        Object started = invoke(start, activityManager, new Object[]{
                component,
                null,
                INSTR_FLAG_DISABLE_HIDDEN_API_CHECKS | INSTR_FLAG_NO_RESTART,
                arguments,
                watcherProxy,
                automationConnection,
                USER_CURRENT,
                null
        });
        if (!(started instanceof Boolean) || !((Boolean) started)) {
            throw new IllegalStateException(
                    "ActivityManager rejected instrumentation " + component.flattenToShortString());
        }
        if (!watcher.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("CarrierConfig instrumentation timed out after "
                    + TIMEOUT_SECONDS + " seconds");
        }
        if (watcher.error != null) {
            throw new IllegalStateException(watcher.error);
        }
        String message = watcher.results == null
                ? null : watcher.results.getString("message");
        if (watcher.resultCode != android.app.Activity.RESULT_OK || message == null) {
            throw new IllegalStateException("CarrierConfig instrumentation returned code="
                    + watcher.resultCode + ", results=" + watcher.results);
        }
        return message;
    }

    private static Object getActivityManagerProxy() throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Method getService = serviceManager.getMethod("getService", String.class);
        IBinder binder = (IBinder) getService.invoke(null, "activity");
        if (binder == null) {
            throw new IllegalStateException("activity Binder service is unavailable");
        }
        Class<?> stub = Class.forName(ACTIVITY_INTERFACE + "$Stub");
        return stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
    }

    private static Method findStartInstrumentationMethod() throws Exception {
        Class<?> iface = Class.forName(ACTIVITY_INTERFACE);
        List<String> candidates = new ArrayList<>();
        for (Method method : iface.getMethods()) {
            if (!"startInstrumentation".equals(method.getName())) {
                continue;
            }
            candidates.add(signature(method));
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 8
                    && types[0] == ComponentName.class
                    && types[1] == String.class
                    && types[2] == int.class
                    && types[3] == Bundle.class
                    && types[6] == int.class
                    && types[7] == String.class) {
                return method;
            }
        }
        candidates.sort(Comparator.naturalOrder());
        throw new NoSuchMethodException("Expected startInstrumentation(8 params); candidates="
                + candidates);
    }

    private static Object newUiAutomationConnection() throws Exception {
        Class<?> type = Class.forName("android.app.UiAutomationConnection");
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Object getWatcherProxy(InstrumentationWatcher watcher) throws Exception {
        Class<?> stub = Class.forName(WATCHER_INTERFACE + "$Stub");
        Method asInterface = stub.getMethod("asInterface", IBinder.class);
        return asInterface.invoke(null, watcher);
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Exception {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
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

    private static String signature(Method method) {
        StringBuilder result = new StringBuilder(method.getName()).append('(');
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                result.append(',');
            }
            result.append(types[i].getSimpleName());
        }
        return result.append(')').toString();
    }

    private static final class InstrumentationWatcher extends Binder {
        private final CountDownLatch finished = new CountDownLatch(1);
        private final int statusTransaction;
        private final int finishedTransaction;

        volatile int resultCode = Integer.MIN_VALUE;
        volatile Bundle results;
        volatile String error;

        InstrumentationWatcher() throws Exception {
            Class<?> stub = Class.forName(WATCHER_INTERFACE + "$Stub");
            statusTransaction = transactionCode(stub, "TRANSACTION_instrumentationStatus");
            finishedTransaction = transactionCode(stub, "TRANSACTION_instrumentationFinished");
        }

        boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return finished.await(timeout, unit);
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws android.os.RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) {
                    reply.writeString(WATCHER_DESCRIPTOR);
                }
                return true;
            }
            if (code != statusTransaction && code != finishedTransaction) {
                return super.onTransact(code, data, reply, flags);
            }
            data.enforceInterface(WATCHER_DESCRIPTOR);
            readComponentName(data);
            int callbackCode = data.readInt();
            Bundle callbackResults = readBundle(data);
            if (reply != null) {
                reply.writeNoException();
            }

            String reportedError = callbackResults == null
                    ? null : callbackResults.getString("Error");
            if (reportedError != null) {
                error = reportedError;
                finished.countDown();
            } else if (code == finishedTransaction) {
                resultCode = callbackCode;
                results = callbackResults;
                if (callbackResults != null && callbackResults.getString("error") != null) {
                    error = callbackResults.getString("error");
                }
                finished.countDown();
            }
            return true;
        }

        private static int transactionCode(Class<?> stub, String fieldName) throws Exception {
            Field field = stub.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(null);
        }

        private static ComponentName readComponentName(Parcel data) {
            return data.readInt() == 0 ? null : ComponentName.CREATOR.createFromParcel(data);
        }

        private static Bundle readBundle(Parcel data) {
            return data.readInt() == 0 ? null : Bundle.CREATOR.createFromParcel(data);
        }
    }
}
