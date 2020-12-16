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
// mono --debug MakeWaypoints.exe 20150820 datums/waypoints_20150820.db [1 for abbr database used by HSIWatch] [1 for ourairports database]

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

    // add the waypoint to in-memory database (used to find waypoints along airways)
    public void DefineWaypoint ()
    {
        LinkedList<Waypoint> waypoints;
        if (!MakeWaypoints.waypointss.TryGetValue (ident, out waypoints)) {
            waypoints = new LinkedList<Waypoint> ();
            MakeWaypoints.waypointss[ident] = waypoints;
        }
        waypoints.AddLast (this);
    }
}

// an airport waypoint
public class Airport : Waypoint {
    public String faaid;
    public double elev;
    public String name;
    public StringBuilder desc = new StringBuilder ();
    public String state;
    public String faciluse;
    public String metaf;
    public String tzname;

    public static char[] linesplit = new char[] { '\n' };
    public static string[] abbinfos = {
        "Elev:",
        "APCH/P",
        "ATIS:",
        "AWOS:",
        "CENTER",
        "CTAF:",
        "DEP/P",
        "GND/P",
        "Rwy",
        "TWR/P",
        "UNICOM:"
    };

    // create database table
    public static void CreateTable (IDbConnection dbcon)
    {
        if (MakeWaypoints.abbrdb) {
            MakeWaypoints.DoCommand (dbcon, "CREATE TABLE airports (apt_icaoid TEXT PRIMARY KEY, apt_faaid TEXT NOT NULL, apt_elev REAL NOT NULL, apt_name TEXT NOT NULL, " +
                    "apt_lat REAL NOT NULL, apt_lon REAL NOT NULL, apt_desc1 TEXT NOT NULL, apt_desc2 TEXT NOT NULL);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX airports_faaid ON airports (apt_faaid);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX airports_lats  ON airports (apt_lat);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX airports_lons  ON airports (apt_lon);");
        } else {
            MakeWaypoints.DoCommand (dbcon, "CREATE TABLE airports (apt_icaoid TEXT PRIMARY KEY, apt_faaid TEXT NOT NULL, apt_elev REAL NOT NULL, apt_name TEXT NOT NULL, " +
                    "apt_lat REAL NOT NULL, apt_lon REAL NOT NULL, apt_desc TEXT NOT NULL, apt_state TEXT NOT NULL, apt_faciluse TEXT NOT NULL, " +
                    "apt_metaf TEXT NOT NULL, apt_tzname TEXT NOT NULL);");
            MakeWaypoints.DoCommand (dbcon, "CREATE TABLE aptkeys  (kw_key TEXT NOT NULL, kw_rowid INTEGER NOT NULL);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX airports_faaid ON airports (apt_faaid);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX airports_lats  ON airports (apt_lat);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX airports_lons  ON airports (apt_lon);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX aptkeys_keys   ON aptkeys  (kw_key);");
        }
    }

    // write database record
    public void WriteRecord (IDbConnection dbcon)
    {
        IDbCommand dbcmd1 = dbcon.CreateCommand ();
        try {
            if (MakeWaypoints.abbrdb) {
                dbcmd1.CommandText = "INSERT INTO airports (apt_icaoid,apt_faaid,apt_elev,apt_name,apt_lat,apt_lon,apt_desc1,apt_desc2) " +
                        "VALUES (@apt_icaoid,@apt_faaid,@apt_elev,@apt_name,@apt_lat,@apt_lon,@apt_desc1,@apt_desc2); " +
                        "SELECT last_insert_rowid ()";
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_icaoid"  , ident));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_faaid"   , faaid));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_elev"    , elev));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_name"    , name));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_lat"     , lat));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_lon"     , lon));
                string[] infos = desc.ToString ().Split (linesplit);
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_desc1"   , infos[0]));
                StringBuilder sb = new StringBuilder ();
                if (MakeWaypoints.ourapt) {
                    int n = 0;
                    foreach (string info in infos) {
                        if (++ n < 2) continue;
                        sb.Append ('\n');
                        sb.Append (info);
                    }
                } else {
                    foreach (string info in infos) {
                        foreach (string abbinfo in abbinfos) {
                            if (info.StartsWith (abbinfo)) {
                                sb.Append ('\n');
                                sb.Append (info);
                            }
                        }
                    }
                }
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_desc2"   , sb.ToString ().Trim ()));
            } else {
                dbcmd1.CommandText = "INSERT INTO airports (apt_icaoid,apt_faaid,apt_elev,apt_name,apt_lat,apt_lon,apt_desc,apt_state,apt_faciluse,apt_metaf,apt_tzname) " +
                        "VALUES (@apt_icaoid,@apt_faaid,@apt_elev,@apt_name,@apt_lat,@apt_lon,@apt_desc,@apt_state,@apt_faciluse,@apt_metaf,@apt_tzname); " +
                        "SELECT last_insert_rowid ()";
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_icaoid"  , ident));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_faaid"   , faaid));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_elev"    , elev));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_name"    , name));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_lat"     , lat));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_lon"     , lon));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_desc"    , desc.ToString ()));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_state"   , state));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_faciluse", faciluse));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_metaf"   , metaf));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_tzname"  , tzname));
            }
            long rowid = (long) dbcmd1.ExecuteScalar ();

            if (! MakeWaypoints.abbrdb) {
                MakeWaypoints.InsertMultipleKeys (dbcon, "aptkeys", ident + " " + faaid + " " + name + " " + desc, rowid);
                DefineWaypoint ();
            }
        } finally {
            dbcmd1.Dispose ();
        }
    }
}

// an fix waypoint
public class Fix : Waypoint {
    public String desc;

