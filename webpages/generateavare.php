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
     * Generate an Avare-compatible IAP georeference file.
     */

    $skippwcheck = TRUE;
    require_once 'iaputil.php';

    $apddir   = "datums/apdgeorefs_$cycles28";
    $dbname   = "viewiap/generateavare.db";
    $oldname  = "$datdir/avare_$cycles28.db";
    $areaname = "$datdir/avarea_$cycles28.db";

    /*
     * If we have any CSV file newer than DB file, re-create DB file.
     */
    if (!file_exists ($dbname)) goto needupdate;
    $dbmtime = filemtime ($dbname);
    if ($dbmtime === FALSE) goto needupdate;

    if (filemtime ($oldname)  > $dbmtime) goto needupdate;
    if (filemtime ($areaname) > $dbmtime) goto needupdate;

    $statenames = scandir ($iapdir);
    foreach ($statenames as $statename) {
        if ((strlen ($statename) == 6) && (substr ($statename, 2) == ".csv")) {
            $statemtime = filemtime ("$iapdir/$statename");
            if ($statemtime > $dbmtime) goto needupdate;
        }
    }
    $statenames = scandir ($apddir);
    foreach ($statenames as $statename) {
        if ((strlen ($statename) == 6) && (substr ($statename, 2) == ".csv")) {
            $statemtime = filemtime ("$apddir/$statename");
            if ($statemtime > $dbmtime) goto needupdate;
        }
    }

    /*
     * Download file as is.
     */
    header ("Content-description: file transfer");
    header ("Content-disposition: attachment; filename=\"avareiapgeoref.db\"");
    header ("Content-length: " . filesize ($dbname));
    header ("Content-type: application/x-sqlite3");
    header ("Expires: 0");
    flush ();
    readfile ($dbname);
    exit;

