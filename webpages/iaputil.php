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

    $thisscript = $_SERVER['PHP_SELF'];

    if (!isset ($skippwcheck) || !$skippwcheck) {
        if (isset ($_REQUEST['password'])) {
            $pw = trim (file_get_contents ("../webdata/password.dat"));
            $_SESSION['isok'] = ($pw != '') && ($_REQUEST['password'] == $pw);
        }
        if (!isset ($argv[1]) && empty ($_SESSION['isok'])) {
            echo <<<END
                <FORM METHOD=POST ACTION="$thisscript">
                    <INPUT TYPE=PASSWORD NAME="password">
                    <INPUT TYPE=SUBMIT VALUE="log in">
                </FORM>
END;
            exit;
        }
        $_SESSION['isok'] = time ();
    }

    $avDpi   = 150;
    $avLeewy = 30;
    $usLeewy = 10;
    $myDpi   = 300;
    $boxsize = 64;
    $datdir  = "../webdata/iaputil";

    $cycles28 = 0;
    $prevcy28 = 0;
    $datumfns = scandir ("datums");
    foreach ($datumfns as $fn) {
        if (strpos ($fn, "aptplates_") === 0) {
            $i = intval (substr ($fn, 10, 8));
            if ($cycles28 < $i) $cycles28 = $i;
        }
    }
    foreach ($datumfns as $fn) {
        if (strpos ($fn, "aptplates_") === 0) {
            $i = intval (substr ($fn, 10, 8));
            if ($i == $cycles28) continue;
            if ($prevcy28 < $i) $prevcy28 = $i;
        }
    }

    $aptname = "datums/airports_$cycles28.csv";
    $gifdir  = "datums/aptplates_$cycles28";
    $iap2dir = "datums/iapgeorefs2_$cycles28";

    /**
     * Read a fix's lat/lon from the FAA database.
     * @param faaid = airport faaid (in case fix is a runway or other redundant fix name)
     * @param fixid = airport icaoid,navaid,localizer,fix identifier
     *                optionally followed by [dmedist/hdgtrue
     * @returns FALSE: not found
     *           else: array ['lat'], ['lon']
     */
    function getFixLatLon ($faaid, $fixid)
    {
        static $sqldb = FALSE;
        if (!$sqldb) $sqldb = openLatLonDatabase ();

        // strip off any DME info
        $i = strpos ($fixid, '[');
        if ($i !== FALSE) {
            $j = strpos ($fixid, '/', $i);
            if ($j !== FALSE) {
                $dmedist = floatval (substr ($fixid, $i + 1, $j - $i - 1));
                $hdgtrue = floatval (substr ($fixid, $j + 1));
                $fixid   = substr ($fixid, 0, $i);
            }
        }

        // read from database.  there may be more than one fix with that name.
        // it might be a runway so check for those too.
        $result = $sqldb->query ("SELECT lat,lon FROM LatLons WHERE fixid='$fixid' OR fixid='$faaid.$fixid'");
        checkSQLiteError ($sqldb, __LINE__);

        // in case of multiple hits, pick closest one to airport
        $aptlat   = FALSE;
        $aptlon   = FALSE;
        $bestdist = FALSE;
        $bestrow  = FALSE;
        while ($row = $result->fetchArray (SQLITE3_ASSOC)) {
            if (!$bestrow) $bestrow = $row;
            else {
                if (!$aptlat) {
                    $resapt = $sqldb->querySingle ("SELECT lat,lon FROM Airports WHERE faaid='$faaid'", TRUE);
                    checkSQLiteError ($sqldb, __LINE__);
                    if (!$resapt) {
                        echo "<P>airport $faaid not found</P>\n";
                        return FALSE;
                    }
                    $aptlat = floatval ($resapt['lat']);
                    $aptlon = floatval ($resapt['lon']);
                    $bestdist = LatLonDist ($aptlat, $aptlon, $bestrow['lat'], $bestrow['lon']);
                }
                $fixlat = $row['lat'];
                $fixlon = $row['lon'];
                $dist   = LatLonDist ($aptlat, $aptlon, $fixlat, $fixlon);
                //echo "<P>multiple <B>$fixid $fixlat $fixlon</B> near <B>$faaid $aptlat $aptlon</B> ($dist nm)</P>\n";
                if ($bestdist > $dist) {
                    $bestdist = $dist;
                    $bestrow  = $row;
                }
            }
        }
        $result->finalize ();

        if (($bestrow !== FALSE) && ($i !== FALSE) && ($j !== FALSE)) {
            $baselat = floatval ($bestrow['lat']);
            $baselon = floatval ($bestrow['lon']);
            $bestrow['lat'] = LatHdgDist2Lat ($baselat, $hdgtrue, $dmedist);
            $bestrow['lon'] = LatLonHdgDist2Lon ($baselat, $baselon, $hdgtrue, $dmedist);
        }

        return $bestrow;
    }

    /**
     * Get airport ICAO id given an FAA id
     */
    function getIcaoId ($faaid)
    {
        static $sqldb = FALSE;
        if (!$sqldb) $sqldb = openLatLonDatabase ();

        $result = $sqldb->querySingle ("SELECT icaoid FROM Airports WHERE faaid='$faaid'", TRUE);
        checkSQLiteError ($sqldb, __LINE__);

        if (!$result) return FALSE;
        return $result['icaoid'];
    }

    /**
     * Open database of lat/lon -> ident tables.
     */
    function openLatLonDatabase ()
    {
        global $cycles28, $datdir;

        if (!file_exists ("$datdir/latlon_$cycles28.db")) {
            echo "<P>generating lat/lon sqlite file</P>\n";
            @flush (); @ob_flush (); @flush ();

            @unlink ("$datdir/latlon_$cycles28.db.tmp");
            $sqldb = new SQLite3 ("$datdir/latlon_$cycles28.db.tmp");
            checkSQLiteError ($sqldb, __LINE__);

            $sqldb->exec ("CREATE TABLE LatLons (fixid TEXT, lat REAL NOT NULL, lon REAL NOT NULL)");
            checkSQLiteError ($sqldb, __LINE__);
            $sqldb->exec ("CREATE INDEX LatLon_fixid ON LatLons (fixid)");
            checkSQLiteError ($sqldb, __LINE__);

            $sqldb->exec ("CREATE TABLE Airports (faaid TEXT, icaoid NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL)");
            checkSQLiteError ($sqldb, __LINE__);
            $sqldb->exec ("CREATE INDEX Airport_faaid ON Airports (faaid)");
            checkSQLiteError ($sqldb, __LINE__);
            $sqldb->exec ("CREATE INDEX Airport_icaoid ON Airports (icaoid)");
            checkSQLiteError ($sqldb, __LINE__);

            $n = 0;
            $sqldb->exec ("BEGIN");

            echo "<P> - airports</P>\n";
            @flush (); @ob_flush (); @flush ();
            $aptfile = fopen ("datums/airports_$cycles28.csv", "r");
            while ($aptline = fgets ($aptfile)) {
                $cols   = QuotedCSVSplit ($aptline);
                $icaoid = $cols[0];  // eg, KBVY
                $afaaid = $cols[1];
                $lat    = $cols[4];
                $lon    = $cols[5];
                $sqldb->exec ("INSERT INTO LatLons (fixid,lat,lon) VALUES ('$icaoid',$lat,$lon)");
                checkSQLiteError ($sqldb, __LINE__);
                $sqldb->exec ("INSERT INTO Airports (faaid,icaoid,lat,lon) VALUES ('$afaaid','$icaoid',$lat,$lon)");
                checkSQLiteError ($sqldb, __LINE__);
                if (++ $n == 1024) {
                    $sqldb->exec ("COMMIT");
                    $n = 0;
                    $sqldb->exec ("BEGIN");
                    set_time_limit (30);
                }
            }
            fclose ($aptfile);

            echo "<P> - fixes</P>\n";
            @flush (); @ob_flush (); @flush ();
            $fixfile = fopen ("datums/fixes_$cycles28.csv", "r");
            while ($fixline = fgets ($fixfile)) {
                $cols  = QuotedCSVSplit ($fixline);
                $fixid = $cols[0];  // eg, BOSOX
                $lat   = $cols[1];
                $lon   = $cols[2];
                $sqldb->exec ("INSERT INTO LatLons (fixid,lat,lon) VALUES ('$fixid',$lat,$lon)");
                checkSQLiteError ($sqldb, __LINE__);
                if (++ $n == 1024) {
                    $sqldb->exec ("COMMIT");
                    $n = 0;
                    $sqldb->exec ("BEGIN");
                    set_time_limit (30);
                }
            }
            fclose ($fixfile);

            echo "<P> - localizers</P>\n";
            @flush (); @ob_flush (); @flush ();
            $locfile = fopen ("datums/localizers_$cycles28.csv", "r");
            while ($locline = fgets ($locfile)) {
                $cols  = QuotedCSVSplit ($locline);
                $fixid = $cols[1];  // eg, I-BVY
                $lat   = $cols[4];
                $lon   = $cols[5];
                if (($lat == '') || ($lon == '')) continue;
                $sqldb->exec ("INSERT INTO LatLons (fixid,lat,lon) VALUES ('$fixid',$lat,$lon)");
                checkSQLiteError ($sqldb, __LINE__);
                if (++ $n == 1024) {
                    $sqldb->exec ("COMMIT");
                    $n = 0;
                    $sqldb->exec ("BEGIN");
                    set_time_limit (30);
                }
            }
            fclose ($locfile);

            echo "<P> - navaids</P>\n";
            @flush (); @ob_flush (); @flush ();
            $navfile = fopen ("datums/navaids_$cycles28.csv", "r");
            while ($navline = fgets ($navfile)) {
                $cols  = QuotedCSVSplit ($navline);
                $fixid = $cols[1];  // eg, LWM
                $lat   = $cols[4];
                $lon   = $cols[5];
                $sqldb->exec ("INSERT INTO LatLons (fixid,lat,lon) VALUES ('$fixid',$lat,$lon)");
                checkSQLiteError ($sqldb, __LINE__);
                if (++ $n == 1024) {
                    $sqldb->exec ("COMMIT");
                    $n = 0;
                    $sqldb->exec ("BEGIN");
                    set_time_limit (30);
                }
            }
            fclose ($navfile);

            echo "<P> - runways</P>\n";
            @flush (); @ob_flush (); @flush ();
            $rwyfile = fopen ("datums/runways_$cycles28.csv", "r");
            while ($rwyline = fgets ($rwyfile)) {
                $cols     = QuotedCSVSplit ($rwyline);
                $rwyfaaid = $cols[0];  // eg, BVY
                $rwynumid = $cols[1];  // eg, 09
                $lat      = $cols[4];
                $lon      = $cols[5];
                $sqldb->exec ("INSERT INTO LatLons (fixid,lat,lon) VALUES ('$rwyfaaid.RW$rwynumid',$lat,$lon)");
                checkSQLiteError ($sqldb, __LINE__);
                if (++ $n == 1024) {
                    $sqldb->exec ("COMMIT");
                    $n = 0;
                    $sqldb->exec ("BEGIN");
                    set_time_limit (30);
                }
            }
            fclose ($rwyfile);

            echo "<P> - finished</P>\n";
            @flush (); @ob_flush (); @flush ();
            $sqldb->exec ("COMMIT");
            $sqldb->close ();

            rename ("$datdir/latlon_$cycles28.db.tmp", "$datdir/latlon_$cycles28.db");
        }

        $sqldb = new SQLite3 ("$datdir/latlon_$cycles28.db", SQLITE3_OPEN_READONLY);
        checkSQLiteError ($sqldb, __LINE__);
        return $sqldb;
    }

    function checkSQLiteError ($sqldb, $line)
    {
        if ($sqldb->lastErrorCode () != 0) {
            die ($line . ': ' . $sqldb->lastErrorMsg () . "\n");
        }
    }

    /**
     * Read airport info given an ICAO id.
     *   [0] = ICAO id
     *   [1] = FAA id
     *   [2] = elevation
     *   [3] = airport name
     *   [4] = lat
     *   [5] = lon
     *   [6] = variation
     *   [7] = long description
     *   [8] = 2-letter state code
     */
    function getAptInfo ($icaoid)
    {
        global $aptname;

        static $aptinfoarray = FALSE;

        if ($aptinfoarray === FALSE) {
            $aptinfoarray = array ();
            $aptfile = fopen ($aptname, "r");
            while ($aptline = fgets ($aptfile)) {
                $aptparts = QuotedCSVSplit (trim ($aptline));
                $aptinfoarray[$aptparts[0]] = $aptparts;
            }
            fclose ($aptfile);
        }

        if (!isset ($aptinfoarray[$icaoid])) return FALSE;
        return $aptinfoarray[$icaoid];
    }

    /**
     * Get current AIRAC cycle number.
     * https://en.wikipedia.org/wiki/Aeronautical_Information_Publication
     */
    function getAiracCycle ($when = FALSE)
    {
        $cycleyear  = 2014;         // cycle 1401 ...
        $cyclemonth = 1;            // ... started at ...
        $cycletime  = 1389258060;   // 2014-01-09@09:01:00 UTC'

        if ($when === FALSE) $when = time ();
        if ($when < $cycletime) return FALSE;

        $oldtz = @date_default_timezone_get ();
        date_default_timezone_set ('UTC');

        while (($nexttime = $cycletime + 60*60*24*28) <= $when) {
            $cycletime = $nexttime;
            $cyclemonth ++;
            $nextyear = intval (date ("Y", $nexttime));
            if ($cycleyear < $nextyear) {
                $cycleyear  = $nextyear;
                $cyclemonth = 1;
            }
        }

        date_default_timezone_set ($oldtz);

        return ($cycleyear - 2000) * 100 + $cyclemonth;
    }

    function getAiracCycles28 ()
    {
        global $cycles28;

        // get unix timestamp at end of 28-day cycle
        $year28 = intval ($cycles28 / 10000);
        $mon28  = intval ($cycles28 / 100) % 100;
        $day28  = $cycles28 % 100;
        $oldtz  = @date_default_timezone_get ();
        date_default_timezone_set ('UTC');
        $time28 = mktime (12, 0, 0, $mon28, $day28, $year28);
        date_default_timezone_set ($oldtz);

        // back it up a few days to be in middle of cycle
        $time28 -= 14*24*60*60;

        // return AIRAC cycle number for the $cycles28 value
        return getAiracCycle ($time28);
    }

    /**
     * Split a comma-separated value string into its various substrings.
     * @param line = comma-separated line
     * @return array of the various substrings
     */
    function QuotedCSVSplit ($line)
    {
        $len    = strlen ($line);
        $cols   = array ();
        $quoted = FALSE;
        $escapd = FALSE;
        $sb     = '';
        for ($i = 0;; $i ++) {
            $c = ($i < $len) ? $line[$i] : FALSE;
            if (!$escapd && ($c == '"')) {
                $quoted = !$quoted;
                continue;
            }
            if (!$escapd && ($c == '\\')) {
                $escapd = TRUE;
                continue;
            }
            if ((!$escapd && !$quoted && ($c == ',')) || ($c === FALSE)) {
                $cols[] = $sb;
                if ($c === FALSE) break;
                $sb = '';
                continue;
            }
            if ($escapd && ($c == 'n')) $c = "\n";
            if ($escapd && ($c == 'z')) $c = 0;
            $sb .= $c;
            $escapd = FALSE;
        }
        return $cols;
    }
    /**
     * Compute great-circle distance between two lat/lon co-ordinates
     * @return distance between two points (in nm)
     */
    function LatLonDist ($srcLat, $srcLon, $dstLat, $dstLon)
    {
        return rad2deg (LatLonDist_rad ($srcLat, $srcLon, $dstLat, $dstLon)) * 60.0;
    }
    function LatLonDist_rad ($srcLat, $srcLon, $dstLat, $dstLon)
    {
        // http://en.wikipedia.org/wiki/Great-circle_distance
        $sLat = $srcLat / 180 * M_PI;
        $sLon = $srcLon / 180 * M_PI;
        $fLat = $dstLat / 180 * M_PI;
        $fLon = $dstLon / 180 * M_PI;
        $dLon = $fLon - $sLon;
        $t1   = Sq (cos ($fLat) * sin ($dLon));
        $t2   = Sq (cos ($sLat) * sin ($fLat) - sin ($sLat) * cos ($fLat) * cos ($dLon));
        $t3   = sin ($sLat) * sin ($fLat);
        $t4   = cos ($sLat) * cos ($fLat) * cos ($dLon);
        return atan2 (sqrt ($t1 + $t2), $t3 + $t4);
    }

    function Sq ($x) { return $x*$x; }

    /**
     * Compute new lat/lon given old lat/lon, heading (degrees), distance (nautical miles)
     * http://stackoverflow.com/questions/7222382/get-lat-long-given-current-point-distance-and-bearing
     */
    function LatHdgDist2Lat ($latdeg, $hdgdeg, $distnm)
    {
        $distrad = deg2rad ($distnm / 60.0);
        $latrad  = deg2rad ($latdeg);
        $hdgrad  = deg2rad ($hdgdeg);

        $latrad = asin (sin ($latrad) * cos ($distrad) + cos ($latrad) * sin ($distrad) * cos ($hdgrad));
        return rad2deg ($latrad);
    }
    function LatLonHdgDist2Lon ($latdeg, $londeg, $hdgdeg, $distnm)
    {
        $distrad = deg2rad ($distnm / 60.0);
        $latrad  = deg2rad ($latdeg);
        $hdgrad  = deg2rad ($hdgdeg);

        $newlatrad = asin (sin ($latrad) * cos ($distrad) + cos ($latrad) * sin ($distrad) * cos ($hdgrad));
        $lonrad = atan2 (sin ($hdgrad) * sin ($distrad) * cos ($latrad), cos ($distrad) - sin ($latrad) * sin ($newlatrad));
        return rad2deg ($lonrad) + $londeg;
    }
?>
