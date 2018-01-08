//    Copyright (C) 2016, Mike Rieker, Beverly, MA USA
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
 * Attempt to print PDF geo-ref information
 *
 *  export CLASSPATH=:pdfbox-1.8.10.jar:commons-logging-1.2.jar
 *
 *  javac -Xlint:deprecation PDFGeoRef.java Lib.java
 *
 *  java PDFGeoRef <pdffile>
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageNode;

public class PDFGeoRef {
    private final static int pdfDpi =  72;  // PDFs are defined with 72dpi units
    private final static int csvDpi = 300;  // database is set up for 300dpi pixels

    private static PDPage pdPage;

    public static void main (String[] args) throws Exception
    {
        String pdfName = args[0];

        PDDocument pddoc = PDDocument.load (pdfName);

        PDDocumentCatalog doccat = pddoc.getDocumentCatalog ();
        PDPageNode pages = doccat.getPages ();
        LinkedList<Object> kids = new LinkedList<Object> ();
        pages.getAllKids (kids);
        if (kids.size () != 1) throw new Exception ("pdf not a single Page");
        pdPage = (PDPage) kids.get (0);
        printCOSObject ("", "pdPage", pdPage.getCOSObject ());

        /*
         * See if PDF contains FAA-supplied geo-referencing info
         *
         *   pdPage = COSDictionary
         *     .VP = COSArray
         *       [0] = COSDictionary
         *         .BBox = COSArray of 4 COSFloats
         *           [ 9.110192 2.772000 378.249808 591.228000 ]
         *         .Measure = COSDictionary
         *           .GPTS = COSArray of 8 COSFloats
         *             [ 42.258538 -71.337998 42.258671 -70.704252 43.008831 -70.700978 43.008697 -71.341849 ]
         *           .LPTS = COSArray of 8 COSFLoats relative to BBox
         *             [  0.100000   0.100000  0.900000   0.100000  0.900000   0.900000  0.100000   0.900000 ]
         */
        COSDictionary pagedict = pdPage.getCOSDictionary ();
        COSArray vparray = (COSArray) pagedict.getDictionaryObject ("VP");
        COSDictionary vp0dict = (COSDictionary) vparray.getObject (0);
        COSArray bbox = (COSArray) vp0dict.getDictionaryObject ("BBox");
        COSDictionary measure = (COSDictionary) vp0dict.getDictionaryObject ("Measure");
        COSDictionary gcs = (COSDictionary) measure.getDictionaryObject ("GCS");
        COSArray gpts = (COSArray) measure.getDictionaryObject ("GPTS");
        COSArray lpts = (COSArray) measure.getDictionaryObject ("LPTS");

        // extract floats from the BBox array
        float[] bboxBins = bbox.toFloatArray ();

        // extract floats from the /GPTS and /LPTS arrays
        float[] gptBins = gpts.toFloatArray ();
        float[] lptBins = lpts.toFloatArray ();

        // transform /LPTS to gif pixel numbers
        for (int i = 0; i < 8; i += 2) {
            lptBins[i+0] = ((bboxBins[2] - bboxBins[0]) * lptBins[i+0] + bboxBins[0]) / pdfDpi * csvDpi;
            lptBins[i+1] = ((bboxBins[1] - bboxBins[3]) * lptBins[i+1] + bboxBins[3]) / pdfDpi * csvDpi;
        }

        // extract well-known text and set up Lcc conversion
        String wkt = gcs.getString ("WKT");
        Lcc lcc = new Lcc (wkt, gptBins, lptBins);

        /*
         * Write result to datums/iapgeorefs2_<expdate>.csv
         */
        System.out.println (
            lcc.centerLat + "," + lcc.centerLon + "," + lcc.stanPar1 + "," + lcc.stanPar2 + "," +
            lcc.rada + "," + lcc.radb + "," + lcc.tfw_a + "," + lcc.tfw_b + "," +
            lcc.tfw_c + "," + lcc.tfw_d + "," + lcc.tfw_e + "," + lcc.tfw_f);
    }

    /**
     * Utility to print out contents of a COSBase object.
     */
    private static int numdigs = 5;
    private static String spacer = "        ";  // numdigs+3 spaces ("<ddddd> ")
    private static HashMap<COSBase,String> seenObjects = new HashMap<COSBase,String> ();

