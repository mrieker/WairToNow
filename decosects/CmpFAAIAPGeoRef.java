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

/**
 * Compare FAA-provided IAP geo refs to WTN-derived IAP geo refs
 * Takes about 2 minutes to run
 *
 *  rm -f CmpFAAIAPGeoRef.jar CmpFAAIAPGeoRef*.class
 *  javac CmpFAAIAPGeoRef.java Lib.java
 *  jar cf CmpFAAIAPGeoRef.jar CmpFAAIAPGeoRef*.class Lib*.class
 *  rm -f CmpFAAIAPGeoRef*.class
 *
 *  export CLASSPATH=CmpFAAIAPGeoRef.jar
 *  export next28=0 or 1
 *  cycles28=`./cureffdate -28 -x yyyymmdd`
 *  cycles56=`./cureffdate     -x yyyymmdd`
 *  cat datums/iapgeorefs*_$cycles28/*.csv | java CmpFAAIAPGeoRef -cycles28 $cycles28 -cycles56 $cycles56 | sort
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;

public class CmpFAAIAPGeoRef {
    private final static double maxFixDistNM = 75;

    private static HashMap<String,Airport> allfaaids = new HashMap<> ();
    private static TreeMap<String,Airport> allicaoids = new TreeMap<> ();
    private static LinkedList<DBFix> allDBFixes = new LinkedList<> ();
    private static HashMap<String,DBFix> nearDBFixes = new HashMap<> ();
    private static String basedir;
    private static String cycles28expdate;
    private static String cycles56expdate;

    public static void main (String[] args)
    {
        try {
            basedir = new File (CmpFAAIAPGeoRef.class.getProtectionDomain ().getCodeSource ().getLocation ().toURI ()).getParent ().toString ();

            // Decode command line

            for (int i = 0; i < args.length;) {
                String arg = args[i++];
                if (arg.startsWith ("-")) {
                    if (arg.equals ("-cycles28") && (i < args.length)) {
                        cycles28expdate = args[i++];
                        continue;
                    }
                    if (arg.equals ("-cycles56") && (i < args.length)) {
                        cycles56expdate = args[i++];
                        continue;
                    }
                    System.err.println ("unknown option " + arg);
                    System.exit (1);
                }
                System.err.println ("unknown parameter " + arg);
                System.exit (1);
            }

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
                allfaaids.put (apt.faaid, apt);
                allicaoids.put (apt.name, apt);
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
                Airport apt   = allfaaids.get (faaid);
                if (apt == null) continue;
                apt.addRunway ("RW" + csvs[1],  // eg, "RW04L" for plate fix name
                        Double.parseDouble (csvs[4]),
                        Double.parseDouble (csvs[5]),
                        Double.parseDouble (csvs[6]),
                        Double.parseDouble (csvs[7]));
            }
            br8.close ();

            // Read state file csv lines from stdin and process all those plates

            BufferedReader br7 = new BufferedReader (new InputStreamReader (System.in), 4096);
            while ((line = br7.readLine ()) != null) {
                String[] csvs = Lib.QuotedCSVSplit (line);
                if (csvs.length > 2) {
                    Airport apt = allicaoids.get (csvs[0]);
                    Plate plate = apt.plates.get (csvs[1]);
                    if (plate == null) {
                        plate = new Plate ();
                        plate.icaoid = csvs[0];
                        plate.plateid = csvs[1];
                        plate.wtngeorefs = new LinkedList<> ();
                        apt.plates.put (csvs[1], plate);
                    }

                    if (csvs.length == 14) {
                        plate.faageoref = new FaaGeoRef (csvs);
                    } else if (csvs.length == 5) {
                        plate.wtngeorefs.addLast (new WtnGeoRef (csvs));
                    }
                }
            }
            br7.close ();

            for (Airport apt : allicaoids.values ()) {
                if (apt.plates.size () > 0) {
                    BuildNearDBFixes (apt);
                    for (Plate plate : apt.plates.values ()) {
                        if (plate.faageoref != null) {
                            FaaGeoRef faageoref = plate.faageoref;
                            for (WtnGeoRef wtngeoref : plate.wtngeorefs) {
                                wtngeoref.compare (faageoref);
                            }
                        }
                    }
                }
            }

            return;
        } catch (Exception e) {
            e.printStackTrace ();
            System.exit (1);
        }
    }

    private static class Plate {
        public FaaGeoRef faageoref;
        public LinkedList<WtnGeoRef> wtngeorefs;
        public String icaoid;
        public String plateid;
    }

    private static class FaaGeoRef {
        private LambertConicalConformal lcc;
        private Point p = new Point ();

        public FaaGeoRef (String[] csvs)
        {
            lcc = new LambertConicalConformal (
                Double.parseDouble (csvs[ 2]),
                Double.parseDouble (csvs[ 3]),
                Double.parseDouble (csvs[ 4]),
                Double.parseDouble (csvs[ 5]),
                Double.parseDouble (csvs[ 6]),
                Double.parseDouble (csvs[ 7]),
                new double[] {
                    Double.parseDouble (csvs[ 8]),
                    Double.parseDouble (csvs[ 9]),
                    Double.parseDouble (csvs[10]),
                    Double.parseDouble (csvs[11]),
                    Double.parseDouble (csvs[12]),
                    Double.parseDouble (csvs[13])
                }
            );
        }

        public int latlon2pixx (double lat, double lon)
        {
            lcc.LatLon2ChartPixelExact (lat, lon, p);
            return p.x;
        }

        public int latlon2pixy (double lat, double lon)
        {
            lcc.LatLon2ChartPixelExact (lat, lon, p);
            return p.y;
        }
    }

    private static class WtnGeoRef {
        private String icaoid;
        private String plateid;
        private String waypt;
        private int pixx, pixy;

        public WtnGeoRef (String[] csvs)
        {
            icaoid  = csvs[0];
            plateid = csvs[1];
            waypt   = csvs[2];
            pixx    = Integer.parseInt (csvs[3]);
            pixy    = Integer.parseInt (csvs[4]);
        }

        public void compare (FaaGeoRef faageoref)
        {
            String ident = waypt;
            double dmedist = 0.0;
            double dmeangl = 0.0;
            int i = waypt.indexOf ('[');
            if (i >= 0) {
                int j   = waypt.indexOf ('/', i);
                ident   = waypt.substring (0, i);
                dmedist = Double.parseDouble (waypt.substring (i + 1, j));
                dmeangl = Double.parseDouble (waypt.substring (j + 1));
            }

            DBFix dbfix = nearDBFixes.get (ident);
            if (dbfix != null) {
                double lat = dbfix.lat;
                double lon = dbfix.lon;
                if (dmedist != 0.0) {
                    lat = Lib.LatHdgDist2Lat    (dbfix.lat, dmeangl, dmedist);
                    lon = Lib.LatLonHdgDist2Lon (dbfix.lat, dbfix.lon, dmeangl, dmedist);
                }
                int faax = faageoref.latlon2pixx (lat, lon);
                int faay = faageoref.latlon2pixy (lat, lon);
                double dist = Math.hypot (pixx - faax, pixy - faay);
                System.out.println (String.format ("%5.1f", dist) + " / " + waypt + " / " + plateid + " / " + icaoid);
            } else {
                System.err.println ("  " + ident + " not found");
            }
        }
    }

    private static void BuildNearDBFixes (Airport airport)
    {
        // Filter out fixes more than 50nm away from airport, no chart goes that far.
        // This helps us from trying to decode spurious strings as fix names and
        // helps us avoid duplicate name problems.

        nearDBFixes.clear ();
        for (DBFix dbfix : allDBFixes) {
            if (Lib.LatLonDist (dbfix.lat, dbfix.lon, airport.lat, airport.lon) <= maxFixDistNM) {
                dbfix.mentioned = false;
                nearDBFixes.put (dbfix.name, dbfix);
            }
        }

        // Also add in runways as fixes cuz some plates use them for fixes.

        for (Runway rwy : airport.runways.values ()) {
            nearDBFixes.put (rwy.name, rwy);
        }
    }

    /**
     * Airports and runways from database.
     */
    private static class Airport extends DBFix {
        public String faaid;
        public TreeMap<String,Plate> plates;
        public HashMap<String,Runway> runways;

        public Airport ()
        {
            type    = "AIRPORT";
            plates  = new TreeMap<> ();
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

    private static class DBFix {
        public final static double MAGVAR_MISSING = 999999.0;

        public String name;
        public String type;
        public double lat, lon;
        public double magvar = MAGVAR_MISSING;  // magnetic = true + magvar
        public boolean mentioned;
    }

    private static class Point {
        public int x, y;
    }

    /**
     * Lambert Conical Conformal chart projection.
     */
    private static class LambertConicalConformal {
        private double pixelsize;
        private double e_e, e_F_rada, e_lam0, e_n, e_phi0, e_rho0;
        private double tfw_a, tfw_b, tfw_c, tfw_d, tfw_e, tfw_f;
        private double wft_a, wft_b, wft_c, wft_d, wft_e, wft_f;

        public LambertConicalConformal (double centerLat, double centerLon,
                                        double stanPar1, double stanPar2,
                                        double rada, double radb, double[] tfws)
        {
            /*
             * Compute projection parameters.
             *
             * Map Projections -- A Working Manual
             * John P. Snyder
             * US Geological Survey Professional Paper 1395
             * http://pubs.usgs.gov/pp/1395/report.pdf
             * see pages viii, v107, v296, ellipsoidal projection
             */
            e_lam0     = Math.toRadians (centerLon);
            e_phi0     = Math.toRadians (centerLat);
            double phi1 = Math.toRadians (stanPar1);
            double phi2 = Math.toRadians (stanPar2);

            e_e = Math.sqrt (1 - (radb * radb) / (rada * rada));

            // v108: equation 14-15
            double m1 = eq1415 (phi1);
            double m2 = eq1415 (phi2);

            // v108: equation 15-9a
            double t0 = eq159a (e_phi0);
            double t1 = eq159a (phi1);
            double t2 = eq159a (phi2);

            // v108: equation 15-8
            e_n = (double) ((Math.log (m1) - Math.log (m2)) / (Math.log (t1) - Math.log (t2)));

            // v108: equation 15-10
            double F = (double) (m1 / (e_n * Math.pow (t1, e_n)));
            e_F_rada = F * rada;

            // v108: equation 15-7a
            e_rho0 = (double) (e_F_rada * Math.pow (t0, e_n));

            /*
             * tfw_a..f = convert pixel to easting/northing
             */
            tfw_a = tfws[0];
            tfw_b = tfws[1];
            tfw_c = tfws[2];
            tfw_d = tfws[3];
            tfw_e = tfws[4];
            tfw_f = tfws[5];

            pixelsize = Math.hypot (tfw_b, tfw_d);

            /*
             * Compute wft_a..f = convert easting/northing to pixel:
             *                [ tfw_a tfw_b 0 ]
             *    [ x y 1 ] * [ tfw_c tfw_d 0 ] = [ e n 1 ]
             *                [ tfw_e tfw_f 1 ]
             *
             * So if wft = inv (tfw):
             *
             *                [ tfw_a tfw_b 0 ]   [ wft_a wft_b 0 ]               [ wft_a wft_b 0 ]
             *    [ x y 1 ] * [ tfw_c tfw_d 0 ] * [ wft_c wft_d 0 ] = [ e n 1 ] * [ wft_c wft_d 0 ]
             *                [ tfw_e tfw_f 1 ]   [ wft_e wft_f 1 ]               [ wft_e wft_f 1 ]
             *
             *                            [ wft_a wft_b 0 ]
             *    [ x y 1 ] = [ e n 1 ] * [ wft_c wft_d 0 ]
             *                            [ wft_e wft_f 1 ]
             */
            double[][] mat = new double[][] {
                    new double[] { tfw_a, tfw_b, 0, 1, 0 },
                    new double[] { tfw_c, tfw_d, 0, 0, 1 },
                    new double[] { tfw_e, tfw_f, 1, 0, 0 }
            };
            Lib.RowReduce (mat);
            wft_a = mat[0][3];
            wft_b = mat[0][4];
            wft_c = mat[1][3];
            wft_d = mat[1][4];
            wft_e = mat[2][3];
            wft_f = mat[2][4];
        }

        /**
         * Given a lat/lon, compute pixel within the chart
         *
         * @param lat = latitude within the chart
         * @param lon = longitude within the chart
         * @param p   = where to return corresponding pixel
         */
        public void LatLon2ChartPixelExact (double lat, double lon, Point p)
        {
            double phi = Math.toRadians (lat);
            double lam = Math.toRadians (lon);

            while (lam < e_lam0 - Math.PI) lam += Math.PI * 2.0F;
            while (lam > e_lam0 + Math.PI) lam -= Math.PI * 2.0F;

            /*
             * Calculate number of metres east of Longitude_of_Central_Meridian
             * and metres north of Latitude_of_Projection_Origin using Lambert
             * Conformal Ellipsoidal Projection formulae.
             */
            // v108: equation 15-9a
            double t = eq159a (phi);

            // v108: equation 15-7
            double rho = (double) (e_F_rada * Math.pow (t, e_n));

            // v108: equation 14-4
            double theta = e_n * (lam - e_lam0);

            // v107: equation 14-1 -> how far east of centerLon
            double easting = rho * Math.sin (theta);

            // v107: equation 14-2 -> how far north of centerLat
            double northing = e_rho0 - rho * Math.cos (theta);

            /*
             * Compute corresponding image pixel number.
             */
            p.x = (int) Math.round (easting * wft_a + northing * wft_c + wft_e);
            p.y = (int) Math.round (easting * wft_b + northing * wft_d + wft_f);
        }

        /**
         * Given a pixel within the chart, compute corresponding lat/lon
         *
         * @param x  = pixel within the entire side of the sectional
         * @param y  = pixel within the entire side of the sectional
         * @param ll = where to return corresponding lat/lon
         */
        /**public void ChartPixel2LatLonExact (double x, double y, LatLon ll)
        {
            // opposite steps of LatLon2ChartPixelExact()

            double easting  = tfw_a * x + tfw_c * y + tfw_e;
            double northing = tfw_b * x + tfw_d * y + tfw_f;

            // easting = rho * sin (theta)
            // easting / rho = sin (theta)

            // northing = e_rho0 - rho * cos (theta)
            // rho * cos (theta) = e_rho0 - northing
            // cos (theta) = (e_rho0 - northing) / rho

            double theta = (double) Math.atan (easting / (e_rho0 - northing));

            // theta = e_n * (lam - e_lam0)
            // theta / e_n = lam - e_lam0
            // theta / e_n + e_lam0 = lam

            double lam = theta / e_n + e_lam0;

            // v108: equation 14-4
            double costheta = Math.cos (e_n * (lam - e_lam0));

            // must calculate phi (latitude) with successive approximation
            // usually takes 3 or 4 iterations to resolve latitude within one pixel

            double phi = e_phi0;
            double metresneedtogonorth;
            do {
                // v108: equation 15-9a
                double t = eq159a (phi);

                // v108: equation 15-7
                double rho = (double) (e_F_rada * Math.pow (t, e_n));

                // v107: equation 14-2 -> how far north of centerLat
                double n = e_rho0 - rho * costheta;

                // update based on how far off our guess is
                // - we are trying to get phi that gives us 'northing'
                // - but we have phi that gives us 'n'
                metresneedtogonorth = northing - n;
                phi += metresneedtogonorth / (Lib.MPerNM * Lib.NMPerDeg * 180.0F / Math.PI);
            } while (Math.abs (metresneedtogonorth) > pixelsize);

            ll.lat = Math.toDegrees (phi);
            ll.lon = Lib.NormalLon (Math.toDegrees (lam));
        }**/

        private double eq1415 (double phi)
        {
            double w = e_e * Math.sin (phi);
            return Math.cos (phi) / Math.sqrt (1 - w * w);
        }

        private double eq159a (double phi)
        {
            double sinphi = Math.sin (phi);
            double u = (1 - sinphi) / (1 + sinphi);
            double v = (1 + e_e * sinphi) / (1 - e_e * sinphi);
            return Math.sqrt (u * Math.pow (v, e_e));
        }
    }
}
