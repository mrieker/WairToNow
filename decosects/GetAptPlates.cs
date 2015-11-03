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
 * Given file stdin containing lines of form:
 *    <icaoid>,<faaid>,...
 * Write lines to stdout of form:
 *    <flag> <state> <faaid> <aptdiagpdfurl> <type>
 *    <title>
 * The first stdout line is expiration date in yyyymmdd form.
 *    flag = S : same as last cycle
 *           C : changed
 *           D : deleted
 *           A : added
 */

// gmcs -debug -out:GetAptPlates.exe GetAptPlates.cs

using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Text;

public class GetAptPlates {
    private static string[] monthNames = { "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec" };
    private static string searchUrl = "http://www.faa.gov/air_traffic/flight_info/aeronav/digital_products/dtpp/search/";

    public static void Main (string[] args)
    {
        bool nextcycle = false;
        int i, j, k;
        string line;

        foreach (string arg in args) {
            if (arg == "-nextcycle") {
                nextcycle = true;
                continue;
            }
            Console.Error.WriteLine ("unknown arg " + arg);
        }

        // <form action="results/" ...
        //   <option value=" ...
        //     Mar 05 - Apr 01, 2015 [1503] ... (Previous)
        //   <option value=" ...
        //     Apr 02 - Apr 29, 2015 [1504]* ... (Current)

        string searchpage = MakeRequest (searchUrl);
        i = searchpage.IndexOf ("<form action=\"results/\"");
    getoption:
        i = searchpage.IndexOf ("<option value=\"", i);
        i = searchpage.IndexOf (" - ", i) + 2;
        string month = GetWord (searchpage, ref i).ToLowerInvariant ();
        string day   = GetWord (searchpage, ref i);
        string year  = GetWord (searchpage, ref i);
        string cycle = GetWord (searchpage, ref i);
        string stage = GetWord (searchpage, ref i).ToLowerInvariant ();
        if (stage == "(previous)") goto getoption;
        if (stage != "(current)") throw new ApplicationException ("bad stage " + stage);

        if (day.EndsWith (",")) day = day.Substring (0, day.Length - 1);

        DateTime expdt = new DateTime (0);
        for (j = 0; j < 12; j ++) {
            if (month == monthNames[j]) {
                expdt = new DateTime (int.Parse (year), j + 1, int.Parse (day), 12, 0, 0);
            }
        }
        while (expdt.DayOfWeek != DayOfWeek.Thursday) expdt = expdt.AddDays (1);
        if (nextcycle) expdt = expdt.AddDays (28);
        Console.WriteLine (expdt.Year.ToString ("D4") + expdt.Month.ToString ("D2") + expdt.Day.ToString ("D2"));

        if (cycle.StartsWith ("[")) cycle = cycle.Substring (1);
        if (cycle.EndsWith   ("]")) cycle = cycle.Substring (0, cycle.Length - 1);
        if (cycle.EndsWith  ("]*")) cycle = cycle.Substring (0, cycle.Length - 2);
        if (nextcycle)              cycle = (int.Parse (cycle) + 1).ToString ();

        string[] tdsplit = new string[] { "<td>" };

        while ((line = Console.In.ReadLine ()) != null) {
            i = line.IndexOf (',');
            string icaoid = line.Substring (0, i);
            j = line.IndexOf (',', ++ i);
            string faaid  = line.Substring (i, j - i);

            int page = 1;
            do {
                string url      = searchUrl + "results/?cycle=" + cycle + "&ident=" + faaid + "&page=" + page;
                string diagpage = MakeRequest (url).Replace ("\r", "").Replace ("\n", "");
                string[] parts  = diagpage.Split (tdsplit, StringSplitOptions.None);
                for (k = 0; k < parts.Length; k ++) {
                    string apidpart = parts[k];  // BVY (KBVY)
                    i  = apidpart.IndexOf ("</td>");
                    if (i < 0) continue;
                    apidpart = apidpart.Substring (0, i).Trim ();
                    if ((apidpart == faaid + " (" + icaoid + ")") || (apidpart == faaid + " ()")) {
                        try {
                            string statpart = parts[k-3];    // MA
                            //string citypart = parts[k-2];  // BEVERLY
                            //string namepart = parts[k-1];  // BEVERLY MUNI

                            //string afdvpart = parts[k+1];  // NE-1
                            string flagpart = parts[k+2];    // A(dded) C(hanged), D(deleted)
                            string typepart = parts[k+3];    // APD, IAP, ...
                            string linkpart = parts[k+4];    // <a href="http://aeronav.faa.gov/d-tpp/1503/05039ad.pdf">AIRPORT DIAGRAM</a>

                            i  = statpart.IndexOf ("</td>");
                            statpart = statpart.Substring (0, i).Trim ();

                            i  = flagpart.IndexOf ("</td>");
                            flagpart = flagpart.Substring (0, i).Trim ();
                            if (flagpart == "") flagpart = "S";

                            i  = typepart.IndexOf ("</td>");
                            typepart = typepart.Substring (0, i).Trim ();

                            i  = linkpart.IndexOf ("\">");
                            string pdfurl = linkpart.Substring (9, i - 9);
                            i += 2;
                            j  = linkpart.IndexOf ("</a>", i);

                            string title  = linkpart.Substring (i, j - i);

                            Console.WriteLine (flagpart + " " + statpart + " " + faaid + " " + pdfurl + " " + typepart);
                            Console.WriteLine (title);
                        } catch (ArgumentOutOfRangeException) {
                        }
                    }
                }

                page ++;
                i = diagpage.IndexOf ("&amp;page=" + page + '"');
            } while (i >= 0);
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
