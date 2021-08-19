//    Copyright (C) 2021, Mike Rieker, Beverly, MA USA
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
 *  Read an Eurpope IAP plate diagram .png file and find the lat/lon marks and correlate them to pixel number.
 *
 *  yum install ghostscript
 *  yum install libexif-0.6.21-6.el7.x86_64 giflib-4.1.6-9.el7.x86_64
 *  rpm -ihv libgdiplus-2.10-9.el7.x86_64.rpm
 *  ln -s /lib64/libgdiplus.so.0 /lib64/libgdiplus.so
 *  mcs -debug -out:ReadEuroIAPPng.exe -reference:Mono.Data.Sqlite.dll -reference:System.Data.dll \
 *      -reference:System.Drawing.dll ReadEuroIAPPng.cs FindWaypoints.cs
 *
 *  Convert to png with:
 *    gs -q -dQuiet -dSAFER -dBATCH -dNOPAUSE -dNOPROMT -dMaxBitmap=500000000 -dAlignToPixels=0 -dGridFitTT=2 \
 *        -sDEVICE=png16m -dTextAlphaBits=4 -dGraphicsAlphaBits=4 -r300x300 -dFirstPage=1 -dLastPage=1 \
 *        -sOutputFile=<pngname> <pdfname>
 *
 *  mono --debug ReadEuroIAPPng.exe plate.png -scale 0.5 -csvoutfile ABCD-ILS-23.csv -csvoutid ABCD-ILS-23
 */

/*
    Basic algorithm:
     1) Read png file into bitmap so we have an array of pixels.
     2) Make a monochromatic (strict black-and-white) version of the bitmap.
        Any pixel with a>200, r<100, g<100 and b<100 is considered black.
        This makes the bitmap easy to work with by eliminating all the gray and colored areas.
     3) Scan image for groups of pixels that fit in a 30 x 30 or smaller rectangle.
     4) For each box found, try to classify it as one of the characters we care about, ie, 0-9, ^, '
         a) normalize char box size to 13x17 grayscale pixels
         b) compare normalized char to idealized grayscale 13x17 characters, pixel by pixel,
            creating a sum of squared differences
         c) choose the char that has the smallest difference sum as the proper decoding
        Crude but effective.
     5) Sort where those chars were found by ascending X-axis value (with Y-axis value as a minor key).
     6) Build strings of proper lat/lon form from that list, by scanning it left to right, considering only characters
        that have the same approximate Y value for any given scan.
     7) Pair dd^ strings with nearby mm' strings.  The mm' will be either below or to right of dd^.
        There is a little separator line (tick mark) indicating pixel number for that lst/lon.
     8) Use the {north,south,east,west}most of those pairs to determine mapping.
     9) Validate the result by checking that the resultant pixels are square (lat/lon-wise).
*/

using Mono.Data.Sqlite;
using System;
using System.Collections.Generic;
using System.Data;
using System.Data.SqlClient;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Text;

public struct XY {
    public int x;
    public int y;

    public XY (int xx, int yy)
    {
        x = xx;
        y = yy;
    }

    public override string ToString ()
    {
        return "(" + x + "," + y + ")";
    }
}

public class ReadEuroIAPPng {
    public const int D09W     =   15;               // digit recognition resolution width
    public const int D09H     =   19;               // digit recognition resolution height
    public const int D09R     =    3;               // pixel size to write to dump file (ReadEuroIAPPng_learnt.png)
    public const int MAXWH    =   50;               // maximum width and height of a character box
    public const int MINLONG  =   60;               // minimum long line length
    public const int MINTHIN  =   20;               // minimum thin line length
    public const double VFYPIXRAT = 0.95;           // pixels should be within 95% square

    public static bool readtext;                    // -readtext
    public static bool stages;                      // -stages
    public static bool verbose;                     // -verbose
    public static string csvoutfile;                // -csvoutfile
    public static string csvoutid;                  // -csvoutid
    public static string markedpng;                 // -markedpng
    public static double scale;                     // -scale

    public static double airportlat, airportlon;
    public static double minlatdeg =   0;
    public static double maxlatdeg =  90;
    public static double minlondeg =   0;
    public static double maxlondeg = 180;

    public static bool thetaeven, thetaodd;
    public static Bitmap rotdBitmap;                // bitmap of original image rotated by theta so lat/lon strings are horizontal
    public static byte[,] blacks;                   // currently operating array of black pixels [y,x] (1=black; 0=white)
    public static int origHeight;                   // height of original unrotated image
    public static int origWidth;                    // width of original unrotated image
    public static int rotdHeight;                   // height of rotdBitmap, blacks
    public static int rotdWidth;                    // width of rotdBitmap, blacks
    public static int theta;                        // rotdBitmap,blacks = original bitmap rotated clockwise by theta (0=0, 1=90, 2=180, 3=270)
    public static LinkedList<ClusPair> clusPairs;   // dd^mm' strings found in rotated image
    public static LinkedList<Cluster> clusters;     // all strings found in rotated image
    public static LinkedList<Deco> allDecos;        // all decoded characters found in rotated image
    public static LinkedList<Given> givens = new LinkedList<Given> ();
                                                    // list of characters given on command line
    public static LinkedList<ClusPairRC> goodClusPairOrigCols;
    public static LinkedList<ClusPairRC> goodClusPairOrigRows;
    public static LinkedList<Rectangle> rotdBoxList;
                                                    // list of character boxes in rotated image
    public static Rectangle[] boxes;                // list of character boxes in rotated & flipped image
    public static SortedDictionary<char,int> counts = new SortedDictionary<char,int> ();
    public static SortedDictionary<char,int[,]> learnt = new SortedDictionary<char,int[,]> ();
    public static string faaid;                     // airport FAA id
    public static string pngname;                   // original .png filename
    public static string pointa;
    public static string pointb;
    public static string rwyname;                   // runway data file name
    public static string wayptdb;

    public static void Main (string[] args)
    {
        scale = 1.0;
        for (int i = 0; i < args.Length; i ++) {
            string arg = args[i];

            if (arg == "-csvoutfile") {
                if (++ i >= args.Length) goto usage;
                csvoutfile = args[i];
                continue;
            }
            if (arg == "-csvoutid") {
                if (++ i >= args.Length) goto usage;
                csvoutid = args[i];
                continue;
            }
            if (arg == "-faaid") {
                if (++ i >= args.Length) goto usage;
                faaid = args[i];
                continue;
            }
            if (arg == "-givens") {
                // x,y=char
                while (++ i < args.Length) {
                    string s = args[i];
                    int j = s.IndexOf (',');
                    int k = s.IndexOf ('=');
                    if ((j <= 0) || (k <= j)) {
                        -- i;
                        break;
                    }
                    int x = int.Parse (s.Substring (0, j ++));
                    int y = int.Parse (s.Substring (j, k - j));
                    givens.AddLast (new Given (x, y, s.Substring (++ k)));
                }
                continue;
            }
            if (arg == "-markedpng") {
                if (++ i >= args.Length) goto usage;
                markedpng = args[i];
                continue;
            }
            if (arg == "-pointa") {
                if (++ i >= args.Length) goto usage;
                pointa = args[i];
                continue;
            }
            if (arg == "-pointb") {
                if (++ i >= args.Length) goto usage;
                pointb = args[i];
                continue;
            }
            if (arg == "-readtext") {
                readtext = true;
                continue;
            }
            if (arg == "-runways") {
                if (++ i >= args.Length) goto usage;
                rwyname = args[i];
                continue;
            }
            if (arg == "-scale") {
                if (++ i >= args.Length) goto usage;
                scale = double.Parse (args[i]);
                continue;
            }
            if (arg == "-stages") {
                stages = true;
                continue;
            }
            if (arg == "-verbose") {
                verbose = true;
                continue;
            }
            if (arg == "-wayptdb") {
                if (++ i >= args.Length) goto usage;
                wayptdb = args[i];
                continue;
            }
            if (arg[0] == '-') goto usage;
            if (pngname != null) goto usage;
            pngname = arg;
        }
        if (pngname == null) goto usage;

        /*
         * If there is an airport ICAOID, look up airport's lat/lon
         * Then we can select numbers that are within that range.
         */
        if (csvoutid != null) {
            int i = csvoutid.IndexOf (',');
            string icaoid = (i < 0) ? csvoutid : csvoutid.Substring (0, i);
            if (! LookupLatLon (icaoid, "waypointsoa_*.db") &&
                ! LookupLatLon (icaoid, "waypointsofm_*.db")) {
                if (verbose) Console.WriteLine ("apt " + icaoid + " not found");
            }
        }

        /*
         * Rotated 90 * theta clockwise.
         */
        goodClusPairOrigCols = new LinkedList<ClusPairRC> ();
        goodClusPairOrigRows = new LinkedList<ClusPairRC> ();
        for (theta = 0; theta < 4; theta ++) {
            if (verbose) Console.WriteLine ("THETA * 90 = " + (theta * 90));
            thetaeven = ((theta & 1) == 0);
            thetaodd  = ((theta & 1) != 0);

            /*
             * Rotate the original image such that the lat/lon strings horizontal.
             */
            CreateRotatedImage ();

            /*
             * Now that lat/lon strings are supposedly all horizontal,
             * scan the image to locate and decode them all.
             */
            FindLatLonStrings ();

            /*
             * Build and print latlon <-> pixel transformation matrix.
             */
            BuildXformMatrix ();
        }
        return;

    usage:
        Console.WriteLine ("usage: mono ReadEuroIAPPng.exe <inputpngfile>");
        Console.WriteLine ("           -csvoutfile <filename>          - write data to given file");
        Console.WriteLine ("           -csvoutid <airportid>           - use this airport id in csvoutfile");
        Console.WriteLine ("           -givens \"x,y=string\" ...      - learning mode");
        Console.WriteLine ("           -markedpng <outputpngfilename>  - write out marked-up png file");
        Console.WriteLine ("           -pointa <waypt> -pointb <waypt> - draw line on markedpng");
        Console.WriteLine ("           -scale <factor>                 - scale csv pixel values");
        Console.WriteLine ("           -stages                         - create intermediate png files (debugging)");
        Console.WriteLine ("           -verbose                        - output intermediate messages (debugging)");
    }


    /**
     * Look up the given airport in waypoint database.
     * Set plate lat,lon limits to +- 2deg of the airport.
     */
    public static bool LookupLatLon (string icaoid, string dbasewild)
    {
        string latestfile = "";
        string[] allfiles = Directory.GetFiles ("datums", dbasewild);
        foreach (string file in allfiles) {
            if (string.Compare (latestfile, file) < 0) latestfile = file;
        }
        SqliteConnection dbcon = new SqliteConnection ("URI=file:" + latestfile);
        dbcon.Open ();
        try {
            SqliteCommand dbcmd = new SqliteCommand ("SELECT apt_lat,apt_lon FROM airports WHERE apt_icaoid=@icaoid", dbcon);
            dbcmd.Parameters.Add (new SqliteParameter ("@icaoid", icaoid));
            SqliteDataReader dbrdr = dbcmd.ExecuteReader ();
            if (! dbrdr.Read ()) return false;
            airportlat = dbrdr.GetDouble (0);
            airportlon = dbrdr.GetDouble (1);
            if (verbose) Console.WriteLine ("apt " + icaoid + " found at lat,lon " + airportlat + "," + airportlon + " in " + latestfile);
            double lat = Math.Abs (airportlat);
            double lon = Math.Abs (airportlon);
            minlatdeg  = lat - 2.0;
            maxlatdeg  = lat + 2.0;
            minlondeg  = lon - 2.0;
            maxlondeg  = lon + 2.0;
            return true;
        } finally {
            dbcon.Close ();
        }
    }


