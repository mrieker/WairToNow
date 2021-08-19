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
        $i100lat = round ($lat * 100.0);
        $i100lon = round ($lon * 100.0);
        return getTZAtI100LL ($i100lat, $i100lon);
    }

    function getTZAtI100LL ($i100lat, $i100lon)
    {
        $dbdir = __DIR__ . "/../webdata";

        $lockfile = fopen ("$dbdir/tzforlatlon.lock", "c");
        if (!$lockfile) diedie ("error opening lockfile\n");
        if (!flock ($lockfile, LOCK_EX)) diedie ("error locking lockfile\n");

        $sqldb = new SQLite3 ("$dbdir/tzforlatlon.db");
        if (!$sqldb) diedie ("error opening tzforlatlon.db\n");

        $sqldb->exec ("CREATE TABLE IF NOT EXISTS timezones (i100lat INTEGER NOT NULL, i100lon INTEGER NOT NULL, tzname TEXT NOT NULL, saved INTEGER NOT NULL, PRIMARY KEY (i100lat, i100lon))");

        $row = $sqldb->querySingle ("SELECT tzname FROM timezones WHERE i100lat=$i100lat AND i100lon=$i100lon");

        if (!$row) {
            sleep (1);
            $lat  = sprintf ("%.2f", $i100lat / 100.0);
            $lon  = sprintf ("%.2f", $i100lon / 100.0);
            $user = trim (file_get_contents ("$dbdir/geonames_username.txt"));
            $repl = file_get_contents ("http://api.geonames.org/timezoneJSON?lat=$lat&lng=$lon&username=$user");
            if (!$repl) exit;
            $json = json_decode ($repl);
            $tzname = isset ($json->timezoneId) ? addslashes (trim ($json->timezoneId)) : "";
            if ($tzname == "") $tzname = "-";
            $now = time ();
            $sqldb->exec ("INSERT INTO timezones (i100lat,i100lon,tzname,saved) VALUES ($i100lat,$i100lon,'$tzname',$now)");
        } else {
            $tzname = $row;
        }

        $sqldb->close ();

        fclose ($lockfile);

        return ($tzname == "-") ? "" : $tzname;
    }
?>
