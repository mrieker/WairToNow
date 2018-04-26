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
        ?>
    </BODY>
</HTML>
