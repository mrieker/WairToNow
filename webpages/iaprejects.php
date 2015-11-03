<?php session_start (); ?>
<!DOCTYPE html>
<HTML>
    <HEAD><TITLE>IAP Rejects</TITLE></HEAD>
    <BODY>
        <?php
            require_once 'iaputil.php';

            if (isset ($_POST['func']) && ($_POST['func'] == 'reset')) {
                unlink ("$datdir/laststate");
                unlink ("$datdir/lastplate");
                unlink ("$datdir/bad.txt");
            }

            if (isset ($_POST['disp'])) {
                $why       = str_replace (" ", "", $_POST['disp']);
                $statecode = $_POST['statecode'];
                $faaid     = $_POST['faaid'];
                $plate     = $_POST['plate'];
                $nextpos   = $_POST['nextpos'];
                @mkdir ("$datdir/iaprejects");
                file_put_contents ("$datdir/iaprejects/$statecode.$why", "$faaid,\"$plate\"\n", FILE_APPEND);
                file_put_contents ("$datdir/lastplate", $nextpos);
                echo "<SCRIPT> window.location.assign ('$thisscript') </SCRIPT>\n";
                exit;
            }

            {
            openplate:

                // get next plate to process

                $statecode = file_get_contents ("$datdir/laststate");
                $lastpos   = intval (file_get_contents ("$datdir/lastplate"));
                if ($statecode) {
                    $statefile = fopen ("$iapdir/$statecode.rej", "r");
                    if (!$statefile) {
                        echo "<P>error opening $iapdir/$statecode.csv</P>";
                        resetForm ();
                        exit;
                    }
                    if (fseek ($statefile, $lastpos, SEEK_SET) < 0) die ("<P>error positioning $iapdir/$statecode.csv</P>");
                    $stateline = fgets ($statefile);
                }
                if (!$statecode || !$stateline) {
                    $statenames = scandir ($iapdir);
                    foreach ($statenames as $statename) {
                        if ((strlen ($statename) == 6) && (substr ($statename, 2) == ".rej")) {
                            $state = substr ($statename, 0, 2);
                            if ($statecode < $state) {
                                file_put_contents ("$datdir/laststate", $state);
                                file_put_contents ("$datdir/lastplate", "0");
                                goto openplate;
                            }
                        }
                    }
                    echo "<P>ALL DONE!!</P>";
                    resetForm ();
                    exit;
                }
                $nextpos = ftell ($statefile);

                // - statecode = two-letter state code
                // - stateline = line from state file for plate: faaid,"plate",pdfname,fix fix ...

                $columns = QuotedCSVSplit (trim ($stateline));
                $faaid   = $columns[0];
                $plate   = $columns[1];
                $fixids  = explode (' ', $columns[3]);

                // see if already classified

                $rejnames = scandir ("$datdir/iaprejects");
                foreach ($rejnames as $rejname) {
                    if (strpos ($rejname, "$statecode.") === 0) {
                        $rejfile = fopen ("$datdir/iaprejects/$rejname", "r");
                        while ($rejline = fgets ($rejfile)) {
                            if ($rejline == "$faaid,\"$plate\"\n") {
                                fclose ($rejfile);
                                $why = substr ($rejname, 3);
                                echo"<P><B>$faaid $plate</B> already classified as $why</P>\n";
                                file_put_contents ("$datdir/lastplate", $nextpos);
                                goto openplate;
                            }
                        }
                        fclose ($rejfile);
                    }
                }

                // generate marked-up png with DecodePlate

                echo "<H3>$statecode $faaid " . htmlspecialchars ($plate) . "</H3>\n";
                @flush (); @ob_flush (); @flush ();

                $plateid = fixAvareId ($plate);
                $pngname = "$faaid-$plateid.png";
                @unlink ("$pngdir/$pngname");
                $clpath  = "../decosects/DecodePlate.jar:../decosects/pdfbox-1.8.10.jar:../decosects/commons-logging-1.2.jar";
                $dpcmnd  = "java DecodePlate $faaid '$plate' -markedpng $pngdir/$pngname -verbose";
                $dpfile  = popen ("CLASSPATH=$clpath $dpcmnd 2>&1", "r");
                if (!$dpfile) die ("<P>error spawning DecodePlate</P>");
                $dplog   = "$dpcmnd\n";
                $linedtext = TRUE;
                while ($dpline = fgets ($dpfile)) {
                    $dplog .= $dpline;
                    if ((strpos ($dpline, "*:") === FALSE) &&
                        (strpos ($dpline, "java ") !== 0) &&
                        (strpos ($dpline, "panel size ") !== 0) &&
                        (strpos ($dpline, "marker ") !== 0) &&
                        (strpos ($dpline, "feeder facilities ") !== 0) &&
                        (strpos ($dpline, "unsupported PDF ") !== 0) &&
                        (strpos ($dpline, "fence at ") !== 0) &&
                        (strpos ($dpline, "no best pair ") !== 0)) {
                        $linedtext = FALSE;
                    }
                }
                pclose ($dpfile);

                // check for lined-text-only plate

                if ($linedtext) {
                    echo "<P> - lined text</P>\n";
                    $why = "linedtext";
                    @mkdir ("$datdir/iaprejects");
                    file_put_contents ("$datdir/iaprejects/$statecode.$why", "$faaid,\"$plate\"\n", FILE_APPEND);
                    file_put_contents ("$datdir/lastplate", $nextpos);
                    goto openplate;
                }

                // see if there is an avare entry for this plate

                $avaremap = getAvareMapping ($faaid, $plate);
                if ($avaremap) {

                    // get avare transformation parameters
                    $av_dx  = floatval ($avaremap['dx']);
                    $av_dy  = floatval ($avaremap['dy']);
                    $av_lat = floatval ($avaremap['lat']);
                    $av_lon = floatval ($avaremap['lon']);

                    echo "<P>avare mapping found</P>";

                    // open png bitmap

                    $imagepng = imagecreatefrompng ("$pngdir/$pngname");
                    $colorIt  = imagecolorallocate ($image, 0, 255, 0);

                    // transform each of our fixes and see if it matches up
                    foreach ($fixids as $fixid) {
                        if ($fixid == '') continue;

                        // get fix's lat/lon from FAA database
                        $ll = getFixLatLon ($faaid, $fixid);
                        if (!$ll) continue;
                        $fix_lat = $ll['lat'];
                        $fix_lon = $ll['lon'];

                        // calculate pixel avare thinks the fix is at
                        $avare_pixx = intval (($fix_lon - $av_lon) * $av_dx * 300 / $avDpi + 0.5);
                        $avare_pixy = intval (($fix_lat - $av_lat) * $av_dy * 300 / $avDpi + 0.5);

                        echo "<P>avare $fixid: $fix_lat/$fix_lon $avare_pixx,$avare_pixy</P>\n";

                        // plot on image
                        imagefilledellipse ($image, $avare_pixx, $avare_pixy, 10, 10, $colorIt);
                        imagestring ($image, 5, $avare_pixx + 6, $avare_pixy + 2, $fixid, $colorIt);
                    }

                    // write image back to file with avare mapped points
                    imagepng ($image, "$pngdir/$pngname");
                }

                // display buttons and image
                echo <<<END
                    <FORM METHOD=POST ACTION="$thisscript">
                        <INPUT TYPE=HIDDEN NAME="statecode" VALUE="$statecode">
                        <INPUT TYPE=HIDDEN NAME="faaid"     VALUE="$faaid">
                        <INPUT TYPE=HIDDEN NAME="plate"     VALUE="$plate">
                        <INPUT TYPE=HIDDEN NAME="nextpos"   VALUE="$nextpos">
                        <P><INPUT TYPE=SUBMIT NAME="disp" VALUE="bad box off"></P>
                        <P><INPUT TYPE=SUBMIT NAME="disp" VALUE="bad drawing"></P>
                        <P><INPUT TYPE=SUBMIT NAME="disp" VALUE="cartoon bubble"></P>
                        <P><INPUT TYPE=SUBMIT NAME="disp" VALUE="centerline"></P>
                        <P><INPUT TYPE=SUBMIT NAME="disp" VALUE="feeder facilities"></P>
                        <P><INPUT TYPE=SUBMIT NAME="disp" VALUE="fixed now"></P>
                        <P><INPUT TYPE=SUBMIT NAME="disp" VALUE="it is ok as is"></P>
                        <P><INPUT TYPE=SUBMIT NAME="disp" VALUE="lined text"></P>
                        <P><INPUT TYPE=SUBMIT NAME="disp" VALUE="missed marker"></P>
                        <P><INPUT TYPE=SUBMIT NAME="disp" VALUE="navaid missing"></P>
                        <P><INPUT TYPE=SUBMIT NAME="disp" VALUE="one fix"></P>
                        <P><INPUT TYPE=SUBMIT NAME="disp" VALUE="should work"></P>
                    </FORM>
                    <IMG SRC="$pngdir/$pngname">
                    <PRE>$dplog</PRE>
END;
            }

            function resetForm ()
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
