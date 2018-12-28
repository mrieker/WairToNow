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
     * @brief Retrieves a list of files associated with a chart from the charts directory.
     *        Will also retrieve filelist for airport information or airport diagram files.
     * @cgivar undername = name of chart as retrieved from chartlimits.csv with any spaces
     *                     replaced by underscores
     */

    // get name from chartlimits.csv with underscores instead of spaces
    $undername = $_REQUEST["undername"];

    if ($undername == 'Waypoints') {

        /*
         * Waypoints (gives lat/lon of all waypoints):
         *   the datums/waypoints_<expdate>.db.gz file
         */
        $dir_entries = scandir ('datums');
        $cycles28 = 0;
        foreach ($dir_entries as $dir_entry) {
            if (strpos ($dir_entry, 'waypoints_') === 0) {
                $xd = intval (substr ($dir_entry, 10, 8));
                if ($cycles28 < $xd) $cycles28 = $xd;
            }
        }
        echo "datums/waypoints_$cycles28.db.gz\n";
    } else if ($undername == 'Obstructions') {

        /*
         * Obstructions (gives height, type, lat/lon of all obstructions):
         *   the datums/obstructions_<expdate>.db.gz file
         */
        $dir_entries = scandir ('datums');
        $cycles28 = 0;
        foreach ($dir_entries as $dir_entry) {
            if (strpos ($dir_entry, 'obstructions_') === 0) {
                $xd = intval (substr ($dir_entry, 13, 8));
                if ($cycles28 < $xd) $cycles28 = $xd;
            }
        }
        echo "datums/obstructions_$cycles28.db.gz\n";
    } else if ($undername == 'Topography') {

        /*
         * Topography is a single static .zip file.
         */
        echo "datums/topo.zip\n";
    } else if (strpos ($undername, 'State_') === 0) {

        /*
         * Per-state airport info and diagrams.
         */
        $stateid = substr ($undername, 6);

        // find latest statezips_<expdate> directory (28-day cycle)
        $dir_entries = scandir ('datums');
        $cycles28 = 0;
        foreach ($dir_entries as $dir_entry) {
            if (strpos ($dir_entry, 'statezips_') === 0) {
                $xd = intval (substr ($dir_entry, 10));
                if ($cycles28 < $xd) $cycles28 = $xd;
            }
        }

        echo "datums/statezips_$cycles28/$stateid.zip\n";
    } else {

        /*
         * Sectionals, TACs and WACs ...
         *   the latest vernum from charts/<undername>_<vernum>.wtn.zip
         */

        // that given name must be followed directly by _<vernum>
        // ... so Anchorage doesn't also get Anchorage_TAC files
        $undername_ = $undername . '_';

        // scan through the charts directory for chart directories
        // whose names begin with $undername_
        $charts_entries = scandir ('charts');
        $highest_vernum_sofar = 0;
        foreach ($charts_entries as $charts_entry) {
            if (strpos ($charts_entry, $undername_) === 0) {

                // we only care about files ending with '.wtn.zip'
                $len = strlen ($charts_entry);
                if (($len < 8) || (substr ($charts_entry, $len - 8) != ".wtn.zip")) continue;

                // the undername_ must be directly followed by a number
                $charts_entry = substr ($charts_entry, 0, $len - 8);
                $vernum = substr ($charts_entry, strlen ($undername_));
                if (!is_numeric ($vernum)) continue;

                // if highest version so far, save name of the entry
                if ($highest_vernum_sofar < $vernum) {
                    $highest_vernum_sofar = $vernum;
                }
            }
        }

        // output name of highest version .wtn.zip file
        echo "charts/$undername_$highest_vernum_sofar.wtn.zip\n";
    }
?>