    /**
     * @brief Create rotated image such that the lat/lon strings are horizontal.
     */
    public static void CreateRotatedImage ()
    {
        /*
         * Create a black-and-white image from original grayscale image.
         */
        Bitmap pngBitmap = new Bitmap (pngname);
        origWidth  = pngBitmap.Width;
        origHeight = pngBitmap.Height;
        if (thetaeven) {
            rotdBitmap = new Bitmap (pngBitmap.Width, pngBitmap.Height);
        } else {
            rotdBitmap = new Bitmap (pngBitmap.Height, pngBitmap.Width);
        }
        rotdWidth  = rotdBitmap.Width;
        rotdHeight = rotdBitmap.Height;

        Monochromaticise (pngBitmap, rotdBitmap);

        pngBitmap.Dispose ();

        if (stages) rotdBitmap.Save ("stage1_" + theta + ".png", ImageFormat.Png);

        /*
         * Strip out long vertical and horizontal lines so they don't interfere with finding characters.
         */
        StripLongLines ();

        if (stages) rotdBitmap.Save ("stage2_" + theta + ".png", ImageFormat.Png);
    }

    /**
     * Find all lat/lon strings in the rotated image.
     *  Input:
     *    rotdBitmap,rotdWidth,rotdHeight = portrait-orientated rotated image
     *  Output:
     *    clusPairs = list of lat/lon degree/minute string pairs
     */
    public static void FindLatLonStrings ()
    {
        if (readtext) {
            ReadClustersFromText ();
        } else {
            ScanImageForClusters ();
        }

        /*
         * Print out all the clusters (ie, lat/lon strings) that we found.
         */
        if (verbose) {
            foreach (Cluster cluster in clusters) {
                Console.WriteLine ("cluster " + cluster.ToString ());
            }
        }

        /*
         * Several formats to look for:
         *
         *  dd^mm'
         *
         *  dd mm
         *
         *  dd^mm
         *
         *  dd^
         *  mm'
         *
         *  dd
         *  mm
         */

        /*
         * Pair each degree string with nearest minute string.
         */
        clusPairs = new LinkedList<ClusPair> ();
        foreach (Cluster degclus in clusters) {
            if (! degclus.IsLatLon) continue;

            int deghite = degclus.hiy - degclus.loy;
            if (verbose) Console.WriteLine ("FindLatLonStrings*: degclus=" + degclus.ToString () + " deghite=" + deghite);

            // sometime have just dd^q eg 54^N
            // ... or can be 5100N or 11230E
            char clustag = degclus.ClusTagSfx;
            if (clustag != 0) {
                ClusPair cp = new ClusPair ();
                cp.degclus = degclus;
                cp.cptag   = clustag;
                double deg = cp.GetDegrees ();
                if (! double.IsNaN (deg)) {
                    clusPairs.AddLast (cp);
                    cp.DrawBox ();
                }
                continue;
            }

            foreach (Cluster minclus in clusters) {
                if (minclus == degclus) continue;
                if (! minclus.IsLatLon) continue;

                // text height should be similar
                int minhite = minclus.hiy - minclus.loy;
                if (verbose) Console.WriteLine ("FindLatLonStrings*:   minclus=" + minclus.ToString () + " minhite=" + minhite);
                if (minhite < deghite * 3 / 4) continue;
                if (deghite < minhite * 3 / 4) continue;
                int avghite = (minhite + deghite) / 2;

                // minutes follow degrees within an height horizontally
                int dx = minclus.lox - degclus.hix;
                int dy = (minclus.loy + minclus.hiy) / 2 - (degclus.loy + degclus.hiy) / 2;
                if ((dy >= - avghite / 2) && (dy <= avghite / 2) && (dx > 0) && (dx <= avghite)) {
                    ClusPair cp = new ClusPair ();
                    cp.degclus = degclus;
                    cp.minclus = minclus;
                    cp.cptag   = (char) (degclus.ClusTag | minclus.ClusTag);
                    double deg = cp.GetDegrees ();
                    if (! double.IsNaN (deg)) {
                        clusPairs.AddLast (cp);
                        cp.DrawBox ();
                    }
                    continue;
                }

                // minutes below degrees within an height and a half vertically
                dx = (minclus.lox + minclus.hix) / 2 - (degclus.lox + degclus.hix) / 2;
                dy = minclus.loy - degclus.hiy;
                if ((dx >= -avghite / 2) && (dx <= avghite / 2) && (dy > 0) && (dy <= avghite * 3 / 2)) {
                    ClusPair cp = new ClusPair ();
                    cp.degclus = degclus;
                    cp.minclus = minclus;
                    cp.cptag   = (char) (degclus.ClusTag | minclus.ClusTag);
                    double deg = cp.GetDegrees ();
                    if (! double.IsNaN (deg)) {
                        clusPairs.AddLast (cp);
                        cp.DrawBox ();
                    }
                }
            }
        }
        if (stages) rotdBitmap.Save ("stage5_" + theta + ".png", ImageFormat.Png);
        if (verbose) {
            foreach (ClusPair cp in clusPairs) {
                Console.WriteLine ("cluspair " + cp.ToString ());
            }
        }
    }

    /**
     * Read cluster strings from stdin
     * Lines in form x= y= dx= dy= str=
     * xy values are scaled for 72 dpi and give lower left corner of char
     * str has at most one character
     * xy origin is lower left corner of image
     */
    public static void ReadClustersFromText ()
    {
        // read all lines sorted by y,x
        char[] splits = new char[] { ' ' };
        SortedDictionary<int,SortedDictionary<int,Deco>> charlines = new SortedDictionary<int,SortedDictionary<int,Deco>> ();
        for (string line; (line = Console.ReadLine ()) != null;) {
            string[] parts = line.Split (splits);
            bool dyzero = false;
            Deco deco = new Deco ();
            foreach (string part in parts) {
                if (part.StartsWith ("x="))  deco.x = (int) Math.Round (double.Parse (part.Substring (2)) * 300.0 / 72.0);
                if (part.StartsWith ("y="))  deco.y = rotdHeight - (int) Math.Round (double.Parse (part.Substring (2)) * 300.0 / 72.0);
                if (part.StartsWith ("dx=")) deco.w = (int) Math.Round (double.Parse (part.Substring (3)) * 300.0 / 72.0);
                if (part.StartsWith ("dy=")) dyzero = double.Parse (part.Substring (3)) == 0.0;
                if (part.StartsWith ("str=") && (part.Length == 5)) deco.c = part[4];
            }
            if (dyzero && (deco.c != 0)) {
                SortedDictionary<int,Deco> charline;
                if (! charlines.TryGetValue (deco.y, out charline)) {
                    charline = new SortedDictionary<int,Deco> ();
                    charlines.Add (deco.y, charline);
                }
                charline.Add (deco.x, deco);
            }
        }

        // build clusters
        //  the x of a char must match the x+w of previous char
        //  and the y's must be the same
        clusters = new LinkedList<Cluster> ();
        foreach (SortedDictionary<int,Deco> charline in charlines.Values) {
            Cluster cluster = null;
            int lastx = -999;
            foreach (Deco deco in charline.Values) {
                if ((cluster == null) || (Math.Abs (deco.x - lastx) > 2)) {
                    if (cluster != null) clusters.AddLast (cluster);
                    cluster = new Cluster ();
                }
                cluster.InsertDeco (deco);
                lastx = deco.x + deco.w;
            }
            if (cluster != null) clusters.AddLast (cluster);
        }

        // we don't get actual character height from pdftotext
        // so make one up based on average character width
        foreach (Cluster cluster in clusters) {
            int cluswidth  = cluster.hix - cluster.lox;
            int decoheight = cluswidth / cluster.decos.Count;
            cluster.loy   -= decoheight;
            foreach (Deco deco in cluster.decos) {
                deco.h  = decoheight;
                deco.y -= decoheight;
            }
        }
    }

    /**
     * Look through the image for clusters, ie, strings
     *  Output:
     *   clusters = list of clusters (lat/lon-looking strings) found
     */
    public static void ScanImageForClusters ()
    {
        /*
         * Read previously learned digit pixel patterns.
         */
        StreamReader learntreader = null;
        try {
            learntreader = new StreamReader ("ReadEuroIAPPng_learnt.csv");
        } catch {
        }
        if (learntreader != null) {
            string line;
            while ((line = learntreader.ReadLine ()) != null) {
                string[] tokens = line.Split (new char[] { ',' });
                int i = 0;
                char key = tokens[i++][0];
                counts[key] = int.Parse (tokens[i++]);
                learnt[key] = new int[D09H,D09W];
                for (int y = 0; y < D09H; y ++) {
                    for (int x = 0; x < D09W; x ++) {
                        learnt[key][y,x] = int.Parse (tokens[i++]);
                    }
                }
            }
            learntreader.Close ();
        }

        /*
         * Find all character boxes in the blacks[,] array.
         */
        if (verbose) Console.WriteLine ("find character boxes");
        FindCharacterBoxes ();
        if (stages) rotdBitmap.Save ("stage3_" + theta + ".png", ImageFormat.Png);

        /*
         * Scan image in portrait (normal) orientation.
         * Add all recognized number strings to 'clusters'.
         */
        if (verbose) Console.WriteLine ("normal portrait scan");
        blacks = new byte[rotdHeight,rotdWidth];
        for (int porty = 0; porty < rotdHeight; porty ++) {
            for (int portx = 0; portx < rotdWidth; portx ++) {
                Color c = rotdBitmap.GetPixel (portx, porty);
                blacks[porty,portx] = ColorIsBlack (c);
            }
        }
        int bi = 0;
        boxes  = new Rectangle[rotdBoxList.Count];
        foreach (Rectangle pr in rotdBoxList) {
            boxes[bi++] = pr;
        }

        // but if learning mode, do that instead
        if (givens.Count > 0) {
            BuildLearning ();
            Environment.Exit (0);
        }

        DecodeLatLonStrings ();
    }

    /**
     * @brief Locate boxes around things that might be characters.
     *        Draw red boxes around them for debugging.
     */
    private static void FindCharacterBoxes ()
    {
        rotdBoxList = new LinkedList<Rectangle> ();
        for (int y = 0; y < rotdHeight; y ++) {
            for (int x = 0; x < rotdWidth; x ++) {
                if (blacks[y,x] == 0) continue;
                int ytop = y;
                int ybot = y;
                int xlef = x;
                int xrit = x;
                if (ExpandPerimeter (ref xlef, ref xrit, ref ytop, ref ybot)) {
                    int xx = xlef;
                    int yy = ytop;
                    int ww = xrit - xlef + 1;
                    int hh = ybot - ytop + 1;
                    if ((ww <= Deco.MAXWH) && (hh > 1) && (hh <= Deco.MAXWH)) {
                        rotdBoxList.AddLast (new Rectangle (xx, yy, ww, hh));
                        DrawBox (xlef - 1, ytop - 1, xrit + 1, ybot + 1, Color.Red);
                    }
                    for (yy = ytop; yy <= ybot; yy ++) {
                        for (xx = xlef; xx <= xrit; xx ++) {
                            blacks[yy,xx] = 0;
                        }
                    }
                }
            }
        }
    }

