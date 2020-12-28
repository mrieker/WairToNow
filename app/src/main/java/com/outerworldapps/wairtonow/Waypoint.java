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
import android.database.Cursor;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.lang.reflect.Constructor;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.TreeMap;

/**
 * Each individual waypoint in the database (airport, localizer, navaid, fix)
 */
public abstract class Waypoint {
    public final static String TAG = "WairToNow";
    public final static double ELEV_UNKNOWN = 999999.0;
    public final static int   VAR_UNKNOWN  = 999999;

    public final static ArrayList<Class<? extends Waypoint>> wpclasses = GetWaypointClasses ();

    public final static Waypoint[] nullarray = new Waypoint[0];

    public double lat;                   // degrees north
    public double lon;                   // degrees east
    public double elev = ELEV_UNKNOWN;   // feet MSL
    public double magvar = VAR_UNKNOWN;  // magnetic variation (+=West; -=East)
    public String ident;                 // for airports, ICAO identifier

    private String typeabbr;

    private final static String[] columns_apt_desc = new String[] { "apt_desc" };
    private final static String[] columns_kw_rowid = new String[] { "kw_rowid" };
    private final static String[] columns_pl_descrip_pl_filename = new String[] { "pl_descrip", "pl_filename" };

    private static ArrayList<Class<? extends Waypoint>> GetWaypointClasses ()
    {
        ArrayList<Class<? extends Waypoint>> al = new ArrayList<> (4);
        al.add (Airport.class);
        al.add (Fix.class);
        al.add (Localizer.class);
        al.add (Navaid.class);
        return al;
    }

