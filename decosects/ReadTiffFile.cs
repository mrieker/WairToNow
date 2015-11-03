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
 * @brief Splits a sectional chart .tif file
 *        into many small .png files
 */

// yum install libgdiplus
// yum install libgdiplus-devel

// gmcs -debug -out:ReadTiffFile.exe -reference:System.Drawing.dll ReadTiffFile.cs ChartTiff.cs

// mono --debug ReadTiffFile.exe 'New York 86 North' [-nosplit] [-markers] [-mingrid <color>]
// adb push New_York_86_North /sdcard/New_York_86_North

// Download zip files from http://aeronav.faa.gov/index.asp?xml=aeronav/applications/VFR/chartlist_sect
// Unzip and then process the tiff files with this program

using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Text;

public class ReadTiffFile {
    public const int hstep = 256;
    public const int wstep = 256;
    public const int overlap = 10;  // big enough for most zoomed-out scaling

    private static ChartTiff chart;
    private static Color mingrid;

    public static void Main (string[] args)
    {
        bool markers = false;
        bool nosplit = false;
        string basename = null;
        mingrid = Color.Transparent;
        for (int i = 0; i < args.Length; i ++) {
            if (args[i] == "-markers") { markers = true; continue; }
            if (args[i] == "-mingrid") { mingrid = NameToColor (args[++i]); continue; }
            if (args[i] == "-nosplit") { nosplit = true; continue; }
            if (args[i][0] == '-') throw new Exception ("unknown option " + args[i]);
            if (basename != null) throw new Exception ("extra arg " + args[i]);
            basename = args[i];
        }
        if (basename == null) throw new Exception ("missing basename");

        chart = new ChartTiff ("", basename);

        /*
         * Convert all black pixels to transparent.
         * The sectionals aren't always scanned exactly square,
         * and there is a black margin around the sides.  So to
         * make the edges match up nicely in the app, mark them
         * all transparent.  It shouldn't matter if any interior
         * black pixels are marked transparent, as the default
         * for the canvas in the app is black.
         */
        chart.bmp.MakeTransparent (Color.Black);

        /*
         * Draw lines every minute for debugging.
         */
        if (mingrid.A != 0) {
            double hilatdeg, hilondeg, lat, lolatdeg, lolondeg, lon;
            int lastx, lasty, latmin, lonmin, thisx, thisy;

            chart.Pixel2LatLon (chart.width,  0, out hilatdeg, out hilondeg);
            chart.Pixel2LatLon (0, chart.height, out lolatdeg, out lolondeg);
            int hilatmin = (int)(hilatdeg * 60.0) + 30;
            int hilonmin = (int)(hilondeg * 60.0) + 30;
            int lolatmin = (int)(lolatdeg * 60.0) - 30;
            int lolonmin = (int)(lolondeg * 60.0) - 30;

            for (latmin = lolatmin; latmin < hilatmin; latmin ++) {
                lonmin = lolonmin;
                lat    = (double)latmin / 60.0;
                lon    = (double)lonmin / 60.0;
                chart.LatLon2Pixel (lat, lon, out lastx, out lasty);

                while (++ lonmin < hilonmin) {
                    lon   = (double)lonmin / 60.0;
                    chart.LatLon2Pixel (lat, lon, out thisx, out thisy);
                    DrawLine (lastx, lasty, thisx, thisy, MinGridSetPixel);
                    lastx = thisx;
                    lasty = thisy;
                }
            }

            for (lonmin = lolonmin; lonmin < hilonmin; lonmin ++) {
                latmin = lolatmin;
                lat    = (double)latmin / 60.0;
                lon    = (double)lonmin / 60.0;
                chart.LatLon2Pixel (lat, lon, out lastx, out lasty);

                while (++ latmin < hilatmin) {
                    lat   = (double)latmin / 60.0;
                    chart.LatLon2Pixel (lat, lon, out thisx, out thisy);
                    DrawLine (lastx, lasty, thisx, thisy, MinGridSetPixel);
                    lastx = thisx;
                    lasty = thisy;
                }
            }
        }

        /*
         * Split it up into wstep/hstep-sized .png files.
         */
        if (!nosplit) {
            string dirname = basename.Replace (' ', '_');
            Directory.CreateDirectory (dirname);
            for (int hhh = 0; hhh < chart.height; hhh += hstep) {
                int thish = chart.height - hhh;
                if (thish > hstep + overlap) thish = hstep + overlap;
                string subdirname = dirname + "/" + (hhh / hstep).ToString ();
                Directory.CreateDirectory (subdirname);
                for (int www = 0; www < chart.width; www += wstep) {
                    int thisw = chart.width  - www;
                    if (thisw > wstep + overlap) thisw = wstep + overlap;
                    string savename = subdirname + "/" + (www / wstep).ToString () + ".png";
                    Bitmap thisbm = new Bitmap (thisw, thish);
                    for (int h = 0; h < thish; h ++) {
                        for (int w = 0; w < thisw; w ++) {
                            thisbm.SetPixel (w, h, chart.bmp.GetPixel (www + w, hhh + h));
                        }
                    }
                    thisbm.Save (savename, System.Drawing.Imaging.ImageFormat.Png);
                }
            }
        }

        if (markers) {

            /*
             * Create whole .png file containing lat/lon markers for visual inspection.
             */
            Console.WriteLine (basename + ".png");

            /*
             * Draw a vertical line through centerLon, ie, easting = 0
             */
            for (int y = 0; y < chart.height; y ++) {
                double easting = 0;
                int x = (int)((easting - chart.tfw_e - chart.tfw_c * y) / chart.tfw_a);
                for (int d = -2; d <= 2; d ++) {
                    SetPixel (x + d, y, Color.Yellow);
                }
            }

            /*
             * Draw an horizontal line through centerLat, ie, northing = 0
             */
            for (int x = 0; x < chart.width; x ++) {
                double northing = 0;
                int y = (int)((northing - chart.tfw_f - chart.tfw_b * x) / chart.tfw_d);
                for (int d = -2; d <= 2; d ++) {
                    SetPixel (x, y + d, Color.Yellow);
                }
            }

            /*
             * LatLon -> Pixel conversion testing.
             */
            for (int lat = -90; lat < 90; lat ++) {
                for (int lon = -180; lon < 180; lon ++) {
                    int x, y;
                    chart.LatLon2Pixel (lat, lon, out x, out y);

                    /*
                     * Draw a red plus sign at that location.
                     */
                    for (int d = -25; d <= 25; d ++) {
                        for (int e = -2; e <= 2; e ++) {
                            SetPixel (x + e, y + d, Color.Red);
                            SetPixel (x + d, y + e, Color.Red);
                        }
                    }
                }
            }

            chart.bmp.Save (basename + ".png", System.Drawing.Imaging.ImageFormat.Png);
        }
    }

