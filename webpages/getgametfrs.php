<?php
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

    $data = "../webdata/tfrs";
    @mkdir ($data);
    chdir ($data);

    date_default_timezone_set ("UTC");

    // make sure only one runs at a time
    $lockfile = fopen ("getgametfrs.lock", "c");
    if (!$lockfile) diedie ("error opening lockfile\n");
    if (!flock ($lockfile, LOCK_EX)) diedie ("error locking lockfile\n");

    // see if we already have today's datafile
    $now = time ();
    $day = date ("Ymd", $now);
    if (! file_exists ("gametfrs_$day.db.gz")) {
        createNewGameTfrsDB ($now, $day);
        system ("gzip -c gametfrs_$day.db > gametfrs_$day.gz");
        rename ("gametfrs_$day.gz", "gametfrs_$day.db.gz");
    }

    fclose ($lockfile);
    readfile ("gametfrs_$day.db.gz");
    exit;

    /**
     * Create new gametfrs_$day.db file.
     */
    function createNewGameTfrsDB ($now, $day)
    {
        @unlink ("gametfrs_$day.db");
        $sqldb = new SQLite3 ("gametfrs_$day.db");
        if (!$sqldb) diedie ("error creating gametfrs.db");

        // write scan time to the database
        // this is how GupdThread knows it has the one for today
        $sqldb->exec ("CREATE TABLE asof (as_of INTEGER NOT NULL)");
        $sqldb->exec ("INSERT INTO asof (as_of) VALUES ($now)");

        // create table for the game tfrs
        $sqldb->exec ("CREATE TABLE gametfrs (g_eff INTEGER NOT NULL, g_name TEXT NOT NULL, g_lat FLOAT NOT NULL, g_lon FLOAT NOT NULL)");

        // load table from sports websites
        loadSchedule ($sqldb, "mlb");
        loadSchedule ($sqldb, "nascar");
        loadSchedule ($sqldb, "nfl");

        // read game TFR stuff from arcgis.com
        //  webpage:  https://ais-faa.opendata.arcgis.com/datasets/67af16061c014365ae9218c489a321be_0/geoservice
        // gives locations of all stadiums but no time info
        $arcgisjson = file_get_contents ("https://services6.arcgis.com/ssFJjBXIUyZDrSYZ/arcgis/rest/services/Stadiums/FeatureServer/0/query?where=1%3D1&outFields=*&outSR=4326&f=json");
        $arcgiss = array ();
        if ($arcgisjson) {
            $arcgisparsed = json_decode ($arcgisjson);
            foreach ($arcgisparsed->features as $feature) {
                $obj = new stdClass ();
                $obj->name = $feature->attributes->NAME;
                $obj->lat  = $feature->geometry->y;
                $obj->lon  = $feature->geometry->x;
                $arcgiss[] = $obj;
            }
        }

        // remove entries from $arcgiss that match entries in the gametfr database
        // assume they match if lat,lon are less than approx 0.25nm apart
        $result = $sqldb->query ("SELECT g_name,g_lat,g_lon FROM gametfrs");
        while ($row = $result->fetchArray ()) {
            $db_name = $row['g_name'];
            $db_lat  = $row['g_lat'];
            $db_lon  = $row['g_lon'];

            $narcgiss = count ($arcgiss);
            for ($i = 0; $i < $narcgiss; $i ++) {
                $obj = $arcgiss[$i];
                if ($obj) {
                    $diff = hypot ($obj->lat - $db_lat, $obj->lon - $db_lon);
                    if ($diff < 1.0/256.0) {
                        $arcgiss[$i] = FALSE;
                    }
                }
            }
        }

        // add remaining $arcgiss entries to the database without time info
        $sqldb->exec ("BEGIN");
        $infeff = 0xFFFFFFFF;
        $stmt = $sqldb->prepare ("INSERT INTO gametfrs (g_name,g_lat,g_lon,g_eff) VALUES (:name,:lat,:lon,$infeff)");
        foreach ($arcgiss as $obj) {
            if ($obj) {
                $stmt->bindValue (":name", $obj->name, SQLITE3_TEXT);
                $stmt->bindValue (":lat",  $obj->lat,  SQLITE3_FLOAT);
                $stmt->bindValue (":lon",  $obj->lon,  SQLITE3_FLOAT);
                $stmt->execute ();
                $stmt->reset ();
            }
        }
        $sqldb->exec ("COMMIT");
    }

    // load games from sports website schedule into database
    function loadSchedule ($sqldb, $league)
    {
        $csv = popen ("python ../../decosects/gametfr.py $league", "r");
        if (!$csv) diedie ("error spawning gametfr.py $league\n");
        $sqldb->exec ("BEGIN");
        $stmt = $sqldb->prepare ("INSERT INTO gametfrs (g_name,g_lat,g_lon,g_eff) VALUES (:name,:lat,:lon,:eff)");
        while ($line = fgets ($csv)) {
            $split = explode (",", trim ($line));
            $stmt->bindValue (":eff",  intval (intval ($split[0]) / 1000), SQLITE3_INTEGER);
            $stmt->bindValue (":name", $split[1],                          SQLITE3_TEXT);
            $stmt->bindValue (":lat",  floatval ($split[2]),               SQLITE3_FLOAT);
            $stmt->bindValue (":lon",  floatval ($split[3]),               SQLITE3_FLOAT);
            $stmt->execute ();
            $stmt->reset ();
        }
        $rc = pclose ($csv);
        if ($rc != 0) diedie ("gametfr.py $league exit status $rc\n");
        $sqldb->exec ("COMMIT");
    }

    // write error message to STDERR and exit with error status
    function diedie ($msg)
    {
        fprintf (STDERR, "$msg\n");
        exit (1);
    }
?>