    // create database table
    public static void CreateTable (IDbConnection dbcon)
    {
        MakeWaypoints.DoCommand (dbcon, "CREATE TABLE fixes (fix_name TEXT NOT NULL, fix_lat REAL NOT NULL, fix_lon REAL NOT NULL, fix_desc TEXT NOT NULL);");
        if (MakeWaypoints.abbrdb) {
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX fixes_name ON fixes (fix_name);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX fixes_lats ON fixes (fix_lat);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX fixes_lons ON fixes (fix_lon);");
        } else {
            MakeWaypoints.DoCommand (dbcon, "CREATE TABLE fixkeys (kw_key TEXT NOT NULL, kw_rowid INTEGER NOT NULL);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX fixes_name ON fixes (fix_name);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX fixes_lats ON fixes (fix_lat);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX fixes_lons ON fixes (fix_lon);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX fixkeys_keys ON fixkeys (kw_key);");
        }
    }

    // write database record
    // also add to airway waypoint list
    public void WriteRecord (IDbConnection dbcon)
    {
        IDbCommand dbcmd1 = dbcon.CreateCommand ();
        try {
            dbcmd1.CommandText = "INSERT INTO fixes (fix_name,fix_lat,fix_lon,fix_desc) " +
                    "VALUES (@fix_name,@fix_lat,@fix_lon,@fix_desc); SELECT last_insert_rowid ()";
            dbcmd1.Parameters.Add (new SqliteParameter ("@fix_name", ident));
            dbcmd1.Parameters.Add (new SqliteParameter ("@fix_lat" , lat));
            dbcmd1.Parameters.Add (new SqliteParameter ("@fix_lon" , lon));
            dbcmd1.Parameters.Add (new SqliteParameter ("@fix_desc", desc));
            long rowid = (long) dbcmd1.ExecuteScalar ();

            if (! MakeWaypoints.abbrdb) {
                IDbCommand dbcmd2 = dbcon.CreateCommand ();
                try {
                    dbcmd2.CommandText = "INSERT INTO fixkeys (kw_key,kw_rowid) VALUES (@kw_key,@kw_rowid)";
                    dbcmd2.Parameters.Add (new SqliteParameter ("@kw_key"  , ident));
                    dbcmd2.Parameters.Add (new SqliteParameter ("@kw_rowid", rowid));
                    dbcmd2.ExecuteNonQuery ();
                } finally {
                    dbcmd2.Dispose ();
                }

                DefineWaypoint ();
            }
        } finally {
            dbcmd1.Dispose ();
        }
    }
}

// an localizer waypoint
public class Localizer : Waypoint {
    public String loc_type;
    public object loc_elev;
    public String loc_name;
    public double loc_thdg;
    public object gs_elev;
    public object gs_tilt;
    public object gs_lat;
    public object gs_lon;
    public object dme_elev;
    public object dme_lat;
    public object dme_lon;
    public String loc_aptfid;
    public String loc_rwyid;
    public int    loc_freq;

    // create database table
    public static void CreateTable (IDbConnection dbcon)
    {
        if (MakeWaypoints.abbrdb) {
            MakeWaypoints.DoCommand (dbcon, "CREATE TABLE localizers (loc_type TEXT NOT NULL, loc_faaid TEXT NOT NULL, loc_elev REAL, " +
                    "loc_aptfid TEXT NOT NULL, loc_rwyid TEXT NOT NULL, loc_freq INTEGER NOT NULL, " +
                    "loc_lat REAL NOT NULL, loc_lon REAL NOT NULL, loc_thdg REAL NOT NULL, " +
                    "gs_elev REAL, gs_tilt REAL, gs_lat REAL, gs_lon REAL, " +
                    "dme_elev REAL, dme_lat REAL, dme_lon REAL);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX localizers_faaid ON localizers (loc_faaid);");
        } else {
            MakeWaypoints.DoCommand (dbcon, "CREATE TABLE localizers (loc_type TEXT, loc_faaid TEXT, loc_elev REAL, loc_name TEXT NOT NULL, " +
                    "loc_aptfid TEXT NOT NULL, loc_rwyid TEXT NOT NULL, loc_freq INTEGER NOT NULL, " +
                    "loc_lat REAL NOT NULL, loc_lon REAL NOT NULL, loc_thdg REAL NOT NULL, " +
                    "gs_elev REAL, gs_tilt REAL, gs_lat REAL, gs_lon REAL, " +
                    "dme_elev REAL, dme_lat REAL, dme_lon REAL);");
            MakeWaypoints.DoCommand (dbcon, "CREATE TABLE lockeys (kw_key TEXT NOT NULL, kw_rowid INTEGER NOT NULL);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX localizers_faaid ON localizers (loc_faaid);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX localizers_lats  ON localizers (loc_lat);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX localizers_lons  ON localizers (loc_lon);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX localizers_freqs ON localizers (loc_freq);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX lockeys_keys ON lockeys (kw_key);");
        }
    }

