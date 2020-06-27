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

// Combine all waypoint csv files into sqlite db file

// mcs -debug -out:MakeWaypoints.exe MakeWaypoints.cs -reference:System.Data.dll -reference:Mono.Data.Sqlite.dll
// mono --debug MakeWaypoints.exe 20150820

using Mono.Data.Sqlite;
using System;
using System.Collections.Generic;
using System.Data;
using System.IO;
using System.Text;

public class Waypoint {
    public string ident;
    public double lat;
    public double lon;
}

public class MakeWaypoints {
    public static Dictionary<string,LinkedList<Waypoint>> waypointss = new Dictionary<string,LinkedList<Waypoint>> ();

    public static void Main (string[] args)
    {
        string expdate = args[0];

        string dbname = "datums/waypoints_" + expdate + ".db";
        File.Delete (dbname);
        IDbConnection dbcon = new SqliteConnection ("URI=file:" + dbname);
        try {
            dbcon.Open ();

            /*
             * Load airport info into database.
             */
            DoCommand (dbcon, "CREATE TABLE airports (apt_icaoid TEXT PRIMARY KEY, apt_faaid TEXT NOT NULL, apt_elev REAL NOT NULL, apt_name TEXT NOT NULL, " +
                    "apt_lat REAL NOT NULL, apt_lon REAL NOT NULL, apt_desc TEXT NOT NULL, apt_state TEXT NOT NULL, apt_faciluse TEXT NOT NULL);");
            DoCommand (dbcon, "CREATE TABLE aptkeys  (kw_key TEXT NOT NULL, kw_rowid INTEGER NOT NULL);");
            DoCommand (dbcon, "CREATE INDEX airports_faaid ON airports (apt_faaid);");
            DoCommand (dbcon, "CREATE INDEX airports_lats  ON airports (apt_lat);");
            DoCommand (dbcon, "CREATE INDEX airports_lons  ON airports (apt_lon);");
            DoCommand (dbcon, "CREATE INDEX airports_names ON airports (apt_name);");
            DoCommand (dbcon, "CREATE INDEX aptkeys_keys   ON aptkeys  (kw_key);");

            DoCommand (dbcon, "BEGIN;");
            int i = 0;

            StreamReader csvrdr = new StreamReader ("datums/airports_" + expdate + ".csv");
            string csv;
            while ((csv = csvrdr.ReadLine ()) != null) {
                string[] cols = QuotedCSVSplit (csv);

                long rowid;
                IDbCommand dbcmd1 = dbcon.CreateCommand ();
                try {
                    dbcmd1.CommandText = "INSERT INTO airports (apt_icaoid,apt_faaid,apt_elev,apt_name,apt_lat,apt_lon,apt_desc,apt_state,apt_faciluse) " +
                            "VALUES (@apt_icaoid,@apt_faaid,@apt_elev,@apt_name,@apt_lat,@apt_lon,@apt_desc,@apt_state,@apt_faciluse); " +
                            "SELECT last_insert_rowid ()";
                    dbcmd1.Parameters.Add (new SqliteParameter ("@apt_icaoid"  , cols[0]));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@apt_faaid"   , cols[1]));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@apt_elev"    , double.Parse (cols[2])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@apt_name"    , cols[3]));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@apt_lat"     , double.Parse (cols[4])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@apt_lon"     , double.Parse (cols[5])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@apt_desc"    , cols[7]));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@apt_state"   , cols[8]));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@apt_faciluse", cols[9]));
                    rowid = (long) dbcmd1.ExecuteScalar ();
                } finally {
                    dbcmd1.Dispose ();
                }

                DefineWaypoint (cols[0], double.Parse (cols[4]), double.Parse (cols[5]));

                InsertMultipleKeys (dbcon, "aptkeys", cols[0] + " " + cols[1] + " " + cols[3] + " " + cols[7], rowid);

                if (++ i == 256) {
                    DoCommand (dbcon, "COMMIT; BEGIN;");
                    i = 0;
                }
            }
            csvrdr.Close ();

            /*
             * Load fix info into database.
             */
            DoCommand (dbcon, "CREATE TABLE fixes (fix_name TEXT NOT NULL, fix_lat REAL NOT NULL, fix_lon REAL NOT NULL, fix_desc TEXT NOT NULL);");
            DoCommand (dbcon, "CREATE TABLE fixkeys (kw_key TEXT NOT NULL, kw_rowid INTEGER NOT NULL);");
            DoCommand (dbcon, "CREATE INDEX fixes_name ON fixes (fix_name);");
            DoCommand (dbcon, "CREATE INDEX fixes_lats ON fixes (fix_lat);");
            DoCommand (dbcon, "CREATE INDEX fixes_lons ON fixes (fix_lon);");
            DoCommand (dbcon, "CREATE INDEX fixkeys_keys ON fixkeys (kw_key);");

            csvrdr = new StreamReader ("datums/fixes_" + expdate + ".csv");
            while ((csv = csvrdr.ReadLine ()) != null) {
                string[] cols = QuotedCSVSplit (csv);

                long rowid;
                IDbCommand dbcmd1 = dbcon.CreateCommand ();
                try {
                    dbcmd1.CommandText = "INSERT INTO fixes (fix_name,fix_lat,fix_lon,fix_desc) " +
                            "VALUES (@fix_name,@fix_lat,@fix_lon,@fix_desc); SELECT last_insert_rowid ()";
                    dbcmd1.Parameters.Add (new SqliteParameter ("@fix_name", cols[0]));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@fix_lat" , double.Parse (cols[1])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@fix_lon" , double.Parse (cols[2])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@fix_desc", cols[3] + " " + cols[4]));
                    rowid = (long) dbcmd1.ExecuteScalar ();
                } finally {
                    dbcmd1.Dispose ();
                }

                DefineWaypoint (cols[0], double.Parse (cols[1]), double.Parse (cols[2]));

                IDbCommand dbcmd2 = dbcon.CreateCommand ();
                try {
                    dbcmd2.CommandText = "INSERT INTO fixkeys (kw_key,kw_rowid) VALUES (@kw_key,@kw_rowid)";
                    dbcmd2.Parameters.Add (new SqliteParameter ("@kw_key"  , cols[0]));
                    dbcmd2.Parameters.Add (new SqliteParameter ("@kw_rowid", rowid));
                    dbcmd2.ExecuteNonQuery ();
                } finally {
                    dbcmd2.Dispose ();
                }

                if (++ i == 1024) {
                    DoCommand (dbcon, "COMMIT; BEGIN;");
                    i = 0;
                }
            }
            csvrdr.Close ();

            /*
             * Load intersection info into database.
             */
            DoCommand (dbcon, "CREATE TABLE intersections (int_num INTEGER PRIMARY KEY, int_lat REAL NOT NULL, int_lon REAL NOT NULL, int_type TEXT NOT NULL);");

            csvrdr = new StreamReader ("datums/intersections_" + expdate + ".csv");
            while ((csv = csvrdr.ReadLine ()) != null) {
                string[] cols = QuotedCSVSplit (csv);

                IDbCommand dbcmd1 = dbcon.CreateCommand ();
                try {
                    dbcmd1.CommandText = "INSERT INTO intersections (int_num,int_lat,int_lon,int_type) " +
                            "VALUES (@int_nun,@int_lat,@int_lon,@int_type)";
                    dbcmd1.Parameters.Add (new SqliteParameter ("@int_nun" , int.Parse (cols[0])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@int_type", cols[1]));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@int_lat" , double.Parse (cols[2])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@int_lon" , double.Parse (cols[3])));
                    dbcmd1.ExecuteScalar ();
                } finally {
                    dbcmd1.Dispose ();
                }

                DefineWaypoint (cols[0], double.Parse (cols[2]), double.Parse (cols[3]));

                if (++ i == 1024) {
                    DoCommand (dbcon, "COMMIT; BEGIN;");
                    i = 0;
                }
            }
            csvrdr.Close ();

            /*
             * Load localizer info into database.
             */
            DoCommand (dbcon, "CREATE TABLE localizers (loc_type TEXT, loc_faaid TEXT, loc_elev REAL NOT NULL, loc_name TEXT NOT NULL, " +
                    "loc_aptfid TEXT NOT NULL, loc_rwyid TEXT NOT NULL, loc_freq INTEGER NOT NULL, " +
                    "loc_lat REAL NOT NULL, loc_lon REAL NOT NULL, loc_thdg REAL NOT NULL, gs_elev REAL, gs_tilt REAL, gs_lat REAL, gs_lon REAL, " +
                    "dme_elev REAL, dme_lat REAL, dme_lon REAL);");
            DoCommand (dbcon, "CREATE TABLE lockeys (kw_key TEXT NOT NULL, kw_rowid INTEGER NOT NULL);");
            DoCommand (dbcon, "CREATE INDEX localizers_faaid ON localizers (loc_faaid);");
            DoCommand (dbcon, "CREATE INDEX localizers_lats  ON localizers (loc_lat);");
            DoCommand (dbcon, "CREATE INDEX localizers_lons  ON localizers (loc_lon);");
            DoCommand (dbcon, "CREATE INDEX localizers_freqs ON localizers (loc_freq);");
            DoCommand (dbcon, "CREATE INDEX lockeys_keys ON lockeys (kw_key);");

            csvrdr = new StreamReader ("datums/localizers_" + expdate + ".csv");
            while ((csv = csvrdr.ReadLine ()) != null) {
                string[] cols = QuotedCSVSplit (csv);

                long rowid;
                IDbCommand dbcmd1 = dbcon.CreateCommand ();
                try {
                    dbcmd1.CommandText = "INSERT INTO localizers (loc_type,loc_faaid,loc_elev,loc_name,loc_lat,loc_lon,loc_thdg,gs_elev,gs_tilt,gs_lat,gs_lon,dme_elev,dme_lat,dme_lon,loc_aptfid,loc_rwyid,loc_freq) " +
                            "VALUES (@loc_type,@loc_faaid,@loc_elev,@loc_name,@loc_lat,@loc_lon,@loc_thdg,@gs_elev,@gs_tilt,@gs_lat,@gs_lon,@dme_elev,@dme_lat,@dme_lon,@loc_aptfid,@loc_rwyid,@loc_freq); SELECT last_insert_rowid ()";
                    dbcmd1.Parameters.Add (new SqliteParameter ("@loc_type",   cols[0]));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@loc_faaid",  cols[1]));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@loc_elev",   double.Parse (cols[2])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@loc_name",   cols[3]));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@loc_lat",    double.Parse (cols[4])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@loc_lon",    double.Parse (cols[5])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@loc_thdg",   cols[6]));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@gs_elev",    ParseElev (cols[7])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@gs_tilt",    ParseElev (cols[8])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@gs_lat",     ParseElev (cols[9])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@gs_lon",     ParseElev (cols[10])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@dme_elev",   ParseElev (cols[11])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@dme_lat",    ParseElev (cols[12])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@dme_lon",    ParseElev (cols[13])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@loc_aptfid", cols[14]));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@loc_rwyid",  cols[15]));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@loc_freq",   int.Parse (cols[16])));
                    rowid = (long) dbcmd1.ExecuteScalar ();
                } catch (Exception) {
                    Console.Error.WriteLine (csv);
                    throw;
                } finally {
                    dbcmd1.Dispose ();
                }

                IDbCommand dbcmd2 = dbcon.CreateCommand ();
                try {
                    dbcmd2.CommandText = "INSERT INTO lockeys (kw_key,kw_rowid) VALUES (@kw_key,@kw_rowid)";
                    dbcmd2.Parameters.Add (new SqliteParameter ("@kw_key"  , cols[1]));
                    dbcmd2.Parameters.Add (new SqliteParameter ("@kw_rowid", rowid));
                    dbcmd2.ExecuteNonQuery ();
                } finally {
                    dbcmd2.Dispose ();
                }

                if (cols[1].StartsWith ("I-")) {
                    IDbCommand dbcmd3 = dbcon.CreateCommand ();
                    try {
                        dbcmd3.CommandText = "INSERT INTO lockeys (kw_key,kw_rowid) VALUES (@kw_key,@kw_rowid)";
                        dbcmd3.Parameters.Add (new SqliteParameter ("@kw_key"  , "I" + cols[1].Substring (2)));
                        dbcmd3.Parameters.Add (new SqliteParameter ("@kw_rowid", rowid));
                        dbcmd3.ExecuteNonQuery ();
                    } finally {
                        dbcmd3.Dispose ();
                    }
                }

                if (++ i == 1024) {
                    DoCommand (dbcon, "COMMIT; BEGIN;");
                    i = 0;
                }
            }
            csvrdr.Close ();

            /*
             * Load navaid info into database.
             */
            DoCommand (dbcon, "CREATE TABLE navaids (nav_type TEXT NOT NULL, nav_faaid TEXT NOT NULL, nav_elev REAL NOT NULL, nav_name TEXT NOT NULL, " +
                    "nav_lat REAL NOT NULL, nav_lon REAL NOT NULL, nav_magvar INTEGER NOT NULL, nav_freq INTEGER, nav_power TEXT NOT NULL);");
            DoCommand (dbcon, "CREATE TABLE navkeys (kw_key TEXT NOT NULL, kw_rowid INTEGER NOT NULL);");
            DoCommand (dbcon, "CREATE INDEX navaids_faaid ON navaids (nav_faaid);");
            DoCommand (dbcon, "CREATE INDEX navaids_lats  ON navaids (nav_lat);");
            DoCommand (dbcon, "CREATE INDEX navaids_lons  ON navaids (nav_lon);");
            DoCommand (dbcon, "CREATE INDEX navaids_freqs ON navaids (nav_freq);");
            DoCommand (dbcon, "CREATE INDEX navkeys_keys  ON navkeys (kw_key);");

            csvrdr = new StreamReader ("datums/navaids_" + expdate + ".csv");
            while ((csv = csvrdr.ReadLine ()) != null) {
                string[] cols = QuotedCSVSplit (csv);

                long rowid;
                IDbCommand dbcmd1 = dbcon.CreateCommand ();
                try {
                    dbcmd1.CommandText = "INSERT INTO navaids (nav_type,nav_faaid,nav_elev,nav_name,nav_lat,nav_lon,nav_magvar,nav_freq,nav_power) " +
                            "VALUES (@nav_type,@nav_faaid,@nav_elev,@nav_name,@nav_lat,@nav_lon,@nav_magvar,@nav_freq,@nav_power); " +
                            "SELECT last_insert_rowid ()";
                    dbcmd1.Parameters.Add (new SqliteParameter ("@nav_type"  , cols[0]));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@nav_faaid" , cols[1]));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@nav_elev"  , double.Parse (cols[2])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@nav_name"  , cols[3]));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@nav_lat"   , double.Parse (cols[4])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@nav_lon"   , double.Parse (cols[5])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@nav_magvar", int.Parse (cols[6])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@nav_freq"  , (cols[7] == "") ? null : (object) int.Parse (cols[7])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@nav_power" , cols[8]));
                    rowid = (long) dbcmd1.ExecuteScalar ();
                } catch (Exception) {
                    Console.Error.WriteLine (csv);
                    throw;
                } finally {
                    dbcmd1.Dispose ();
                }

                DefineWaypoint (cols[1], double.Parse (cols[4]), double.Parse (cols[5]));

                InsertMultipleKeys (dbcon, "navkeys", cols[0] + " " + cols[1] + " " + cols[3], rowid);

                if (++ i == 1024) {
                    DoCommand (dbcon, "COMMIT; BEGIN;");
                    i = 0;
                }
            }
            csvrdr.Close ();

            /*
             * Load runway info into database.
             */
            DoCommand (dbcon, "CREATE TABLE runways (rwy_faaid TEXT NOT NULL, rwy_number TEXT NOT NULL, rwy_truehdg INTEGER, rwy_tdze REAL, " +
                    "rwy_beglat REAL NOT NULL, rwy_beglon REAL NOT NULL, rwy_endlat REAL NOT NULL, rwy_endlon REAL NOT NULL, rwy_ritraf TEXT NOT NULL);");
            DoCommand (dbcon, "CREATE INDEX runways_faaids  ON runways (rwy_faaid);");
            DoCommand (dbcon, "CREATE INDEX runways_beglats ON runways (rwy_beglat);");
            DoCommand (dbcon, "CREATE INDEX runways_beglons ON runways (rwy_beglon);");

            csvrdr = new StreamReader ("datums/runways_" + expdate + ".csv");
            while ((csv = csvrdr.ReadLine ()) != null) {
                string[] cols = QuotedCSVSplit (csv);

                IDbCommand dbcmd1 = dbcon.CreateCommand ();
                try {
                    dbcmd1.CommandText = "INSERT INTO runways (rwy_faaid,rwy_number,rwy_truehdg,rwy_tdze,rwy_beglat,rwy_beglon,rwy_endlat,rwy_endlon,rwy_ritraf) " +
                            "VALUES (@rwy_faaid,@rwy_number,@rwy_truehdg,@rwy_tdze,@rwy_beglat,@rwy_beglon,@rwy_endlat,@rwy_endlon,@rwy_ritraf);";
                    dbcmd1.Parameters.Add (new SqliteParameter ("@rwy_faaid",   cols[0]));  // eg, "BOS"
                    dbcmd1.Parameters.Add (new SqliteParameter ("@rwy_number",  cols[1]));  // eg, "04L"
                    dbcmd1.Parameters.Add (new SqliteParameter ("@rwy_truehdg", (cols[2] == "") ? null : (object) int.Parse (cols[2])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@rwy_tdze",    (cols[3] == "") ? null : (object) double.Parse (cols[3])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@rwy_beglat", double.Parse (cols[4])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@rwy_beglon", double.Parse (cols[5])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@rwy_endlat", double.Parse (cols[6])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@rwy_endlon", double.Parse (cols[7])));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@rwy_ritraf", cols[8]));
                    dbcmd1.ExecuteScalar ();
                } finally {
                    dbcmd1.Dispose ();
                }

                if (++ i == 1024) {
                    DoCommand (dbcon, "COMMIT; BEGIN;");
                    i = 0;
                }
            }
            csvrdr.Close ();
            DoCommand (dbcon, "COMMIT;");

            /*
             * Load airway info into database.
             * This must be last as we verify the waypoints in the other tables.
             */
            DoCommand (dbcon, "CREATE TABLE airways (awy_segid TEXT PRIMARY KEY, awy_wpident TEXT NOT NULL, awy_wptable TEXT NOT NULL, awy_wplat REAL NOT NULL, awy_wplon REAL NOT NULL, awy_copnm INTEGER);");

            string awyid = "";
            string awyregion = "";
            int awyseg = 0;
            string awysegfmt = "";
            csvrdr = new StreamReader ("datums/airways_" + expdate + ".csv");
            while ((csv = csvrdr.ReadLine ()) != null) {
                string[] cols = QuotedCSVSplit (csv);

                if (cols[0] != "") {
                    if (awyid != "") DoCommand (dbcon, "COMMIT;");
                    awyid     = cols[0];
                    awyregion = cols[1];
                    awysegfmt = "D" + cols[2].Length;
                    if (awyregion == "") awyregion = "-";
                    awyseg = 0;
                    DoCommand (dbcon, "BEGIN;");
                    continue;
                }

                if (awyid == "") continue;

                // get info about the waypoint along the airway
                string wpident = cols[1];
                double wplat = double.Parse (cols[3]);
                double wplon = double.Parse (cols[4]);

                // make sure we have that waypoint defined in the above tables and that its lat/lon matches
                LinkedList<Waypoint> waypoints;
                if (!waypointss.TryGetValue (wpident, out waypoints)) {
                    Console.Error.WriteLine ("airway " + awyid + " " + awyregion + " contains undefined waypoint " + wpident);
                    goto bad;
                }
                foreach (Waypoint wp in waypoints) {
                    double latdiff = wp.lat - wplat;
                    double londiff = wp.lon - wplon;
                    if (Math.Sqrt (latdiff * latdiff + londiff * londiff) < 1.0/60.0) goto good;
                }
                Console.Error.WriteLine ("airway " + awyid + " " + awyregion + " contains far away waypoint " + wpident);
            bad:
                DoCommand (dbcon, "ROLLBACK;");
                awyid = "";
                continue;
            good:

                // write airway point to database
                ++ awyseg;
                IDbCommand dbcmd1 = dbcon.CreateCommand ();
                try {
                    dbcmd1.CommandText = "INSERT INTO airways (awy_segid,awy_wpident,awy_wptable,awy_wplat,awy_wplon,awy_copnm) " +
                            "VALUES (@awy_segid,@awy_wpident,@awy_wptable,@awy_wplat,@awy_wplon,@awy_copnm)";
                    dbcmd1.Parameters.Add (new SqliteParameter ("@awy_segid"  , awyid + " " + awyregion + " " + awyseg.ToString (awysegfmt)));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@awy_wpident", wpident));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@awy_wptable", cols[2]));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@awy_wplat"  , wplat));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@awy_wplon"  , wplon));
                    dbcmd1.Parameters.Add (new SqliteParameter ("@awy_copnm"  , (cols[5] == "") ? null : (object) int.Parse (cols[5])));
                    dbcmd1.ExecuteScalar ();
                } finally {
                    dbcmd1.Dispose ();
                }
            }
            csvrdr.Close ();

            if (awyid != "") DoCommand (dbcon, "COMMIT;");
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

    private static string[] QuotedCSVSplit (string line)
    {
        int len = line.Length;
        List<string> cols = new List<string> (len + 1);
        bool quoted = false;
        bool escapd = false;
        StringBuilder sb = new StringBuilder (len);
        for (int i = 0;; i ++) {
            char c = (char) 0;
            if (i < len) c = line[i];
            if (!escapd && (c == '"')) {
                quoted = !quoted;
                continue;
            }
            if (!escapd && (c == '\\')) {
                escapd = true;
                continue;
            }
            if ((!escapd && !quoted && (c == ',')) || (c == 0)) {
                cols.Add (sb.ToString ());
                if (c == 0) break;
                sb.Remove (0, sb.Length);
                continue;
            }
            if (escapd && (c == 'n')) c = '\n';
            sb.Append (c);
            escapd = false;
        }
        return cols.ToArray ();
    }

    private static object ParseElev (string s)
    {
        if (s == "") return null;
        return (object) double.Parse (s);
    }

    private static void InsertMultipleKeys (IDbConnection dbcon, string table, string descrip, long rowid)
    {
        // matches NormalizeKey in client Waypoint.java
        StringBuilder sb = new StringBuilder (descrip.Length);
        foreach (char c in descrip) {
            if (c == '\n') break;
            if ((c >= 'a') && (c <= 'z')) {
                char u = (char) (c - 'a' - 'A');
                sb.Append (u);
                continue;
            }
            if (((c >= '0') && (c <= '9')) ||
                ((c >= 'A') && (c <= 'Z')) ||
                (c == '_')) {
                sb.Append (c);
                continue;
            }
            sb.Append (' ');
        }
        string[] keys = sb.ToString ().Split (' ');
        int len = keys.Length;
        for (int i = 0; i < len; i ++) {
            string key = keys[i];
            if (key == "") continue;
            int j;
            for (j = 0; j < i; j ++) {
                if (keys[j] == key) break;
            }
            if (j >= i) {
                IDbCommand dbcmd2 = dbcon.CreateCommand ();
                try {
                    dbcmd2.CommandText = "INSERT INTO " + table + " (kw_key,kw_rowid) VALUES (@kw_key,@kw_rowid)";
                    dbcmd2.Parameters.Add (new SqliteParameter ("@kw_key"  , key));
                    dbcmd2.Parameters.Add (new SqliteParameter ("@kw_rowid", rowid));
                    dbcmd2.ExecuteNonQuery ();
                } finally {
                    dbcmd2.Dispose ();
                }
            }
        }
    }

    private static void DefineWaypoint (string ident, double lat, double lon)
    {
        LinkedList<Waypoint> waypoints;
        if (!waypointss.TryGetValue (ident, out waypoints)) {
            waypoints = new LinkedList<Waypoint> ();
            waypointss[ident] = waypoints;
        }
        Waypoint waypoint = new Waypoint ();
        waypoint.ident = ident;
        waypoint.lat = lat;
        waypoint.lon = lon;
        waypoints.AddLast (waypoint);
    }
}
