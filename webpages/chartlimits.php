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
     * @brief Aggregate all the charts/*.csv files into one file for downloading.
     *        Output only the latest version of each chart.
     */
    $dir_entries = scandir ('charts');
    $charts = array ();
    foreach ($dir_entries as $dir_entry) {
        $len = strlen ($dir_entry);
        if (($len > 4) && (substr ($dir_entry, $len - 4) == '.csv')) {
            $i = strrpos ($dir_entry, '_');
            if ($i !== FALSE) {
                $basename = substr ($dir_entry, 0, $i ++);
                $version  = intval (substr ($dir_entry, $i, $len - 4 - $i));
                if (!isset ($charts[$basename]) || ($charts[$basename] < $version)) {
                    $charts[$basename] = $version;
                }
            }
        }
    }
    foreach ($charts as $basename => $version) {
        readfile ('charts/' . $basename . '_' . $version . '.csv');
    }
?>