    /**
     * @brief Decode pixels to corresponding characters and strings.
     *  Output:
     *   allDecos = list of individual characters found (temporary, empty on return)
     *   clusters = list of all strings found
     */
    private static void DecodeLatLonStrings ()
    {
        clusters = new LinkedList<Cluster> ();
        allDecos = new LinkedList<Deco> ();

        /*
         * Scan the character boxes to see if we can decode characters therein.
         */
        foreach (Rectangle box in boxes) {
            int x = box.X;
            int y = box.Y;
            int w = box.Width;
            int h = box.Height;

            bool debug = false; // (x >= 1420) && (x <= 1540) && (y >= 2210) && (y <= 2250);
            if (debug) Console.WriteLine ("DecodeLatLonStrings*: " + x + "," + y + " " + w + "x" + h);

            /*
             * Sometimes we have a digit followed immediately by the degree symbol.
             * Make sure cell is at least 18x18 and is wider than it is high.
             * And make sure the lower right quarter (under degree symbol) is blank.
             * Eg, CBM 33^38'N.
             */
            if ((w > 17) && (h > 17) && (w > h - 2)) {

                /*
                 * Find largest rectangular blank area in lower right corner of box.
                 */
                int bestA = 0;
                int bestW = 0;
                int bestH = 0;
                int wlim  = w;
                int ww, hh;
                for (hh = 0; ++ hh <= h;) {
                    for (ww = 0; ++ ww <= wlim;) {
                        if (blacks[y+h-hh,x+w-ww] != 0) break;
                    }
                    wlim = -- ww;
                    int aa = hh * ww;
                    if (aa > bestA) {
                        bestA = aa;
                        bestW = ww;
                        bestH = hh;
                    }
                }
                if (debug) Console.WriteLine ("DecodeLatLonStrings*: bestWH=" + bestW + "x" + bestH);

                /*
                 * If there is at least a 9x9 area, assume it is a digit/degree
                 * stuck together.  Decode them as two characters.
                 */
                if ((bestW > 8) && (bestH > 8)) {
                    DecodeCharacter (x, y, w - bestW, h);
                    DecodeCharacter (x + w - bestW, y, bestW, h - bestH);
                    continue;
                }
            }

            /*
             * Presumably just a single character, decode it.
             */
            DecodeCharacter (x, y, w, h);
        }

        /*
         * Gather decoded characters into clusters.
         */
        GatherIntoClusters ();

        /*
         * Write out debug image.
         */
        if (stages) {
            for (int y = 0; y < rotdHeight; y ++) {
                for (int x = 0; x < rotdWidth; x ++) {
                    rotdBitmap.SetPixel (x, y, (blacks[y,x] == 0) ? Color.White : Color.Black);
                }
            }
            foreach (Rectangle box in boxes) {
                DrawBox (box.X - 1, box.Y - 1, box.X + box.Width, box.Y + box.Height, Color.Red);
            }
            foreach (Cluster cluster in clusters) {
                DrawBox (cluster.lox - 2, cluster.loy - 2, cluster.hix + 2, cluster.hiy + 2, Color.Blue);
            }
            rotdBitmap.Save ("stage4_" + theta + ".png", ImageFormat.Png);
        }
    }

    /**
     * @brief Try to decode the character at upper-left corner blacks[y,x]
     * @param x,y = upper left corner
     * @param w,h = width/height of box (exclusive)
     */
    private static void DecodeCharacter (int x, int y, int w, int h)
    {
        byte[,] grays = null;
        char ch;

        bool debug = false; // (x >= 1420) && (x <= 1540) && (y >= 2210) && (y <= 2250);
        if (debug) Console.WriteLine ("DecodeCharacter*: " + x + "," + y + " " + w + "x" + h);

        if ((w <= 0) || (w > Deco.MAXWH)) return;
        if ((h <= 0) || (h > Deco.MAXWH)) return;

        // seconds mark
        if (CheckForSecMark (x, y, w, h)) {
            ch = '\'';
        }

        // degrees mark
        else if ((w < 11) && (h < 15)) {
            ch = '^';
        }

        // something that needs to be decoded
        else if ((w > 4) && (h > 14)) {
            grays = BuildGraysArray (x, y, w, h);
            ch = DecodeGraysArray (grays, false);

            /*
             * Sometimes we misread what is really a 2 as a 7.
             * Real 7's don't have any black pixels in lower right corner.
             * See NUW 122^38'W (the second 2).
             */
            if (ch == '7') {
                int nb = blacks[y+h-1,x+w-1] + blacks[y+h-1,x+w-2] + blacks[y+h-1,x+w-3] +
                         blacks[y+h-2,x+w-1] + blacks[y+h-2,x+w-2] + blacks[y+h-2,x+w-3];
                if (nb > 0) ch = '2';
            }
        }

        // some garbage
        else {
            if (debug) Console.WriteLine ("DecodeCharacter*: " + x + "," + y + " " + w + "x" + h + " => garbage");
            return;
        }

        if (verbose) Console.WriteLine ("DecodeCharacter " + x + "," + y + " " + w + "x" + h + " => " + ch);

        // add to list of all decoded characters by ascending X value.
        // because strings go from left-to-right (ascending X value).
        // we sort out the Y values in GatherIntoClusters().
        Deco deco = new Deco ();
        deco.x = x;
        deco.y = y;
        deco.w = w;
        deco.h = h;
        deco.c = ch;
        deco.grays = grays;

        for (LinkedListNode<Deco> ptr = allDecos.First; ptr != null; ptr = ptr.Next) {
            Deco d = ptr.Value;
            if ((deco.x < d.x) || ((deco.x == d.x) && (deco.y < d.y))) {
                allDecos.AddBefore (ptr, deco);
                return;
            }
        }
        allDecos.AddLast (deco);
    }

    /**
     * @brief See if we have an apostrophe character.
     *        Just a thin short vertical line.
     */
    private static bool CheckForSecMark (int x, int y, int w, int h)
    {
        return (w <= 4) && (h <= 10) && (h >= 6);
    }

    /**
     * @brief Pixellate the given pixels into a gray-scale array.
     * @param x,y = upper-left corner pixel
     * @param w,h = size of the input array
     * @returns grayscale array of size [D09H,D09W]
     */
    public static byte[,] BuildGraysArray (int x, int y, int w, int h)
    {
        Bitmap srcbitmap = new Bitmap (w, h);

        for (int sy = 0; sy < h; sy ++) {
            for (int sx = 0; sx < w; sx ++) {
                srcbitmap.SetPixel (sx, sy, (blacks[y+sy,x+sx] == 0) ? Color.White : Color.Black);
            }
        }

        Bitmap dstbitmap = new Bitmap (srcbitmap, D09W, D09H);

        byte[,] grays = new byte[D09H,D09W];
        for (int dy = 0; dy < D09H; dy ++) {
            for (int dx = 0; dx < D09W; dx ++) {
                grays[dy,dx] = dstbitmap.GetPixel (dx, dy).R;
            }
        }

        return grays;
    }

    /**
     * @brief Decode a given grays array to the character it represents.
     */
    private static char DecodeGraysArray (byte[,] grays, bool debug)
    {
        char bestChar = '?';
        if (givens.Count == 0) {
            double bestScore = 0;
            foreach (char c in counts.Keys) {
                int count = counts[c];
                if (count > 0) {
                    int[,] learntc = learnt[c];
                    double score = 0;
                    for (int y = 0; y < D09H; y ++) {
                        for (int x = 0; x < D09W; x ++) {
                            double diff = (double)(int)grays[y,x] - (double)learntc[y,x] / (double)count;
                            score += diff * diff;
                        }
                    }
                    if (debug) Console.WriteLine ("DecodeGraysArray*: [" + c + "] score " + score);
                    if ((bestChar == '?') || (score < bestScore)) {
                        bestChar = c;
                        bestScore = score;
                    }
                }
            }
        }
        return bestChar;
    }

    /**
     * @brief Gather the separate decoded characters into clusters.
     */
    private static void GatherIntoClusters ()
    {
        while (allDecos.Count > 0) {

            /*
             * Find leftmost character.
             */
            Deco deco = allDecos.First.Value;
            allDecos.RemoveFirst ();

            /*
             * Sometimes there are stray marks around that look like ' and ..
             * Skip over them so they don't obscure a nearby legitimate number.
             */
            if ((deco.c == '\'') || (deco.c == '.')) continue;

            /*
             * Create a cluster for it.
             */
        newclus:
            Cluster cluster = new Cluster ();
            clusters.AddLast (cluster);
            char lastdecochar = (char) 0;

            bool debug = false; // (deco.x >= 1380) && (deco.x <= 1405) && (deco.y >= 2180) && (deco.y <= 2190);
            if (debug) Console.WriteLine ("GatherIntoClusters*: new cluster " + deco.x + "," + deco.y + " <" + deco.c + ">");

            /*
             * Append first character to cluster and likewise with all
             * subsequent nearby characters to its right that overlap on
             * the Y axis.
             */
        useit:
            if ((lastdecochar == 'N') || (lastdecochar == 'S') || (lastdecochar == 'E') || (lastdecochar == 'W')) goto newclus;
            if ((deco.c != 'N') && (deco.c != 'S') && (deco.c != 'E') && (deco.c != 'W')) {
                if ((lastdecochar == '^') || (lastdecochar == '\'')) goto newclus;
            }
            cluster.InsertDeco (deco);
            if (debug) Console.WriteLine ("GatherIntoClusters*: + <" + deco.c + "> " + deco.x + ".." + (deco.x + deco.w) + "," + deco.y + ".." + (deco.y + deco.h));
            lastdecochar = deco.c;

            for (LinkedListNode<Deco> ptr = allDecos.First; ptr != null; ptr = ptr.Next) {
                Deco dd  = ptr.Value;                       // scan by ascending X value
                int gap  = Math.Max (deco.w, dd.w) / 2;     // max gap allowed between digits
                int gapo = gap;
                if (deco.c == '1') gap += gapo;             // bigger gap allowed for '1' digits
                if (dd.c   == '1') gap += gapo;
                if (debug) Console.WriteLine ("GatherIntoClusters*:   dd=<" + dd.c + ">  " + dd.x + "," + dd.y);
                if (dd.x > cluster.hix + gap) continue;     // if too far right, just ignore it
                if (dd.x < cluster.hix - 2) continue;       // if too far left, just ignore it

                // deco = last char in cluster
                //   dd = new possible char

                /*
                 * We have char dd just to the right of char deco
                 * but we don't know if they are vertically aligned.
                 *
                 * Sometimes we mis-decode an apostrophe as a dot.
                 * Eg, HOP 36^40'N
                 * So we have to fix it in context.
                 */
                int ydiff = Math.Abs (dd.y - deco.y);
                int yover = deco.h / 4;
                if (dd.c == '.') {
                    if (ydiff <= yover) {
                        // the dot is near the top of the line of chars
                        dd.c = '\'';
                    } else {
                        ydiff += dd.h - deco.h;
                        if (ydiff > yover) continue;
                        // the dot is near the bottom of the line of chars
                    }
                    dd.y = deco.y;
                    dd.h = deco.h;
                } else {
                    if (ydiff > yover) continue;
                }

                /*
                 * Remove from allDecos and append to cluster.
                 */
                allDecos.Remove (ptr);
                deco = dd;
                goto useit;
            }
        }
    }

