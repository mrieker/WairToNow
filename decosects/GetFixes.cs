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


// gmcs -debug -out:GetFixes.exe GetFixes.cs
// mono --debug GetFixes.exe < FIX.txt | sort > fixes.csv

// ref: https://nfdc.faa.gov/webContent/56DaySub/2015-06-25/Layout_Data/fix_rf.txt

using System;
using System.Collections.Generic;
using System.IO;
using System.Text;

public class GetFixes {
    public static void Main ()
    {
        string line;
        while ((line = Console.ReadLine ()) != null) {
            if (!line.StartsWith ("FIX1")) continue;
            if (line[212] != 'Y') continue;
            string fix = line.Substring (228, 5).Replace (" ", "");
            if (fix.Length != 5) continue;
            double lat = DecodeLatLon (line.Substring (66, 14).Trim (), 'N', 'S');
            double lon = DecodeLatLon (line.Substring (80, 14).Trim (), 'E', 'W');
            string state = line.Substring (34, 30).Trim ();   // can be MICRONESIA, FED STATES OF
            string usage = line.Substring (213, 15).Trim ();  // REP-PT, etc
            Console.WriteLine (fix + "," + lat + "," + lon + ",\"" + state + "\"," + usage);
        }
    }

    // ddd-mm-ss.sssc
    public static double DecodeLatLon (string str, char posch, char negch)
    {
        char endch = str[str.Length-1];
        bool neg = false;
        if (endch == negch) neg = true;
        else if (endch != posch) throw new Exception ("bad string end " + str);
        str = str.Substring (0, str.Length - 1);
        int i = str.IndexOf ('-');
        double deg = int.Parse (str.Substring (0, i));
        int j = str.IndexOf ('-', ++ i);
        double min = int.Parse (str.Substring (i, j - i));
        double sec = double.Parse (str.Substring (++ j));
        deg += min / 60.0;
        deg += sec / 3600.0;
        if (neg) deg = -deg;
        return deg;
    }
}
