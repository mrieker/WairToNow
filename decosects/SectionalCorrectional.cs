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
 * @brief Determine sectional correction values.
 *        Scans chart for purple airports and correlates them to
 *        known co-ordinates.
 *        Incorrect vs Correct values though are pretty random,
 *        no visible pattern for generating correction values.
 */

// yum install libgdiplus
// yum install libgdiplus-devel

// gmcs -debug -out:SectionalCorrectional.exe -reference:System.Drawing.dll SectionalCorrectional.cs ChartTiff.cs

// mono --debug SectionalCorrectional.exe 'New York 86 North'

using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Text;

public class SectionalCorrectional {
    private static byte[,] blacks;
    private static ChartTiff chart;

    public static void Main (string[] args)
    {
        string basename = null;
        for (int i = 0; i < args.Length; i ++) {
            if (args[i][0] == '-') throw new Exception ("unknown option " + args[i]);
            if (basename != null) throw new Exception ("extra arg " + args[i]);
            basename = args[i];
        }
        if (basename == null) throw new Exception ("missing basename");

        chart = new ChartTiff ("charts/", basename);

        /*
         * Mark all transparent pixels as white.
         */
        for (int y = 0; y < chart.height; y ++) {
            for (int x = 0; x < chart.width; x ++) {
                Color c = chart.bmp.GetPixel (x, y);
                if (c.A < 200) {
                    chart.bmp.SetPixel (x, y, Color.White);
                }
            }
        }

        /*
         * Mark all pixels outside lat/lon limits for the chart
         * as white so we don't misidentify them.
         */
        int xmin = chart.width;
        int ymin = chart.height;
        int xmax = 0;
        int ymax = 0;
        for (int y = 0; y < chart.height; y ++) {
            for (int x = 0; x < chart.width; x ++) {
                double lat, lon;
                chart.Pixel2LatLon (x, y, out lat, out lon);
                if ((x == 0 || x == chart.width-1) && (y == 0 || y == chart.height-1)) {
                    Console.WriteLine ("(" + x + "," + y + ") => " + lat + "," + lon);
                }
                if ((lon < chart.westLonLimit) || (lon > chart.eastLonLimit) ||
                    (lat > chart.northLatLimit) || (lat < chart.southLatLimit)) {
                    chart.bmp.SetPixel (x, y, Color.White);
                } else {
                    if (xmin > x) xmin = x;
                    if (ymin > y) ymin = y;
                    if (xmax < x) xmax = x;
                    if (ymax < y) ymax = y;
                }
            }
        }
        Console.WriteLine ("xmin=" + xmin);
        Console.WriteLine ("ymin=" + ymin);
        Console.WriteLine ("xmax=" + xmax);
        Console.WriteLine ("ymax=" + ymax);

        /*
         * Convert magenta pixels to black and everything else to white
         * for easy processing.
         */
        Console.WriteLine ("monochromaticising");
        blacks = new byte[chart.height,chart.width];
        for (int y = ymin; y < ymax; y ++) {
            for (int x = xmin; x < xmax; x ++) {
                Color c = chart.bmp.GetPixel (x, y);
                int r = c.R;
                int g = c.G;
                int b = c.B;
                if ((r >= 100) && (r <= 130) && (g >= 70) && (g <= 90) && (b >= 100) && (b <= 130)) {
                    blacks[y,x] = 1;
                    chart.bmp.SetPixel (x, y, Color.Black);
                } else {
                    blacks[y,x] = 0;
                    chart.bmp.SetPixel (x, y, Color.White);
                }
            }
        }
        chart.bmp.Save ("stage1.png", System.Drawing.Imaging.ImageFormat.Png);

        /*
         * If a white pixel is surrounded by at least 7 blacks,
         * change the white to a black.  This will fill in a lot
         * of spotty airports and not much else.
         */
        Console.WriteLine ("filling in spots");
        for (int y = ymin + 1; y < ymax - 1; y ++) {
            for (int x = xmin + 1; x < xmax - 1; x ++) {
                if (blacks[y,x] == 0) {
                    if (blacks[y-1,x-1] + blacks[y-1,x] + blacks[y-1,x+1] +
                        blacks[y,  x-1] +                 blacks[y,  x+1] +
                        blacks[y+1,x-1] + blacks[y+1,x] + blacks[y+1,x+1] > 6) {
                        blacks[y,x] = 1;
                        chart.bmp.SetPixel (x, y, Color.Black);
                    }
                }
            }
        }
        chart.bmp.Save ("stage2.png", System.Drawing.Imaging.ImageFormat.Png);

        /*
         * Draw box around where each airport should be on the chart.
         */
        Console.WriteLine ("finding airports");
        string[] csvnames = Directory.GetFiles ("datums", "airports_*.csv");
        string bestcsvname = null;
        foreach (string csvname in csvnames) {
            if ((bestcsvname == null) || StrLt (bestcsvname, csvname)) {
                bestcsvname = csvname;
            }
        }
        StreamReader csvreader = new StreamReader (bestcsvname);
        string csvline;
        while ((csvline = csvreader.ReadLine ()) != null) {

            /*
             * Compute where the airport should be on the map
             * trusting the gubmint's numbers.
             */
            string[] csvtokens = ChartTiff.SplitQuotedCSVLine (csvline);
            string aptid = csvtokens[1];
            double csvlat = double.Parse (csvtokens[4]);
            double csvlon = double.Parse (csvtokens[5]);
            int csvpixx, csvpixy;
            chart.LatLon2Pixel (csvlat, csvlon, out csvpixx, out csvpixy);

            /*
             * Skip airport if not on graphics part of chart.
             */
            if ((csvpixx < xmin) || (csvpixx >= xmax)) continue;
            if ((csvpixy < ymin) || (csvpixy >= ymax)) continue;
            Console.Write (aptid.PadLeft (5));

            /*
             * Search the surrounding area for a tight fitting circle around
             * black pixels.
             */
            double bestRatio = 0;
            int bestRadius   = 0;
            int bestCenterX  = 0;
            int bestCenterY  = 0;
            for (int dy = -20; dy <= 20; dy ++) {
                for (int dx = -20; dx <= 20; dx ++) {
                    for (int rad = 14; rad <= 22; rad ++) {
                        int loestx = csvpixx + dx - rad - 2;
                        int loesty = csvpixy + dy - rad - 2;
                        int hiestx = csvpixx + dx + rad + 2;
                        int hiesty = csvpixy + dy + rad + 2;
                        if ((loestx < xmin) || (hiestx >= xmax)) continue;
                        if ((loesty < ymin) || (hiesty >= ymax)) continue;

                        int nbi = 0;  // number blacks inside radius
                        int nbo = 0;  // number blacks outside radius
                        int num = 0;  // number of angles checked
                        for (double ang = 0.0; ang < Math.PI * 2.0; ang += 0.5 / rad) {
                            double cos = Math.Cos (ang);
                            double sin = Math.Sin (ang);
                            int xxi = (int)((rad + 0) * cos + 0.5);
                            int yyi = (int)((rad + 0) * sin + 0.5);
                            int xxo = (int)((rad + 1) * cos + 0.5);
                            int yyo = (int)((rad + 1) * sin + 0.5);
                            if (Math.Abs (xxi - xxo) + Math.Abs (yyi - yyo) == 2) {
                                nbi += blacks[csvpixy+dy+yyi,csvpixx+dx+xxi];
                                nbo += blacks[csvpixy+dy+yyo,csvpixx+dx+xxo];
                                num ++;
                            }
                        }
                        if ((num >= 8) && (nbo > 0) && (nbi * 2 > nbo * 6)) {
                            double ratio = (double)nbi / (double)nbo;
                            if (bestRatio < ratio) {
                                bestRatio = ratio;
                                bestRadius = rad;
                                bestCenterX = csvpixx + dx;
                                bestCenterY = csvpixy + dy;
                            }
                        }
                    }
                }
            }
            if (bestRadius > 0) {
                DrawCircle (bestCenterX, bestCenterY, bestRadius, Color.Red);
                DrawCircle (csvpixx, csvpixy, bestRadius, Color.Magenta);
            } else {
                DrawCircle (csvpixx, csvpixy, 30, Color.Cyan);
            }
        }
        Console.WriteLine ("");
        csvreader.Close ();
        chart.bmp.Save ("stage3.png", System.Drawing.Imaging.ImageFormat.Png);
    }

