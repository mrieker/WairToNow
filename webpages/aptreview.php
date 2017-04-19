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

    session_name ("PHPSESSID_WairToNow");
    session_start ();
    require_once 'iaputil.php';
?>
<!DOCTYPE html>
<HTML>
    <HEAD><TITLE>Review downloaded airport info</TITLE></HEAD>
    <BODY>
        <?php

            /**
             * Checks to see if every airport listed in APT.txt was actually downloaded.
             */
            $expdatenew = 0;
            foreach (scandir ("datums") as $fn) {
                if (strpos ($fn, "aptinfo_") === 0) {
                    $i = intval (substr ($fn, 8, 8));
                    if ($expdatenew < $i) {
                        $expdatenew = $i;
                    }
                }
            }
            if ($expdatenew > 0) {
                echo "<P>Checking airport info expiring on $expdatenew</P>\n";
                $zipfile = popen ("unzip -c datums/APT_$expdatenew.zip", "r");
                if (!$zipfile) die ("<P>error opening datums/APT_$expdatenew.zip</P>\n");
                echo "<UL>";
                $numfiles = 0;
                while ($zipline = fgets ($zipfile)) {
                    if (substr ($zipline, 0, 3) != 'APT') continue;
                    $faaid = trim (substr ($zipline, 27, 4));
                    $first = substr ($faaid, 0, 1);
                    $rest  = substr ($faaid, 1);
                    $size  = @filesize ("datums/aptinfo_$expdatenew/$first/$rest.html.gz");
                    if ($size <= 0) {
                        echo "<LI>missing datums/aptinfo_$expdatenew/$first/$rest.html.gz\n";
                        if (++ $numfiles > 200) {
                            echo "<LI>... possibly more\n";
                            break;
                        }
                    }
                }
                echo "</UL>\n";
            } else {
                echo "<P>no datums/APT_&lt;expdate&gt;.zip found</P>\n";
            }
        ?>
        <P>End.</P>
    </BDDY>
</HTML>
