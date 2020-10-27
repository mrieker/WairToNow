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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.zip.ZipFile;

/**
 * Each individual waypoint in the database (airport, localizer, navaid, fix)
 */
public abstract class Waypoint {
    public interface Selected {
        void wpSeld (Waypoint wp);
        void noWPSeld ();
    }

    public final static String TAG = "WairToNow";
    public final static double ELEV_UNKNOWN = 999999.0;
    public final static int   VAR_UNKNOWN  = 999999;

    public final static ArrayList<Class<? extends Waypoint>> wpclasses = GetWaypointClasses ();

    public final static Waypoint[] nullarray = new Waypoint[0];

    public double lat;                   // degrees north
    public double lon;                   // degrees east
    public double elev = ELEV_UNKNOWN;   // feet MSL
    public int    magvar = VAR_UNKNOWN;  // magnetic variation (+=West; -=East)
    public String ident;                 // for airports, ICAO identifier

    private String typeabbr;

    private final static String[] columns_apt_desc = new String[] { "apt_desc" };
    private final static String[] columns_kw_rowid = new String[] { "kw_rowid" };
    private final static String[] columns_pl_descrip_pl_filename = new String[] { "pl_descrip", "pl_filename" };

    private static ArrayList<Class<? extends Waypoint>> GetWaypointClasses ()
    {
        ArrayList<Class<? extends Waypoint>> al = new ArrayList<> (5);
        al.add (Airport.class);
        al.add (Fix.class);
        al.add (Intersection.class);
        al.add (Localizer.class);
        al.add (Navaid.class);
        return al;
    }

    /**
     * Display a text dialog for user to enter a waypoint ident.
     * @param wtn = what app this is part of
     * @param title = title for the dialog box
     * @param nearlat = nearby latitude
     * @param nearlon = nearby longitude
     * @param nearapt = nearby airport (or null)
     * @param oldid = initial value for dialog box
     * @param wpsel = what to call when waypoint selection complete
     * @param ermsg = error message to display (or null for none)
     */
    @SuppressLint("SetTextI18n")
    public static void ShowWaypointDialog (
            final WairToNow wtn,
            final String title,
            final double nearlat,
            final double nearlon,
            final Airport nearapt,
            final String oldid,
            final Selected wpsel,
            final String ermsg)
    {
        // set up a text box to enter the new identifier in
        final EditText ident = new EditText (wtn);
        ident.setEms (10);
        ident.setInputType (InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        ident.setSingleLine ();

        // set up a text box to enter a RNAV offset radial
        final EditText rnavradl = new EditText (wtn);
        rnavradl.setEms (3);
        rnavradl.setInputType (InputType.TYPE_CLASS_NUMBER);
        rnavradl.setSingleLine ();
        rnavradl.addTextChangedListener (new TextWatcher () {
            private String before;

            @Override
            public void beforeTextChanged (CharSequence charSequence, int i, int i1, int i2)
            {
                before = charSequence.toString ();
            }

            @Override
            public void onTextChanged (CharSequence charSequence, int i, int i1, int i2)
            { }

            @Override
            public void afterTextChanged (Editable editable)
            {
                String after = editable.toString ();
                if (!after.equals ("")) {
                    try {
                        double value = Double.parseDouble (after);
                        if ((value >= 0.0) && (value <= 360.0)) return;
                    } catch (NumberFormatException nfe) {
                        Lib.Ignored ();
                    }
                    editable.replace (0, after.length (), before);
                }
            }
        });

        // set up a spinner to say mag/true for RNAV offset radial
        final MagTrueSpinner magTrue = new MagTrueSpinner (wtn);

        // set up a text box to enter RNAV offset distance
        final EditText rnavdist = new EditText (wtn);
        rnavdist.setEms (3);
        rnavdist.setInputType (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        rnavdist.setSingleLine ();
        rnavdist.addTextChangedListener (new TextWatcher () {
            private String before;

            @Override
            public void beforeTextChanged (CharSequence charSequence, int i, int i1, int i2)
            {
                before = charSequence.toString ();
            }

            @Override
            public void onTextChanged (CharSequence charSequence, int i, int i1, int i2)
            { }

            @Override
            public void afterTextChanged (Editable editable)
            {
                String after = editable.toString ();
                if (!after.equals ("")) {
                    try {
                        double value = Double.parseDouble (after);
                        if ((value >= 0.0) && (value <= 999.0)) return;
                    } catch (NumberFormatException nfe) {
                        Lib.Ignored ();
                    }
                    editable.replace (0, after.length (), before);
                }
            }
        });

        // fill in initial values for those boxes from given oldid string
        boolean hasRNavOffset = false;
        try {
            RNavParse rnavParse = new RNavParse (oldid);
            ident.setText (rnavParse.baseident);
            if (rnavParse.rnavsuffix != null) {
                rnavradl.setText (Lib.DoubleNTZ (rnavParse.rnavradial));
                magTrue.setMag (rnavParse.magnetic);
                rnavdist.setText (Lib.DoubleNTZ (rnavParse.rnavdistance));
                hasRNavOffset = true;
            }
        } catch (Exception e) {
            ident.setText (oldid);
        }

        // put those boxes in a linear layout
        final LinearLayout llh = new LinearLayout (wtn);
        llh.setOrientation (LinearLayout.HORIZONTAL);
        llh.addView (ident);
        if (hasRNavOffset) {

            // already has RNAV offset, display the RNAV offset boxes too
            addRNavOffsetBoxes (llh, rnavradl, magTrue, rnavdist);
        } else {

            // no RNAV offset, give them a button to show the boxes if they want
            Button rnavOffsetButton = new Button (wtn);
            rnavOffsetButton.setText ("RNAV>>");
            rnavOffsetButton.setOnClickListener (new View.OnClickListener () {
                @Override
                public void onClick (View view)
                {
                    llh.removeAllViews ();
                    llh.addView (ident);
                    addRNavOffsetBoxes (llh, rnavradl, magTrue, rnavdist);
                }
            });
            llh.addView (rnavOffsetButton);
        }

        // maybe display error message below input boxes
        LinearLayout ll = llh;
        if (ermsg != null) {
            TextView ertxt = new TextView (wtn);
            wtn.SetTextSize (ertxt);
            ertxt.setText (ermsg);

            LinearLayout llv = new LinearLayout (wtn);
            llv.setOrientation (LinearLayout.VERTICAL);
            llv.addView (llh);
            llv.addView (ertxt);
            ll = llv;
        }

        // wrap that all in an horizontal scroll in case of small screen
        DetentHorizontalScrollView sv = new DetentHorizontalScrollView (wtn);
        wtn.SetDetentSize (sv);
        sv.addView (ll);

        // set up and display an alert dialog box to enter new identifier
        AlertDialog.Builder adb = new AlertDialog.Builder (wtn);
        adb.setTitle (title);
        adb.setView (sv);
        adb.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
            @Override
            public void onClick (DialogInterface dialogInterface, int i) {
                waypointEntered (wtn, title, wpsel, nearlat, nearlon, nearapt,
                        ident, rnavradl, magTrue, rnavdist);
            }
        });
        adb.setNeutralButton ("OFF", new DialogInterface.OnClickListener () {
            @Override
            public void onClick (DialogInterface dialogInterface, int i) {
                ident.setText ("");
                waypointEntered (wtn, title, wpsel, nearlat, nearlon, nearapt,
                        ident, rnavradl, magTrue, rnavdist);
            }
        });
        adb.setNegativeButton ("Cancel", new DialogInterface.OnClickListener () {
            @Override
            public void onClick (DialogInterface dialogInterface, int i) {
                wpsel.noWPSeld ();
            }
        });
        final AlertDialog ad = adb.show ();

        // set up listener for the DONE keyboard key
        ident.setOnEditorActionListener (new TextView.OnEditorActionListener () {
            @Override
            public boolean onEditorAction (TextView textView, int i, KeyEvent keyEvent)
            {
                if (i == EditorInfo.IME_ACTION_DONE) {
                    Lib.dismiss (ad);
                    waypointEntered (wtn, title, wpsel, nearlat, nearlon, nearapt,
                            ident, rnavradl, magTrue, rnavdist);
                }
                return false;
            }
        });
    }

