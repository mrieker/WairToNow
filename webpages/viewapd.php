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

    session_name ("PHPSESSID_WairToNow");
    session_start ();
    $skippwcheck = TRUE;
    require_once 'iaputil.php';
?>
<!DOCTYPE html>
<HTML>
    <HEAD>
        <TITLE>View FAA Airport Diagrams</TITLE>
    </HEAD>
    <BODY>
        <?php
            if (!empty ($_REQUEST['faaid'])) {
                $mydir   = __DIR__;
                $exetime = filemtime ("$mydir/../decosects/ReadArptDgmPng.exe");
                $faaid   = $_REQUEST['faaid'];
                $icaoid  = getIcaoId ($faaid);
                echo "<H3> Marked up $icaoid Airport Diagram </H3>\n";
                @flush (); @ob_flush (); @flush ();
                $cleanname = $_REQUEST['gifname'];
                $cleanname = str_replace ("gif_150", "pngtemp", $cleanname);
                $cleanname = str_replace (".gif",    ".png.p1", $cleanname);
                $cleanname = "$mydir/datums/aptplates_$cycles28/$cleanname";
                $cleantime = filemtime ($cleanname);
                $markdname = "$mydir/apdreview/$icaoid.markd.png";
                $markdtime = 0 + @filemtime ($markdname);
                if (($markdtime < $cleantime) || ($markdtime < $exetime)) {
                    $rwyname = "$mydir/datums/runways_$cycles28.csv";
                    $csvname = "$mydir/apdreview/$icaoid.csv";
                    $cvtfile = popen ("cd ../decosects ; umask 0002 ; mono --debug ReadArptDgmPng.exe $cleanname -faaid $faaid -runways $rwyname -markedpng $markdname -csvoutfile $csvname -csvoutid $icaoid 2>&1", "r");
                    if (!$cvtfile) {
                        echo "<P>error starting ReadArptDgmPng.exe</P>\n";
                    } else {
                        echo "<PRE>";
                        while ($cvtline = fgets ($cvtfile)) {
                            echo $cvtline;
                            @flush (); @ob_flush (); @flush ();
                        }
                        fclose ($cvtfile);
                        echo "</PRE>";
                    }
                }
                echo "<P><IMG SRC=\"apdreview/$icaoid.markd.png\"></P>\n";
            }

            echo "<UL>";
            $statenames = scandir ("datums/aptplates_$cycles28/state");
            foreach ($statenames as $statename) {
                if (strlen ($statename) != 6) continue;
                $state = substr ($statename, 0, 2);
                echo "<LI>$state:";
                $statefile = fopen ("datums/aptplates_$cycles28/state/$statename", "r");
                while ($stateline = fgets ($statefile)) {
                    $parts = QuotedCSVSplit (trim ($stateline));
                    if ($parts[1] == "APD-AIRPORT DIAGRAM") {
                        $faaid   = $parts[0];
                        $gifname = $parts[2];
                        echo " <A HREF=\"?faaid=$faaid&gifname=$gifname\">$faaid</A>";
                    }
                }
                fclose ($statefile);
                echo "\n";
            }
            echo "</UL>\n";
        ?>
    </BODY>
</HTML>
