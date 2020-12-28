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

// mcs -debug -out:MakeWaypoints.exe MakeWaypoints.cs GeoContext.cs GetTZForLL.cs Topography.cs -reference:System.Data.dll -reference:Mono.Data.Sqlite.dll

// mono --debug MakeWaypoints.exe 20150820 datums/waypoints_20150820.db <dbfmt> <infmt>
//   dbfmt = 0 : normal (WairToNow)
//           1 : abbreviated (HSIWatch)
//   infmt = 0 : faa files
//                  datums/airports_<expdate>.csv
//                  datums/airways_<expdate>.csv
//                  datums/fixes_<expdate>.csv
//                  datums/intersections_<expdate>.csv
//                  datums/localizers_<expdate>.csv
//                  datums/navaids_<expdate>.csv
//                  datums/runways_<expdate>.csv
//           1 : ourairports.com files
//                  datums/oa_<expdate>/aptfreqs.csv
//                  datums/oa_<expdate>/airports.hxl
//                  datums/oa_<expdate>/navaids.csv
//                  datums/oa_<expdate>/runways.csv
//           2 : openflightmap.org files
//                  datums/ofmwaypts_<expdate>.xml.gz

// testing openflightmaps:
//  php downloadofmtiles.php  # to create datums/ofmwaypts_20201231.xml.gz
//  time mono --debug MakeWaypoints.exe 20201231 x.db 0 2 > x.out
//  sqlite3 x.db              # to examine results

using Mono.Data.Sqlite;
using System;
using System.Collections.Generic;
using System.Data;
using System.IO;
using System.IO.Compression;
using System.Text;
using System.Xml;

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
    public String apt_faaid;
    public double apt_elev;
    public String apt_name;
    public StringBuilder apt_desc = new StringBuilder ();
    public String apt_state;
    public String apt_faciluse;
    public String apt_metaf;
    public String apt_tzname;

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
    public virtual void WriteRecord (IDbConnection dbcon)
    {
        String aptdesc = apt_desc.ToString ().Trim ();
        IDbCommand dbcmd1 = dbcon.CreateCommand ();
        try {
            if (MakeWaypoints.abbrdb) {
                dbcmd1.CommandText = "INSERT INTO airports (apt_icaoid,apt_faaid,apt_elev,apt_name,apt_lat,apt_lon,apt_desc1,apt_desc2) " +
                        "VALUES (@apt_icaoid,@apt_faaid,@apt_elev,@apt_name,@apt_lat,@apt_lon,@apt_desc1,@apt_desc2); " +
                        "SELECT last_insert_rowid ()";
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_icaoid"  , ident));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_faaid"   , apt_faaid));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_elev"    , apt_elev));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_name"    , apt_name));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_lat"     , lat));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_lon"     , lon));
                string[] infos = aptdesc.Split (linesplit);
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_desc1"   , infos[0]));
                StringBuilder sb = new StringBuilder ();
                if (MakeWaypoints.infmt == 1) {
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
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_faaid"   , apt_faaid));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_elev"    , apt_elev));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_name"    , apt_name));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_lat"     , lat));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_lon"     , lon));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_desc"    , aptdesc));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_state"   , apt_state));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_faciluse", apt_faciluse));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_metaf"   , apt_metaf));
                dbcmd1.Parameters.Add (new SqliteParameter ("@apt_tzname"  , apt_tzname));
            }
            long rowid = (long) dbcmd1.ExecuteScalar ();

            if (! MakeWaypoints.abbrdb) {
                MakeWaypoints.InsertMultipleKeys (dbcon, "aptkeys", ident + " " + apt_faaid + " " + apt_name + " " + aptdesc, rowid);
                DefineWaypoint ();
            }
        } finally {
            dbcmd1.Dispose ();
        }
    }
}