needupdate:;

    echo "<HTML><HEAD><TITLE>Generate Avare Georef Database</TITLE></HEAD><BODY>\n";

    @unlink ($dbname);
    @unlink ("$dbname-journal");
    @unlink ("$dbname.tmp");
    @unlink ("$dbname.tmp-journal");
    $sqldb = new SQLite3 ("$dbname.tmp");
    checkSQLiteError ($sqldb, __LINE__);
    $sqldb->exec ("CREATE TABLE geoplates (proc TEXT NOT NULL PRIMARY KEY, dx REAL NOT NULL, dy REAL NOT NULL, lon REAL NOT NULL, lat REAL NOT NULL)");
    checkSQLiteError ($sqldb, __LINE__);

    $statenames = scandir ($iapdir);
    foreach ($statenames as $statename) {
        if ((strlen ($statename) == 6) && (substr ($statename, 2) == ".csv")) {
            $state = substr ($statename, 0, 2);
            echo "<P>IAP State $state ...</P>\n";
            @flush (); @ob_flush (); @flush ();
            set_time_limit (30);

            $sqldb->exec ("BEGIN");
            checkSQLiteError ($sqldb, __LINE__);

            $statefile = fopen ("$iapdir/$statename", "r");
            $pair = 0;
            while ($lineone = fgets ($statefile)) {
                $linetwo  = fgets ($statefile);
                $pair ++;
                $partsone = QuotedCSVSplit ($lineone);
                $partstwo = QuotedCSVSplit ($linetwo);
                if ((count ($partsone) != 5) || (count ($partstwo) != 5)) {
                    echo "<P>bad counts on pair $pair</P>";
                    break;
                }
                if (($partsone[0] != $partstwo[0]) || ($partsone[1] != $partsone[1])) {
                    echo "<P>mismatch on pair $pair</P>";
                    break;
                }

                $icaoid  = $partsone[0];
                $plateid = $partsone[1];

                $fixid1  = $partsone[2];
                $pixx1   = $partsone[3];
                $pixy1   = $partsone[4];

                $fixid2  = $partstwo[2];
                $pixx2   = $partstwo[3];
                $pixy2   = $partstwo[4];

                $aptinfo = getAptInfo ($icaoid);
                $faaid   = $aptinfo[1];
                $aptlat  = $aptinfo[4];

                $latlon1 = getFixLatLon ($faaid, $fixid1);
                $latlon2 = getFixLatLon ($faaid, $fixid2);

                $lat1    = $latlon1['lat'];
                $lon1    = $latlon1['lon'];
                $lat2    = $latlon2['lat'];
                $lon2    = $latlon2['lon'];

                // get pixels x,y per degree of longitude,latitude
                $pixyperdeglat = -0.5 * hypot ($pixx2 - $pixx1, $pixy1 - $pixy2) /
                                    rad2deg (LatLonDist_rad ($lat1, $lon1, $lat2, $lon2));

                $pixxperdeglon = - $pixyperdeglat * cos (deg2rad ($aptlat));

                // get top left corner lat,lon using those conversions and points
                $topleftlon = ($lon1 - $pixx1 / $pixxperdeglon / 2 +
                               $lon2 - $pixx2 / $pixxperdeglon / 2) / 2;

                $topleftlat = ($lat1 - $pixy1 / $pixyperdeglat / 2 +
                               $lat2 - $pixy2 / $pixyperdeglat / 2) / 2;

                // write Avare database record
                $avareid = str_replace ("IAP-",   "",        $plateid);
                $avareid = str_replace ("(",      "",        $avareid);
                $avareid = str_replace (")",      "",        $avareid);
                $avareid = str_replace (" ",      "-",       $avareid);
                $avareid = str_replace ("ILSDME", "ILS-DME", $avareid);
                $avareid = str_replace ("LDADME", "LDA-DME", $avareid);
                $avareid = str_replace ("LOCDME", "LOC-DME", $avareid);
                $avareid = str_replace ("LOCNDB", "LOC-NDB", $avareid);
                $avareid = str_replace ("NDBDME", "NDB-DME", $avareid);
                $avareid = str_replace ("VORDME", "VOR-DME", $avareid);
                $i = strlen ($avareid);
                if (substr ($avareid, $i - 2) == "LR") $avareid = substr ($avareid, 0, $i - 2) . "L-R";
                $avareid = "$faaid/$avareid.png";

                $q = "INSERT INTO geoplates (proc,dx,dy,lon,lat) VALUES ('$avareid',$pixxperdeglon,$pixyperdeglat,$topleftlon,$topleftlat)";
                $sqldb->exec ($q);
                checkSQLiteError ($sqldb, __LINE__);
            }
            fclose ($statefile);

            $sqldb->exec ("COMMIT");
            checkSQLiteError ($sqldb, __LINE__);
            printTotalRows ($sqldb);
        }
    }

    // get records from original avare database that we are missing
    mergeOldDatabase ($sqldb, $oldname);

    // get AREA.png records from avare
    mergeOldDatabase ($sqldb, $areaname);

    $sqldb->close ();
    rename ("$dbname.tmp", "$dbname");
    echo "<P>done</P>\n";

    echo "<SCRIPT> window.location.href = 'generateavare.php' </SCRIPT>\n";

    function mergeOldDatabase ($sqldb, $oldname)
    {
        echo "<P>merging old $oldname</P>\n";
        @flush (); @ob_flush (); @flush ();
        $olddb = new sqlite3 ($oldname, SQLITE3_OPEN_READONLY);
        checkSQLiteError ($olddb, __LINE__);
        $oldres = $olddb->query ("SELECT * FROM geoplates");
        checkSQLiteError ($olddb, __LINE__);
        $sqldb->exec ("BEGIN");
        checkSQLiteError ($sqldb, __LINE__);
        $n = 0;
        while ($oldrow = $oldres->fetchArray (SQLITE3_ASSOC)) {
            $proc = $oldrow['proc'];
            $dx   = $oldrow['dx'];
            $dy   = $oldrow['dy'];
            $lon  = $oldrow['lon'];
            $lat  = $oldrow['lat'];
            $q = "INSERT OR IGNORE INTO geoplates (proc,dx,dy,lon,lat) VALUES ('$proc',$dx,$dy,$lon,$lat)";
            $sqldb->exec ($q);
            checkSQLiteError ($sqldb, __LINE__);
            if (++ $n == 1000) {
                $n = 0;
                $sqldb->exec ("COMMIT");
                checkSQLiteError ($sqldb, __LINE__);
                $sqldb->exec ("BEGIN");
                checkSQLiteError ($sqldb, __LINE__);
            }
        }
        $olddb->close ();
        $sqldb->exec ("COMMIT");
        checkSQLiteError ($sqldb, __LINE__);

        printTotalRows ($sqldb);
    }

    function printTotalRows ($sqldb)
    {
        $res = $sqldb->querySingle ("SELECT COUNT(*) FROM geoplates");
        echo "<P> nrows $res </P>\n";
    }
?>
