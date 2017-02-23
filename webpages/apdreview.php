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
     * Manually review APD georeferencing.
     */

    session_start ();
    require_once 'iaputil.php';
?>
<!DOCTYPE html>
<HTML>
    <HEAD>
        <TITLE ID="title">APD Review</TITLE>
    </HEAD>
    <BODY>
        <?php

            /*
             * Check for callback link to display single marked-up diagram.
             */
            if (!empty ($_GET['icaoid'])) {
                $icaoid  = $_GET['icaoid'];
                echo "<SCRIPT LANGUAGE=JAVASCRIPT> document.getElementById ('title').innerHTML = 'APD Review $icaoid'</SCRIPT>\n";
                echo "<H3> Marked up $icaoid Airport Diagram </H3>\n";
                @flush (); @ob_flush (); @flush ();
                $cleanname = $_GET['pngname'];
                $markdname = "../webpages/apdreview/$icaoid.markd.png";
                $csvname   = "../webpages/apdreview/$icaoid.csv";
                $cvtfile = popen ("cd ../decosects ; mono --debug ReadArptDgmPng.exe $cleanname -markedpng $markdname -csvoutfile $csvname -csvoutid $icaoid 2>&1", "r");
                if (!$cvtfile) {
                    echo "<P>error starting ReadArptDgmPng.exe</P>\n";
                } else {
                    echo "<PRE>";
                    while ($cvtline = fgets ($cvtfile)) {
                        echo $cvtline;
                        @flush (); @ob_flush (); @flush ();
                    }
                    fclose ($cvtfile);
                    $w = 1075 / 2.5;
                    $h = 1650 / 2.5;
                    echo "</PRE><P><IMG SRC=\"apdreview/$icaoid.markd.png\" WIDTH=$w HEIGHT=$h></P>\n";
                }
                exit;
            }

            /**
             * Compare the current APD georef info to previously confirmed values.
             */
            $apdgeorefs_older  = "";
            $apdgeorefs_latest = "";
            foreach (scandir ("datums") as $fn) {
                if (strpos ($fn, "apdgeorefs_") === 0) {
                    $apdgeorefs_older  = $apdgeorefs_latest;
                    $apdgeorefs_latest = $fn;
                }
            }

            echo "<H3> Comparing Airport Diagrams $apdgeorefs_latest to $apdgeorefs_older </H3>\n";
            @flush (); @ob_flush (); @flush ();

            $allstates = array ();
            foreach (scandir ("datums/$apdgeorefs_older") as $fn) {
                if (strpos ($fn, ".csv") === 2) {
                    $allstates[$fn] = $fn;
                }
            }
            foreach (scandir ("datums/$apdgeorefs_latest") as $fn) {
                if (strpos ($fn, ".csv") === 2) {
                    $allstates[$fn] = $fn;
                }
            }
            ksort ($allstates);

            foreach ($allstates as $state) {
                echo "<P>State $state</P>\n";
                $difffile = popen ("mono --debug ../decosects/DiffArptDgmCsvs.exe datums/$apdgeorefs_latest/$state datums/$apdgeorefs_older/$state", "r");
                if (!$difffile) {
                    echo "<P>error starting ../decosects/DiffArptDgmCsvs.exe</P>";
                    exit;
                }
                $lasticaoid = "";
                while ($diffline = fgets ($difffile)) {
                    $diffline = trim ($diffline);
                    $parts = explode (' ', $diffline);
                    if ($lasticaoid != $parts[1]) {
                        if ($lasticaoid != "") echo "</UL></A>\n";
                        $lasticaoid = $parts[1];
                        $aptinfo = getAptInfo ($lasticaoid);
                        $faaid   = $aptinfo[1];
                        $state   = $aptinfo[8];
                        $gifname = "";
                        $csvfile = fopen ("datums/aptplates_$cycles28/state/$state.csv", "r");
                        if ($csvfile) {
                            while ($csvline = fgets ($csvfile)) {
                                $csvline = trim ($csvline);
                                $parts = QuotedCSVSplit ($csvline);
                                if (($parts[0] == $faaid) && ($parts[1] == "APD-AIRPORT DIAGRAM")) {
                                    $gifname = $parts[2];
                                    break;
                                }
                            }
                            fclose ($csvfile);
                        }
                        if ($gifname != "") {
                            $pngname = str_replace ("gif_150", "pngtemp", $gifname);
                            $pngname = str_replace (".gif",    ".png.p1", $pngname);
                            echo "<A HREF=\"$thisscript?icaoid=$lasticaoid&pngname=datums/aptplates_$cycles28/$pngname\" TARGET=_BLANK>\n";
                        }
                        echo "<UL>\n";
                    }
                    echo "<LI>" . htmlspecialchars ($diffline) . "\n";
                    if (strpos ($diffline, ">") === 0) {
                        echo "try <TT>decosects/singleapd.sh " . htmlspecialchars (substr ($diffline, 1)) . "</TT>\n";
                    }
                    @flush (); @ob_flush (); @flush ();
                }
                pclose ($difffile);
                if ($lasticaoid != "") echo "</UL></A>\n";
            }
            echo "<P>The end.</P>\n";
        ?>
    </BODY>
</HTML>