    private static void printCOSObject (String prefix, String name, COSBase obj)
    {
        String n;
        if (seenObjects.containsKey (obj)) {
            n = seenObjects.get (obj);
            String val = "";
            if ((obj instanceof COSFloat) || (obj instanceof COSInteger) || (obj instanceof COSName) || (obj instanceof COSString)) {
                val = " " + obj.toString ();
            }
            System.out.println (spacer + prefix + name + ": " + n + val);
            return;
        }
        n = Integer.toString (seenObjects.size () + 1);
        while (n.length () < numdigs) n = "0" + n;
        n = "<" + n + "> ";
        seenObjects.put (obj, n);

        if (obj instanceof COSArray) {
            System.out.println (n + prefix + name + ": COSArray {");
            int i = 0;
            for (COSBase elem : (COSArray) obj) {
                printCOSObject (prefix + "  ", "[" + i + "]", elem);
                i ++;
            }
            System.out.println (n + prefix + "}");
        } else if (obj instanceof COSDictionary) {
            System.out.println (n + prefix + name + ": COSDictionary {");
            Set<Map.Entry<COSName,COSBase>> entries = ((COSDictionary) obj).entrySet ();
            for (Map.Entry<COSName,COSBase> entry : entries) {
                COSName key = entry.getKey ();
                COSBase val = entry.getValue ();
                printCOSObject (prefix + "  ", key.getName (), val);
            }
            System.out.println (n + prefix + "}");
        } else if (obj instanceof COSObject) {
            System.out.println (n + prefix + name + ": " + obj.toString ());
            COSBase encap = ((COSObject) obj).getObject ();
            if (encap != null) {
                printCOSObject (prefix + "  ", "encap", encap);
            }
        } else {
            System.out.println (n + prefix + name + ": " + obj.toString ());
        }
    }

    /**
     * Lambert Conical Conformal charts.
     */
    private static class Lcc {
        public double centerLat;
        public double centerLon;
        public double stanPar1;
        public double stanPar2;
        public double rada;
        public double radb;
        public double tfw_a, tfw_b, tfw_c, tfw_d, tfw_e, tfw_f;
        public double wft_a, wft_b, wft_c, wft_d, wft_e, wft_f;

        private double pixelsize;
        private double e_e, e_F_rada, e_lam0, e_n, e_phi0, e_rho0;

