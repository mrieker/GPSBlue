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

import android.annotation.SuppressLint;
import android.location.Location;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Display status text:
 *   whether service is started/stopped
 *   latest GPS position received
 *   number of GPS locations & statuses received
 *   number of current bluetooth connections
 */
@SuppressLint("ViewConstructor")
public class StatusTextView extends TextView {
    public static final double FtPerM   = 3.28084;
    public static final double KtPerMPS = 1.94384;

    public  GPSBlue gpsBlue;
    private SimpleDateFormat sdf;

    public StatusTextView (GPSBlue gpsb)
    {
        super (gpsb);
        gpsBlue = gpsb;

        gpsBlue.SetTextSize (this);
        LinearLayout.LayoutParams gpsStatusTextLayoutParams =
                new LinearLayout.LayoutParams (ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        setLayoutParams (gpsStatusTextLayoutParams);

        sdf = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        sdf.setTimeZone (TimeZone.getTimeZone ("UTC"));
    }

    public void updateText ()
    {
        StringBuilder sb = new StringBuilder ();

        sb.append (gpsBlue.running ?
                "STARTED\n  click Menu\u21D2Stop BT to stop\n" :
                "STOPPED\n  click Menu\u21D2Start BT to start\n");

        JSessionService jss = gpsBlue.jSessionService;
        if (jss != null) {
            Location loc = jss.latestLocation;
            if (loc != null) {
                sb.append ("\n");
                sb.append (sdf.format (loc.getTime ()));
                sb.append (" UTC'\n");

                LatLonString (sb, loc.getLatitude (), 'N', 'S');
                sb.append ("    ");
                LatLonString (sb, loc.getLongitude (), 'E', 'W');
                sb.append ('\n');

                sb.append (Math.round (loc.getAltitude () * FtPerM));
                sb.append (" ft MSL    ");

                sb.append (String.format (Locale.US, "%05.1f", loc.getBearing ()));
                sb.append ("\u00B0 T    ");

                sb.append (String.format (Locale.US, "%3.1f", loc.getSpeed () * KtPerMPS));
                sb.append (" kts\n");
            }

            sb.append ("\nStatuses received: ");
            sb.append (jss.numstatusesrcvd);
            sb.append ("\nLocations received: ");
            sb.append (jss.numlocationsrcvd);
            sb.append ('\n');

            String lst = jss.latestStatusText;
            if (lst != null) {
                sb.append ('\n');
                sb.append (lst);
                sb.append ('\n');
            }
        }

        setText (sb);
    }

    private static void LatLonString (StringBuilder sb, double ll, char pos, char neg)
    {
        if (ll < 0.0) {
            sb.append (String.format (Locale.US, "%09.5f", -ll));
            sb.append ('\u00B0');
            sb.append (neg);
        } else {
            sb.append (String.format (Locale.US, "%09.5f", ll));
            sb.append ('\u00B0');
            sb.append (pos);
        }
    }
}
