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
     * Manually review IAP georeferencing.
     *
     * Files:
     *   ../webdata/iaputil/good_20160915.db : last month's verified iaps
     *   ../webdata/iaputil/good_20161013.db : this month's iaps
     *       LatLons.lastgood = date/time that is was last verified as good
     *   ../decosects/datums/aptplates_20161013/state/<stateid>.csv : plates to verify
     */

    session_start ();
    require_once 'iaputil.php';

    // true to check avare mapping of the plates as well as our own
    // false to just check our previous mapping of plate
    $checkavare = FALSE; // TRUE;

    if (!empty ($_GET['delgood'])) {
        $icaoid_plate = $_GET['delgood'];
        $gooddb = OpenGoodDB ();
        $gooddb->exec ("DELETE FROM LatLons WHERE icaoid_plate='$icaoid_plate'");
        checkSQLiteError ($gooddb, __LINE__);
        echo "OK\n";
        exit;
    }
?>
<!DOCTYPE html>
<HTML>
    <HEAD>
        <TITLE>IAP Review</TITLE>
        <SCRIPT LANGUAGE=JAVASCRIPT>
            function popUp (icaoid)
            {
                var cycle = <?php echo getAiracCycles28 (); ?>;
                var url = 'http://www.faa.gov/air_traffic/flight_info/aeronav/digital_products/dtpp/search/' +
                        'results/?cycle=' + cycle + '&ident=' + icaoid + '&page=1';
                window.open (url, 'faainfo', 'resizable=yes,scrollbars=yes');
            }

            function okToDel (icaoid_plate)
            {
                var req = new XMLHttpRequest ();
                req.addEventListener ('load', function ()
                {
                    var rt = req.responseText.trim ();
                    if (rt == 'OK') {
                        document.getElementById (icaoid_plate).innerHTML = '';
                    } else {
                        window.alert ('error deleting ' + icaoid_plate + ': ' + rt);
                    }
                });
                req.addEventListener ('error', function ()
                {
                    var st = req.statusText;
                    window.alert ('error deleting ' + icaoid_plate + ': ' + st);
                });
                req.open ('GET', location.href + '?delgood=' +
                        encodeURIComponent (icaoid_plate));
                req.send ();
            }
        </SCRIPT>
    </HEAD>
    <BODY>
        <?php
            /**
             * Compare the current IAP georef info to previously confirmed values.
             */

            echo "<P>$iapdir</P>";
            echo "<P ID=\"count\"></P>";

            if (isset ($_POST['func']) && ($_POST['func'] == 'reset')) {
                unlink ("$datdir/laststate");
                unlink ("$datdir/lastplate");
                unlink ("$datdir/lastcheck");
                unlink ("$datdir/bad.txt");
            }

            if (!isset ($_POST['disp'])) {
                $nloops = 0;
                $toplines = FALSE;
                $topstuff = 0;
            openplate:
                if (++ $nloops > 1000) {
                    endTopStuff ();
                    $nonce = time ();
                    echo <<<END
                        <FORM METHOD=POST ACTION="$thisscript">
                            <INPUT TYPE=HIDDEN NAME="nonce" VALUE="$nonce">
                            <INPUT TYPE=SUBMIT VALUE="more">
                        </FORM>
END;
                    exit;
                }
                echo "<SCRIPT>document.getElementById ('count').innerHTML = '$nloops'</SCRIPT>\n";

                // get next plate to process

                if (!file_exists ("$datdir/lastcheck")) {
                    file_put_contents ("$datdir/lastcheck", time ());
                }

                $statecode = file_get_contents ("$datdir/laststate");
                $lastpos   = intval (file_get_contents ("$datdir/lastplate"));
                if ($statecode) {
                    $statefile = fopen ("$iapdir/$statecode.csv", "r");
                    if (!$statefile) die ("<P>error opening $iapdir/$statecode.csv</P>");
                    if (fseek ($statefile, $lastpos, SEEK_SET) < 0) die ("<P>error positioning $iapdir/$statecode.csv</P>");
                    $stateline = fgets ($statefile);
                }
                if (!$statecode || !$stateline) {
                    $statenames = scandir ($iapdir);
                    foreach ($statenames as $statename) {
                        if ((strlen ($statename) == 6) && (substr ($statename, 2) == ".csv")) {
                            $state = substr ($statename, 0, 2);
                            if ($statecode < $state) {
                                file_put_contents ("$datdir/laststate", $state);
                                file_put_contents ("$datdir/lastplate", "0");
                                goto openplate;
                            }
                        }
                    }
                    endTopStuff ();

                    MissedPlates ();

                    echo "<P>ALL DONE!!</P>";
                    ResetForm ();
                    exit;
                }
                $percent = $lastpos / filesize ("$iapdir/$statecode.csv") * 100;

                // - statecode = two-letter state code
                // - stateline = first line from state file for plate: icaoid,"plate name",fixid,xcord,ycord

                $j = 0;
                for ($i = strlen ($stateline); -- $i >= 0;) {
                    if (($stateline[$i] == ',') && (++ $j == 3)) break;
                }
                $icaoidplate = substr ($stateline, 0, ++ $i);
                $ipx = htmlspecialchars ($icaoidplate);

                // - statecode   = two-letter state code
                // - icaoidplate = icaoid,"plate name",

                $i = strpos ($icaoidplate, ",");
                $icaoid = substr ($icaoidplate, 0, $i);
                $plate  = substr ($icaoidplate, $i);

                // - icaoid = icaoid
                // - plate  = ,"plate name",

                // get airport's faaid and its latitude

                $aptinfo = getAptInfo ($icaoid);
                if (!$aptinfo) {
                    echo "<P>can't find airport $icaoid</P>";
                    ResetForm ();
                    exit;
                }
                $faaid   = $aptinfo[1];
                $aptlat  = floatval ($aptinfo[4]);
                $faaidplate = $faaid . $plate;

                // - faaidplate = faaid,"plate name",

                // get list of fixes found by DecodePlate for this plate and their lat/lon

                $fixes = array ();
                do {
                    $stateparts = explode (",", substr ($stateline, strlen ($icaoidplate)));
                    $fixid = $stateparts[0];                // some fix DecodePlate found on the plate
                    $xcord = intval ($stateparts[1]);       // what x-coord DecodePlate found the fix at
                    $ycord = intval ($stateparts[2]);       // what y-coord DecodePlate found the fix at
                    $ll = getFixLatLon ($faaid, $fixid);    // get fix's lat/lon from the FAA database
                    if (!$ll) {
                        echo "<P>fix $faaid.$fixid not found</P>\n";
                    } else {
                        $lat = $ll['lat'];
                        $lon = $ll['lon'];
                        //echo "<P>fix $faaid.$fixid at $lat/$lon</P>\n";
                        $fixes[$fixid] = array ($xcord, $ycord, $lat, $lon);
                    }

                    $nextpos   = ftell ($statefile);
                    $stateline = fgets ($statefile);
                } while ($stateline && (strpos ($stateline, $icaoidplate) === 0));
                fclose ($statefile);

                // get mapping from lat/lon to pixel as determined by DecodePlate

                $newxfmwft = getLatLonXfm ($aptlat, $fixes);
                if (!$newxfmwft) die ("<P>plate does not have 2 fixes</P>");

                // - fixes = array of fixes and their pixel location in gif image and their lat/lon
                // - nextpos = position in state file for next plate to process

                // get gif image filename

                $gifname = "";
                $giffile = fopen ("$gifdir/state/$statecode.csv", "r");
                while ($gifline = fgets ($giffile)) {
                    if (strpos ($gifline, $faaidplate) === 0) {
                        $gifname = trim (substr ($gifline, strlen ($faaidplate)));
                        break;
                    }
                }
                if ($gifname == "") die ("<P>can't find gif for $faaidplate</P>");
                fclose ($giffile);

                // - gifname = gif_150/whatever.gif

                // if there is an existing good mapping

                $oldgood = 0;
                $olddifs = '';
                $oldmap  = getGoodMapping ($icaoid, $plate);
                if ($oldmap) {

                    // transform each of our new fixes and see if it matches up
                    foreach ($fixes as $fixid => $fix) {

                        // get pixel we now think the fix is at
                        $new_pixx = $fix[0];
                        $new_pixy = $fix[1];

                        // get fix's lat/lon from FAA database
                        $fix_lat = $fix[2];
                        $fix_lon = $fix[3];

                        // calculate pixel the existing mapping thinks the fix is at
                        $old_pixx = LatLon2BitmapX ($oldmap, $fix_lat, $fix_lon);
                        $old_pixy = LatLon2BitmapY ($oldmap, $fix_lat, $fix_lon);

                        // hopefully they match up close enough
                        $diff_pixx = $old_pixx - $new_pixx;
                        $diff_pixy = $old_pixy - $new_pixy;
                        $diff_pix  = intval (sqrt ($diff_pixx * $diff_pixx + $diff_pixy * $diff_pixy) + 0.5);
                        $olddifs  .= ($olddifs == "") ? "(" : "; ";
                        $olddifs  .= "$fixid $diff_pix";
                        if ($diff_pix < 10) $oldgood ++;
                    }
                    if ($olddifs != "") $olddifs .= ")";
                }
                if ($oldgood >= 2) {
                    topStuffLine ("<B>$statecode $ipx</B> already verified $olddifs");
                    MarkImageGood ($icaoid, $plate, $newxfmwft);
                    file_put_contents ("$datdir/lastplate", $nextpos);
                    set_time_limit (30);
                    goto openplate;
                }

                // see if there is an avare entry for this plate
                // if it maps DecodePlate's fixes onto same pixels found by DecodePlate, assume they are correct

                if ($checkavare) {
                    $wairtonow_faaid_plateid = fixAvareId (str_replace (",\"", "/", $faaidplate));
                    $avaregood = 0;
                    $avaredifs = "";
                    $avaremap  = getAvareMapping ($faaid, $plate);
                    if ($avaremap) {

                        // get avare transformation parameters
                        $av_dx  = floatval ($avaremap['dx']);
                        $av_dy  = floatval ($avaremap['dy']);
                        $av_lat = floatval ($avaremap['lat']);
                        $av_lon = floatval ($avaremap['lon']);

                        // transform each of our fixes and see if it matches up
                        foreach ($fixes as $fixid => $fix) {

                            // get pixel wairtonow thinks the fix is at
                            $my_pixx = $fix[0];
                            $my_pixy = $fix[1];

                            // get fix's lat/lon from FAA database
                            $fix_lat = $fix[2];
                            $fix_lon = $fix[3];

                            // calculate pixel avare thinks the fix is at
                            $avare_pixx = ($fix_lon - $av_lon) * $av_dx * $myDpi / $avDpi;
                            $avare_pixy = ($fix_lat - $av_lat) * $av_dy * $myDpi / $avDpi;

                            // hopefully they match up close enough
                            $diff_pixx  = $avare_pixx - $my_pixx;
                            $diff_pixy  = $avare_pixy - $my_pixy;
                            $diff_pix   = intval (sqrt ($diff_pixx * $diff_pixx + $diff_pixy * $diff_pixy) + 0.5);
                            $avaredifs .= ($avaredifs == "") ? "(" : "; ";
                            $avaredifs .= "$fixid $diff_pix";
                            if ($diff_pix < $avLeewy) $avaregood ++;
                        }
                        if ($avaredifs != "") $avaredifs .= ")";
                    }
                    if ($avaregood >= 2) {
                        topStuffLine ("<B>$statecode $ipx</B> verified in avare $avaredifs");
                        if ($olddifs != "") {
                            topStuffLine ("<P>previous verification diffs $olddifs</P>");
                        } else {
                            topStuffLine ("<P>not previously verified</P>");
                        }
                        MarkImageGood ($icaoid, $plate, $newxfmwft);
                        file_put_contents ("$datdir/lastplate", $nextpos);
                        set_time_limit (30);
                        goto openplate;
                    }
                }

                // tell user we are working on it

                endTopStuff ();
                echo "<H3>$statecode $ipx</H3>";
                if ($olddifs != "") {
                    echo "<P>previous verification diffs $olddifs</P>";
                } else {
                    echo "<P>not previously verified</P>";
                }
                if ($checkavare) {
                    if ($avaredifs != "") {
                        echo "<P>avare diffs $avaredifs</P>";
                    } else {
                        echo "<P>not found in avare</P>";
                    }
                }
                echo "<P>$percent %</P>";
                @flush (); @ob_flush (); @flush ();

                // use DecodePlate to generate a marked-up image

                $plate   = str_replace (',"', '', $plate);
                $plate   = str_replace ('",', '', $plate);
                $plateid = fixAvareId ($plate);
                $pngname = "$faaid-$plateid.png";
                @unlink ("$pngdir/$pngname");
                $clpath  = "../decosects/DecodePlate.jar:../decosects/pdfbox-1.8.10.jar:../decosects/commons-logging-1.2.jar";
                $dpcmnd  = "java DecodePlate -cycles28 $cycles28 -cycles56 $cycles56 $faaid '$plate' -markedpng $pngdir/$pngname -verbose";
                $dpfile  = popen ("CLASSPATH=$clpath $dpcmnd 2>&1", "r");
                if (!$dpfile) die ("<P>error spawning DecodePlate</P>");
                $dplog   = "$dpcmnd\n";
                $fixes   = array ();
                while ($dpline = fgets ($dpfile)) {
                    $dplog .= $dpline;

                    // get the fixes used for mapping lat/lon => pixel
                    if (substr ($dpline, 0, 2) == "  ") {
                        $dpline = trim ($dpline);
                        $dpline = explode (' ', $dpline);
                        if ((count ($dpline) == 3) && ($dpline[1] == "at")) {
                            $fixid = $dpline[0];
                            $pix   = explode (',', $dpline[2]);
                            $ll    = getFixLatLon ($faaid, $fixid);
                            if (!$ll) {
                                echo "<P>can't find '[$faaid.]$fixid'</P>";
                            } else {
                                $pixx = intval ($pix[0]);
                                $pixy = intval ($pix[1]);
                                $lat  = floatval ($ll['lat']);
                                $lon  = floatval ($ll['lon']);
                                $fixes[$fixid] = array ($pixx, $pixy, $lat, $lon);
                                echo "<P>$fixid $pixx,$pixy</P>";
                            }
                        }
                    }
                }
                pclose ($dpfile);

                // display image and buttons

                $newxfmwft = getLatLonXfm ($aptlat, $fixes);
                if (!$newxfmwft) {
                    echo "<P>plate does not have 2 fixes</P>";
                } else {
                    $dpx = htmlspecialchars ($dpcmnd);
                    $plx = htmlspecialchars ($plate);
                    echo <<<END
                        <FORM METHOD=POST ACTION="$thisscript">
                            <INPUT TYPE=SUBMIT NAME="disp"     VALUE="good">
                            <INPUT TYPE=SUBMIT NAME="disp"     VALUE="bad">
                            <INPUT TYPE=HIDDEN NAME="aptlat"   VALUE="$aptlat">
                            <INPUT TYPE=HIDDEN NAME="dpcmnd"   VALUE="$dpcmnd">
                            <INPUT TYPE=HIDDEN NAME="gifname"  VALUE="$gifname">
                            <INPUT TYPE=HIDDEN NAME="icaoid"   VALUE="$icaoid">
                            <INPUT TYPE=HIDDEN NAME="nextpos"  VALUE="$nextpos">
                            <INPUT TYPE=HIDDEN NAME="plate"    VALUE="$plx">
                            <INPUT TYPE=HIDDEN NAME="pngname"  VALUE="$pngname">
END;
                    for ($i = 0; $i < 6; $i ++) echo "<INPUT TYPE=HIDDEN NAME=\"xfmwft_$i\" VALUE=\"" . $newxfmwft[$i] . "\">\n";
                    echo "</FORM>\n";
                }
                echo <<<END
                    <IMG SRC="$pngdir/$pngname" WIDTH=1075 HEIGHT=1650>
                    <PRE>$dplog</PRE>
END;
            } else {
                $disp = $_POST['disp'];
                $pngname = $_POST['pngname'];

                unlink ("$pngdir/$pngname");

                // user said the image is bad, save DecodePlate command line for the bad plate
                if ($disp == "bad") {
                    $dpcmnd = $_POST['dpcmnd'];
                    file_put_contents ("$datdir/bad.txt", "$dpcmnd\n", FILE_APPEND);
                }

                // user said the image is good, write lat/lon=>pixel mapping to local database
                if ($disp == "good") {
                    $newxfmwft = array ();
                    for ($i = 0; $i < 6; $i ++) $newxfmwft[$i] = $_POST["xfmwft_$i"];
                    MarkImageGood ($_POST['icaoid'], $_POST['plate'], $newxfmwft);
                }

                // either way, mark this image completed and show next
                $nextpos = $_POST['nextpos'];
                file_put_contents ("$datdir/lastplate", $nextpos);
                echo "<SCRIPT> window.location.assign ('$thisscript') </SCRIPT>";
            }

            /**
             * Output the first 3 lines directly.
             * Buffer subsequent lines, outputting any over 3 beyond that.
             */
            function topStuffLine ($line)
            {
                global $toplines, $topstuff;

                $line = "<P>$line</P>\n";

                if ($topstuff == 0) {
                    $toplines = array ();
                }

                if ($topstuff < 3) {
                    echo $line;
                } else {
                    if ($topstuff == 3) {
                        echo "<DIV ID=\"topstuff\">";
                    }
                    $toplines[$topstuff] = $line;
                    while (count ($toplines) > 3) {
                        foreach ($toplines as $n => $line) {
                            echo $line;
                            unset ($toplines[$n]);
                            break;
                        }
                    }
                }
                $topstuff ++;
                @flush (); @ob_flush (); @flush ();
            }

            /**
             * Flush any lines buffered by topStuffLine(),
             * replacing those before it with '...' so the
             * stuff at end of page can be easily seen.
             */
            function endTopStuff ()
            {
                global $toplines, $topstuff;

                if ($topstuff > 3) {
                    echo "</DIV>";
                    if ($topstuff > 8) {
                        echo "<SCRIPT> document.getElementById ('topstuff').innerHTML = '...' </SCRIPT>\n";
                    }
                    foreach ($toplines as $line) {
                        echo $line;
                    }
                    $topstuff = 0;
                }
            }

            /**
             * Display list of plates previously validated that weren't
             * validated this time.
             */
            function MissedPlates ()
            {
                global $datdir;

                $lastcheck = trim (file_get_contents ("$datdir/lastcheck"));
                $gooddb = OpenGoodDB ();
                $missed = $gooddb->query ("SELECT icaoid_plate FROM LatLons WHERE lastcheck < $lastcheck");
                if (!$missed) die ("<P>good.db SELECT error</P>");
                $first = TRUE;
                $lasticaoid = "";
                while ($row = $missed->fetchArray (SQLITE3_ASSOC)) {
                    $icaoid_plate = $row['icaoid_plate'];
                    $parts  = explode (':', $icaoid_plate);
                    $icaoid = $parts[0];
                    $plate  = substr ($icaoid_plate, strlen ($icaoid) + 1);
                    $delerr = '';
                    if (PlateMarkedWithD ($icaoid, $plate)) {
                        if ($gooddb->exec ("DELETE FROM LatLons WHERE icaoid_plate='$icaoid_plate'")) continue;
                        $delerr = "<BR>error deleting $icaoid_plate: " . $gooddb->lastErrorMsg ();
                    }
                    if ($first) {
                        echo "<P><B>Missing plates:</B></P><UL>\n";
                        echo "<P>(try <TT>./singleiap.php &lt;icao-id&gt;</TT> to re-attempt download ";
                        echo "of missing plates.)</P>\n";
                        $first = FALSE;
                    }
                    if ($lasticaoid != $icaoid) {
                        $lasticaoid = $icaoid;
                        $whatwehave = $gooddb->query ("SELECT icaoid_plate FROM LatLons WHERE icaoid_plate>'$icaoid:' AND icaoid_plate<'$icaoid;' AND lastcheck >= $lastcheck");
                        if (!$whatwehave) die ("<P>good.db SELECT error</P>");
                        echo "<BR>\n";
                        while ($rowhave = $whatwehave->fetchArray (SQLITE3_ASSOC)) {
                            $have_icaoid_plate = $rowhave['icaoid_plate'];
                            echo "have $have_icaoid_plate<BR>\n";
                        }
                    }
                    echo "<SPAN ID=\"$icaoid_plate\"><LI><INPUT TYPE=BUTTON ONCLICK=\"popUp('$icaoid')\" VALUE=\"$icaoid\">$plate ";
                    echo "<INPUT TYPE=BUTTON VALUE=\"D'LEET\" ONCLICK=\"okToDel ('$icaoid_plate')\"></LI></SPAN>\n";
                    echo $delerr;
                }
                if ($first) {
                    echo "<P>No missing plates since last check.</P>";
                } else {
                    echo "</UL>";
                }
            }

            /**
             * See if the plate is marked 'D' on the FAA webpage.
             * Fortunately we have datafiles with the parsed info.
             */
            function PlateMarkedWithD ($icaoid, $plateid)
            {
                $aptinfo = getAptInfo ($icaoid);
                if (!$aptinfo) return FALSE;
                $faaid   = $aptinfo[1];
                $cycle   = getAiracCycles28 ();
                $gapfile = fopen ("datums/getaptplates_$cycle/$faaid.dat", 'r');
                if (!$gapfile) return FALSE;
                while ($parmsline = fgets ($gapfile)) {
                    $parmsline = trim ($parmsline);
                    $parms     = explode (' ', $parmsline);
                    $plateline = str_replace ('/', '', trim (fgets ($gapfile)));
                    $itsplate  = $parms[4] . '-' . $plateline;
                    if ($itsplate == $plateid) {
                        fclose ($gapfile);
                        return $parms[0] == 'D';
                    }
                }
                fclose ($gapfile);
                return FALSE;
            }

            /**
             * Get transform matrix given two lat/lon => pixelx,y mappings.
             */
            function getLatLonXfm ($aptlat, $fixes)
            {
                if (count ($fixes) != 2) return FALSE;
                $fix1 = $fix0 = FALSE;
                foreach ($fixes as $fix) {
                    $fix0 = $fix1;
                    $fix1 = $fix;
                }
                $newxfmwft = SetLatLon2Bitmap ($aptlat,
                        $fix0[2], $fix0[3], $fix1[2], $fix1[3],
                        $fix0[0], $fix0[1], $fix1[0], $fix1[1]);
                return $newxfmwft;
            }

            function ResetForm ()
            {
                echo <<<END
                    <FORM METHOD=POST ACTION="$thisscript">
                        <INPUT TYPE=SUBMIT NAME="func" VALUE="reset">
                    </FORM>
END;
            }
        ?>
    </BODY>
</HTML>
