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


// gmcs -out:PurgeOldCharts.exe PurgeOldCharts.cs
// mono --debug PurgeOldCharts.exe <chartdir>

using System;
using System.Collections.Generic;
using System.IO;

public class PurgeOldCharts {
    public static void Main (string[] args)
    {
        SortedDictionary<String,SortedDictionary<int,String>> charts = new SortedDictionary<String,SortedDictionary<int,string>> ();
        string[] files = Directory.GetFileSystemEntries (args[0]);

        foreach (string file in files) {
            string spacename = file.Replace ("_", " ");
            string ext = "";
            int i = spacename.IndexOf ('.');
            if (i >= 0) {
                ext = spacename.Substring (i);
                spacename = spacename.Substring (0, i);
            }
            int j = spacename.LastIndexOf (' ');
            if (j < 0) continue;
            int rev;
            if (!int.TryParse (spacename.Substring (j + 1), out rev)) continue;
            string basename = spacename.Substring (0, j) + ext;  // eg "Chicago TAC.tif"
            SortedDictionary<int,String> chart;
            if (!charts.TryGetValue (basename, out chart)) {
                chart = new SortedDictionary<int,String> ();
                charts[basename] = chart;
            }
            chart[rev] = file;
        }

        foreach (SortedDictionary<int,string> chart in charts.Values) {
            string lastfile = null;
            foreach (string file in chart.Values) {
                if (lastfile != null) Console.WriteLine (lastfile);
                lastfile = file;
            }
        }
    }
}