    private static void addRNavOffsetBoxes (LinearLayout ll, EditText rnavradl, MagTrueSpinner magTrue, EditText rnavdist)
    {
        Context ctx = ll.getContext ();
        ll.addView (new TextViewString (ctx, "["));
        ll.addView (rnavradl);
        ll.addView (new TextViewString (ctx, "\u00B0"));
        ll.addView (magTrue);
        ll.addView (new TextViewString (ctx, "@"));
        ll.addView (rnavdist);
        TextView nm = new TextViewString (ctx, "nm");
        nm.setSingleLine ();
        ll.addView (nm);
    }

    private static void waypointEntered (
            WairToNow wtn,
            String title,
            Selected wpsel,
            double nearlat,
            double nearlon,
            Airport nearapt,
            EditText ident,
            EditText rnavradl,
            MagTrueSpinner magTrue,
            EditText rnavdist)
    {
        // if ident entered is blank, tell caller that user wants no ident selected
        String newid = ident.getText ().toString ().replace (" ", "").toUpperCase (Locale.US);
        if (newid.equals ("")) {
            wpsel.wpSeld (null);
        } else {

            // something in ident box, look it up in waypoint database
            LinkedList<Waypoint> wplist = GetWaypointsByIdent (newid, wtn);

            // look for the one so named that is closest to given lat/lon
            Waypoint bestwp = null;
            double bestnm = 999999.0;
            for (Waypoint wp : wplist) {
                double nm = Lib.LatLonDist (wp.lat, wp.lon, nearlat, nearlon);
                if (bestnm > nm) {
                    bestnm = nm;
                    bestwp = wp;
                }
            }

            // if no such waypoint exists, maybe it is an airport's runway number
            if ((bestwp == null) && (nearapt != null)) {
                HashMap<String,Runway> rwys = nearapt.GetRunways ();
                bestwp = newid.startsWith ("RW") ? rwys.get (newid.substring (2)) : rwys.get (newid);
            }

            // get RNAV offset info
            String rnavradlstr = rnavradl.getText ().toString ();
            boolean magnetic   = magTrue.getMag ();
            String rnavdiststr = rnavdist.getText ().toString ();
            String rnavsuffix  = rnavradlstr + (magnetic ? "@" : "T@") + rnavdiststr;

            // if waypoint not found, re-prompt
            if (bestwp == null) {
                if (!rnavradlstr.equals ("") && !rnavdiststr.equals ("")) {
                    newid += "[" + rnavsuffix;
                }
                ShowWaypointDialog (wtn, title, nearlat, nearlon, nearapt, newid, wpsel,
                        "Waypoint not found\n" +
                        "Airport ICAO ID (eg KBOS)\n" +
                        "VOR or NDB ID (eg BOS)\n" +
                        "Loc/ILS ID (eg IBOS)\n" +
                        "Fix ID (eg BOSOX)\n" +
                        "Runway numbers (eg BOS.04R)\n" +
                        "Synthetic ILS/DME (eg IBOS.04R)");
                return;
            }

            // if a RNAV distance given, wrap the waypoint with RNAV radial/distance info
            if (!rnavradlstr.equals ("") || !rnavdiststr.equals ("")) {
                try {
                    double rnavradlbin = Double.parseDouble (rnavradlstr);
                    double rnavdistbin = Double.parseDouble (rnavdiststr);
                    if (rnavdistbin != 0.0) {
                        rnavsuffix = Lib.DoubleNTZ (rnavradlbin) + (magnetic ? "@" : "T@") +
                                Lib.DoubleNTZ (rnavdistbin);
                        bestwp = new RNavOffset (bestwp, rnavdistbin, rnavradlbin, magnetic, rnavsuffix);
                    }
                } catch (NumberFormatException nfe) {
                    newid += "[" + rnavsuffix;
                    ShowWaypointDialog (wtn, title, nearlat, nearlon, nearapt, newid, wpsel,
                            "Bad RNAV radial and/or distance\n" +
                            "Radial is number 0 to 360\n" +
                            "Distance is number gt 0");
                    return;
                }
            }

            // tell caller that a waypoint has been selected,
            // possibly wrapped with a RNavOffset.
            wpsel.wpSeld (bestwp);
        }
    }

    /**
     * Find waypoints by identifier (airport icaoid, localizer ident, navaid identifier, fix name).
     * May include a RNAV offset, ie, bearing and distance.
     */
    public static LinkedList<Waypoint> GetWaypointsByIdent (String ident, WairToNow wairToNow)
    {
        LinkedList<Waypoint> wplist = new LinkedList<> ();

        ident = ident.toUpperCase (Locale.US);

        /*
         * Maybe we have aptid.rwyid or aptid.RWrwyid
         * aptid can be faaid or icaoid
         *
         * We can also have I-aptid.rwid or I-aptid.RWrwyid for synthetic ILS/DME.
         */
        int i = ident.indexOf ('.');
        if (i >= 0) {
            String aptid = ident.substring (0, i);
            String rwyid = ident.substring (++ i);
            if (rwyid.startsWith ("RW")) rwyid = rwyid.substring (2);
            Airport aptwp = GetAirportByIdent (aptid, wairToNow);
            if (aptwp != null) {
                HashMap<String,Runway> rwys = aptwp.GetRunways ();
                Runway rwywp = rwys.get (rwyid);
                if (rwywp != null) wplist.addLast (rwywp);
            }
            if (aptid.startsWith ("I") || aptid.startsWith ("I-")) {
                aptid = aptid.substring (1);
                if (aptid.startsWith ("-")) aptid = aptid.substring (1);
                aptwp = GetAirportByIdent (aptid, wairToNow);
                if (aptwp != null) {
                    HashMap<String,Runway> rwys = aptwp.GetRunways ();
                    Runway rwywp = rwys.get (rwyid);
                    if (rwywp != null) wplist.addLast (rwywp.GetSynthLoc ());
                }
            }
        }

        /*
         * If there is a [rnavoffset on the end, get ident then add that offset.
         */
        RNavParse rnavParse;
        try {
            rnavParse = new RNavParse (ident);
        } catch (Exception e) {
            rnavParse = null;
        }
        if ((rnavParse != null) && (rnavParse.rnavsuffix != null)) {

            /*
             * Look for all instances of the base identifier waypoint.
             */
            LinkedList<Waypoint> basewps = GetWaypointsByIdent (rnavParse.baseident, wairToNow);

            /*
             * Add the RNAV offset to each of those for the resultant value.
             */
            for (Waypoint basewp : basewps) {
                Waypoint rnavwp = rnavParse.makeWaypoint (basewp);
                wplist.addLast (rnavwp);
            }
        } else {

            /*
             * Accept format /<lat>/<lon> as generated by ParseCifp.java on the server.
             */
            if (ident.startsWith ("/")) {
                i = ident.indexOf ("/", 1);
                SelfLL selfll = new SelfLL ();
                selfll.ident = ident;
                selfll.lat = Double.parseDouble (ident.substring (1, i));
                selfll.lon = Double.parseDouble (ident.substring (++ i));
                wplist.addLast (selfll);
                return wplist;
            }

            /*
             * Otherwise, just get the waypoint as named.
             */
            SQLiteDBs sqldb = openWayptDB (wairToNow);
            if (sqldb != null) {
                try {

                    /*
                     * Search through each of the waypoint tables to get all matches.
                     */
                    for (Class<? extends Waypoint> wpclass : wpclasses) {
                        String dbtable  = (String) wpclass.getField ("dbtable").get (null);
                        String dbkeyid  = (String) wpclass.getField ("dbkeyid").get (null);
                        String[] dbcols = (String[]) wpclass.getField ("dbcols").get (null);
                        Cursor result = sqldb.query (
                                dbtable, dbcols,
                                dbkeyid + "=?", new String[] { ident },
                                null, null, null, null);
                        try {
                            if (result.moveToFirst ()) {
                                do {
                                    Constructor<? extends Waypoint> ctor = wpclass.getConstructor (Cursor.class, WairToNow.class);
                                    Waypoint wpe = ctor.newInstance (result, wairToNow);
                                    wplist.addLast (wpe);
                                } while (result.moveToNext ());
                            }
                        } finally {
                            result.close ();
                        }
                    }
                } catch (Exception e) {
                    Log.e (TAG, "error reading " + sqldb.mydbname, e);
                }
                if (wplist.isEmpty () && ident.startsWith ("I") && !ident.startsWith ("I-")) {
                    return GetWaypointsByIdent ("I-" + ident.substring (1), wairToNow);
                }
            }
        }
        return wplist;
    }