    /**
     * @brief We have some givens on the command line so we are in learning mode.
     *        Locate the given strings in the image then add the characters vs pixel patterns to the
     *        learned characters file.
     */
    private static void BuildLearning ()
    {
        /*
         * Scan boxes, looking for those matched by a given.
         */
        foreach (Given gv in givens) {
            foreach (Rectangle pr in boxes) {

                /*
                 * Match the approximate positions.
                 */
                if ((gv.x - pr.Left) * (gv.x - pr.Left) + (gv.y - pr.Top) * (gv.y - pr.Top) > 49) continue;

                /*
                 * Average the pixel values in learnt array.
                 */
                char c = gv.c;
                byte[,] grays = BuildGraysArray (pr.Left, pr.Top, pr.Width, pr.Height);

                /*
                 * Maybe make array entry for never seen before character.
                 */
                if (!counts.ContainsKey (c)) {
                    counts[c] = 0;
                    learnt[c] = new int[D09H,D09W];
                }

                /*
                 * Then accumulate the new grays array info.
                 */
                counts[c] = counts[c] + 1;
                int[,] learntc = learnt[c];
                for (int y = 0; y < D09H; y ++) {
                    for (int x = 0; x < D09W; x ++) {
                        learntc[y,x] += grays[y,x];
                    }
                }

                /*
                 * Given found.
                 */
                gv.found = true;
                break;
            }
        }

        /*
         * Print out any givens that weren't found as an error message.
         */
        foreach (Given gv in givens) {
            if (!gv.found) {
                Console.WriteLine ("given " + gv.x + "," + gv.y + "=" + gv.c + " not found");
            }
        }

        /*
         * Write out new learning.
         */
        StreamWriter learntwriter = new StreamWriter ("ReadEuroIAPPng_learnt.csv");
        foreach (char c in counts.Keys) {
            StringBuilder line = new StringBuilder ();
            line.Append (c);
            line.Append (',');
            line.Append (counts[c]);
            int[,] learntc = learnt[c];
            for (int y = 0; y < D09H; y ++) {
                for (int x = 0; x < D09W; x ++) {
                    line.Append (',');
                    line.Append (learntc[y,x]);
                }
            }
            learntwriter.WriteLine (line.ToString ());
        }
        learntwriter.Close ();

        /*
         * Write resultant learned digits to ReadEuroIAPPng_learnt.png (debugging only).
         */
        Bitmap learntbmp = new Bitmap (counts.Count * (D09R * D09W + 1) + 1, D09R * D09H + 2);
        for (int y = D09R * D09H + 2; -- y >= 0;) {
            for (int x = counts.Count * (D09R * D09W + 1) + 1; -- x >= 0;) {
                learntbmp.SetPixel (x, y, Color.Pink);
            }
        }
        int j = 0;
        foreach (char c in counts.Keys) {
            int count = counts[c];
            int[,] learntc = learnt[c];
            int xc = j * (D09W * D09R + 1);
            for (int y = 0; y < D09H; y ++) {
                for (int x = 0; x < D09W; x ++) {
                    int gray = learntc[y,x] / count;
                    Color color = Color.FromArgb (gray, gray, gray);
                    for (int yy = 0; yy < D09R; yy ++) {
                        for (int xx = 0; xx < D09R; xx ++) {
                            learntbmp.SetPixel (xc + x * D09R + xx + 1, y * D09R + yy + 1, color);
                        }
                    }
                }
            }
            j ++;
        }
        learntbmp.Save ("ReadEuroIAPPng_learnt.png", ImageFormat.Png);
    }

