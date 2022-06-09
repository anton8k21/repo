package org.application.example;

import android.app.Application;

import com.onesignal.OneSignal;

public class ApplicationOverrideForOneSignal extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        OneSignal.initWithContext(this);
        OneSignal.setAppId("ONESIGNAL_APP_ID");
    }
}
