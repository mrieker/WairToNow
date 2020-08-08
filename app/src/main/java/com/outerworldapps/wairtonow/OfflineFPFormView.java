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

/**
 *  Display the Offline Flight Plan Form page
 */

package com.outerworldapps.wairtonow;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.TreeMap;

import static com.outerworldapps.wairtonow.Waypoint.*;

@SuppressLint("ViewConstructor")
public class OfflineFPFormView extends WebView implements WairToNow.CanBeMainView {
    public final static String TAG = "WairToNow";
    public final static String persistfile = "offlinefpform.txt";

    private WairToNow wairToNow;

    @SuppressLint({ "AddJavascriptInterface", "SetJavaScriptEnabled" })
    public OfflineFPFormView (WairToNow wtn)
    {
        super (wtn);
        wairToNow = wtn;

        getSettings ().setBuiltInZoomControls (true);
        getSettings ().setJavaScriptEnabled (true);
        getSettings ().setDefaultFontSize (Math.round (wairToNow.textSize / 2.0F));
        getSettings ().setDefaultFixedFontSize (Math.round (wairToNow.textSize / 2.0F));
        getSettings ().setSupportZoom (true);

        addJavascriptInterface (this, "garbo");
        addJavascriptInterface (new JavaScriptPersist (), "persist");
        addJavascriptInterface (new JavaScriptWayptDB (), "wayptdb");

        loadUrl ("file:///android_asset/offlinefpform.html");
    }

    // provide current UTC date/time
    // WebView seems to always add 5hrs to Eastern Time whether daylight savings or not
    @SuppressWarnings ("unused")
    @android.webkit.JavascriptInterface
    public String getUTCNow ()
    {
        SimpleDateFormat sdf = new SimpleDateFormat ("yyyy-MM-dd HH:mm", Locale.US);
        sdf.setTimeZone (TimeZone.getTimeZone ("UTC"));
        return sdf.format (new Date ());
    }

    @Override
    public String GetTabName () {
        return "Plan";
    }

    @Override
    public boolean IsPowerLocked () {
        return false;
    }

    @Override
    public void OpenDisplay () { }

    @Override
    public void CloseDisplay () { }

    @Override
    public void ReClicked ()
    {
        wairToNow.SetCurrentTab (wairToNow.planView);
    }

    @Override
    public View GetBackPage ()
    {
        if (canGoBack ()) {
            goBack ();
            return this;
        }
        return wairToNow.planView;
    }

    // persistent hash map available to javascript as 'persist'
    private static class JavaScriptPersist implements Runnable {
        private boolean initted;
        private boolean modified;
        private boolean queued;
        private final TreeMap<String,String> map;

        public JavaScriptPersist ()
        {
            map = new TreeMap<> ();
        }

        @SuppressWarnings ("unused")
        @android.webkit.JavascriptInterface
        public String get (String key, String def)
        {
            synchronized (map) {
                initialize ();
                String val = map.get (key);
                if (val == null) val = def;
                return val;
            }
        }

        @SuppressWarnings ("unused")
        @android.webkit.JavascriptInterface
        public String put (String key, String val)
        {
            synchronized (map) {
                initialize ();
                String old = map.put (key, val);
                //noinspection StringEquality
                if (old != val) {
                    if ((val == null) || ! val.equals (old)) {
                        modified = true;
                        if (! queued) {
                            queued = true;
                            WairToNow.wtnHandler.runDelayed (3210, this);
                        }
                    }
                }
                return old;
            }
        }

        private void initialize ()
        {
            if (! initted) {
                try {
                    BufferedReader br = new BufferedReader (new FileReader (WairToNow.dbdir + "/" + persistfile));
                    for (String line; (line = br.readLine ()) != null; ) {
                        int i = line.indexOf ('=');
                        String key = line.substring (0, i);
                        String val = line.substring (++i);
                        map.put (key, val);
                    }
                    br.close ();
                } catch (FileNotFoundException e) {
                    Lib.Ignored ();
                } catch (Exception e) {
                    Log.e (TAG, "error reading " + persistfile, e);
                }
                initted = true;
            }
        }