    private static Color NameToColor (string name)
    {
        try {
            uint n = uint.Parse (name, System.Globalization.NumberStyles.HexNumber);
            return Color.FromArgb ((int)(n >> 16), (int)(n >> 8), (int)n);
        } catch {
            Color c = Color.FromName (name);
            if ((c.A | c.R | c.G | c.B) == 0) {
                throw new Exception ("unknown color " + name);
            }
            return c;
        }
    }

    /**
     * @brief Draw a line to the bitmap, clipping as necessary.
     */
    private delegate void DrawLineSP (int x, int y);
    private static void DrawLine (int x1, int y1, int x2, int y2, DrawLineSP sp)
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
                y = (int)((float)(y2 - y1) / (float)(x2 - x1) * (float)(x - x1) + 0.5) + y1;
                sp (x, y);
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
                x = (int)((float)(x2 - x1) / (float)(y2 - y1) * (float)(y - y1) + 0.5) + x1;
                sp (x, y);
            }
        }
    }

    private static void MinGridSetPixel (int x, int y)
    {
        if ((x >= 0) && (x < chart.width) && (y >= 0) && (y < chart.height)) {
            Color c = chart.bmp.GetPixel (x, y);
            if ((c.A > 250) && ((c.R | c.G | c.B) > 0)) {
                SetPixel (x, y, mingrid);
            }
        }
    }

    /**
     * @brief Set pixel to a given color, clipping if necessary.
     */
    private static void SetPixel (int x, int y, Color c)
    {
        if (x < 0) return;
        if (x >= chart.width) return;
        if (y < 0) return;
        if (y >= chart.height) return;
        chart.bmp.SetPixel (x, y, c);
    }
}
