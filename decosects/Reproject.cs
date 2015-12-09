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
 * @brief Takes a Lambert chart and flattens it to a Box projection,
 *        ie, each pixel is the same lat/lon size, so the lat/lon
 *        lines end up being drawn as rectangular boxes.
 *        Mercator except lines of latitude are equally spaced.
 *        Also creates .csv file suitable for AirChart.Box class.
 *        Resultant PNG file must be split into tiles with ReadImageFile.exe.
 */

// yum install libgdiplus
// yum install libgdiplus-devel

// gmcs -debug -out:Reproject.exe -reference:System.Drawing.dll Reproject.cs ChartTiff.cs
// mono --debug Reproject.exe 'New York SEC 92' ny.png
//  - creates ny.png and ny.png.csv

using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Text;

public class Reproject {
    public static void Main (string[] args)
    {
        string basename = args[0];
        string savename = args[1];

        ChartTiff tiffchart = new ChartTiff ("charts/", basename);
        int tw = tiffchart.width;
        int th = tiffchart.height;

        double upperLeftLat, upperLeftLon;
        tiffchart.Pixel2LatLon (0,  0,  out upperLeftLat, out upperLeftLon);

        double upperRiteLat, upperRiteLon;
        tiffchart.Pixel2LatLon (tw, 0,  out upperRiteLat, out upperRiteLon);

        double lowerLeftLat, lowerLeftLon;
        tiffchart.Pixel2LatLon (0,  th, out lowerLeftLat, out lowerLeftLon);

        double lowerRiteLat, lowerRiteLon;
        tiffchart.Pixel2LatLon (tw, th, out lowerRiteLat, out lowerRiteLon);

        double loestLat = Math.Min (Math.Min (upperLeftLat, upperRiteLat), Math.Min (lowerLeftLat, lowerRiteLat));
        double hiestLat = Math.Max (Math.Max (upperLeftLat, upperRiteLat), Math.Max (lowerLeftLat, lowerRiteLat));
        double loestLon = Math.Min (Math.Min (upperLeftLon, upperRiteLon), Math.Min (lowerLeftLon, lowerRiteLon));
        double hiestLon = Math.Max (Math.Max (upperLeftLon, upperRiteLon), Math.Max (lowerLeftLon, lowerRiteLon));

        double avgLat = (loestLat + hiestLat) / 2.0;
        double avgLon = (loestLon + hiestLon) / 2.0;
        int oneNorthX, oneNorthY, oneSouthX, oneSouthY;
        tiffchart.LatLon2Pixel (avgLat + 0.5, avgLon, out oneNorthX, out oneNorthY);
        tiffchart.LatLon2Pixel (avgLat - 0.5, avgLon, out oneSouthX, out oneSouthY);

        double pixperdeglat = Hypot (oneNorthX - oneSouthX, oneNorthY - oneSouthY);
        double pixperdeglon = pixperdeglat * Math.Cos (avgLat * Math.PI / 180.0);

        int pngwidth  = (int) Math.Round ((hiestLon - loestLon) * pixperdeglon);
        int pngheight = (int) Math.Round ((hiestLat - loestLat) * pixperdeglat);
        Console.WriteLine ("pngwidth,height=" + pngwidth + "," + pngheight);

        Bitmap pngbm = new Bitmap (pngwidth, pngheight);

        for (int pngy = 0; pngy < pngheight; pngy ++) {
            if (pngy % 100 == 0) Console.Write ("\rpngy=" + pngy);
            for (int pngx = 0; pngx < pngwidth; pngx ++) {
                double lat = (double) pngy / pngheight * (loestLat - hiestLat) + hiestLat;
                double lon = (double) pngx / pngwidth  * (hiestLon - loestLon) + loestLon;
                int tiffx, tiffy;
                tiffchart.LatLon2Pixel (lat, lon, out tiffx, out tiffy);
                if ((tiffx >= 0) && (tiffx < tw) && (tiffy >= 0) && (tiffy < th)) {
                    Color pixel = tiffchart.bmp.GetPixel (tiffx, tiffy);
                    pngbm.SetPixel (pngx, pngy, pixel);
                }
            }
        }
        Console.WriteLine ("");

        pngbm.Save (savename, System.Drawing.Imaging.ImageFormat.Png);

        string csvline = "box:" + hiestLat + "," + loestLon + "," + loestLat + "," + hiestLon + "," +
                pngwidth + "," + pngheight + "," + tiffchart.enddate + "," + tiffchart.spacename;
        File.WriteAllText (savename + ".csv", csvline + "\n");
    }

    public static double Hypot (double dx, double dy)
    {
        return Math.Sqrt (dx * dx + dy * dy);
    }
}