        // time is up, write modifications to data file
        @Override
        public void run ()
        {
            boolean modded;
            do {
                File perm = new File (WairToNow.dbdir, persistfile);
                File temp = new File (WairToNow.dbdir, persistfile + ".tmp");
                try {
                    BufferedWriter bw = new BufferedWriter (new FileWriter (temp));
                    synchronized (map) {
                        modified = false;
                        for (String key : map.keySet ()) {
                            String val = map.get (key);
                            if (val != null) {
                                val = val.replace ("\\", "\\\\").replace ("\n", "\\n");
                                bw.write (key + "=" + val + "\n");
                            }
                        }
                    }
                    bw.close ();
                    if (! temp.renameTo (perm)) {
                        throw new IOException ("rename error");
                    }
                } catch (Exception e) {
                    Log.e (TAG, "error writing " + perm.getPath (), e);
                }
                synchronized (map) {
                    modded = modified;
                    if (! modded) queued = false;
                }
            } while (modded);
        }
    }

    // give javascript access to waypoint database
    private class JavaScriptWayptDB {
        private String error;

        // get airport waypoint given its ICAO id
        // also finds airport by FAA id
        @SuppressWarnings ("unused")
        @android.webkit.JavascriptInterface
        public Object getAptByICAOId (String id)
        {
            Waypoint wp = GetAirportByIdent (id, wairToNow);
            return (wp == null) ? null : new JavaScriptWaypoint (wp);
        }

        // get error message
        @SuppressWarnings ("unused")
        @android.webkit.JavascriptInterface
        public String getError ()
        {
            return error;
        }

        // get enroute waypoint given previous waypoint, its id and next id
        @SuppressWarnings ("unused")
        @android.webkit.JavascriptInterface
        public Object getRouteWaypt (Object prevjswp, String id, String nextid)
        {
            error = null;

            // if previous is an airway, this should be the waypoint it ends on
            if (prevjswp instanceof JavaScriptAirway) {
                JavaScriptAirway prevjsaw = (JavaScriptAirway) prevjswp;
                if (! prevjsaw.nextwp.ident.equals (id)) {
                    throw new IllegalArgumentException ("wp doesn't end awy");
                }
                return new JavaScriptWaypoint (prevjsaw.nextwp);
            }

            // get previous waypoint lat/lon
            // use current position if none
            Waypoint prevwp = null;
            if (prevjswp != null) prevwp = ((JavaScriptWaypoint) prevjswp).waypt;
            double prevlat = (prevwp == null) ? wairToNow.currentGPSLat : prevwp.lat;
            double prevlon = (prevwp == null) ? wairToNow.currentGPSLon : prevwp.lon;

            // find waypoints of the given id
            // if any found, return closest one
            LinkedList<Waypoint> wps = GetWaypointsByIdent (id, wairToNow);
            double bestnm = 999999.0;
            Waypoint bestwp = null;
            for (Waypoint wp : wps) {
                double nm = Lib.LatLonDist (prevlat, prevlon, wp.lat, wp.lon);
                if (bestnm > nm) {
                    bestnm = nm;
                    bestwp = wp;
                }
            }
            if (bestwp != null) return new JavaScriptWaypoint (bestwp);

            // maybe it is an airway
            // we must have a previous and next waypoint so we know where to start and stop
            Collection<Waypoint.Airway> awys = Waypoint.Airway.GetAirways (id, wairToNow);
            if (awys == null) {
                error = "unknown airway/waypoint";
                return null;
            }
            if ((prevwp == null) || (nextid == null)) {
                error = "airway must start and end on waypoint";
                return null;
            }
            for (Waypoint.Airway awy : awys) {
                int prevfound = -1;
                int nextfound = -1;
                for (int i = 0; i < awy.waypoints.length; i ++) {
                    Waypoint awywp = awy.waypoints[i];
                    if (awywp.equals (prevwp)) {
                        prevfound = i;
                    }
                    if (awywp.ident.equals (nextid)) {
                        nextfound = i;
                    }
                }
                if ((prevfound >= 0) && (nextfound >= 0)) {
                    return new JavaScriptAirway (awy, prevfound, nextfound);
                }
            }
            error = "airway does not contain waypoints";
            return null;
        }
    }

