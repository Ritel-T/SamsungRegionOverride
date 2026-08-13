package com.riteldevelopment.carriertestoverride;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

/** Keeps the app's default process alive while Samsung AMS attaches instrumentation to it. */
public final class InstrumentationHostService extends Service {
    private final IBinder binder = new Binder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
