<?php
//    Copyright (C) 2020, Mike Rieker, Beverly, MA USA
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

    // seems to avoid '403 forbidden' error from accessing aviationweather.gov directly from the app

    $icaoids = $_REQUEST["icaoids"];
    $icaoids = str_replace ("/", "", $icaoids);
    $icaoids = str_replace (".", "", $icaoids);
    $dir  = "../webdata/webmetaf";
    $name = "$dir/$icaoids";
    @mkdir ($dir, 0777, TRUE);
    if (time () - @filemtime ($name) > 300) {
        $metaf = file_get_contents ("https://aviationweather.gov/taf/data?format=raw&metars=on&layout=off&ids=$icaoids");
        file_put_contents ($name, $metaf);
    }
    readfile ($name);
?>
