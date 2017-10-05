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
 * Given file stdin containing lines of form (from datums/airports_<cycles28>.csv):
 *    <icaoid>,<faaid>,<elev>,<name>,<lat>,<lon>,<magvar>,<descr>,<state>,<publib/private>
 *      [0] KBVY,
 *      [1] BVY,
 *      [2] 107.3,
 *      [3] "BEVERLY RGNL",
 *      [4] 42.5841388888889,
 *      [5] -70.9161388888889,
 *      [6] 16,
 *      [7] "3 NW of BEVERLY, MA..."
 *      [8] MA,
 *      [9] PU
 * Write lines to stdout of form:
 *    <flag> <state> <faaid> <aptdiagpdfurl> <type>
 *    <title>
 *    flag = S : same as last cycle
 *           C : changed
 *           D : deleted
 *           A : added
 */

// gmcs -debug -out:GetAptPlates.exe GetAptPlates.cs
// mono --debug GetAptPlates.exe $airaccycle < ... > ...

using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Text;

public class GetAptPlates {
    private static string searchUrl = "http://www.faa.gov/air_traffic/flight_info/aeronav/digital_products/dtpp/search/";

    public static void Main (string[] args)
    {
        int i, j, k;
        string line;

        string airac = args[0];

        string[] comsplit = new string[] { "," };
        string[] tdsplit = new string[] { "<td>" };

        while ((line = Console.In.ReadLine ()) != null) {
            string[] parts = line.Split (comsplit, StringSplitOptions.None);
            string icaoid  = parts[0];
            string faaid   = parts[1];
            string stateid = parts[parts.Length-2];

            string datname = "datums/getaptplates_" + airac + "/" + faaid + ".dat";
            if (!File.Exists (datname)) {
                Directory.CreateDirectory ("datums/getaptplates_" + airac);
                StreamWriter datfile = new StreamWriter (datname + ".tmp");

                int page = 1;
                do {
                    string url      = searchUrl + "results/?cycle=" + airac + "&ident=" + faaid + "&page=" + page;
                    string diagpage = MakeRequest (url).Replace ("\r", "").Replace ("\n", "");
                    parts           = diagpage.Split (tdsplit, StringSplitOptions.None);
                    for (k = 0; k < parts.Length; k ++) {
                        string apidpart = parts[k];  // BVY (KBVY)
                        i  = apidpart.IndexOf ("</td>");
                        if (i < 0) continue;
                        apidpart = apidpart.Substring (0, i).Trim ();
                        if ((apidpart == faaid + " (" + icaoid + ")") || (apidpart == faaid + " ()")) {
                            try {
                                //string statpart = parts[k-3];  // MA
                                //string citypart = parts[k-2];  // BEVERLY
                                //string namepart = parts[k-1];  // BEVERLY MUNI

                                //string afdvpart = parts[k+1];  // NE-1
                                string flagpart = parts[k+2];    // A(dded) C(hanged), D(deleted)
                                string typepart = parts[k+3];    // APD, IAP, ...
                                string linkpart = parts[k+4];    // <a href="http://aeronav.faa.gov/d-tpp/1503/05039ad.pdf">AIRPORT DIAGRAM</a>

                                //Console.WriteLine ("state=" + stateid + " flag=" + flagpart + " type=" + typepart + " link=" + linkpart);

                                i  = flagpart.IndexOf ("</td>");
                                flagpart = flagpart.Substring (0, i).Trim ();
                                if (flagpart == "") flagpart = "S";

                                i  = typepart.IndexOf ("</td>");
                                typepart = typepart.Substring (0, i).Trim ();

                                string pdfurl, title;
                                if (flagpart != "D") {
                                    i  = linkpart.IndexOf ("\">");
                                    pdfurl = linkpart.Substring (9, i - 9);
                                    i += 2;
                                    j  = linkpart.IndexOf ("</a>", i);
                                    title  = linkpart.Substring (i, j - i);
                                } else {
                                    i  = linkpart.IndexOf ("</td>");
                                    pdfurl = "-";
                                    title  = linkpart.Substring (0, i);
                                }

                                datfile.WriteLine (flagpart + " " + stateid + " " + faaid + " " + pdfurl + " " + typepart);
                                datfile.WriteLine (title);
                                Console.WriteLine (flagpart + " " + stateid + " " + faaid + " " + pdfurl + " " + typepart);
                                Console.WriteLine (title);
                            } catch (ArgumentOutOfRangeException) {
                            }
                        }
                    }

                    page ++;
                    i = diagpage.IndexOf ("&amp;page=" + page + '"');
                } while (i >= 0);

                datfile.Close ();
                File.Move (datname + ".tmp", datname);
            } else {
                StreamReader datfile = new StreamReader (datname);
                string datline;
                while ((datline = datfile.ReadLine ()) != null) {
                    Console.WriteLine (datline);
                }
                datfile.Close ();
            }
        }
    }


    /**
     * @brief Get next non-blank token from string.
     */
    private static string GetWord (string str, ref int i)
    {
        int j = i;
        while (str[j] <= ' ') j ++;
        int k = j;
        while (str[k] >  ' ') k ++;
        i = k;
        return str.Substring (j, k - j);
    }


    /**
     * @brief Send Http(s) request and read response.
     * @param requestUrl = web address
     * @returns string containing reply
     */
    public static string MakeRequest (string requestUrl)
    {
        Process p = new Process ();
        p.StartInfo.Arguments = "-q -O - '" + requestUrl + "'";
        p.StartInfo.CreateNoWindow = true;
        p.StartInfo.FileName = "wget";
        p.StartInfo.RedirectStandardOutput = true;
        p.StartInfo.UseShellExecute = false;
        p.Start ();

        string reply = p.StandardOutput.ReadToEnd ();
        p.WaitForExit ();
        return reply;
    }
}
