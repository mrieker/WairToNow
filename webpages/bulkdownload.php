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
     *  Bulk file downloader
     *
     *  Downloads several files given in POST vars f0, f1, ...
     *  Each file returned in that order as:
     *    @@name=givenfilename\n
     *    @@size=sizeinbytes\n
     *    binaryfiledata
     *    @@eof\n
     *  Then after all files:
     *    @@done\n
     */
    $n = 0;
    while (isset ($_POST["f$n"])) {
        $name = $_POST["f$n"];
        if ($name[0] == '/') {
            die ("name $name cannot start with /\n");
        }
        if (strpos ($name, '../') !== FALSE) {
            die ("name $name cannot contain ../\n");
        }
        echo "@@name=$name\n";
        $size = filesize ($name);
        echo "@@size=$size\n";
        $read = readfile ($name);
        if ($read != $size) {
            die ("size $size ne read $read for $name\n");
        }
        echo "@@eof\n";
        $n ++;
    }
    echo "@@done\n";
?>
