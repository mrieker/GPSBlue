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

/**
 * Service to hang on to bluetooth connections even if GUI put in background.
 * Accomplished by keeping a table of all Thread objects.  It also maintains
 * a status bar notification indicating to the user that it is in memory
 * holding bluetooth connections open.
 */

package com.outerworldapps.gpsblue;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

public class JSessionService extends Service {
    public final static String TAG = "GPSBlue";

    private final static double KtPerMPS  = 1.94384;
    private final static int NOTIFY_ID = 423112313;
    private final static String APP_NAME = "GPSBlue";
    private final static String CHANNEL_ID = "connectioncount";

    public  BluetoothServer bluetoothServer;
    private boolean gpsStarted;
    private boolean listening;
    private GPSBlue gpsBlue;
    public  int numlocationsrcvd;
    public  int numstatusesrcvd;
    private int numsats;
    public  InternalGps internalGps;
    public  Location latestLocation;
    private final MyBinder myBinder = new MyBinder ();
    private MyGpsSatellite[] latestSatellites;
    private NotificationManager notificationManager;
    public  final Object connectionLock = new Object ();
    private PowerManager.WakeLock partialWakeLock;
    private SimpleDateFormat sdfhms;
    private SimpleDateFormat sdfdmy;
    public  String latestStatusText;
    private String pendingAlertMessage;
    private String pendingAlertTitle;

    /***************************\
     *  Service-context calls  *
    \***************************/

    // service just brought into memory
    @Override
    public void onCreate ()
    {
        Log.d (TAG, "JSessionService created");

        notificationManager = (NotificationManager) getSystemService (Context.NOTIFICATION_SERVICE);
        createNotificationChannel ();
        Notification notification = createNotification (0);
        startForeground (NOTIFY_ID, notification);

        PowerManager powerManager = (PowerManager) getSystemService (Context.POWER_SERVICE);
        partialWakeLock = powerManager.newWakeLock (PowerManager.PARTIAL_WAKE_LOCK,
            APP_NAME + ":bluetooth connections");

        sdfhms = new SimpleDateFormat ("HHmmss.SSS", Locale.US);
        sdfhms.setTimeZone (TimeZone.getTimeZone ("UTC"));

        sdfdmy = new SimpleDateFormat ("ddMMyy", Locale.US);
        sdfdmy.setTimeZone (TimeZone.getTimeZone ("UTC"));

        bluetoothServer = new BluetoothServer (this);
        internalGps = new InternalGps (this);
    }

    // service being taken out of memory
    @Override
    public void onDestroy ()
    {
        Log.d (TAG, "JSessionService destroyed");
        notificationManager.cancelAll ();
        bluetoothServer.shutdown ();
        internalGps.stopSensor ();
        if (gpsStarted) {
            partialWakeLock.release ();
            gpsStarted = false;
        }
        bluetoothServer = null;
        internalGps = null;
        notificationManager = null;
        partialWakeLock = null;
    }

    // GPSBlue app was just started and called startService()
    // We stay in memory even after the app is destroyed
    @Override
    public int onStartCommand (Intent intent, int flags, int startId)
    {
        Log.d (TAG, "JSessionService started");
        return Service.START_STICKY;
    }

    // GPSBlue app was just started and called bindService()
    // Return a pointer to this object for GPSBlue to use to call us
    @Override
    public IBinder onBind (Intent intent)
    {
        return myBinder;
    }

    public class MyBinder extends Binder {
        public JSessionService getService ()
        {
            return JSessionService.this;
        }
    }

    /****************************\
     *  Activity-context calls  *
    \****************************/

    public void openingScreen (GPSBlue gpsb)
    {
        if (gpsBlue != null) {
            throw new IllegalStateException ("service already bound");
        }
        gpsBlue = gpsb;

        if (pendingAlertTitle != null) {
            gpsb.fatalError (pendingAlertTitle, pendingAlertMessage);
        }
    }

    public void closingScreen ()
    {
        gpsBlue = null;
    }

    public void startListening (UUID uuid)
    {
        if (! listening) {
            Log.d (TAG, "JSessionService start listening");
            listening = true;
            bluetoothServer.startup (uuid);
        }
    }

    public void stopListening ()
    {
        if (listening) {
            listening = false;
            bluetoothServer.shutdown ();
            Log.d (TAG, "JSessionService stop listening");
        }
    }

    /****************************************************\
     *  Called from various threads within the service  *
    \****************************************************/

