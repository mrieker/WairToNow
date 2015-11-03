<?php
    //$_SESSION['isok'] = TRUE;

    $thisscript = $_SERVER['PHP_SELF'];

    if (!isset ($skippwcheck) || !$skippwcheck) {
        if (isset ($_POST['password'])) {
            $pw = trim (file_get_contents ("../webdata/password.dat"));
            $_SESSION['isok'] = ($_POST['password'] == $pw);
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
    $pngdir  = "iaputil";

    $cycles28 = intval (file_get_contents ("datums/aptplates_expdate.dat"));
    $cycles56 = intval (file_get_contents ("datums/aptinfo_expdate.dat"));

    $aptname = "datums/airports_$cycles56.csv";
    $gifdir  = "datums/aptplates_$cycles28";
    $iapdir  = "datums/iapgeorefs_$cycles28";

    /**
     * User has said a plate image is good, save in database.
     * @param $icaoid = airport icao id
     * @param $plate = plate id string, with leading ," and trailing ",
     * @param $xfmwft = mapping array from SetLatLon2Bitmap ()
     */
    function MarkImageGood ($icaoid, $plate, $xfmwft)
    {
        if (strpos  ($plate, ',"') ===  0) $plate = substr ($plate, 2);
        $i = strlen ($plate) - 2;
        if (strrpos ($plate, '",') === $i) $plate = substr ($plate, 0, $i);

        $a = $xfmwft[0];
        $b = $xfmwft[1];
        $c = $xfmwft[2];
        $d = $xfmwft[3];
        $e = $xfmwft[4];
        $f = $xfmwft[5];

        $sqldb = OpenGoodDB ();
        $sqldb->exec ("INSERT OR REPLACE INTO LatLons (icaoid_plate,a,b,c,d,e,f) VALUES ('$icaoid:$plate',$a,$b,$c,$d,$e,$f)");
        checkSQLiteError ($sqldb, __LINE__);
    }

    /**
     * Get previously verified mapping for the given plate.
     */
    function getGoodMapping ($icaoid, $plate)
    {
        if (strpos  ($plate, ',"') ===  0) $plate = substr ($plate, 2);
        $i = strlen ($plate) - 2;
        if (strrpos ($plate, '",') === $i) $plate = substr ($plate, 0, $i);

        $sqldb  = OpenGoodDB ();
        $result = $sqldb->querySingle ("SELECT a,b,c,d,e,f FROM LatLons WHERE icaoid_plate='$icaoid:$plate'", TRUE);
        if (!is_array ($result) || (count ($result) != 6)) return FALSE;

        $xfmwft = array ();
        $xfmwft[0] = floatval ($result['a']);
        $xfmwft[1] = floatval ($result['b']);
        $xfmwft[2] = floatval ($result['c']);
        $xfmwft[3] = floatval ($result['d']);
        $xfmwft[4] = floatval ($result['e']);
        $xfmwft[5] = floatval ($result['f']);
        return $xfmwft;
    }

    /**
     * Database that contains one record per verified plate,
     * giving the six xfmwft values.
     */
    function OpenGoodDB ()
    {
        global $cycles56, $datdir;

        static $sqldb = FALSE;

        if ($sqldb === FALSE) {
            if (!file_exists ("$datdir/good_$cycles56.db")) {
                $sqldb = new SQLite3 ("$datdir/good_$cycles56.db");
                checkSQLiteError ($sqldb, __LINE__);
                $sqldb->query ("CREATE TABLE LatLons (icaoid_plate TEXT NOT NULL PRIMARY KEY, " .
                        "a REAL NOT NULL, b REAL NOT NULL, c REAL NOT NULL, " .
                        "d REAL NOT NULL, e REAL NOT NULL, f REAL NOT NULL)");
                checkSQLiteError ($sqldb, __LINE__);
            } else {
                $sqldb = new SQLite3 ("$datdir/good_$cycles56.db");
                checkSQLiteError ($sqldb, __LINE__);
            }
        }
        return $sqldb;
    }

    /**
     * See if a .../good/...gz (as written by MarkImageGood()) file exists and it matches the image.
     */
    function ImageMarkedGood ($faaid, $icaoid, $newfixes, $goodname, $gifname)
    {
        global $boxsize, $usLeewy;

        if (!file_exists ($goodname)) return FALSE;

        $goodfile = gzopen ($goodname, "r");

        // open gif image file

        $image = OpenGifTrueColor ($gifname, $width, $height);

        // make sure image width and height matches
        $goodline = fgets ($goodfile);
        if ($goodline != "$width,$height\n") goto goodisbad;

        // check pixels around each fix saved in the good file

        $oldfixes = array ();
        while ($goodline = fgets ($goodfile)) {

            // read fixid,xcord,ycord and save the mapping

            $goodline = explode (",", $goodline);
            $fixid = $goodline[0];
            $xcord = intval ($goodline[1]);
            $ycord = intval ($goodline[2]);
            $ll = getFixLatLon ($faaid, $fixid);
            if (!$ll) goto goodisbad;
            $oldfixes[] = array ($fixid, $xcord, $ycord, $ll['lat'], $ll['lon']);

            // make sure the corresponding pixel values match

            for ($y = $ycord - $boxsize / 2; $y <= $ycord + $boxsize / 2; $y ++) {
                $goodline = fgets ($goodfile);
                if (!$goodline) goto goodisbad;
                $pixels = explode (",", $goodline);
                $i = 0;
                for ($x = $xcord - $boxsize / 2; $x <= $xcord + $boxsize / 2; $x ++) {
                    $rgb = 0;
                    if (($y >= 0) && ($y < $height) && ($x >= 0) && ($x < $width)) {
                        $rgb = imagecolorat ($image, $x, $y);
                    }
                    if ($rgb != $pixels[$i++]) goto goodisbad;
                }
            }
        }

        // if all the old fixes are identical to the new fixes, it is good as is

        foreach ($oldfixes as $oldfix) {
            $fixid = $oldfix[0];
            if (!isset ($newfixes[$fixid])) goto newisdiff;
            $newfixvals = $newfixes[$fixid];
            if ($newfixvals[0] != $oldfix[1]) goto newisdiff;
            if ($newfixvals[1] != $oldfix[2]) goto newisdiff;
        }
        gzclose ($goodfile);
        return TRUE;

        // different fixes, see if that lat/lon mapping is the same

    newisdiff:
        // set up lat/lon => pixel transformation
        if (count ($oldfixes) != 2) goto goodisbad;
        $oldfix0 = $oldfixes[0];
        $oldfix1 = $oldfixes[1];
        $avglat  = getFixLatLon ($faaid, $icaoid)['lat'];
        $xfmwft  = SetLatLon2Bitmap ($avglat,
                $oldfix0[3], $oldfix0[4], $oldfix1[3], $oldfix1[4],
                $oldfix0[1], $oldfix0[2], $oldfix1[1], $oldfix1[2]);

        $difftot = 0;
        foreach ($newfixes as $fixid => $newfix) {
            $xcord = $newfix[0];
            $ycord = $newfix[1];
            $lat   = $newfix[2];
            $lon   = $newfix[3];
            $xcomp = LatLon2BitmapX ($xfmwft, $lat, $lon);
            $ycomp = LatLon2BitmapY ($xfmwft, $lat, $lon);
            $difftot += hypot ($xcomp - $xcord, $ycomp - $ycord);
        }
        $difftot /= count ($newfixes);
        if ($difftot > $usLeewy) goto goodisbad;

        // different fixes but lat/lon mapping matches the old fix mapping,
        // so store the new fixes as verified

        MarkImageGood ($gifname, $goodname, $newfixes);
        return TRUE;

        // mismatch of sorts, discard old good file and display image for verification

    goodisbad:
        gzclose ($goodfile);
        // unlink ($goodname);
        return FALSE;
    }

    /**
     * Open a gif file then convert to true color bitmap so we get actual pixel colors,
     * not gif colortable indices which might change even though color doesn't change.
     */
    function OpenGifTrueColor ($gifname, &$width, &$height)
    {
        global $gifdir;

        // open original un-marked gif
        $imagegif = imagecreatefromgif ("$gifdir/$gifname.p1");
        if (!$imagegif) die ("<P>error reading $gifdir/$gifname.p1</P>");
        $width  = imagesx ($imagegif);
        $height = imagesy ($imagegif);

        // convert to true-color
        $imagetru = imagecreatetruecolor ($width, $height);
        imagecopy ($imagetru, $imagegif, 0, 0, 0, 0, $width, $height);
        imagedestroy ($imagegif);

        return $imagetru;
    }

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
        global $cycles56, $datdir;

        static $sqldb = FALSE;

        if (!$sqldb) {
            if (!file_exists ("$datdir/latlon_$cycles56.db")) {
                echo "<P>generating lat/lon sqlite file</P>\n";
                @flush (); @ob_flush (); @flush ();

                @unlink ("$datdir/latlon_$cycles56.db.tmp");
                $sqldb = new SQLite3 ("$datdir/latlon_$cycles56.db.tmp");
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
                $aptfile = fopen ("datums/airports_$cycles56.csv", "r");
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
                $fixfile = fopen ("datums/fixes_$cycles56.csv", "r");
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
                $locfile = fopen ("datums/localizers_$cycles56.csv", "r");
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
                $navfile = fopen ("datums/navaids_$cycles56.csv", "r");
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
                $rwyfile = fopen ("datums/runways_$cycles56.csv", "r");
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

                rename ("$datdir/latlon_$cycles56.db.tmp", "$datdir/latlon_$cycles56.db");
            }

            $sqldb = new SQLite3 ("$datdir/latlon_$cycles56.db", SQLITE3_OPEN_READONLY);
            checkSQLiteError ($sqldb, __LINE__);
        }

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
     * Read a plate's avare mapping from database.
     */
    function getAvareMapping ($faaid, $plate)
    {
        global $cycles28, $datdir;

        static $sqldb = FALSE;

        if (!$sqldb) {

            // make copy of avare database with modified plate ids that we can look up
            if (!file_exists ("$datdir/avare_$cycles28.db")) {

                // download latest zip file from Avare
                echo "<P>Downloading Avare geoplates</P>\n";
                $netfile = FALSE;
                for ($cycle4 = 1520; $cycle4 > 1501; -- $cycle4) {
                    $netfile = @fopen ("http://208.113.226.170/new/$cycle4/geoplates.zip", "r");
                    if ($netfile) break;
                }
                if (!$netfile) die ("error opening Avare geoplates.zip\n");
                @unlink ("$datdir/avare/geoplates.zip");
                $zipfile = fopen ("$datdir/avare/geoplates.zip", "w");
                if (!$zipfile) die ("error creating $datdir/avare/geoplates.zip\n");
                while ($zipbuff = fread ($netfile, 4096)) {
                    fwrite ($zipfile, $zipbuff);
                }
                fclose ($netfile);
                fclose ($zipfile);

                // extract SQLite3 file from zip file
                echo "<P>Extracting Avare geoplates</P>\n";
                $zipfile = new ZipArchive ();
                $rc = $zipfile->open ("$datdir/avare/geoplates.zip");
                if ($rc !== TRUE) die ("error $rc opening $datdir/avare/geoplates.zip\n");
                @unlink ("$datdir/avare/geoplates.db");
                $rc = $zipfile->extractTo ("$datdir/avare/", array ('geoplates.db'));
                if (!$rc) die ("error extracting geoplates.db from $datdir/avare/geoplates.zip\n");
                $zipfile->close ();

                // open the downloaded Avare database
                echo "<P>Converting Avare geoplates</P>\n";
                $olddb = new SQLite3 ("$datdir/avare/geoplates.db", SQLITE3_OPEN_READONLY);
                checkSQLiteError ($olddb, __LINE__);
                $oldres = $olddb->query ("SELECT * FROM geoplates");
                checkSQLiteError ($olddb, __LINE__);

                // create a similar database
                @unlink ("$datdir/avare_$cycles28.db.tmp");
                @unlink ("$datdir/avare_$cycles28.db.tmp-journal");
                $sqldb = new SQLite3 ("$datdir/avare_$cycles28.db.tmp");
                checkSQLiteError ($sqldb, __LINE__);
                $sqldb->exec ("CREATE TABLE Maps (faaid_plate TEXT PRIMARY KEY, dx REAL NOT NULL, dy REAL NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL)");
                checkSQLiteError ($sqldb, __LINE__);

                // copy records from one database to the other
                // convert the plateids to lowest common denominator
                $sqldb->query ("BEGIN");
                $n = 0;
                while ($row = $oldres->fetchArray (SQLITE3_ASSOC)) {
                    $procparts = explode ('/', $row['proc']);
                    $dx  = $row['dx'];
                    $dy  = $row['dy'];
                    $lon = $row['lon'];
                    $lat = $row['lat'];
                    $fid = $procparts[0];
                    $pid = fixAvareId ($procparts[1]);
                    $sqldb->exec ("INSERT OR IGNORE INTO Maps (faaid_plate,dx,dy,lat,lon) VALUES ('$fid/$pid',$dx,$dy,$lat,$lon)");
                    checkSQLiteError ($sqldb, __LINE__);
                    if (++ $n % 1000 == 0) {
                        $sqldb->query ("COMMIT");
                        checkSQLiteError ($sqldb, __LINE__);
                        $sqldb->query ("BEGIN");
                        checkSQLiteError ($sqldb, __LINE__);
                    }
                }
                $olddb->close ();
                if ($n == 0) die ("no records in downloaded Avare database\n");
                echo "<P>$n records extracted from Avare database</P>\n";

                $sqldb->query ("COMMIT");
                checkSQLiteError ($sqldb, __LINE__);
                $sqldb->close ();
                rename ("$datdir/avare_$cycles28.db.tmp", "$datdir/avare_$cycles28.db");
            }

            // open the converted database
            $sqldb = new SQLite3 ("$datdir/avare_$cycles28.db", SQLITE3_OPEN_READONLY);
            checkSQLiteError ($sqldb, __LINE__);
        }

        // look up record using lowest common denominator for the plateid
        $plate = fixAvareId ($plate);
        $result = $sqldb->querySingle ("SELECT dx,dy,lat,lon FROM Maps WHERE faaid_plate='$faaid/$plate'", TRUE);
        checkSQLiteError ($sqldb, __LINE__);
        if ($result  && is_array ($result)) return $result;
        return FALSE;
    }

    function checkSQLiteError ($sqldb, $line)
    {
        if ($sqldb->lastErrorCode () != 0) {
            die ($line . ': ' . $sqldb->lastErrorMsg () . "\n");
        }
    }

    /**
     * Reduce a plate id string to lowest common denominator
     * between Avare and WairToNow.
     */
    function fixAvareId ($id)
    {
        $id = str_replace ("IAP-", "", $id);
        $id = str_replace (" ",    "", $id);
        $id = str_replace ("-",    "", $id);
        $id = str_replace (".png", "", $id);
        $id = str_replace ("(",    "", $id);
        $id = str_replace (")",    "", $id);
        $id = str_replace ("\"",   "", $id);
        $id = str_replace (",",    "", $id);
        return $id;
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

    /*
     * Copied from PlateView.java
     */
    function SetLatLon2Bitmap ($avglat, $ilat, $ilon, $jlat, $jlon, $ipixelx, $ipixely, $jpixelx, $jpixely)
    {
        $avglatcos = cos (deg2rad ($avglat));

        $inorthing = $ilat;                 // 60m units north of equator
        $ieasting  = $ilon * $avglatcos;    // 60m units east of prime meridian
        $jnorthing = $jlat;                 // 60m units north of equator
        $jeasting  = $jlon * $avglatcos;    // 60m units east of prime meridian

        // determine transform to convert northing/easting to pixel

        //                     [ wfta' wftb' 0 ]
        //  [ east north 1 ] * [ wftc' wftd' 0 ] = [ pixx pixy 1 ]
        //                     [ wfte' wftf' 1 ]

        //   wfta * lon + wftc * lat + wfte = pixx  as given in LatLon2BitmapX()
        //   wftb * lon + wftd * lat + wftf = pixy  as given in LatLon2BitmapY()

        //   wfta' * easting + wftc' * northing + wfte' = pixx
        //   wftb' * easting + wftd' * northing + wftf' = pixy

        // we want to allow only scaling, rotation, translation, but we also want to invert pixy axis:
        //    wfta' + wftd' = 0
        //    wftb' - wftc' = 0

        $mat = array (
                array (1,  0, 0, 1, 0, 0, 0),
                array (0, -1, 1, 0, 0, 0, 0),
                array ($ieasting, 0, $inorthing, 0, 1, 0, $ipixelx),
                array (0, $ieasting, 0, $inorthing, 0, 1, $ipixely),
                array ($jeasting, 0, $jnorthing, 0, 1, 0, $jpixelx),
                array (0, $jeasting, 0, $jnorthing, 0, 1, $jpixely)
        );

        rowreduce ($mat);

        $xfmwft = array ($mat[0][6], $mat[1][6], $mat[2][6], $mat[3][6], $mat[4][6], $mat[5][6]);

        // convert the wfta and wftb to include factor to convert longitude => easting

        $xfmwft[0] *= $avglatcos;
        $xfmwft[1] *= $avglatcos;

        // invert for reverse transform

        //                  [ wfta wftb 0 ]
        //  [ lon lat 1 ] * [ wftc wftd 0 ] = [ pixx pixy 1 ]
        //                  [ wfte wftf 1 ]

        //                  [ wfta wftb 0 ]   [ tfwa tfwb 0 ]                     [ tfwa tfwb 0 ]
        //  [ lon lat 1 ] * [ wftc wftd 0 ] * [ tfwc tfwd 0 ] = [ pixx pixy 1 ] * [ tfwc tfwd 0 ]
        //                  [ wfte wftf 1 ]   [ tfwe tfwf 1 ]                     [ tfwe tfwf 1 ]

        //                                    [ tfwa tfwb 0 ]
        //  [ lon lat 1 ] = [ pixx pixy 1 ] * [ tfwc tfwd 0 ]
        //                                    [ tfwe tfwf 1 ]

        //   tfwa * pixx + tfwc * pixy + tfwe = lon
        //   tfwb * pixx + tfwd * pixy + tfwf = lat

        $rev = array (
                array ($xfmwft[0], $xfmwft[1], 0, 1, 0, 0),
                array ($xfmwft[2], $xfmwft[3], 0, 0, 1, 0),
                array ($xfmwft[4], $xfmwft[5], 1, 0, 0, 1)
        );

        rowreduce ($rev);

        $xfmwft[ 6] = $rev[0][3];   // tfwa
        $xfmwft[ 7] = $rev[0][4];   // tfwb
        $xfmwft[ 8] = $rev[1][3];   // tfwc
        $xfmwft[ 9] = $rev[1][4];   // tfwd
        $xfmwft[10] = $rev[2][3];   // tfwe
        $xfmwft[11] = $rev[2][4];   // tfwf

        return $xfmwft;
    }

    function LatLon2BitmapX ($xfmwft, $lat, $lon)
    {
        return $xfmwft[0] * $lon + $xfmwft[2] * $lat + $xfmwft[4];
    }
    function LatLon2BitmapY ($xfmwft, $lat, $lon)
    {
        return $xfmwft[1] * $lon + $xfmwft[3] * $lat + $xfmwft[5];
    }

    function BitmapXY2Lat ($xfmwft, $bmx, $bmy)
    {
        return $xfmwft[7] * $bmx + $xfmwft[9] * $bmy + $xfmwft[11];
    }
    function BitmapXY2Lon ($xfmwft, $bmx, $bmy)
    {
        return $xfmwft[6] * $bmx + $xfmwft[8] * $bmy + $xfmwft[10];
    }

    /**
     * Matrix functions taken from Lib.java.
     */
    function matmul ($lh, $rh)
    {
        $lhRows = count ($lh);
        $lhCols = count ($lh[0]);
        $rhRows = count ($rh);
        $rhCols = count ($rh[0]);

        if ($lhCols != $rhRows) return FALSE;

        $pr = array ($lhRows);

        for ($i = 0; $i < $lhRows; $i ++) {
            $pr_i_ = array ($rhCols);
            $lh_i_ = $lh[$i];
            for ($j = 0; $j < $rhCols; $j ++) {
                $sum = 0;
                for ($k = 0; $k < $lhCols; $k ++) {
                    $sum += $lh_i_[$k] * $rh[$k][$j];
                }
                $pr_i_[$j] = $sum;
            }
            $pr[$i] = $pr_i_;
        }

        return $pr;
    }

    function rowreduce (&$T)
    {
        $trows = count ($T);
        $tcols = count ($T[0]);

        for ($row = 0; $row < $trows; $row ++) {

            /*
             * Make this row's major diagonal column 1.0 by
             * swapping it with a row below that has the
             * largest value in this row's major diagonal
             * column, then dividing the row by that number.
             */
            $pivot = $T[$row][$row];
            $bestRow = $row;
            for ($swapRow = $row; ++ $swapRow < $trows;) {
                $swapPivot = $T[$swapRow][$row];
                if (abs ($pivot) < abs ($swapPivot)) {
                    $pivot   = $swapPivot;
                    $bestRow = $swapRow;
                }
            }
            if ($pivot == 0.0) return FALSE;
            if ($bestRow != $row) {
                $tmp = $T[$row];
                $T[$row] = $T[$bestRow];
                $T[$bestRow] = $tmp;
            }
            if ($pivot != 1.0) {
                for ($col = $row; $col < $tcols; $col ++) {
                    $T[$row][$col] /= $pivot;
                }
            }

            /*
             * Subtract this row from all below it such that we zero out
             * this row's major diagonal column in all rows below.
             */
            for ($rr = $row; ++ $rr < $trows;) {
                $pivot = $T[$rr][$row];
                if ($pivot != 0.0) {
                    for ($cc = $row; $cc < $tcols; $cc ++) {
                        $T[$rr][$cc] -= $pivot * $T[$row][$cc];
                    }
                }
            }
        }

        for ($row = $trows; -- $row >= 0;) {
            for ($rr = $row; -- $rr >= 0;) {
                $pivot = $T[$rr][$row];
                if ($pivot != 0.0) {
                    for ($cc = $row; $cc < $tcols; $cc ++) {
                        $T[$rr][$cc] -= $pivot * $T[$row][$cc];
                    }
                }
            }
        }

        return TRUE;
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
     * Compute great-circle true course from one lat/lon to another lat/lon
     * @return true course (in degrees) at source point
     */
    function LatLonTC ($srcLat, $srcLon, $dstLat, $dstLon)
    {
        return LatLonTC_rad ($srcLat, $srcLon, $dstLat, $dstLon) * 180.0 / M_PI;
    }
    function LatLonTC_rad ($srcLat, $srcLon, $dstLat, $dstLon)
    {
        // http://en.wikipedia.org/wiki/Great-circle_navigation
        $sLat = $srcLat / 180 * M_PI;
        $sLon = $srcLon / 180 * M_PI;
        $fLat = $dstLat / 180 * M_PI;
        $fLon = $dstLon / 180 * M_PI;
        $dLon = $fLon - $sLon;
        $t1 = cos($sLat) * tan($fLat);
        $t2 = sin($sLat) * cos($dLon);
        return atan2 (sin ($dLon), $t1 - $t2);
    }

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