    /**
     * @brief Build and print latlon <-> pixel transformation matrix.
     */
    private static void BuildXformMatrix ()
    {
        ClusPair[] cparray = new ClusPair[clusPairs.Count];
        clusPairs.CopyTo (cparray, 0);

        ClusPairRC topgoodrow, botgoodrow, leftgoodcol, ritegoodcol;

        bool withdegmarks = false;
        while (true) {

            // sort by x, y
            Array.Sort (cparray, 0, cparray.Length, new SortClusPairByX ());

            // find rows of numbers - theta even: assume they are longitudes
            //                         theta odd: assume they are latitudes
            LinkedList<ClusPairRC> clusPairRotdRows = new LinkedList<ClusPairRC> ();
            foreach (ClusPair cp in cparray) {
                double deg = cp.GetDegrees ();
                if (thetaeven && ((deg < minlondeg) || (deg > maxlondeg))) continue;
                if (thetaodd  && ((deg < minlatdeg) || (deg > maxlatdeg))) continue;
                if (withdegmarks && ! cp.degclus.Result.EndsWith ("^")) continue;
                int cpy = cp.GetRefY (0);
                int cpx = cp.GetLoX ();

                // see if there is an existing row to add the number to
                ClusPairRC row = null;
                for (LinkedListNode<ClusPairRC> cprlln = clusPairRotdRows.First; cprlln != null; cprlln = cprlln.Next) {
                    row = cprlln.Value;
                    if ((row.RotdHiX < cpx) && (row.RotdLoY <= cpy) && (cpy <= row.RotdHiY)) break;
                    row = null;
                }

                // create new row if existing not found
                if (row == null) {
                    row = new ClusPairRC ();
                    row.theta = theta;
                    clusPairRotdRows.AddLast (row);
                }

                // add number to row
                row.AddClusPair (cp);
            }

            foreach (ClusPairRC row in clusPairRotdRows) {
                DrawBox (row.RotdLoX - 5, row.RotdLoY - 5, row.RotdHiX + 5, row.RotdHiY + 5, Color.Magenta);
            }

            // sort by y, x
            Array.Sort (cparray, 0, cparray.Length, new SortClusPairByY ());

            // find columns of numbers - theta even: assume they are latitudes
            //                            theta odd: assume they are longitudes
            LinkedList<ClusPairRC> clusPairRotdCols = new LinkedList<ClusPairRC> ();
            foreach (ClusPair cp in cparray) {
                double deg = cp.GetDegrees ();
                if (thetaeven && ((deg < minlatdeg) || (deg > maxlatdeg))) continue;
                if (thetaodd  && ((deg < minlondeg) || (deg > maxlondeg))) continue;
                if (withdegmarks && ! cp.degclus.Result.EndsWith ("^")) continue;
                int cpx = cp.GetRefX (0);
                int cpy = cp.GetLoY ();

                // see if there is an existing column to add the number to
                ClusPairRC col = null;
                for (LinkedListNode<ClusPairRC> cprlln = clusPairRotdCols.First; cprlln != null; cprlln = cprlln.Next) {
                    col = cprlln.Value;
                    if ((col.RotdHiY < cpy) && (col.RotdLoX <= cpx) && (cpx <= col.RotdHiX)) break;
                    col = null;
                }

                // create new column if existing not found
                if (col == null) {
                    col = new ClusPairRC ();
                    col.theta = theta;
                    clusPairRotdCols.AddLast (col);
                }

                // add number to column
                col.AddClusPair (cp);
            }

            foreach (ClusPairRC col in clusPairRotdCols) {
                DrawBox (col.RotdLoX - 5, col.RotdLoY - 5, col.RotdHiX + 5, col.RotdHiY + 5, Color.Magenta);
            }

            if (stages) rotdBitmap.Save ("stage6_" + theta + ".png", ImageFormat.Png);

            // find rows that look good
            // list should already be sorted by ascending X
            //  there are at least 2 elements
            //  no more than 3^ difference
            //  values increase as X increases
            foreach (ClusPairRC row in (thetaeven ? clusPairRotdRows : clusPairRotdCols)) {
                if (((row.theta & 1) == 0) ? row.IsGoodRotdRow () : row.IsGoodRotdCol ()) {
                    goodClusPairOrigRows.AddLast (row);
                }
            }
            foreach (ClusPairRC col in (thetaeven ? clusPairRotdCols : clusPairRotdRows)) {
                if (((col.theta & 1) == 0) ? col.IsGoodRotdCol () : col.IsGoodRotdRow ()) {
                    goodClusPairOrigCols.AddLast (col);
                }
            }

            if (verbose) {
                Console.WriteLine ("good original rows:");
                foreach (ClusPairRC row in goodClusPairOrigRows) {
                    Console.WriteLine ("  " + row.OrigLoY + ".." + row.OrigHiY);
                    foreach (ClusPair cp in row.cluspairs) {
                        Console.Write (String.Format ("  {0,4}^{1,-3}", cp.degclus.Result, ((cp.minclus == null) ? "" : cp.minclus.Result)));
                    }
                    Console.WriteLine ("");
                    foreach (ClusPair cp in row.cluspairs) {
                        Console.Write (String.Format ("  {0,7:D} ", cp.GetRefX (row.theta)));
                    }
                    Console.WriteLine ("");
                }

                Console.WriteLine ("good original columns:");
                foreach (ClusPairRC col in goodClusPairOrigCols) {
                    Console.WriteLine ("  " + col.OrigLoX + ".." + col.OrigHiX);
                    foreach (ClusPair cp in col.cluspairs) {
                        Console.Write (String.Format ("  {0,4}^{1,-3}", cp.degclus.Result, ((cp.minclus == null) ? "" : cp.minclus.Result)));
                    }
                    Console.WriteLine ("");
                    foreach (ClusPair cp in col.cluspairs) {
                        Console.Write (String.Format ("  {0,7:D} ", cp.GetRefY (col.theta)));
                    }
                    Console.WriteLine ("");
                }
            }

            // if we got two good rows and two good columns, done trying
            topgoodrow  = null;
            botgoodrow  = null;
            leftgoodcol = null;
            ritegoodcol = null;
            foreach (ClusPairRC row in goodClusPairOrigRows) {
                if ((topgoodrow == null) || (topgoodrow.OrigLoY > row.OrigLoY)) topgoodrow = row;
                if ((botgoodrow == null) || (botgoodrow.OrigLoY < row.OrigLoY)) botgoodrow = row;
            }
            foreach (ClusPairRC col in goodClusPairOrigCols) {
                if ((leftgoodcol == null) || (leftgoodcol.OrigLoX > col.OrigLoX)) leftgoodcol = col;
                if ((ritegoodcol == null) || (ritegoodcol.OrigLoX < col.OrigLoX)) ritegoodcol = col;
            }
            if ((topgoodrow != botgoodrow) && (leftgoodcol != ritegoodcol)) break;

            // maybe just use cluspairs with degree marks cuz columns might have been rejected
            if (withdegmarks) return;

            int degmkpairs = 0;
            int totalpairs = 0;
            LinkedList<ClusPairRC>[] cprclists = new LinkedList<ClusPairRC>[] { goodClusPairOrigCols, goodClusPairOrigRows };
            foreach (LinkedList<ClusPairRC> cprclist in cprclists) {
                foreach (ClusPairRC col in cprclist) {
                    foreach (ClusPair cp in col.cluspairs) {
                        if (cp.degclus.Result.EndsWith ("^")) degmkpairs ++;
                        totalpairs ++;
                    }
                }
            }
            if (verbose) Console.WriteLine ("degmkpairs=" + degmkpairs + " totalpairs=" + totalpairs);
            if (degmkpairs < totalpairs * 3 / 4) break;

            withdegmarks = true;
            if (verbose) Console.WriteLine ("try again withdegmarks");
        }

        // get 8 mapping points, one from each end of the 4 rows/cols
        // assume rows give longitudes and columns give latitudes
        //  tfw_a * x + tfw_c * y + tfw_e + tfw_g * x * y = lon
        //  tfw_b * x + tfw_d * y + tfw_f + tfw_h * x * y = lat
        double[,] tfwmat = new double[8,9];
         topgoodrow.cluspairs.First.Value.FillTfwLonRow (tfwmat, 0,  topgoodrow.theta);
         topgoodrow.cluspairs. Last.Value.FillTfwLonRow (tfwmat, 1,  topgoodrow.theta);
         botgoodrow.cluspairs.First.Value.FillTfwLonRow (tfwmat, 2,  botgoodrow.theta);
         botgoodrow.cluspairs. Last.Value.FillTfwLonRow (tfwmat, 3,  botgoodrow.theta);
        leftgoodcol.cluspairs.First.Value.FillTfwLatRow (tfwmat, 4, leftgoodcol.theta);
        leftgoodcol.cluspairs. Last.Value.FillTfwLatRow (tfwmat, 5, leftgoodcol.theta);
        ritegoodcol.cluspairs.First.Value.FillTfwLatRow (tfwmat, 6, ritegoodcol.theta);
        ritegoodcol.cluspairs. Last.Value.FillTfwLatRow (tfwmat, 7, ritegoodcol.theta);
        PrintMat ("initial tfwmat", tfwmat);
        RowReduce (tfwmat);

        // now we can get lat,lon at 4 corners of plate
        double midlat = 0.0;
        double midlon = 0.0;
        Corner[] corners = new Corner[4];
        for (int i = 0; i < 4; i ++) {
            double scaledx = (i & 1) * rotdWidth * scale;
            double scaledy = (i & 2) / 2 * rotdHeight * scale;
            double lat = tfwmat[1,8] * scaledx + tfwmat[3,8] * scaledy + tfwmat[5,8] + tfwmat[7,8] * scaledx * scaledy;
            double lon = tfwmat[0,8] * scaledx + tfwmat[2,8] * scaledy + tfwmat[4,8] + tfwmat[6,8] * scaledx * scaledy;
            corners[i].lat = lat;
            corners[i].lon = lon;
            corners[i].scaledx = scaledx;
            corners[i].scaledy = scaledy;
            midlat += lat / 4.0;
            midlon += lon / 4.0;
        }

        // calculate tfw,wft for plate using lat,lon of 4 corners
        double[,] wftmat = new double[8,9];
        for (int i = 0; i < 4; i ++) {
            corners[i].FillTfwLonRow (tfwmat, i);
            corners[i].FillTfwLatRow (tfwmat, i + 4);
            corners[i].FillWftXRow   (wftmat, i);
            corners[i].FillWftYRow   (wftmat, i + 4);
        }
        RowReduce (tfwmat);
        RowReduce (wftmat);

        /*
         * Verify mapping.
         */
        XY midxy = LatLon2PixXY (tfwmat, wftmat, midlat, midlon);
        double lselat = midlat - 1.0 / 60.0;
        double lselon = midlon + 1.0 / 60.0 / Math.Cos (midlat / 180.0 * Math.PI);
        XY lsexy = LatLon2PixXY (tfwmat, wftmat, lselat, lselon);
        double lsedx = lsexy.x - midxy.x;
        double lsedy = lsexy.y - midxy.y;
        if (verbose) Console.WriteLine ("1 nm pixels at center = " + lsedx + "," + lsedy);
        if ((lsedx < lsedy * 0.95) || (lsedy < lsedx * 0.95)) {
            throw new Exception ("pixels not square");
        }

        VerifyPointMapping (tfwmat, wftmat, topgoodrow.cluspairs.First.Value, false);
        VerifyPointMapping (tfwmat, wftmat, topgoodrow.cluspairs. Last.Value, false);
        VerifyPointMapping (tfwmat, wftmat, botgoodrow.cluspairs.First.Value, false);
        VerifyPointMapping (tfwmat, wftmat, botgoodrow.cluspairs. Last.Value, false);
        VerifyPointMapping (tfwmat, wftmat, leftgoodcol.cluspairs.First.Value, true);
        VerifyPointMapping (tfwmat, wftmat, leftgoodcol.cluspairs. Last.Value, true);
        VerifyPointMapping (tfwmat, wftmat, ritegoodcol.cluspairs.First.Value, true);
        VerifyPointMapping (tfwmat, wftmat, ritegoodcol.cluspairs. Last.Value, true);

        if (verbose) {
            XY airportxy = LatLon2PixXY (tfwmat, wftmat, airportlat, airportlon);
            Console.WriteLine ("airport scaled x,y=" + airportxy.x + "," + airportxy.y + "  original x,y=" + (airportxy.x / scale) + "," + (airportxy.y / scale));
        }

        /*
         * Maybe write to .csv file.
         * Lines are of the form:
         *   csvoutid,BIL,tfwA,tfwB,tfwC,tfwD,tfwE,tfwF,tfwG,tfwH,wftA,wftB,wftC,wftD,wftE,wftF,wtfG,wtfH
         */
        if ((csvoutfile != null) && (csvoutid != null)) {
            StringBuilder sb = new StringBuilder ();
            sb.Append (csvoutid);
            sb.Append (",BIL");
            for (int i = 0; i < 8; i ++) {
                sb.Append (',');
                sb.Append (tfwmat[i,8]);
            }
            for (int i = 0; i < 8; i ++) {
                sb.Append (',');
                sb.Append (wftmat[i,8]);
            }
            sb.Append ('\n');
            File.WriteAllText (csvoutfile, sb.ToString ());
        }

////        /*
////         * Maybe create marked-up .png file.
////         */
////        if (markedpng != null) {

////            /*
////             * Get original image with white background.
////             */
////            bmp    = new Bitmap (pngname);
////            width  = bmp.Width;
////            height = bmp.Height;
////            for (int y = 0; y < height; y ++) {
////                for (int x = 0; x < width; x ++) {
////                    Color c = bmp.GetPixel (x, y);
////                    int w = 255 - c.A;
////                    if (w != 0) {
////                        bmp.SetPixel (x, y, Color.FromArgb (c.R + w, c.G + w, c.B + w));
////                    }
////                }
////            }

////            /*
////             * Draw red crosses wherever lat/lon lines intersect.
////             */
////            foreach (Cluster lonClus in lonClusters) {
////                foreach (Cluster latClus in latClusters) {
////                    double lat = latClus.Latitude;
////                    double lon = lonClus.Longitude;
////                    int x = (int)(wftA * lon + wftC * lat + wftE + 0.5);
////                    int y = (int)(wftB * lon + wftD * lat + wftF + 0.5);
////                    DrawCross (x, y, 25, 2, Color.Red);
////                }
////            }

////            /*
////             * If we have FAA id and runway datafile, draw runway outlines.
////             */
////            if ((faaid != null) && (rwyname != null)) {
////                StreamReader file = new StreamReader (rwyname);
////                char[] splits = new char[] { ',' };
////                for (string line; (line = file.ReadLine ()) != null;) {
////                    string[] parts = line.Split (splits);
////                    if (parts[0] == faaid) {
////                        double lat1 = double.Parse (parts[4]);
////                        double lon1 = double.Parse (parts[5]);
////                        double lat2 = double.Parse (parts[6]);
////                        double lon2 = double.Parse (parts[7]);

////                        int x1 = (int)(wftA * lon1 + wftC * lat1 + wftE + 0.5);
////                        int y1 = (int)(wftB * lon1 + wftD * lat1 + wftF + 0.5);
////                        int x2 = (int)(wftA * lon2 + wftC * lat2 + wftE + 0.5);
////                        int y2 = (int)(wftB * lon2 + wftD * lat2 + wftF + 0.5);

////                        DrawLine (x1, y1, x2, y2, 2, Color.Magenta);
////                    }
////                }

////                // maybe draw line between waypoints
////                if ((pointa != null) && (pointb != null) && (wayptdb != null)) {
////                    FindWaypoints.Waypoint waypta = FindWaypoints.FindOne (wayptdb, pointa);
////                    FindWaypoints.Waypoint wayptb = FindWaypoints.FindOne (wayptdb, pointb);

////                    double lat1 = waypta.lat;
////                    double lon1 = waypta.lon;
////                    double lat2 = wayptb.lat;
////                    double lon2 = wayptb.lon;

////                    int x1 = (int)(wftA * lon1 + wftC * lat1 + wftE + 0.5);
////                    int y1 = (int)(wftB * lon1 + wftD * lat1 + wftF + 0.5);
////                    int x2 = (int)(wftA * lon2 + wftC * lat2 + wftE + 0.5);
////                    int y2 = (int)(wftB * lon2 + wftD * lat2 + wftF + 0.5);

////                    DrawLine (x1, y1, x2, y2, 2, Color.Blue);
////                }

////                file.Close ();
////            }

////            bmp.Save (markedpng);
////        }
        Environment.Exit (0);
    }

    public static void VerifyPointMapping (double[,] tfw, double[,] wft, ClusPair point, bool iscol)
    {
        double ox = point.GetRefX (0);
        double oy = point.GetRefY (0);
        double x  = ox * scale;
        double y  = oy * scale;

        // lon = tfwA * x + tfwC * y + tfwE + tfwG * x * y
        // lat = tfwB * x + tfwD * y + tfwF + tfwH * x * y
        double lon = tfw[0,8] * x + tfw[2,8] * y + tfw[4,8] + tfw[6,8] * x * y;
        double lat = tfw[1,8] * x + tfw[3,8] * y + tfw[5,8] + tfw[7,8] * x * y;

        if (verbose) {
            Console.WriteLine ("verify scaled x,y=" + x + "," + y + " original x,y=" + ox + "," + oy + " => lat,lon=" + lat + "," + lon + " ? " + point.GetSignedDegs ());
        }

        if (  iscol && (Math.Abs (lat - point.GetSignedDegs ()) > 1e-3)) throw new Exception ("latitude mismatch");
        if (! iscol && (Math.Abs (lon - point.GetSignedDegs ()) > 1e-3)) throw new Exception ("longitude mismatch");
    }

    public static XY LatLon2PixXY (double[,] tfw, double[,] wft, double lat, double lon)
    {
        // x = wftA * lon + wftC * lat + wftE + wftG * lon * lat
        // y = wftB * lon + wftD * lat + wftF + wftH * lon * lat
        double x = wft[0,8] * lon + wft[2,8] * lat + wft[4,8] + wft[6,8] * lon * lat;
        double y = wft[1,8] * lon + wft[3,8] * lat + wft[5,8] + wft[7,8] * lon * lat;

        // lon = tfwA * x + tfwC * y + tfwE + tfwG * x * y
        // lat = tfwB * x + tfwD * y + tfwF + tfwH * x * y
        double nol = tfw[0,8] * x + tfw[2,8] * y + tfw[4,8] + tfw[6,8] * x * y;
        double tal = tfw[1,8] * x + tfw[3,8] * y + tfw[5,8] + tfw[7,8] * x * y;

        if (verbose) {
            Console.WriteLine ("latlon " + lat + "," + lon + " xy " + x + "," + y + " latlon " + tal + "," + nol);
        }

        if ((Math.Abs (nol - lon) > 1.0e-3) || (Math.Abs (tal - lat) > 1.0e-3)) {
            throw new Exception ("bad latlon pixel mapping");
        }

        return new XY ((int) Math.Round (x), (int) Math.Round (y));
    }

