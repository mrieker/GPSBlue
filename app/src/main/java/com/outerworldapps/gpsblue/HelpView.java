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
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.View;
import android.webkit.WebView;

/**
 * Display help page in a web view.
 * Allows internal links to other resource pages
 * and allows external links to web pages.
 */
@SuppressLint("ViewConstructor")
public class HelpView extends WebView {
    private GPSBlue gpsBlue;

    @SuppressLint({ "AddJavascriptInterface", "SetJavaScriptEnabled" })
    public HelpView (GPSBlue gpsb)
    {
        super (gpsb);
        gpsBlue = gpsb;

        getSettings ().setBuiltInZoomControls (true);
        getSettings ().setJavaScriptEnabled (true);
        getSettings ().setDefaultFontSize (Math.round (gpsBlue.textSize / 2.0F));
        getSettings ().setDefaultFixedFontSize (Math.round (gpsBlue.textSize / 2.0F));
        getSettings ().setSupportZoom (true);
        addJavascriptInterface (new JavaScriptObject (), "hvjso");
        loadUrl ("file:///android_asset/help.html");
    }

    public View GetBackPage ()
    {
        goBack ();
        return this;
    }

    /**
     * Accessed via javascript in the internal .HTML files.
     */
    private class JavaScriptObject {

        @SuppressWarnings ("unused")
        @android.webkit.JavascriptInterface
        public String getVersionName ()
        {
            try {
                PackageInfo pInfo = gpsBlue.getPackageManager ().getPackageInfo (gpsBlue.getPackageName (), 0);
                return pInfo.versionName;
            } catch (PackageManager.NameNotFoundException nnfe) {
                return "";
            }
        }

        @SuppressWarnings ("unused")
        @android.webkit.JavascriptInterface
        public int getVersionCode ()
        {
            try {
                PackageInfo pInfo = gpsBlue.getPackageManager ().getPackageInfo (gpsBlue.getPackageName (), 0);
                return pInfo.versionCode;
            } catch (PackageManager.NameNotFoundException nnfe) {
                return -1;
            }
        }

        @SuppressWarnings ("unused")
        @android.webkit.JavascriptInterface
        public String getGithubLink ()
        {
            String fullhash = BuildConfig.GitHash;
            String abbrhash = fullhash.substring (0, 7);
            String status = BuildConfig.GitStatus;
            String[] lines = status.split ("\n");
            for (String line : lines) {
                line = line.trim ();
                if (line.startsWith ("On branch ")) {
                    if (line.equals ("On branch github")) {
                        String link = "https://github.com/mrieker/GPSBlue/commit/" + fullhash;
                        return "<A HREF=\"" + link + "\">" + abbrhash + "</A>";
                    }
                    break;
                }
            }
            return abbrhash;
        }

        @SuppressWarnings ("unused")
        @android.webkit.JavascriptInterface
        public boolean getGitDirtyFlag ()
        {
            String status = BuildConfig.GitStatus;
            String[] lines = status.split ("\n");
            for (String line : lines) {
                if (line.contains ("modified:") && !line.contains ("app.iml")) {
                    return true;
                }
            }
            return false;
        }
    }
}