    /**
     * Find airport by identifier (icaoid or faaid).
     */
    public static Airport GetAirportByIdent (String ident, WairToNow wtn)
    {
        SQLiteDBs sqldb = openWayptDB (wtn);
        if (sqldb != null) {
            try {
                Cursor result = sqldb.query (
                        "airports", Airport.dbcols,
                        "apt_icaoid=? OR apt_faaid=?", new String[] { ident, ident },
                        null, null, null, null);
                try {
                    if (result.moveToFirst ()) {
                        return new Airport (result, wtn);
                    }
                } finally {
                    result.close ();
                }
            } catch (Exception e) {
                Log.e (TAG, "error reading " + sqldb.mydbname, e);
            }
        }
        return null;
    }

    /**
     * Scan database for all waypoints matching the given key.
     * @param key = keywords with all redundant spaces removed and converted to upper case
     */
    public static String NormalizeKey (String key)
    {
        // matches InsertMultipleKeys in server MakeWaypoints.cs
        StringBuilder sb = new StringBuilder (key.length ());
        for (int i = 0; i < key.length (); i ++) {
            char c = key.charAt (i);
            if ((c >= 'a') && (c <= 'z')) {
                char u = (char) (c - 'a' + 'A');
                sb.append (u);
                continue;
            }
            if (((c >= '0') && (c <= '9')) ||
                ((c >= 'A') && (c <= 'Z')) ||
                (c == '_')) {
                sb.append (c);
                continue;
            }
            if ((sb.length () > 0) && (sb.charAt (sb.length () - 1) != ' ')) {
                sb.append (' ');
            }
        }
        String out = sb.toString ().trim ();
        if (out.startsWith ("I ") && key.toUpperCase ().startsWith ("I-")) {
            out = "I-" + out.substring (2);
        }
        return out;
    }

    public static LinkedList<Waypoint> GetWaypointsMatchingKey (String key, WairToNow wtn)
    {
        String[] keys = key.split (" ");
        LinkedList<Waypoint> matches = new LinkedList<> ();

        /*
         * Scan database for match on first keyword in given string.
         */
        SQLiteDBs sqldb = openWayptDB (wtn);
        if (sqldb != null) {
            try {
                String kw = keys[0];

                /*
                 * Read list of airports, localizers, navaids and fixes into matches list.
                 */
                for (Class<? extends Waypoint> wpclass : wpclasses) {
                    String dbkytbl = (String) wpclass.getField ("dbkytbl").get (null);
                    if (dbkytbl == null) continue;
                    Cursor result = sqldb.query (
                            dbkytbl, columns_kw_rowid,
                            "kw_key=?", new String[] { kw },
                            null, null, null, null);
                    try {
                        if (result.moveToFirst ()) do {
                            long rowid = result.getLong (0);
                            String dbtable = (String) wpclass.getField ("dbtable").get (null);
                            String[] dbcols = (String[]) wpclass.getField ("dbcols").get (null);
                            Cursor result2 = sqldb.query (
                                    dbtable, dbcols,
                                    "ROWID=?", new String[] { Long.toString (rowid) },
                                    null, null, null, null);
                            try {
                                if (result2.moveToFirst ()) {
                                    Constructor<? extends Waypoint> ctor = wpclass.getConstructor (Cursor.class, WairToNow.class);
                                    Waypoint wp = ctor.newInstance (result2, wtn);
                                    matches.add (wp);
                                }
                            } finally {
                                result2.close ();
                            }
                        } while (result.moveToNext ());
                    } finally {
                        result.close ();
                    }
                }
            } catch (Exception e) {
                Log.e (TAG, "error reading " + sqldb.mydbname, e);
            }
        } else {
            Log.w (TAG, "no database file");
        }

        /*
         * Make sure all the matches match all the given keywords.
         * Eg, keys=SPRINGFIELD MA
         * and matches=SPRINGFIELD KY, SPRINGFIELD MA, SPRINGFIELD MO
         * just allow SPRINGFIELD MA
         */
        for (Iterator<Waypoint> wpit = matches.iterator (); wpit.hasNext ();) {
            Waypoint wp = wpit.next ();
            for (String kw : keys) {
                if (!kw.equals ("") && !wp.MatchesKeyword (kw)) {
                    wpit.remove ();
                    break;
                }
            }
        }

        return matches;
    }

    /**
     * Parse a waypoint[rnavdist/rnavradial (true)
     * or waypoint[rnavradial@rnavdist string (magnetic)
     */
    public static class RNavParse {
        public String baseident;     // waypoint
        public String rnavsuffix;    // [... or null if missing
        public double rnavdistance;   // parsed distance
        public double rnavradial;     // parsed radial
        public boolean magnetic;     // true: magnetic; false: true

        public RNavParse (String ident)
        {
            int i = ident.lastIndexOf ('[');
            if (i < 0) {
                baseident = ident;
                return;
            }
            baseident = ident.substring (0, i).trim ();
            String suffix = ident.substring (++ i).replace (" ", "").toUpperCase (Locale.US);
            i = suffix.indexOf ('/');
            if (i >= 0) {
                String diststr = suffix.substring (0, i);
                String radlstr = suffix.substring (++ i);
                if (parseDistanceRadial (diststr, radlstr, false)) {
                    StringBuilder sb = new StringBuilder ();
                    sb.append (Lib.DoubleNTZ (rnavdistance));
                    sb.append ('/');
                    sb.append (Lib.DoubleNTZ (rnavradial));
                    if (magnetic) sb.append ('M');
                    rnavsuffix = sb.toString ();
                }
                return;
            }
            i = suffix.indexOf ('@');
            if (i >= 0) {
                String radlstr = suffix.substring (0, i);
                String diststr = suffix.substring (++ i);
                if (parseDistanceRadial (diststr, radlstr, true)) {
                    StringBuilder sb = new StringBuilder ();
                    sb.append (Lib.DoubleNTZ (rnavradial));
                    if (!magnetic) sb.append ('T');
                    sb.append ('@');
                    sb.append (Lib.DoubleNTZ (rnavdistance));
                    rnavsuffix = sb.toString ();
                }
                return;
            }
            throw new NumberFormatException ("unknown rnav offset " + suffix);
        }

        private boolean parseDistanceRadial (String diststr, String radlstr, boolean mag)
        {
            magnetic = mag;
            rnavdistance = Double.parseDouble (diststr);
            if (rnavdistance <= 0) {
                rnavsuffix = null;
                return false;
            }
            if (radlstr.endsWith ("M")) {
                magnetic = true;
                radlstr = radlstr.substring (0, radlstr.length () - 1);
            } else if (radlstr.endsWith ("T")) {
                magnetic = false;
                radlstr = radlstr.substring (0, radlstr.length () - 1);
            }
            rnavradial = Double.parseDouble (radlstr);
            while (rnavradial > 360.0) rnavradial -= 360.0;
            while (rnavradial <   0.0) rnavradial += 360.0;
            return true;
        }

        public Waypoint makeWaypoint (Waypoint basewp)
        {
            if (!basewp.ident.equals (baseident)) throw new IllegalArgumentException ("basewp");
            if ((rnavsuffix == null) || (rnavdistance == 0)) return basewp;
            return new RNavOffset (basewp, rnavdistance, rnavradial, magnetic, rnavsuffix);
        }
    }

    // see if two waypoints are equal
    @Override
    public boolean equals (Object o)
    {
        if (!(o instanceof Waypoint)) return false;
        Waypoint w = (Waypoint) o;
        return w.ident.equals (ident) && (w.lat == lat) && (w.lon == lon);
    }

    // eg, "AIRPORT", "NAVAID:NDB", "LOCALIZER:ILS/DME", "FIX", etc.
    public abstract String GetType ();

    // eg, "BEVERLY RGNL", "LAWRENCE", etc.
    public abstract String GetName ();

    // type string used on Street tiles
    public String GetTypeAbbr ()
    {
        if (typeabbr == null) {
            String type = "(" + GetType ().toLowerCase (Locale.US) + ")";
            typeabbr = type.replace ("(localizer:", "(").replace ("(navaid:", "(");
        }
        return typeabbr;
    }