    /**
     * @brief Perform matrix multiplication.
     * @returns A * B
     */
    private static double[,] MatMul (double[,] A, double[,] B)
    {
        int arows = A.GetLength (0);
        int acols = A.GetLength (1);
        int brows = B.GetLength (0);
        int bcols = B.GetLength (1);

        if (acols != brows) throw new Exception ("A cols != B rows");

        double[,] C = new double[arows,bcols];

        for (int crow = 0; crow < arows; crow ++) {
            for (int ccol = 0; ccol < bcols; ccol ++) {
                double sum = 0;
                for (int i = 0; i < acols; i ++) {
                    sum += A[crow,i] * B[i,ccol];
                }
                C[crow,ccol] = sum;
            }
        }

        return C;
    }

    private static void PrintMat (string label, double[,] mat)
    {
        if (! verbose) return;
        Console.WriteLine (label);
        int rows = mat.GetLength (0);
        int cols = mat.GetLength (1);
        for (int r = 0; r < rows; r ++) {
            for (int c = 0; c < cols; c ++) {
                Console.Write (String.Format ("  {0,8:F3}", mat[r,c]));
            }
            Console.WriteLine ("");
        }
    }

    private static double[,] MatInv (double[,] A)
    {
        int arows = A.GetLength (0);
        int acols = A.GetLength (1);
        if (arows != acols) throw new Exception ("A not square");

        double[,] T = new double[arows,2*arows];
        for (int row = 0; row < arows; row ++) {
            for (int col = 0; col < arows; col ++) {
                T[row,col] = A[row,col];
            }
            T[row,arows+row] = 1.0;
        }

        RowReduce (T);

        double[,] I = new double[arows,arows];
        for (int row = 0; row < arows; row ++) {
            for (int col = 0; col < arows; col ++) {
                I[row,col] = T[row,arows+col];
            }
        }

        return I;
    }

    private static void RowReduce (double[,] T)
    {
        int trows = T.GetLength (0);
        int tcols = T.GetLength (1);

        double pivot;
        for (int row = 0; row < trows; row ++) {

            /*
             * Make this row's major diagonal column one by
             * dividing the whole row by that number.
             * But if the number is zero, swap with some row below.
             */
            pivot = T[row,row];
            if (pivot == 0.0) {
                int swaprow;
                for (swaprow = row; ++ swaprow < trows;) {
                    pivot = T[swaprow,row];
                    if (pivot != 0.0) {
                        for (int col = 0; col < tcols; col ++) {
                            double tmp = T[row,col];
                            T[row,col] = T[swaprow,col];
                            T[swaprow,col] = tmp;
                        }
                        break;
                    }
                }
                if (swaprow >= trows) throw new Exception ("not invertable");
            }
            if (pivot != 1.0) {
                for (int col = row; col < tcols; col ++) {
                    T[row,col] /= pivot;
                }
            }

            /*
             * Subtract this row from all below it such that we zero out
             * this row's major diagonal column in all rows below.
             */
            for (int rr = row; ++ rr < trows;) {
                pivot = T[rr,row];
                if (pivot != 0.0) {
                    for (int cc = row; cc < tcols; cc ++) {
                        T[rr,cc] -= pivot * T[row,cc];
                    }
                }
            }
        }

        for (int row = trows; -- row >= 0;) {
            for (int rr = row; -- rr >= 0;) {
                pivot = T[rr,row];
                if (pivot != 0.0) {
                    for (int cc = row; cc < tcols; cc ++) {
                        T[rr,cc] -= pivot * T[row,cc];
                    }
                }
            }
        }
    }

    private static void Monochromaticise (Bitmap bm, Bitmap bwbm)
    {
        blacks = new byte[rotdHeight,rotdWidth];

        switch (theta & 3) {
            // original orientation
            case 0: {
                for (int y = 0; y < rotdHeight; y ++) {
                    for (int x = 0; x < rotdWidth; x ++) {
                        Color c = bm.GetPixel (x, y);
                        byte bl = ColorIsBlack (c);
                        blacks[y,x] = bl;
                        bwbm.SetPixel (x, y, (bl != 0) ? Color.Black : Color.White);
                    }
                }
                break;
            }
            // clockwise
            case 1: {
                for (int dsty = 0; dsty < rotdHeight; dsty ++) {
                    for (int dstx = 0; dstx < rotdWidth; dstx ++) {
                        int srcx = dsty;
                        int srcy = rotdWidth - 1 - dstx;
                        Color c = bm.GetPixel (srcx, srcy);
                        byte bl = ColorIsBlack (c);
                        blacks[dsty,dstx] = bl;
                        bwbm.SetPixel (dstx, dsty, (bl != 0) ? Color.Black : Color.White);
                    }
                }
                break;
            }
            // inverted
            case 2: {
                for (int dsty = 0; dsty < rotdHeight; dsty ++) {
                    for (int dstx = 0; dstx < rotdWidth; dstx ++) {
                        int srcx = rotdWidth  - 1 - dstx;
                        int srcy = rotdHeight - 1 - dsty;
                        Color c = bm.GetPixel (srcx, srcy);
                        byte bl = ColorIsBlack (c);
                        blacks[dsty,dstx] = bl;
                        bwbm.SetPixel (dstx, dsty, (bl != 0) ? Color.Black : Color.White);
                    }
                }
                break;
            }
            // counter-clockwise
            case 3: {
                for (int dsty = 0; dsty < rotdHeight; dsty ++) {
                    for (int dstx = 0; dstx < rotdWidth; dstx ++) {
                        int srcx = rotdHeight - 1 - dsty;
                        int srcy = dstx;
                        Color c = bm.GetPixel (srcx, srcy);
                        byte bl = ColorIsBlack (c);
                        blacks[dsty,dstx] = bl;
                        bwbm.SetPixel (dstx, dsty, (bl != 0) ? Color.Black : Color.White);
                    }
                }
                break;
            }
        }
    }

    private static byte ColorIsBlack (Color c)
    {
        return (byte)(((c.A > 200) && (c.R < 100) && (c.G < 100) && (c.B < 100)) ? 1 : 0);
    }

    /**
     * Strip out long horizontal and vertical lines.
     */
    private static void StripLongLines ()
    {
        // strip long horizontal lines
        for (int y = 0; y < rotdHeight; y ++) {
            for (int x = 0; x < rotdWidth - MINLONG; x ++) {
                for (int xx = 0; xx < MINLONG; xx ++) {
                    if (blacks[y,x+xx] == 0) goto nextlongx;
                }
                for (int xx = 0; x + xx < rotdWidth; xx ++) {
                    if (blacks[y,x+xx] == 0) goto nextlongx;
                    blacks[y,x+xx] = 0;
                    rotdBitmap.SetPixel (x + xx, y, Color.White);
                }
            nextlongx:;
            }
        }

        // strip long vertical lines
        for (int x = 0; x < rotdWidth; x ++) {
            for (int y = 0; y < rotdHeight - MINLONG; y ++) {
                for (int yy = 0; yy < MINLONG; yy ++) {
                    if (blacks[y+yy,x] == 0) goto nextlongy;
                }
                for (int yy = 0; y + yy < rotdHeight; yy ++) {
                    if (blacks[y+yy,x] == 0) goto nextlongy;
                    blacks[y+yy,x] = 0;
                    rotdBitmap.SetPixel (x, y + yy, Color.White);
                }
            nextlongy:;
            }
        }

        // strip thin horizontal lines
        for (int y = 1; y < rotdHeight - 1; y ++) {
            for (int x = 0; x < rotdWidth - MINTHIN; x ++) {
                for (int xx = 0; xx < MINTHIN; xx ++) {
                    if (blacks[y-1,x+xx] != 0) goto nextthinx;
                    if (blacks[y,  x+xx] == 0) goto nextthinx;
                    if (blacks[y+1,x+xx] != 0) goto nextthinx;
                }
                for (int xx = 0; x + xx < rotdWidth; xx ++) {
                    if (blacks[y-1,x+xx] != 0) goto nextthinx;
                    if (blacks[y,  x+xx] == 0) goto nextthinx;
                    if (blacks[y+1,x+xx] != 0) goto nextthinx;
                    blacks[y,x+xx] = 0;
                    rotdBitmap.SetPixel (x + xx, y, Color.White);
                }
            nextthinx:;
            }
        }

        // strip thin vertical lines
        for (int x = 1; x < rotdWidth - 1; x ++) {
            for (int y = 0; y < rotdHeight - MINTHIN; y ++) {
                for (int yy = 0; yy < MINTHIN; yy ++) {
                    if (blacks[y+yy,x-1] != 0) goto nextthiny;
                    if (blacks[y+yy,x  ] == 0) goto nextthiny;
                    if (blacks[y+yy,x+1] != 0) goto nextthiny;
                }
                for (int yy = 0; y + yy < rotdHeight; yy ++) {
                    if (blacks[y+yy,x-1] != 0) goto nextthiny;
                    if (blacks[y+yy,x  ] == 0) goto nextthiny;
                    if (blacks[y+yy,x+1] != 0) goto nextthiny;
                    blacks[y+yy,x] = 0;
                    rotdBitmap.SetPixel (x, y + yy, Color.White);
                }
            nextthiny:;
            }
        }
    }

    /**
     * @brief Expand a rectangular perimeter to enclose a blotch of black pixels.
     *        This seems to reliably enclose all characters we care about,
     *        ie, 0-9,N,W,degree,period,apostrophe.
     * @param xlef = current leftmost boundary (inclusive)
     * @param xrit = current rightmost boundary (inclusive)
     * @param ytop = current topmost boundary (inclusive)
     * @param ybot = current bottommost boundary (inclusive)
     * @returns true iff size of box is a nice size, ie, at most MAXWH x MAXWH
     */
    private static bool ExpandPerimeter (ref int xlef, ref int xrit, ref int ytop, ref int ybot)
    {
    top:

        /*
         * If anything left of left edge is black, expand left edge.
         */
        for (int yy = ytop; yy <= ybot; yy ++) {
            if ((blacks[yy,xlef] & (blacks[yy,xlef-1] | blacks[yy-1,xlef-1] | blacks[yy+1,xlef-1])) > 0) {
                xlef --;
                if ((xlef > 0) && (xrit - xlef < MAXWH)) goto top;
                return false;
            }
        }

        /*
         * If anything right of right edge is black, expand right edge.
         */
        for (int yy = ytop; yy <= ybot; yy ++) {
            if ((blacks[yy,xrit] & (blacks[yy,xrit+1] | blacks[yy-1,xrit+1] | blacks[yy+1,xrit+1])) > 0) {
                xrit ++;
                if ((xrit < rotdWidth - 1) && (xrit - xlef < MAXWH)) goto top;
                return false;
            }
        }

        /*
         * If anything above top edge is black, expand top edge.
         */
        for (int xx = xlef; xx <= xrit; xx ++) {
            if ((blacks[ytop,xx] & (blacks[ytop-1,xx] | blacks[ytop-1,xx-1] | blacks[ytop-1,xx+1])) > 0) {
                ytop --;
                if ((ytop > 0) && (ybot - ytop < MAXWH)) goto top;
                return false;
            }
        }

        /*
         * If anything below bottom edge is black, expand bottom edge.
         */
        for (int xx = xlef; xx <= xrit; xx ++) {
            if ((blacks[ybot,xx] & (blacks[ybot+1,xx] | blacks[ybot+1,xx-1] | blacks[ybot+1,xx+1])) > 0) {
                ybot ++;
                if ((ybot < rotdHeight - 1) && (ybot - ytop < MAXWH)) goto top;
                return false;
            }
        }

        return true;
    }


