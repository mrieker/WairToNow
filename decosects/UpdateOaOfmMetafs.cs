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

// Update waypoints{oa,ofm} databases from intlmetafs database
// ...indicating which airports have metars & tafs

// mcs -debug -out:UpdateOaOfmMetafs.exe UpdateOaOfmMetafs.cs -reference:System.Data.dll -reference:Mono.Data.Sqlite.dll

// mono --debug UpdateOaOfmMetafs.exe <expdate>

using Mono.Data.Sqlite;
using System;
using System.Data;

public class UpdateOaOfmMetafs {
    public static void Main (string[] args)
    {
        String expdate = args[0];

        IDbConnection intlmetafsdbcon = new SqliteConnection ("URI=file:datums/intlmetafs.db");
        IDbConnection wayptsoadbcon = new SqliteConnection ("URI=file:datums/waypointsoa_" + expdate + ".db");
        IDbConnection wayptsofmdbcon = new SqliteConnection ("URI=file:datums/waypointsofm_" + expdate + ".db");

        intlmetafsdbcon.Open ();
        wayptsoadbcon.Open ();
        wayptsofmdbcon.Open ();

        ExecuteNonQuery (wayptsoadbcon, "BEGIN");
        ExecuteNonQuery (wayptsofmdbcon, "BEGIN");

        IDbCommand dbcmd1 = intlmetafsdbcon.CreateCommand ();
        dbcmd1.CommandText = "SELECT iamt_icaoid,iamt_metaf FROM intlmetafs WHERE iamt_metaf<>'?'";
        IDataReader reader = dbcmd1.ExecuteReader ();

        int noa = 0;
        int nofm = 0;

        while (reader.Read ()) {
            String icaoid = reader.GetString (0);
            String metaf  = reader.GetString (1);
            noa  += ExecuteNonQuery (wayptsoadbcon, "UPDATE airports SET apt_metaf='" + metaf + "' WHERE apt_icaoid='" + icaoid + "'");
            nofm += ExecuteNonQuery (wayptsofmdbcon, "UPDATE airports SET apt_metaf='" + metaf + "' WHERE apt_icaoid='" + icaoid + "'");
        }

        ExecuteNonQuery (wayptsoadbcon, "COMMIT");
        ExecuteNonQuery (wayptsofmdbcon, "COMMIT");

        Console.WriteLine ("noa=" + noa + " nofm=" + nofm);
    }

    public static int ExecuteNonQuery (IDbConnection dbcon, String nonquery)
    {
        IDbCommand dbcmd = dbcon.CreateCommand ();
        dbcmd.CommandText = nonquery;
        return dbcmd.ExecuteNonQuery ();
    }
}
