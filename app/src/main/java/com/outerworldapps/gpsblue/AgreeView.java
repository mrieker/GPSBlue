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
import android.app.Activity;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Validate that user has agreed to licence terms.
 */
@SuppressLint("ViewConstructor")
public class AgreeView extends ScrollView {
    private GPSBlue gpsBlue;

    @SuppressLint("SetTextI18n")
    public AgreeView (GPSBlue wtn)
    {
        super (wtn);
        gpsBlue = wtn;

        removeAllViews ();

        LinearLayout llv = new LinearLayout (gpsBlue);
        llv.setOrientation (LinearLayout.VERTICAL);

        TextView tvh = new TextView (gpsBlue);
        gpsBlue.SetTextSize (tvh);
        tvh.setText ("Please read then scroll to bottom to accept or reject license agreement.");
        llv.addView (tvh);

        llv.addView (gpsBlue.helpView);

        TextView tvf = new TextView (gpsBlue);
        gpsBlue.SetTextSize (tvf);
        tvf.setText ("Note:  The above can be re-displayed later by clicking the Help button in the menu.");
        llv.addView (tvf);

        Button butok = new Button (gpsBlue);
        gpsBlue.SetTextSize (butok);
        butok.setText ("I ACCEPT the terms of the above license, and will use GPSBlue only in a " +
                       "manner where, in the words of AC 120-76C, any failure would result " +
                       "in only a minor hazard or less.");
        butok.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view)
            {
                SharedPreferences prefs = gpsBlue.getPreferences (Activity.MODE_PRIVATE);
                long now = System.currentTimeMillis ();
                SharedPreferences.Editor editr = prefs.edit ();
                editr.putLong ("hasAgreed", now);
                editr.apply ();

                gpsBlue.hasAgreed ();
            }
        });
        llv.addView (butok);

        Button butcan = new Button (gpsBlue);
        gpsBlue.SetTextSize (butcan);
        butcan.setText ("I DO NOT ACCEPT the terms of the above license, or I might use GPSBlue in " +
                        "a manner where, in the words of AC 120-76C, any failure might present " +
                        "more than a minor hazard.");
        butcan.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view)
            {
                gpsBlue.finish ();
            }
        });
        llv.addView (butcan);

        addView (llv);
    }
}
