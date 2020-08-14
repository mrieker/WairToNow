<?php
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

    /**
     * @brief Download an openstreepmap tile
     *        streets.php?tile=zoom/x/y.png
     */

    $datadir = "../webdata/streets";

    writelog ("ip=" . $_SERVER["REMOTE_ADDR"] . " uri=" . $_SERVER["REQUEST_URI"]);

    // single download
    if (isset ($_REQUEST['tile'])) {
        $tile = $_REQUEST['tile'];
        writelog ("tile=$tile");
        $newpath = download ($tile);
        header ('Content-Length: ' . filesize ($newpath));
        header ('Content-Type: image/png');
        readfile ($newpath);
        return;
    }

    // bulk download
    ob_start (NULL, 4000);
    for ($i = 1; isset ($_REQUEST["tile_$i"]); $i ++) {
        $tile = $_REQUEST["tile_$i"];
        writelog ("tile_$i=$tile");
        $path = download ($tile);
        $size = filesize ($path);
        writelog ("$path - sending $size");
        echo "@@tile=$tile\n";
        echo "@@size=$size\n";
        $sent = readfile ($path);
        if ($sent != $size) writelog ("$path - only sent $sent", TRUE);
        echo "@@eof\n";
        writelog ("$path - send complete");
    }
    echo "@@done\n";
    ob_end_flush ();
    exit;

    function download ($tile)
    {
        global $datadir;

        if (strpos ($tile, "..") !== FALSE) return FALSE;

        $parts = explode ('/', $tile);
        if (!is_numeric ($parts[0])) die ("bad or missing zoom level");
        $z = intval ($parts[0]);
        if (($z < 0) || ($z > 17)) die ("bad zoom level $z");

        // see if we already have the file
        // also re-download if file is a year old
        $newpath = "$datadir/$tile";
        writelog ("newpath=$newpath");
        if (!file_exists ($newpath) || (filemtime ($newpath) < time () - 60*60*24*365)) {

            // make sure it has been at least 1.25 seconds since last download or openstreetmap will complain
            $lockfile = fopen ("$datadir/streets.lock", "c+");
            if (! $lockfile) writelog ("error opening lock file", TRUE);
            if (! flock ($lockfile, LOCK_EX)) writelog ("error locking lock file", TRUE);
            $lastdownload = fgets ($lockfile);
            $lastdownload = $lastdownload ? floatval ($lastdownload) : 0;
            writelog ("lastdownload=$lastdownload");
            @time_sleep_until ($lastdownload + 1.25);

            // if not, pick a tile server at random
            // http://wiki.openstreetmap.org/wiki/Slippy_map_tilenames
            $servers = array (
                'a.tile.openstreetmap.org',
                'b.tile.openstreetmap.org',
                'c.tile.openstreetmap.org',
                //'http://a.tile.opencyclemap.org/cycle',
                //'http://b.tile.opencyclemap.org/cycle',
                //'http://c.tile.opencyclemap.org/cycle',
                //'http://otile1.mqcdn.com/tiles/1.0.0/osm',
                //'http://otile2.mqcdn.com/tiles/1.0.0/osm',
                //'http://otile3.mqcdn.com/tiles/1.0.0/osm',
                //'http://otile4.mqcdn.com/tiles/1.0.0/osm',
            );
            $i = rand (0, count ($servers) - 1);

            $hostname = $servers[$i];
            $location = "/$tile";

            // download the tile into temp file
            $timeout = 45;
            writelog ("hostname=$hostname location=$location - connecting");
            $tempath = "$newpath.tmp";
            $fp = fsockopen ($hostname, 80, $errno, $errstr, $timeout);
            if (!$fp) {
                writelog ("hostname=$hostname connect error $errstr", TRUE);
            }
            stream_set_timeout ($fp, $timeout);
            fwrite ($fp, "GET $location HTTP/1.1\r\n");
            fwrite ($fp, "Host: $hostname\r\n");
            fwrite ($fp, "Http-Referer: https://play.google.com/store/apps/details?id=com.outerworldapps.wairtonow\r\n");
            fwrite ($fp, "User-Agent: WairToNow aviation EFB app\r\n");
            fwrite ($fp, "\r\n");

            $status = fgets ($fp);
            $status = trim ($status);
            if (strpos ($status, "HTTP/1.1 200") !== 0) {
                writelog ("- status=$status", FALSE);
                while ($line = fgets ($fp)) {
                    $line = trim ($line);
                    writelog ("- header=$line", FALSE);
                }
                writelog ("- end headers", TRUE);
                fclose ($fp);
            }

            $contentlength = FALSE;
            while (TRUE) {
                $line = fgets ($fp);
                if (!$line) {
                    fclose ($fp);
                    writelog ("- error/timeout reading headers", TRUE);
                }
                $line = strtolower (trim ($line));
                if ($line == "") break;
                writelog ("header=$line");
                if (strpos ($line, "content-length:") === 0) {
                    $contentlength = intval (trim (substr ($line, 15)));
                }
            }
            if (!$contentlength) {
                fclose ($fp);
                writelog ("- missing content-length", TRUE);
            }

            makedirectories ($tempath);
            $of = fopen ($tempath, "w");
            if (!$of) {
                fclose ($fp);
                writelog ("- error creating $tempath", TRUE);
            }
            writelog ("hostname=$hostname location=$location - receiving $contentlength");
            for ($i = 0; $i < $contentlength; $i += $rc) {
                $buff = fread ($fp, $contentlength - $i);
                if ($buff === FALSE) {
                    fclose ($fp);
                    writelog ("hostname=$hostname location=$location read error at $i", TRUE);
                }
                $rc = strlen ($buff);
                if ($rc == 0) {
                    fclose ($fp);
                    writelog ("hostname=$hostname location=$location received null buffer at $i", TRUE);
                }
                fwrite ($of, $buff);
            }
            fclose ($fp);
            fclose ($of);

            rename ($tempath, $newpath);
            writelog ("newpath=$newpath - success");

            $nowbin = microtime (TRUE);
            rewind ($lockfile);
            fputs ($lockfile, "$nowbin\n");
            fclose ($lockfile);
        }
        return $newpath;
    }

    // given a/b/c/d, make sure directories a/, a/b/, a/b/c/ exist
    function makedirectories ($file)
    {
        $parts = explode ("/", $file);
        $n = count ($parts);
        $path  = "";
        for ($i = 0; $i < $n - 1; $i ++) {
            $path .= $parts[$i] . "/";
            if (!file_exists ($path)) {
                writelog ("mkdir $path");
                mkdir ($path);
            }
        }
    }

    function writelog ($msg, $die = FALSE)
    {
        global $datadir;

        /*
        date_default_timezone_set ("UTC");
        $pid = getmypid ();
        $now = date ("Y-m-d@H:i:s");
        $split = explode ("@", $now);
        file_put_contents ("$datadir/streets.log." . $split[0], $split[1] . " $pid $die $msg\n", FILE_APPEND);
        */
        if ($die) {
            ob_end_flush ();
            die ("@@error=$msg\n");
        }
    }
?>
