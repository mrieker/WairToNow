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
     * Used to manually display an airport info page in web browser.
     * Not needed for normal operation.
     */
    $expdate = 0;
    foreach (scandir ("datums") as $fn) {
        if (strpos ($fn, "aptinfo_") === 0) {
            $i = intval (substr ($fn, 8, 8));
            if ($expdate < $i) $expdate = $i;
        }
    }
    $faaid     = strtoupper ($_GET['faaid']);
    $firstchar = $faaid[0];
    $restchars = substr ($faaid, 1);
    system ("zcat datums/aptinfo_$expdate/$firstchar/$restchars.html.gz");
?>
