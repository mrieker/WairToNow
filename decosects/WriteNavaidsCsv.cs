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
 * @brief Read the NAV.txt file from the FAA
 *        and extract navaid information and write to navaids.csv.
 *
 *  gmcs -debug -out:WriteNavaidsCsv.exe WriteNavaidsCsv.cs
 *  mono --debug WriteNavaidsCsv.exe < NAV.txt | sort > navaids.csv
 */


using System;
using System.IO;

// ref: https://nfdc.faa.gov/xwiki
// ref: https://nfdc.faa.gov/webContent/56DaySub/2015-06-25/Layout_Data/nav_rf.txt

public class WriteNavaidsCsv {

    public static string[] acceptables = new string[] { "DME", "NDB", "NDB/DME", "VOR", "VOR/DME", "VORTAC" };

    public static void Main ()
    {
        string line;
        while ((line = Console.ReadLine ()) != null) {
            if (!line.StartsWith ("NAV1")) continue;
            string type   = line.Substring (  8, 20).Trim ();
            string ident  = line.Substring ( 28,  4).Trim ();
            string name   = line.Substring ( 42, 30).Trim ();
            string city   = line.Substring ( 72, 40).Trim ();
            string state  = line.Substring (112, 30).Trim ();
            string cntry  = line.Substring (147, 30).Trim ();
            double lat    = DecodeLatLon (line.Substring (385, 11), 'N', 'S');
            double lon    = DecodeLatLon (line.Substring (410, 11), 'E', 'W');
            string elev   = line.Substring (472, 7).Trim ();
            string magvar = line.Substring (479, 5).Trim ();
            if (magvar != "") {
                int magbin = int.Parse (magvar.Substring (0, magvar.Length - 1));
                if (magvar.EndsWith ("E")) magbin = - magbin;
                magvar = magbin.ToString ();
            }
            string freq   = line.Substring (533, 6).Trim ();

            // see plate KMLK ILS or LOC RWY 2
            if ((type == "NDB") && (ident == "MKL")) ident = "MK";

            foreach (string acceptable in acceptables) {
                if (type == acceptable) {
                    if (state != "") city += ", " + state;
                    if (cntry != "") city += ", " + cntry;
                    Console.WriteLine (type + "," + ident + "," + elev + ",\"" + name + " - " + freq + " - " + city + "\"," + lat + "," + lon + "," + magvar + ",");
                }
            }
        }
    }

    private static double DecodeLatLon (string str, char posch, char negch)
    {
        int lm1 = str.Length - 1;
        double sec = double.Parse (str.Substring (0, lm1));
        if (str[lm1] == negch) sec = -sec;
        else if (str[lm1] != posch) throw new Exception ("bad latlon direction");
        return sec / 3600.0;
    }
}
