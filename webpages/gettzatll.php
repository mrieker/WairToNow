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

    function getTZAtLL ($lat, $lon)
    {
        $dbdir = __DIR__ . "/../webdata";

        $lockfile = fopen ("$dbdir/tzforlatlon.lock", "c");
        if (!$lockfile) diedie ("error opening lockfile\n");
        if (!flock ($lockfile, LOCK_EX)) diedie ("error locking lockfile\n");

        $sqldb = new SQLite3 ("$dbdir/tzforlatlon.db");
        if (!$sqldb) diedie ("error opening tzforlatlon.db\n");

        $ilat = intval ($lat * 1000000.0);
        $ilon = intval ($lon * 1000000.0);

        $sqldb->exec ("CREATE TABLE IF NOT EXISTS timezones (lat INTEGER NOT NULL, lon INTEGER NOT NULL, tzname TEXT NOT NULL, PRIMARY KEY (lat, lon))");

        $row = $sqldb->querySingle ("SELECT tzname FROM timezones WHERE lat=$ilat AND lon=$ilon");

        if (!$row) {
            sleep (3);
            $user = trim (file_get_contents ("$dbdir/geonames_username.txt"));
            $repl = file_get_contents ("http://api.geonames.org/timezoneJSON?lat=$lat&lng=$lon&username=$user");
            if (!$repl) exit;
            $json = json_decode ($repl);
            $tzname = isset ($json->timezoneId) ? addslashes (trim ($json->timezoneId)) : "";
            if ($tzname == "") $tzname = "-";
            $sqldb->exec ("INSERT INTO timezones (lat,lon,tzname) VALUES ($ilat,$ilon,'$tzname')");
        } else {
            $tzname = $row;
        }

        $sqldb->close ();

        fclose ($lockfile);

        return ($tzname == "-") ? "" : $tzname;
    }
?>
