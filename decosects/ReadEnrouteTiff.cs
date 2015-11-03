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
 *  Read an IFR Enroute chart TIFF file and find the lat/lon marks and correlate them to pixel number.
 *
 *  yum install libexif-0.6.21-6.el7.x86_64 giflib-4.1.6-9.el7.x86_64
 *  rpm -ihv libgdiplus-2.10-9.el7.x86_64.rpm
 *  ln -s /lib64/libgdiplus.so.0 /lib64/libgdiplus.so
 *  gmcs -debug -unsafe -out:ReadEnrouteTiff.exe -reference:System.Drawing.dll ReadEnrouteTiff.cs ChartTiff.cs
 *
 *  cd charts
 *  mono --debug ../ReadEnrouteTiff.exe -stages -verbose 'ENR ELUS33 20150430'
 *        -csvoutfile ELUS33.csv -markedpng ELUS33.png
 */

using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Text;

/**
 * @brief A lat/lon line.
 */
public class LongLine {
    public int begx;    // one end of the lat/lon line
    public int begy;
    public int endx;    // other end of the lat/lon line
    public int endy;
    public int ticks;
    public double rounded;  // rounded lat/lon

    public override String ToString ()
    {
        return "(" + begx + "," + begy + ") .. (" + endx + "," + endy + ")";
    }
}

public class ReadEnrouteTiff {
    public const int MINLINELEN = 100;
    public const int MAXWH    =   50;               // maximum width and height of a character box
    public const int TICKMIN  =   10;
    public const int TICKMAX  =   50;

    public const double TOLERANCE = 0.12;           // degrees tolerance for lat/lon lines
    public const int LINEGAP = 5;                   // max gap between lines at same lat or lon

    public static bool stages;                      // -stages
    public static bool verbose;                     // -verbose
    public static string csvoutfile;                // -csvoutfile
    public static string csvoutid;                  // -csvoutid
    public static string markedpng;                 // -markedpng

    public static byte[,] blacks;                   // currently operating array of black pixels [y,x]
    public static ChartTiff chart;
    public static double maxlat, maxlon, minlat, minlon;
    public static int height;                       // height of blacks[h,w]
    public static int width;                        // width  of blacks[h,w]
    public static LinkedList<LongLine> horzLines = new LinkedList<LongLine> ();
    public static LinkedList<LongLine> vertLines = new LinkedList<LongLine> ();
    public static string chartname;                 // original chart spacename