    /**
     * @brief Draw box to bitmap.
     */
    public static void DrawBox (int x1, int y1, int x2, int y2, Color c)
    {
        DrawLine (x1, y1, x2, y1, c);
        DrawLine (x2, y1, x2, y2, c);
        DrawLine (x2, y2, x1, y2, c);
        DrawLine (x1, y2, x1, y1, c);
    }

    /**
     * @brief Draw cross on image.
     */
    private static void DrawCross (int x, int y, int size, int thick, Color c)
    {
        for (int ss = -size; ss <= size; ss ++) {
            for (int tt = -thick; tt <= thick; tt ++) {
                SetPixel (x + ss, y + tt, c);
                SetPixel (x + tt, y + ss, c);
            }
        }
    }

    /**
     * @brief Draw line on image.
     */
    public static void DrawLine (int x1, int y1, int x2, int y2, Color c)
    {
        DrawLine (x1, y1, x2, y2, 0, c);
    }
    public static void DrawLine (int x1, int y1, int x2, int y2, int thick, Color c)
    {
        int x, y;

        if (x2 != x1) {
            if (x2 < x1) {
                x = x1;
                y = y1;
                x1 = x2;
                y1 = y2;
                x2 = x;
                y2 = y;
            }
            for (x = x1; x <= x2; x ++) {
                y = (int)((float)(y2 - y1) / (float)(x2 - x1) * (float)(x - x1)) + y1;
                for (int xt = x - thick; xt <= x + thick; xt ++) {
                    for (int yt = y - thick; yt <= y + thick; yt ++) {
                        SetPixel (xt, yt, c);
                    }
                }
            }
        }

        if (y2 != y1) {
            if (y2 < y1) {
                y = y1;
                x = x1;
                y1 = y2;
                x1 = x2;
                y2 = y;
                x2 = x;
            }
            for (y = y1; y <= y2; y ++) {
                x = (int)((float)(x2 - x1) / (float)(y2 - y1) * (float)(y - y1)) + x1;
                for (int xt = x - thick; xt <= x + thick; xt ++) {
                    for (int yt = y - thick; yt <= y + thick; yt ++) {
                        SetPixel (xt, yt, c);
                    }
                }
            }
        }
    }

    public static void SetPixel (int x, int y, Color c)
    {
        if ((x >= 0) && (x < rotdWidth) && (y >= 0) && (y < rotdHeight)) {
            rotdBitmap.SetPixel (x, y, c);
        }
    }
}

public class SortClusPairByX : IComparer<ClusPair> {
    public int Compare (ClusPair cpa, ClusPair cpb)
    {
        int cmp = cpa.GetRefX (0) - cpb.GetRefX (0);
        if (cmp == 0) cmp = cpa.GetRefY (0) - cpb.GetRefY (0);
        return cmp;
    }
}

public class SortClusPairByY : IComparer<ClusPair> {
    public int Compare (ClusPair cpa, ClusPair cpb)
    {
        int cmp = cpa.GetRefY (0) - cpb.GetRefY (0);
        if (cmp == 0) cmp = cpa.GetRefX (0) - cpb.GetRefX (0);
        return cmp;
    }
}

// row or column of cluster pairs
public class ClusPairRC {
    public double hival = double.NaN;
    public double loval = double.NaN;
    public int theta;
    public LinkedList<ClusPair> cluspairs = new LinkedList<ClusPair> ();

    private int hix, hiy;   // x,y limits in rotated image
    private int lox, loy;

    public void AddClusPair (ClusPair cp)
    {
        double degs = cp.GetDegrees ();
        if (cluspairs.Count > 0) {
            lox = Math.Min (lox, cp.GetLoX ());
            loy = Math.Min (loy, cp.GetLoY ());
            hix = Math.Max (hix, cp.GetHiX ());
            hiy = Math.Max (hiy, cp.GetHiY ());
            loval = Math.Min (loval, degs);
            hival = Math.Max (hival, degs);
        } else {
            lox = cp.GetLoX ();
            loy = cp.GetLoY ();
            hix = cp.GetHiX ();
            hiy = cp.GetHiY ();
            loval = hival = degs;
        }
        cluspairs.AddLast (cp);
    }

    // get x,y limits in rotated image
    public int RotdHiX { get { return hix; } }
    public int RotdHiY { get { return hiy; } }
    public int RotdLoX { get { return lox; } }
    public int RotdLoY { get { return loy; } }

    // get x,y limits in original image
    public int OrigHiX { get { switch (theta & 3) {
        case 0: return hix;
        case 1: return hiy;
        case 2: return ReadEuroIAPPng.origWidth - 1 - hix;
        case 3: return ReadEuroIAPPng.origWidth - 1 - hiy;
        default: throw new Exception ("bad theta");
    }}}
    public int OrigHiY { get { switch (theta & 3) {
        case 0: return hiy;
        case 1: return ReadEuroIAPPng.origHeight - 1 - hix;
        case 2: return ReadEuroIAPPng.origHeight - 1 - hiy;
        case 3: return hix;
        default: throw new Exception ("bad theta");
    }}}
    public int OrigLoX { get { switch (theta & 3) {
        case 0: return lox;
        case 1: return loy;
        case 2: return ReadEuroIAPPng.origWidth - 1 - lox;
        case 3: return ReadEuroIAPPng.origWidth - 1 - loy;
        default: throw new Exception ("bad theta");
    }}}
    public int OrigLoY { get { switch (theta & 3) {
        case 0: return loy;
        case 1: return ReadEuroIAPPng.origHeight - 1 - lox;
        case 2: return ReadEuroIAPPng.origHeight - 1 - loy;
        case 3: return lox;
        default: throw new Exception ("bad theta");
    }}}

    // see if the row on rotated image is good
    // - must have at least 2 points
    // - the two end values cannot be the same
    // - there can't be more than 2 degrees difference
    // - intermediate points must be proportional
    public bool IsGoodRotdRow ()
    {
        if (cluspairs.Count < 2) return false;
        if (hival == loval) return false;
        if (hival - loval > 2.0) return false;
        if (hix == lox) return false;

        ClusPair first = cluspairs.First.Value;
        ClusPair last  = cluspairs.Last.Value;

        double firstdeg  = first.GetDegrees ();
        int firstrefx    = first.GetRefX (0);
        double degperpix = (last.GetDegrees () - firstdeg) / (last.GetRefX (0) - firstrefx);

        //TODO handle gatwick plates what have a 0 in the middle
        first.neg = degperpix < 0;

        for (LinkedListNode<ClusPair> cplln = cluspairs.First; (cplln = cplln.Next) != null;) {
            ClusPair cp = cplln.Value;
            double actvalue = cp.GetDegrees () - firstdeg;
            double estvalue = (cp.GetRefX (0) - firstrefx) * degperpix;
            double ratio = actvalue / estvalue;
            if ((ratio < 0.9) || (ratio > 1.1)) return false;
            cp.neg = degperpix < 0;
        }

        return true;
    }

    // see if the column on rotated image is good
    // - must have at least 2 points
    // - the two end values cannot be the same
    // - there can't be more than 2 degrees difference
    // - intermediate points must be proportional
    public bool IsGoodRotdCol ()
    {
        if (IsGoodColRotdWork ()) return true;
        int numcps = cluspairs.Count;
        if (numcps < 4) return false;

        ClusPair[] cluspairarray = new ClusPair[numcps];
        cluspairs.CopyTo (cluspairarray, 0);
        for (int itoskip = 0; itoskip < numcps; itoskip ++) {
            ClusPairRC newcpcol = new ClusPairRC ();
            newcpcol.theta = theta;
            for (int i = 0; i < numcps; i ++) {
                if (i != itoskip) {
                    newcpcol.AddClusPair (cluspairarray[i]);
                }
            }
            if (newcpcol.IsGoodColRotdWork ()) {
                hival     = newcpcol.hival;
                loval     = newcpcol.loval;
                hix       = newcpcol.hix;
                hiy       = newcpcol.hiy;
                lox       = newcpcol.lox;
                loy       = newcpcol.loy;
                cluspairs = newcpcol.cluspairs;
                return true;
            }
        }
        return false;
    }

    private bool IsGoodColRotdWork ()
    {
        if (cluspairs.Count < 2) return false;
        if (hival == loval) return false;
        if (hival - loval > 2.0) return false;
        if (hiy == loy) return false;

        ClusPair first = cluspairs.First.Value;
        ClusPair last  = cluspairs.Last.Value;

        double firstdeg  = first.GetDegrees ();
        int firstrefy    = first.GetRefY (0);
        double degperpix = (last.GetDegrees () - firstdeg) / (last.GetRefY (0) - firstrefy);

        first.neg = degperpix > 0;

        for (LinkedListNode<ClusPair> cplln = cluspairs.First; (cplln = cplln.Next) != null;) {
            ClusPair cp = cplln.Value;
            double actvalue = cp.GetDegrees () - firstdeg;
            double estvalue = (cp.GetRefY (0) - firstrefy) * degperpix;
            double ratio = actvalue / estvalue;
            if ((ratio < 0.9) || (ratio > 1.1)) return false;
            cp.neg = degperpix > 0;
        }

        return true;
    }
}

/**
 * Degree/Minute strings on the tick marks along left,right,top,bottom edges of main box.
 */
public class ClusPair {
    public bool    neg;         // leading minus sign
    public char    cptag;       // 'N' 'S' 'E' 'W' or nil
    public Cluster degclus;     // "nn^"
    public Cluster minclus;     // "nn'" or null

    public int GetHiX () { return (minclus == null) ? degclus.hix : Math.Max (degclus.hix, minclus.hix); }
    public int GetHiY () { return (minclus == null) ? degclus.hiy : Math.Max (degclus.hiy, minclus.hiy); }
    public int GetLoX () { return (minclus == null) ? degclus.lox : Math.Min (degclus.lox, minclus.lox); }
    public int GetLoY () { return (minclus == null) ? degclus.loy : Math.Min (degclus.loy, minclus.loy); }

    public void DrawBox ()
    {
        ReadEuroIAPPng.DrawBox (GetLoX () - 3, GetLoY () - 3, GetHiX () + 3, GetHiY () + 3, Color.Green);
    }

    public double GetDegrees ()
    {
        double degval = degclus.Value;
        double minval = (minclus == null) ? 0.0 : minclus.Value;
        if (minval >= 60.0) return double.NaN;
        return degval + minval / 60.0;
    }

    public double GetSignedDegs ()
    {
        double d = GetDegrees ();
        return neg ? - d : d;
    }

