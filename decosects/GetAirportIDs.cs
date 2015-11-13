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
 * @brief Extracts airport identifiers (FAA format, not ICAO)
 *        Also writes airports.csv and runways.csv summary files
 *
 * gmcs -debug -out:GetAirportIDs.exe GetAirportIDs.cs
 * cat APT.txt TWR.txt | mono --debug GetAirportIDs.exe airports.csv runways.csv > ...
 */

// https://nfdc.faa.gov/xwiki -> automatic redirect
// https://nfdc.faa.gov/xwiki/bin/view/NFDC/WebHome -> 56 Day NASR Subscription
// https://nfdc.faa.gov/xwiki/bin/view/NFDC/56+Day+NASR+Subscription -> Current
// https://nfdc.faa.gov/xwiki/bin/view/NFDC/56DaySub-2015-06-25   (2015-06-25 = cycle start date)
// https://nfdc.faa.gov/webContent/56DaySub/2015-06-25/APT.zip
// https://nfdc.faa.gov/webContent/56DaySub/2015-06-25/TWR.zip

using System;
using System.Collections.Generic;
using System.IO;
using System.Text;

public class GetAirportIDs {
    public class Airport {
        public string keyid;    // eg, "08762.*A" for BVY
        public string aptlat;
        public string aptlon;
        public string aptname;
        public string elevatn;
        public string faaid;
        public string icaoid;
        public string info;
        public string state;
        public string variatn;
    }

    public static Dictionary<String,Airport> aptsbykeyid = new Dictionary<String,Airport> ();
    public static Dictionary<String,Airport> aptsbyfaaid = new Dictionary<String,Airport> ();

    public static void Main (string[] args)
    {
        StreamWriter airports = new StreamWriter (args[0]);
        StreamWriter runways  = new StreamWriter (args[1]);

        string line;
        while ((line = Console.ReadLine ()) != null) {

            // write airport record
            if (line.StartsWith ("APT")) {
                string keyid = line.Substring (3, 11).Trim ();

                Airport apt;
                if (!aptsbykeyid.TryGetValue (keyid, out apt)) {
                    aptsbykeyid[keyid] = apt = new Airport ();
                }
                apt.faaid  = line.Substring (27, 4).Trim ();
                apt.icaoid = line.Substring (1210, 4).Trim ();
                if (apt.icaoid == "") apt.icaoid = apt.faaid;

                aptsbyfaaid[apt.faaid] = apt;

                Console.WriteLine (apt.faaid);

                apt.state   = line.Substring ( 48, 2).Trim ();
                apt.aptname = line.Substring (133, 50).Trim ();
                apt.aptlat  = ParseLat (line.Substring (538, 12));
                apt.aptlon  = ParseLon (line.Substring (565, 12));
                apt.variatn = ParseVar (line.Substring (586, 3));   // bvy is '16W'
                apt.elevatn = line.Substring (578, 7).Trim ();

                string distfromcity = line.Substring (627, 2).Trim ();
                while ((distfromcity.Length > 1) && (distfromcity[0] == '0')) distfromcity = distfromcity.Substring (1);

                apt.info = distfromcity + " " + line.Substring (629, 3).Trim () + " of " +
                        line.Substring (93, 40).Trim () + ", " + apt.state;
                if (apt.elevatn != "") apt.info += "\nElev: " + apt.elevatn + " ft.";

                string unicom = TrimTrailingZeroes (line.Substring (981, 7).Trim ());
                if (unicom != "") apt.info += "\nUNICOM: " + unicom;

                string ctaf = TrimTrailingZeroes (line.Substring (988, 7).Trim ());
                if (ctaf != "") apt.info += "\nCTAF: " + ctaf;
            }

            // write runway records
            if (line.StartsWith ("RWY")) {
                string keyid = line.Substring (3, 11).Trim ();      // key id
                Airport apt  = aptsbykeyid[keyid];

                string numa = line.Substring (65, 3).Trim ();       // runway number
                string alna = line.Substring (69, 3).Trim ();       // true alignment (degrees)
                string lata = ParseLat (line.Substring (103, 12));
                string lona = ParseLon (line.Substring (130, 12));
                string elva = line.Substring (142, 7).Trim ();      // elevation (feet)

                string numb = line.Substring (287, 3).Trim ();      // runway number
                string alnb = line.Substring (290, 3).Trim ();      // true alignment (degrees)
                string latb = ParseLat (line.Substring (325, 12));
                string lonb = ParseLon (line.Substring (352, 12));
                string elvb = line.Substring (364, 7).Trim ();      // elevation (feet)

                runways.WriteLine (apt.faaid + "," + numa + "," + alna + "," + elva + "," + lata + "," + lona + "," + latb + "," + lonb);
                runways.WriteLine (apt.faaid + "," + numb + "," + alnb + "," + elvb + "," + latb + "," + lonb + "," + lata + "," + lona);

                string surface = line.Substring (32, 12).Trim ();
                surface = surface.Replace ("-E", " EXCELLENT");
                surface = surface.Replace ("-G", " GOOD");
                surface = surface.Replace ("-F", " FAIR");
                surface = surface.Replace ("-P", " POOR");
                surface = surface.Replace ("-L", " FAILED");

                apt.info += "\nRwy " + line.Substring (16, 7).Trim () + ": " +  // numbers numa/numb
                            line.Substring (23, 5).Trim () + " ft. x " +        // length
                            line.Substring (28, 4).Trim () + " ft. " +          // width
                            surface;                                            // surface type and condition
            }

            // tower-related frequencies (tower, ground, atis)
            if (line.StartsWith ("TWR3")) {
                string faaid = line.Substring (4, 4).Trim ();
                Airport apt;
                if (aptsbyfaaid.TryGetValue (faaid, out apt)) {
                    for (int i = 0; i < 9; i ++) {
                        string freq = line.Substring (i * 94 +  8, 44).Trim ();
                        string purp = line.Substring (i * 94 + 52, 50).Trim ();
                        purp = purp.Replace ("LCL", "TOWER");
                        if ((freq != "") && (purp != "")) apt.info += "\n" + purp + ": " + freq;
                    }
                }
            }

            // approach control frequencies
            if (line.StartsWith ("TWR7")) {
                string faaid = line.Substring (113, 4).Trim ();
                Airport apt;
                if (aptsbyfaaid.TryGetValue (faaid, out apt)) {
                    string freq  = line.Substring (8, 44).Trim ();
                    string purp  = line.Substring (52, 50).Trim ();
                    if ((freq != "") && (purp != "")) apt.info += "\n" + purp + ": " + freq;
                }
            }
        }

        foreach (Airport apt in aptsbyfaaid.Values) {
            airports.WriteLine (apt.icaoid + "," + apt.faaid + "," + apt.elevatn + "," +
                    QuotedString (apt.aptname) + "," + apt.aptlat + "," + apt.aptlon + "," +
                    apt.variatn + "," + QuotedString (apt.info) + "," + apt.state);
        }

        airports.Close ();
        runways.Close ();
    }

