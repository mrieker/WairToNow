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

    // read https://tfr.faa.gov pages by proxy
    // used by older android versions (sdk 15 or so)
    // ...that can't use https://tfr.faa.gov site directly

    date_default_timezone_set ("UTC");

    $tfrfaaurl = $_REQUEST['tfrfaaurl'];
    $ifmodsin  = isset ($_REQUEST['ifmodsince']) ? intval ($_REQUEST['ifmodsince']) : FALSE;

    $headers = array ();
    if ($ifmodsin) {
        $headers[] = "if-modified-since: " . date (DATE_RSS, $ifmodsin);
    }

    $ch = curl_init ();
    curl_setopt ($ch, CURLOPT_FILETIME, TRUE);
    curl_setopt ($ch, CURLOPT_FOLLOWLOCATION, TRUE);
    curl_setopt ($ch, CURLOPT_MAXREDIRS, 10);
    curl_setopt ($ch, CURLOPT_RETURNTRANSFER, TRUE);
    curl_setopt ($ch, CURLOPT_CONNECTTIMEOUT, 10);
    curl_setopt ($ch, CURLOPT_URL, "https://tfr.faa.gov/$tfrfaaurl");
    curl_setopt ($ch, CURLOPT_HTTPHEADER, $headers);
    curl_setopt ($ch, CURLOPT_HEADER, TRUE);
    curl_setopt ($ch, CURLOPT_FILETIME, TRUE);

    $data     = curl_exec ($ch);
    $info     = curl_getinfo ($ch);
    $httpcode = $info["http_code"];
    $modtime  = $info["filetime"];

    //file_put_contents ("../webdata/tfrfaagov.log", "tfrfaaurl=$tfrfaaurl ifmodsin=$ifmodsin httpcode=$httpcode modtime=$modtime\n", FILE_APPEND);

    http_response_code ($httpcode);
    header ("last-modified: " . date (DATE_RSS, $modtime) . " GMT");
    echo $data;
?>