    /**
     * Start up the app, display error message then stop the service.
     */
    public void fatalError (final String tit, final String msg)
    {
        Notification notification = createNotification (tit);
        notificationManager.notify (NOTIFY_ID, notification);
        GPSBlue gpsb = gpsBlue;
        if (gpsb != null) {
            gpsb.fatalError (tit, msg);
        } else {
            pendingAlertMessage = msg;
            pendingAlertTitle   = tit;
            startActivity (getPackageManager ().getLaunchIntentForPackage (getPackageName ()));
        }
    }

    /**
     * Current number of connections has changed.
     * If zero, let CPU and screen go to sleep.
     * If non-zero, keep CPU on, let screen shut off.
     * Update in-app count and notification count.
     * Called with connectionLock locked.
     */
    @SuppressLint("WakelockTimeout")
    public void updateConnectionCount (UUID sppUUID, int count)
    {
        if (count > 0) {
            if (! gpsStarted) {
                partialWakeLock.acquire ();
                internalGps.startSensor ();
                gpsStarted = true;
            }
        } else {
            if (gpsStarted) {
                internalGps.stopSensor ();
                partialWakeLock.release ();
                gpsStarted = false;
            }
        }

        latestStatusText = "uuid: " + sppUUID.toString ().toUpperCase () +
                "\nconnections: " + count;
        final GPSBlue gpsb = gpsBlue;
        if (gpsb != null) {
            gpsb.runOnUiThread (new Runnable () {
                @Override
                public void run ()
                {
                    gpsb.statusTextView.updateText ();
                }
            });
        }

        Notification notification = createNotification (count);
        notificationManager.notify (NOTIFY_ID, notification);
    }

    /*******************************************************************\
     *  Values received from GPS get transmitted to bluetooth clients  *
    \*******************************************************************/

    /**
     * GPS location received.
     * Called in InternalGps.GPSRcvrThread.
     */
    public void LocationReceived (Location loc)
    {
        Date date = new Date (loc.getTime ());
        String strhms = sdfhms.format (date);   // hhmmss.sss
        String strdmy = sdfdmy.format (date);   // ddmmyy

        double lat = loc.getLatitude ();
        double lon = loc.getLongitude ();
        double alt = loc.getAltitude ();

        // http://www.gpsinformation.org/dale/nmea.htm#GGA
        StringBuilder sb = new StringBuilder ();
        sb.append ("$GPGGA,");
        sb.append (strhms);
        sb.append (',');
        LatLonDegMin (sb, lat, 'N', 'S');
        sb.append (',');
        LatLonDegMin (sb, lon, 'E', 'W');
        sb.append (String.format (Locale.US, ",1,%d,0.9,%.1f,M,,,,", numsats, alt));
        NMEAChecksum (sb);

        // http://www.gpsinformation.org/dale/nmea.htm#RMC
        sb.append ("$GPRMC,");
        sb.append (strhms);
        sb.append (",A,");
        LatLonDegMin (sb, lat, 'N', 'S');
        sb.append (',');
        LatLonDegMin (sb, lon, 'E', 'W');
        sb.append (String.format (Locale.US, ",%.1f,%.1f,", loc.getSpeed () * KtPerMPS, loc.getBearing ()));
        sb.append (strdmy);
        sb.append (",,");
        NMEAChecksum (sb);

        TransmitString (sb.toString ());

        latestLocation = loc;
        numlocationsrcvd ++;
        final GPSBlue gpsb = gpsBlue;
        if (gpsb != null) {
            gpsb.runOnUiThread (new Runnable () {
                @Override
                public void run ()
                {
                    gpsb.statusTextView.updateText ();
                }
            });
        }
    }