    // write database record
    public void WriteRecord (IDbConnection dbcon)
    {
        IDbCommand dbcmd1 = dbcon.CreateCommand ();
        try {
            if (MakeWaypoints.abbrdb) {
                dbcmd1.CommandText = "INSERT INTO localizers (loc_type,loc_faaid,loc_elev,loc_lat,loc_lon,loc_thdg,gs_elev,gs_tilt,gs_lat,gs_lon,dme_elev,dme_lat,dme_lon,loc_aptfid,loc_rwyid,loc_freq) " +
                        "VALUES (@loc_type,@loc_faaid,@loc_elev,@loc_lat,@loc_lon,@loc_thdg,@gs_elev,@gs_tilt,@gs_lat,@gs_lon,@dme_elev,@dme_lat,@dme_lon,@loc_aptfid,@loc_rwyid,@loc_freq); SELECT last_insert_rowid ()";
                dbcmd1.Parameters.Add (new SqliteParameter ("@loc_type",   loc_type));
                dbcmd1.Parameters.Add (new SqliteParameter ("@loc_faaid",  ident));
                dbcmd1.Parameters.Add (new SqliteParameter ("@loc_elev",   loc_elev));
                dbcmd1.Parameters.Add (new SqliteParameter ("@loc_lat",    lat));
                dbcmd1.Parameters.Add (new SqliteParameter ("@loc_lon",    lon));
                dbcmd1.Parameters.Add (new SqliteParameter ("@loc_thdg",   loc_thdg));
                dbcmd1.Parameters.Add (new SqliteParameter ("@gs_elev",    gs_elev));
                dbcmd1.Parameters.Add (new SqliteParameter ("@gs_tilt",    gs_tilt));
                dbcmd1.Parameters.Add (new SqliteParameter ("@gs_lat",     gs_lat));
                dbcmd1.Parameters.Add (new SqliteParameter ("@gs_lon",     gs_lon));
                dbcmd1.Parameters.Add (new SqliteParameter ("@dme_elev",   dme_elev));
                dbcmd1.Parameters.Add (new SqliteParameter ("@dme_lat",    dme_lat));
                dbcmd1.Parameters.Add (new SqliteParameter ("@dme_lon",    dme_lon));
                dbcmd1.Parameters.Add (new SqliteParameter ("@loc_aptfid", loc_aptfid));
                dbcmd1.Parameters.Add (new SqliteParameter ("@loc_rwyid",  loc_rwyid));
                dbcmd1.Parameters.Add (new SqliteParameter ("@loc_freq",   loc_freq));
            } else {
                dbcmd1.CommandText = "INSERT INTO localizers (loc_type,loc_faaid,loc_elev,loc_name,loc_lat,loc_lon,loc_thdg,gs_elev,gs_tilt,gs_lat,gs_lon,dme_elev,dme_lat,dme_lon,loc_aptfid,loc_rwyid,loc_freq) " +
                        "VALUES (@loc_type,@loc_faaid,@loc_elev,@loc_name,@loc_lat,@loc_lon,@loc_thdg,@gs_elev,@gs_tilt,@gs_lat,@gs_lon,@dme_elev,@dme_lat,@dme_lon,@loc_aptfid,@loc_rwyid,@loc_freq); SELECT last_insert_rowid ()";
                dbcmd1.Parameters.Add (new SqliteParameter ("@loc_type",   loc_type));
                dbcmd1.Parameters.Add (new SqliteParameter ("@loc_faaid",  ident));
                dbcmd1.Parameters.Add (new SqliteParameter ("@loc_elev",   loc_elev));
                dbcmd1.Parameters.Add (new SqliteParameter ("@loc_name",   loc_name));
                dbcmd1.Parameters.Add (new SqliteParameter ("@loc_lat",    lat));
                dbcmd1.Parameters.Add (new SqliteParameter ("@loc_lon",    lon));
                dbcmd1.Parameters.Add (new SqliteParameter ("@loc_thdg",   loc_thdg));
                dbcmd1.Parameters.Add (new SqliteParameter ("@gs_elev",    gs_elev));
                dbcmd1.Parameters.Add (new SqliteParameter ("@gs_tilt",    gs_tilt));
                dbcmd1.Parameters.Add (new SqliteParameter ("@gs_lat",     gs_lat));
                dbcmd1.Parameters.Add (new SqliteParameter ("@gs_lon",     gs_lon));
                dbcmd1.Parameters.Add (new SqliteParameter ("@dme_elev",   dme_elev));
                dbcmd1.Parameters.Add (new SqliteParameter ("@dme_lat",    dme_lat));
                dbcmd1.Parameters.Add (new SqliteParameter ("@dme_lon",    dme_lon));
                dbcmd1.Parameters.Add (new SqliteParameter ("@loc_aptfid", loc_aptfid));
                dbcmd1.Parameters.Add (new SqliteParameter ("@loc_rwyid",  loc_rwyid));
                dbcmd1.Parameters.Add (new SqliteParameter ("@loc_freq",   loc_freq));
            }
            long rowid = (long) dbcmd1.ExecuteScalar ();

            if (! MakeWaypoints.abbrdb) {
                IDbCommand dbcmd2 = dbcon.CreateCommand ();
                try {
                    dbcmd2.CommandText = "INSERT INTO lockeys (kw_key,kw_rowid) VALUES (@kw_key,@kw_rowid)";
                    dbcmd2.Parameters.Add (new SqliteParameter ("@kw_key"  , ident));
                    dbcmd2.Parameters.Add (new SqliteParameter ("@kw_rowid", rowid));
                    dbcmd2.ExecuteNonQuery ();
                } finally {
                    dbcmd2.Dispose ();
                }

                if (ident.StartsWith ("I-")) {
                    IDbCommand dbcmd3 = dbcon.CreateCommand ();
                    try {
                        dbcmd3.CommandText = "INSERT INTO lockeys (kw_key,kw_rowid) VALUES (@kw_key,@kw_rowid)";
                        dbcmd3.Parameters.Add (new SqliteParameter ("@kw_key"  , "I" + ident.Substring (2)));
                        dbcmd3.Parameters.Add (new SqliteParameter ("@kw_rowid", rowid));
                        dbcmd3.ExecuteNonQuery ();
                    } finally {
                        dbcmd3.Dispose ();
                    }
                }
            }
        } finally {
            dbcmd1.Dispose ();
        }
    }
}

// an navaid waypoint
public class Navaid : Waypoint {
    public String nav_type;
    public double nav_elev;
    public String nav_name;
    public int    nav_magvar;
    public object nav_freq;
    public String nav_power;

