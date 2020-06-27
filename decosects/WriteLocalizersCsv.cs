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
 * @brief Read the ILS.txt file from the FAA
 *        and extract localizer information and write to localizers.csv.
 *
 *  mcs -debug -out:WriteLocalizersCsv.exe WriteLocalizersCsv.cs
 *  mono --debug WriteLocalizersCsv.exe < ILS.txt | sort > localizers.csv
 */


using System;
using System.IO;

// ref: https://nfdc.faa.gov/xwiki
// ref: https://nfdc.faa.gov/webContent/56DaySub/2015-06-25/Layout_Data/ils_rf.txt

public class WriteNavaidsCsv {

    public static void Main ()
    {
        string type   = "";
        string ident  = "";
        string name   = "";
        string elev   = "";
        string freq   = "";
        string city   = "";
        double lat    = 0.0;
        double lon    = 0.0;
        double thdg   = 0.0;
        string gsalt  = "";      // gs antenna elevation feet msl
        string gsang  = "";      // angle in degrees, eg, 2.75
        double gslat  = 0.0;     // glideslope antenna location
        double gslon  = 0.0;
        string dmealt = "";
        double dmelat = 0.0;
        double dmelon = 0.0;
        string aptid  = "";      // faaid eg "BVY"
        string rwyid  = "";      // eg "04R"
        int    magvar = 0;       // mhdg = thdg + magvar

        string line;
        while ((line = Console.ReadLine ()) != null) {
            if (line.StartsWith ("ILS1")) {
                if (type != "") {
                    // elev gets filled in with airport elev by MakeWaypoints.cs
                    Console.WriteLine (type + "," + ident + "," + elev + ",\"" + name +
                            " - " + freq + " - " + city + "\"," + lat + "," + lon + "," +
                            thdg + "," + gsalt + "," + gsang + "," + gslat + "," +
                            gslon + "," + dmealt + "," + dmelat + "," + dmelon + "," +
                            aptid + "," + rwyid + "," +
                            Math.Round (double.Parse (freq) * 1000.0) + "," + magvar);
                    type   = "";
                    ident  = "";
                    name   = "";
                    elev   = "";
                    freq   = "";
                    city   = "";
                    lat    = 0.0;
                    lon    = 0.0;
                    thdg   = 0.0;
                    gsalt  = "";
                    gsang  = "";
                    gslat  = 0.0;
                    gslon  = 0.0;
                    dmealt = "";
                    dmelat = 0.0;
                    dmelon = 0.0;
                    aptid  = "";
                    rwyid  = "";
                    magvar = 0;
                }
                type   = line.Substring (18, 10).Trim ();
                ident  = line.Substring (28,  6).Trim ();
                rwyid  = line.Substring (15,  3).Trim ();
                name   = line.Substring (44, 50).Trim () + " rwy " + rwyid;  // airport name and runway number
                city   = line.Substring (94, 40).Trim () + ", " + line.Substring (136, 20).Trim ();   // city and state
                thdg   = Double.Parse (line.Substring (281, 6).Trim ()) - ParseVariation (line.Substring (287, 3).Trim ());
                aptid  = line.Substring (159, 3).Trim ();
            }
            if (line.StartsWith ("ILS2")) {
                lat    = DecodeLatLon (line.Substring (74, 11).Trim (), 'N', 'S');
                lon    = DecodeLatLon (line.Substring (99, 11).Trim (), 'E', 'W');
                elev   = line.Substring (126, 7).Trim ();
                freq   = line.Substring (133, 7).Trim ();
            }
            if (line.StartsWith ("ILS3")) {
                gsalt  = line.Substring (126, 7).Trim ();
                gsang  = line.Substring (148, 5).Trim ();
                gslat  = DecodeLatLon (line.Substring (74, 11).Trim (), 'N', 'S');
                gslon  = DecodeLatLon (line.Substring (99, 11).Trim (), 'E', 'W');
            }
            if (line.StartsWith ("ILS4")) {
                dmealt = line.Substring (126, 7).Trim ();
                dmelat = DecodeLatLon (line.Substring (74, 11).Trim (), 'N', 'S');
                dmelon = DecodeLatLon (line.Substring (99, 11).Trim (), 'E', 'W');
            }

            if (line.StartsWith ("ILS5")) {
                string markertype = line.Substring ( 28,  2).Trim ();   // IM, MM, OM
                double markerlat  = DecodeLatLon (line.Substring ( 76, 11).Trim (), 'N', 'S');
                double markerlon  = DecodeLatLon (line.Substring (101, 11).Trim (), 'E', 'W');
                string markerelev = line.Substring (128,  7).Trim ();   // elevation of marker in feet
                string markeridnt = line.Substring (150,  2).Trim ();   // short identifier (2 letters)
                string markername = line.Substring (152, 30).Trim ();   // long name
                string markerfreq = line.Substring (182,  3).Trim ();   // marker beacon frequency

                if (markerfreq == "75") markerfreq = "75000";

                if ((markeridnt != "") && (markerfreq != "")) {
                    if (markerelev == "") markerelev = Topography.GetElevFt (markerlat, markerlon).ToString ("F1");
                    Console.WriteLine (markertype + "," + markeridnt + "," + markerelev + ",\"" + markername + " - " + markerfreq + " - " + city + "\"," + markerlat + "," + markerlon + ",,,,,,,,,,," + markerfreq + ",");
                }
            }
        }
        if (type != "") {
            Console.WriteLine (type + "," + ident + "," + elev + ",\"" + name +
                    " - " + freq + " - " + city + "\"," + lat + "," + lon + "," +
                    thdg + "," + gsalt + "," + gsang + "," + gslat + "," +
                    gslon + "," + dmealt + "," + dmelat + "," + dmelon + "," +
                    aptid + "," + rwyid + "," +
                    Math.Round (double.Parse (freq) * 1000.0) + "," + magvar);
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

    private static int ParseVariation (string str)
    {
        int lm1 = str.Length - 1;
        int v = int.Parse (str.Substring (0, lm1));
        if (str[lm1] == 'E') return -v;
        if (str[lm1] == 'W') return v;
        throw new Exception ("bad variation direction");
    }
}