    public static void Main (string[] args)
    {
        for (int i = 0; i < args.Length; i ++) {
            string arg = args[i];

            if (arg == "-csvoutfile") {
                if (++ i >= args.Length) goto usage;
                csvoutfile = args[i];
                continue;
            }
            if (arg == "-markedpng") {
                if (++ i >= args.Length) goto usage;
                markedpng = args[i];
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
            if (arg[0] == '-') goto usage;
            if (chartname != null) goto usage;
            chartname = arg;
        }
        if (chartname == null) goto usage;

        /*
         * Read chart and straighten out according to given Lambert and TFW values.
         * Chart isn't quite straight and not scaled quite correctly.
         */
        chart = new ChartTiff ("", chartname);
        BuildRotatedImage ();
        chart.bmp.Dispose ();
        chart.bmp = null;
        if (stages) SaveStage ("stage1.png", false);

        /*
         * Find lat/lon segment lines.
         */
        FindSegments ();
        if (stages) SaveStage ("stage4.png", true);

        /*
         * Group the lat/lon segment lines together into one long line per lat/lon.
         */
        GroupLatLonSegments ();
        if (stages) SaveStage ("stage5.png", true);

        /*
         * Rebuild TFW matrix to match lat/lon lines found on chart.
         */
        RebuildTFWMatrix ();

        /*
         * If option given, write out updated CSV file.
         */
        if (csvoutfile != null) {
            chart.UpdateCSVFile (csvoutfile);
        }

        /*
         * If option given, write out marked-up PNG file.
         */
        if (markedpng != null) {
            Bitmap bmp = new Bitmap (chartname + ".tif");
            MarkedPngSetter mps = new MarkedPngSetter ();
            mps.bmp = bmp;
            for (int lon = (int)minlon - 1; lon <= (int)maxlon; lon ++) {
                for (int lat = (int)minlat - 1; lat <= (int)maxlat; lat ++) {
                    int x, y;
                    chart.LatLon2Pixel (lat, lon, out x, out y);
                    DrawCross (mps, x, y, 20, 5);
                }
            }
            bmp.Save (markedpng, ImageFormat.Png);
        }

        return;

    usage:
        Console.WriteLine ("usage: mono ReadEnrouteTiff.exe <inputchartname>");
        Console.WriteLine ("           -csvoutfile <filename>         - write data to given file");
        Console.WriteLine ("           -markedpng <outputpngfilename> - write out marked-up png file");
        Console.WriteLine ("           -stages                        - create intermediate png files (debugging)");
        Console.WriteLine ("           -verbose                       - output intermediate messages (debugging)");
    }

    /**
     * @brief Take the original chart and rotate it as given by the existing TFW matrix.
     *        Also do Lambert projection.
     * @returns blacks = rotated image
     *           width = rotated image width
     *          height = rotated image height
     */
    private static void BuildRotatedImage ()
    {
        int h = chart.height;
        int w = chart.width;

        /*
         * Get lat/lon at the four corners of the chart, including any legend area.
         */
        double lat1, lat2, lat3, lat4;
        double lon1, lon2, lon3, lon4;

        chart.Pixel2LatLon (0, 0, out lat1, out lon1);
        chart.Pixel2LatLon (w, 0, out lat2, out lon2);
        chart.Pixel2LatLon (0, h, out lat3, out lon3);
        chart.Pixel2LatLon (w, h, out lat4, out lon4);

        /*
         * Get min/max of the lat/lon that could possibly be in the chart.
         */
        minlat = Math.Floor   (Math.Min (Math.Min (lat1, lat2), Math.Min (lat3, lat4)));
        minlon = Math.Floor   (Math.Min (Math.Min (lon1, lon2), Math.Min (lon3, lon4)));
        maxlat = Math.Ceiling (Math.Max (Math.Max (lat1, lat2), Math.Max (lat3, lat4)));
        maxlon = Math.Ceiling (Math.Max (Math.Max (lon1, lon2), Math.Max (lon3, lon4)));

        /*
         * Calc worst-case rotation width / height needed.
         */
        int newbmwh = (h > w ? h : w) * 3 / 2;

        /*
         * Get values with correct aspect ratio to keep lat/lon boxes somewhat square.
         */
        if (maxlat - minlat > maxlon - minlon) {
            // portrait style
            height = newbmwh;
            width  = (int)(newbmwh * (maxlon - minlon) / (maxlat - minlat));
        } else {
            // landscape style
            height = (int)(newbmwh * (maxlat - minlat) / (maxlon - minlon));
            width  = newbmwh;
        }

        /*
         * Create rotate image bitmap and copy original chart to rotated image bitmap.
         * Just copy the lat/lon lines and filter out everything else.
         */
        blacks = new byte[height,width];
        int minoldx = chartname.Contains (" ELUS23 ") ? 5100 : 0;
        for (int newy = 0; newy < height; newy ++) {
            for (int newx = 0; newx < width; newx ++) {
                int oldx, oldy;
                MapNewOldPix (newx, newy, out oldx, out oldy);
                if ((oldx >= minoldx) && (oldx < chart.width) && (oldy >= 0) && (oldy < chart.height)) {
                    Color c = chart.bmp.GetPixel (oldx, oldy);
                    blacks[newy,newx] = ColorIsBlack (c);
                }
            }
        }
    }

    /**
     * @brief Filter the lat/lon line color (actually a light blue).
     */
    private static byte ColorIsBlack (Color c)
    {
        int r = c.R;
        int g = c.G;
        int b = c.B;

        // 80 130 215

        if ((r >=  75) && (r <=  85) &&
            (g >= 125) && (g <= 135) &&
            (b >= 210) && (b <= 225)) return (byte)1;

        return (byte)0;
    }

    /**
     * @brief Scan through image looking for line segments of minimum length MINLINELEN.
     *        Remove each used segment from blacks[,] array.
     *        Overwrite the segments with red in the bitmap image for debugging.
     */
    public static void FindSegments ()
    {
        for (int y = 1; y < height - 1; y ++) {
            int len = 0;
            for (int x = 1; x < width - 1; x ++) {
                if (blacks[y,x] > 0) len ++;
                else {
                    if (len > MINLINELEN) {
                        LongLine ll = new LongLine ();
                        ll.begx  = x - len;
                        ll.begy  = y;
                        ll.endx  = x - 1;
                        ll.endy  = y;
                        horzLines.AddLast (ll);
                    }
                    len = 0;
                }
            }
        }

        for (int x = 1; x < width - 1; x ++) {
            int len = 0;
            for (int y = 1; y < height - 1; y ++) {
                if (blacks[y,x] > 0) len ++;
                else {
                    if (len > MINLINELEN) {
                        LongLine ll = new LongLine ();
                        ll.begx  = x;
                        ll.begy  = y - len;
                        ll.endx  = x;
                        ll.endy  = y - 1;
                        vertLines.AddLast (ll);
                    }
                    len = 0;
                }
            }
        }

        /*
         * Erase the blacks[,] entries so we don't try to use them again.
         */
        foreach (LongLine ll in horzLines) {
            for (int y = ll.begy; y <= ll.endy; y ++) {
                for (int x = ll.begx; x <= ll.endx; x ++) blacks[y,x] = 0;
            }
        }
        foreach (LongLine ll in vertLines) {
            for (int y = ll.begy; y <= ll.endy; y ++) {
                for (int x = ll.begx; x <= ll.endx; x ++) blacks[y,x] = 0;
            }
        }
    }

    /**
     * @brief Group the found segments into one long line per lat or lon.
     */
    public static void GroupLatLonSegments ()
    {
        int lasty = -LINEGAP;
        int lox = 0;
        int loy = 0;
        int hix = 0;
        int hiy = 0;
        LinkedList<LongLine> horzLongs = new LinkedList<LongLine> ();
        foreach (LongLine ll in horzLines) {
            int y = ll.begy;
            if (y > lasty + LINEGAP) {
                if (lox > 0) {
                    LongLine newll = new LongLine ();
                    newll.begx = lox;
                    newll.begy = loy;
                    newll.endx = hix;
                    newll.endy = hiy;
                    CountVertTicks (newll);
                    horzLongs.AddLast (newll);
                }

                lox = ll.begx;
                loy = ll.begy;
                hix = ll.endx;
                hiy = ll.endy;
            } else {
                if (lox > ll.begx) {
                    lox = ll.begx;
                    loy = ll.begy;
                }
                if (hix < ll.endx) {
                    hix = ll.endx;
                    hiy = ll.endy;
                }
            }
            lasty = y;
        }
        horzLines = horzLongs;

        int lastx = -LINEGAP;
        lox = 0;
        loy = 0;
        hix = 0;
        hiy = 0;
        LinkedList<LongLine> vertLongs = new LinkedList<LongLine> ();
        foreach (LongLine ll in vertLines) {
            int x = ll.begx;
            if (x > lastx + LINEGAP) {
                if (lox > 0) {
                    LongLine newll = new LongLine ();
                    newll.begx = lox;
                    newll.begy = loy;
                    newll.endx = hix;
                    newll.endy = hiy;
                    CountHorizTicks (newll);
                    vertLongs.AddLast (newll);
                }

                lox = ll.begx;
                loy = ll.begy;
                hix = ll.endx;
                hiy = ll.endy;
            } else {
                if (loy > ll.begy) {
                    lox = ll.begx;
                    loy = ll.begy;
                }
                if (hiy < ll.endy) {
                    hix = ll.endx;
                    hiy = ll.endy;
                }
            }
            lastx = x;
        }
        vertLines = vertLongs;
    }

    public static void CountVertTicks (LongLine ll)
    {
        int dy;
        int y = (ll.begy + ll.endy) / 2;
        for (int x = ll.begx; x < ll.endx; x ++) {
            for (dy = 0; y + dy < height; dy ++) {
                if (blacks[y+dy,x] == 0) break;
            }
            if ((dy >= TICKMIN) && (dy <= TICKMAX)) {
                ll.ticks ++;
                x += 5;
                continue;
            }
            for (dy = 0; y - dy >= 0; dy ++) {
                if (blacks[y-dy,x] == 0) break;
            }
            if ((dy >= TICKMIN) && (dy <= TICKMAX)) {
                ll.ticks ++;
                x += 5;
            }
        }
    }

    public static void CountHorizTicks (LongLine ll)
    {
        int dx;
        int x = (ll.begx + ll.endx) / 2;
        for (int y = ll.begy; y < ll.endy; y ++) {
            for (dx = 0; x + dx < width; dx ++) {
                if (blacks[y,x+dx] == 0) break;
            }
            if ((dx >= TICKMIN) && (dx <= TICKMAX)) {
                ll.ticks ++;
                y += 5;
                continue;
            }
            for (dx = 0; x - dx >= 0; dx ++) {
                if (blacks[y,x-dx] == 0) break;
            }
            if ((dx >= TICKMIN) && (dx <= TICKMAX)) {
                ll.ticks ++;
                y += 5;
            }
        }
    }

    /**
     * @brief Rebuild the TFW matrix from the lat/lon lines found on chart.
     */
    public static void RebuildTFWMatrix ()
    {
        /*
         * Get the widest lat/lon lines to minimize rounding errors.
         * Use the ones having the most tick marks.
         */
        LongLine botmost  = null;
        LongLine leftmost = null;
        LongLine ritemost = null;
        LongLine topmost  = null;

        foreach (LongLine ll in horzLines) {
            int newy = (ll.begy + ll.endy) / 2;
            double lat = newy * (minlat - maxlat) / height + maxlat;
            if (verbose) Console.WriteLine ("horiz line: " + ll.ToString () + "  lat " + lat);
            double rounded = Math.Round (lat);
            if (Math.Abs (rounded - lat) < TOLERANCE) {
                ll.rounded = rounded;
                SaveMostTickMarks (ll, ref topmost, ref botmost);
            }
        }

        foreach (LongLine ll in vertLines) {
            int newx = (ll.begx + ll.endx) / 2;
            double lon = newx * (maxlon - minlon) / width  + minlon;
            if (verbose) Console.WriteLine (" vert line: " + ll.ToString () + "  lon " + lon);
            double rounded = Math.Round (lon);
            if (Math.Abs (rounded - lon) < TOLERANCE) {
                ll.rounded = rounded;
                SaveMostTickMarks (ll, ref leftmost, ref ritemost);
            }
        }

        /*
         * The bottom line on the ELUS25 is a mess.
         */
        if ((botmost == null) && chartname.Contains (" ELUS25 ")) {
            botmost = new LongLine ();
            botmost.begx = 0;
            botmost.endx = width;
            botmost.begy = botmost.endy = 10982;
            botmost.rounded = 35.0;
        }

        /*
         * Make sure we have each needed line.
         */
        if ((topmost == null) || (botmost == null)) throw new Exception ("missing horizontal lines");
        if ((leftmost == null) || (ritemost == null)) throw new Exception ("missing vertical lines");

        if (verbose) {
            Console.WriteLine (" topmost=" + topmost.ToString ());
            Console.WriteLine (" botmost=" + botmost.ToString ());
            Console.WriteLine ("leftmost=" + leftmost.ToString ());
            Console.WriteLine ("ritemost=" + ritemost.ToString ());
        }

        /*
         * Make sure the lat/lon pixels are square.
         */
        {
            int topy = (topmost.begy + topmost.endy) / 2;
            int boty = (botmost.begy + botmost.endy) / 2;
            double pixperlat = (boty - topy) / (topmost.rounded - botmost.rounded);

            int leftx = (leftmost.begx + leftmost.endx) / 2;
            int ritex = (ritemost.begx + ritemost.endx) / 2;
            double pixperlon = (ritex - leftx) / (ritemost.rounded - leftmost.rounded);

            double ratio = pixperlat / pixperlon;

            if (verbose) Console.WriteLine ("pixelratio=" + ratio);

            if ((ratio < 0.99) || (ratio > 1.01)) {
                throw new Exception ("non-square pixel ratio " + ratio);
            }
        }

        /*
         * Get 3 x,y intersections on the rotated chart.
         */
        int x1, x2, x3, y1, y2, y3;
        Intersect (botmost, leftmost, out x1, out y1);
        Intersect (botmost, ritemost, out x2, out y2);
        Intersect (topmost, ritemost, out x3, out y3);

        /*
         * Transform to corresponding x,y on original chart.
         * These are the pixel addresses of the actual visual intersection points.
         */
        MapNewOldPix (x1, y1, out x1, out y1);
        MapNewOldPix (x2, y2, out x2, out y2);
        MapNewOldPix (x3, y3, out x3, out y3);

        /*
         * Get the slightly off lat/lon at each of those x,y points.
         */
        double lon1, lon2, lon3, lat1, lat2, lat3;
        chart.Pixel2LatLon (x1, y1, out lat1, out lon1);
        chart.Pixel2LatLon (x2, y2, out lat2, out lon2);
        chart.Pixel2LatLon (x3, y3, out lat3, out lon3);

        /*
         * Get the lat/lon that they really should be.
         */
        lat1 = Math.Round (lat1);
        lat2 = Math.Round (lat2);
        lat3 = Math.Round (lat3);
        lon1 = Math.Round (lon1);
        lon2 = Math.Round (lon2);
        lon3 = Math.Round (lon3);

        /*
         * Compute corresponding eastings/northings.
         */
        double e1, e2, e3, n1, n2, n3;
        chart.LatLon2Ings (lat1, lon1, out e1, out n1);
        chart.LatLon2Ings (lat2, lon2, out e2, out n2);
        chart.LatLon2Ings (lat3, lon3, out e3, out n3);

        /*
         * Recompute the TFW matrix:
         *
         *   easting  = tfw_a * x + tfw_c * y + tfw_e
         *   northing = tfw_b * x + tfw_d * y + tfw_f
         */
        double[,] tfwmat = new double[6,7];
        tfwmat[0,0] = x1; tfwmat[0,2] = y1; tfwmat[0,4] = 1.0; tfwmat[0,6] = e1;
        tfwmat[1,1] = x1; tfwmat[1,3] = y1; tfwmat[1,5] = 1.0; tfwmat[1,6] = n1;
        tfwmat[2,0] = x2; tfwmat[2,2] = y2; tfwmat[2,4] = 1.0; tfwmat[2,6] = e2;
        tfwmat[3,1] = x2; tfwmat[3,3] = y2; tfwmat[3,5] = 1.0; tfwmat[3,6] = n2;
        tfwmat[4,0] = x3; tfwmat[4,2] = y3; tfwmat[4,4] = 1.0; tfwmat[4,6] = e3;
        tfwmat[5,1] = x3; tfwmat[5,3] = y3; tfwmat[5,5] = 1.0; tfwmat[5,6] = n3;
        RowReduce (tfwmat);

        chart.tfw_a = tfwmat[0,6];
        chart.tfw_b = tfwmat[1,6];
        chart.tfw_c = tfwmat[2,6];
        chart.tfw_d = tfwmat[3,6];
        chart.tfw_e = tfwmat[4,6];
        chart.tfw_f = tfwmat[5,6];
    }

    public static void SaveMostTickMarks (LongLine ll, ref LongLine topmost, ref LongLine botmost)
    {
        if ((topmost == null) || (ll.ticks >= topmost.ticks)) {
            botmost = topmost;
            topmost = ll;
        } else if ((botmost == null) || (ll.ticks > botmost.ticks)){
            botmost = ll;
        }
    }

    /**
     * @brief Convert a pixel x,y in the rotated chart to the corresponding pixel x,y in the original chart.
     */
    public static void MapNewOldPix (int newx, int newy, out int oldx, out int oldy)
    {
        double lat = newy * (minlat - maxlat) / height + maxlat;
        double lon = newx * (maxlon - minlon) / width  + minlon;
        chart.LatLon2Pixel (lat, lon, out oldx, out oldy);
    }

    /**
     * @brief Find the intersection of two lines.
     */
    public static void Intersect (LongLine l1, LongLine l2, out int x, out int y)
    {
        int x1a = l1.begx;
        int x1b = l1.endx;
        int x2a = l2.begx;
        int x2b = l2.endx;
        int y1a = l1.begy;
        int y1b = l1.endy;
        int y2a = l2.begy;
        int y2b = l2.endy;

        double x1d = x1b - x1a;
        double x2d = x2b - x2a;
        double y1d = y1b - y1a;
        double y2d = y2b - y2a;

        // (y - y1a) / (x - x1a) = y1d / x1d
        // y = (x - x1a) * y1d / x1d + y1a

        // (x - x2a) * y2d / x2d + y2a = (x - x1a) * y1d / x1d + y1a
        // (x - x2a) * y2d * x1d + y2a * x2d * x1d = (x - x1a) * y1d * x2d + y1a * x2d * x1d
        // (x - x2a) * x1d * y2d - (x - x1a) * x2d * y1d = (y1a - y2a) * x2d * x1d
        // x * (x1d * y2d - x2d * y1d) = x2a * x1d * y2d - x1a * x2d * y1d + (y1a - y2a) * x2d * x1d

        x = (int) ((x2a * x1d * y2d - x1a * x2d * y1d + (y1a - y2a) * x2d * x1d) /
                   (x1d * y2d - x2d * y1d));
        y = (int) ((y2a * y1d * x2d - y1a * y2d * x1d + (x1a - x2a) * y2d * y1d) /
                   (y1d * x2d - y2d * x1d));
    }

    /**
     * @brief Row-reduce a matrix.
     * @param T[row,col] = matrix to reduce
     */
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

    /**
     * @brief Write current blacks array out to PNG file.
     *        Optionally include the horzLines and vertLines lists in red.
     */
    private static void SaveStage (string stagename, bool lines)
    {
        Console.WriteLine ("saving to " + stagename + " width=" + width + " height=" + height);

        Bitmap bmp = new Bitmap (width, height, PixelFormat.Format8bppIndexed);

        ColorPalette pal = bmp.Palette;
        pal.Entries[0] = Color.White;
        pal.Entries[1] = Color.Black;
        pal.Entries[2] = Color.Red;
        bmp.Palette = pal;

        Rectangle rec = new Rectangle (0, 0, width, height);
        BitmapData dat = bmp.LockBits (rec, ImageLockMode.ReadWrite, PixelFormat.Format8bppIndexed);
        IntPtr ptr = dat.Scan0;

        for (int y = 0; y < height; y ++) {
            unsafe {
                byte *p = (byte *)(void *)ptr;
                for (int x = 0; x < width; x ++) {
                    *(p ++) = blacks[y,x];
                }
            }
            ptr = (IntPtr) (((Int64)ptr) + dat.Stride);
        }

        if (lines) {
            SaveStagePixelSetter ps = new SaveStagePixelSetter ();
            ps.dat = dat;
            foreach (LongLine ll in horzLines) {
                DrawLine (ps, ll.begx, ll.begy, ll.endx, ll.endy);
            }
            foreach (LongLine ll in vertLines) {
                DrawLine (ps, ll.begx, ll.begy, ll.endx, ll.endy);
            }
        }

        bmp.UnlockBits (dat);

        Console.WriteLine ("...writing");
        bmp.Save (stagename, ImageFormat.Png);
        bmp.Dispose ();
        Console.WriteLine ("...saved");
    }

    /**
     * @brief Draw cross on image.
     */
    private static void DrawCross (ISetPixel spx, int x, int y, int size, int thick)
    {
        for (int ss = -size; ss <= size; ss ++) {
            for (int tt = -thick; tt <= thick; tt ++) {
                SetPixel (spx, x + ss, y + tt);
                SetPixel (spx, x + tt, y + ss);
            }
        }
    }

    /**
     * @brief Draw line on image.
     */
    public static void DrawLine (ISetPixel spx, int x1, int y1, int x2, int y2)
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
                SetPixel (spx, x, y);
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
                SetPixel (spx, x, y);
            }
        }
    }

    public static void SetPixel (ISetPixel spx, int x, int y)
    {
        if ((x >= 0) && (x < spx.Width) && (y >= 0) && (y < spx.Height)) {
            spx.SetPixel (x, y);
        }
    }
}

public interface ISetPixel {
    int Height { get; }
    int Width  { get; }
    void SetPixel (int x, int y);
}

public class SaveStagePixelSetter : ISetPixel {
    public BitmapData dat;

    public int Height { get { return dat.Height; } }
    public int Width  { get { return dat.Width;  } }

    public void SetPixel (int x, int y)
    {
        IntPtr ptr = dat.Scan0;
        ptr = (IntPtr) ((Int64)ptr + (Int64)dat.Stride * y + x);
        unsafe {
            byte *p = (byte *)(void *)ptr;
            *p = 2;
        }
    }
}

public class MarkedPngSetter : ISetPixel {
    public Bitmap bmp;

    public int Height { get { return bmp.Height; } }
    public int Width  { get { return bmp.Width;  } }

    public void SetPixel (int x, int y)
    {
        bmp.SetPixel (x, y, Color.Red);
    }
}
