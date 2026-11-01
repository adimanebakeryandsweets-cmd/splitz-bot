package com.splitzblr.driver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After the phone restarts, go back online if the driver was logged in. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        try {
            if (c.getSharedPreferences(AlertService.PREFS, Context.MODE_PRIVATE).getBoolean("online", false)) AlertService.start(c);
        } catch (Exception e) { }
    }
}
