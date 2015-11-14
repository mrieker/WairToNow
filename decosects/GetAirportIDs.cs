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
 * @brief Writes airports.csv and runways.csv summary files
 *        and per-airport information files.
 *
 * gmcs -debug -out:GetAirportIDs.exe GetAirportIDs.cs
 * cat APT.txt TWR.txt | mono --debug GetAirportIDs.exe airports.csv runways.csv aptinfo aptinfo.html
 */

// https://nfdc.faa.gov/xwiki -> automatic redirect
// https://nfdc.faa.gov/xwiki/bin/view/NFDC/WebHome -> 56 Day NASR Subscription
// https://nfdc.faa.gov/xwiki/bin/view/NFDC/56+Day+NASR+Subscription -> Current
// https://nfdc.faa.gov/xwiki/bin/view/NFDC/56DaySub-2015-06-25   (2015-06-25 = cycle start date)
// https://nfdc.faa.gov/webContent/56DaySub/2015-06-25/APT.zip
// https://nfdc.faa.gov/webContent/56DaySub/2015-06-25/TWR.zip

// https://nfdc.faa.gov/webContent/56DaySub/2015-10-15/Layout_Data/apt_rf.txt
// https://nfdc.faa.gov/webContent/56DaySub/2015-10-15/Layout_Data/Twr_rf.txt

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
        public string info;     // goes on FAAWP page
        public string state;
        public string variatn;

        // shown when Info button clicked
        public Dictionary<String,String>  nvp  = new Dictionary<String,String>  ();
        public Dictionary<String,RwyPair> rwps = new Dictionary<String,RwyPair> ();
    }

    public class RwyPair {
        public Dictionary<String,String> nvp = new Dictionary<String,String> ();
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

                apt.nvp["faciltype"]     = line.Substring (  14, 13).Trim ();   // AIRPORT, BALLOONPORT, ...
                apt.nvp["faaid"]         = line.Substring (  27,  4).Trim ();
                apt.nvp["effdate"]       = line.Substring (  31, 10).Trim ();
                apt.nvp["region"]        = line.Substring (  41,  3).Trim ();
                apt.nvp["state2let"]     = line.Substring (  48,  2).Trim ();
                apt.nvp["statelong"]     = line.Substring (  50, 20).Trim ();
                apt.nvp["county"]        = line.Substring (  70, 21).Trim ();
                apt.nvp["city"]          = line.Substring (  93, 40).Trim ();
                apt.nvp["facilname"]     = line.Substring ( 133, 50).Trim ();
                apt.nvp["faciluse"]      = line.Substring ( 185,  2).Trim ();   // PU=public; PR =private
                apt.nvp["ownername"]     = line.Substring ( 187, 35).Trim ();
                apt.nvp["owneraddr"]     = line.Substring ( 222, 72).Trim ();
                apt.nvp["ownercityetc"]  = line.Substring ( 294, 45).Trim ();
                apt.nvp["ownerphone"]    = line.Substring ( 339, 16).Trim ();
                apt.nvp["mangrname"]     = line.Substring ( 355, 35).Trim ();
                apt.nvp["mangraddr"]     = line.Substring ( 390, 72).Trim ();
                apt.nvp["mangrcityetc"]  = line.Substring ( 462, 45).Trim ();
                apt.nvp["mangrphone"]    = line.Substring ( 507, 16).Trim ();
                apt.nvp["latitude"]      = line.Substring ( 523, 15).Trim ();
                apt.nvp["longitude"]     = line.Substring ( 550, 15).Trim ();
                apt.nvp["elevation"]     = line.Substring ( 578,  7).Trim ();
                apt.nvp["magvar"]        = line.Substring ( 586,  3).Trim ();
                apt.nvp["magvaryear"]    = line.Substring ( 589,  4).Trim ();
                apt.nvp["trafpatagl"]    = line.Substring ( 593,  4).Trim ();
                apt.nvp["secchart"]      = line.Substring ( 597, 30).Trim ();
                apt.nvp["nmfromcity"]    = line.Substring ( 627,  2).Trim ();
                apt.nvp["dirfromcity"]   = line.Substring ( 629,  3).Trim ();
                apt.nvp["activdate"]     = line.Substring ( 833,  7).Trim ();
                apt.nvp["status"]        = line.Substring ( 840,  2).Trim ();   // CI=closed indef; CP=closed perm; O=operational
                apt.nvp["aptofentry"]    = line.Substring ( 877,  1).Trim ();   // Y, N
                apt.nvp["landrights"]    = line.Substring ( 878,  1).Trim ();   // Y, N
                apt.nvp["aptlights"]     = line.Substring ( 966,  7).Trim ();   // hour range
                apt.nvp["beaconlits"]    = line.Substring ( 973,  7).Trim ();   // hour range
                apt.nvp["towered"]       = line.Substring ( 980,  1).Trim ();   // Y, N
                apt.nvp["unicom"]        = line.Substring ( 981,  7).Trim ();
                apt.nvp["ctaf"]          = line.Substring ( 988,  7).Trim ();
                apt.nvp["landfeenoncom"] = line.Substring (1002,  1).Trim ();   // Y, N
                apt.nvp["icaoid"]        = line.Substring (1210,  7).Trim ();
            }

            // write runway records
            if (line.StartsWith ("RWY")) {
                string keyid = line.Substring (3, 11).Trim ();      // key id
                Airport apt  = aptsbykeyid[keyid];

                string numa = line.Substring (65, 3).Trim ();       // runway number
                string alna = TrimLZ (line.Substring (68, 3));      // true alignment (degrees)
                string lata = ParseLat (line.Substring (103, 12));
                string lona = ParseLon (line.Substring (130, 12));
                string elva = TrimTZ (line.Substring (142, 7));     // elevation (feet)

                string numb = line.Substring (287, 3).Trim ();      // runway number
                string alnb = TrimLZ (line.Substring (290, 3));     // true alignment (degrees)
                string latb = ParseLat (line.Substring (325, 12));
                string lonb = ParseLon (line.Substring (352, 12));
                string elvb = TrimTZ (line.Substring (364, 7));     // elevation (feet)

                if ((lata != "") && (lona != "") && (latb != "") && (lonb != "")) {
                    runways.WriteLine (apt.faaid + "," + numa + "," + alna + "," + elva + "," + lata + "," + lona + "," + latb + "," + lonb);
                    runways.WriteLine (apt.faaid + "," + numb + "," + alnb + "," + elvb + "," + latb + "," + lonb + "," + lata + "," + lona);
                }

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

                RwyPair rwp = new RwyPair ();
                rwp.nvp["rwidpair"]      = line.Substring (     16,  7).Trim ();
                rwp.nvp["rwlen"]         = line.Substring (     23,  5).Trim ();
                rwp.nvp["width"]         = line.Substring (     28,  4).Trim ();
                rwp.nvp["surftype"]      = line.Substring (     32, 12).Trim ();
                rwp.nvp["edgelights"]    = line.Substring (     60,  5).Trim ();
                rwp.nvp["base_id"]       = line.Substring (     65,  3).Trim ();
                rwp.nvp["base_trualn"]   = line.Substring (     68,  3).Trim ();
                rwp.nvp["base_ils"]      = line.Substring (     71, 10).Trim ();
                rwp.nvp["base_ritraf"]   = line.Substring (     81,  1).Trim ();    // Y=right; N=left
                rwp.nvp["base_markng"]   = line.Substring (     82,  5).Trim ();
                rwp.nvp["base_marcnd"]   = line.Substring (     87,  1).Trim ();
                rwp.nvp["base_lat"]      = line.Substring (     88, 15).Trim ();
                rwp.nvp["base_lon"]      = line.Substring (    115, 15).Trim ();
                rwp.nvp["base_elevat"]   = line.Substring (    142,  7).Trim ();
                rwp.nvp["base_gpangl"]   = line.Substring (    152,  4).Trim ();
                rwp.nvp["base_vasi"]     = line.Substring (    228,  5).Trim ();
                rwp.nvp["base_applt"]    = line.Substring (    237,  8).Trim ();
                rwp.nvp["base_reil"]     = line.Substring (    245,  1).Trim ();
                rwp.nvp["base_clinelt"]  = line.Substring (    246,  1).Trim ();
                rwp.nvp["base_tdlite"]   = line.Substring (    247,  1).Trim ();
                rwp.nvp["recip_id"]      = line.Substring (222+ 65,  3).Trim ();
                rwp.nvp["recip_trualn"]  = line.Substring (222+ 68,  3).Trim ();
                rwp.nvp["recip_ils"]     = line.Substring (222+ 71, 10).Trim ();
                rwp.nvp["recip_ritraf"]  = line.Substring (222+ 81,  1).Trim ();    // Y=right; N=left
                rwp.nvp["recip_markng"]  = line.Substring (222+ 82,  5).Trim ();
                rwp.nvp["recip_marcnd"]  = line.Substring (222+ 87,  1).Trim ();
                rwp.nvp["recip_lat"]     = line.Substring (222+ 88, 15).Trim ();
                rwp.nvp["recip_lon"]     = line.Substring (222+115, 15).Trim ();
                rwp.nvp["recip_elevat"]  = line.Substring (222+142,  7).Trim ();
                rwp.nvp["recip_gpangl"]  = line.Substring (222+152,  4).Trim ();
                rwp.nvp["recip_vasi"]    = line.Substring (222+228,  5).Trim ();
                rwp.nvp["recip_applt"]   = line.Substring (222+237,  8).Trim ();
                rwp.nvp["recip_reil"]    = line.Substring (222+245,  1).Trim ();
                rwp.nvp["recip_clinelt"] = line.Substring (222+246,  1).Trim ();
                rwp.nvp["recip_tdlite"]  = line.Substring (222+247,  1).Trim ();

                apt.rwps[rwp.nvp["rwidpair"]] = rwp;
            }

            if (line.StartsWith ("RMK")) {
                string keyid = line.Substring (3, 11).Trim ();      // key id
                Airport apt  = aptsbykeyid[keyid];
                apt.nvp["RMK-"+line.Substring(16,13).Trim()] = line.Substring (29, 1500).Trim ();
            }

            if (line.StartsWith ("TWR1")) {
                string faaid = line.Substring (4, 4).Trim ();
                Airport apt;
                if (aptsbyfaaid.TryGetValue (faaid, out apt)) {
                    apt.nvp["towertype"] = line.Substring (238, 12).Trim ();
                    apt.nvp["numhours"]  = line.Substring (250, 2).Trim ();
                    apt.nvp["hourdays"]  = line.Substring (252, 3).Trim ();
                }
            }

            if (line.StartsWith ("TWR2")) {
                string faaid = line.Substring (4, 4).Trim ();
                Airport apt;
                if (aptsbyfaaid.TryGetValue (faaid, out apt)) {
                    apt.nvp["hourslocal"] = line.Substring (1408, 200).Trim ();
                }
            }

            // tower-related frequencies (tower, ground, atis)
            if (line.StartsWith ("TWR3")) {
                string faaid = line.Substring (4, 4).Trim ();
                Airport apt;
                if (aptsbyfaaid.TryGetValue (faaid, out apt)) {
                    for (int i = 0; i < 9; i ++) {
                        string freq = line.Substring (i * 94 +  8, 44).Trim ();
                        string purp = line.Substring (i * 94 + 52, 50).Trim ();
                        if ((freq != "") && (purp != "")) {
                            apt.info += "\n" + purp + ": " + freq;
                            string key = "FREQ-" + purp;
                            if (apt.nvp.ContainsKey (key)) {
                                apt.nvp[key] += "; " + freq;
                            } else {
                                apt.nvp[key]  = freq;
                            }
                        }
                    }
                }
            }

            if (line.StartsWith ("TWR6")) {
                string faaid = line.Substring (4, 4).Trim ();
                Airport apt;
                if (aptsbyfaaid.TryGetValue (faaid, out apt)) {
                    apt.nvp["TWRMK-"+line.Substring(8,5).Trim()] = line.Substring (13, 800).Trim ();
                }
            }

            // approach control frequencies
            if (line.StartsWith ("TWR7")) {
                string faaid = line.Substring (113, 4).Trim ();
                Airport apt;
                if (aptsbyfaaid.TryGetValue (faaid, out apt)) {
                    string freq = line.Substring (8, 44).Trim ();
                    string purp = line.Substring (52, 50).Trim ();
                    if ((freq != "") && (purp != "")) {
                        apt.info += "\n" + purp + ": " + freq;
                        string key = "FREQ-" + purp;
                        if (apt.nvp.ContainsKey (key)) {
                            apt.nvp[key] += "; " + freq;
                        } else {
                            apt.nvp[key]  = freq;
                        }
                    }
                }
            }

            if (line.StartsWith ("TWR9")) {
                string faaid = line.Substring (113, 4).Trim ();
                Airport apt;
                if (aptsbyfaaid.TryGetValue (faaid, out apt)) {
                    apt.nvp["atishours"] = line.Substring ( 12, 200).Trim ();
                    apt.nvp["atispurp"]  = line.Substring (212, 100).Trim ();
                    apt.nvp["atisphone"] = line.Substring (312,  18).Trim ();
                }
            }
        }

        string htmlfile = File.ReadAllText (args[3]);
        string htmlbeg  = htmlfile.Substring (0, htmlfile.IndexOf ("%%%%"));
        string htmlend  = htmlfile.Substring (htmlfile.IndexOf ("%%%%") + 4);

        foreach (Airport apt in aptsbyfaaid.Values) {

            // this goes into the airports_<expdate>.csv file
            // the info string is displayed on the FAAWP page itself
            airports.WriteLine (apt.icaoid + "," + apt.faaid + "," + apt.elevatn + "," +
                    QuotedString (apt.aptname, '"') + "," + apt.aptlat + "," + apt.aptlon + "," +
                    apt.variatn + "," + QuotedString (apt.info, '"') + "," + apt.state);

            // this goes into the aptinfo_<expdate>/f/aaid.html.gz file and is displayed when the Info button is clicked
            StreamWriter infofile = new StreamWriter (args[2] + "/" + apt.faaid + ".html");
            infofile.WriteLine (htmlbeg);
            infofile.WriteLine ("apt = [];");
            foreach (String key in apt.nvp.Keys) {
                String val = apt.nvp[key];
                infofile.WriteLine ("apt['" + key + "'] = " + QuotedString (val, '\'') + ";");
            }
            infofile.WriteLine ("rwps = [];");
            foreach (String key in apt.rwps.Keys) {
                infofile.WriteLine ("rwps['" + key + "'] = [];");
                RwyPair rwp = apt.rwps[key];
                foreach (String key2 in rwp.nvp.Keys) {
                    String val = rwp.nvp[key2];
                    infofile.WriteLine ("rwps['" + key + "']['" + key2 + "'] = " + QuotedString (val, '\'') + ";");
                }
            }
            infofile.WriteLine (htmlend);
            infofile.Close ();
        }

        airports.Close ();
        runways.Close ();
    }

    public static string TrimLZ (string str)
    {
        str = str.Trim ();
        while ((str.Length > 1) && (str[0] == '0')) str = str.Substring (1);
        return str;
    }

    public static string TrimTZ (string str)
    {
        str = str.Trim ();
        if (str.IndexOf ('.') > 0) {
            while (str.EndsWith (".0")) str = str.Substring (0, str.Length - 1);
            if (str.EndsWith (".")) str = str.Substring (0, str.Length - 1);
        }
        return str;
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

    public static String QuotedString (String unquoted, char quote)
    {
        int len = unquoted.Length;
        StringBuilder sb = new StringBuilder (len + 2);
        sb.Append (quote);
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
                case '\'': {
                    sb.Append ("\\'");
                    break;
                }
                default: {
                    sb.Append (c);
                    break;
                }
            }
        }
        sb.Append (quote);
        return sb.ToString ();
    }
}
