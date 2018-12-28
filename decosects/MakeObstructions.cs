//    Copyright (C) 2018, Mike Rieker, Beverly, MA USA
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

// Convert obstruction datafile to SQLite database

// mcs -debug -out:MakeObstructions.exe MakeObstructions.cs -reference:System.Data.dll -reference:Mono.Data.Sqlite.dll
// cat DOF.DAT | mono --debug MakeObstructions.exe datums/obstructions_20190103.db

using Mono.Data.Sqlite;
using System;
using System.Data;
using System.IO;
using System.Text;

public class MakeObstructions {
    public static void Main (string[] args)
    {
        string dbname = args[0];
        File.Delete (dbname);
        IDbConnection dbcon = new SqliteConnection ("URI=file:" + dbname);
        try {
            dbcon.Open ();

            DoCommand (dbcon, "CREATE TABLE obstrs (ob_msl INTEGER NOT NULL, ob_agl INTEGER NOT NULL, " +
                    "ob_lat REAL NOT NULL, ob_lon REAL NOT NULL, ob_type TEXT NOT NULL);");
            DoCommand (dbcon, "CREATE INDEX obstrs_lats ON obstrs (ob_lat);");
            DoCommand (dbcon, "CREATE INDEX obstrs_lons ON obstrs (ob_lon);");

            DoCommand (dbcon, "BEGIN;");
            int i = 0;

            string csv;
            while ((csv = Console.ReadLine ()) != null) {
                if (csv.StartsWith ("--------")) break;
            }
            while ((csv = Console.ReadLine ()) != null) {
                //WS-000160 O WS    SAMOA            13 49 00.00S 171 50 00.00W TOWER              2 00407 00447 U 9 I U                C 1999032 
                double lat = ParseLatLon (
                        csv.Substring (35, 2),
                        csv.Substring (38, 2),
                        csv.Substring (41, 5),
                        csv[46], 'N', 'S');
                double lon = ParseLatLon (
                        csv.Substring (48, 3),
                        csv.Substring (52, 2),
                        csv.Substring (55, 5),
                        csv[60], 'E', 'W');
                string type = csv.Substring (62, 18).Trim ();
                int agl = int.Parse (csv.Substring (83, 5).Trim ());
                int msl = int.Parse (csv.Substring (89, 5).Trim ());

                IDbCommand dbcmd1 = dbcon.CreateCommand ();
                try {
                    dbcmd1.CommandText = "INSERT INTO obstrs (ob_msl,ob_agl,ob_lat,ob_lon,ob_type) " +
                            "VALUES (@ob_msl,@ob_agl,@ob_lat,@ob_lon,@ob_type)";
                    dbcmd1.Parameters.Add (new SqliteParameter ("@ob_msl",  msl));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@ob_lat",  lat));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@ob_lon",  lon));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@ob_agl",  agl));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@ob_type", type));
                    dbcmd1.ExecuteNonQuery ();
                } finally {
                    dbcmd1.Dispose ();
                }

                if (++ i == 4096) {
                    Console.WriteLine (csv);
                    DoCommand (dbcon, "COMMIT; BEGIN;");
                    i = 0;
                }
            }
            DoCommand (dbcon, "COMMIT;");
        } finally {
            dbcon.Close ();
        }
    }

    private static void DoCommand (IDbConnection dbcon, string command)
    {
        IDbCommand dbcmd = dbcon.CreateCommand ();
        try {
            dbcmd.CommandText = command;
            dbcmd.ExecuteNonQuery ();
        } finally {
            dbcmd.Dispose ();
        }
    }

    private static double ParseLatLon (
            string degs, string mins, string secs,
            char hem, char pos, char neg)
    {
        double ll = int.Parse (degs.Trim ()) +
                    int.Parse (mins.Trim ()) / 60.0 +
                    double.Parse (secs.Trim ()) / 3600.0;
        if (hem == neg) ll = -ll;
        else if (hem != pos) throw new Exception ("bad hemisphere " + hem);
        return ll;
    }
}
