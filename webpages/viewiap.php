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
     * Look at IAP plates.
     * Not needed for normal operation.
     */

    $skippwcheck = TRUE;
    require_once 'iaputil.php';

    if (!empty ($_GET['good'])) {
        $goodname  = $_GET['good'];
        $goodcyc28 = intval (substr ($goodname, 5, 8));
        $goodpath  = "../webdata/iaputil/good_$goodcyc28.db";
        header ("Content-description: file transfer");
        header ("Content-disposition: attachment; filename=\"$goodname\"");
        header ("Content-length: " . filesize ($goodpath));
        header ("Content-type: application/x-sqlite3");
        header ("Expires: 0");
        flush ();
        readfile ($goodpath);
        exit;
    }
?>
<!DOCTYPE html>
<HTML>
    <HEAD>
        <TITLE id="title">View IAP</TITLE>
    </HEAD>
    <BODY>
        <?php
            $iapdirs = array ("WTN" => "datums/iapgeorefs_$cycles28",   // WairToNow generated (DecodePlate.java)
                              "FAA" => "datums/iapgeorefs2_$cycles28"); // FAA generated (DecodePlate2.java)

            $tmpdir = "viewiap";

            if (isset ($_GET['iapid'])) {
                gotIAPId ();
            } elseif (isset ($_GET['icaoid'])) {
                gotAirport ();
            } elseif (isset ($_GET['first'])) {
                gotFirst ();
            } elseif (isset ($_GET['state'])) {
                gotState ();
            } else {
                gotNothing ();
            }

            /**
             * Display menu to select state or first character.
             */
            function gotNothing ()
            {
                global $cycles28, $iapdirs, $tmpdir;

                echo "<P><A HREF=\"viewiap.php\">Top</A></P>";

                $states = array ();
                foreach ($iapdirs as $iapdir) {
                    $statex = scandir ($iapdir);
                    foreach ($statex as $state) {
                        if (strpos ($state, ".csv") == 2) {
                            $state = substr ($state, 0, 2);
                            $states[] = $state;
                        }
                    }
                }
                sort ($states);
                outputLinkTable ("state", $states, 8);

                $firstx = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
                $firsts = array ();
                for ($i = 0; $i < strlen ($firstx); $i ++) $firsts[] = $firstx[$i];
                outputLinkTable ("first", $firsts, 10);

                //echo "<P><A HREF=\"generateavare.php\">Avare Compatible DB</A></P>\n";

                $goodnames = scandir ("../webdata/iaputil");
                $goodlatest = '';
                foreach ($goodnames as $goodname) {
                    if ((strlen ($goodname) == 16) && (substr ($goodname, 0, 5) == 'good_') &&
                            (substr ($goodname, 13) == '.db') && ($goodlatest < $goodname)) {
                        $goodlatest = $goodname;
                    }
                }
                if ($goodlatest > '') {
                    echo "<P><A HREF=\"viewiap.php?good=$goodlatest\">$goodlatest</A></P>\n";
                }
            }

            /**
             * Display menu to select airport for those in a given state.
             */
            function gotState ()
            {
                global $cycles28, $iapdirs, $tmpdir;

                $state = getStateCode ();

                echo "<P><A HREF=\"viewiap.php\">Top</A> $state</P>";
                echo "<SCRIPT> document.getElementById ('title').innerHTML += ': $state' </SCRIPT>\n";

                echo "<UL>";
                foreach ($iapdirs as $type => $iapdir) {
                    if (file_exists ("$iapdir/$state.csv")) {
                        echo "<LI><A HREF=\"$iapdir/$state.csv\">$type CSV file</A>";
                    }
                }
                echo "</UL>\n";

                // get FAA ids of all airports in this state that have approaches
                $refdicaoids = array ();
                $refdfaaids  = array ();

                $goodiaps = array ();   // indexed via icaoid
                foreach ($iapdirs as $iapdir) {
                    $csvfile  = @fopen ("$iapdir/$state.csv", "r");
                    if ($csvfile) {
                        while ($csvline = fgets ($csvfile)) {
                            $columns = explode (",", $csvline);
                            $icaoid  = $columns[0];
                            $iapid   = $columns[1];
                            $refdicaoids[$icaoid] = TRUE;
                            if (!isset ($goodiaps[$icaoid])) $goodiaps[$icaoid] = array ();
                            $goodiaps[$icaoid][$iapid] = TRUE;
                        }
                        fclose ($csvfile);
                    }
                }

                $badiaps = array ();    // indexed via faaid
                foreach ($iapdirs as $iapdir) {
                    $rejfile = @fopen ("$iapdir/$state.rej", "r");
                    if ($rejfile) {
                        while ($rejline = fgets ($rejfile)) {
                            $columns = explode (",", $rejline);
                            $faaid   = $columns[0];
                            $iapid   = $columns[1];
                            $refdfaaids[$faaid] = TRUE;
                            if (!isset ($badiaps[$faaid])) $badiaps[$faaid] = array ();
                            $badiaps[$faaid][$iapid] = TRUE;
                        }
                        fclose ($rejfile);
                    }
                }

                // get ICAO <-> FAA id translation array for airports referenced by the approaches
                $faaids  = array ();
                $icaoids = array ();
                $aptfile = fopen ("datums/airports_$cycles28.csv", "r");
                while ($aptline = fgets ($aptfile)) {
                    $columns = explode (",", $aptline);
                    $icaoid  = $columns[0];
                    $faaid   = $columns[1];
                    if (isset ($refdicaoids[$icaoid]) || isset ($refdfaaids[$faaid])) {
                        $faaids[$icaoid] = $faaid;
                        $icaoids[$faaid] = $icaoid;
                    }
                }
                fclose ($aptfile);

                // get all faaids referenced
                foreach ($refdicaoids as $refdicaoid => $dummy) {
                    $faaid = $faaids[$refdicaoid];
                    $refdfaaids[$faaid] = TRUE;
                }

                // approaches get listed as rejected by FAA generated georef when accepted by WTN generated georef
                // so remove entries from $badiaps[][] what are listed in $goodiaps[][]
                foreach ($badiaps as $faaid => $iapids) {
                    $icaoid = $icaoids[$faaid];
                    foreach ($iapids as $iapid => $dummy) {
                        if (isset ($goodiaps[$icaoid][$iapid])) {
                            unset ($badiaps[$faaid][$iapid]);
                        }
                    }
                }

                // output a link for each such airport
                ksort ($refdfaaids);
                echo "<UL>";
                foreach ($refdfaaids as $faaid => $dummy) {
                    $icaoid = $icaoids[$faaid];
                    $ngood  = isset ($goodiaps[$icaoid]) ? count ($goodiaps[$icaoid]) : 0;
                    $nbad   = isset ($badiaps[$faaid])   ? count ($badiaps[$faaid])   : 0;
                    echo "<LI><A HREF=\"viewiap.php?state=$state&icaoid=$icaoid&faaid=$faaid\">$faaid ($icaoid)</A> ngood:$ngood nbad:$nbad\n";
                }
                echo "</UL>\n";
            }

            /**
             * Display menu to select airport for those beginning with the given character.
             */
            function gotFirst ()
            {
                global $cycles28, $iapdirs, $tmpdir;

                $first = $_GET['first'];

                echo "<P><A HREF=\"viewiap.php\">Top</A> $first</P>";
                echo "<SCRIPT> document.getElementById ('title').innerHTML += ': $first' </SCRIPT>\n";

                // get ICAO <-> FAA id translation arrays for airports beginning with $first
                $icaoids = array ();
                $faaids  = array ();
                $aptfile = fopen ("datums/airports_$cycles28.csv", "r");
                while ($aptline = fgets ($aptfile)) {
                    $columns = explode (",", $aptline);
                    $icaoid  = $columns[0];
                    $faaid   = $columns[1];
                    if (strpos ($faaid, $first) === 0) {
                        $icaoids[$faaid] = $icaoid;
                        $faaids[$icaoid] = $faaid;
                    }
                }
                fclose ($aptfile);

                // get FAA ids of all airports in this state that have approaches
                $gotfaaids = array ();

                $goodiaps = array ();
                foreach ($iapdirs as $iapdir) {
                    $allfnames = scandir ($iapdir);
                    foreach ($allfnames as $fname) {
                        if (substr ($fname, 2) == ".csv") {
                            $state = substr ($fname, 0, 2);
                            $csvfile = fopen ("$iapdir/$fname", "r");
                            while ($csvline = fgets ($csvfile)) {
                                $columns = explode (",", $csvline);
                                $icaoid  = $columns[0];
                                $iapid   = $columns[1];
                                if (isset ($faaids[$icaoid])) {
                                    $faaid = $faaids[$icaoid];
                                    $gotfaaids[$faaid] = $state;
                                    if (!isset ($goodiaps[$faaid])) $goodiaps[$faaid] = array ();
                                    $goodiaps[$faaid][$iapid] = TRUE;
                                }
                            }
                            fclose ($csvfile);
                        }
                    }
                }

                $badiaps = array ();
                foreach ($iapdirs as $iapdir) {
                    $allfnames = scandir ($iapdir);
                    foreach ($allfnames as $fname) {
                        if (substr ($fname, 2) == ".rej") {
                            $state = substr ($fname, 0, 2);
                            $rejfile = fopen ("$iapdir/$fname", "r");
                            while ($rejline = fgets ($rejfile)) {
                                $columns = explode (",", $rejline);
                                $faaid   = $columns[0];
                                $iapid   = $columns[1];
                                if (isset ($icaoids[$faaid]) && !isset ($goodiaps[$faaid][$iapid])) {
                                    $icaoid = $icaoids[$faaid];
                                    $gotfaaids[$faaid] = $state;
                                    if (!isset ($badiaps[$faaid])) $badiaps[$faaid] = array ();
                                    $badiaps[$faaid][$iapid] = TRUE;
                                }
                            }
                            fclose ($rejfile);
                        }
                    }
                }

                // output a link for each such airport
                ksort ($gotfaaids);
                echo "<UL>";
                foreach ($gotfaaids as $faaid => $state) {
                    $icaoid = $icaoids[$faaid];
                    $ngood  = isset ($goodiaps[$faaid]) ? count ($goodiaps[$faaid]) : 0;
                    $nbad   = isset ($badiaps[$faaid])  ? count ($badiaps[$faaid])  : 0;
                    echo "<LI><A HREF=\"viewiap.php?first=$first&state=$state&icaoid=$icaoid&faaid=$faaid\">$faaid ($icaoid)</A> ngood:$ngood nbad:$nbad\n";
                }
                echo "</UL>\n";
            }

            /**
             * Display menu to select IAP for an airport.
             */
            function gotAirport ()
            {
                global $cycles28, $iapdirs, $tmpdir;

                $state  = getStateCode ();
                $icaoid = $_GET['icaoid'];
                $faaid  = $_GET['faaid'];

                $sl = getStateLink ();
                echo "<P><A HREF=\"viewiap.php\">Top</A> $sl $faaid ($icaoid)</P>";
                echo "<SCRIPT> document.getElementById ('title').innerHTML += ': $faaid' </SCRIPT>\n";

                $sl = "state=$state";
                if (isset ($_GET['first'])) {
                    $first = $_GET['first'];
                    $sl = "first=$first&state=$state";
                }

                echo "<UL>";
                $gotiaps = array ();
                foreach ($iapdirs as $type => $iapdir) {
                    $csvfile = @fopen ("$iapdir/$state.csv", "r");
                    if ($csvfile) {
                        while ($csvline = fgets ($csvfile)) {
                            $columns = explode (",", $csvline);
                            if ($columns[0] == $icaoid) {
                                $iapid = $columns[1];
                                $iapid = str_replace ("\"", "", $iapid);
                                if (!isset ($gotiaps[$iapid])) {
                                    $iap_x = urlencode ($iapid);
                                    $iap_y = htmlspecialchars ($iapid);
                                    echo "<LI>";
                                    if ($type == "WTN") echo "<A HREF=\"viewiap.php?$sl&icaoid=$icaoid&faaid=$faaid&iapid=$iap_x\">";
                                    echo $iap_y;
                                    if ($type == "WTN") echo "</A>\n";
                                    echoIAPGifLink ($state, $faaid, $iapid);
                                    $gotiaps[$iapid] = TRUE;
                                }
                            }
                        }
                    }
                }
                echo "</UL>\n";

                $first = TRUE;
                foreach ($iapdirs as $iapdir) {
                    $rejfile = @fopen ("$iapdir/$state.rej", "r");
                    if ($rejfile) {
                        while ($rejline = fgets ($rejfile)) {
                            $columns = explode (",", $rejline);
                            if ($columns[0] == $faaid) {
                                $iapid = $columns[1];
                                $iapid = str_replace ("\"", "", $iapid);
                                if (!isset ($gotiaps[$iapid])) {
                                    if ($first) {
                                        echo "<P>Rejects:</P><UL>";
                                        $first = FALSE;
                                    }
                                    $iap_x = urlencode ($iapid);
                                    $iap_y = htmlspecialchars ($iapid);
                                    echo "<LI><A HREF=\"viewiap.php?$sl&icaoid=$icaoid&faaid=$faaid&iapid=$iap_x\">$iap_y</A>\n";
                                    echoIAPGifLink ($state, $faaid, $iapid);
                                    $gotiaps[$iapid] = TRUE;
                                }
                            }
                        }
                    }
                }
                if (!$first) echo "</UL>\n";
            }

            function echoIAPGifLink ($state, $faaid, $iapid)
            {
                global $cycles28;

                $gifcsvfile = fopen ("datums/aptplates_$cycles28/state/$state.csv", "r");
                if ($gifcsvfile) {
                    while ($gifcsvline = fgets ($gifcsvfile)) {
                        $parts = QuotedCSVSplit (trim ($gifcsvline));
                        if (($parts[0] == $faaid) && ($parts[1] == $iapid)) {
                            $gifname = $parts[2];
                            echo "<A HREF=\"datums/aptplates_$cycles28/$gifname.p1\" TARGET=_BLANK>(GIF)</A>\n";
                            break;
                        }
                    }
                    fclose ($gifcsvfile);
                }
            }

            /**
             * Generate marked-up image and display it.
             */
            function gotIapId ()
            {
                global $cycles28, $iapdirs, $tmpdir;

                $state  = getStateCode ();
                $icaoid = $_GET['icaoid'];
                $faaid  = $_GET['faaid'];
                $iapid  = $_GET['iapid'];

                $iapid_esa = escapeshellarg ($iapid);
                $iapid_hsc = htmlspecialchars ($iapid);

                $sl = getStateLink ();
                $al = getAirportLink ($faaid, $icaoid);
                echo "<P><A HREF=\"viewiap.php\">Top</A> $sl $al $iapid_hsc</P>";
                echo "<SCRIPT> document.getElementById ('title').innerHTML += ': $faaid $iapid_hsc' </SCRIPT>\n";

                $pdfname = FALSE;
                $csvkey  = "$faaid,\"$iapid\",";
                $csvfile = fopen ("datums/aptplates_$cycles28/state/$state.csv", "r");
                while ($csvline = fgets ($csvfile)) {
                    if (strpos ($csvline, $csvkey) === 0) {
                        $gifname = trim (substr ($csvline, strlen ($csvkey)));
                        $pdfname = "datums/aptplates_$cycles28/" . str_replace (".gif", ".pdf", str_replace ("gif_150", "pdftemp", $gifname));
                        break;
                    }
                }
                fclose ($csvfile);
                if (!$pdfname) die ("approach not found");

                $oldmask = umask (0022);
                @mkdir ("$tmpdir/$state");
                @mkdir ("$tmpdir/$state/$faaid");
                umask ($oldmask);
                $tmpnam = "$tmpdir/$state/$faaid/" . fixid ($iapid);

                if (!file_exists ("$tmpnam.log") ||
                    !file_exists ("$tmpnam.png") ||
                    file_older_than ("$tmpnam.png", $pdfname) ||
                    file_older_than ("$tmpnam.png", "../decosects/DecodePlate.jar")) {
                    @unlink ("$tmpnam.log");
                    @unlink ("$tmpnam.png");
                    $cpaths = "../decosects/DecodePlate.jar:../decosects/pdfbox-1.8.10.jar:../decosects/commons-logging-1.2.jar";
                    $dpcmnd = "java DecodePlate -basedir " . __DIR__ . " -cycles28 $cycles28 $faaid $iapid_esa -markedpng $tmpnam.png -verbose";
                    $dpfile = popen ("CLASSPATH=$cpaths $dpcmnd 2>&1", "r");
                    if (!$dpfile) die ("error starting DecodePlate");

                    $dplog = "$dpcmnd\n";
                    while ($dpline = fgets ($dpfile)) {
                        $dplog .= $dpline;
                    }
                    pclose ($dpfile);
                    file_put_contents ("$tmpnam.log", $dplog);

                    @chmod ("$tmpnam.log", 0644);
                    @chmod ("$tmpnam.png", 0644);
                } else {
                    $dplog = file_get_contents ("$tmpnam.log");
                }

                echo "<P><IMG SRC=\"$tmpnam.png\"></P>\n";
                echo "<PRE>$dplog</PRE>\n";
            }

            /**
             * Output top-level link table, either of states or first characters.
             */
            function outputLinkTable ($link, $values, $width)
            {
                $nvals = count ($values);
                $height = intval (($nvals + $width - 1) / $width);
                $tops = array ();
                for ($j = 0; $j <= $width; $j ++) {
                    $tops[$j] = intval (($nvals * $j + $width - 1) / $width);
                }
                echo "<P><TABLE>";
                for ($i = 0; $i < $height; $i ++) {
                    echo "<TR>";
                    for ($j = 0; $j < $width; $j ++) {
                        $n = $tops[$j] + $i;
                        if ($n < $tops[$j+1]) {
                            $v = $values[$n];
                            echo "<TD>&nbsp;<A HREF=\"viewiap.php?$link=$v\">$v</A>&nbsp;</TD>";
                        } else {
                            echo "<TD></TD>";
                        }
                    }
                    echo "</TR>\n";
                }
                echo "</TABLE></P>\n";
            }

            /**
             * Generate a link to the state-level page that shows airports for the state.
             * But if a 'first' arg is present, generate link to page that shows airports beginning with the given character.
             */
            function getStateLink ()
            {
                if (isset ($_GET['first'])) {
                    $first = $_GET['first'];
                    return "<A HREF=\"viewiap.php?first=$first\">$first</A>";
                }
                $state = $_GET['state'];
                return "<A HREF=\"viewiap.php?state=$state\">$state</A>";
            }

            /**
             * Generate a link to the airport-level page that shows IAPs for that airport.
             */
            function getAirportLink ($faaid, $icaoid)
            {
                $state = $_GET['state'];
                $link  = "state=$state";
                if (isset ($_GET['first'])) {
                    $first = $_GET['first'];
                    $link  = "first=$first&state=$state";
                }
                return "<A HREF=\"viewiap.php?$link&icaoid=$icaoid&faaid=$faaid\">$faaid ($icaoid)</A>";
            }

            /**
             * Reduce a plate id string to numbers and letters only.
             */
            function fixid ($id)
            {
                $out = '';
                $len = strlen ($id);
                for ($i = 0; $i < $len; $i ++) {
                    $c = $id[$i];
                    if (($c >= '0') && ($c <= '9')) $out .= $c;
                    if (($c >= 'A') && ($c <= 'Z')) $out .= $c;
                    if (($c >= 'a') && ($c <= 'z')) $out .= $c;
                }
                return $out;
            }

            function file_older_than ($aname, $bname)
            {
                return filectime ($aname) < filectime ($btime);
            }

            /**
             * Get and validate state code URL parameter.
             */
            function getStateCode ()
            {
                global $iapdirs;

                $state = $_GET['state'];
                if (strlen ($state) != 2) die ("bad state code");
                foreach ($iapdirs as $iapdir) {
                    if (file_exists ("$iapdir/$state.csv")) return $state;
                }
                die ("unknown state");
            }
        ?>
    </BODY>
</HTML>
