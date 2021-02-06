//    Copyright (C) 2015, Mike Rieker, Beverly, MA USA
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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

/**
 * Display help page in a web view.
 * Allows internal links to other resource pages
 * and allows external links to web pages.
 */
@SuppressLint("ViewConstructor")
public class HelpView extends WebView
        implements WairToNow.CanBeMainView {
    public final static String TAG = "WairToNow";
    public int barfmeout;
    private WairToNow wairToNow;

    @SuppressLint({ "AddJavascriptInterface", "SetJavaScriptEnabled" })
    public HelpView (WairToNow wtn)
    {
        super (wtn);
        wairToNow = wtn;

        getSettings ().setBuiltInZoomControls (true);
        getSettings ().setJavaScriptEnabled (true);
        getSettings ().setDefaultFontSize (Math.round (wairToNow.textSize / 2.0F));
        getSettings ().setDefaultFixedFontSize (Math.round (wairToNow.textSize / 2.0F));
        getSettings ().setSupportZoom (true);
        addJavascriptInterface (new JavaScriptObject (), "hvjso");

        ReClicked ();
    }

    // implement WairToNow.CanBeMainView
    public String GetTabName ()
    {
        return "Help";
    }
    public void OpenDisplay () { }
    @Override  // CanBeMainView
    public void OrientationChanged () { }
    public void CloseDisplay () { }
    public void ReClicked ()
    {
        loadUrl ("file:///android_asset/help.html");
    }
    public View GetBackPage ()
    {
        goBack ();
        return this;
    }

    @Override  // CanBeMainView
    public boolean IsPowerLocked ()
    {
        return false;
    }

    /**
     * Accessed via javascript in the internal .HTML files.
     */
    private class JavaScriptObject {

        @SuppressWarnings ("unused")
        @JavascriptInterface
        public String getBaseUrl ()
        {
            return MaintView.dldir;
        }

        @SuppressWarnings ("unused")
        @JavascriptInterface
        public String getVersionName ()
        {
            try {
                PackageInfo pInfo = wairToNow.getPackageManager ().getPackageInfo (wairToNow.getPackageName (), 0);
                return pInfo.versionName;
            } catch (PackageManager.NameNotFoundException nnfe) {
                return "";
            }
        }

        @SuppressWarnings ("unused")
        @JavascriptInterface
        public int getVersionCode ()
        {
            try {
                PackageInfo pInfo = wairToNow.getPackageManager ().getPackageInfo (wairToNow.getPackageName (), 0);
                return pInfo.versionCode;
            } catch (PackageManager.NameNotFoundException nnfe) {
                return -1;
            }
        }

        @SuppressWarnings ("unused")
        @JavascriptInterface
        public String getGithubLink ()
        {
            String fullhash = BuildConfig.GitHash;
            String abbrhash = fullhash.substring (0, 7);
            String status = BuildConfig.GitStatus;
            String[] lines = status.split ("\n");
            for (String line : lines) {
                line = line.trim ();
                if (line.startsWith ("# On branch ")) {
                    if (line.equals ("# On branch github")) {
                        String link = "https://github.com/mrieker/WairToNow/commit/" + fullhash;
                        return "<A HREF=\"" + link + "\">" + abbrhash + "</A>";
                    }
                    break;
                }
            }
            return abbrhash;
        }

        @SuppressWarnings ("unused")
        @JavascriptInterface
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

        @SuppressWarnings ("unused")
        @JavascriptInterface
        public void imDirty ()
        {
            wairToNow.runOnUiThread (new Runnable () {
                @Override
                public void run ()
                {
                    barfmeout = 50 / barfmeout;
                }
            });
        }

        @SuppressWarnings ("unused")
        @android.webkit.JavascriptInterface
        public int getSdkVersion ()
        {
            return Build.VERSION.SDK_INT;
        }

        @SuppressWarnings ("unused")
        @JavascriptInterface
        @NonNull
        public String getManualGeoRefs (String categ)
        {
            try {
                switch (categ) {

                    // automatically generated by server (ReadEuroIAPPng.cs)
                    case "auto": {
                        int expdate = MaintView.GetLatestPlatesExpDate ();
                        if (expdate <= 0) return "";
                        String dbname = "nobudb/plates_" + expdate + ".db";
                        SQLiteDBs sqldb = SQLiteDBs.open (dbname);
                        if (sqldb == null) return "";
                        Cursor result = sqldb.query (true, "iapgeorefs3",
                                new String[] { "gr_icaoid", "gr_plate" },
                                "gr_state LIKE 'EUR-%'", null, null, null, "gr_icaoid,gr_plate", null);
                        try {
                            if (!result.moveToFirst ()) return "";
                            StringBuilder sb = new StringBuilder ();
                            sb.append ("{\"expdate\":");
                            sb.append (expdate);
                            sb.append (",\"airports\":[");
                            boolean firstapt = true;
                            boolean firstplt = true;
                            String lasticaoid = "";
                            do {
                                String icaoid = result.getString (0);
                                String plateid = result.getString (1);
                                if (! lasticaoid.equals (icaoid)) {
                                    if (! firstplt) sb.append ("]}");
                                    if (! firstapt) sb.append (',');
                                    sb.append ("{\"icaoid\":\"");
                                    sb.append (icaoid);
                                    sb.append ("\",\"plates\":[");
                                    lasticaoid = icaoid;
                                    firstplt = true;
                                    firstapt = false;
                                }
                                if (! firstplt) sb.append (',');
                                sb.append ("{\"id\":\"");
                                sb.append (result.getString (1));
                                sb.append ("\"}");
                                firstplt = false;
                            } while (result.moveToNext ());
                            sb.append ("]}]}");
                            return sb.toString ();
                        } finally {
                            result.close ();
                        }
                    }

                    // manual georef contributed (by self or by others)
                    case "cont": {
                        SQLiteDBs sqldb = SQLiteDBs.open ("manualiapgeorefs.db");
                        if (sqldb == null) return "";
                        if (! sqldb.tableExists ("contributions")) return "";
                        Cursor result = sqldb.query (true, "contributions",
                                new String[] { "co_icaoid", "co_plateid", "co_effdate", "co_entdate", "co_username" },
                                null, null, null, null, "co_icaoid,co_plateid,co_effdate,co_entdate,co_username", null);
                        try {
                            if (!result.moveToFirst ()) return "";
                            StringBuilder sb = new StringBuilder ();
                            sb.append ("{\"plates\":[");
                            boolean first = true;
                            do {
                                if (! first) sb.append (',');
                                sb.append ("{\"icaoid\":\"");
                                sb.append (result.getString (0));
                                sb.append ("\",\"plate\":\"");
                                sb.append (result.getString (1));
                                sb.append ("\",\"effdate\":");
                                sb.append (result.getInt (2));
                                sb.append (",\"entdate\":");
                                sb.append (result.getInt (3));
                                sb.append (",\"username\":\"");
                                sb.append (result.getString (4));
                                sb.append ("\"}");
                                first = false;
                            } while (result.moveToNext ());
                            sb.append ("]}");
                            return sb.toString ();
                        } finally {
                            result.close ();
                        }
                    }

                    // plates manually georefd on this device
                    case "self": {
                        SQLiteDBs sqldb = SQLiteDBs.open ("manualiapgeorefs.db");
                        if (sqldb == null) return "";
                        if (! sqldb.tableExists ("maniapgeorefs")) return "";
                        boolean contable = sqldb.tableExists ("contributions");
                        Cursor result = sqldb.query (true, "maniapgeorefs",
                                new String[] { "mg_icaoid", "mg_plate", "mg_effdate" },
                                null, null, null, null, "mg_icaoid,mg_plate,mg_effdate", null);
                        try {
                            if (!result.moveToFirst ()) return "<P>- plates you manually georeference will be shown here</P>";
                            StringBuilder sb = new StringBuilder ();
                            sb.append ("{\"plates\":[");
                            boolean first = true;
                            do {
                                if (! first) sb.append (',');
                                String icaoid = result.getString (0);
                                String plateid = result.getString (1);
                                int effdate = result.getInt (2);
                                sb.append ("{\"icaoid\":\"");
                                sb.append (icaoid);
                                sb.append ("\",\"plate\":\"");
                                sb.append (plateid);
                                sb.append ("\",\"effdate\":");
                                sb.append (effdate);
                                if (contable) {
                                    Cursor rescont = sqldb.query ("contributions",
                                            new String[] { "co_entdate", "co_username" },
                                            "co_icaoid=? AND co_plateid=? AND co_effdate=" + effdate,
                                            new String[] { icaoid, plateid }, null, null,
                                            "co_entdate,co_username", null);
                                    try {
                                        if (rescont.moveToFirst ()) {
                                            sb.append (",\"contribs\":[");
                                            boolean firstt = true;
                                            do {
                                                if (! firstt) sb.append (',');
                                                sb.append ("{\"entdate\":");
                                                sb.append (rescont.getInt (0));
                                                sb.append (",\"username\":\"");
                                                sb.append (rescont.getString (1));
                                                sb.append ("\"}");
                                                firstt = false;
                                            } while (rescont.moveToNext ());
                                            sb.append ("]");
                                        }
                                    } finally {
                                        rescont.close ();
                                    }
                                }
                                sb.append ('}');
                                first = false;
                            } while (result.moveToNext ());
                            sb.append ("]}");
                            return sb.toString ();
                        } finally {
                            result.close ();
                        }
                    }
                }
            } catch (Exception e) {
                Log.e (TAG, "exception reading maniapgeorefs.db", e);
            } finally {
                SQLiteDBs.CloseAll ();
            }
            return "";
        }
    }
}
