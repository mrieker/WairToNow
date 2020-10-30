<?php
//    Copyright (C) 2018, Mike Rieker, Beverly, MA USA
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
    $skippwcheck = TRUE;
    require_once 'iaputil.php';
?>
<!DOCTYPE html>
<HTML>
    <HEAD>
        <TITLE>Data Size Differences</TITLE>
    </HEAD>
    <BODY>
        <?php
            $pid = posix_getpid ();
            system ("du -s datums/*$cycles28* > $datdir/current.$pid");
            system ("du -s datums/aptplates_$cycles28/* >> $datdir/current.$pid");
            if ($prevcy28) {
                system ("du -s datums/*$prevcy28* > $datdir/previous.$pid");
                system ("du -s datums/aptplates_$prevcy28/* >> $datdir/previous.$pid");
                echo "<PRE>";
                system ("diff --width=96 --side-by-side $datdir/previous.$pid $datdir/current.$pid");
                echo "</PRE>";
            } else {
                echo "<PRE>";
                readfile ("$datdir/current.$pid");
                echo "</PRE>";
            }

            system ("rm -f $datdir/previous.$pid $datdir/current.$pid");

            if ($prevcy28) {
                echo "<TABLE><TR><TH>$cycles28</TH><TH>$prevcy28</TH><TH>assets</TH></TR>\n";
                $curstates = readStateZips ("datums/statezips_$cycles28");
                $prvstates = readStateZips ("datums/statezips_$prevcy28");
                $assstates = readAssetFile ();
                $ci = 0;
                $pi = 0;
                $ai = 0;
                $cl = count ($curstates);
                $pl = count ($prvstates);
                $al = count ($assstates);
                while (($ci < $cl) || ($pi < $pl) || ($ai < $al)) {
                    $cur = ($ci < $cl) ? $curstates[$ci] : "~~";
                    $prv = ($pi < $pl) ? $prvstates[$pi] : "~~";
                    $ass = ($ai < $al) ? $assstates[$ai] : "~~";
                    $lowest = "~~";
                    if ($lowest > $cur) $lowest = $cur;
                    if ($lowest > $prv) $lowest = $prv;
                    if ($lowest > $ass) $lowest = $ass;
                    echo "<TR><TD>";
                    if ($cur == $lowest) { echo $cur; $ci ++; } echo "</TD><TD>";
                    if ($prv == $lowest) { echo $prv; $pi ++; } echo "</TD><TD>";
                    if ($ass == $lowest) { echo $ass; $ai ++; } echo "</TD></TR>\n";
                }
                echo "</TABLE>\n";
            }

            // read state codes from the given statezips_<expdate> directory
            function readStateZips ($dir)
            {
                $a = array ();
                $r = scandir ($dir);
                foreach ($r as $f) {
                    $i = strpos ($f, ".zip");
                    if ($i !== FALSE) $a[] = substr ($f, 0, $i);
                }
                sort ($a);
                return $a;
            }

            // read state codes from the app's statelocation file
            function readAssetFile ()
            {
                $a = array ();
                $f = fopen ("../app/src/main/assets/statelocation.dat", "r");
                while ($l = fgets ($f)) {
                    $w = explode (" ", trim ($l));
                    $a[] = $w[0];
                }
                fclose ($f);
                sort ($a);
                return $a;
            }
        ?>
    </BODY>
</HTML>
