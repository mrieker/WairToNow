//    Copyright (C) 2017, Mike Rieker, Beverly, MA USA
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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;

public class Waypts {
    public final static char dmeMark = '[';

    public static HashMap<String,Airport> allAirports = new HashMap<> ();
    public static HashMap<String,Airport> allIcaoApts = new HashMap<> ();
    public static LinkedList<DBFix> allDBFixes = new LinkedList<> ();

    public static void locknload (String basedir, String cycles56expdate) throws IOException
    {
        // Read in airports to get their lat/lons.
        // KBVY,BVY,107.3,"BEVERLY MUNI",42.5841410277778,-70.9161444166667,16,...

        BufferedReader br4 = new BufferedReader (new FileReader (basedir + "/datums/airports_" + cycles56expdate + ".csv"), 4096);
        String line;
        while ((line = br4.readLine ()) != null) {
            String[] csvs = Lib.QuotedCSVSplit (line);
            if (csvs[6].equals ("")) continue;
            Airport apt = new Airport ();
            apt.name    = csvs[0];  // icaoid
            apt.faaid   = csvs[1];
            apt.lat     = Double.parseDouble (csvs[4]);
            apt.lon     = Double.parseDouble (csvs[5]);
            apt.magvar  = Double.parseDouble (csvs[6]);
            apt.stateid = csvs[8];
            allIcaoApts.put (apt.name, apt);
            allAirports.put (apt.faaid, apt);
        }
        br4.close ();

        // Read in fixes to get their lat/lons.

        BufferedReader br5 = new BufferedReader (new FileReader (basedir + "/datums/fixes_" + cycles56expdate + ".csv"), 4096);
        while ((line = br5.readLine ()) != null) {
            String[] csvs = Lib.QuotedCSVSplit (line);
            DBFix dbfix = new DBFix ();
            dbfix.name  = csvs[0];
            dbfix.type  = csvs[4];
            dbfix.lat   = Double.parseDouble (csvs[1]);
            dbfix.lon   = Double.parseDouble (csvs[2]);
            allDBFixes.addLast (dbfix);
        }
        br5.close ();

        // Read in localizers to get their lat/lons.

        BufferedReader br10 = new BufferedReader (new FileReader (basedir + "/datums/localizers_" + cycles56expdate + ".csv"), 4096);
        while ((line = br10.readLine ()) != null) {
            String[] csvs = Lib.QuotedCSVSplit (line);
            DBFix dbfix = new DBFix ();
            dbfix.name  = csvs[1];  // eg, "I-BVY"
            dbfix.type  = csvs[0];  // eg, "ILS/DME"
            dbfix.lat   = Double.parseDouble (csvs[4]);
            dbfix.lon   = Double.parseDouble (csvs[5]);
            allDBFixes.addLast (dbfix);
        }
        br10.close ();

        // Read in navaids to get their lat/lons.

        BufferedReader br6 = new BufferedReader (new FileReader (basedir + "/datums/navaids_" + cycles56expdate + ".csv"), 4096);
        while ((line = br6.readLine ()) != null) {
            String[] csvs = Lib.QuotedCSVSplit (line);
            DBFix dbfix = new DBFix ();
            dbfix.name  = csvs[1];
            dbfix.type  = csvs[0];
            dbfix.lat   = Double.parseDouble (csvs[4]);
            dbfix.lon   = Double.parseDouble (csvs[5]);
            if (!csvs[6].equals ("")) dbfix.magvar = Double.parseDouble (csvs[6]);
            if (dbfix.name.equals ("OSH") && (dbfix.magvar < 0)) dbfix.magvar = - dbfix.magvar;
            allDBFixes.addLast (dbfix);
        }
        br6.close ();

        // Read in runways to get their lat/lons.

        BufferedReader br8 = new BufferedReader (new FileReader (basedir + "/datums/runways_" + cycles56expdate + ".csv"), 4096);
        while ((line = br8.readLine ()) != null) {
            String[] csvs = Lib.QuotedCSVSplit (line);
            String faaid  = csvs[0];
            Airport apt   = allAirports.get (faaid);
            if (apt == null) continue;
            apt.addRunway ("RW" + csvs[1],  // eg, "RW04L" for plate fix name
                    Double.parseDouble (csvs[4]),
                    Double.parseDouble (csvs[5]),
                    Double.parseDouble (csvs[6]),
                    Double.parseDouble (csvs[7]));
        }
        br8.close ();
    }

