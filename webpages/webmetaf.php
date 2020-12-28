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

    $dir = "../webdata/webmetaf";
    @mkdir ($dir, 0777, TRUE);

    if (! isset ($_REQUEST["dbase"])) {

        // FAA airports only, just copy the FAA format as is
        // input:
        //  icaoids = comma separated airport ICAO ids
        // output:
        //  <html tags embedded throughout> also &nbsp;
        //  icaoid ddhhmmZ ...                                          < METAR
        //  icaoid ddhhmmZ ... FMddhhmm ... FMddhhmm ... FMddhhmm ...   < TAF

        $icaoids = $_REQUEST["icaoids"];
        $icaoids = str_replace ("/", "", $icaoids);
        $icaoids = str_replace (".", "", $icaoids);
        $name = "$dir/$icaoids";
        if (time () - @filemtime ($name) > 300) {
            $metaf = file_get_contents ("https://aviationweather.gov/taf/data?format=raw&metars=on&layout=off&ids=$icaoids");
            file_put_contents ("$name.tmp", $metaf);
            rename ("$name.tmp", $name);
        }
    } else {

        // input:
        //  icaoid = single airport ICAO id
        //  dbase  = FAA, OA or OFM
        // output:
        //  METAR: icaoid ddhhmmZ ...
        //    TAF: icaoid ddhhmmZ ... FMddhhmm ... FMddhhmm ... FMddhhmm ...

        $icaoid = strtoupper ($_REQUEST["icaoid"]);
        $dbase  = isset ($_REQUEST["dbase"]) ? $_REQUEST["dbase"] : "";
        $name   = "$dir/$icaoid.$dbase";
        if (time () - @filemtime ($name) > 300) {
            switch ($dbase) {

                // get FAA airports from FAA
                case "FAA": {
                    $metaf = file_get_contents ("https://aviationweather.gov/taf/data?format=raw&metars=on&layout=off&ids=$icaoid");
                    $metaf = decodeFAA ($metaf, $icaoid);
                    break;
                }

                // get other (OA and OFM) airports from MeteoStar
                default: {
                    $metaf = file_get_contents ("http://wxweb.meteostar.com/cgi-bin/metartafsearch/both.cgi?choice=$icaoid");
                    //file_put_contents ("$name.htm", $metaf);
                    $words = wordize ($metaf);
                    //file_put_contents ("$name.wds", implode (" ", $words));
                    $metaf = decodeMET ($words, $icaoid);
                    break;
                }
            }
            file_put_contents ("$name.tmp", $metaf);
            rename ("$name.tmp", $name);
        }
    }

    readfile ($name);
    exit;

    // get upper case words
    function wordize ($str)
    {
        $str   = strtoupper ($str);
        $words = array ();
        $len   = strlen ($str);
        $word  = "";
        for ($i = 0; $i < $len; $i ++) {
            $c = $str[$i];

            // treat space, HTML tag and &nbsp; as a space
            if ($c == '<') {
                while (($i + 1 < $len) && ($str[++$i] != '>')) { }
            } else if (substr ($str, $i, 6) == "&NBSP;") {
                $i += 5;
            } else if ($c > ' ') {

                // all else is a printable char
                $word .= $c;
                continue;
            }

            // space or something like it, append word to array
            if ($word != "") {
                $words[] = $word;
                $word = "";
            }
        }

        // append last word to array
        if ($word != "") $words[] = $word;

        return $words;
    }

    // decode FAA-style output
    function decodeFAA ($metaf, $icaoid)
    {
        $out = "";

        // doesn't explicitly show metar vs taf
        //  but tafs contain FMddhhmm lines
        // different segments delimited by occurrences of icaoid

        $words   = wordize ($metaf);
        $words[] = $icaoid;
        $count   = count ($words);
        $lasti   = -1;
        for ($i = 0; $i < $count; $i ++) {
            $word = $words[$i];
            if ($word == $icaoid) {
                if ($lasti >= 0) {
                    $type = "METAR";
                    for ($j = $lasti; ++ $j < $i;) {
                        $word = $words[$j];
                        if ((strlen ($word) == 8) && (substr ($word, 0, 2) == "FM")) {
                            $type = "TAF";
                            break;
                        }
                    }
                    $out .= " $type: $icaoid";
                    for ($j = $lasti; ++ $j < $i;) $out .= " " . $words[$j];
                }
                $lasti = $i;
            }
        }

        return $out;
    }

    // decode meteostar-style output
    //  METAR: ... icaoid ddhhmmZ ... =
    //    TAF: ... icaoid ddhhmmZ ... =
    function decodeMET ($words, $icaoid)
    {
        $out = "";

        $count   = count ($words);
        $started = 0;
        $type    = "";
        for ($i = 0; $i < $count; $i ++) {
            $word = $words[$i];
            if ($word == "=") $started = 0;
            if (($word == "METAR:") || ($word == "TAF:")) {
                $type = $word;
                $started = 1;
            }
            if (($started == 1) && ($word == $icaoid)) {
                $out .= " $type";
                $started = 2;
            }
            if ($started == 2) $out .= " $word";
        }

        return $out;
    }
?>
