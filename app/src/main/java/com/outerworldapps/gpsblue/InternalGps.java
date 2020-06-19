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

import android.content.Context;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import java.util.LinkedList;

/**
 * Use Android device's internal GPS for location source.
 * Runs as part of the service.
 * Passes status & location messages to jSessionSerivce.
 */
public class InternalGps implements GpsStatus.Listener, LocationListener {
    public final static String TAG = "GPSBlue";

    private final static MyGpsSatellite[] nullsatarray = new MyGpsSatellite[0];

    private GPSRcvrThread rcvrThread;
    private GpsStatus gpsStatus;
    private JSessionService jSessionService;
    private LinkedList<MyGpsSatellite> satellites;
    private LocationManager locationManager;

    public InternalGps (JSessionService jss)
    {
        jSessionService = jss;
    }

    // called with jSessionService.connectionLock locked
    public void startSensor ()
    {
        if (rcvrThread == null) {
            locationManager = (LocationManager) jSessionService.getSystemService (Context.LOCATION_SERVICE);
            if (locationManager == null) {
                jSessionService.fatalError ("GPS Access Error", "No location manager available");
                return;
            }
            if (! locationManager.isProviderEnabled (LocationManager.GPS_PROVIDER)) {
                jSessionService.fatalError ("GPS Access Error",
                        "GPS location receiver disabled\nenable in Android settings and restart");
                return;
            }
            rcvrThread = new GPSRcvrThread ();
            rcvrThread.start ();
        }
    }

    // called with jSessionService.connectionLock locked
    public void stopSensor ()
    {
        if (rcvrThread != null) {
            rcvrThread.thelooper.quit ();
            try { rcvrThread.join (); } catch (InterruptedException ignored) { }
            rcvrThread = null;
        }
    }

    /**************************\
     *   GPS receiver thread  *
    \**************************/

    private class GPSRcvrThread extends Thread {
        public Looper thelooper;

        @Override
        public void run ()
        {
            Looper.prepare ();
            thelooper = Looper.myLooper ();

            // start receiving status & location from internal GPS receiver
            try {
                locationManager.requestLocationUpdates (LocationManager.GPS_PROVIDER, 1000, 0.0F, InternalGps.this);
                locationManager.addGpsStatusListener (InternalGps.this);
                satellites = new LinkedList<> ();
            } catch (SecurityException se) {
                Log.e (TAG, "error starting GPS", se);
                jSessionService.fatalError ("GPS Startup Error", se.getMessage ());
                return;
            }

            // process the incoming status & location messages from internal GPS receiver
            Looper.loop ();

            // stopSensor() was called, stop receiving messages
            locationManager.removeUpdates (InternalGps.this);
            locationManager.removeGpsStatusListener (InternalGps.this);

            // update display to show no longer active (removes inner rings from circle graphic)
            jSessionService.SatellitesReceived (null);
        }
    }

    /*********************************************\
     *  LocationListener implementation          *
     *  Receives incoming GPS location readings  *
     *  Runs in GpsRcvrThread via Looper.loop()  *
    \*********************************************/

    @Override  // LocationListener
    public void onLocationChanged (Location loc)
    {
        jSessionService.LocationReceived (loc);
    }

    @Override  // LocationListener
    public void onProviderDisabled(String arg0)
    { }

    @Override  // LocationListener
    public void onProviderEnabled(String arg0)
    { }

    @Override  // LocationListener
    public void onStatusChanged(String provider, int status, Bundle extras)
    { }

    /*********************************************\
     *  GpsStatus.Listener implementation        *
     *  Receives incoming GPS satellite status.  *
     *  Runs in GpsRcvrThread via Looper.loop()  *
    \*********************************************/

    @Override  // GpsStatus.Listener
    public void onGpsStatusChanged (int event)
    {
        if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
            try {
                gpsStatus = locationManager.getGpsStatus (gpsStatus);
                Iterable<GpsSatellite> sats = gpsStatus.getSatellites ();
                satellites.clear ();
                for (GpsSatellite sat : sats) {
                    MyGpsSatellite mysat = new MyGpsSatellite ();
                    mysat.azim = sat.getAzimuth ();
                    mysat.elev = sat.getElevation ();
                    mysat.prn  = sat.getPrn ();
                    mysat.snr  = sat.getSnr ();
                    mysat.used = sat.usedInFix ();
                    satellites.addLast (mysat);
                }
                MyGpsSatellite[] satarray = satellites.toArray (nullsatarray);
                jSessionService.SatellitesReceived (satarray);
            } catch (SecurityException se) {
                Log.w (TAG, "error reading gps status", se);
                jSessionService.fatalError ("GPS Status Error", se.getMessage ());
            }
        }
    }
}
