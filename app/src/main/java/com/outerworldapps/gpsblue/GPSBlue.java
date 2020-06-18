//    Copyright (C) 2020, Mike Rieker, Beverly, MA USA
//    www.outerworldapps.com
//
//    This program is free software; you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation; version 2 of the License.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    EXPECT it to FAIL when someone's HeALTh or PROpeRTy is at RISk.
//
//    You should have received a copy of the GNU General Public License
//    along with this program; if not, write to the Free Software
//    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//    http://www.gnu.org/licenses/gpl-2.0.html

package com.outerworldapps.gpsblue;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.UUID;

public class GPSBlue extends Activity implements ServiceConnection {
    public final static String TAG = "GPSBlue";

    private final static int RC_INTGPS = 9876;

    private boolean hasAgreed;
    public  boolean running;
    public  float textSize;
    public  int displayWidth;
    public  int displayHeight;
    public  HelpView helpView;
    private Intent jsessionserviceintent;
    public  JSessionService jSessionService;
    public  StatusTextView statusTextView;
    public  SatelliteRingView satelliteRingView;
    private ScrollView homeView;
    private UUIDView uuidView;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate (Bundle savedInstanceState)
    {
        super.onCreate (savedInstanceState);

        /*
         * Get pixel size.
         */
        DisplayMetrics metrics = new DisplayMetrics ();
        getWindowManager ().getDefaultDisplay ().getMetrics (metrics);
        float dotsPerInch = (float) Math.sqrt (metrics.xdpi * metrics.ydpi);

        /*
         * Get text size to use throughout.
         */
        textSize = dotsPerInch / 7;

        /*
         * Also get screen size in pixels.
         */
        displayWidth = metrics.widthPixels;
        displayHeight = metrics.heightPixels;

        // set up help page
        helpView = new HelpView (this);

        // text box that shows current lat/lon, altitude, time, etc.
        statusTextView = new StatusTextView (this);

        // compass-like dial that shows where GPS satellites are
        satelliteRingView = new SatelliteRingView (this);
        int gsvSize = Math.round (dotsPerInch * 1.5F);
        LinearLayout.LayoutParams gpsStatusViewLayoutParams =
                new LinearLayout.LayoutParams (gsvSize, gsvSize);
        satelliteRingView.setLayoutParams (gpsStatusViewLayoutParams);

        // bluetooth SPP UUID
        uuidView = new UUIDView (this);
        SharedPreferences prefs = getPreferences (Context.MODE_PRIVATE);
        String uuidpref = prefs.getString ("btsppuuid", "00001101-0000-1000-8000-00805f9b34fb");
        uuidView.setVal (UUID.fromString (uuidpref));
        uuidView.setTextSize (TypedValue.COMPLEX_UNIT_PX, textSize);

        // bundle them together in a vertically scrollable linear layout
        LinearLayout statusLinearLayout = new LinearLayout (this);
        statusLinearLayout.setOrientation (LinearLayout.VERTICAL);
        statusLinearLayout.addView (satelliteRingView);
        statusLinearLayout.addView (statusTextView);
        statusLinearLayout.addView (uuidView);

        // make all that scrollable in case it overflows screen
        homeView = new ScrollView (this);
        homeView.addView (statusLinearLayout);

        // see if has agreed to license terms within past 90 days
        long hasagreed = prefs.getLong ("hasAgreed", 0);
        if (System.currentTimeMillis () - hasagreed < 90L * 86400L * 1000L) {
            hasAgreed ();
        } else {
            setContentView (new AgreeView (this));
        }
    }

