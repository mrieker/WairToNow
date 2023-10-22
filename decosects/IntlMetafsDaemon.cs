//    Copyright (C) 2020, Mike Rieker, Beverly, MA USA
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

// mcs -debug -out:IntlMetafsDaemon.exe IntlMetafsDaemon.cs -reference:System.Data.dll -reference:Mono.Data.Sqlite.dll

// mono --debug IntlMetafsDaemon.exe <seconds>

using Mono.Data.Sqlite;
using System;
using System.Collections.Generic;
using System.Data;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Text;
using System.Threading;
using System.Xml;

// main program
public class IntlMetafsDaemon {
    public static void Main (string[] args)
    {
        int seconds = int.Parse (args[0]);

        IDbConnection intlmetafsdbcon = new SqliteConnection ("URI=file:datums/intlmetafs.db");
        intlmetafsdbcon.Open ();

        long gap = 0;

        long whenms = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds ();
        while (true) {
            long when = DateTimeOffset.UtcNow.ToUnixTimeSeconds ();

            // get airport of unknown status, but don't starve out re-checking ones we think we know about
            IDbCommand dbcmd1 = intlmetafsdbcon.CreateCommand ();
            dbcmd1.CommandText = "SELECT iamt_icaoid FROM intlmetafs WHERE iamt_metaf='?' AND iamt_when<" + (when - gap) + " ORDER BY iamt_when,iamt_icaoid LIMIT 1";
            String icaoid = (String) dbcmd1.ExecuteScalar ();
            if (icaoid != null) {
                gap += 5;
            } else {

                // none of those around, get one we think we know about and check it out again
                dbcmd1.CommandText = "SELECT iamt_icaoid FROM intlmetafs ORDER BY iamt_when,iamt_icaoid LIMIT 1";
                icaoid = (String) dbcmd1.ExecuteScalar ();

                gap -= 5;
                if (gap < 0) gap = 0;
            }
            dbcmd1.Dispose ();

            if (icaoid == null) break;

            // try to read METAR and TAF from internet
            String metaf = GetIntlMetaf (icaoid);
            Console.WriteLine (icaoid + " " + when + " " + metaf);

            // write the status to database
            IDbCommand dbcmd2 = intlmetafsdbcon.CreateCommand ();
            dbcmd2.CommandText = "UPDATE intlmetafs SET iamt_metaf=@iamt_metaf,iamt_when=" + when + " WHERE iamt_icaoid=@iamt_icaoid";
            dbcmd2.Parameters.Add (new SqliteParameter ("@iamt_icaoid", icaoid));
            dbcmd2.Parameters.Add (new SqliteParameter ("@iamt_metaf",  metaf));
            dbcmd2.ExecuteNonQuery ();
            dbcmd2.Dispose ();

            if (seconds > 0) {
                // try next in given seconds
                whenms += seconds * 1000;
            } else {
                // pace to go through them all every 27 days
                IDbCommand dbcmd3 = intlmetafsdbcon.CreateCommand ();
                dbcmd3.CommandText = "SELECT COUNT(iamt_icaoid) FROM intlmetafs";
                int count = int.Parse (dbcmd3.ExecuteScalar ().ToString ());
                dbcmd3.Dispose ();
                long ms = (count > 0) ? 27L * 86400L * 1000L / count : 999999999;
                if (ms > 3600 * 1000) ms = 3600 * 1000;
                whenms += ms;
            }
            long nowms = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds ();
            if (whenms > nowms) Thread.Sleep ((int) (whenms - nowms));
        }
    }

    // call webmetaf.php to get metars and tafs
    // return 'M' if we get a metar
    //        'T' if we get a taf
    //       'MT' if we get both
    //         '' if we get neither
    //        '?' if unable to read web page
    public static String GetIntlMetaf (String icaoid)
    {
        try {
            ProcessStartInfo psi = new ProcessStartInfo ("php", "../webpages/webmetaf.php IMDB " + icaoid);
            psi.RedirectStandardOutput = true;
            psi.UseShellExecute = false;
            psi.CreateNoWindow = true;
            Process proc = new Process ();
            proc.StartInfo = psi;
            proc.Start ();
            String reply = proc.StandardOutput.ReadToEnd ().Trim ();
            proc.StandardOutput.Close ();
            proc.WaitForExit ();
            proc.Dispose ();

            bool hasmetar = reply.Contains ("METAR:");
            bool hastaf   = reply.Contains ("TAF:");

            return ((hasmetar | hastaf) ? "M" : "") + (hastaf ? "T" : "");
        } catch (Exception e) {
            Console.WriteLine (icaoid + ": " + e.Message);
            return "?";
        }

    }
}