    // full-page description
    @SuppressLint("AddJavascriptInterface")
    public void GetDetailViews (WaypointView wayview, WebView webview)
    {
        webview.addJavascriptInterface (this, "simjso");
        webview.loadUrl ("file:///android_asset/wpviewsimple.html");
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public String getURLParam (String name)
    {
        switch (name) {
            case "detail": return GetDetailText ();
            case "elev": return (elev ==  ELEV_UNKNOWN) ? "" : Double.toString  (elev);
            case "magvar": return (magvar == VAR_UNKNOWN) ? "" : Integer.toString (magvar);
            default: return "";
        }
    }

    // full-page description
    public abstract String GetDetailText ();

    // retrieve key string suitable to display in disambiguation menu
    public abstract String MenuKey ();

    // matches the given search keyword
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean MatchesKeyword (String key)
    {
        return GetDetailText ().toUpperCase (Locale.US).contains (key);
    }

    // maybe there is an info page available
    public InputStream HasInfo ()
    {
        return null;
    }

    // maybe there is a METAR available
    public boolean HasMetar ()
    {
        return false;
    }

    // maybe there is something that needs downloading
    public boolean NeedsDwnld ()
    {
        return false;
    }

    // start downloading it
    public void StartDwnld (WairToNow wtn, Runnable done)
    {
        done.run ();
    }

    // get magnetic variation
    //  input:
    //   altmsl = altitude (feet MSL)
    //  output:
    //   returns amount to add to a true course to get a magnetic course (degrees)
    //           ie, >0 on east coast, <0 on west coast
    public double GetMagVar (double altmsl)
    {
        if (magvar != VAR_UNKNOWN) return magvar;
        if (elev != ELEV_UNKNOWN) altmsl = elev;
        return Lib.MagVariation (lat, lon, altmsl);
    }

    // DME is normally at same spot
    public double GetDMEElev ()
    {
        return elev;
    }
    public double GetDMELat ()
    {
        return lat;
    }
    public double GetDMELon ()
    {
        return lon;
    }

    // get corresponding airport
    // - for airport, the object itself
    // - for ILS/Localizer, the airport it is on
    // - for Runway, the runway's airport
    // - all others, null
    public Airport GetAirport () { return null; }

    // most waypoints are not VORs
    public boolean isAVOR () { return false; }

    /**
     * Get waypoints within a lat/lon area.
     */
    public static class Within {
        private double westLon, eastLon, northLat, southLat;
        private HashMap<String,Waypoint> found = new HashMap<> ();
        private WairToNow wairToNow;

        public Within (WairToNow wtn)
        {
            wairToNow = wtn;
            clear ();
        }

        /**
         * Set up to (re-)read waypoints within boundaries (GetWaypointsWithin()).
         */
        public void clear ()
        {
            found.clear ();
            southLat =  1000.0;
            northLat = -1000.0;
            westLon  =  1000.0;
            eastLon  = -1000.0;
        }

        /**
         * Return list of all waypoints within the given latitude & longitude.
         * May include waypoints outside that range as well.
         */
        public Collection<Waypoint> Get (double sLat, double nLat, double wLon, double eLon)
        {
            wLon = Lib.NormalLon (wLon);
            eLon = Lib.NormalLon (eLon);

            // if new box is completely separate from old box, wipe out everything and start over
            boolean alloutside = (sLat > northLat) || (nLat < southLat);
            if (! alloutside) {
                boolean oldlonwraps = westLon > eastLon;
                boolean newlonwraps = wLon > eLon;
                if (! oldlonwraps && ! newlonwraps) {
                    alloutside = (wLon > eastLon) || (eLon < westLon);
                } else if (! oldlonwraps || ! newlonwraps) {
                    alloutside = (westLon > eLon) && (eastLon < wLon);
                }
            }
            if (alloutside) {
                clear ();
            }

            // if new box goes outside old box, re-scan database
            boolean goesoutside = alloutside || (sLat < southLat) || (nLat > northLat);
            if (! goesoutside) {
                boolean oldlonwraps = westLon > eastLon;
                boolean newlonwraps = wLon > eLon;
                if (oldlonwraps == newlonwraps) {
                    goesoutside = (wLon < westLon) || (eLon > eastLon);
                } else if (newlonwraps) {
                    // oldlonwraps = false; newlonwraps = true
                    goesoutside = true;
                } else {
                    // oldlonwraps = true; newlonwraps = false
                    goesoutside = (eLon > eastLon) && (wLon < westLon);
                }
            }
            if (goesoutside) {

                // expand borders a little so we don't read each time for small changes
                sLat -= 3.0 / 64.0;
                nLat += 3.0 / 64.0;
                wLon = Lib.NormalLon (wLon - 3.0 / 64.0);
                eLon = Lib.NormalLon (eLon + 3.0 / 64.0);

                // re-scanning database, get rid of all previous points
                found.clear ();

                // save exact limits of what we are scanning for
                southLat = sLat;
                northLat = nLat;
                westLon  = wLon;
                eastLon  = eLon;

                SQLiteDBs sqldb = openWayptDB (wairToNow);
                if (sqldb != null) {
                    try {
                        for (Class<? extends Waypoint> wpclass : Waypoint.wpclasses) {
                            String dbtable = (String) wpclass.getField ("dbtable").get (null);
                            String keyid = (String) wpclass.getField ("dbkeyid").get (null);
                            String[] dbcols = (String[]) wpclass.getField ("dbcols").get (null);
                            Constructor<? extends Waypoint> ctor = wpclass.getConstructor (Cursor.class, WairToNow.class);

                            assert keyid != null;
                            String prefix = keyid.substring (0, 4);

                            String where =
                                    prefix + "lat>=" + sLat + " AND " +
                                    prefix + "lat<=" + nLat + " AND (" +
                                    prefix + "lon>=" + wLon +
                                    ((wLon < eLon) ? " AND " : " OR ") +
                                    prefix + "lon<=" + eLon + ')';

                            Cursor result = sqldb.query (
                                    dbtable, dbcols, where,
                                    null, null, null, null, null);
                            try {
                                if (result.moveToFirst ()) do {
                                    Waypoint wp = ctor.newInstance (result, wairToNow);
                                    found.put (wp.ident, wp);
                                } while (result.moveToNext ());
                            } finally {
                                result.close ();
                            }
                        }
                    } catch (Exception e) {
                        Log.e (TAG, "error reading " + sqldb.mydbname, e);
                    }
                } else {
                    Log.w (TAG, "no database file");
                }
            }
            return found.values ();
        }
    }

    public static class Airport extends Waypoint  {
        public final static String  dbtable = "airports";
        public final static String  dbkeyid = "apt_icaoid";
        @SuppressWarnings ("unused")  // reflection
        public final static String  dbkytbl = "aptkeys";
        // WebMetarThread assumes dbcols[0].equals("apt_icaoid")
        public final static String[] dbcols = new String[] { "apt_icaoid", "apt_faaid", "apt_elev",
                "apt_name", "apt_lat", "apt_lon", "apt_desc", "apt_state", "apt_metaf", "apt_tzname" };

        public  final String name;       // eg, "BEVERLY MUNI"
        public  final String faaident;   // non-ICAO ident
        public  final String state;      // eg, "MA"
        public  final String tzname;     // eg, "America/New_York"

        public boolean hasmet;      // METARs can be retrieved from aviationweather.gov
        public boolean hastaf;      // TAFs can be retrieved from aviationweather.gov

        private LinkedList<Runway> runwayPairs;
        private NNHashMap<String,Runway> runways;
        private String details;
        private String menuKey;
        private WairToNow wairToNow;

        public Airport (Cursor result, WairToNow wtn)
        {
            ident    = result.getString (0);  // ICAO identifier
            faaident = result.getString (1);  // FAA identifier
            elev     = result.getDouble (2);  // feet MSL
            name     = result.getString (3);
            lat      = result.getDouble (4);
            lon      = result.getDouble (5);
            state    = result.getString (7);  // two letters
            String metaf = result.getString (8);
            hasmet   = metaf.contains ("M");
            hastaf   = metaf.contains ("T");
            tzname   = result.getString (9);

            wairToNow = wtn;
        }

        public static Airport GetByFaaID (String faaid, WairToNow wtn)
        {
            SQLiteDBs sql56db = openWayptDB (wtn);
            if (sql56db == null) return null;
            Cursor resultapt = sql56db.query (
                    "airports", Waypoint.Airport.dbcols,
                    "apt_faaid=?", new String[] { faaid },
                    null, null, null, null);
            try {
                if (!resultapt.moveToFirst ()) return null;
                return new Airport (resultapt, wtn);
            } finally {
                resultapt.close ();
            }
        }

        @Override
        public String GetType ()
        {
            return "AIRPORT";
        }

        @Override
        public String GetName ()
        {
            return name;
        }

        /**
         * Get contents of the details for the given airport.
         * It consists of the detail text
         * followed by a list of buttons, one button per approach plate
         */
        @SuppressLint({ "SetTextI18n", "AddJavascriptInterface" })
        @Override
        public void GetDetailViews (WaypointView wayview, WebView webview)
        {
            waypointView = wayview;
            webview.addJavascriptInterface (this, "aptjso");
            webview.loadUrl ("file:///android_asset/wpviewairport.html");
        }

        // script debugging
        //   aptjso.showLogcat ('pageLoaded*:A');
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public void showLogcat (String s)
        {
            Log.d (TAG, "aptjso: " + s);
        }

        // get metars and tafs for the airport
        // the source is WebMetarThread and any received from ADS-B
        //   T<type>   METAR, TAF, etc
        //   X<unixtime>
        //   D<line>
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public String getMetars ()
        {
            StringBuilder sb = new StringBuilder ();
            synchronized (wairToNow.metarRepos) {
                MetarRepo repo = wairToNow.metarRepos.get (ident);
                if (repo != null) {
                    for (Iterator<String> it = repo.metarTypes.keySet ().iterator (); it.hasNext (); ) {
                        String type = it.next ();
                        TreeMap<Long,Metar> metars = repo.metarTypes.nnget (type);
                        if (metars.isEmpty ()) {
                            it.remove ();
                        } else {
                            sb.append ('T');
                            sb.append (type);
                            sb.append ('\n');
                            for (Metar metar : metars.values ()) {
                                sb.append ('X');
                                sb.append (metar.time);
                                sb.append ('\n');
                                String[] lines = metar.data.split ("\n");
                                for (String line : lines) {
                                    sb.append ('D');
                                    sb.append (line);
                                    sb.append ('\n');
                                }
                            }
                        }
                    }
                }
            }
            return sb.toString ();
        }

        // get airport detail text (runways, frequencies)
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public String getDetail ()
        {
            return GetDetailText ();
        }

        private NNHashMap<String,Integer> latestexpdates;
        private TreeMap<String,String> latestplates;
        private WaypointView waypointView;

        // get plate list, one plate per line
        // lines are like APD-AIRPORT DIAGRAM, IAP-ILS RWY 07, DP-BEVERLY ONE, ...
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public String getPlates ()
        {
            // get list of plates from database and the corresponding .gif file name
            String[] dbnames = SQLiteDBs.Enumerate ();
            int latestexpdate = 0;
            latestplates = new TreeMap<> ();
            latestexpdates = new NNHashMap<> ();

            for (String dbname : dbnames) {
                if (dbname.startsWith ("nobudb/plates_") && dbname.endsWith (".db")) {
                    int expdate = Integer.parseInt (dbname.substring (14, dbname.length () - 3));
                    if (latestexpdate < expdate) {
                        SQLiteDBs sqldb = SQLiteDBs.open (dbname);
                        if ((sqldb != null) && sqldb.tableExists ("plates")) {
                            Cursor result = sqldb.query (
                                    "plates", columns_pl_descrip_pl_filename,
                                    "pl_faaid=?", new String[] { this.faaident },
                                    null, null, null, null);
                            try {
                                if (result.moveToFirst ()) {
                                    latestplates.clear ();
                                    do {
                                        String descrip = result.getString (0);
                                        latestplates.put (descrip, result.getString (1));
                                        latestexpdates.put (descrip, expdate);
                                    } while (result.moveToNext ());
                                    latestexpdate = expdate;
                                }
                            } finally {
                                result.close ();
                            }
                        }
                    }
                }
            }

            // also, we can synthesize a runway diagram from openstreetmap tiles
            // give it a way-in-the-future expiration date cuz we build it on the fly
            // and so don't need to download anything for it
            latestplates.put   ("RWY-RUNWAY DIAGRAM", "-");
            latestexpdates.put ("RWY-RUNWAY DIAGRAM", 99999999);

            // maybe make up synthetic ILS/DME plate for each runway
            if (wairToNow.optionsView.synthILSDMEOption.checkBox.isChecked ()) {
                for (String rwyno : GetRunways ().keySet ()) {
                    String plateid = IAPSynthPlateImage.prefix + rwyno;
                    latestplates.put (plateid, "-");
                    latestexpdates.put (plateid, 99999999);
                }
            }

            // make a line for each plate (apt diagram, iap, sid, star, etc)
            StringBuilder sb = new StringBuilder ();
            for (String descrip : latestplates.keySet ()) {
                sb.append (descrip);
                sb.append ('\n');
            }
            return sb.toString ();
        }

        // get url that when fetched, will fetch latest METARs and TAFs from FAA into
        // wairToNow.metarRepo for this airport.  then use getMetars() to fetch from repo.
        // returns "" if airport does not do metars and/or tafs
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public String getMetarUrl ()
        {
            if (! hasmet && ! hastaf) return "";
            return wairToNow.webMetarThread.getWebMetarProxyURL (ident);
        }

        // plate link clicked, display corresponding page
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public void plateClicked (final String descrip)
        {
            wairToNow.runOnUiThread (new Runnable () {
                @Override
                public void run ()
                {
                    String filename = latestplates.get (descrip);
                    int expdate = latestexpdates.nnget (descrip);
                    PlateView pv = new PlateView (waypointView, filename, Airport.this, descrip, expdate, true);
                    waypointView.selectedPlateView = pv;
                    wairToNow.SetCurrentTab (pv);
                }
            });
        }

        // convert zulu time to airport local time
        //  input:
        //   ddhhmmz = time in 'ddhhmm' format
        //  output:
        //   returns local time formatted string
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public String getAptLclTime (String ddhhmmz)
        {
            int dd = Integer.parseInt (ddhhmmz.substring (0, 2));
            int hh = Integer.parseInt (ddhhmmz.substring (2, 4));
            int mm = Integer.parseInt (ddhhmmz.substring (4, 6));
            GregorianCalendar gc = new GregorianCalendar (Lib.tzUtc, Locale.US);
            int nowdd = gc.get (GregorianCalendar.DAY_OF_MONTH);
            if ((nowdd > 20) && (dd < 10)) gc.add (GregorianCalendar.MONTH,  1);
            if ((nowdd < 10) && (dd > 20)) gc.add (GregorianCalendar.MONTH, -1);
            gc.set (GregorianCalendar.DAY_OF_MONTH, dd);
            gc.set (GregorianCalendar.HOUR_OF_DAY, hh);
            gc.set (GregorianCalendar.MINUTE, mm);
            gc.set (GregorianCalendar.SECOND, 0);
            gc.set (GregorianCalendar.MILLISECOND, 0);
            TimeZone tz = tzname.equals ("") ? Lib.tzUtc : TimeZone.getTimeZone (tzname);
            SimpleDateFormat sdf = new SimpleDateFormat ("dd HH:mm", Locale.US);
            sdf.setTimeZone (tz);
            return sdf.format (gc.getTime ()) + " " + Lib.simpTZName (tz.getID ());
        }

        @Override
        public String GetDetailText ()
        {
            return "Airport " + ident + ": " + name + "\n" + GetArptDetails ();
        }

        @Override
        public String MenuKey ()
        {
            if (menuKey == null) {
                String details = GetArptDetails ();
                String[] lines = details.split ("\n");
                menuKey = "APT " + ident + ": " + name;
                if (lines.length > 0) menuKey += " " + lines[0];
            }
            return menuKey;
        }

        @Override
        public boolean HasMetar ()
        {
            return hasmet | hastaf;
        }

        @Override
        public InputStream HasInfo ()
        {
            try {
                ZipFile zf = wairToNow.maintView.getCurentStateZipFile (state);
                if (zf == null) return null;
                return zf.getInputStream (zf.getEntry (faaident + ".html"));
            } catch (IOException ioe) {
                Log.w (TAG, "error opening " + state + " zip file " + faaident + ".html", ioe);
            }
            return null;
        }

        // maybe there is something that needs downloading
        public boolean NeedsDwnld ()
        {
            Calendar nowcal = new GregorianCalendar (Lib.tzUtc, Locale.US);
            nowcal.add (Calendar.MINUTE, -MaintView.DAYBEGINS);  // day starts at 0901z
            int nowdate = nowcal.get (Calendar.YEAR) * 10000 +
                          nowcal.get (Calendar.MONTH) * 10 +
                          nowcal.get (Calendar.DAY_OF_MONTH);
            int enddate = wairToNow.maintView.GetLatestPlatesExpDate (state);
            return enddate <= nowdate;
        }

        // start downloading it
        public void StartDwnld (WairToNow wtn, Runnable done)
        {
            wtn.maintView.StateDwnld (state, done);
        }

        // get list of all runways for this airport
        // gets separate entries for each direction
        public NNHashMap<String,Runway> GetRunways ()
        {
            if (runways == null) {

                /*
                 * Read list of runways into airport waypoint list.
                 */
                runways = new NNHashMap<> ();
                SQLiteDBs sqldb = openWayptDB (wairToNow);
                if (sqldb != null) {
                    try {
                        Cursor result = sqldb.query (
                                "runways", Runway.dbcols,
                                "rwy_faaid=?", new String[] { this.faaident },
                                null, null, null, null);
                        try {
                            if (result.moveToFirst ()) {
                                do {
                                    Runway rw = new Runway (result, this);
                                    this.runways.put (rw.number, rw);
                                } while (result.moveToNext ());
                            }
                        } finally {
                            result.close ();
                        }
                    } catch (Exception e) {
                        Log.e (TAG, "error reading " + sqldb.mydbname, e);
                    }
                }
            }
            return runways;
        }

        // get list of runways for this airport
        // just gives one entry per pair
        public LinkedList<Runway> GetRunwayPairs ()
        {
            if (runwayPairs == null) {
                LinkedList<Runway> ll = new LinkedList<> ();
                for (Runway rwy : GetRunways ().values ()) {
                    for (Runway r : ll) {
                        if ((r.lat == rwy.endLat) && (r.lon == rwy.endLon) && (rwy.lat == r.endLat) && (rwy.lon == r.endLon)) {
                            rwy = null;
                            break;
                        }
                    }
                    if (rwy != null) ll.addLast (rwy);
                }
                runwayPairs = ll;
            }
            return runwayPairs;
        }

        /**
         * Get waypoint by id that is near this airport.
         * It might even be one of this airport's runways.
         * @param wayptid = waypoint identifier
         * @return null if not found, else waypoint
         */
        public Waypoint GetNearbyWaypoint (String wayptid)
        {
            // if it might be a runway, look it up in the current airport
            Waypoint bestwp = null;
            if (wayptid.startsWith ("RW")) {
                Waypoint.RNavParse rnp = new Waypoint.RNavParse (wayptid);
                String rwno = rnp.baseident.substring (2);
                HashMap<String,Waypoint.Runway> rws = GetRunways ();
                bestwp = rws.get (rwno);
                if (bestwp == null) bestwp = rws.get ("0" + rwno);
                if (bestwp != null) bestwp = rnp.makeWaypoint (bestwp);
            }

            if (bestwp == null) {

                // not a runway, look up identifier as given
                // see if it is closest so far to this airport
                double bestnm = 999999.0;
                LinkedList<Waypoint> wps = Waypoint.GetWaypointsByIdent (wayptid, wairToNow);
                for (Waypoint wp : wps) {
                    double nm = Lib.LatLonDist (wp.lat, wp.lon, lat, lon);
                    if (bestnm > nm) {
                        bestnm = nm;
                        bestwp = wp;
                    }
                }

                // maybe it is a localizer witout the _ after the I
                // see if it is closest so far to this airport
                if (wayptid.startsWith ("I") && !wayptid.startsWith ("I-")) {
                    wps = Waypoint.GetWaypointsByIdent ("I-" + wayptid.substring (1), wairToNow);
                    for (Waypoint wp : wps) {
                        double nm = Lib.LatLonDist (wp.lat, wp.lon, lat, lon);
                        if (bestnm > nm) {
                            bestnm = nm;
                            bestwp = wp;
                        }
                    }
                }
            }

            return bestwp;
        }

        /**
         * Retrieve the airport details field from the database file.
         */
        public String GetArptDetails ()
        {
            if (details == null) {
                SQLiteDBs sqldb = openWayptDB (wairToNow);
                if (sqldb == null) return "<missing>";
                Cursor result = sqldb.query (
                        "airports", columns_apt_desc,
                        "apt_icaoid=?", new String[] { this.ident },
                        null, null, null, null);
                try {
                    if (!result.moveToFirst ()) return "<missing>";
                    details = result.getString (0);
                } finally {
                    result.close ();
                }
            }
            return details;
        }

        // Get associated airport
        @Override  // Waypoint
        public Airport GetAirport () { return this; }
    }

    public static class SelfLL extends Waypoint {
        @Override  // Waypoint
        public String GetType ()
        {
            return "SelfLL";
        }

        @Override
        public String GetName ()
        {
            return GetDetailText ();
        }

        @Override  // Waypoint
        public String GetDetailText ()
        {
            return "/ latitude " + lat + " / longitude " + lon;
        }

        @Override  // Waypoint
        public String MenuKey ()
        {
            return GetDetailText ();
        }
    }

    /**
     * An RNAV offset from another waypoint.
     */
    public static class RNavOffset extends Waypoint {
        private boolean magnetic;  // rnavradl is magnetic
        private double rnavdist;
        private double rnavradl;
        public Waypoint basewp;

        /**
         * Wrap the given waypoint with a distance and an heading
         * @param basewp   = waypoint to be wrapped
         * @param rnavdist = distance away from the basewp (naut miles)
         * @param rnavradl = direction away from the basewp (degrees)
         * @param mag      = true: magnetic; false: true
         * @param suffix   = string to represent the RNAV portion
         */
        public RNavOffset (Waypoint basewp, double rnavdist, double rnavradl, boolean mag, String suffix)
        {
            if (rnavdist == 0) throw new IllegalArgumentException ("rnavdist zero");
            this.basewp    = basewp;
            this.rnavdist  = rnavdist;
            this.rnavradl  = rnavradl;
            this.magnetic  = mag;
            double hdgtrue = rnavradl;
            if (mag) {
                hdgtrue -= basewp.GetMagVar (basewp.elev);
            }

            ident = basewp.ident + "[" + suffix;
            lat = Lib.LatHdgDist2Lat (basewp.lat, hdgtrue, rnavdist);
            lon = Lib.LatLonHdgDist2Lon (basewp.lat, basewp.lon, hdgtrue, rnavdist);
        }

        @Override  // Waypoint
        public String GetType ()
        {
            return "RNavOffset." + basewp.GetType ();
        }

        @Override
        public String GetName ()
        {
            return MenuKey ();
        }

        @Override  // Waypoint
        public String GetDetailText ()
        {
            return "RNAV offset " + Lib.DoubleNTZ (rnavdist) + "nm on " + Lib.DoubleNTZ (rnavradl) +
                    (magnetic ? "\u00B0 magnetic from " : "\u00B0 true from ") +
                    basewp.GetDetailText ();
        }

        @Override  // Waypoint
        public String MenuKey ()
        {
            return "RNAV " + Lib.DoubleNTZ (rnavdist) + "nm " + Lib.DoubleNTZ (rnavradl) +
                    (magnetic ? "\u00B0M from " : "\u00B0T from ") + basewp.MenuKey ();
        }
    }

    /**
     * Every record in runways.csv that we are able to parse.
     */
    public static class Runway extends Waypoint {
        public final static String[] dbcols = new String[] { "rwy_number",
                "rwy_truehdg", "rwy_tdze", "rwy_beglat", "rwy_beglon", "rwy_endlat", "rwy_endlon",
                "rwy_ritraf" };

        public Airport airport;
        public String  number;   // eg, "02L"
        public int     truehdg;  // published
        public double  endLat;
        public double  endLon;
        public double  trueHdg;  // computed
        public boolean ritraf;   // true=right; false=left

        private int lengthFt = -1;
        private int widthFt  = -1;
        private Localizer synthloc;

        public Runway (Cursor result, Airport apt)
        {
            number  = result.getString (0);
            elev    = result.isNull (2) ? apt.elev : result.getDouble (2);
            lat     = result.getDouble (3);
            lon     = result.getDouble (4);
            endLat  = result.getDouble (5);
            endLon  = result.getDouble (6);
            ritraf  = result.getString (7).equals ("Y");
            airport = apt;

            trueHdg = Lib.LatLonTC (lat, lon, endLat, endLon);
            if (result.isNull (1)) {
                truehdg = (int) Math.round (trueHdg);
                if (truehdg <= 0) truehdg += 360;
            } else {
                truehdg = result.getInt (1);
            }

            // set up Waypoint values
            ident = "RW" + number;
        }

        @Override
        public String GetType ()
        {
            return "RUNWAY";
        }

        @Override
        public String GetName ()
        {
            return airport.GetName () + " rwy " + number;
        }

        @Override  // Waypoint
        public String GetDetailText ()
        {
            return null;
        }

        @Override  // Waypoint
        public String MenuKey ()
        {
            return airport.ident + "." + ident;
        }

        public int getLengthFt ()
        {
            if (lengthFt < 0) {
                lengthFt = 0;
                String[] detcols = getInfoLine ();
                if (detcols != null) {
                    StringBuilder sb = new StringBuilder ();
                    for (int i = 2; i < detcols.length; i ++) sb.append (detcols[i]);
                    String detline = sb.toString ();
                    detline = detline.replace ("FT.", "");
                    detline = detline.replace ("FT", "");
                    int j = detline.indexOf ('X');
                    if (j >= 0) {
                        try {
                            lengthFt = Integer.parseInt (detline.substring (0, j));
                        } catch (NumberFormatException nfe) {
                            Lib.Ignored ();
                        }
                    }
                }
                if (lengthFt == 0) {
                    lengthFt = (int) (Lib.LatLonDist (lat, lon, endLat, endLon) * Lib.NMPerDeg * Lib.MPerNM * Lib.FtPerM + 0.5);
                }
            }
            return lengthFt;
        }

        public int getWidthFt ()
        {
            if (widthFt < 0) {
                widthFt = 0;
                String[] detcols = getInfoLine ();
                if (detcols != null) {
                    StringBuilder sb = new StringBuilder ();
                    for (int i = 2; i < detcols.length; i++) sb.append (detcols[i]);
                    String detline = sb.toString ();
                    detline = detline.replace ("FT.", "");
                    detline = detline.replace ("FT", "");
                    int j = detline.indexOf ('X');
                    if (j >= 0) {
                        int k = ++ j;
                        while ((k < detline.length ()) && (detline.charAt (k) >= '0') && (detline.charAt (k) <= '9')) {
                            k ++;
                        }
                        widthFt = Integer.parseInt (detline.substring (j, k));
                    }
                }
                if (widthFt == 0) widthFt = 50;
            }
            return widthFt;
        }

        // Rwy 04L/22R: 7864 ft. x 150 ft. ASPH GOOD
        public String[] getInfoLine ()
        {
            String details = airport.GetArptDetails ();
            String[] detlines = details.replace (":", "").toUpperCase (Locale.US).split ("\n");
            for (String detline : detlines) {
                String[] detcols = detline.trim ().split (" ");
                if (detcols.length < 3) continue;
                if (!detcols[0].equals ("RWY")) continue;
                String[] rwids = detcols[1].split ("/");
                if (rwids.length != 2) continue;
                if (!rwids[0].equals (number) && !rwids[1].equals (number)) continue;
                return detcols;
            }
            return null;
        }

        // get a synthetic localizer that is an ILS/DME
        // with localizer antenna at far end of runway
        // and glideslope antenna 1000ft down runway at 3.25deg
        public Localizer GetSynthLoc ()
        {
            if (synthloc == null) synthloc = new Localizer (this, 3.25, 1000.0);
            return synthloc;
        }

        // Get associated airport
        @Override  // Waypoint
        public Airport GetAirport () { return airport; }
    }

    public static class Localizer extends Waypoint {
        @SuppressWarnings ("unused")  // reflection
        public final static String  dbtable = "localizers";
        @SuppressWarnings ("unused")  // reflection
        public final static String  dbkeyid = "loc_faaid";
        @SuppressWarnings ("unused")  // reflection
        public final static String  dbkytbl = "lockeys";
        @SuppressWarnings ("unused")  // reflection
        public final static String[] dbcols = new String[] {
                "loc_type", "loc_faaid", "loc_elev",
                "loc_name", "loc_lat", "loc_lon", "loc_thdg",
                "gs_elev", "gs_tilt", "gs_lat", "gs_lon",
                "dme_elev", "dme_lat", "dme_lon", "loc_aptfid" };

        public String type;  // "ILS", "LOC/DME", "LOCALIZER", etc
        public String descr;

        public double thdg;
        public double gs_elev;
        public double gs_tilt;
        public double gs_lat;
        public double gs_lon;
        public double dme_elev;
        public double dme_lat;
        public double dme_lon;

        private Airport airport;

        @SuppressWarnings("unused")  // used by reflection
        public Localizer (Cursor result, WairToNow wtn)
        {
            this.type     = result.getString  (0);
            this.ident    = result.getString  (1);
            this.elev     = result.isNull     (2) ? ELEV_UNKNOWN : result.getDouble (2);
            this.descr    = result.getString  (3);
            this.lat      = result.getDouble  (4);
            this.lon      = result.getDouble  (5);
            this.thdg     = result.getDouble  (6);
            this.gs_elev  = result.isNull     (7) ? ELEV_UNKNOWN : result.getDouble (7);
            this.gs_tilt  = result.isNull     (8) ? 0 : result.getDouble (8);
            this.gs_lat   = result.getDouble  (9);
            this.gs_lon   = result.getDouble (10);
            this.dme_elev = result.isNull    (11) ? ELEV_UNKNOWN : result.getDouble (11);
            this.dme_lat  = result.getDouble (12);
            this.dme_lon  = result.getDouble (13);
            String aptfid = result.getString (14);

            this.airport = GetAirportByIdent (aptfid, wtn);
            if ((this.airport != null) && (this.elev == ELEV_UNKNOWN)) {
                this.elev = this.airport.elev;
            }

            if (this.elev == ELEV_UNKNOWN) {
                short topoelev = Topography.getElevMetres (this.lat, this.lon);
                if (topoelev == Topography.INVALID_ELEV) topoelev = 0;
                this.elev = topoelev * Lib.FtPerM;
            }

            this.magvar = (int) Math.round (Lib.MagVariation (this.lat, this.lon, this.elev / Lib.FtPerM));
        }

        // make up a phony localizer pointing down the given runway
        public Localizer (Runway rwy, double gstilt, double gsdist)
        {
            this.type     = "ILS/DME";
            this.ident    = "I-" + rwy.airport.faaident + "." + rwy.number;  // eg, I-BVY.09
            this.elev     = rwy.elev;
            this.descr    = "Synth ILS/DME for " + rwy.ident + " of " + rwy.airport.name;
            this.lat      = Lib.LatHdgDist2Lat (rwy.endLat, rwy.trueHdg, gsdist / Lib.FtPerNM);
            this.lon      = Lib.LatLonHdgDist2Lon (rwy.endLat, rwy.endLon, rwy.trueHdg, gsdist / Lib.FtPerNM);
            this.thdg     = rwy.trueHdg;    // computed
            this.magvar   = (int) Math.round (Lib.MagVariation (this.lat, this.lon, this.elev / Lib.FtPerM));
            this.gs_elev  = rwy.elev;
            this.gs_tilt  = gstilt;
            this.gs_lat   = Lib.LatHdgDist2Lat (rwy.lat, rwy.trueHdg, gsdist / Lib.FtPerNM);
            this.gs_lon   = Lib.LatLonHdgDist2Lon (rwy.lat, rwy.lon, rwy.trueHdg, gsdist / Lib.FtPerNM);
            this.dme_elev = rwy.elev;       // dme at localizer (far end of runway)
            this.dme_lat  = this.lat;
            this.dme_lon  = this.lon;
            this.airport  = rwy.airport;
        }

        @Override
        public String GetType ()
        {
            return "LOCALIZER:" + type;
        }

        @Override
        public String GetName ()
        {
            return airport.GetName () + " loc " + ident;
        }

        @Override
        public String GetDetailText ()
        {
            return type + " " + ident + ": " + descr;
        }

        @Override
        public String MenuKey ()
        {
            return GetDetailText ();
        }

        // make sure we match on something like ILWM as well as I-LWM
        @Override
        public boolean MatchesKeyword (String key)
        {
            return (ident.startsWith ("I-") && key.equals ("I" + ident.substring (2))) ||
                    super.MatchesKeyword (key);
        }

        @Override
        public double GetDMEElev ()
        {
            return ((dme_lat != 0) || (dme_lon != 0)) ? dme_elev : elev;
        }
        @Override
        public double GetDMELat ()
        {
            return ((dme_lat != 0) || (dme_lon != 0)) ? dme_lat : lat;
        }
        @Override
        public double GetDMELon ()
        {
            return ((dme_lat != 0) || (dme_lon != 0)) ? dme_lon : lon;
        }

        // Get associated airport
        @Override  // Waypoint
        public Airport GetAirport () { return airport; }
    }

    public static class Navaid extends Waypoint {
        @SuppressWarnings ("unused")  // reflection
        public final static String  dbtable = "navaids";
        @SuppressWarnings ("unused")  // reflection
        public final static String  dbkeyid = "nav_faaid";
        @SuppressWarnings ("unused")  // reflection
        public final static String  dbkytbl = "navkeys";
        @SuppressWarnings ("unused")  // reflection
        public final static String[] dbcols = new String[] { "nav_type", "nav_faaid", "nav_elev", "nav_name", "nav_lat", "nav_lon", "nav_magvar" };

        public String type;  // "NDB", "VOR", "VOR/DME", etc
        public String descr;

        @SuppressWarnings("unused")  // used by reflection
        public Navaid (Cursor result, WairToNow wtn)
        {
            this.type   = result.getString (0);
            this.ident  = result.getString (1);
            this.descr  = result.getString (3);
            this.lat    = result.getDouble (4);
            this.lon    = result.getDouble (5);
            this.magvar = result.getInt (6);

            if (result.isNull (2)) {
                short topoelev = Topography.getElevMetres (this.lat, this.lon);
                if (topoelev == Topography.INVALID_ELEV) topoelev = 0;
                this.elev = topoelev * Lib.FtPerM;
            } else {
                this.elev = result.getDouble (2);
            }
        }

        @Override
        public String GetType ()
        {
            return "NAVAID:" + type;
        }

        @Override
        public String GetName ()
        {
            return descr;
        }

        @Override
        public String GetDetailText ()
        {
            return type + " " + ident + ": " + descr;
        }

        @Override
        public String MenuKey ()
        {
            return GetDetailText ();
        }

        @Override
        public boolean isAVOR ()
        {
            return type.contains ("VOR") || type.contains ("TAC");
        }
    }

    private static class Fix extends Waypoint {
        @SuppressWarnings ("unused")  // reflection
        public final static String  dbtable = "fixes";
        @SuppressWarnings ("unused")  // reflection
        public final static String  dbkeyid = "fix_name";
        @SuppressWarnings ("unused")  // reflection
        public final static String  dbkytbl = "fixkeys";
        @SuppressWarnings ("unused")  // reflection
        public final static String[] dbcols = new String[] { "fix_name", "fix_lat", "fix_lon", "fix_desc" };

        private String descr;

        @SuppressWarnings("unused")  // used by reflection
        public Fix (Cursor result, WairToNow wtn)
        {
            this.ident = result.getString (0);
            this.lat   = result.getDouble (1);
            this.lon   = result.getDouble (2);
            this.descr = result.getString (3);
        }

        @Override
        public String GetType ()
        {
            return "FIX";
        }

        @Override
        public String GetName ()
        {
            return GetDetailText ();
        }

        @Override
        public String GetDetailText ()
        {
            return "Fix " + ident + ": " + descr;
        }

        @Override
        public String MenuKey ()
        {
            return GetDetailText ();
        }
    }

    /**
     * Numbered airway intersections.
     * Defined in AWY.txt for intersections of airways where there is no fix.
     */
    public static class Intersection extends Waypoint {
        @SuppressWarnings ("unused")  // reflection
        public final static String  dbtable = "intersections";
        @SuppressWarnings ("unused")  // reflection
        public final static String  dbkeyid = "int_num";
        @SuppressWarnings ("unused")  // reflection
        public final static String  dbkytbl = null;
        @SuppressWarnings ("unused")  // reflection
        public final static String[] dbcols = new String[] { "int_num", "int_lat", "int_lon", "int_type" };

        private String type;

        @SuppressWarnings("unused")  // used by reflection
        public Intersection (Cursor result, WairToNow wtn)
        {
            this.ident = result.getString (0);
            this.lat   = result.getDouble (1);
            this.lon   = result.getDouble (2);
            this.type  = result.getString (3);
        }

        @Override
        public String GetType ()
        {
            return "INT";
        }

        @Override
        public String GetName ()
        {
            return GetDetailText ();
        }

        @Override
        public String GetDetailText ()
        {
            return "Intersection " + ident + ": " + type;
        }

        @Override
        public String MenuKey ()
        {
            return GetDetailText ();
        }
    }

    /**
     * Airways - series of waypoints, not just a single waypoint.
     * ident is something like 'V1' or 'J32'
     * region is '-' for continental US, 'H' for Hawaii, 'A' for Alaska
     */
    public static class Airway {
        public final static String[] regions = { "-", "A", "H" };

        public String ident;            // includes region
        public Waypoint[] waypoints;    // points in order

        public static Collection<Airway> GetAirways (String ident, WairToNow wairToNow)
        {
            LinkedList<Airway> airways = new LinkedList<> ();
            for (String region : regions) {
                Airway awy = GetAirway (ident, region, wairToNow);
                if (awy != null) airways.addLast (awy);
            }
            return airways.isEmpty () ? null : airways;
        }

        public static Airway GetAirway (String ident, String region, WairToNow wairToNow)
        {
            Airway awy = new Airway ();
            awy.ident = ident + " " + region;
            SQLiteDBs sqldb = openWayptDB (wairToNow);
            if (sqldb == null) return null;
            Cursor cursor = sqldb.query (
                    "airways", new String[] { "awy_wpident", "awy_wplat", "awy_wplon" },
                    "awy_segid BETWEEN ? AND ?",
                            new String[] { awy.ident + " ", awy.ident + "~" },
                    null, null, null, null);
            try {
                if (!cursor.moveToFirst ()) return null;
                awy.waypoints = new Waypoint[cursor.getCount()];
                int i = 0;
                do {
                    String wpident  = cursor.getString (0);
                    double  wplat    = cursor.getDouble (1);
                    double  wplon    = cursor.getDouble (2);
                    double bestnm    = 1.0;
                    Waypoint bestwp = null;
                    LinkedList<Waypoint> wps = GetWaypointsByIdent (wpident, wairToNow);
                    for (Waypoint wp : wps) {
                        double nm = Lib.LatLonDist (wplat, wplon, wp.lat, wp.lon);
                        if (bestnm > nm) {
                            bestnm = nm;
                            bestwp = wp;
                        }
                    }
                    if (bestwp == null) {
                        throw new RuntimeException ("airway " + awy.ident + " undefined waypoint " + wpident);
                    }
                    awy.waypoints[i++] = bestwp;
                } while (cursor.moveToNext ());
                return awy;
            } finally {
                cursor.close ();
            }
        }
    }

    // open current version of waypoint database
    public static SQLiteDBs openWayptDB (WairToNow wairToNow)
    {
        int waypointexpdate = wairToNow.maintView.GetCurentWaypointExpDate ();
        String dbname = "nobudb/waypoints_" + waypointexpdate + ".db";
        SQLiteDBs sqldb = SQLiteDBs.open (dbname);
        if ((sqldb != null) && !sqldb.columnExists ("airports", "apt_metaf")) {
            sqldb.execSQL ("ALTER TABLE airports ADD COLUMN apt_metaf TEXT NOT NULL DEFAULT ''");
            sqldb.execSQL ("BEGIN");
            Cursor result = sqldb.query (true, "runways", new String[] { "rwy_faaid" }, "rwy_length>=1500", null, null, null, null, null);
            try {
                if (result.moveToFirst ()) do {
                    sqldb.execSQL ("UPDATE airports SET apt_metaf='?' WHERE apt_faaid='" + result.getString (0) + "'");
                } while (result.moveToNext ());
            } finally {
                result.close ();
            }
            sqldb.execSQL ("COMMIT");
        }
        if ((sqldb != null) && !sqldb.columnExists ("airports", "apt_tzname")) {
            sqldb.execSQL ("ALTER TABLE airports ADD COLUMN apt_tzname TEXT NOT NULL DEFAULT ''");
        }
        return sqldb;
    }
}