    public static string ParseLat (string str)
    {
        return ParseLatLon (str, "N", "S");
    }

    public static string ParseLon (string str)
    {
        return ParseLatLon (str, "E", "W");
    }

    public static String ParseLatLon (String str, String poschr, String negchr)
    {
        str = str.Trim ();
        int len = str.Length;
        if (len > 0) {
            if (str.EndsWith (poschr)) {
                return (double.Parse (str.Substring (0, len - 1)) /  3600.0).ToString ();
            }
            if (str.EndsWith (negchr)) {
                return (double.Parse (str.Substring (0, len - 1)) / -3600.0).ToString ();
            }
        }
        return str;
    }

    // bvy is '16W'
    public static string ParseVar (string str)
    {
        str = str.Trim ();
        int len = str.Length;
        if (str.EndsWith ("W")) return       int.Parse (str.Substring (0, len - 1)).ToString ();
        if (str.EndsWith ("E")) return "-" + int.Parse (str.Substring (0, len - 1)).ToString ();
        return str;
    }

    public static string TrimTrailingZeroes (string str)
    {
        while (str.EndsWith ("0") && !str.EndsWith (".0") && (str.Length > 1)) {
            str = str.Substring (0, str.Length - 1);
        }
        return str;
    }

    public static String QuotedString (String unquoted)
    {
        int len = unquoted.Length;
        StringBuilder sb = new StringBuilder (len + 2);
        sb.Append ('"');
        for (int i = 0; i < len; i ++) {
            char c = unquoted[i];
            switch (c) {
                case '\\': {
                    sb.Append ("\\\\");
                    break;
                }
                case '\n': {
                    sb.Append ("\\n");
                    break;
                }
                case (char) 0: {
                    sb.Append ("\\z");
                    break;
                }
                case '"': {
                    sb.Append ("\\\"");
                    break;
                }
                default: {
                    sb.Append (c);
                    break;
                }
            }
        }
        sb.Append ('"');
        return sb.ToString ();
    }
}
