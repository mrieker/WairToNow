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
 * Compare Instrument Approach Procedure georeference data to Avare
 *
 *  javac -Xlint:deprecation CmpIAPToAvare.java Lib.java
 *
 *  exec java CmpIAPToAvare < csvfile
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.TreeMap;

public class CmpIAPToAvare {
    private final static double maxFixDistNM = 75;
    private final static int csvDpi = 300;
    private final static int avareDpi = 150;
    private final static int leeway = 30;

    private static HashMap<String,Airport> allAirports = new HashMap<> ();  // by faaid
    private static HashMap<String,LinkedList<DBFix>> allDBFixeses = new HashMap<> ();
    private static TreeMap<String,Airport> allAptIcaos = new TreeMap<> ();  // by icaoid
    private static String basedir;
    private static String cycles28expdate;
    private static String cycles56expdate;

    public static void main (String[] args)
    {
        try {
            basedir = new File (CmpIAPToAvare.class.getProtectionDomain ().getCodeSource ().getLocation ().toURI ()).getParent ().toString ();

            // Read expiration dates so we know what cycle we are dealing with

            BufferedReader br1 = new BufferedReader (new FileReader (basedir + "/datums/aptplates_expdate.dat"), 256);
            cycles28expdate = br1.readLine ();
            br1.close ();

            BufferedReader br3 = new BufferedReader (new FileReader (basedir + "/datums/aptinfo_expdate.dat"), 256);
            cycles56expdate = br3.readLine ();
            br3.close ();

            // Read in airports to get their lat/lons.
            // KBVY,BVY,107.3,"BEVERLY MUNI",42.5841410277778,-70.9161444166667,...

            BufferedReader br4 = new BufferedReader (new FileReader (basedir + "/datums/airports_" + cycles56expdate + ".csv"), 4096);
            String line;
            while ((line = br4.readLine ()) != null) {
                String[] csvs = Lib.QuotedCSVSplit (line);
                Airport apt = new Airport ();
                apt.name    = csvs[0];  // icaoid
                apt.faaid   = csvs[1];
                apt.lat     = Double.parseDouble (csvs[4]);
                apt.lon     = Double.parseDouble (csvs[5]);
                apt.magvar  = Double.parseDouble (csvs[6]);
                allAirports.put (apt.faaid, apt);
                allAptIcaos.put (apt.name,  apt);
                addDBFix (apt);
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
                addDBFix (dbfix);
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
                addDBFix (dbfix);
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
                addDBFix (dbfix);
            }
            br6.close ();

            // Read in runways to get their lat/lons.

            BufferedReader br8 = new BufferedReader (new FileReader (basedir + "/datums/runways_" + cycles56expdate + ".csv"), 4096);
            while ((line = br8.readLine ()) != null) {
                String[] csvs = Lib.QuotedCSVSplit (line);
                String faaid  = csvs[0];
                Airport apt   = allAirports.get (faaid);
                apt.addRunway ("RW" + csvs[1],  // eg, "RW04L" for plate fix name
                        Double.parseDouble (csvs[4]),
                        Double.parseDouble (csvs[5]),
                        Double.parseDouble (csvs[6]),
                        Double.parseDouble (csvs[7]));
            }
            br8.close ();

            // Read in Avare database.

            BufferedReader br11 = new BufferedReader (new FileReader (basedir + "/avare_geoplates.dat"), 4096);
            while ((line = br11.readLine ()) != null) {
                String[] csvs = line.split ("[|]");
                int i = csvs[0].indexOf ('/');
                String faaid = csvs[0].substring (0, i);
                String plate = csvs[0].substring (++ i);
                Airport airport = allAirports.get (faaid);
                if (airport == null) {
                    // System.out.println ("avare has airport faaid <" + faaid + "> that is not in our airports file");
                } else {
                    plate = makeAvPlateKey (plate);
                    if (!plate.equals ("AREA")) {
                        Avare avare = new Avare ();
                        avare.dx  = Float.parseFloat (csvs[1]);
                        avare.dy  = Float.parseFloat (csvs[2]);
                        avare.lon = Float.parseFloat (csvs[3]);
                        avare.lat = Float.parseFloat (csvs[4]);
                        airport.avares.put (plate, avare);
                    }
                }
            }
            br11.close ();

            // Read state file csv lines from stdin and save the mappings

            BufferedReader br9 = new BufferedReader (new InputStreamReader (System.in), 4096);
            while ((line = br9.readLine ()) != null) {
                String[] csvs = Lib.QuotedCSVSplit (line);
                String icaoid = csvs[0];
                String plate  = csvs[1];
                String fixid  = csvs[2];

                if (plate.startsWith ("IAP-")) {
                    Airport airport = allAptIcaos.get (icaoid);
                    if (airport == null) {
                        System.out.println ("stdin has airport icaoid <" + icaoid + "> that is not in our airports file");
                    } else {
                        DBFix bestdbfix = airport.runways.get (fixid);
                        if (bestdbfix == null) {
                            double bestdist = maxFixDistNM;
                            LinkedList<DBFix> dbfixes = allDBFixeses.get (fixid);
                            if (dbfixes != null) {
                                for (DBFix dbfix : dbfixes) {
                                    double dist = Lib.LatLonDist (dbfix.lat, dbfix.lon, airport.lat, airport.lon);
                                    if (bestdist > dist) {
                                        bestdist = dist;
                                        bestdbfix = dbfix;
                                    }
                                }
                            }
                        }
                        if (bestdbfix == null) {
                            System.out.println ("stdin has fix <" + fixid + "> near airport icaoid <" + icaoid + "> that is not in any of our fixes files");
                        } else {
                            LinkedList<OurMap> ourmaps = airport.ourmapses.get (plate);
                            if (ourmaps == null) {
                                ourmaps = new LinkedList<> ();
                                airport.ourmapses.put (plate, ourmaps);
                            }
                            OurMap ourmap = new OurMap ();
                            ourmap.dbfix = bestdbfix;
                            ourmap.pixx = Integer.parseInt (csvs[3]);
                            ourmap.pixy = Integer.parseInt (csvs[4]);
                            ourmaps.addLast (ourmap);
                        }
                    }
                }
            }
            br9.close ();

            // Compare the two databases

            for (Airport airport : allAptIcaos.values ()) {

                // loop through all plates we have decoded for this airport

                for (String ourplate : airport.ourmapses.keySet ()) {

                    // get corresponding avare plate

                    String avplate = makeAvPlateKey (ourplate.substring (4));
                    Avare avare = airport.avares.get (avplate);
                    if (avare == null) {
                        System.out.println ("avare missing faaid <" + airport.faaid + "> plate <" + avplate + ">");
                    } else {

                        // loop through each database fix we have mapped for this plate (should always be 2)

                        LinkedList<OurMap> ourmaps = airport.ourmapses.get (ourplate);

                        //OurMap ourmap1 = ourmaps.getFirst ();
                        //OurMap ourmap2 = ourmaps.getLast ();
                        //double ourdx = (ourmap2.pixx - ourmap1.pixx) / (ourmap2.dbfix.lon - ourmap1.dbfix.lon);
                        //double ourdy = (ourmap2.pixy - ourmap1.pixy) / (ourmap2.dbfix.lat - ourmap1.dbfix.lat);
                        //System.out.println ("ourdx,dy=" + ourdx + "," + ourdy + " avaredx,dy=" + avare.dx + "," + avare.dy + " ratios=" + (ourdx / avare.dx) + "," + (ourdy / avare.dy));

                        int ngood = 0;
                        for (OurMap ourmap : ourmaps) {
                            DBFix dbfix  = ourmap.dbfix;

                            // see where avare would map that pixel to the plate

                            double avpixx = (dbfix.lon - avare.lon) * avare.dx * csvDpi / avareDpi;
                            double avpixy = (dbfix.lat - avare.lat) * avare.dy * csvDpi / avareDpi;

                            // compare it to where we would map that pixel to the plate

                            int diff = (int) (Math.hypot (avpixx - ourmap.pixx, avpixy - ourmap.pixy) + 0.5);
                            if (diff > leeway) {
                                System.out.println (String.format ("%09d", diff) + " mismatch faaid <" + airport.faaid + "> plate <" + ourplate + "> fix <" + dbfix.name + ">");
                                break;
                            } else {
                                ngood ++;
                            }
                        }
                        if (ngood == ourmaps.size ()) {
                            System.out.println ("verified icaoid <" + airport.name + "> plate <" + ourplate + ">");
                        }
                    }
                }

                // loop through all plates avare has decoded for this airport

                for (String avplate : airport.avares.keySet ()) {
                    boolean found = false;
                    for (String ourplate : airport.ourmapses.keySet ()) {
                        String ouravname = makeAvPlateKey (ourplate.substring (4));
                        found |= ouravname.equals (avplate);
                    }
                    if (!found) {
                        System.out.println ("we are missing icaoid <" + airport.name + "> plate <" + avplate + ">");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace ();
            System.exit (1);
        }
    }

    private static void addDBFix (DBFix dbfix)
    {
        LinkedList<DBFix> dbfixes = allDBFixeses.get (dbfix.name);
        if (dbfixes == null) {
            dbfixes = new LinkedList<> ();
            allDBFixeses.put (dbfix.name, dbfixes);
        }
        dbfixes.addLast (dbfix);
    }

    /**
     * Take either our string or an avare plate string and make the key used for the airport.avares hash.
     */
    private static String makeAvPlateKey (String id)
    {
        return id.replace (" ", "").replace ("-", "").replace (".png", "").replace ("(", "").replace (")", "");
    }

    /**
     * Airports and runways from database.
     */
    private static class Airport extends DBFix {
        public String faaid;
        public HashMap<String,Avare> avares;
        public HashMap<String,Runway> runways;
        public double magvar;  // magnetic = true + magvar
        public TreeMap<String,LinkedList<OurMap>> ourmapses;

        public Airport ()
        {
            type      = "AIRPORT";
            runways   = new HashMap<> ();
            avares    = new HashMap<> ();
            ourmapses = new TreeMap<> ();
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
    }

    public static class Avare {
        public float dx, dy, lat, lon;
    }

    public static class OurMap {
        public DBFix dbfix;
        public int pixx, pixy;
    }

    /**
     * Single runway direction for an airport.
     */
    private static class Runway extends DBFix {
        public double endlat, endlon;

        public Runway ()
        {
            type = "RUNWAY";
        }
    }

    /**
     * A fix as defined in database (including navaids).
     */
    private static class DBFix {
        public String name;
        public String type;
        public double lat, lon;
    }
}
