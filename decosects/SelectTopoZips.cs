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
 * @brief Scan all chart .csv files to determine what latitudes they reference.
 *        Output integer latitudes referenced by the charts.
 */

// yum install libgdiplus
// yum install libgdiplus-devel

// mcs -debug -out:SelectTopoZips.exe -reference:System.Drawing.dll SelectTopoZips.cs ChartTiff.cs

// mono --debug SelectTopoZips.exe

using System;
using System.Collections.Generic;
using System.IO;
using System.Text;

public class SelectTopoZips {

    public static void Main (string[] args)
    {
        SortedDictionary<int,bool> referenced = new SortedDictionary<int,bool> ();

        string[] filenames = Directory.GetFiles ("charts");
        for (int i = 0; i < filenames.Length; i ++) {
            string csvname = filenames[i];
            if (!csvname.EndsWith (".csv")) continue;
            csvname = csvname.Substring (0, csvname.Length - 4);
            int j = csvname.LastIndexOf ('/') + 1;
            String spacename = csvname.Substring (j).Replace ('_', ' ');

            ChartTiff chart = new ChartTiff (csvname.Substring (0, j), spacename, false);

            double nlat = -90;
            double slat =  90;
            double lat, lon;

            chart.Pixel2LatLon (0, 0, out lat, out lon);
            if (nlat < lat) nlat = lat;
            if (slat > lat) slat = lat;

            chart.Pixel2LatLon (0, chart.height, out lat, out lon);
            if (nlat < lat) nlat = lat;
            if (slat > lat) slat = lat;

            chart.Pixel2LatLon (chart.width, 0, out lat, out lon);
            if (nlat < lat) nlat = lat;
            if (slat > lat) slat = lat;

            chart.Pixel2LatLon (chart.width, chart.height, out lat, out lon);
            if (nlat < lat) nlat = lat;
            if (slat > lat) slat = lat;

            for (int ilat = (int) Math.Floor (slat); ilat < (int) Math.Ceiling (nlat); ilat ++) {
                referenced[ilat] = true;
            }
        }
        for (int ilat = -90; ilat < 90; ilat ++) {
            if (referenced.ContainsKey (ilat)) {
                Console.WriteLine ("ln datums/topo/" + ilat + ".zip.save datums/topo/" + ilat + ".zip");
            } else {
                Console.WriteLine ("touch datums/topo/" + ilat + ".zip");
            }
        }
    }
}