    // get clusterpair centerpoint X
    //  theta = 0: X on rotated image
    //        > 0: X on original image
    public int GetRefX (int theta)
    {
        switch (theta & 3) {
            case 0: return (GetLoX () + GetHiX ()) / 2;
            case 1: return GetRefY (0);
            case 2: return ReadEuroIAPPng.origWidth - 1 - GetRefX (0);
            case 3: return ReadEuroIAPPng.origWidth - 1 - GetRefY (0);
            default: throw new Exception ("bad theta");
        }
    }

    // get clusterpair centerpoint Y
    //  theta = 0: Y on rotated image
    //        > 0: Y on original image
    public int GetRefY (int theta)
    {
        switch (theta & 3) {
            case 0: return (GetLoY () + GetHiY ()) / 2;
            case 1: return ReadEuroIAPPng.origHeight - 1 - GetRefX (0);
            case 2: return ReadEuroIAPPng.origHeight - 1 - GetRefY (0);
            case 3: return GetRefX (0);
            default: throw new Exception ("bad theta");
        }
    }

    // tfw_a * x + tfw_c * y + tfw_e + tfw_g * x * y = lon
    public void FillTfwLonRow (double[,] tfwmat, int r, int theta)
    {
        Corner c;
        c.lat = double.NaN;
        c.lon = GetSignedDegs ();
        c.scaledx = GetRefX (theta) * ReadEuroIAPPng.scale;
        c.scaledy = GetRefY (theta) * ReadEuroIAPPng.scale;
        c.FillTfwLonRow (tfwmat, r);
    }

    // tfw_b * x + tfw_d * y + tfw_f + tfw_h * x * y = lat
    public void FillTfwLatRow (double[,] tfwmat, int r, int theta)
    {
        Corner c;
        c.lat = GetSignedDegs ();
        c.lon = double.NaN;
        c.scaledx = GetRefX (theta) * ReadEuroIAPPng.scale;
        c.scaledy = GetRefY (theta) * ReadEuroIAPPng.scale;
        c.FillTfwLatRow (tfwmat, r);
    }

    public override string ToString ()
    {
        StringBuilder sb = new StringBuilder ();
        sb.Append ("(");
        sb.Append (GetLoX ());
        sb.Append (",");
        sb.Append (GetLoY ());
        sb.Append (")..(");
        sb.Append (GetHiX ());
        sb.Append (",");
        sb.Append (GetHiY ());
        sb.Append (") [");
        sb.Append (degclus.Result);
        if (minclus != null) {
            sb.Append (" ");
            sb.Append (minclus.Result);
        }
        sb.Append ("] (");
        sb.Append (GetRefX (0));
        sb.Append (",");
        sb.Append (GetRefY (0));
        sb.Append (")");
        return sb.ToString ();
    }
}

public struct Corner {
    public double lat, lon, scaledx, scaledy;

    // tfw_a * x + tfw_c * y + tfw_e + tfw_g * x * y = lon
    public void FillTfwLonRow (double[,] tfwmat, int r)
    {
        tfwmat[r,0] = scaledx;
        tfwmat[r,1] = 0.0;
        tfwmat[r,2] = scaledy;
        tfwmat[r,3] = 0.0;
        tfwmat[r,4] = 1.0;
        tfwmat[r,5] = 0.0;
        tfwmat[r,6] = scaledx * scaledy;
        tfwmat[r,7] = 0.0;
        tfwmat[r,8] = lon;
    }

    // tfw_b * x + tfw_d * y + tfw_f + tfw_h * x * y = lat
    public void FillTfwLatRow (double[,] tfwmat, int r)
    {
        tfwmat[r,0] = 0.0;
        tfwmat[r,1] = scaledx;
        tfwmat[r,2] = 0.0;
        tfwmat[r,3] = scaledy;
        tfwmat[r,4] = 0.0;
        tfwmat[r,5] = 1.0;
        tfwmat[r,6] = 0.0;
        tfwmat[r,7] = scaledx * scaledy;
        tfwmat[r,8] = lat;
    }

    // wft_a * lon + wft_c * lat + wft_e + wft_g * lon * lat = x
    public void FillWftXRow (double[,] wftmat, int r)
    {
        wftmat[r,0] = lon;
        wftmat[r,1] = 0.0;
        wftmat[r,2] = lat;
        wftmat[r,3] = 0.0;
        wftmat[r,4] = 1.0;
        wftmat[r,5] = 0.0;
        wftmat[r,6] = lon * lat;
        wftmat[r,7] = 0.0;
        wftmat[r,8] = scaledx;
    }

    // wft_b * lon + wft_d * lat + wft_f + wft_h * lon * lat = y
    public void FillWftYRow (double[,] wftmat, int r)
    {
        wftmat[r,0] = 0.0;
        wftmat[r,1] = lon;
        wftmat[r,2] = 0.0;
        wftmat[r,3] = lat;
        wftmat[r,4] = 0.0;
        wftmat[r,5] = 1.0;
        wftmat[r,6] = 0.0;
        wftmat[r,7] = lon * lat;
        wftmat[r,8] = scaledy;
    }
}

/**
 * @brief Contains a string recovered from the pixels in the image pixels.
 */
public class Cluster {
    public const int WIDTH  = 125;  // widest cluster
    public const int HEIGHT =  22;  // highest cluster box
    public const int LINEFUZZ = 50; // distance bewteen string and associated lat/lon line
                                    // big number for BIX 30^24'N
    public const int MINTICKS = 3;  // sometimes numbers are near the main outline
                                    // and the margin appears similar to a tick line
                                    // except it doesn't have many ticks.  so only
                                    // accept tick lines if they have at least this
                                    // many ticks.  see KAFW diagram along the bottom,
                                    // and KTMB along right side.  but KABY only has
                                    // 4 ticks along 31^32.5'N.  and MWC has only 3 ticks
                                    // for 43^06.5'N and SPG has only 3 for 27^46.0'N.
    public const int SEMITILT = 21; // maximum semi-tilt segments we will accept
                                    // 21 for KTUL

    public int lox = 999999999;     // upper left corner inclusive
    public int loy = 999999999;
    public int hix = -1;            // lower right corner exclusive
    public int hiy = -1;

    public LinkedList<Deco> decos = new LinkedList<Deco> ();

    /**
     * @before Insert a character in list by ascending X value.
     * @param x = x pixel of upper left corner of character
     * @param y = y pixel of upper left corner of character
     * @param c = character to be added
     */
    public void InsertDeco (Deco deco)
    {
        if (lox > deco.x) lox = deco.x;
        if (loy > deco.y) loy = deco.y;
        if (hix < deco.x + deco.w) hix = deco.x + deco.w;
        if (hiy < deco.y + deco.h) hiy = deco.y + deco.h;

        for (LinkedListNode<Deco> ptr = decos.First; ptr != null; ptr = ptr.Next) {
            if (deco.x < ptr.Value.x) {
                decos.AddBefore (ptr, deco);
                return;
            }
        }
        decos.AddLast (deco);
    }

    /**
     * @brief Get resultant string.
     */
    public string Result {
        get {
            char[] chars = new char[decos.Count];
            int i = 0;
            foreach (Deco deco in decos) {
                if ((deco.c == '\'') && (i > 0) && (chars[i-1] == '\'')) {
                    chars[i-1] = '"';
                } else {
                    chars[i++] = deco.c;
                }
            }
            return new String (chars, 0, i);
        }
    }

    /**
     * Maybe cluster begins or ends with N, S, E, W
     * Return the char if so, or a 0 if not
     */
    public char ClusTag {
        get {
            if (decos.Count == 0) return (char)0;
            string res = Result;
            char tag = res[0];
            if ((tag == 'N') || (tag == 'S') || (tag == 'E') || (tag == 'W')) return tag;
            tag = res[res.Length-1];
            return ((tag != 'N') && (tag != 'S') && (tag != 'E') && (tag != 'W')) ? (char)0 : tag;
        }
    }

    public char ClusTagSfx {
        get {
            if (decos.Count == 0) return (char)0;
            string res = Result;
            char tag = res[res.Length-1];
            return ((tag != 'N') && (tag != 'S') && (tag != 'E') && (tag != 'W')) ? (char)0 : tag;
        }
    }

    /**
     * @brief Determine if this cluster is a valid lat or lon string.
     *        optionally starts with N, S, E, W
     *        digits
     *        optionally followed by ^ or '
     *        optionally followed by N, S, E, W
     */
    public bool IsLatLon {
        get {

            // get the string as we have it
            string r = Result;

            // append null terminator so we don't have to keep checking subscripts
            r += (char)0;

            // maybe haf N S E W on front
            int i = 0;
            char c = r[i];
            bool hasnsewpfx = (c == 'N') || (c == 'S') || (c == 'E') || (c == 'W');
            if (hasnsewpfx) i ++;

            // must start with a digit
            c = r[i++];
            if ((c < '0') || (c > '9')) return false;
            int ndigs = 0;
            int ival = 0;

            // skip other digits
            do {
                ival = ival * 10 + (c - '0');
                ndigs ++;
                c = r[i++];
            } while ((c >= '0') && (c <= '9'));

            // if 4 or 5 digits,
            //  might be ddmmq or dddmmq
            //  like 5100N or 11230E
            //   or N5100, E11230
            if ((ndigs == 4) || (ndigs == 5)) {
                int imin = ival % 100;
                if (imin > 59) return false;
                if (! hasnsewpfx) {
                    if ((c != 'N') && (c != 'S') && (c != 'E') && (c != 'W')) return false;
                    c = r[i++];
                }
                return c == (char)0;
            }

            // must be 2 or 3 digits
            if ((ndigs < 2) || (ndigs > 3)) return false;

            // maybe have a ^ or ' next
            if ((c == '^') || (c == '\'')) {
                c = r[i++];
            }

            // maybe have N S E W next
            if (! hasnsewpfx) {
                if ((c == 'N') || (c == 'S') || (c == 'E') || (c == 'W')) {
                    c = r[i++];
                }
            }

            // make sure it's the end of the string
            return c == (char)0;
        }
    }

    // gets value
    // assumes IsLatLon returns true
    public double Value {
        get {
            string r = Result;
            int val = 0;
            int ndigs = 0;
            for (int i = 0; i < r.Length; i ++) {
                char c = r[i];
                if ((c < '0') || (c > '9')) break;
                val = val * 10 + (c - '0');
                ndigs ++;
            }
            if (ndigs < 4) return val;
            int ideg = val / 100;
            int imin = val % 100;
            return ideg + imin / 60.0;
        }
    }

    public override string ToString ()
    {
        return Result + ":" +
                " loxy=(" + lox + "," + loy + ")" +
                " hixy=(" + hix + "," + hiy + ")";
    }
}

/**
 * @brief A decoded character from the image and where it was found.
 */
public class Deco {
    public const int MAXWH = 30;

    public int x;       // upper left corner x
    public int y;       // upper left corner y
    public int w;       // number of pixels wide (exclusive)
    public int h;       // number of pixels high (exclusive)
    public char c;      // resultant character
    public byte[,] grays;
}

/**
 * @brief Character given on the command line and
 *        approximately where it should be found in image.
 */
public class Given {
    public bool found;
    public int x;
    public int y;
    public char c;

    public Given (int xx, int yy, string ss)
    {
        x = xx;
        y = yy;
        if (ss.Length != 1) throw new Exception ("given string not a single char " + ss);
        c = ss[0];
    }
}
