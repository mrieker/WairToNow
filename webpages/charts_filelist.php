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
    $undername = $_GET["undername"];

    if ($undername == 'Waypoints') {

        /*
         * Waypoints (gives lat/lon of all waypoints):
         *   the datums/waypoints_<expdate>.db.gz file
         */

        $dir_entries = scandir ('datums');
        $cycles56 = 0;
        foreach ($dir_entries as $dir_entry) {
            if (strpos ($dir_entry, 'waypoints_') === 0) {
                $xd = intval (substr ($dir_entry, 10, 8));
                if ($cycles56 < $xd) $cycles56 = $xd;
            }
        }
        echo "datums/waypoints_$cycles56.db.gz\n";
    } else if ($undername == 'Topography') {

        /*
         * Topography is several .zip files.
         */
        for ($lat = -90; $lat < 90; $lat ++) {
            echo "datums/topo/$lat.zip\n";
        }
    } else if (strpos ($undername, 'State_') === 0) {

        /*
         * Per-state airport info and diagrams.
         */
        $stateid = substr ($undername, 6);

        // find latest aptplates_<expdate> directory (28-day cycle)
        // find latest aptinfo_<expdate> directory (56-day cycle)
        $dir_entries = scandir ('datums');
        $cycles28 = 0;
        $cycles56 = 0;
        foreach ($dir_entries as $dir_entry) {
            if (strpos ($dir_entry, 'aptplates_') === 0) {
                $xd = intval (substr ($dir_entry, 10));
                if ($cycles28 < $xd) $cycles28 = $xd;
            }
            if (strpos ($dir_entry, 'aptinfo_') === 0) {
                $xd = intval (substr ($dir_entry, 8));
                if ($cycles56 < $xd) $cycles56 = $xd;
            }
        }

        // read the state/<stateid>.csv file to get list of
        // .gif files needed for all airports in the state
        // the .gif files have multiple pages, eg. blabla.gif.p<pageno>
        $csvname = "datums/aptplates_$cycles28/state/$stateid.csv";
        $csvfile = fopen ($csvname, 'r');
        while ($csvline = fgets ($csvfile)) {
            $filename = strrpos ($csvline, ',');
            $filename = trim (substr ($csvline, $filename + 1));
            for ($pageno = 1;; $pageno ++) {
                $fn = "datums/aptplates_$cycles28/$filename.p$pageno";
                if (!file_exists ($fn)) break;
                echo "$fn\n";
            }
        }
        fclose ($csvfile);

        // send name of .csv file itself that says which .gif files go with which airports
        echo "$csvname\n";

        // send names of machine-detected georef info files
        echo "datums/apdgeorefs_$cycles28/$stateid.csv\n";
        echo "datums/iapgeorefs_$cycles28/$stateid.csv\n";
        if (file_exists ("datums/iapgeorefs2_$cycles28/$stateid.csv")) {
            echo "datums/iapgeorefs2_$cycles28/$stateid.csv\n";
        }

        // send names of all the airport information .html.gz files
        // for airports in this state
        $airportscsvname = "datums/airports_$cycles56.csv";
        $airportscsvfile = fopen ($airportscsvname, 'r');
        while ($csvline = fgets ($airportscsvfile)) {
            $csvparts = explode (',', trim ($csvline));
            $lastpart = $csvparts[count($csvparts)-2];
            if ($lastpart == $stateid) {
                $faaid  = $csvparts[1];
                $faaid0 = $faaid[0];
                $faaid1 = substr ($faaid, 1);
                echo "datums/aptinfo_$cycles56/$faaid0/$faaid1.html.gz\n";
            }
        }
        fclose ($airportscsvfile);
    } else {

        /*
         * Sectionals, TACs and WACs ...
         *   all files from charts/<undername>_<vernum>* directories
         *   all charts/<undername>_<vernum>*.csv files
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

                // we only care about directories
                if (!is_dir ('charts/' . $charts_entry)) continue;

                // the undername_ must be directly followed by a number
                $vernum = substr ($charts_entry, strlen ($undername_));
                if (!is_numeric ($vernum)) continue;

                // if highest version so far, save name of the entry
                if ($highest_vernum_sofar < $vernum) {
                    $highest_vernum_sofar = $vernum;
                }
            }
        }

        // output list of .png files for the matching chart
        // followed by the corresponding .csv file
        $dirname = 'charts/' . $undername_ . $highest_vernum_sofar . $suffix;

        $hhh_names = scandir ($dirname);
        foreach ($hhh_names as $hhh_name) {
            if ($hhh_name[0] != '.') {
                $png_names = scandir ("$dirname/$hhh_name");
                foreach ($png_names as $png_name) {
                    $rev_png_name = strrev ($png_name);
                    if (strpos ($rev_png_name, 'gnp.') === 0) {
                        $thisname = "$dirname/$hhh_name/$png_name";
                        echo "$thisname\n";
                    }
                }
            }
        }
        echo "$dirname/icon.png\n";
        echo "$dirname.csv\n";
    }
?>