    public void hasAgreed ()
    {
        hasAgreed = true;
        setContentView (homeView);

        if (ContextCompat.checkSelfPermission (this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions (this,
                    new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                    RC_INTGPS);
            return;
        }

        // pretend clicked the Start button
        // also binds to the service and so we know it is started
        StartBt ();
    }

    // Permission granting
    @Override
    public void onRequestPermissionsResult (int requestCode,
                                            @NonNull String[] permissions,
                                            @NonNull int[] grantResults)
    {
        if (requestCode == RC_INTGPS) {
            if (ContextCompat.checkSelfPermission (this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                finish ();
            } else {
                StartBt ();
            }
        }
    }

    /**
     * App being taken out of memory.
     * Disconnect from service but leave service running.
     */
    @Override
    public void onDestroy ()
    {
        super.onDestroy ();
        unbindService ();
    }

    /**
     * Set standard text size used throughout.
     */
    public void SetTextSize (TextView tv)
    {
        tv.setTextSize (TypedValue.COMPLEX_UNIT_PX, textSize);
    }

    /**
     * We are all done with the jSessionService object.
     */
    private void unbindService ()
    {
        // tell jSessionService to stop updating our screen
        if (jSessionService != null) {
            jSessionService.closingScreen ();
            jSessionService = null;

            // tell android we just got rid of our jSessionService pointer
            // ...but service keeps running with whatever connections it has
            unbindService (this);
        }
    }

    /**************************************************\
     *  Service binds when we call bindService()      *
    \**************************************************/

    /**
     * Something just called bindService().
     * Save pointer to service so we can call it.
     * Then tell the service to start listening for incoming connections if not already.
     */
    @Override
    public void onServiceConnected (ComponentName name, IBinder service)
    {
        jSessionService = ((JSessionService.MyBinder) service).getService ();
        jSessionService.openingScreen (this);

        UUID uuid = uuidView.getVal ();
        SharedPreferences prefs = getPreferences (Context.MODE_PRIVATE);
        SharedPreferences.Editor editr = prefs.edit ();
        editr.putString ("btsppuuid", uuid.toString ());
        editr.apply ();
        jSessionService.startListening (uuid);

        runOnUiThread (new Runnable () {
            @Override
            public void run ()
            {
                statusTextView.updateText ();
            }
        });
    }

    // the service crashed (should not happen)
    @Override
    public void onServiceDisconnected (ComponentName name)
    {
        throw new IllegalStateException ("service crashed");
    }

    /**
     * Callback from JSessionService if there is a fatal error.
     * Display message then terminate service.
     */
    public void fatalError (final String tit, final String msg)
    {
        runOnUiThread (new Runnable () {
            @Override
            public void run ()
            {
                AlertDialog.Builder adb = new AlertDialog.Builder (GPSBlue.this);
                adb.setTitle (tit);
                adb.setMessage (msg);
                adb.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
                    @Override
                    public void onClick (DialogInterface dialog, int which)
                    {
                        StopBt ();
                    }
                });
                adb.show ();
            }
        });
    }

    /*************************\
     *  MENU key processing  *
    \*************************/

    // Display the main menu when the hardware menu button is clicked.
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // main menu
        menu.add ("Close UI");
        menu.add ("Start BT");
        menu.add ("Stop BT");
        menu.add ("Help");
        menu.add ("Home");

        return true;
    }

    // This is called when someone clicks on an item in the
    // main menu displayed by the hardware menu button.
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem)
    {
        if (hasAgreed) {
            CharSequence sel = menuItem.getTitle ();
            if ("Close UI".contentEquals (sel)) {
                CloseUI ();
            }
            if ("Start BT".contentEquals (sel)) {
                StartBt ();
            }
            if ("Stop BT".contentEquals (sel)) {
                StopBt ();
            }
            if ("Help".contentEquals (sel)) {
                setContentView (helpView);
            }
            if ("Home".contentEquals (sel)) {
                setContentView (homeView);
            }
        }
        return true;
    }

    /**
     * Close the app.
     * Leave the service running so it will still handle connections.
     */
    private void CloseUI ()
    {
        unbindService ();
        finish ();
    }

    /**
     * User clicked Start - start the service listening for connections
     */
    private void StartBt ()
    {
        if (! running) {
            running = true;
            statusTextView.updateText ();
            uuidView.setVisibility (View.GONE);

            /*
             * Start JSessionService if not already running.
             * Calls onCreate() only if not already running.
             * Then always calls onStartCommand().
             * Service keeps running until stopSelf() or stopService() is called.
             * https://developer.android.com/guide/components/services
             */
            jsessionserviceintent = new Intent (this, JSessionService.class);
            startService (jsessionserviceintent);

            // get access to the JSessionService instance
            // calls onServiceConnected() below with instance
            if (! bindService (jsessionserviceintent, this, 0)) {
                throw new RuntimeException ("failed to bind to service");
            }
        }
    }

    /**
     * User clicked Stop - abort any connections and stop listening and stop service
     */
    private void StopBt ()
    {
        if (running) {
            jSessionService.stopListening ();
            unbindService ();
            stopService (jsessionserviceintent);
            uuidView.setVisibility (View.VISIBLE);
            running = false;
            statusTextView.updateText ();
        }
    }
}