    // create database table
    public static void CreateTable (IDbConnection dbcon)
    {
        if (MakeWaypoints.abbrdb) {
            MakeWaypoints.DoCommand (dbcon, "CREATE TABLE navaids (nav_type TEXT NOT NULL, nav_faaid TEXT NOT NULL, nav_elev REAL NOT NULL, nav_name TEXT NOT NULL, " +
                    "nav_lat REAL NOT NULL, nav_lon REAL NOT NULL, nav_magvar INTEGER NOT NULL);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX navaids_faaid ON navaids (nav_faaid);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX navaids_lats  ON navaids (nav_lat);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX navaids_lons  ON navaids (nav_lon);");
        } else {
            MakeWaypoints.DoCommand (dbcon, "CREATE TABLE navaids (nav_type TEXT NOT NULL, nav_faaid TEXT NOT NULL, nav_elev REAL NOT NULL, nav_name TEXT NOT NULL, " +
                    "nav_lat REAL NOT NULL, nav_lon REAL NOT NULL, nav_magvar INTEGER NOT NULL, nav_freq INTEGER, nav_power TEXT NOT NULL);");
            MakeWaypoints.DoCommand (dbcon, "CREATE TABLE navkeys (kw_key TEXT NOT NULL, kw_rowid INTEGER NOT NULL);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX navaids_faaid ON navaids (nav_faaid);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX navaids_lats  ON navaids (nav_lat);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX navaids_lons  ON navaids (nav_lon);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX navaids_freqs ON navaids (nav_freq);");
            MakeWaypoints.DoCommand (dbcon, "CREATE INDEX navkeys_keys  ON navkeys (kw_key);");
        }
    }

    // write database record
    // also add to airway waypoint list
    public void WriteRecord (IDbConnection dbcon)
    {
        IDbCommand dbcmd1 = dbcon.CreateCommand ();
        try {
            if (MakeWaypoints.abbrdb) {
                dbcmd1.CommandText = "INSERT INTO navaids (nav_type,nav_faaid,nav_elev,nav_name,nav_lat,nav_lon,nav_magvar) " +
                        "VALUES (@nav_type,@nav_faaid,@nav_elev,@nav_name,@nav_lat,@nav_lon,@nav_magvar); " +
                        "SELECT last_insert_rowid ()";
                dbcmd1.Parameters.Add (new SqliteParameter ("@nav_type"  , nav_type));
                dbcmd1.Parameters.Add (new SqliteParameter ("@nav_faaid" , ident));
                dbcmd1.Parameters.Add (new SqliteParameter ("@nav_elev"  , nav_elev));
                dbcmd1.Parameters.Add (new SqliteParameter ("@nav_name"  , nav_name));
                dbcmd1.Parameters.Add (new SqliteParameter ("@nav_lat"   , lat));
                dbcmd1.Parameters.Add (new SqliteParameter ("@nav_lon"   , lon));
                dbcmd1.Parameters.Add (new SqliteParameter ("@nav_magvar", nav_magvar));
            } else {
                dbcmd1.CommandText = "INSERT INTO navaids (nav_type,nav_faaid,nav_elev,nav_name,nav_lat,nav_lon,nav_magvar,nav_freq,nav_power) " +
                        "VALUES (@nav_type,@nav_faaid,@nav_elev,@nav_name,@nav_lat,@nav_lon,@nav_magvar,@nav_freq,@nav_power); " +
                        "SELECT last_insert_rowid ()";
                dbcmd1.Parameters.Add (new SqliteParameter ("@nav_type"  , nav_type));
                dbcmd1.Parameters.Add (new SqliteParameter ("@nav_faaid" , ident));
                dbcmd1.Parameters.Add (new SqliteParameter ("@nav_elev"  , nav_elev));
                dbcmd1.Parameters.Add (new SqliteParameter ("@nav_name"  , nav_name));
                dbcmd1.Parameters.Add (new SqliteParameter ("@nav_lat"   , lat));
                dbcmd1.Parameters.Add (new SqliteParameter ("@nav_lon"   , lon));
                dbcmd1.Parameters.Add (new SqliteParameter ("@nav_magvar", nav_magvar));
                dbcmd1.Parameters.Add (new SqliteParameter ("@nav_freq"  , nav_freq));
                dbcmd1.Parameters.Add (new SqliteParameter ("@nav_power" , nav_power));
            }
            long rowid = (long) dbcmd1.ExecuteScalar ();

            if (! MakeWaypoints.abbrdb) {
                MakeWaypoints.InsertMultipleKeys (dbcon, "navkeys", nav_type + " " + ident + " " + nav_name, rowid);
                DefineWaypoint ();
            }
        } finally {
            dbcmd1.Dispose ();
        }
    }
}

// an runway (just one half of it)
public class Runway {
    public String rwy_faaid;
    public String rwy_number;
    public object rwy_truehdg;
    public object rwy_tdze;
    public double rwy_beglat;
    public double rwy_beglon;
    public double rwy_endlat;
    public double rwy_endlon;
    public String rwy_ritraf;
    public int    rwy_length;
    public int    rwy_width;

    // create database table
    public static void CreateTable (IDbConnection dbcon)
    {
        MakeWaypoints.DoCommand (dbcon, "CREATE TABLE runways (rwy_faaid TEXT NOT NULL, rwy_number TEXT NOT NULL, rwy_truehdg INTEGER, rwy_tdze REAL, " +
                "rwy_beglat REAL NOT NULL, rwy_beglon REAL NOT NULL, rwy_endlat REAL NOT NULL, rwy_endlon REAL NOT NULL, " +
                "rwy_ritraf TEXT NOT NULL, rwy_length INTEGER NOT NULL, rwy_width INTEGER NOT NULL);");
        MakeWaypoints.DoCommand (dbcon, "CREATE INDEX runways_faaids  ON runways (rwy_faaid);");
        MakeWaypoints.DoCommand (dbcon, "CREATE INDEX runways_beglats ON runways (rwy_beglat);");
        MakeWaypoints.DoCommand (dbcon, "CREATE INDEX runways_beglons ON runways (rwy_beglon);");
    }