    private static void DrawLine (int x1, int y1, int x2, int y2, Color c)
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
                SetBigPixel (x, y, c);
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
                SetBigPixel (x, y, c);
            }
        }
    }

    private static void DrawCircle (int x, int y, int rad, Color c)
    {
        for (double ang = 0.0; ang < Math.PI * 2.0; ang += 0.5 / rad) {
            double cos = Math.Cos (ang);
            double sin = Math.Sin (ang);
            int xxi = (int)(rad * cos + 0.5);
            int yyi = (int)(rad * sin + 0.5);
            SetBigPixel (x + xxi, y + yyi, c);
        }
    }

    private static void SetBigPixel (int x, int y, Color c)
    {
        if ((x > 0) && (x < chart.width - 1) && (y > 0) && (y < chart.height - 1)) {
            chart.bmp.SetPixel (x-1, y, c);
            chart.bmp.SetPixel (x+1, y, c);
            chart.bmp.SetPixel (x, y, c);
            chart.bmp.SetPixel (x, y-1, c);
            chart.bmp.SetPixel (x, y+1, c);
        }
    }

    private static long Sq (int n)
    {
        return (long)n * (long)n;
    }

    private static bool StrLt (string s, string t)
    {
        int slen = s.Length;
        int tlen = t.Length;
        for (int i = 0; (i < slen) && (i < tlen); i ++) {
            char c = s[i];
            char d = t[i];
            if (c < d) return true;
            if (c > d) return false;
        }
        return slen < tlen;
    }

    private static void RowReduce (double[,] T)
    {
        int trows = T.GetLength (0);
        int tcols = T.GetLength (1);

        double pivot;
        for (int row = 0; row < trows; row ++) {

            /*
             * Make this row's major diagonal colum one by
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
}