    private class JavaScriptWaypoint {
        public Waypoint waypt;

        public JavaScriptWaypoint (Waypoint wp)
        {
            waypt = wp;
        }

        @SuppressWarnings ("unused")
        @android.webkit.JavascriptInterface
        public String getId ()
        {
            return waypt.ident;
        }

        @SuppressWarnings ("unused")
        @android.webkit.JavascriptInterface
        public String getName ()
        {
            return waypt.GetName ();
        }

        @SuppressWarnings ("unused")
        @android.webkit.JavascriptInterface
        public double distTo (Object jswp)
        {
            if (jswp instanceof JavaScriptWaypoint) {
                JavaScriptWaypoint wp = (JavaScriptWaypoint) jswp;
                return Lib.LatLonDist (waypt.lat, waypt.lon, wp.waypt.lat, wp.waypt.lon);
            }
            if (jswp instanceof JavaScriptAirway) {
                JavaScriptAirway aw = (JavaScriptAirway) jswp;
                Waypoint awywp = aw.prevwp;
                return Lib.LatLonDist (waypt.lat, waypt.lon, awywp.lat, awywp.lon);
            }
            throw new IllegalArgumentException ("bad waypoint");
        }

        @SuppressWarnings ("unused")
        @android.webkit.JavascriptInterface
        public double tcrsTo (Object jswp)
        {
            if (jswp instanceof JavaScriptWaypoint) {
                JavaScriptWaypoint wp = (JavaScriptWaypoint) jswp;
                return Lib.LatLonTC (waypt.lat, waypt.lon, wp.waypt.lat, wp.waypt.lon);
            }
            if (jswp instanceof JavaScriptAirway) {
                JavaScriptAirway aw = (JavaScriptAirway) jswp;
                Waypoint awywp = aw.prevwp;
                return Lib.LatLonTC (waypt.lat, waypt.lon, awywp.lat, awywp.lon);
            }
            throw new IllegalArgumentException ("bad waypoint");
        }
    }

    // travel along an airway from a specific waypoint to another specific waypoint
    private class JavaScriptAirway {
        private JavaScriptWaypoint[] jswpts;    // waypoints along airway, starting with prevwp, ending with nextwp inclusive
        public Waypoint nextwp;                 // waypoint where getting on the airway
        public Waypoint prevwp;                 // waypoint where getting off the airway
        private Waypoint.Airway airwy;

        // input:
        //  awy = airway
        //  pi  = index in airway.waypoints[] where we are getting on airway
        //  ni  = index in airway.waypoints[] where we are getting off airway
        public JavaScriptAirway (Waypoint.Airway awy, int pi, int ni)
        {
            airwy  = awy;
            prevwp = awy.waypoints[pi];
            nextwp = awy.waypoints[ni];

            if (ni >= pi) {
                jswpts = new JavaScriptWaypoint[ni-pi+1];
                for (int i = 0; i <= ni - pi; i ++) {
                    jswpts[i] = new JavaScriptWaypoint (awy.waypoints[pi+i]);
                }
            } else {
                jswpts = new JavaScriptWaypoint[pi-ni+1];
                for (int i = 0; i <= pi - ni; i ++) {
                    jswpts[i] = new JavaScriptWaypoint (awy.waypoints[pi-i]);
                }
            }
        }

        @SuppressWarnings ("unused")
        @android.webkit.JavascriptInterface
        public String getId ()
        {
            return airwy.ident + "." + prevwp.ident;
        }

        @SuppressWarnings ("unused")
        @android.webkit.JavascriptInterface
        public double distTo (Object jswp)
        {
            return -1.0;
        }

        @SuppressWarnings ("unused")
        @android.webkit.JavascriptInterface
        public Object getWaypt (int i)
        {
            if ((i < 0) || (i >= jswpts.length)) return null;
            return jswpts[i];
        }
    }
}