    // write database record
    public void WriteRecord (IDbConnection dbcon)
    {
        IDbCommand dbcmd1 = dbcon.CreateCommand ();
        try {
            dbcmd1.CommandText = "INSERT INTO runways (rwy_faaid,rwy_number,rwy_truehdg,rwy_tdze,rwy_beglat,rwy_beglon,rwy_endlat,rwy_endlon,rwy_ritraf,rwy_length,rwy_width) " +
                    "VALUES (@rwy_faaid,@rwy_number,@rwy_truehdg,@rwy_tdze,@rwy_beglat,@rwy_beglon,@rwy_endlat,@rwy_endlon,@rwy_ritraf,@rwy_length,@rwy_width)";
            dbcmd1.Parameters.Add (new SqliteParameter ("@rwy_faaid",   rwy_faaid));    // eg, "BOS"
            dbcmd1.Parameters.Add (new SqliteParameter ("@rwy_number",  rwy_number));   // eg, "04L"
            dbcmd1.Parameters.Add (new SqliteParameter ("@rwy_truehdg", rwy_truehdg));
            dbcmd1.Parameters.Add (new SqliteParameter ("@rwy_tdze",    rwy_tdze));
            dbcmd1.Parameters.Add (new SqliteParameter ("@rwy_beglat",  rwy_beglat));
            dbcmd1.Parameters.Add (new SqliteParameter ("@rwy_beglon",  rwy_beglon));
            dbcmd1.Parameters.Add (new SqliteParameter ("@rwy_endlat",  rwy_endlat));
            dbcmd1.Parameters.Add (new SqliteParameter ("@rwy_endlon",  rwy_endlon));
            dbcmd1.Parameters.Add (new SqliteParameter ("@rwy_ritraf",  rwy_ritraf));   // "Y" or "N"
            dbcmd1.Parameters.Add (new SqliteParameter ("@rwy_length",  rwy_length));
            dbcmd1.Parameters.Add (new SqliteParameter ("@rwy_width",   rwy_width));
            dbcmd1.ExecuteScalar ();
        } finally {
            dbcmd1.Dispose ();
        }
    }
}

// main program
public class MakeWaypoints {
    public static bool abbrdb;
    public static bool ourapt;
    public static Dictionary<string,LinkedList<Waypoint>> waypointss = new Dictionary<string,LinkedList<Waypoint>> ();
    public static string dbname;
    public static string expdate;

    public static void Main (string[] args)
    {
        expdate = args[0];
        dbname  = args[1];
        abbrdb  = (args.Length > 2) && ((args[2][0] & 1) != 0);
        ourapt  = (args.Length > 3) && ((args[3][0] & 1) != 0);

        File.Delete (dbname);
        IDbConnection dbcon = new SqliteConnection ("URI=file:" + dbname);
        try {
            dbcon.Open ();

            /*
             * Create various tables.
             */
            Airport.CreateTable (dbcon);
            Fix.CreateTable (dbcon);
            Localizer.CreateTable (dbcon);
            Navaid.CreateTable (dbcon);
            Runway.CreateTable (dbcon);
            if (! abbrdb) {
                DoCommand (dbcon, "CREATE TABLE airways (awy_segid TEXT PRIMARY KEY, awy_wpident TEXT NOT NULL, awy_wptable TEXT NOT NULL, awy_wplat REAL NOT NULL, awy_wplon REAL NOT NULL, awy_copnm INTEGER);");
                DoCommand (dbcon, "CREATE TABLE intersections (int_num INTEGER PRIMARY KEY, int_lat REAL NOT NULL, int_lon REAL NOT NULL, int_type TEXT NOT NULL);");
            }

            if (ourapt) {
                WriteOA (dbcon);
            } else {
                WriteFAA (dbcon);
            }
        } finally {
            dbcon.Close ();
        }
    }

