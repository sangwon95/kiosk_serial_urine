package com.onwards.kiosk.utils;

import com.onwards.kiosk.BuildConfig;

public class Constants {

    // values have to be globally unique
    static public final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    static public final String INTENT_ACTION_DISCONNECT = BuildConfig.APPLICATION_ID + ".Disconnect";
    static public final String NOTIFICATION_CHANNEL = BuildConfig.APPLICATION_ID + ".Channel";
    static public final String INTENT_CLASS_MAIN_ACTIVITY = BuildConfig.APPLICATION_ID + ".MainActivity";

    private Constants() {}
}