    /**
     * Satellite status received from GPS.
     * Transmit update over bluetooth.
     * Update screen if app attached.
     * Called in InternalGps.GPSRcvrThread.
     */
    public void SatellitesReceived (final MyGpsSatellite[] satellites)
    {
        if (satellites == null) {
            numsats = 0;
        } else {
            numsats = satellites.length;

            // http://www.gpsinformation.org/dale/nmea.htm#GSV
            int totalsentences = (numsats + 3) / 4;
            if (totalsentences == 0) ++totalsentences;
            int satelliteindex = 0;
            StringBuilder sb = new StringBuilder ();
            MyGpsSatellite[] usedprns = new MyGpsSatellite[12];
            int nusedprns = 0;
            for (MyGpsSatellite sat : satellites) {
                if (satelliteindex % 4 == 0) {
                    sb.append ("$GPGSV,");
                    sb.append (totalsentences);
                    sb.append (',');
                    sb.append (satelliteindex / 4 + 1);
                    sb.append (',');
                    sb.append (numsats);
                }
                sb.append (',');
                sb.append (sat.prn);
                sb.append (',');
                sb.append (sat.elev);
                sb.append (',');
                sb.append (sat.azim);
                sb.append (',');
                sb.append (sat.snr);
                if (++ satelliteindex % 4 == 0) {
                    NMEAChecksum (sb);
                }
                if (sat.used) {
                    int i;
                    for (i = 0; i < nusedprns; i ++) {
                        if (sat.snr > usedprns[i].snr) break;
                    }
                    if (nusedprns < usedprns.length) nusedprns ++;
                    if (i < nusedprns) {
                        System.arraycopy (usedprns, i, usedprns, i + 1, nusedprns - i - 1);
                        usedprns[i] = sat;
                    }
                }
            }
            if (satelliteindex % 4 != 0) {
                NMEAChecksum (sb);
            }
            sb.append ("$GPGSA,A,3");
            for (int i = 0; i < usedprns.length; i ++) {
                sb.append (',');
                if (i < nusedprns) {
                    sb.append (usedprns[i].prn);
                }
            }
            sb.append (",1.2,1.2,1.2");
            NMEAChecksum (sb);

            TransmitString (sb.toString ());

            numstatusesrcvd ++;
        }
        latestSatellites = satellites;

        final GPSBlue gpsb = gpsBlue;
        if (gpsb != null) {
            gpsb.runOnUiThread (new Runnable () {
                @Override
                public void run ()
                {
                    gpsb.statusTextView.updateText ();
                    gpsb.satelliteRingView.UpdateSatellites (latestSatellites);
                }
            });
        }
    }

    // convert a number of degrees to ddmm.1000s string
    private static void LatLonDegMin (StringBuilder sb, double ll, char pos, char neg)
    {
        int min1000 = (int) Math.round (ll * 60000.0);
        if (min1000 < 0) {
            min1000 = - min1000;
            pos = neg;
        }
        int deg  = min1000 / 60000;
        min1000 %= 60000;
        int min  = min1000 / 1000;
        min1000 %= 1000;
        sb.append (String.format (Locale.US, "%d%02d.%03d", deg, min, min1000));
        sb.append (',');
        sb.append (pos);
    }

    // append NMEA checksum and CRLF to a string
    private static void NMEAChecksum (StringBuilder sb)
    {
        int len = sb.length ();
        int xor = 0;
        for (int i = len; -- i > 0;) {
            char c = sb.charAt (i);
            if (c == '$') break;
            xor ^= c;
        }
        sb.append (String.format (Locale.US, "*%02X\r\n", xor));
    }

    // transmit to all connected bluetooth EFB apps
    // called in InternalGps.GPSRcvrThread.
    private void TransmitString (String st)
    {
        byte[] bytes = st.getBytes ();
        BluetoothServer bs = bluetoothServer;
        if (bs != null) bs.write (bytes, 0, bytes.length);
    }

    /**************\
     *  Internal  *
    \**************/

    /**
     * Create a new status bar notification indicating the given connection count.
     */
    private Notification createNotification (int count)
    {
        String text;
        switch (count) {
            case 0:
                text = "listening for connections";
                break;
            case 1:
                text = "1 connection active";
                break;
            default:
                text = count + " connections active";
                break;
        }
        return createNotification (text);
    }

    /**
     * Create a new status bar notification displaying the given text.
     */
    private Notification createNotification (String text)
    {
        Notification.Builder nb;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nb = new Notification.Builder (this, CHANNEL_ID);
        } else {
            nb = new Notification.Builder (this);
        }
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)) {
            // use a full-color icon when android can handle full-color icons for notifications
            nb.setSmallIcon (R.drawable.satblue_64);
        } else {
            // background->full transparent; foreground->white,full opaque
            nb.setSmallIcon (R.drawable.satwhite_64);
        }
        nb.setTicker (APP_NAME + " connections");
        nb.setWhen (System.currentTimeMillis ());

        Intent ni = new Intent (this, this.getClass ());
        ni.setFlags (Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity (this, 0, ni, 0);

        nb.setContentTitle (APP_NAME + " connections");
        nb.setContentText (text);
        nb.setContentIntent (pi);

        @SuppressWarnings("deprecation")
        Notification note = nb.getNotification ();
        note.flags |= Notification.FLAG_FOREGROUND_SERVICE | Notification.FLAG_ONGOING_EVENT |
                Notification.FLAG_ONLY_ALERT_ONCE;
        return note;
    }

    // https://developer.android.com/training/notify-user/build-notification
    private void createNotificationChannel()
    {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = APP_NAME + " Connections";
            String description = "number of " + APP_NAME + " connections";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel (CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            notificationManager.createNotificationChannel(channel);
        }
    }
}