        //  WKT: COSString{
        //    PROJCS[
        //      "",
        //      GEOGCS[
        //        "GCS_North_American_1983",
        //        DATUM[
        //          "D_North_American_1983",
        //          SPHEROID[
        //            "GRS_1980",
        //            6378137.000,
        //            298.25722210
        //          ]
        //        ],
        //        PRIMEM["Greenwich",0],
        //        UNIT["Degree",0.017453292519943295]
        //      ],
        //      PROJECTION["Lambert_Conformal_Conic"],
        //      PARAMETER["False_Easting",0.000],
        //      PARAMETER["False_Northing",0.000],
        //      PARAMETER["Central_Meridian",-70.99547222222222],
        //      PARAMETER["Latitude_Of_Origin",42.65519444444441],
        //      PARAMETER["Standard_Parallel_1",45.00000000000001],
        //      PARAMETER["Standard_Parallel_2",33.00000000000001],
        //      UNIT["Inch",0.02540005080010]
        //    ]
        //  }
        //
        //  gpts = array of four lat,lon pairs
        //  lpts = array of four x,y pairs where the lat,lons are on the final gif
        public Lcc (String wkt, float[] gpts, float[] lpts)
        {
            /*
             * Parse the well-known text string to get Lcc parameters.
             */
            centerLat = Double.NaN;
            centerLon = Double.NaN;
            stanPar1  = Double.NaN;
            stanPar2  = Double.NaN;
            rada      = Double.NaN;
            radb      = Double.NaN;

            NamedList projcs = parseWKT (wkt);
            if (!projcs.name.equals ("PROJCS")) throw new RuntimeException ("WKT top-level not PROJCS");
            for (Object projcselem : projcs.list) {
                if (projcselem instanceof NamedList) {
                    NamedList nl = (NamedList) projcselem;
                    if (nl.name.equals ("GEOGCS")) {
                        for (Object gcselem : nl.list) {
                            if ((gcselem instanceof NamedList) && ((NamedList) gcselem).name.equals ("DATUM")) {
                                for (Object datelem : ((NamedList) gcselem).list) {
                                    if ((datelem instanceof NamedList) && ((NamedList) datelem).name.equals ("SPHEROID")) {
                                        Object[] spheroid = ((NamedList) datelem).list;
                                        rada = (Double) spheroid[1];  // equatorial 6378137
                                        double invf = (Double) spheroid[2];  // 1/f
                                        // https://en.wikipedia.org/wiki/GRS_80
                                        // invf = rada / (rada - radb)
                                        radb = rada - rada / invf;    // polar 6356752
                                    }
                                }
                            }
                        }
                    }
                    if (nl.name.equals ("PROJECTION")) {
                        if (!((String) nl.list[0]).equals ("Lambert_Conformal_Conic")) {
                            throw new RuntimeException ("projection not Lambert_Conformal_Conic");
                        }
                    }
                    if (nl.name.equals ("PARAMETER")) {
                        if (((String) nl.list[0]).equals ("Central_Meridian")) {
                            centerLon = (Double) nl.list[1];
                        }
                        if (((String) nl.list[0]).startsWith ("False_")) {
                            if ((Double) nl.list[1] != 0.0) {
                                throw new RuntimeException (((String) nl.list[0]) + " not zero");
                            }
                        }
                        if (((String) nl.list[0]).equals ("Latitude_Of_Origin")) {
                            centerLat = (Double) nl.list[1];
                        }
                        if (((String) nl.list[0]).equals ("Standard_Parallel_1")) {
                            stanPar1  = (Double) nl.list[1];
                        }
                        if (((String) nl.list[0]).equals ("Standard_Parallel_2")) {
                            stanPar2  = (Double) nl.list[1];
                        }
                    }
                }
            }

            /*
             * Compute projection parameters.
             *
             * Map Projections -- A Working Manual
             * John P. Snyder
             * US Geological Survey Professional Paper 1395
             * http://pubs.usgs.gov/pp/1395/report.pdf
             * see pages viii, v107, v296, ellipsoidal projection
             */
            pixelsize   = Math.hypot (tfw_b, tfw_d);
            e_lam0      = Math.toRadians (centerLon);
            e_phi0      = Math.toRadians (centerLat);
            double phi1 = Math.toRadians (stanPar1);
            double phi2 = Math.toRadians (stanPar2);

            e_e = Math.sqrt (1 - (radb * radb) / (rada * rada));  // 0.081819191

            // v108: equation 14-15
            double m1 = eq1415 (phi1);
            double m2 = eq1415 (phi2);

            // v108: equation 15-9a
            double t0 = eq159a (e_phi0);
            double t1 = eq159a (phi1);
            double t2 = eq159a (phi2);

            // v108: equation 15-8
            e_n = (Math.log (m1) - Math.log (m2)) / (Math.log (t1) - Math.log (t2));

            // v108: equation 15-10
            double F = m1 / (e_n * Math.pow (t1, e_n));
            e_F_rada = F * rada;

            // v108: equation 15-7a
            e_rho0 = e_F_rada * Math.pow (t0, e_n);

            /*
             * Compute tfw_a..f = convert pixel to easting/northing
             * using the given gpts and lpts arrays.
             *
             *  easting  = tfw_a * x + tfw_c * y + tfw_e;
             *  northing = tfw_b * x + tfw_d * y + tfw_f;
             */
            double[][] tfws = new double[][] {
                new double[] { lpts[0], 0, lpts[1], 0, 1, 0, calcEasting  (gpts[0], gpts[1]) },
                new double[] { 0, lpts[0], 0, lpts[1], 0, 1, calcNorthing (gpts[0], gpts[1]) },
                new double[] { lpts[2], 0, lpts[3], 0, 1, 0, calcEasting  (gpts[2], gpts[3]) },
                new double[] { 0, lpts[2], 0, lpts[3], 0, 1, calcNorthing (gpts[2], gpts[3]) },
                new double[] { lpts[4], 0, lpts[5], 0, 1, 0, calcEasting  (gpts[4], gpts[5]) },
                new double[] { 0, lpts[4], 0, lpts[5], 0, 1, calcNorthing (gpts[4], gpts[5]) }
            };
            Lib.RowReduce (tfws);
            tfw_a = tfws[0][6];
            tfw_b = tfws[1][6];
            tfw_c = tfws[2][6];
            tfw_d = tfws[3][6];
            tfw_e = tfws[4][6];
            tfw_f = tfws[5][6];

            /*
             * tfw_a..f = convert pixel to easting/northing
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
            double[][] wfts = new double[][] {
                    new double[] { tfw_a, tfw_b, 0, 1, 0 },
                    new double[] { tfw_c, tfw_d, 0, 0, 1 },
                    new double[] { tfw_e, tfw_f, 1, 0, 0 }
            };
            Lib.RowReduce (wfts);
            wft_a = wfts[0][3];
            wft_b = wfts[0][4];
            wft_c = wfts[1][3];
            wft_d = wfts[1][4];
            wft_e = wfts[2][3];
            wft_f = wfts[2][4];
        }

        private static class NamedList {
            public String name;
            public Object[] list;
            public NamedList (String n)
            {
                name = n;
            }
        }

        /**
         * Parse the well-known text.
         */
        //    PROJCS[
        //      "",
        //      GEOGCS[
        //        "GCS_North_American_1983",
        //        DATUM[
        //          "D_North_American_1983",
        //          SPHEROID[
        //            "GRS_1980",
        //            6378137.000,
        //            298.25722210
        //          ]
        //        ],
        //        PRIMEM["Greenwich",0],
        //        UNIT["Degree",0.017453292519943295]
        //      ],
        //      PROJECTION["Lambert_Conformal_Conic"],
        //      PARAMETER["False_Easting",0.000],
        //      PARAMETER["False_Northing",0.000],
        //      PARAMETER["Central_Meridian",-70.99547222222222],
        //      PARAMETER["Latitude_Of_Origin",42.65519444444441],
        //      PARAMETER["Standard_Parallel_1",45.00000000000001],
        //      PARAMETER["Standard_Parallel_2",33.00000000000001],
        //      UNIT["Inch",0.02540005080010]
        //    ]
        //  }
        private static NamedList parseWKT (String wkt)
        {
            // parse into list of tokens
            //  namedlist
            //  string
            //  double
            //  char
            LinkedList<Object> tokens = new LinkedList<> ();
            int wktlen = wkt.length ();
            for (int i = 0; i < wktlen;) {
                char c = wkt.charAt (i);
                if (((c >= 'A') && (c <= 'Z')) || ((c >= 'a') && (c <= 'z'))) {
                    int j = i;
                    while (++ i < wktlen) {
                        c = wkt.charAt (i);
                        if (((c >= 'A') && (c <= 'Z')) || ((c >= 'a') && (c <= 'z'))) {
                            continue;
                        }
                        break;
                    }
                    tokens.addLast (new NamedList (wkt.substring (j, i)));
                    continue;
                }
                if (((c >= '0') && (c <= '9')) || (c == '-')) {
                    int j = i;
                    while (++ i < wktlen) {
                        c = wkt.charAt (i);
                        if (((c >= '0') && (c <= '9')) || (c == '.')) {
                            continue;
                        }
                        break;
                    }
                    tokens.addLast (Double.parseDouble (wkt.substring (j, i)));
                    continue;
                }
                if (c == '"') {
                    int j = ++ i;
                    while (i < wktlen) {
                        c = wkt.charAt (i);
                        if (c == '"') break;
                        i ++;
                    }
                    tokens.addLast (wkt.substring (j, i ++));
                    continue;
                }
                if (c > ' ') {
                    tokens.addLast (c);
                }
                i ++;
            }

            // reduce to a single top-level token
            Iterator<Object> it = tokens.iterator ();
            Object top = getWKTObject (it);
            if (it.hasNext ()) throw new RuntimeException ("more than one top-level object");
            return (NamedList) top;
        }

        private static Object getWKTObject (Iterator<Object> it)
        {
            Object obj = it.next ();
            if (obj instanceof NamedList) {
                NamedList nl = (NamedList) obj;
                if ((Character) it.next () != '[') throw new RuntimeException ("list name " + nl.name + " not followed by [");
                LinkedList<Object> list = new LinkedList<> ();
                char c;
                do {
                    list.addLast (getWKTObject (it));
                    c = (Character) it.next ();
                } while (c == ',');
                if (c != ']') throw new RuntimeException ("named list value not followed by , or ]");
                nl.list = list.toArray ();
            }
            return obj;
        }

        /**
         * Calculate number of metres east of Longitude_of_Central_Meridian
         * using Lambert Conformal Ellipsoidal Projection formulae.
         */
        private double calcEasting (double lat, double lon)
        {
            double phi = Math.toRadians (lat);
            double lam = Math.toRadians (lon);

            while (lam < e_lam0 - Math.PI) lam += Math.PI * 2.0;
            while (lam > e_lam0 + Math.PI) lam -= Math.PI * 2.0;

            // v108: equation 15-9a
            double t = eq159a (phi);

            // v108: equation 15-7
            double rho = e_F_rada * Math.pow (t, e_n);

            // v108: equation 14-4
            double theta = e_n * (lam - e_lam0);

            // v107: equation 14-1 -> how far east of centerLon
            return rho * Math.sin (theta);
        }

        /**
         * Calculate number of metres north of Latitude_of_Projection_Origin
         * using Lambert Conformal Ellipsoidal Projection formulae.
         */
        private double calcNorthing (double lat, double lon)
        {
            double phi = Math.toRadians (lat);
            double lam = Math.toRadians (lon);

            while (lam < e_lam0 - Math.PI) lam += Math.PI * 2.0;
            while (lam > e_lam0 + Math.PI) lam -= Math.PI * 2.0;

            // v108: equation 15-9a
            double t = eq159a (phi);

            // v108: equation 15-7
            double rho = e_F_rada * Math.pow (t, e_n);

            // v108: equation 14-4
            double theta = e_n * (lam - e_lam0);

            // v107: equation 14-2 -> how far north of centerLat
            return e_rho0 - rho * Math.cos (theta);
        }

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
