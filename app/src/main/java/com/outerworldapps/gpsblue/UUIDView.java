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
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.UUID;

/**
 * Editable UUID box.
 */
@SuppressLint("ViewConstructor")
public class UUIDView extends LinearLayout {
    private final static int[] ndigs = { 8, 4, 4, 4, 12 };

    private Context ctx;
    private float   textSizeSize;
    private int     textSizeUnit;
    private String[] parts;
    private TextView[] digits0n;

    public UUIDView (Context ctx)
    {
        super (ctx);
        this.ctx = ctx;
        setOrientation (HORIZONTAL);
        textSizeSize = 10;
        textSizeUnit = TypedValue.COMPLEX_UNIT_PX;
        setup ();
    }

    public void setTextSize (int unit, float size)
    {
        textSizeUnit = unit;
        textSizeSize = size;
        setup ();
    }

    public UUID getVal ()
    {
        StringBuilder sb = new StringBuilder ();
        for (TextView digits : digits0n) {
            if (sb.length () > 0) sb.append ('-');
            sb.append (digits.getText ().toString ());
        }
        return UUID.fromString (sb.toString ());
    }

    public void setVal (UUID val)
    {
        String str = val.toString ().toUpperCase ();
        parts = str.split ("-");
        setup ();
    }

    private void setup ()
    {
        removeAllViews ();
        digits0n = new TextView[ndigs.length];
        int i = 0;
        for (int ndigit : ndigs) {
            if (i == 0) {
                HexDigits digits0 = new HexDigits (ndigit);
                if (parts != null) digits0.setText (parts[i]);
                digits0n[i++] = digits0;
                addView (digits0);
            } else {
                TextView dash = new TextView (ctx);
                dash.setTextSize (textSizeUnit, textSizeSize);
                dash.setText ("-");
                addView (dash);
                TextView digs = new TextView (ctx);
                digs.setTextSize (textSizeUnit, textSizeSize);
                if (parts != null) digs.setText (parts[i]);
                digits0n[i++] = digs;
                addView (digs);
            }
        }
    }

    private class HexDigits extends EditText implements TextWatcher {
        private int ndigs;

        public HexDigits (int nd)
        {
            super (ctx);
            ndigs = nd;
            setSingleLine (true);
            setTextSize (textSizeUnit, textSizeSize);
            addTextChangedListener (this);
        }

        @Override  // TextWatcher
        public void afterTextChanged (Editable s)
        {
            String str = s.toString ();
            if (str.equals ("")) return;
            StringBuilder sb = new StringBuilder (ndigs);
            for (int i = 0; i < str.length (); i ++) {
                char c = str.charAt (i);
                if ((c >= 'a') && (c <= 'f')) c -= 'a' - 'A';
                if (((c >= '0') && (c <= '9')) || ((c >= 'A') && (c <= 'F'))) {
                    sb.append (c);
                    if (sb.length () == ndigs) break;
                }
            }
            if (! str.equals (sb.toString ())) {
                setText (sb.toString ());
                setSelection (getText ().length ());
            }
        }

        @Override  // TextWatcher
        public void beforeTextChanged (CharSequence s, int start, int count, int after) { }
        @Override  // TextWatcher
        public void onTextChanged (CharSequence s, int start, int before, int count) { }
    }
}
