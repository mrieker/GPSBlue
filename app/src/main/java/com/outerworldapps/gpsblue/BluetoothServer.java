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
 * Accept incoming bluetooth connections.
 * Ignore and flush any incoming data.
 * Send out GPS data in form of NMEA messages.
 * Runs in service context.
 */

package com.outerworldapps.gpsblue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.util.HashSet;
import java.util.UUID;

public class BluetoothServer {
    private AcceptThread acceptThread;
    private final HashSet<ReceiveThread> receivers;
    private JSessionService jSessionService;
    private UUID sppUUID;

    public BluetoothServer (JSessionService jss)
    {
        jSessionService = jss;
        receivers = new HashSet<> ();
    }

    /**
     * Start listening on the given bluetooth socket UUID.
     */
    public void startup (UUID uuid)
    {
        if ((acceptThread == null) || ! sppUUID.equals (uuid)) {
            shutdown ();
            sppUUID = uuid;
            acceptThread = new AcceptThread ();
            acceptThread.start ();
        }
    }

    /**
     * Stop listening for new inbound connections.
     * Terminate any existing connections.
     */
    public void shutdown ()
    {
        if (acceptThread != null) {
            acceptThread.finish ();
            try { acceptThread.join (); } catch (InterruptedException ignored) { }
            acceptThread = null;
        }
        while (true) {
            ReceiveThread rt;
            synchronized (receivers) {
                if (receivers.isEmpty ()) break;
                rt = receivers.iterator ().next ();
            }
            rt.finish ();
            try { rt.join (); } catch (InterruptedException ignored) { }
        }
    }

    /**
     * Send message to all connections.
     */
    public void write (byte[] buf, int ofs, int len)
    {
        synchronized (receivers) {
            for (ReceiveThread rt : receivers) {
                try {
                    rt.bs.getOutputStream ().write (buf, ofs, len);
                } catch (IOException ioe) {
                    Log.w (GPSBlue.TAG, "error sending to bluetooth", ioe);
                    rt.senderr = true;
                }
            }
        }
    }

    /**
     * Thread what listens for incoming bluetooth connections.
     * Spawns receiver thread for each found.
     */
    private class AcceptThread extends Thread {
        private BluetoothServerSocket serverSocket;
        private boolean finished;

        /**
         * Tell thread to exit.
         */
        public void finish ()
        {
            finished = true;
            try { serverSocket.close (); } catch (Exception ignored) { }
        }

        @Override
        public void run ()
        {
            try {
                BluetoothManager bm = (BluetoothManager) jSessionService.getSystemService (Context.BLUETOOTH_SERVICE);
                BluetoothAdapter ba = bm.getAdapter ();
                if (ba == null) throw new Exception ("no bluetooth on this device");
                serverSocket = ba.listenUsingInsecureRfcommWithServiceRecord ("GPSBlue", sppUUID);
                synchronized (receivers) {
                    updateStatusText ();
                }
                //noinspection InfiniteLoopStatement
                while (true) {
                    ReceiveThread rt = new ReceiveThread ();
                    rt.bs = serverSocket.accept ();
                    rt.start ();
                }
            } catch (Exception e) {
                Log.w (GPSBlue.TAG, "error accepting bluetooth " + sppUUID, e);
                try { serverSocket.close (); } catch (Exception ignored) { }
                serverSocket = null;
                if (! finished) {
                    jSessionService.fatalError ("Bluetooth Error", "try starting bluetooth\nor try different UUID\n" + e.getMessage ());
                }
            }
        }
    }

    // we have a new inbound connection
    // this thread runs as long as that device is connected
    // we don't really receive anything meaningful from the device,
    // ...but we use this thread to sense when it disconnects
    private class ReceiveThread extends Thread {
        public BluetoothSocket bs;
        public boolean senderr;

        // drop connection and get thread to exit
        public void finish ()
        {
            try { bs.close (); } catch (IOException ignored) { }
        }

        @Override
        public void run ()
        {
            // if this is the first connection, turn the GPS receiver on
            // in any case, update the total number of inbound connections
            synchronized (receivers) {
                if (receivers.isEmpty ()) {
                    jSessionService.internalGps.startSensor ();
                }
                receivers.add (this);
                jSessionService.updateNotificationConnectionCount (receivers.size ());
                updateStatusText ();
            }

            // read from the connection simply to detect when it disconnects
            byte[] buf = new byte[4096];
            try {
                while (! senderr) {
                    int rc = bs.getInputStream ().read (buf);
                    if (rc <= 0) break;
                }
            } catch (IOException ioe) {
                Log.w (GPSBlue.TAG, "error receiving from bluetooth", ioe);
            } finally {

                // close the socket and tell service one less connection being handled
                // if no connections, turn the GPS receiver off
                synchronized (receivers) {
                    try { bs.close (); } catch (IOException ignored) { }
                    receivers.remove (this);
                    jSessionService.updateNotificationConnectionCount (receivers.size ());
                    updateStatusText ();
                    if (receivers.isEmpty ()) {
                        jSessionService.internalGps.stopSensor ();
                    }
                }
            }
        }
    }

    private void updateStatusText ()
    {
        jSessionService.setBtStatusText (
                "uuid: " + sppUUID.toString ().toUpperCase () +
                "\nconnections: " + receivers.size ());
    }
}
