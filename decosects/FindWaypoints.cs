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

using Mono.Data.Sqlite;
using System;
using System.Collections.Generic;
using System.Data;
using System.IO;
using System.Text;

public class FindWaypoints {
    public class Waypoint {
        public double lat;
        public double lon;
    }

    public static Waypoint FindOne (string dbname, string ident)
    {
        LinkedList<Waypoint> waypts = FindAll (dbname, ident);
        if (waypts.Count != 1) throw new Exception (waypts.Count + " instances of " + ident + " found");
        return waypts.First.Value;
    }

    public static LinkedList<Waypoint> FindAll (string dbname, string ident)
    {
        LinkedList<Waypoint> waypts = new LinkedList<Waypoint> ();
        IDbConnection dbcon = new SqliteConnection ("URI=file:" + dbname);
        try {
            dbcon.Open ();

            IDbCommand dbcmd1 = dbcon.CreateCommand ();
            dbcmd1.CommandText = "SELECT apt_lat,apt_lon FROM airports WHERE apt_icaoid=@ident";
            dbcmd1.Parameters.Add (new SqliteParameter ("@ident", ident));
            GetWaypoints (waypts, dbcmd1, "apt_lat", "apt_lons");

            IDbCommand dbcmd2 = dbcon.CreateCommand ();
            dbcmd2.CommandText = "SELECT fix_lat,fix_lon FROM fixes WHERE fix_name=@ident";
            dbcmd2.Parameters.Add (new SqliteParameter ("@ident", ident));
            GetWaypoints (waypts, dbcmd2, "fix_lat", "fix_lons");

            IDbCommand dbcmd3 = dbcon.CreateCommand ();
            dbcmd3.CommandText = "SELECT loc_lat,loc_lon FROM localizers WHERE loc_faaid=@ident";
            dbcmd3.Parameters.Add (new SqliteParameter ("@ident", ident));
            GetWaypoints (waypts, dbcmd3, "loc_lat", "loc_lons");

            IDbCommand dbcmd4 = dbcon.CreateCommand ();
            dbcmd4.CommandText = "SELECT nav_lat,nav_lon FROM navaids WHERE nav_faaid=@ident";
            dbcmd4.Parameters.Add (new SqliteParameter ("@ident", ident));
            GetWaypoints (waypts, dbcmd4, "nav_lat", "nav_lons");
        } finally {
            dbcon.Close ();
        }

        return waypts;
    }

    private static void GetWaypoints (
            LinkedList<Waypoint> waypts, 
            IDbCommand dbcmd,
            string latcol, string loncol)
    {
        using (IDataReader rdr = dbcmd.ExecuteReader ()) {
            while (rdr.Read ()) {
                Waypoint wpt = new Waypoint ();
                wpt.lat = rdr.GetDouble (0);
                wpt.lon = rdr.GetDouble (1);
                waypts.AddLast (wpt);
            }
        }
    }
}
