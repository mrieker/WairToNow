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

package com.outerworldapps.wairtonow;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.ZipFile;

/**
 * Display airport detail page (AFD-like data).
 */
@SuppressLint("ViewConstructor")
public class AptDetailsView extends WebView
        implements WairToNow.CanBeMainView {
    public final static String TAG = "WairToNow";

    private View priorView;
    private WairToNow wairToNow;
    private Waypoint.Airport airport;

    @SuppressLint({ "AddJavascriptInterface", "SetJavaScriptEnabled" })
    public AptDetailsView (WairToNow wtn, final Waypoint.Airport apt)
    {
        super (wtn);
        wairToNow = wtn;
        airport   = apt;
        priorView = wairToNow.currentTabButton.view;

        getSettings ().setBuiltInZoomControls (true);
        getSettings ().setJavaScriptEnabled (true);
        getSettings ().setDefaultFontSize (Math.round (wairToNow.textSize / 2.0F));
        getSettings ().setDefaultFixedFontSize (Math.round (wairToNow.textSize / 2.0F));
        getSettings ().setSupportZoom (true);
        addJavascriptInterface (this, "advjso");

        loadUrl ("file:///android_asset/aptdetail.html");
    }

    // implement WairToNow.CanBeMainView
    public String GetTabName ()
    {
        return wairToNow.currentTabButton.ident;
    }
    public void OpenDisplay () { }
    @Override  // CanBeMainView
    public void OrientationChanged () { }
    public void CloseDisplay () { }
    public void ReClicked ()
    {
        wairToNow.SetCurrentTab (priorView);
    }
    public View GetBackPage ()
    {
        return priorView;
    }
    public boolean IsPowerLocked ()
    {
        return false;
    }

    // script debugging
    //   advjso.showLogcat ('pageLoaded*:A');
    @SuppressWarnings ("unused")
    @JavascriptInterface
    public void showLogcat (String s)
    {
        Log.d (TAG, "advjso: " + s);
    }

    @SuppressWarnings ("unused")
    @JavascriptInterface
    public String getAFDData ()
    {
        try {
            ZipFile zf = wairToNow.maintView.getCurentStateZipFile (airport.state);
            if (zf == null) return "";
            try {
                InputStream zis = zf.getInputStream (zf.getEntry (airport.faaident + ".html"));
                try {
                    StringBuilder sb = new StringBuilder ();
                    BufferedReader zisrdr = new BufferedReader (new InputStreamReader (zis));
                    for (String line; (line = zisrdr.readLine ()) != null; ) {
                        if (line.startsWith ("apt['") || line.startsWith ("rwps['")) {
                            sb.append (line);
                            sb.append ('\n');
                        }
                    }
                    return sb.toString ();
                } finally {
                    zis.close ();
                }
            } finally {
                zf.close ();
            }
        } catch (Exception e) {
            Log.w (TAG, "error reading " + airport.state + " zip file " + airport.faaident + ".html", e);
        }
        return "";
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public String getAptTime (String tzname)
    {
        SimpleDateFormat sdf = new SimpleDateFormat ("MMM d, yyyy kk:mm", Locale.US);
        sdf.setTimeZone (TimeZone.getTimeZone (tzname));
        return sdf.format (new Date ());
    }
}