    /**
     * Airports and runways from database.
     */
    public static class Airport extends DBFix {
        public String faaid;
        public String stateid;
        public HashMap<String,Runway> runways;

        public Airport ()
        {
            type    = "AIRPORT";
            runways = new HashMap<> ();
        }

        /**
         * Add runway database definition to this airport.
         */
        public void addRunway (String number, double beglat, double beglon, double endlat, double endlon)
        {
            /*
             * Make a DBFix for the runway so it can be used as a GPS fix.
             */
            Runway rwy = new Runway ();
            rwy.name   = number;  // eg, "RW04R"
            rwy.lat    = beglat;
            rwy.lon    = beglon;
            rwy.endlat = endlat;
            rwy.endlon = endlon;
            runways.put (number, rwy);
        }

        /**
         * Get list of DBFixes near the airport.
         */
        public HashMap<String,DBFix> getNearbyDBFixes (double maxnm)
        {
            HashMap<String,DBFix> localfixes = new HashMap<> ();

            // make list of waypoints near the airport
            // - the airport itself (by icaoid)
            // - its runways (RWnnl)
            // - nearby navaids, fixes etc
            localfixes.put (name, this);
            for (Runway rwy : runways.values ()) {
                localfixes.put (rwy.name, rwy);
            }

            for (DBFix dbfix : allDBFixes) {

                // points must be within maxnm of the airport
                // and if duplicate, must be closer of the two
                double oldist = maxnm;
                DBFix oldbfix = localfixes.get (dbfix.name);
                if (oldbfix != null) {
                    oldist = Lib.LatLonDist ((float)lat, (float)lon,
                            (float)oldbfix.lat, (float)oldbfix.lon);
                }

                // add point if it meets those criteria
                double dist = Lib.LatLonDist ((float)lat, (float)lon,
                        (float)dbfix.lat, (float)dbfix.lon);
                if (dist < oldist) {
                    localfixes.put (dbfix.name, dbfix);
                }
            }

            return localfixes;
        }
    }

    /**
     * Single runway direction for an airport.
     */
    public static class Runway extends DBFix {
        public double endlat, endlon;

        public Runway ()
        {
            type = "RUNWAY";
        }
    }

    /**
     * A fix as defined in database (including navaids).
     */
    public interface GetDBFix {
        DBFix getDBFix ();
    }

    public static class DBFix implements GetDBFix {
        public final static double MAGVAR_MISSING = 999999.0;

        public String name;
        public String type;
        public double lat, lon;
        public double magvar = MAGVAR_MISSING;  // magnetic = true + magvar
        public boolean mentioned;

        @Override  // GetDBFix
        public DBFix getDBFix () { return this; }
    }

    public static class DMEDBFix extends DBFix {
        public DBFix navaid;
        public float distnm;

        // make up the dme fix's name given that the lat/lon and navaid are filled in
        public void makeName ()
        {
            // get base navaid
            DBFix nav = navaid;
            while (nav instanceof DMEDBFix) {
                nav = ((DMEDBFix) nav).navaid;
            }

            // get distance and heading from the base navaid to this dme fix
            double dist = Lib.LatLonDist (nav.lat, nav.lon, lat, lon);
            double hdg  = Lib.LatLonTC   (nav.lat, nav.lon, lat, lon);
            if (hdg < 0.0) hdg += 360.0;

            // make our name from the base navaid, distance and heading
            String diststr = String.format ("%3.1f", dist);
            String hdgstr  = String.format ("%3.1f", hdg);
            if (diststr.endsWith (".0")) diststr = diststr.substring (0, diststr.length () - 2);
            if (hdgstr.endsWith  (".0")) hdgstr  = hdgstr.substring  (0, hdgstr.length  () - 2);
            name = nav.name + dmeMark + diststr + "/" + hdgstr;
        }
    }
}