    /**
     * Find waypoints by identifier (airport icaoid, localizer ident, navaid identifier, fix name).
     * May include a RNAV offset, ie, bearing and distance.
     */
    public static LinkedList<Waypoint> GetWaypointsByIdent (String ident, WairToNow wairToNow)
    {
        return GetWaypointsByIdent (ident, wairToNow, null);
    }
    public static LinkedList<Waypoint> GetWaypointsByIdent (String ident, WairToNow wairToNow, DBase dbtagname)
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
            Airport aptwp = GetAirportByIdent (aptid, wairToNow, dbtagname);
            if (aptwp != null) {
                HashMap<String,Runway> rwys = aptwp.GetRunways ();
                Runway rwywp = rwys.get (rwyid);
                if (rwywp != null) wplist.addLast (rwywp);
            }
            if (aptid.startsWith ("I") || aptid.startsWith ("I-")) {
                aptid = aptid.substring (1);
                if (aptid.startsWith ("-")) aptid = aptid.substring (1);
                aptwp = GetAirportByIdent (aptid, wairToNow, dbtagname);
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
            LinkedList<Waypoint> basewps = GetWaypointsByIdent (rnavParse.baseident, wairToNow, dbtagname);

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
            for (SQLiteDBs sqldb : wairToNow.maintView.getWaypointDBs ()) {
                if ((dbtagname == null) || (dbtagname == sqldb.dbaux)) {
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
                                        Constructor<? extends Waypoint> ctor = wpclass.getConstructor (Cursor.class, DBase.class, WairToNow.class);
                                        Waypoint wpe = ctor.newInstance (result, (DBase) sqldb.dbaux, wairToNow);
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
                }
            }
            if (wplist.isEmpty () && ident.startsWith ("I") && !ident.startsWith ("I-")) {
                return GetWaypointsByIdent ("I-" + ident.substring (1), wairToNow, dbtagname);
            }
        }
        return wplist;
    }

    /**
     * Get list of all airports in a state.
     * Input:
     *  state = 2-letter state code (upper case)
     *  dbase = which waypoint database to search
     * Output:
     *  returns list of airports, sorted by airport icao-id
     */
    public static TreeMap<String,Airport> GetAptsInState (String state, DBase dbase, WairToNow wairToNow)
    {
        TreeMap<String,Airport> wplist = new TreeMap<> ();
        SQLiteDBs sqldb = wairToNow.maintView.getWaypointDB (dbase);
        if (sqldb != null) {
            try {
                Cursor result = sqldb.query (
                        Airport.dbtable, Airport.dbcols,
                        "apt_state=?", new String[] { state },
                        null, null, null, null);
                try {
                    if (result.moveToFirst ()) {
                        do {
                            Airport apt = new Airport (result, (DBase) sqldb.dbaux, wairToNow);
                            wplist.put (apt.ident, apt);
                        } while (result.moveToNext ());
                    }
                } finally {
                    result.close ();
                }
            } catch (Exception e) {
                Log.e (TAG, "error reading " + sqldb.mydbname, e);
            }
        }
        return wplist;
    }

    /**
     * Find airport by identifier (icaoid or faaid).
     */
    public static Airport GetAirportByIdent (String ident, WairToNow wtn)
    {
        return GetAirportByIdent (ident, wtn, null);
    }
    public static Airport GetAirportByIdent (String ident, WairToNow wtn, DBase dbtagname)
    {
        for (SQLiteDBs sqldb : wtn.maintView.getWaypointDBs ()) {
            if ((dbtagname == null) || (dbtagname == sqldb.dbaux)) {
                try {
                    Cursor result = sqldb.query (
                            "airports", Airport.dbcols,
                            "apt_icaoid=? OR apt_faaid=?", new String[] { ident, ident },
                            null, null, null, null);
                    try {
                        if (result.moveToFirst ()) {
                            return new Airport (result, (DBase) sqldb.dbaux, wtn);
                        }
                    } finally {
                        result.close ();
                    }
                } catch (Exception e) {
                    Log.e (TAG, "error reading " + sqldb.mydbname, e);
                }
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
        for (SQLiteDBs sqldb : wtn.maintView.getWaypointDBs ()) {
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
                                    Constructor<? extends Waypoint> ctor = wpclass.getConstructor (Cursor.class, DBase.class, WairToNow.class);
                                    Waypoint wp = ctor.newInstance (result2, (DBase) sqldb.dbaux, wtn);
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

    // Airport:       BEVERLY RGNL (faa)
    // Fix:           BOSOX: MASSACHUSETTS REP-PT (faa)
    // Localizer:     BEVERLY RGNL (faa) loc I-BVY
    // Navaid:        KENNEBUNK - 117.10 - KENNEBUNK, MAINE (faa)
    // PresPosWaypt:  present position
    // RNavOffset:    3.5nm 343^M from BEVERLY RGNL (faa)
    // Runway:        BEVERLY RGNL (faa) rwy 16
    // SelfLL:        / latitude 42.5432 longitude -71.2353
    // UserWP:        MYHOUSE
    public abstract String GetName ();

    // full-page description
    // Airport:       AIRPORT KBVY: BEVERLY RGNL (faa)\n3 NW of BEVERLY, MASSACHUSETTS\nElev: 107.3ft...
    // Fix:           FIX BOSOX: MASSACHUSETTS REP-PT (faa)
    // Localizer:     LOC/DME I-BVY: BEVERLY RGNL rwy 16 - 110.50 - BEVERLY, MASSACHUSETTS (faa)
    // Navaid:        VOR/DME ENE: KENNEBUNK - 117.10 - KENNEBUNK, MAINE (faa)
    // PresPosWaypt:  present position
    // RNavOffset:    3.5nm 343^M from Airport KBVY: BEVERLY RGNL (faa)
    // Runway:        RUNWAY 16: BEVERLY RGNL (faa)
    // SelfLL:        / latitude 42.5432 longitude -71.2353
    // UserWP:        USER MYHOUSE: over the house
    public abstract String GetDetailText ();

    // retrieve key string suitable to display in disambiguation menu
    // Airport:       AIRPORT KBVY: BEVERLY RGNL (faa) 3 NW of BEVERLY, MASSACHUSETTS
    // Fix:           FIX BOSOX: MASSACHUSETTS REP-PT (faa)
    // Localizer:     LOC/DME I-BVY: BEVERLY RGNL rwy 16 - 110.50 - BEVERLY, MASSACHUSETTS (faa)
    // Navaid:        VOR/DME ENE: KENNEBUNK - 117.10 - KENNEBUNK, MAINE (faa)
    // PresPosWaypt:  present position
    // RNavOffset:    3.5nm 343^M from Airport KBVY: BEVERLY RGNL (faa)
    // Runway:        RUNWAY 16: BEVERLY RGNL (faa)
    // SelfLL:        / latitude 42.5432 longitude -71.2353
    // UserWP:        USER MYHOUSE: over the house
    public String MenuKey ()
    {
        return GetDetailText ();
    }

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
            case "magvar": return (magvar == VAR_UNKNOWN) ? "" : Double.toString (magvar);
            default: return "";
        }
    }

    // matches the given search keyword
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean MatchesKeyword (String key)
    {
        return GetDetailText ().toUpperCase (Locale.US).contains (key);
    }

    // get magnetic variation
    //  input:
    //   altmsl = altitude (feet MSL)
    //  output:
    //   returns amount to add to a true course to get a magnetic course (degrees)
    //           ie, >0 on east coast, <0 on west coast
    public double GetMagVar (double altmsl)
    {
        if (magvar == VAR_UNKNOWN) {
            if (elev != ELEV_UNKNOWN) altmsl = elev;
            magvar = Lib.MagVariation (lat, lon, altmsl);
        }
        return magvar;
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
        public DBase dbtagname;

        private LinkedList<Runway> runwayPairs;
        private NNHashMap<String,Runway> runways;
        private String details;
        private String menuKey;
        private WairToNow wairToNow;

        public Airport (Cursor result, DBase dbtn, WairToNow wtn)
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
            dbtagname = dbtn;

            wairToNow = wtn;
        }

        @Override
        public String GetType ()
        {
            return "AIRPORT";
        }

        @Override
        public String GetName ()
        {
            return name + " (" + dbtagname + ")";
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
            interwebView = webview;
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

        @SuppressWarnings ("unused")
        @JavascriptInterface
        public String getICAOID ()
        {
            return ident;
        }

        // get metars and tafs for the given airport
        // the source is WebMetarThread and any received from ADS-B
        //   T<type>   METAR, TAF, etc
        //   X<unixtime>
        //   D<line>
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public String getMetars (String icaoid)
        {
            StringBuilder sb = new StringBuilder ();
            synchronized (wairToNow.metarRepos) {
                MetarRepo repo = wairToNow.metarRepos.get (icaoid);
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
        private WebView interwebView;

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

            if (dbtagname == DBase.FAA) {
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
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public String getMetarUrl (String icaoid)
        {
            return wairToNow.webMetarThread.getWebMetarProxyURL (icaoid);
        }

        // get url that when fetched, returns a string 'true' or 'false' if internet is accessible
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public String getInetStatusUrl ()
        {
            return wairToNow.webMetarThread.getInetStatusURL ();
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

        // convert zulu time range to airport local time range
        //  input:
        //   ddhh/ddhh = time range
        //  output:
        //   returns local time formatted string
        //     dd hh:mm..hh:mm tzname
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public String getAptLclRange (String ddhh_ddhh)
        {
            if (ddhh_ddhh.length () != 9) return "";
            if (ddhh_ddhh.charAt (4) != '/') return "";
            String fromtm = getAptLclTime (ddhh_ddhh.substring (0, 4) + "00");
            String totime = getAptLclTime (ddhh_ddhh.substring (5, 9) + "00");
            return fromtm.substring (0, 8) + ".." + totime.substring (3);
        }

        // convert zulu time to airport local time
        //  input:
        //   ddhhmmZ = time in 'ddhhmm' format
        //  output:
        //   returns local time formatted string
        //     dd hh:mm tzname
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

        // see if airport state's data is downloaded
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public int isDownloaded ()
        {
            return wairToNow.maintView.GetLatestPlatesExpDate (state);
        }

        // start downloading state zip file
        // when download completes, re-display view page, hopefully with plates filled in
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public void dwnldState ()
        {
            wairToNow.runOnUiThread (new Runnable () {
                @Override
                public void run ()
                {
                    wairToNow.maintView.StateDwnld (state, new Runnable () {
                        @Override
                        public void run ()
                        {
                            interwebView.loadUrl ("file:///android_asset/wpviewairport.html");
                        }
                    });
                }
            });
        }

        // open airport details page as a new main page
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public void detailPage ()
        {
            wairToNow.runOnUiThread (new Runnable () {
                @Override
                public void run ()
                {
                    AptDetailsView adv = new AptDetailsView (wairToNow, Airport.this);
                    wairToNow.SetCurrentTab (adv);
                }
            });
        }

        // search for nearest airport that has metar and taf
        // might be this one, otherwise look within 100nm
        // returns singleicaoid or metaricaoid,taficaoid
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public String getNearestMetafIDs ()
        {
            if (hasmet && hastaf) return ident;

            double nmradius = 100.0;
            double northlat = lat + nmradius / Lib.NMPerDeg;
            double southlat = lat - nmradius / Lib.NMPerDeg;
            double eastlon  = lon + nmradius / Lib.NMPerDeg / Mathf.cosdeg (lat);
            double westlon  = lon - nmradius / Lib.NMPerDeg / Mathf.cosdeg (lat);
            double metdist  = 999.0;
            double tafdist  = 999.0;
            String meticao  = "";
            String taficao  = "";

            SQLiteDBs sqldb = wairToNow.maintView.getWaypointDB (dbtagname);
            if (sqldb != null) {
                String where    = "(apt_lat<" + northlat + ") AND (apt_lat>" + southlat +
                        ") AND (apt_lon<" + eastlon + ") AND (apt_lon>" + westlon +
                        ") AND (apt_metaf>'')";
                Cursor result = sqldb.query (
                        "airports", new String[] { "apt_icaoid", "apt_lat", "apt_lon", "apt_metaf" },
                        where, null, null, null, null, null);
                try {
                    if (result.moveToFirst ()) {
                        do {
                            String wpicao = result.getString (0);
                            double wplat  = result.getDouble (1);
                            double wplon  = result.getDouble (2);
                            String metaf  = result.getString (3);
                            double wpdist = Lib.LatLonDist (wplat, wplon, lat, lon);
                            if (metaf.contains ("M") && (metdist > wpdist)) {
                                metdist = wpdist;
                                meticao = wpicao;
                            }
                            if (metaf.contains ("T") && (tafdist > wpdist)) {
                                tafdist = wpdist;
                                taficao = wpicao;
                            }
                        } while (result.moveToNext ());
                    }
                } finally {
                    result.close ();
                }
            }
            SQLiteDBs.CloseAll ();
            return meticao.equals (taficao) ? meticao : (meticao + "," + taficao);
        }

        @Override
        public boolean MatchesKeyword (String key)
        {
            String blob = ident + " " + faaident + " " + state + " " + name;
            if (blob.toUpperCase (Locale.US).contains (key)) return true;
            String detx = GetArptDetails ();
            int i = detx.indexOf ('\n');
            if (i >= 0) detx = detx.substring (0, i);
            return detx.toUpperCase (Locale.US).contains (key);
        }

        @Override
        public String GetDetailText ()
        {
            return "AIRPORT " + ident + ": " + name + " (" + dbtagname + ")\n" + GetArptDetails ();
        }

        @Override
        public String MenuKey ()
        {
            if (menuKey == null) {
                String details = GetDetailText ();
                String[] lines = details.split ("\n");
                menuKey = lines[0];
                if (lines.length > 1) menuKey += " " + lines[1];
            }
            return menuKey;
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
                SQLiteDBs sqldb = wairToNow.maintView.getWaypointDB (dbtagname);
                if (sqldb != null) {
                    try {
                        Cursor result = sqldb.query (
                                "runways", Runway.dbcols,
                                "rwy_icaoid=?", new String[] { this.ident },
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
                LinkedList<Waypoint> wps = Waypoint.GetWaypointsByIdent (wayptid, wairToNow, dbtagname);
                for (Waypoint wp : wps) {
                    double nm = Lib.LatLonDist (wp.lat, wp.lon, lat, lon);
                    if (bestnm > nm) {
                        bestnm = nm;
                        bestwp = wp;
                    }
                }

                // maybe it is a localizer without the _ after the I
                // see if it is closest so far to this airport
                if (wayptid.startsWith ("I") && !wayptid.startsWith ("I-")) {
                    wps = Waypoint.GetWaypointsByIdent ("I-" + wayptid.substring (1), wairToNow, dbtagname);
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
                SQLiteDBs sqldb = wairToNow.maintView.getWaypointDB (dbtagname);
                if (sqldb != null) {
                    Cursor result = sqldb.query (
                            "airports", columns_apt_desc,
                            "apt_icaoid=?", new String[] { this.ident },
                            null, null, null, null);
                    try {
                        if (result.moveToFirst ()) {
                            details = result.getString (0);
                        }
                    } finally {
                        result.close ();
                    }
                }
                if ((details == null) || details.equals ("")) details = "{missing}";
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
            return "/ latitude " + lat + " / longitude " + lon;
        }

        @Override
        public String GetDetailText ()
        {
            return "/ latitude " + lat + " / longitude " + lon;
        }
    }

    /**
     * An RNAV offset from another waypoint.
     */
    public static class RNavOffset extends Waypoint {
        private String rnavstr;
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
            double hdgtrue = rnavradl;
            if (mag) {
                hdgtrue -= basewp.GetMagVar (basewp.elev);
            }

            ident = basewp.ident + "[" + suffix;
            lat = Lib.LatHdgDist2Lat (basewp.lat, hdgtrue, rnavdist);
            lon = Lib.LatLonHdgDist2Lon (basewp.lat, basewp.lon, hdgtrue, rnavdist);

            rnavstr = Lib.DoubleNTZ (rnavdist) + "nm " +
                    Lib.DoubleNTZ (rnavradl) +
                    (mag ? "\u00B0M from " : "\u00B0T from ");
        }

        @Override  // Waypoint
        public String GetType ()
        {
            return "RNavOffset." + basewp.GetType ();
        }

        @Override
        public String GetName ()
        {
            return rnavstr + basewp.GetName ();
        }

        @Override  // Waypoint
        public String GetDetailText ()
        {
            String basedetail = basewp.GetDetailText ();
            String[] basedetlines = basedetail.split ("\n");
            return rnavstr + basedetlines[0].trim ();
        }
    }

    /**
     * Every record in runways.csv that we are able to parse.
     */
    public static class Runway extends Waypoint {
        public final static String[] dbcols = new String[] { "rwy_number",
                "rwy_truehdg", "rwy_tdze", "rwy_beglat", "rwy_beglon", "rwy_endlat", "rwy_endlon",
                "rwy_ritraf", "rwy_length", "rwy_width" };

        public Airport airport;
        public String  number;   // eg, "02L"
        public int     truehdg;  // published
        public double  endLat;
        public double  endLon;
        public double  trueHdg;  // computed
        public boolean ritraf;   // true=right; false=left

        private DBase dbtagname;
        private int lengthFt;
        private int widthFt;
        private Localizer synthloc;

        public Runway (Cursor result, Airport apt)
        {
            number   = result.getString (0);
            elev     = result.isNull (2) ? apt.elev : result.getDouble (2);
            lat      = result.getDouble (3);
            lon      = result.getDouble (4);
            endLat   = result.getDouble (5);
            endLon   = result.getDouble (6);
            ritraf   = result.getString (7).equals ("Y");
            lengthFt = result.getInt (8);
            widthFt  = result.getInt (9);
            airport  = apt;
            dbtagname = apt.dbtagname;

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

        @Override
        public String GetDetailText ()
        {
            return "RUNWAY " + number + ": " + airport.GetName ();
        }

        public int getLengthFt ()
        {
            return lengthFt;
        }

        public int getWidthFt ()
        {
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
        private DBase dbtagname;

        @SuppressWarnings("unused")  // used by reflection
        public Localizer (Cursor result, DBase dbtn, WairToNow wtn)
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
            dbtagname     = dbtn;
            String aptfid = result.getString (14);

            this.airport = GetAirportByIdent (aptfid, wtn, dbtagname);
            if ((this.airport != null) && (this.elev == ELEV_UNKNOWN)) {
                this.elev = this.airport.elev;
            }

            if (this.elev == ELEV_UNKNOWN) {
                short topoelev = Topography.getElevMetres (this.lat, this.lon);
                if (topoelev == Topography.INVALID_ELEV) topoelev = 0;
                this.elev = topoelev * Lib.FtPerM;
            }

            this.magvar = Math.round (Lib.MagVariation (this.lat, this.lon, this.elev / Lib.FtPerM));
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
            this.magvar   = Lib.MagVariation (this.lat, this.lon, this.elev / Lib.FtPerM);
            this.gs_elev  = rwy.elev;
            this.gs_tilt  = gstilt;
            this.gs_lat   = Lib.LatHdgDist2Lat (rwy.lat, rwy.trueHdg, gsdist / Lib.FtPerNM);
            this.gs_lon   = Lib.LatLonHdgDist2Lon (rwy.lat, rwy.lon, rwy.trueHdg, gsdist / Lib.FtPerNM);
            this.dme_elev = rwy.elev;       // dme at localizer (far end of runway)
            this.dme_lat  = this.lat;
            this.dme_lon  = this.lon;
            this.airport  = rwy.airport;
            dbtagname     = rwy.dbtagname;
        }

        @Override
        public String GetType ()
        {
            return "LOCALIZER:" + type;
        }

        @Override
        public String GetName ()
        {
            return airport.GetName () + " " + type.substring (0, 3).toLowerCase (Locale.US) + " " + ident;
        }

        @Override
        public String GetDetailText ()
        {
            return type + " " + ident + ": " + descr + " (" + dbtagname + ")";
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

        public String type;     // "NDB", "VOR", "VOR/DME", etc
        public String descr;    // "KENNEBUNK - 117.10 - KENNEBUNK, MAINE"

        private DBase dbtagname;

        @SuppressWarnings("unused")  // used by reflection
        public Navaid (Cursor result, DBase dbtn, WairToNow wtn)
        {
            this.type   = result.getString (0);
            this.ident  = result.getString (1);
            this.descr  = result.getString (3);
            this.lat    = result.getDouble (4);
            this.lon    = result.getDouble (5);
            this.magvar = result.getDouble (6);
            dbtagname   = dbtn;

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
            return descr + " (" + dbtagname + ")";
        }

        @Override
        public String GetDetailText ()
        {
            return type + " " + ident + ": " + descr + " (" + dbtagname + ")";
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

        private DBase dbtagname;
        private String descr;

        @SuppressWarnings("unused")  // used by reflection
        public Fix (Cursor result, DBase dbtn, WairToNow wtn)
        {
            this.ident = result.getString (0);
            this.lat   = result.getDouble (1);
            this.lon   = result.getDouble (2);
            this.descr = result.getString (3);
            dbtagname  = dbtn;
        }

        @Override
        public String GetType ()
        {
            return "FIX";
        }

        @Override
        public String GetName ()
        {
            return ident + ": " + descr + " (" + dbtagname + ")";
        }

        @Override
        public String GetDetailText ()
        {
            return "FIX " + ident + ": " + descr + " (" + dbtagname + ")";
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
            for (SQLiteDBs sqldb : wairToNow.maintView.getWaypointDBs ()) {
                Cursor cursor = sqldb.query (
                        "airways", new String[] { "awy_wpident", "awy_wplat", "awy_wplon" },
                        "awy_segid BETWEEN ? AND ?",
                                new String[] { awy.ident + " ", awy.ident + "~" },
                        null, null, null, null);
                try {
                    if (!cursor.moveToFirst ()) continue;
                    awy.waypoints = new Waypoint[cursor.getCount()];
                    int i = 0;
                    do {
                        String  wpident = cursor.getString (0);
                        double    wplat = cursor.getDouble (1);
                        double    wplon = cursor.getDouble (2);
                        double   bestnm = 1.0;
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
            return null;
        }
    }
}
