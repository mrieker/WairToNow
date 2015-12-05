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
 * @brief Read the AWY.txt file from the FAA
 *        and extract airway information and write to airways.csv and intersections.csv.
 *
 *  gmcs -debug -out:WriteAirwaysCsv.exe WriteAirwaysCsv.cs
 *  mono --debug WriteAirwaysCsv.exe < AWY.txt
 */


using System;
using System.Collections.Generic;
using System.IO;

// ref: https://nfdc.faa.gov/xwiki
// ref: https://nfdc.faa.gov/webContent/56DaySub/2015-12-10/Layout_Data/awy_rf.txt

public class Airway {
    public string awyname;
    public string awytype;
    public SortedDictionary<int,AwyPt> awypts = new SortedDictionary<int,AwyPt> ();
}

public class AwyPt {
    public int seq;
    public string copnm;
    public string fixid;
    public string fixtable;
    public double lat;
    public double lon;
}

public class AwyIntxn {
    public int number;
    public string type;
    public double lat;
    public double lon;
}

public class WriteAirwaysCsv {

    public static SortedDictionary<string,Airway> airways = new SortedDictionary<string,Airway> ();
    public static SortedDictionary<int,AwyIntxn> awyintxns = new SortedDictionary<int,AwyIntxn> ();

    public static void Main ()
    {
        Airway airway;
        AwyIntxn awyintxn;
        AwyPt awypt;
        int number;
        string line;

        while ((line = Console.ReadLine ()) != null) {
            if (line.StartsWith ("AWY1")) {
                string awyname = line.Substring (  4, 5).Trim ();               // eg "V1"
                string awytype = line.Substring (  9, 1).Trim ();               // eg "A" "H" or blank
                int    seq     = int.Parse (line.Substring ( 10, 5));           // eg "140"
                string copnm   = line.Substring (107, 3).Trim ();               // eg "024" usually blank

                string key = awytype + "-" + awyname;
                if (!airways.TryGetValue (key, out airway)) {
                    airway = new Airway ();
                    airway.awyname = awyname;
                    airway.awytype = awytype;
                    airways[key] = airway;
                }
                if (!airway.awypts.TryGetValue (seq, out awypt)) {
                    awypt = new AwyPt ();
                    awypt.seq = seq;
                    airway.awypts[seq] = awypt;
                }

                awypt.copnm = copnm;
            }
            if (line.StartsWith ("AWY2")) {
                string awyname = line.Substring ( 4,  5).Trim ();               // eg "V1"
                string awytype = line.Substring ( 9,  1).Trim ();               // eg "A" "H" or blank
                int    seq     = int.Parse (line.Substring (10,  5));           // eg "140"
                string fixid   = line.Substring (15, 30).Trim ();               // eg "BOSOX" "LAWRENCE"
                string fixtype = line.Substring (45, 19).Trim ();               // eg "VORTAC" "REP-PT"
                double fixlat  = ParseLL (line.Substring (83, 14), 'N', 'S');   // eg "30-20-19.965N"
                double fixlon  = ParseLL (line.Substring (97, 14), 'E', 'W');   // eg "081-30-35.738W"
                string navid   = line.Substring (116, 4).Trim ();               // navaid id (might be blank)

                string key = awytype + "-" + awyname;
                if (!airways.TryGetValue (key, out airway)) {
                    airway = new Airway ();
                    airway.awyname = awyname;
                    airway.awytype = awytype;
                    airways[key] = airway;
                }
                if (!airway.awypts.TryGetValue (seq, out awypt)) {
                    awypt = new AwyPt ();
                    awypt.seq = seq;
                    airway.awypts[seq] = awypt;
                }

                if (fixid.Contains ("BORDER")) {
                    airway.awypts.Remove (seq);
                }

                string fixtable = "FIX";

                if (int.TryParse (fixid, out number)) {
                    if (!awyintxns.TryGetValue (number, out awyintxn)) {
                        awyintxn = new AwyIntxn ();
                        awyintxn.number = number;
                        awyintxn.type = fixtype;
                        awyintxn.lat = fixlat;
                        awyintxn.lon = fixlon;
                        awyintxns[number] = awyintxn;
                    } else if ((awyintxn.lat != fixlat) || (awyintxn.lon != fixlon)) {
                        throw new Exception ("conflicting definitions of intersection " + number);
                    }
                    fixid = number.ToString ();
                    fixtable = "INT";
                } else if (navid != "") {
                    fixid = navid;
                    fixtable = "NAV";
                }

                awypt.fixid    = fixid;
                awypt.fixtable = fixtable;
                awypt.lat      = fixlat;
                awypt.lon      = fixlon;
            }
        }

        StreamWriter intxnfile = new StreamWriter ("intersections.csv");
        foreach (AwyIntxn ai in awyintxns.Values) {
            intxnfile.WriteLine (ai.number + "," + ai.type + "," + ai.lat + "," + ai.lon);
        }
        intxnfile.Close ();

        StreamWriter airwayfile = new StreamWriter ("airways.csv");
        foreach (Airway awy in airways.Values) {
            airwayfile.WriteLine (awy.awyname + "," + awy.awytype + "," + awy.awypts.Count);
            foreach (AwyPt pt in awy.awypts.Values) {
                airwayfile.WriteLine ("," + pt.fixid + "," + pt.fixtable + "," + pt.lat + "," + pt.lon + "," + pt.copnm);
            }
        }
        airwayfile.Close ();
    }

    private static double ParseLL (string str, char posch, char negch)
    {
        str = str.Trim ();
        if (str == "") return 0;
        int lm1 = str.Length - 1;
        char dir = str[lm1];
        str = str.Substring (0, lm1);
        string[] parts = str.Split ('-');
        double deg = int.Parse (parts[0]) + int.Parse (parts[1]) / 60.0 + double.Parse (parts[2]) / 3600.0;
        if (dir == negch) deg = -deg;
        else if (dir != posch) throw new Exception ("bad latlon direction in <" + str + dir + ">");
        return deg;
    }
}