// an fix waypoint
public class Fix : Waypoint {
    public String fix_desc;

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
            dbcmd1.Parameters.Add (new SqliteParameter ("@fix_desc", fix_desc));
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
        if (nav_elev   == -99999) nav_elev   = Math.Round (Topography.GetElevFt (lat, lon));
        if (nav_magvar == -99999) nav_magvar = MakeWaypoints.GetMagVar (lat, lon, nav_elev);

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
    public String rwy_icaoid;   // eg "KBOS", filled for all new databases
    public String rwy_faaid;    // eg "BOS", filled in for FAA,OA (for backward compat); null for OFM
    public String rwy_number;   // eg "04L"
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
        MakeWaypoints.DoCommand (dbcon, "CREATE TABLE runways (rwy_icaoid TEXT NOT NULL, rwy_faaid TEXT, rwy_number TEXT NOT NULL, rwy_truehdg INTEGER, rwy_tdze REAL, " +
                "rwy_beglat REAL NOT NULL, rwy_beglon REAL NOT NULL, rwy_endlat REAL NOT NULL, rwy_endlon REAL NOT NULL, " +
                "rwy_ritraf TEXT NOT NULL, rwy_length INTEGER NOT NULL, rwy_width INTEGER NOT NULL);");
        MakeWaypoints.DoCommand (dbcon, "CREATE INDEX runways_icaoids ON runways (rwy_icaoid);");
        MakeWaypoints.DoCommand (dbcon, "CREATE INDEX runways_faaids  ON runways (rwy_faaid);");
        MakeWaypoints.DoCommand (dbcon, "CREATE INDEX runways_beglats ON runways (rwy_beglat);");
        MakeWaypoints.DoCommand (dbcon, "CREATE INDEX runways_beglons ON runways (rwy_beglon);");
    }

    // write database record
    public void WriteRecord (IDbConnection dbcon)
    {
        IDbCommand dbcmd1 = dbcon.CreateCommand ();
        try {
            dbcmd1.CommandText = "INSERT INTO runways (rwy_icaoid,rwy_faaid,rwy_number,rwy_truehdg,rwy_tdze,rwy_beglat,rwy_beglon,rwy_endlat,rwy_endlon,rwy_ritraf,rwy_length,rwy_width) " +
                    "VALUES (@rwy_icaoid,@rwy_faaid,@rwy_number,@rwy_truehdg,@rwy_tdze,@rwy_beglat,@rwy_beglon,@rwy_endlat,@rwy_endlon,@rwy_ritraf,@rwy_length,@rwy_width)";
            dbcmd1.Parameters.Add (new SqliteParameter ("@rwy_icaoid",  rwy_icaoid));   // eg, "KBOS"
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

// OA Extensions

public class OA_Airport : Airport {
    public int oa_longrwyft;
}

// OFM Extensions

public class OFM_Airport : Airport {
    public String _Ahp_AhpUid_codeId;
    public String _Ahp_AhpUid_region;
    public String _Ahp_codeIata;
    public String _Ahp_codeIcao;
    public String _Ahp_txtNameCitySer = "";
    public String _Ahp_txtRmk;

    public SortedDictionary<String,OFM_RwyPair> rwypairs = new SortedDictionary<String,OFM_RwyPair> ();
    public LinkedList<OFM_AptFreq> aptfreqs = new LinkedList<OFM_AptFreq> ();

    public override void WriteRecord (IDbConnection dbcon)
    {
        ident =
                (_Ahp_codeIcao != null) ? _Ahp_codeIcao :
                (_Ahp_codeIata != null) ? _Ahp_codeIata :
                _Ahp_AhpUid_codeId;
        apt_faaid =
                (_Ahp_codeIata != null) ? _Ahp_codeIata :
                (_Ahp_codeIcao != null) ? _Ahp_codeIcao :
                _Ahp_AhpUid_codeId;

        if (apt_elev == -99999) apt_elev = Math.Round (Topography.GetElevFt (lat, lon));

        // first comes city, elevation
        // don't seem to have country anywhere
        if ((_Ahp_txtNameCitySer != null) || (_Ahp_AhpUid_region != null)) {
            if (_Ahp_txtNameCitySer != null) apt_desc.Append (_Ahp_txtNameCitySer);
            if (_Ahp_AhpUid_region != null) {
                apt_desc.Append (", ");
                apt_desc.Append (_Ahp_AhpUid_region);
            }
            apt_desc.Append ('\n');
        }
        apt_desc.Append ("Elev: ");
        apt_desc.Append (apt_elev);
        apt_desc.Append (" ft.");

        // next comes runways
        int longestrwy = 0;
        foreach (OFM_RwyPair rwypair in rwypairs.Values) {
            apt_desc.Append ("\nRwy ");
            apt_desc.Append (rwypair._Rwy_RwyUid_txtDesig);
            apt_desc.Append (": ");
            apt_desc.Append (rwypair._Rwy_valLen);
            apt_desc.Append (' ');
            apt_desc.Append (rwypair._Rwy_uomDimRwy);
            apt_desc.Append (" x ");
            apt_desc.Append (rwypair._Rwy_valWid);
            apt_desc.Append (' ');
            apt_desc.Append (rwypair._Rwy_uomDimRwy);
            apt_desc.Append (rwypair._Rwy_codeCompPrep);
            int length = rwypair.getLengthFt ();
            if (longestrwy < length) longestrwy = length;
        }

        // see if airport is capable of generating METARs and/or TAFs
        apt_metaf = (longestrwy < 1000) ? "" : MakeWaypoints.GetIntlMetaf (ident);

        // then the frequencies (sort and eliminate duplicates)
        String[] freqlines = new String[aptfreqs.Count];
        int i = 0;
        foreach (OFM_AptFreq aptfreq in aptfreqs) {
            freqlines[i++] =
                aptfreq._Fqy_FqyUid_SerUid_codeType + ": " +
                aptfreq._Fqy_FqyUid_valFreqTrans + " " +
                aptfreq._Fqy_uomFreq + " '" +
                aptfreq._Fqy_Cdl_txtCallSign + "'";
        }
        Array.Sort (freqlines, 0, i, null);
        for (i = 0; i < freqlines.Length; i ++) {
            String freqline = freqlines[i];
            if ((i == 0) || (freqline != freqlines[i-1])) {
                apt_desc.Append ('\n');
                apt_desc.Append (freqline);
            }
        }

        // write airport record
        base.WriteRecord (dbcon);

        // write runway records
        foreach (OFM_RwyPair rwypair in rwypairs.Values) {
            rwypair.WriteRecord (dbcon, this);
        }
    }
}

public class OFM_AptFreq {
    public String _Fqy_FqyUid_SerUid_UniUid_txtName;
    public String _Fqy_FqyUid_SerUid_codeType;
    public String _Fqy_FqyUid_valFreqTrans;
    public String _Fqy_uomFreq;
    public String _Fqy_Cdl_txtCallSign;
}

public class OFM_RwyPair {
    public String _Rwy_RwyUid_AhpUid_codeId;
    public String _Rwy_RwyUid_txtDesig;
    public String _Rwy_valLen;
    public String _Rwy_valWid;
    public String _Rwy_uomDimRwy;
    public String _Rwy_codeCompPrep = "";

    public OFM_Runway lorwy;
    public OFM_Runway hirwy;

    public void WriteRecord (IDbConnection dbcon, OFM_Airport airport)
    {
        if ((lorwy != null) && (hirwy != null)) {
            lorwy.WriteRecord (dbcon, airport, this);
            hirwy.WriteRecord (dbcon, airport, this);
        }
    }

    public int getLengthFt ()
    {
        switch (_Rwy_uomDimRwy) {
            case "FT": {
                return (int) Math.Round (double.Parse (_Rwy_valLen));
            }
            case "M": {
                return (int) Math.Round (double.Parse (_Rwy_valLen) * 3.28084);
            }
        }
        return 0;
    }

    public int getWidthFt ()
    {
        switch (_Rwy_uomDimRwy) {
            case "FT": {
                return (int) Math.Round (double.Parse (_Rwy_valWid));
            }
            case "M": {
                return (int) Math.Round (double.Parse (_Rwy_valWid) * 3.28084);
            }
        }
        return 0;
    }
}

public class OFM_Runway : Runway {
    public String _Rdn_RdnUid_RwyUid_AhpUid_codeId;     // <ETNG>
    public String _Rdn_RdnUid_RwyUid_txtDesig;          // <09/27>
    public String _Rdn_RdnUid_txtDesig;                 // <09>

    public void WriteRecord (IDbConnection dbcon, OFM_Airport airport, OFM_RwyPair rwypair)
    {
        rwy_icaoid = airport.ident;
        rwy_number = _Rdn_RdnUid_txtDesig;
        rwy_ritraf = "";
        rwy_endlat = rwypair.lorwy.rwy_beglat + rwypair.hirwy.rwy_beglat - rwy_beglat;
        rwy_endlon = rwypair.lorwy.rwy_beglon + rwypair.hirwy.rwy_beglon - rwy_beglon;
        rwy_length = rwypair.getLengthFt ();
        rwy_width  = rwypair.getWidthFt ();

        base.WriteRecord (dbcon);
    }
}

public class OFM_Navaid : Navaid {
    public String _Nav_NavUid_state;    // two-letter code
    public String _Nav_NavUid_region;   // spelled out
    public String _Vor_txtName;
    public String _Vor_uomFreq;
    public String _Vor_valFreq;
}

// main program
public class MakeWaypoints {
    public static bool abbrdb;
    public static bool debug;
    public static int infmt;
    public static Dictionary<string,LinkedList<Waypoint>> waypointss = new Dictionary<string,LinkedList<Waypoint>> ();
    public static string dbname;
    public static string expdate;

    public static void Main (string[] args)
    {
        int argc = 0;
        foreach (String arg in args) {
            if (arg == "-debug") {
                debug = true;
                continue;
            }
            if (arg.StartsWith ("-")) throw new Exception ("unknown option");
            switch (argc ++) {
                case 0: expdate = arg; break;
                case 1: dbname  = arg; break;
                case 2: abbrdb  = ((arg[0] & 1) != 0); break;
                case 3: infmt   = int.Parse (arg); break;
                default: throw new Exception ("too many args");
            }
        }
        if (argc != 4) throw new Exception ("missing args");

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

            switch (infmt) {
                case 0: WriteFAA (dbcon); break;
                case 1: WriteOA  (dbcon); break;
                case 2: WriteOFM (dbcon); break;
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
        Dictionary<String,String> apt_faaid_to_icaoid = new Dictionary<String,String> ();
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

            airport.ident        = cols[0];
            airport.apt_faaid    = cols[1];
            airport.apt_elev     = double.Parse (cols[2]);
            airport.apt_name     = cols[3];
            airport.lat          = double.Parse (cols[4]);
            airport.lon          = double.Parse (cols[5]);
            airport.apt_desc.Append (cols[7]);
            airport.apt_state    = cols[8];
            airport.apt_faciluse = cols[9];
            airport.apt_metaf    = cols[10];
            airport.apt_tzname   = cols[11];

            apt_faaid_to_icaoid[airport.apt_faaid] = airport.ident;

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

            fix.ident    = cols[0];
            fix.lat      = double.Parse (cols[1]);
            fix.lon      = double.Parse (cols[2]);
            fix.fix_desc = cols[3] + " " + cols[4];

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
            runway.rwy_icaoid  = apt_faaid_to_icaoid[runway.rwy_faaid];

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
     *   datums/oa_<expdate>/airports.hxl
     *   datums/oa_<expdate>/navaids.csv
     *   datums/oa_<expdate>/runways.csv
     */
    public static void WriteOA (IDbConnection dbcon)
    {
        Dictionary<String,int> colnames;
        int i;
        StreamReader csvrdr;
        string csv;

        DoCommand (dbcon, "BEGIN;");

        /*
         * Read airport info into memory.
         */
        Dictionary<string,OA_Airport> oaidents = new Dictionary<string,OA_Airport> ();
        csvrdr = new StreamReader ("datums/oa_" + expdate + "/airports.hxl");
        colnames = ReadColNames (csvrdr);
        csvrdr.ReadLine ();
        while ((csv = csvrdr.ReadLine ()) != null) {
            csv = csv.Replace ("\"Idanha-a-Nova\\\"", "Idanha-a-Nova");
            csv = csv.Replace ("Calgary\\Okotoks", "Calgary/Okotoks");

            string[] cols = QuotedCSVSplit (csv);
            if (cols[colnames["type"]] == "closed") continue;

            OA_Airport airport = new OA_Airport ();

            try {
                airport.ident        = cols[colnames["ident"]].ToUpperInvariant ();     // EGLL     KBVY
                airport.apt_faaid    = cols[colnames["iata_code"]];                     // LHR      BVY
                airport.apt_name     = cols[colnames["name"]];
                airport.lat          = double.Parse (cols[colnames["latitude_deg"]]);
                airport.lon          = double.Parse (cols[colnames["longitude_deg"]]);
                airport.apt_state    = cols[colnames["iso_region"]];                    // GB-ENG   US-MA
                airport.apt_faciluse = "PU";
                airport.apt_tzname   = GetTZForLL.Get (airport.lat, airport.lon);
                airport.apt_elev     = (cols[colnames["elevation_ft"]] != "") ?
                            double.Parse (cols[colnames["elevation_ft"]]) :
                            Math.Round (Topography.GetElevFt (airport.lat, airport.lon));
                airport.apt_desc.Append (cols[colnames["municipality"]]);               // London   Beverly
                airport.apt_desc.Append (", ");
                airport.apt_desc.Append (cols[colnames["region_name"]]);                // England  Massachusetts
                airport.apt_desc.Append (", ");
                airport.apt_desc.Append (cols[colnames["country_name"]]);               // Uni Kin  Uni Sta
                String lu = cols[colnames["last_updated"]];
                if (lu != "") {
                    i = lu.IndexOf ('T');
                    if (i >= 0) lu = lu.Substring (0, i);
                    airport.apt_desc.Append ("\nLast Update: ");
                    airport.apt_desc.Append (lu);
                }
                airport.apt_desc.Append ("\nElev: ");
                airport.apt_desc.Append (Math.Round (airport.apt_elev));
                airport.apt_desc.Append (" ft.");
            } catch (Exception e) {
                Console.Error.WriteLine ("csv=" + csv);
                Console.Error.WriteLine (e.ToString ());
                Console.Error.WriteLine ("");
                continue;
            }

            oaidents[airport.ident] = airport;
        }
        csvrdr.Close ();

        /*
         * Load runway info into database and into memory.
         */
        csvrdr = new StreamReader ("datums/oa_" + expdate + "/runways.csv");
        colnames = ReadColNames (csvrdr);
        i = 0;
        while ((csv = csvrdr.ReadLine ()) != null) {
            string[] cols = QuotedCSVSplit (csv);

            Runway lorwy = new Runway ();

            try {
                lorwy.rwy_beglat = ParseDoubleOrNaN (cols[colnames["le_latitude_deg"]]);
                lorwy.rwy_beglon = ParseDoubleOrNaN (cols[colnames["le_longitude_deg"]]);
                lorwy.rwy_endlat = ParseDoubleOrNaN (cols[colnames["he_latitude_deg"]]);
                lorwy.rwy_endlon = ParseDoubleOrNaN (cols[colnames["he_longitude_deg"]]);

                if (cols[colnames["length_ft"]] != "") {
                    lorwy.rwy_length = int.Parse (cols[colnames["length_ft"]]) -
                                            ParseIntOrZero (cols[colnames["le_displaced_threshold_ft"]]) -
                                            ParseIntOrZero (cols[colnames["he_displaced_threshold_ft"]]);
                } else if (! double.IsNaN (lorwy.rwy_beglat) && ! double.IsNaN (lorwy.rwy_beglon) &&
                           ! double.IsNaN (lorwy.rwy_endlat) && ! double.IsNaN (lorwy.rwy_endlon)) {
                    lorwy.rwy_length = (int) Math.Round (LatLonDist (lorwy.rwy_beglat, lorwy.rwy_beglon, lorwy.rwy_endlat, lorwy.rwy_endlon) * FtPerNM);
                }

                lorwy.rwy_width  = ParseIntOrZero (cols[colnames["width_ft"]]);
            } catch (Exception) {
                Console.Error.WriteLine ("csv=" + csv);
                throw;
            }

            OA_Airport airport;
            if (oaidents.TryGetValue (cols[colnames["airport_ident"]].ToUpperInvariant (), out airport)) {
                lorwy.rwy_icaoid = airport.ident;
                lorwy.rwy_faaid  = airport.apt_faaid;

                airport.apt_desc.Append ("\nRwy ");
                airport.apt_desc.Append (cols[colnames["le_ident"]]);
                airport.apt_desc.Append ('/');
                airport.apt_desc.Append (cols[colnames["he_ident"]]);
                airport.apt_desc.Append (": ");
                airport.apt_desc.Append (lorwy.rwy_length);
                airport.apt_desc.Append (" ft. x ");
                airport.apt_desc.Append (lorwy.rwy_width);
                airport.apt_desc.Append (" ft. ");
                airport.apt_desc.Append (cols[colnames["surface"]]);

                if (airport.oa_longrwyft < lorwy.rwy_length) airport.oa_longrwyft = lorwy.rwy_length;
            } else {
                lorwy.rwy_icaoid = cols[colnames["airport_ident"]];
                lorwy.rwy_faaid  = cols[colnames["airport_ident"]];
            }

            if (double.IsNaN (lorwy.rwy_beglat) || double.IsNaN (lorwy.rwy_beglon) ||
                double.IsNaN (lorwy.rwy_endlat) || double.IsNaN (lorwy.rwy_endlon)) continue;

            try {
                lorwy.rwy_number  = cols[colnames["le_ident"]]; // eg, "04L"
                lorwy.rwy_tdze    = ParseElev (cols[colnames["le_elevation_ft"]]);
                lorwy.rwy_truehdg = (int) Math.Round ((cols[colnames["le_heading_degT"]] != "") ?
                                            double.Parse (cols[colnames["le_heading_degT"]]) :
                                            LatLonTC (lorwy.rwy_beglat, lorwy.rwy_beglon, lorwy.rwy_endlat, lorwy.rwy_endlon));
                lorwy.rwy_ritraf  = "";
            } catch (Exception) {
                Console.Error.WriteLine ("csv=" + csv);
                throw;
            }

            lorwy.WriteRecord (dbcon);

            Runway hirwy = new Runway ();

            try {
                hirwy.rwy_icaoid  = lorwy.rwy_icaoid;           // eg, "KBOS"
                hirwy.rwy_faaid   = lorwy.rwy_faaid;            // eg, "BOS"
                hirwy.rwy_length  = lorwy.rwy_length;
                hirwy.rwy_width   = lorwy.rwy_width;
                hirwy.rwy_number  = cols[colnames["he_ident"]]; // eg, "22R"
                hirwy.rwy_beglat  = lorwy.rwy_endlat;
                hirwy.rwy_beglon  = lorwy.rwy_endlon;
                hirwy.rwy_endlat  = lorwy.rwy_beglat;
                hirwy.rwy_endlon  = lorwy.rwy_beglon;
                hirwy.rwy_tdze    = ParseElev (cols[colnames["he_elevation_ft"]]);
                hirwy.rwy_truehdg = (int) Math.Round ((cols[colnames["he_heading_degT"]] != "") ?
                                            Math.Round (double.Parse (cols[colnames["he_heading_degT"]])) :
                                            LatLonTC (hirwy.rwy_beglat, hirwy.rwy_beglon, hirwy.rwy_endlat, hirwy.rwy_endlon));
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
            OA_Airport airport;
            if (oaidents.TryGetValue (cols[2].ToUpperInvariant (), out airport)) {
                airport.apt_desc.Append ('\n');
                airport.apt_desc.Append (cols[3]);
                airport.apt_desc.Append (": ");
                airport.apt_desc.Append (cols[5]);
                if ((cols[4] != "") && (cols[4] != cols[3])) {
                    airport.apt_desc.Append (' ');
                    airport.apt_desc.Append (cols[4]);
                }
            }
        }
        csvrdr.Close ();

        /*
         * Write airport info into database.
         * Include runway and frequency information.
         */
        OpenIntlMetafs ();
        foreach (OA_Airport airport in oaidents.Values) {
            airport.apt_metaf = (airport.oa_longrwyft < 1000) ? "" : GetIntlMetaf (airport.ident);
            airport.WriteRecord (dbcon);
            if (++ i == 1024) {
                DoCommand (dbcon, "COMMIT; BEGIN;");
                i = 0;
            }
        }
        csvrdr.Close ();
        CloseIntlMetafs ();

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
                navaid.nav_elev   = (cols[8] != "") ? double.Parse (cols[8]) : -99999;
                navaid.nav_power  = cols[18];

                if (cols[15] != "") navaid.nav_magvar = (int) - Math.Round (double.Parse (cols[15]));
                else if (cols[16] != "") navaid.nav_magvar = (int) - Math.Round (double.Parse (cols[16]));
                else navaid.nav_magvar = -99999;
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

    public static Dictionary<String,int> ReadColNames (StreamReader csvrdr)
    {
        Dictionary<String,int> colnames = new Dictionary<String,int> ();
        String   csv  = csvrdr.ReadLine ();
        String[] cols = QuotedCSVSplit (csv);
        for (int i = 0; i < cols.Length; i ++) {
            colnames[cols[i]] = i;
        }
        return colnames;
    }

    /**
     * Write database from data sourced from openflightmaps.org.
     *  Input:
     *   datums/ofmwaypts_<expdate>.xml.gz
     */
    public static void WriteOFM (IDbConnection dbcon)
    {
        Dictionary<String,OFM_Airport> apts_by_codeId  = new Dictionary<String,OFM_Airport> ();
        Dictionary<String,OFM_Airport> apts_by_txtName = new Dictionary<String,OFM_Airport> ();
        Dictionary<String,String> region_names = new Dictionary<String,String> ();
        HashSet<String> oldnavaids = new HashSet<String> ();
        String region = null;
        String tags   = "";
        OFM_Navaid  navaid  = null;
        OFM_Airport airport = null;
        OFM_AptFreq aptfreq = null;
        OFM_RwyPair rwypair = null;
        OFM_Runway  runway  = null;

        // read table that translates 2-letter and 4-letter region code to country name

        StreamReader sr = new StreamReader ("ofmregions.dat");
        for (String ln; (ln = sr.ReadLine ()) != null;) {
            int i = ln.IndexOf ("//");
            if (i >= 0) ln = ln.Substring (0, i);
            ln = ln.Trim ();
            if (ln != "") {
                region_names[ln.Substring(0,4).ToUpperInvariant()] = ln.Substring (8);
                region_names[ln.Substring(5,2).ToUpperInvariant()] = ln.Substring (8);
            }
        }
        sr.Close ();

        // PASS 1: Airports, Navaids
        //  airports get added to the apts_by_codeId and apts_by_txtName lists
        //  navaids get written directly to the database

        FileStream filestream = File.Open ("datums/ofmwaypts_" + expdate + ".xml.gz", FileMode.Open, FileAccess.Read);
        GZipStream gzipstream = new GZipStream (filestream, CompressionMode.Decompress);
        XmlTextReader xmlreader = new XmlTextReader (gzipstream);

        while (xmlreader.Read ()) {
            switch (xmlreader.NodeType) {
                case XmlNodeType.Element: {
                    if (! xmlreader.IsEmptyElement) {
                        tags += " " + xmlreader.Name;
                        switch (tags) {
                            case " ofmx Ahp": {
                                airport = new OFM_Airport ();
                                airport.apt_faciluse = "";
                                airport.apt_tzname   = "";
                                airport.apt_elev     = -99999;
                                airport.apt_state    = region;
                                break;
                            }
                            case " ofmx Ahp AhpUid": {
                                String rr = xmlreader.GetAttribute ("region");
                                if (rr != null) {
                                    airport.apt_state = rr = rr.ToUpperInvariant ();
                                    region_names.TryGetValue (rr, out airport._Ahp_AhpUid_region);
                                }
                                break;
                            }
                            case " ofmx Ndb": {
                                navaid = new OFM_Navaid ();
                                navaid.nav_type   = "NDB";
                                navaid.nav_elev   = -99999;
                                navaid.nav_magvar = -99999;
                                navaid.nav_power  = "";
                                break;
                            }
                            case " ofmx Vor": {
                                navaid = new OFM_Navaid ();
                                navaid.nav_elev   = -99999;
                                navaid.nav_magvar = -99999;
                                navaid.nav_power  = "";
                                break;
                            }
                            case " ofmx Ndb NdbUid":
                            case " ofmx Vor VorUid": {
                                String rr = xmlreader.GetAttribute ("region");
                                if (rr != null) {
                                    navaid._Nav_NavUid_state = rr = rr.ToUpperInvariant ();
                                    region_names.TryGetValue (rr, out navaid._Nav_NavUid_region);
                                }
                                break;
                            }
                        }
                    }
                    break;
                }
                case XmlNodeType.Text: {
                    String val = xmlreader.Value.Trim ();
                    if (debug) {
                        Console.WriteLine ("{0:D8}{1}=<{2}>", xmlreader.LineNumber, tags,
                                    val.Replace ("\\", "\\\\").Replace ("\n", "\\n").Replace ("\r", "\\r"));
                    }
                    if (val == "") break;
                    switch (tags) {

                        // openflightmaps.org region name for following waypoints
                        case " ofmx ofmxfile name": {
                            region = val;
                            break;
                        }

                        // airport data
                        case " ofmx Ahp AhpUid codeId": {
                            airport._Ahp_AhpUid_codeId = val;
                            break;
                        }
                        case " ofmx Ahp codeIata": {
                            airport._Ahp_codeIata = val;
                            break;
                        }
                        case " ofmx Ahp codeIcao": {
                            airport._Ahp_codeIcao = val;
                            break;
                        }
                        case " ofmx Ahp geoLat": {
                            airport.lat = ParseLatLon (val, 'N', 'S');
                            break;
                        }
                        case " ofmx Ahp geoLong": {
                            airport.lon = ParseLatLon (val, 'E', 'W');
                            break;
                        }
                        case " ofmx Ahp valElev": {
                            //TODO ft or m?
                            airport.apt_elev = (int) Math.Round (double.Parse (val));
                            break;
                        }
                        case " ofmx Ahp txtName": {
                            airport.apt_name = val;
                            break;
                        }
                        case " ofmx Ahp txtNameCitySer": {
                            airport._Ahp_txtNameCitySer = val;
                            break;
                        }
                        case " ofmx Ahp txtRmk": {
                            airport._Ahp_txtRmk = val;
                            break;
                        }

                        // navaid (ndb,vor) data
                        case " ofmx Ndb NdbUid codeId":
                        case " ofmx Vor VorUid codeId": {
                            navaid.ident = val;
                            break;
                        }
                        case " ofmx Ndb NdbUid geoLat":
                        case " ofmx Vor VorUid geoLat": {
                            navaid.lat = ParseLatLon (val, 'N', 'S');
                            break;
                        }
                        case " ofmx Ndb NdbUid geoLong":
                        case " ofmx Vor VorUid geoLong": {
                            navaid.lon = ParseLatLon (val, 'E', 'W');
                            break;
                        }
                        case " ofmx Vor codeType": {
                            navaid.nav_type = val;
                            break;
                        }
                        case " ofmx Ndb txtName":
                        case " ofmx Vor txtName": {
                            navaid._Vor_txtName = val;
                            break;
                        }
                        case " ofmx Ndb uomFreq":
                        case " ofmx Vor uomFreq": {
                            navaid._Vor_uomFreq = val;
                            break;
                        }
                        case " ofmx Ndb valElev":
                        case " ofmx Vor valElev": {
                            navaid.nav_elev = double.Parse (val);
                            break;
                        }
                        case " ofmx Ndb valFreq":
                        case " ofmx Vor valFreq": {
                            navaid._Vor_valFreq = val;
                            break;
                        }
                        case " ofmx Vor valMagVar": {
                            navaid.nav_magvar = (int) Math.Round (double.Parse (val));
                            break;
                        }
                    }
                    break;
                }
                case XmlNodeType.EndElement: {
                    if (! tags.EndsWith (" " + xmlreader.Name)) {
                        throw new Exception ("tags <" + tags + "> doesn't end with < " + xmlreader.Name + ">");
                    }
                    switch (tags) {

                        // end of airport definition, save in lists for reference in pass 2
                        case " ofmx Ahp": {
                            airport.apt_tzname = GetTZForLL.Get (airport.lat, airport.lon);
                            apts_by_codeId[airport._Ahp_AhpUid_codeId] = airport;
                            apts_by_txtName[airport.apt_name] = airport;
                            airport = null;
                            break;
                        }

                        // end of navaid definition, write to database if not a duplicate
                        case " ofmx Ndb":
                        case " ofmx Vor": {
                            String key = navaid.nav_type + " " + navaid.ident + " " + navaid.lat + " " + navaid.lon;
                            if (! oldnavaids.Contains (key)) {
                                oldnavaids.Add (key);
                                navaid.nav_name = navaid._Vor_txtName + " - " + navaid._Vor_valFreq + " " + navaid._Vor_uomFreq;
                                switch (navaid._Vor_uomFreq) {
                                    case "KHZ": navaid.nav_freq = (int) Math.Round (double.Parse (navaid._Vor_valFreq)); break;
                                    case "MHZ": navaid.nav_freq = (int) Math.Round (double.Parse (navaid._Vor_valFreq) * 1000.0); break;
                                }
                                if (navaid._Nav_NavUid_region != null) {
                                    navaid.nav_name += " - " + navaid._Nav_NavUid_region;
                                } else if (navaid._Nav_NavUid_state != null) {
                                    navaid.nav_name += " - " + navaid._Nav_NavUid_state;
                                }
                                navaid.WriteRecord (dbcon);
                            }
                            navaid = null;
                            break;
                        }
                    }
                    int len = xmlreader.Name.Length + 1;
                    tags = tags.Substring (0, tags.Length - len);
                    break;
                }
            }
        }
        filestream.Close ();

        // PASS 2 : airport frequencies, runways
        //  add their definitions to existing airport definitions

        filestream = File.Open ("datums/ofmwaypts_" + expdate + ".xml.gz", FileMode.Open, FileAccess.Read);
        gzipstream = new GZipStream (filestream, CompressionMode.Decompress);
        xmlreader  = new XmlTextReader (gzipstream);

        while (xmlreader.Read ()) {
            switch (xmlreader.NodeType) {
                case XmlNodeType.Element: {
                    if (! xmlreader.IsEmptyElement) {
                        tags += " " + xmlreader.Name;
                        switch (tags) {
                            case " ofmx Fqy": {
                                aptfreq = new OFM_AptFreq ();
                                break;
                            }
                            case " ofmx Rwy": {
                                rwypair = new OFM_RwyPair ();
                                break;
                            }
                            case " ofmx Rdn": {
                                runway  = new OFM_Runway ();
                                break;
                            }
                        }
                    }
                    break;
                }
                case XmlNodeType.Text: {
                    String val = xmlreader.Value.Trim ();
                    if (val == "") break;
                    switch (tags) {

                        // airport frequencies
                        // - keyed by " ofmx Fqy FqyUid SerUid UniUid txtName" = " ofmx Ahp txtName"
                        case " ofmx Fqy FqyUid SerUid UniUid txtName": {
                            aptfreq._Fqy_FqyUid_SerUid_UniUid_txtName = val;    // "GEILENKIRCHEN"
                            break;
                        }
                        case " ofmx Fqy FqyUid SerUid codeType": {
                            aptfreq._Fqy_FqyUid_SerUid_codeType = val;          // "TWR"
                            break;
                        }
                        case " ofmx Fqy FqyUid valFreqTrans": {
                            aptfreq._Fqy_FqyUid_valFreqTrans = val;             // "120.55"
                            break;
                        }
                        case " ofmx Fqy uomFreq": {
                            aptfreq._Fqy_uomFreq = val;                         // "MHZ"
                            break;
                        }
                        case " ofmx Fqy Cdl txtCallSign": {
                            aptfreq._Fqy_Cdl_txtCallSign = val;                 // "FRISBEE TWR (GE/EN)"
                            break;
                        }

                        // runways:  Rwy=pair;  Rdn=oneend
                        case " ofmx Rwy RwyUid AhpUid codeId": {
                            rwypair._Rwy_RwyUid_AhpUid_codeId = val;
                            break;
                        }
                        case " ofmx Rwy RwyUid txtDesig": {
                            rwypair._Rwy_RwyUid_txtDesig = val;
                            break;
                        }
                        case " ofmx Rwy valLen": {
                            rwypair._Rwy_valLen = val;
                            break;
                        }
                        case " ofmx Rwy valWid": {
                            rwypair._Rwy_valWid = val;
                            break;
                        }
                        case " ofmx Rwy uomDimRwy": {
                            rwypair._Rwy_uomDimRwy = val;
                            break;
                        }
                        case " ofmx Rwy codeComposition":
                        case " ofmx Rwy codePreparation": {
                            rwypair._Rwy_codeCompPrep += " " + val;
                            break;
                        }

                        case " ofmx Rdn RdnUid RwyUid AhpUid codeId": {    // <ETNG>
                            runway._Rdn_RdnUid_RwyUid_AhpUid_codeId = val;
                            break;
                        }
                        case " ofmx Rdn RdnUid RwyUid txtDesig": {    // <09/27>
                            runway._Rdn_RdnUid_RwyUid_txtDesig = val;
                            break;
                        }
                        case " ofmx Rdn RdnUid txtDesig": {    // <09>
                            runway._Rdn_RdnUid_txtDesig = val;
                            break;
                        }
                        case " ofmx Rdn geoLat": {    // <50.96102200N>
                            runway.rwy_beglat = ParseLatLon (val, 'N', 'S');
                            break;
                        }
                        case " ofmx Rdn geoLong": {    // <006.02218200E>
                            runway.rwy_beglon = ParseLatLon (val, 'E', 'W');
                            break;
                        }
                        case " ofmx Rdn valTrueBrg": {    // <090>
                            runway.rwy_truehdg = (int) Math.Round (double.Parse (val));
                            break;
                        }
                    }
                    break;
                }
                case XmlNodeType.EndElement: {
                    if (! tags.EndsWith (" " + xmlreader.Name)) {
                        throw new Exception ("tags <" + tags + "> doesn't end with < " + xmlreader.Name + ">");
                    }
                    switch (tags) {

                        // append frequency to airport's frequency list
                        //    \nTWR: 125.2 MHZ Tower Beverlie
                        case " ofmx Fqy": {
                            if (apts_by_txtName.TryGetValue (aptfreq._Fqy_FqyUid_SerUid_UniUid_txtName, out airport)) {
                                airport.aptfreqs.AddLast (aptfreq);
                            } else {
                                //Console.Error.WriteLine ("no airport " + aptfreq._Fqy_FqyUid_SerUid_UniUid_txtName + " for frequency (" +
                                //      aptfreq._Fqy_FqyUid_SerUid_codeType + " " + aptfreq._Fqy_Cdl_txtCallSign + ")");
                            }
                            airport = null;
                            aptfreq = null;
                            break;
                        }

                        // add rwypair to airport's runway list
                        case " ofmx Rwy": {
                            if (rwypair._Rwy_RwyUid_AhpUid_codeId == null) {
                                throw new Exception ("rwypair missing Rwy_RwyUid_AhpUid_codeId at " + xmlreader.LineNumber);
                            } else if (apts_by_codeId.TryGetValue (rwypair._Rwy_RwyUid_AhpUid_codeId, out airport)) {
                                airport.rwypairs[rwypair._Rwy_RwyUid_txtDesig] = rwypair;
                            } else {
                                Console.Error.WriteLine ("no airport " + rwypair._Rwy_RwyUid_AhpUid_codeId + " for runway");
                            }
                            airport = null;
                            rwypair = null;
                            break;
                        }

                        // add runway endpoint to rwypair of the airport
                        case " ofmx Rdn": {
                            if (! apts_by_codeId.TryGetValue (runway._Rdn_RdnUid_RwyUid_AhpUid_codeId, out airport)) {
                                Console.Error.WriteLine ("no airport " + runway._Rdn_RdnUid_RwyUid_AhpUid_codeId + " for runway");
                            } else if (! airport.rwypairs.TryGetValue (runway._Rdn_RdnUid_RwyUid_txtDesig, out rwypair)) {
                                Console.Error.WriteLine ("no rwypair " + runway._Rdn_RdnUid_RwyUid_txtDesig + " on airport " + airport._Ahp_AhpUid_codeId);
                            } else {
                                if (rwypair._Rwy_RwyUid_txtDesig.StartsWith (runway._Rdn_RdnUid_txtDesig + "/")) rwypair.lorwy = runway;
                                if (rwypair._Rwy_RwyUid_txtDesig.EndsWith   ("/" + runway._Rdn_RdnUid_txtDesig)) rwypair.hirwy = runway;
                            }
                            airport = null;
                            runway  = null;
                            rwypair = null;
                            break;
                        }
                    }
                    int len = xmlreader.Name.Length + 1;
                    tags = tags.Substring (0, tags.Length - len);
                    break;
                }
            }
        }
        filestream.Close ();

        // airport writing is done at the end
        // ...cuz things in pass 2 added to airports
        OpenIntlMetafs ();
        foreach (Airport apt in apts_by_codeId.Values) {
            try {
                apt.WriteRecord (dbcon);
            } catch (Mono.Data.Sqlite.SqliteException se) {
                Console.Error.WriteLine ("error writing airport " + apt.ident + ": " + se.Message.Replace ("\r", "").Replace ("\n", " "));
            }
        }
        CloseIntlMetafs ();
    }

    /**
     * Parse a lat/lon string given as n.nnnH
     *  n.nnn = number of degrees
     *  H = hemisphere
     */
    public static double ParseLatLon (String val, char pos, char neg)
    {
        char endchar = val[val.Length-1];
        double dval = double.Parse (val.Substring (0, val.Length - 1));
        if (endchar == neg) dval = -dval;
        else if (endchar != pos) throw new Exception ("bad latlon " + val);
        return dval;
    }


    /**
     * Execute a non-query SQL statement.
     */
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

    /**
     * Split a comma-separated string, taking into account quoted elements.
     */
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

    // magnetic = true + magvar
    //  positive on east coast us
    //  negative on west coast us
    public static int GetMagVar (double lat, double lon, double elevft)
    {
        DateTime now  = DateTime.UtcNow;
        double sdatyr = now.Year + (now.DayOfYear - 1) / 365.25;
        double latrad = ToRadians (lat);
        double lonrad = ToRadians (lon);
        double elevkm = elevft / 3280.84;
        double mvrads = GeoContext.magnetic_variation (sdatyr, latrad, lonrad, elevkm);
        return (int) Math.Round (ToDegrees (mvrads));
    }

    /************************************************\
     *  International airport METAR/TAF capability  *
     *  Fetched via MeteoStar (see webmetafs.php)   *
    \************************************************/

    public static IDbConnection intlmetafsdbcon;

    public static void OpenIntlMetafs ()
    {
        intlmetafsdbcon = new SqliteConnection ("URI=file:datums/intlmetafs.db");
        intlmetafsdbcon.Open ();
        IDbCommand dbcmd1 = intlmetafsdbcon.CreateCommand ();
        dbcmd1.CommandText = "CREATE TABLE IF NOT EXISTS intlmetafs (iamt_icaoid TEXT NOT NULL PRIMARY KEY,iamt_metaf TEXT NOT NULL,iamt_when INTEGER DEFAULT 0)";
        dbcmd1.ExecuteNonQuery ();
    }

    // returns:
    //    "" : airport generates neither metar nor taf
    //   "M" : airport generates only metar
    //   "T" : airport generates only taf
    //  "MT" : airport generates both metar and taf
    //   "?" : unknown (but IntlMetafsDaemon.cs will eventually find out)
    public static String GetIntlMetaf (String icaoid)
    {
        // filter out non-ICAO ids (exactly 4 letters)
        // assume no metar/taf otherwise
        if (icaoid.Length != 4) return "";
        for (int i = 0; i < 4; i ++) {
            char c = icaoid[i];
            if (c < 'A') return "";
            if (c > 'Z') return "";
        }

        // see if we already know about this airport
        IDbCommand dbcmd1 = intlmetafsdbcon.CreateCommand ();
        dbcmd1.CommandText = "SELECT iamt_metaf FROM intlmetafs WHERE iamt_icaoid=@iamt_icaoid";
        dbcmd1.Parameters.Add (new SqliteParameter ("@iamt_icaoid", icaoid));
        String metaf = (String) dbcmd1.ExecuteScalar ();
        if (metaf == null) {

            // if not, tell IntlMetafsDaemon.cs to find out eventually
            // next time we run hopefully it will have an answer for us
            metaf = "?";
            IDbCommand dbcmd2 = intlmetafsdbcon.CreateCommand ();
            dbcmd2.CommandText = "INSERT OR IGNORE INTO intlmetafs (iamt_icaoid,iamt_metaf) VALUES (@iamt_icaoid,'?')";
            dbcmd2.Parameters.Add (new SqliteParameter ("@iamt_icaoid", icaoid));
            dbcmd2.ExecuteNonQuery ();
        }

        return metaf;
    }

    public static void CloseIntlMetafs ()
    {
        intlmetafsdbcon.Close ();
        intlmetafsdbcon = null;
    }
}
