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
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Each individual waypoint in the database (airport, localizer, navaid, fix)
 */
public abstract class Waypoint {
    public interface Selected {
        void wpSeld (Waypoint wp);
    }

    public final static String TAG = "WairToNow";
    public final static float ELEV_UNKNOWN = 999999.0F;
    public final static int   VAR_UNKNOWN  = 999999;

    public final static ArrayList<Class<? extends Waypoint>> wpclasses = GetWaypointClasses ();

    public float  lat;                   // degrees north
    public float  lon;                   // degrees east
    public float  elev = ELEV_UNKNOWN;   // feet MSL
    public int    magvar = VAR_UNKNOWN;  // magnetic variation (+=West; -=East)
    public String ident;                 // for airports, ICAO identifier

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
     * @param oldid = initial value for dialog box
     * @param wpsel = what to call when waypoint selection complete
     */
    @SuppressLint("SetTextI18n")
    public static void ShowWaypointDialog (
            final WairToNow wtn,
            final String title,
            final float nearlat,
            final float nearlon,
            final String oldid,
            final Selected wpsel)
    {
        // set up a text box to enter the new identifier in
        final EditText ident = new EditText (wtn);
        ident.setEms (5);
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
                        float value = Float.parseFloat (after);
                        if ((value >= 0.0F) && (value <= 360.0F)) return;
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
                        float value = Float.parseFloat (after);
                        if ((value >= 0.0F) && (value <= 999.0F)) return;
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
                rnavradl.setText (Lib.FloatNTZ (rnavParse.rnavradial));
                magTrue.setMag (rnavParse.magnetic);
                rnavdist.setText (Lib.FloatNTZ (rnavParse.rnavdistance));
                hasRNavOffset = true;
            }
        } catch (Exception e) {
            ident.setText (oldid);
        }

        // put those boxes in a linear layout
        final LinearLayout ll = new LinearLayout (wtn);
        ll.setOrientation (LinearLayout.HORIZONTAL);
        ll.addView (ident);
        if (hasRNavOffset) {

            // already has RNAV offset, display the RNAV offset boxes too
            addRNavOffsetBoxes (ll, rnavradl, magTrue, rnavdist);
        } else {

            // no RNAV offset, give them a button to show the boxes if they want
            Button rnavOffsetButton = new Button (wtn);
            rnavOffsetButton.setText ("RNAV>>");
            rnavOffsetButton.setOnClickListener (new View.OnClickListener () {
                @Override
                public void onClick (View view)
                {
                    ll.removeAllViews ();
                    ll.addView (ident);
                    addRNavOffsetBoxes (ll, rnavradl, magTrue, rnavdist);
                }
            });
            ll.addView (rnavOffsetButton);
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
            public void onClick (DialogInterface dialogInterface, int i)
            {
                waypointEntered (wtn, title, wpsel, nearlat, nearlon,
                        ident, rnavradl, magTrue, rnavdist);
            }
        });
        adb.setNeutralButton ("OFF", new DialogInterface.OnClickListener () {
            @Override
            public void onClick (DialogInterface dialogInterface, int i)
            {
                ident.setText ("");
                waypointEntered (wtn, title, wpsel, nearlat, nearlon,
                        ident, rnavradl, magTrue, rnavdist);
            }
        });
        adb.setNegativeButton ("Cancel", null);
        final AlertDialog ad = adb.show ();

        // set up listener for the DONE keyboard key
        ident.setOnEditorActionListener (new TextView.OnEditorActionListener () {
            @Override
            public boolean onEditorAction (TextView textView, int i, KeyEvent keyEvent)
            {
                if (i == EditorInfo.IME_ACTION_DONE) {
                    ad.dismiss ();
                    waypointEntered (wtn, title, wpsel, nearlat, nearlon,
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
        ll.addView (new TextViewString (ctx, "nm"));
    }

    private static void waypointEntered (
            WairToNow wtn,
            String title,
            Selected wpsel,
            float nearlat,
            float nearlon,
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
            LinkedList<Waypoint> wplist = GetWaypointsByIdent (newid);

            // look for the one so named that is closest to given lat/lon
            Waypoint bestwp = null;
            float bestnm = 999999.0F;
            for (Waypoint wp : wplist) {
                float nm = Lib.LatLonDist (wp.lat, wp.lon, nearlat, nearlon);
                if (bestnm > nm) {
                    bestnm = nm;
                    bestwp = wp;
                }
            }

            // get RNAV offset info
            String rnavradlstr = rnavradl.getText ().toString ();
            boolean magnetic   = magTrue.getMag ();
            String rnavdiststr = rnavdist.getText ().toString ();
            String rnavsuffix  = rnavradlstr + (magnetic ? "@" : "T@") + rnavdiststr;

            // if waypoint not found, re-prompt
            if (bestwp == null) {
                newid += "[" + rnavsuffix;
                ShowWaypointDialog (wtn, title, nearlat, nearlon, newid, wpsel);
                return;
            }

            // if a RNAV distance given, wrap the waypoint with RNAV radial/distance info
            if (!rnavradlstr.equals ("") || !rnavdiststr.equals ("")) {
                try {
                    float rnavradlbin = Float.parseFloat (rnavradlstr);
                    float rnavdistbin = Float.parseFloat (rnavdiststr);
                    if (rnavdistbin != 0) {
                        rnavsuffix = Lib.FloatNTZ (rnavradlbin) + (magnetic ? "@" : "T@") +
                                Lib.FloatNTZ (rnavdistbin);
                        bestwp = new RNavOffset (bestwp, rnavdistbin, rnavradlbin, magnetic, rnavsuffix);
                    }
                } catch (NumberFormatException nfe) {
                    newid += "[" + rnavsuffix;
                    ShowWaypointDialog (wtn, title, nearlat, nearlon, newid, wpsel);
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
    public static LinkedList<Waypoint> GetWaypointsByIdent (String ident)
    {
        LinkedList<Waypoint> wplist = new LinkedList<> ();

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
            LinkedList<Waypoint> basewps = GetWaypointsByIdent (rnavParse.baseident);

            /*
             * Add the RNAV offset to each of those for the resultant value.
             */
            for (Waypoint basewp : basewps) {
                Waypoint rnavwp = rnavParse.makeWaypoint (basewp);
                wplist.addLast (rnavwp);
            }
        } else {

            /*
             * Otherwise, just get the waypoint as named.
             */
            int waypointexpdate = MaintView.GetWaypointExpDate ();
            String dbname = "waypoints_" + waypointexpdate + ".db";
            SQLiteDBs sqldb = SQLiteDBs.open (dbname);
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
                                    Constructor<? extends Waypoint> ctor = wpclass.getConstructor (Cursor.class);
                                    Waypoint wpe = ctor.newInstance (result);
                                    wplist.addLast (wpe);
                                } while (result.moveToNext ());
                            }
                        } finally {
                            result.close ();
                        }
                    }
                } catch (Exception e) {
                    Log.e (TAG, "error reading " + dbname, e);
                }
                if (wplist.isEmpty () && ident.startsWith ("I") && !ident.startsWith ("I-")) {
                    return GetWaypointsByIdent ("I-" + ident.substring (1));
                }
            }
        }
        return wplist;
    }

    /**
     * Find airport by identifier (icaoid or faaid).
     */
    public static Airport GetAirportByIdent (String ident)
    {
        int waypointexpdate = MaintView.GetWaypointExpDate ();
        String dbname = "waypoints_" + waypointexpdate + ".db";
        SQLiteDBs sqldb = SQLiteDBs.open (dbname);
        if (sqldb != null) {
            try {
                Cursor result = sqldb.query (
                        "airports", Airport.dbcols,
                        "apt_icaoid=? OR apt_faaid=?", new String[] { ident, ident },
                        null, null, null, null);
                try {
                    if (result.moveToFirst ()) {
                        return new Airport (result);
                    }
                } finally {
                    result.close ();
                }
            } catch (Exception e) {
                Log.e (TAG, "error reading " + dbname, e);
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

    public static LinkedList<Waypoint> GetWaypointsMatchingKey (String key)
    {
        String[] keys = key.split (" ");
        LinkedList<Waypoint> matches = new LinkedList<> ();

        /*
         * Scan database for match on first keyword in given string.
         */
        int waypointexpdate = MaintView.GetWaypointExpDate ();
        String dbname = "waypoints_" + waypointexpdate + ".db";
        SQLiteDBs sqldb = SQLiteDBs.open (dbname);
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
                                    Constructor<? extends Waypoint> ctor = wpclass.getConstructor (Cursor.class);
                                    Waypoint wp = ctor.newInstance (result2);
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
                Log.e (TAG, "error reading " + dbname, e);
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
     * Parse a waypoint[rnavdist/rnavradial
     * or waypoint[rnavradial@rnavdist string
     */
    public static class RNavParse {
        public String baseident;    // waypoint
        public String rnavsuffix;    // [... or null if missing
        public float rnavdistance;   // parsed distance
        public float rnavradial;     // parsed radial
        public boolean magnetic;    // true: magnetic; false: true

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
                    sb.append (Lib.FloatNTZ (rnavdistance));
                    sb.append ('/');
                    sb.append (Lib.FloatNTZ (rnavradial));
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
                    sb.append (Lib.FloatNTZ (rnavradial));
                    if (!magnetic) sb.append ('T');
                    sb.append ('@');
                    sb.append (Lib.FloatNTZ (rnavdistance));
                    rnavsuffix = sb.toString ();
                }
                return;
            }
            throw new NumberFormatException ("unknown rnav offset " + suffix);
        }

        private boolean parseDistanceRadial (String diststr, String radlstr, boolean mag)
        {
            magnetic = mag;
            rnavdistance = Float.parseFloat (diststr);
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
            rnavradial = Float.parseFloat (radlstr);
            while (rnavradial > 360.0F) rnavradial -= 360.0F;
            while (rnavradial <   0.0F) rnavradial += 360.0F;
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
        if (o == null) return false;
        if (!(o instanceof Waypoint)) return false;
        Waypoint w = (Waypoint) o;
        return w.ident.equals (ident) && (w.lat == lat) && (w.lon == lon);
    }

    // eg, "AIRPORT", "NAVAID:NDB", "LOCALIZER:ILS/DME", "FIX", etc.
    public abstract String GetType ();

    // full-page description
    public void GetDetailViews (WaypointView wpv, LinearLayout ll)
    {
        TextView dt = new TextView (wpv.wairToNow);
        wpv.wairToNow.SetTextSize (dt);
        dt.setText (GetDetailText ());
        ll.addView (dt);
    }

    // full-page description
    public abstract String GetDetailText ();

    // retrieve key string suitable to display in disambiguation menu
    public abstract String MenuKey ();

    // matches the given search keyword
    public boolean MatchesKeyword (String key)
    {
        return GetDetailText ().toUpperCase (Locale.US).contains (key);
    }

    // maybe there is an info page available
    public boolean HasInfo ()
    {
        return false;
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
    public float GetMagVar (float altmsl)
    {
        if (magvar != VAR_UNKNOWN) return magvar;
        if (elev != ELEV_UNKNOWN) {
            altmsl = elev / Lib.FtPerM;
        }
        return Lib.MagVariation (lat, lon, altmsl);
    }

    // DME is normally at same spot
    public float GetDMEElev ()
    {
        return elev;
    }
    public float GetDMELat ()
    {
        return lat;
    }
    public float GetDMELon ()
    {
        return lon;
    }

    /**
     * Get waypoints within a lat/lon area.
     */
    public static class Within {
        private float leftLon, riteLon, topLat, botLat;
        private HashMap<String,Waypoint> found = new HashMap<> ();

        public Within ()
        {
            clear ();
        }

        /**
         * Set up to (re-)read waypoints within boundaries (GetWaypointsWithin()).
         */
        public void clear ()
        {
            found.clear ();
            botLat  =  1000.0F;
            topLat  = -1000.0F;
            leftLon =  1000.0F;
            riteLon = -1000.0F;
        }

        /**
         * Return list of all waypoints within the given latitude & longitude.
         * May include waypoints outside that range as well.
         */
        public Collection<Waypoint> Get (float bLat, float tLat, float lLon, float rLon)
        {
            // if new box is completely separate from old box, wipe out everything and start over
            if ((bLat > topLat) || (tLat < botLat) || (lLon > riteLon) || (rLon < leftLon)) {
                clear ();
            }

            // if new box goes outside old box, re-scan database
            if ((botLat > bLat) || (topLat < tLat) || (leftLon > lLon) || (riteLon < rLon)) {

                // expand borders a little so we don't read each time for small changes
                bLat -= 3.0 / 64.0;
                tLat += 3.0 / 64.0;
                lLon -= 3.0 / 64.0;
                rLon += 3.0 / 64.0;

                // re-scanning database, get rid of all previous points
                found.clear ();

                // save exact limits of what we are scanning for
                botLat  = bLat;
                topLat  = tLat;
                leftLon = lLon;
                riteLon = rLon;

                int waypointexpdate = MaintView.GetWaypointExpDate ();
                String dbname = "waypoints_" + waypointexpdate + ".db";
                SQLiteDBs sqldb = SQLiteDBs.open (dbname);
                if (sqldb != null) {
                    try {
                        for (Class<? extends Waypoint> wpclass : Waypoint.wpclasses) {
                            String dbtable = (String) wpclass.getField ("dbtable").get (null);
                            String prefix = ((String) wpclass.getField ("dbkeyid").get (null)).substring (0, 4);
                            String[] dbcols = (String[]) wpclass.getField ("dbcols").get (null);

                            Cursor result = sqldb.query (
                                    dbtable, dbcols,
                                    "(" + prefix + "lat BETWEEN ? AND ?) AND (" +
                                            prefix + "lon BETWEEN ? AND ?)",
                                    new String[] { Float.toString (bLat), Float.toString (tLat),
                                            Float.toString (lLon), Float.toString (rLon) },
                                    null, null, null, null);
                            Constructor<? extends Waypoint> ctor = wpclass.getConstructor (Cursor.class);
                            try {
                                if (result.moveToFirst ()) do {
                                    Waypoint wp = ctor.newInstance (result);
                                    found.put (wp.ident, wp);
                                } while (result.moveToNext ());
                            } finally {
                                result.close ();
                            }
                        }
                    } catch (Exception e) {
                        Log.e (TAG, "error reading " + dbname, e);
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
        public final static String[] dbcols = new String[] { "apt_icaoid", "apt_faaid", "apt_elev", "apt_name", "apt_lat", "apt_lon", "apt_desc", "apt_state" };

        private final static char metarsHidden = '\u25B6';  // right-pointing triangle when closed
        private final static char metarsShown  = '\u25BC';  // down-pointing triangle when open

        public  final String name;       // eg, "BEVERLY MUNI"
        public  final String faaident;   // non-ICAO ident

        private HashMap<String,Runway> runways;
        private String details;
        private String menuKey;
        private String state;

        public Airport (Cursor result)
        {
            ident    = result.getString (0);  // ICAO identifier
            faaident = result.getString (1);  // FAA identifier
            elev     = result.isNull (2) ? ELEV_UNKNOWN : result.getFloat (2);
            name     = result.getString (3);
            lat      = result.getFloat (4);
            lon      = result.getFloat (5);
            state    = result.getString (7);  // two letters
        }

        public static Airport GetByFaaID (String faaid)
        {
            int waypointexpdate = MaintView.GetWaypointExpDate ();
            String db56name = "waypoints_" + waypointexpdate + ".db";
            SQLiteDBs sql56db = SQLiteDBs.open (db56name);
            if (sql56db == null) return null;
            Cursor resultapt = sql56db.query (
                    "airports", Waypoint.Airport.dbcols,
                    "apt_faaid=?", new String[] { faaid },
                    null, null, null, null);
            try {
                if (!resultapt.moveToFirst ()) return null;
                return new Airport (resultapt);
            } finally {
                resultapt.close ();
            }
        }

        @Override
        public String GetType ()
        {
            return "AIRPORT";
        }

        /**
         * Get contents of the details for the given airport.
         * It consists of the detail text
         * followed by a list of buttons, one button per approach plate
         */
        @SuppressLint("SetTextI18n")
        @Override
        public void GetDetailViews (WaypointView wpv, LinearLayout ll)
        {
            // start with ADS-B derived metars etc at the top
            // just present a button for each type we currently have if any
            // the user must click the button to see the info
            synchronized (wpv.wairToNow.metarRepos) {
                MetarRepo repo = wpv.wairToNow.metarRepos.get (ident);
                if (repo != null) {
                    for (Iterator<String> it = repo.metarTypes.keySet ().iterator (); it.hasNext ();) {
                        String type = it.next ();
                        TreeMap<Long,Metar> metars = repo.metarTypes.get (type);
                        if (metars.isEmpty ()) {
                            it.remove ();
                        } else {
                            TextView tv = new TextView (wpv.wairToNow);
                            tv.setOnClickListener (showHideAdsbMetars);
                            tv.setTag (type);
                            tv.setTextColor (Color.CYAN);
                            wpv.wairToNow.SetTextSize (tv);
                            tv.setText (metarsHidden + " " + type);
                            ll.addView (tv);
                        }
                    }
                }
            }

            // next comes standard detail text
            super.GetDetailViews (wpv, ll);

            // get list of plates from database and the corresponding .gif file name
            String[] dbnames = SQLiteDBs.Enumerate ();
            int latestexpdate = 0;
            TreeMap<String,String> latestplates    = new TreeMap<> ();
            HashMap<String,Integer> latestexpdates = new HashMap<> ();

            for (String dbname : dbnames) {
                if (dbname.startsWith ("plates_") && dbname.endsWith (".db")) {
                    int expdate = Integer.parseInt (dbname.substring (7, dbname.length () - 3));
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

            // make a button for each plate (apt diagram, iap, sid, star, etc) and display them below the detail text
            for (String descrip : latestplates.keySet ()) {
                String filename = latestplates.get (descrip);
                int expdate = latestexpdates.get (descrip);
                PlateButton pb = new PlateButton (wpv, descrip, filename, expdate, this);
                ll.addView (pb);
            }
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
            return true;
        }

        @Override
        public boolean HasInfo ()
        {
            char subdir = faaident.charAt (0);
            String subnam = faaident.substring (1);
            String name = WairToNow.dbdir + "/datums/aptinfo_" + MaintView.GetWaypointExpDate ();
            name += "/" + subdir + "/" + subnam + ".html.gz";
            return new File (name).exists ();
        }

        // maybe there is something that needs downloading
        public boolean NeedsDwnld ()
        {
            Calendar nowcal = new GregorianCalendar ();
            int nowdate = nowcal.get (Calendar.YEAR) * 10000 +
                          nowcal.get (Calendar.MONTH) * 10 +
                          nowcal.get (Calendar.DAY_OF_MONTH);
            int enddate = MaintView.GetPlatesExpDate (state);
            return enddate <= nowdate;
        }

        // start downloading it
        public void StartDwnld (WairToNow wtn, Runnable done)
        {
            wtn.maintView.StateDwnld (state, done);
        }

        public HashMap<String,Runway> GetRunways ()
        {
            if (runways == null) {

                /*
                 * Read list of runways into airport waypoint list.
                 */
                runways = new HashMap<> ();
                int waypointexpdate = MaintView.GetWaypointExpDate ();

                String dbname = "waypoints_" + waypointexpdate + ".db";
                SQLiteDBs sqldb = SQLiteDBs.open (dbname);
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
                        Log.e (TAG, "error reading " + dbname, e);
                    }
                }
            }
            return runways;
        }

        /**
         * Retrieve the airport details field from the database file.
         */
        private String GetArptDetails ()
        {
            if (details == null) {
                int waypointexpdate = MaintView.GetWaypointExpDate ();
                String dbname = "waypoints_" + waypointexpdate + ".db";
                SQLiteDBs sqldb = SQLiteDBs.open (dbname);
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

        /**
         * These buttons are used to display a plate (apt diagram, iap, sid, star, etc).
         */
        @SuppressLint("ViewConstructor")
        private static class PlateButton extends Button implements View.OnClickListener {
            private Airport aw;  // eg, "KBVY"
            private int ex;      // eg, 20150527
            private String fn;   // eg, "gifperm/050/39l16.gif"
            private String pd;   // eg, "IAP-LOC RWY 16", "APD-AIRPORT DIAGRAM", etc
            private WaypointView wpv;

            public PlateButton (WaypointView waypointView, String descrip, String filename, int expdate, Airport airport)
            {
                super (waypointView.wairToNow);
                setText (descrip);
                waypointView.wairToNow.SetTextSize (this);
                setOnClickListener (this);
                fn  = filename;
                ex  = expdate;
                aw  = airport;
                pd  = descrip;
                wpv = waypointView;
            }

            @Override  // OnClickListener
            public void onClick (View button)
            {
                PlateView pv = new PlateView (wpv, WairToNow.dbdir + "/datums/aptplates_" + ex + "/" + fn, aw, pd, ex, true);

                /*
                 * Display this view, telling any previous view to release any screen locks.
                 * Then save the previous screen orientation.
                 * Finally, lock screen in portrait because:
                 *   1) portrait-style diagrams fit as is, even if phone is physically rotated
                 *   2) landscape diagrams also fit as is, the user can rotate phone sideways
                 *      to read it easier without the phone then flipping the diagram as well
                 */
                wpv.wairToNow.SetCurrentTab (pv);
                wpv.wairToNow.setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        }

        /**
         * Show/Hide ADS-B derived Metars, etc.
         * Each type is given a TextView that can be clicked on to open it up.
         * The first character of the text view is an arrow char, either metarsHidden
         * or metarsShown that indicates the state of the text view.
         */
        private final View.OnClickListener showHideAdsbMetars = new View.OnClickListener () {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick (View view)
            {
                String nowstr   = Lib.TimeStringUTC (System.currentTimeMillis ());
                String nowyear_ = nowstr.substring (0, 5);
                TextView tv = (TextView) view;
                WairToNow wtn = (WairToNow) tv.getContext ();
                String type = (String) tv.getTag ();
                if (tv.getText ().charAt (0) == metarsHidden) {
                    synchronized (wtn.metarRepos) {
                        MetarRepo repo = wtn.metarRepos.get (ident);
                        if (repo == null) return;
                        TreeMap<Long,Metar> metars = repo.metarTypes.get (type);
                        if (metars == null) return;
                        if (metars.isEmpty ()) return;
                        tv.setText (metarsShown + " " + type);
                        for (Metar met : metars.values ()) {
                            String timestr = Lib.TimeStringUTC (met.time);
                            timestr = timestr.replace (nowyear_, "");
                            timestr = timestr.replace (":00.000 UTC'", "z");
                            timestr = timestr.replace (".000 UTC'", "z");
                            tv.append ("\n" + timestr + " " + met.type + " " + met.data);
                        }
                    }
                } else {
                    tv.setText (metarsHidden + " " + type);
                }
            }
        };
    }

    /**
     * An RNAV offset from another waypoint.
     */
    public static class RNavOffset extends Waypoint {
        private boolean magnetic;  // rnavradl is magnetic
        private float hdgtrue;
        private float rnavdist;
        private float rnavradl;
        public Waypoint basewp;

        /**
         * Wrap the given waypoint with a distance and an heading
         * @param basewp   = waypoint to be wrapped
         * @param rnavdist = distance away from the basewp (naut miles)
         * @param rnavradl = direction away from the basewp (degrees)
         * @param mag      = true: magnetic; false: true
         * @param suffix   = string to represent the RNAV portion
         */
        public RNavOffset (Waypoint basewp, float rnavdist, float rnavradl, boolean mag, String suffix)
        {
            if (rnavdist == 0) throw new IllegalArgumentException ("rnavdist zero");
            this.basewp   = basewp;
            this.rnavdist = rnavdist;
            this.rnavradl = rnavradl;
            this.magnetic = mag;
            this.hdgtrue  = rnavradl;
            if (mag) {
                this.hdgtrue -= basewp.GetMagVar (basewp.elev);
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

        @Override  // Waypoint
        public String GetDetailText ()
        {
            return "RNAV offset " + Lib.FloatNTZ (rnavdist) + "nm on " + Lib.FloatNTZ (rnavradl) +
                    (magnetic ? "\u00B0 magnetic from " : "\u00B0 true from ") +
                    basewp.GetDetailText ();
        }

        @Override  // Waypoint
        public String MenuKey ()
        {
            return "RNAV " + Lib.FloatNTZ (rnavdist) + "nm " + Lib.FloatNTZ (rnavradl) +
                    (magnetic ? "\u00B0M from " : "\u00B0T from ") + basewp.MenuKey ();
        }
    }

    /**
     * Every record in runways.csv that we are able to parse.
     */
    public static class Runway extends Waypoint {
        public final static String[] dbcols = new String[] { "rwy_faaid", "rwy_number",
                "rwy_truehdg", "rwy_tdze", "rwy_beglat", "rwy_beglon", "rwy_endlat", "rwy_endlon" };

        public Airport airport;
        public String aptid;    // faa ident
        public String number;   // eg, "02L"
        public int    truehdg;  // published
        public float  begLat;
        public float  begLon;
        public float  endLat;
        public float  endLon;
        public float  trueHdg;  // computed

        private int lengthFt = -1;
        private int widthFt  = -1;

        public Runway (Cursor result, Airport apt)
        {
            aptid   = result.getString (0);
            number  = result.getString (1);
            elev    = result.isNull (3) ? apt.elev : result.getFloat (3);
            begLat  = result.getFloat (4);
            begLon  = result.getFloat (5);
            endLat  = result.getFloat (6);
            endLon  = result.getFloat (7);
            airport = apt;

            trueHdg = Lib.LatLonTC (begLat, begLon, endLat, endLon);
            if (result.isNull (2)) {
                truehdg = Math.round (trueHdg);
                if (truehdg <= 0) truehdg += 360;
            } else {
                truehdg = result.getInt (2);
            }

            // set up Waypoint values
            ident = "RW" + number;
            lat = begLat;
            lon = begLon;
        }

        @Override
        public String GetType ()
        {
            return "RUNWAY";
        }

        @Override  // Waypoint
        public String GetDetailText ()
        {
            return null;
        }
        @Override  // Waypoint
        public String MenuKey ()
        {
            return null;
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
                "dme_elev", "dme_lat", "dme_lon" };

        public String type;  // "ILS", "LOC/DME", "LOCALIZER", etc
        public String descr;

        public float thdg;
        public float gs_elev;
        public float gs_tilt;
        public float gs_lat;
        public float gs_lon;
        public float dme_elev;
        public float dme_lat;
        public float dme_lon;

        public Localizer (Cursor result)
        {
            this.type     = result.getString (0);
            this.ident    = result.getString (1);
            this.elev     = result.isNull    (2) ? ELEV_UNKNOWN : result.getFloat (2);
            this.descr    = result.getString (3);
            this.lat      = result.getFloat  (4);
            this.lon      = result.getFloat  (5);
            this.thdg     = result.getFloat  (6);
            this.gs_elev  = result.isNull    (7) ? ELEV_UNKNOWN : result.getFloat (7);
            this.gs_tilt  = result.isNull    (8) ? 0 : result.getFloat (8);
            this.gs_lat   = result.getFloat  (9);
            this.gs_lon   = result.getFloat (10);
            this.dme_elev = result.isNull   (11) ? ELEV_UNKNOWN : result.getFloat (11);
            this.dme_lat  = result.getFloat (12);
            this.dme_lon  = result.getFloat (13);
        }

        @Override
        public String GetType ()
        {
            return "LOCALIZER:" + type;
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
        public float GetDMEElev ()
        {
            return ((dme_lat != 0) || (dme_lon != 0)) ? dme_elev : elev;
        }
        @Override
        public float GetDMELat ()
        {
            return ((dme_lat != 0) || (dme_lon != 0)) ? dme_lat : lat;
        }
        @Override
        public float GetDMELon ()
        {
            return ((dme_lat != 0) || (dme_lon != 0)) ? dme_lon : lon;
        }
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

        public Navaid (Cursor result)
        {
            this.type   = result.getString (0);
            this.ident  = result.getString (1);
            this.elev   = result.isNull (2) ? ELEV_UNKNOWN : result.getFloat (2);
            this.descr  = result.getString (3);
            this.lat    = result.getFloat (4);
            this.lon    = result.getFloat (5);
            this.magvar = result.isNull (6) ? VAR_UNKNOWN : result.getInt (6);
        }

        @Override
        public String GetType ()
        {
            return "NAVAID:" + type;
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

        public Fix (Cursor result)
        {
            this.ident = result.getString (0);
            this.lat   = result.getFloat (1);
            this.lon   = result.getFloat (2);
            this.descr = result.getString (3);
        }

        @Override
        public String GetType ()
        {
            return "FIX";
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

        public Intersection (Cursor result)
        {
            this.ident = result.getString (0);
            this.lat   = result.getFloat  (1);
            this.lon   = result.getFloat  (2);
            this.type  = result.getString (3);
        }

        @Override
        public String GetType ()
        {
            return "INT";
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

        public static Collection<Airway> GetAirways (String ident)
        {
            LinkedList<Airway> airways = new LinkedList<> ();
            for (String region : regions) {
                Airway awy = GetAirway (ident, region);
                if (awy != null) airways.addLast (awy);
            }
            return airways.isEmpty () ? null : airways;
        }

        public static Airway GetAirway (String ident, String region)
        {
            Airway awy = new Airway ();
            awy.ident = ident + " " + region;
            int waypointexpdate = MaintView.GetWaypointExpDate ();
            String dbname = "waypoints_" + waypointexpdate + ".db";
            SQLiteDBs sqldb = SQLiteDBs.open (dbname);
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
                    float  wplat    = cursor.getFloat (1);
                    float  wplon    = cursor.getFloat (2);
                    float bestnm    = 1.0F;
                    Waypoint bestwp = null;
                    LinkedList<Waypoint> wps = GetWaypointsByIdent (wpident);
                    for (Waypoint wp : wps) {
                        float nm = Lib.LatLonDist (wplat, wplon, wp.lat, wp.lon);
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
}