    /**
     * Write database from data sourced from FAA.
     *  Input:
     *   datums/airports_<expdate>.csv
     *   datums/airways_<expdate>.csv
     *   datums/fixes_<expdate>.csv
     *   datums/intersections_<expdate>.csv
     *   datums/localizers_<expdate>.csv
     *   datums/navaids_<expdate>.csv
     *   datums/runways_<expdate>.csv
     */
    public static void WriteFAA (IDbConnection dbcon)
    {
        int i = 0;
        StreamReader csvrdr;
        string csv;

        DoCommand (dbcon, "BEGIN;");

        /*
         * Load airport info into database.
         */
        csvrdr = new StreamReader ("datums/airports_" + expdate + ".csv");
        while ((csv = csvrdr.ReadLine ()) != null) {
            Airport airport = new Airport ();

            string[] cols = QuotedCSVSplit (csv);

            airport.ident    = cols[0];
            airport.faaid    = cols[1];
            airport.elev     = double.Parse (cols[2]);
            airport.name     = cols[3];
            airport.lat      = double.Parse (cols[4]);
            airport.lon      = double.Parse (cols[5]);
            airport.desc.Append (cols[7]);
            airport.state    = cols[8];
            airport.faciluse = cols[9];
            airport.metaf    = cols[10];
            airport.tzname   = cols[11];

            airport.WriteRecord (dbcon);

            if (++ i == 1024) {
                DoCommand (dbcon, "COMMIT; BEGIN;");
                i = 0;
            }
        }
        csvrdr.Close ();

        /*
         * Load fix info into database.
         */
        csvrdr = new StreamReader ("datums/fixes_" + expdate + ".csv");
        while ((csv = csvrdr.ReadLine ()) != null) {
            Fix fix = new Fix ();

            string[] cols = QuotedCSVSplit (csv);

            fix.ident = cols[0];
            fix.lat   = double.Parse (cols[1]);
            fix.lon   = double.Parse (cols[2]);
            fix.desc  = cols[3] + " " + cols[4];

            fix.WriteRecord (dbcon);

            if (++ i == 1024) {
                DoCommand (dbcon, "COMMIT; BEGIN;");
                i = 0;
            }
        }
        csvrdr.Close ();

        /*
         * Load runway info into database.
         */
        csvrdr = new StreamReader ("datums/runways_" + expdate + ".csv");
        while ((csv = csvrdr.ReadLine ()) != null) {
            Runway runway = new Runway ();

            string[] cols = QuotedCSVSplit (csv);

            runway.rwy_faaid   = cols[0];                   // eg, "BOS"
            runway.rwy_number  = cols[1];                   // eg, "04L"
            runway.rwy_truehdg = (cols[2] == "") ? null : (object) int.Parse (cols[2]);
            runway.rwy_tdze    = (cols[3] == "") ? null : (object) double.Parse (cols[3]);
            runway.rwy_beglat  = double.Parse (cols[4]);
            runway.rwy_beglon  = double.Parse (cols[5]);
            runway.rwy_endlat  = double.Parse (cols[6]);
            runway.rwy_endlon  = double.Parse (cols[7]);
            runway.rwy_ritraf  = cols[8];                   // "Y" or "N"
            runway.rwy_length  = int.Parse (cols[ 9]);
            runway.rwy_width   = int.Parse (cols[10]);

            runway.WriteRecord (dbcon);

            if (++ i == 1024) {
                DoCommand (dbcon, "COMMIT; BEGIN;");
                i = 0;
            }
        }
        csvrdr.Close ();

        /*
         * Load localizer info into database.
         */
        csvrdr = new StreamReader ("datums/localizers_" + expdate + ".csv");
        while ((csv = csvrdr.ReadLine ()) != null) {
            Localizer localizer = new Localizer ();

            string[] cols = QuotedCSVSplit (csv);

            localizer.loc_type   = cols[0];                     // "ILS", "LOC/DME", "MM", "OM" etc
            localizer.ident      = cols[1];                     // "I-BVY"
            localizer.loc_elev   = ParseElev (cols[2]);         // feet
            localizer.loc_name   = cols[3];                     // "BEVERLY RGNL rwy 16 - 110.50 - BEVERLY, MASSACHUSETTS"
            localizer.lat        = double.Parse (cols[4]);
            localizer.lon        = double.Parse (cols[5]);
            localizer.loc_thdg   = (cols[6] == "") ? 0.0 : double.Parse (cols[6]);  // missing for "MM", "OM"
            localizer.gs_elev    = ParseElev (cols[7]);
            localizer.gs_tilt    = ParseElev (cols[8]);
            localizer.gs_lat     = ParseElev (cols[9]);
            localizer.gs_lon     = ParseElev (cols[10]);
            localizer.dme_elev   = ParseElev (cols[11]);
            localizer.dme_lat    = ParseElev (cols[12]);
            localizer.dme_lon    = ParseElev (cols[13]);
            localizer.loc_aptfid = cols[14];                    // "BVY"
            localizer.loc_rwyid  = cols[15];                    // "16"
            localizer.loc_freq   = int.Parse (cols[16]);        // 110500 KHz

            localizer.WriteRecord (dbcon);

            if (++ i == 1024) {
                DoCommand (dbcon, "COMMIT; BEGIN;");
                i = 0;
            }
        }
        csvrdr.Close ();

        /*
         * Load navaid info into database.
         */
        csvrdr = new StreamReader ("datums/navaids_" + expdate + ".csv");
        while ((csv = csvrdr.ReadLine ()) != null) {
            Navaid navaid = new Navaid ();

            string[] cols = QuotedCSVSplit (csv);

            navaid.nav_type   = cols[0];
            navaid.ident      = cols[1];
            navaid.nav_elev   = double.Parse (cols[2]);
            navaid.nav_name   = cols[3];
            navaid.lat        = double.Parse (cols[4]);
            navaid.lon        = double.Parse (cols[5]);
            navaid.nav_magvar = int.Parse (cols[6]);
            navaid.nav_freq   = (cols[7] == "") ? null : (object) int.Parse (cols[7]);
            navaid.nav_power  = cols[8];

            navaid.WriteRecord (dbcon);

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
        if (! abbrdb) {
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
        }
    }

    /**
     * Write database from data sourced from ourairports.com.
     *  Input:
     *   datums/oa_<expdate>/aptfreqs.csv
     *   datums/oa_<expdate>/airports.csv
     *   datums/oa_<expdate>/navaids.csv
     *   datums/oa_<expdate>/runways.csv
     */
    public static void WriteOA (IDbConnection dbcon)
    {
        int i = 0;
        StreamReader csvrdr;
        string csv;

        DoCommand (dbcon, "BEGIN;");

        /*
         * Read airport info into memory.
         */
        Dictionary<string,Airport> apt_icaoid = new Dictionary<string,Airport> ();
        csvrdr = new StreamReader ("datums/oa_" + expdate + "/airports.csv");
        csvrdr.ReadLine ();
        while ((csv = csvrdr.ReadLine ()) != null) {
            Airport airport = new Airport ();

            string[] cols = QuotedCSVSplit (csv);

            try {
                airport.ident    = cols[1];
                airport.faaid    = (cols[13] == "") ? cols[1] : cols[13];
                airport.elev     = (cols[6] == "") ? 0.0 : double.Parse (cols[6]);  // ?? fixme
                airport.name     = cols[3];
                airport.lat      = double.Parse (cols[4]);
                airport.lon      = double.Parse (cols[5]);
                airport.state    = cols[9];                     // "US-MA"
                airport.faciluse = "PU";
                airport.metaf    = "";
                airport.tzname   = "";
                airport.desc.Append (cols[10]);
                airport.desc.Append (", ");
                airport.desc.Append (cols[9]);
                airport.desc.Append ('\n');
                airport.desc.Append ("Elev: ");
                airport.desc.Append (cols[6]);
                airport.desc.Append (" ft.");
            } catch (Exception) {
                Console.Error.WriteLine ("csv=" + csv);
                throw;
            }

            apt_icaoid[airport.ident] = airport;
        }
        csvrdr.Close ();

        /*
         * Load runway info into database and into memory.
         */
        csvrdr = new StreamReader ("datums/oa_" + expdate + "/runways.csv");
        csvrdr.ReadLine ();
        while ((csv = csvrdr.ReadLine ()) != null) {
            string[] cols = QuotedCSVSplit (csv);

            Runway lorwy = new Runway ();

            try {
                lorwy.rwy_beglat = ParseDoubleOrNaN (cols[9]);
                lorwy.rwy_beglon = ParseDoubleOrNaN (cols[10]);
                lorwy.rwy_endlat = ParseDoubleOrNaN (cols[15]);
                lorwy.rwy_endlon = ParseDoubleOrNaN (cols[16]);

                if (cols[3] != "") {
                    lorwy.rwy_length = int.Parse (cols[3]) - ParseIntOrZero (cols[13]) - ParseIntOrZero (cols[19]);
                } else if (! double.IsNaN (lorwy.rwy_beglat) && ! double.IsNaN (lorwy.rwy_beglon) &&
                           ! double.IsNaN (lorwy.rwy_endlat) && ! double.IsNaN (lorwy.rwy_endlon)) {
                    lorwy.rwy_length = (int) Math.Round (LatLonDist (lorwy.rwy_beglat, lorwy.rwy_beglon, lorwy.rwy_endlat, lorwy.rwy_endlon) * FtPerNM);
                }

                lorwy.rwy_width  = ParseIntOrZero (cols[4]);
            } catch (Exception) {
                Console.Error.WriteLine ("csv=" + csv);
                throw;
            }

            Airport airport;
            if (apt_icaoid.TryGetValue (cols[2], out airport)) {
                lorwy.rwy_faaid = airport.faaid;

                airport.desc.Append ("\nRwy ");
                airport.desc.Append (cols[8]);
                airport.desc.Append ('/');
                airport.desc.Append (cols[14]);
                airport.desc.Append (": ");
                airport.desc.Append (lorwy.rwy_length);
                airport.desc.Append (" ft. x ");
                airport.desc.Append (lorwy.rwy_width);
                airport.desc.Append (" ft. ");
                airport.desc.Append (cols[5]);
            } else {
                lorwy.rwy_faaid = cols[2];
            }

            if (double.IsNaN (lorwy.rwy_beglat) || double.IsNaN (lorwy.rwy_beglon) ||
                double.IsNaN (lorwy.rwy_endlat) || double.IsNaN (lorwy.rwy_endlon)) continue;

            try {
                lorwy.rwy_number  = cols[8];                    // eg, "04L"
                lorwy.rwy_tdze    = ParseElev (cols[11]);
                lorwy.rwy_truehdg = (int) Math.Round ((cols[12] != "") ? double.Parse (cols[12]) : LatLonTC (lorwy.rwy_beglat, lorwy.rwy_beglon, lorwy.rwy_endlat, lorwy.rwy_endlon));
                lorwy.rwy_ritraf  = "";
            } catch (Exception) {
                Console.Error.WriteLine ("csv=" + csv);
                throw;
            }

            lorwy.WriteRecord (dbcon);

            Runway hirwy = new Runway ();

            try {
                hirwy.rwy_faaid   = lorwy.rwy_faaid;            // eg, "BOS"
                hirwy.rwy_length  = lorwy.rwy_length;
                hirwy.rwy_width   = lorwy.rwy_width;
                hirwy.rwy_number  = cols[14];                   // eg, "22R"
                hirwy.rwy_beglat  = lorwy.rwy_endlat;
                hirwy.rwy_beglon  = lorwy.rwy_endlon;
                hirwy.rwy_tdze    = ParseElev (cols[17]);
                hirwy.rwy_truehdg = (cols[18] != "") ? (int) Math.Round (double.Parse (cols[18])) : (int) lorwy.rwy_truehdg + 180;
                hirwy.rwy_endlat  = lorwy.rwy_beglat;
                hirwy.rwy_endlon  = lorwy.rwy_beglon;
                hirwy.rwy_ritraf  = "";
            } catch (Exception) {
                Console.Error.WriteLine ("csv=" + csv);
                throw;
            }

            hirwy.WriteRecord (dbcon);

            if (++ i == 1024) {
                DoCommand (dbcon, "COMMIT; BEGIN;");
                i = 0;
            }
        }
        csvrdr.Close ();

        /*
         * Read airport frequencies into memory.
         */
        csvrdr = new StreamReader ("datums/oa_" + expdate + "/aptfreqs.csv");
        csvrdr.ReadLine ();
        while ((csv = csvrdr.ReadLine ()) != null) {
            string[] cols = QuotedCSVSplit (csv);
            Airport airport;
            if (apt_icaoid.TryGetValue (cols[2], out airport)) {
                airport.desc.Append ('\n');
                airport.desc.Append (cols[3]);
                airport.desc.Append (": ");
                airport.desc.Append (cols[5]);
                if ((cols[4] != "") && (cols[4] != cols[3])) {
                    airport.desc.Append (' ');
                    airport.desc.Append (cols[4]);
                }
            }
        }
        csvrdr.Close ();

        /*
         * Write airport info into database.
         * Include runway and frequency information.
         */
        foreach (Airport airport in apt_icaoid.Values) {
            airport.WriteRecord (dbcon);
            if (++ i == 1024) {
                DoCommand (dbcon, "COMMIT; BEGIN;");
                i = 0;
            }
        }
        csvrdr.Close ();

        /*
         * Load navaid info into database.
         */
        csvrdr = new StreamReader ("datums/oa_" + expdate + "/navaids.csv");
        csvrdr.ReadLine ();
        while ((csv = csvrdr.ReadLine ()) != null) {
            Navaid navaid = new Navaid ();

            string[] cols = QuotedCSVSplit (csv);

            string freqstr = cols[5];
            int freqstrlen = freqstr.Length;
            if (freqstrlen > 5) {
                freqstr = (freqstr.Substring (0, freqstrlen - 3) + "." + freqstr.Substring (freqstrlen - 3, 3));
            }

            try {
                navaid.ident      = cols[2];
                navaid.nav_name   = cols[3] + " - " + freqstr + " - " + cols[9];
                navaid.nav_type   = cols[4].Replace ("-", "/");
                navaid.nav_freq   = int.Parse (cols[5]);
                navaid.lat        = double.Parse (cols[6]);
                navaid.lon        = double.Parse (cols[7]);
                navaid.nav_elev   = (cols[8] != "") ? double.Parse (cols[8]) : 0.0; //TODO fix this ????????
                navaid.nav_power  = cols[18];

                if (cols[15] != "") navaid.nav_magvar = (int) - Math.Round (double.Parse (cols[15]));
                else if (cols[16] != "") navaid.nav_magvar = (int) - Math.Round (double.Parse (cols[16]));
                else navaid.nav_magvar = 0; //TODO fix this ????????
            } catch (Exception) {
                Console.Error.WriteLine ("csv=" + csv);
                throw;
            }

            navaid.WriteRecord (dbcon);

            if (++ i == 1024) {
                DoCommand (dbcon, "COMMIT; BEGIN;");
                i = 0;
            }
        }
        csvrdr.Close ();

        DoCommand (dbcon, "COMMIT;");
    }

    public static void DoCommand (IDbConnection dbcon, string command)
    {
        IDbCommand dbcmd = dbcon.CreateCommand ();
        try {
            dbcmd.CommandText = command;
            dbcmd.ExecuteNonQuery ();
        } finally {
            dbcmd.Dispose ();
        }
    }

    public static string[] QuotedCSVSplit (string line)
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

    public static object ParseElev (string s)
    {
        if (s == "") return null;
        return (object) double.Parse (s);
    }

    public static double ParseDoubleOrNaN (string s)
    {
        if (s == "") return double.NaN;
        return double.Parse (s);
    }

    public static int ParseIntOrZero (string s)
    {
        if (s == "") return 0;
        return int.Parse (s);
    }

    public static void InsertMultipleKeys (IDbConnection dbcon, string table, string descrip, long rowid)
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

    public static double FtPerM    = 3.28084;
    public static double FtPerNM   = 6076.12;
    public static double KtPerMPS  = 1.94384;
    public static double NMPerDeg  = 60.0;
    public static double MMPerIn   = 25.4;    // millimetres per inch
    public static double SMPerNM   = 1.15078;
    public static int    MPerNM    = 1852;    // metres per naut mile

    public static double LatLonTC (double srcLat, double srcLon, double dstLat, double dstLon)
    {
        return LatLonTC_rad (srcLat, srcLon, dstLat, dstLon) * 180.0 / Math.PI;
    }
    public static double LatLonTC_rad (double srcLat, double srcLon, double dstLat, double dstLon)
    {
        // http://en.wikipedia.org/wiki/Great-circle_navigation
        double sLat = ToRadians (srcLat);
        double sLon = ToRadians (srcLon);
        double fLat = ToRadians (dstLat);
        double fLon = ToRadians (dstLon);
        double dLon = fLon - sLon;
        double t1 = Math.Cos (sLat) * Math.Tan (fLat);
        double t2 = Math.Sin (sLat) * Math.Cos (dLon);
        return Math.Atan2 (Math.Sin (dLon), t1 - t2);
    }

    public static double LatLonDist (double srcLat, double srcLon, double dstLat, double dstLon)
    {
        return ToDegrees (LatLonDist_rad (srcLat, srcLon, dstLat, dstLon)) * NMPerDeg;
    }
    public static double LatLonDist_rad (double srcLat, double srcLon, double dstLat, double dstLon)
    {
        // http://en.wikipedia.org/wiki/Great-circle_distance
        double sLat = ToRadians (srcLat);
        double sLon = ToRadians (srcLon);
        double fLat = ToRadians (dstLat);
        double fLon = ToRadians (dstLon);
        double dLon = fLon - sLon;
        double t1   = Sq (Math.Cos (fLat) * Math.Sin (dLon));
        double t2   = Sq (Math.Cos (sLat) * Math.Sin (fLat) - Math.Sin (sLat) * Math.Cos (fLat) * Math.Cos (dLon));
        double t3   = Math.Sin (sLat) * Math.Sin (fLat);
        double t4   = Math.Cos (sLat) * Math.Cos (fLat) * Math.Cos (dLon);
        return Math.Atan2 (Math.Sqrt (t1 + t2), t3 + t4);
    }

    private static double Sq (double x) { return x*x; }

    public static double ToRadians (double deg)
    {
        return deg * Math.PI / 180.0;
    }

    public static double ToDegrees (double rad)
    {
        return rad * 180.0 / Math.PI;
    }
}
