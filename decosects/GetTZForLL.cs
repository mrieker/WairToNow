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

/**
 * @brief Get timezone name for a given lat/lon
 */

using Mono.Data.Sqlite;
using System;
using System.Diagnostics;
using System.Data;
using System.IO;
using System.Text;

public class GetTZForLL {
    private static IDbConnection tzdbcon = null;

    public static string Get (double dlat, double dlon)
    {
        int i100lat = (int) Math.Round (dlat * 100.0);
        int i100lon = (int) Math.Round (dlon * 100.0);
        if ((tzdbcon == null) && File.Exists ("../webdata/tzforlatlon.db")) {
            tzdbcon = new SqliteConnection ("URI=file:../webdata/tzforlatlon.db");
            tzdbcon.Open ();
        }
        if (tzdbcon != null) {
            IDbCommand dbcmd = tzdbcon.CreateCommand ();
            dbcmd.CommandText = "SELECT tzname FROM timezones WHERE i100lat=" + i100lat + " AND i100lon=" + i100lon;
            string tzname = (string) dbcmd.ExecuteScalar ();
            if (tzname != null) return tzname;
            tzdbcon.Close ();
            tzdbcon = null;
        }

        ProcessStartInfo psi = new ProcessStartInfo ("php", "gettzforllcsharp.php " + i100lat + " " + i100lon);
        psi.RedirectStandardOutput = true;
        psi.UseShellExecute = false;
        psi.CreateNoWindow = true;
        Process p = new Process ();
        p.StartInfo = psi;
        p.Start ();
        return p.StandardOutput.ReadToEnd ().Trim ();
    }
}
