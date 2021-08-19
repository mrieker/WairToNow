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

    /**
     * Download and convert all the openflightmap.org charts
     * Takes about an hour to run
     */

    // 4-letter code
    //  https://www.openflightmaps.org/eh-netherlands/?airac=2013&language=english
    //   var regionCode = "EHAA";

    // read region list into array
    // each line:
    //  4-char code (ljsf)
    //  space
    //  2-char code
    //  space
    //  string name
    // examples:
    //  ebbu EB Belgium
    //  ed   ED Germany
    $regions = array ();
    $regfile = fopen ("ofmregions.dat", "r");
    while ($regline = fgets ($regfile)) {
        $i = strpos ($regline, "//");
        if ($i !== FALSE) $regline = substr ($regline, 0, $i);
        $regline = trim ($regline);
        if ($regline !== "") $regions[] = $regline;
    }
    fclose ($regfile);

    $airac    = runoneline ("./cureffdate -28 airac");          // 2013
    $effdate  = runoneline ("./cureffdate -28 yyyymmdd");       // 20201203
    $expdate  = runoneline ("./cureffdate -28 -x yyyymmdd");    // 20201231
    $oldairac = runoneline ("next28=0 ./cureffdate -28 airac"); // 2012 or 2013

    $zoom =  10;    // use the zoom 10 slippy tiles
    $size = 256;    // slippy tiles are 256x256

    @mkdir ("datums/statezips_$expdate");

    $wpxml = gzopen ("datums/ofmwaypts_$expdate.xml.gz.tmp", "wb9");
    gzputs ($wpxml, "<ofmx>\n");

    foreach ($regions as $region) {
        echo "$region\n";

        $dlkey = trim (substr ($region, 0, 4));
        $dlky2 = substr ($region, 0, 2);

        $country   = trim (substr ($region, 8));
        $spacename = "OFM $country $effdate";
        $undername = str_replace (" ", "_", $spacename);

        /***************\
         *  Waypoints  *
        \***************/

        if (! file_exists ("datums/ofmx_${dlky2}_$expdate.zip")) {
            echo "  downloading waypoints\n";
            downloadFile ("https://snapshots.openflightmaps.org/live/$airac/ofmx/$dlkey/latest/ofmx_$dlky2.zip", "datums/ofmx_${dlky2}_$expdate.zip");
        }

        if (file_exists ("datums/ofmx_${dlky2}_$expdate.zip")) {
            echo "  processing waypoints\n";
            gzputs ($wpxml, "<ofmxfile><key>$dlkey</key><name>$country</name></ofmxfile>\n");
            $zfile = popen ("unzip -pq datums/ofmx_${dlky2}_$expdate.zip ofmx_$dlky2/embedded/ofmx_$dlky2", "r");
            if (! $zfile) diedie ("error reading datums/ofmx_$dlky2.zip\n");
            while ($line = fgets ($zfile)) {
                if (strpos ($line, "<?xml") !== FALSE) continue;
                if (strpos ($line, "<OFMX-Snapshot") !== FALSE) continue;
                if (strpos ($line, "</OFMX-Snapshot") !== FALSE) continue;
                gzputs ($wpxml, $line);
            }
            pclose ($zfile);
            gzputs ($wpxml, "\n");
        }

        /************\
         *  Charts  *
        \************/

        if (! file_exists ("charts/$undername.zip")) {

            // read zip file from web
            echo "  downloading chart\n";
            if (downloadFile (
                    "https://snapshots.openflightmaps.org/live/$airac/tiles/$dlkey/noninteractive/epsg3857/slippyTiles_clipped.zip",
                    "charts/$undername.zip")) {
                echo "  - using newairac $airac\n";
            } else if (($oldairac != $airac) && downloadFile (
                    "https://snapshots.openflightmaps.org/live/$oldairac/tiles/$dlkey/noninteractive/epsg3857/slippyTiles_clipped.zip",
                    "charts/$undername.zip")) {
                echo "  - using oldairac $oldairac\n";
            }
        }
    }

    gzputs ($wpxml, "</ofmx>\n");
    if (! gzclose ($wpxml)) diedie ("error closing datums/ofmwaypts_$expdate.xml.gz.tmp\n");
    renameck ("datums/ofmwaypts_$expdate.xml.gz.tmp", "datums/ofmwaypts_$expdate.xml.gz");

    // generate waypoint sqlite3 file from downloaded xml
    if (! file_exists ("MakeWaypoints.exe") || (filemtime ("MakeWaypoints.exe") < filemtime ("MakeWaypoints.cs"))) {
        runoneline ("mcs -debug -out:MakeWaypoints.exe MakeWaypoints.cs GeoContext.cs Topography.cs -reference:System.Data.dll -reference:Mono.Data.Sqlite.dll");
    }
    runoneline ("mono --debug MakeWaypoints.exe $expdate datums/waypointsofm_$expdate.db 0 2");
    runoneline ("gzip -c datums/waypointsofm_$expdate.db > datums/waypointsofm_$expdate.db.gz");

    // generate chart tiles from downloaded zip files
    runoneline ("CLASSPATH=ReadSlippies.jar java -Xmx16g ReadSlippies $effdate $expdate");

    echo "done\n";
    exit;

    // download file from web server
    //  $wname = web file name https://...
    //  $zname = local on-disk filename
    function downloadFile ($wname, $zname)
    {
        $wfile = fopen ($wname, "r");
        if (! $wfile) return FALSE;
        $zfile = fopen ("$zname.tmp", "w");
        if (! $zfile) diedie ("error creating $zname.tmp\n");
        while ($buff = fread ($wfile, 4096)) {
            if (! fwrite ($zfile, $buff)) diedie ("error writing $zname.tmp\n");
        }
        fclose ($wfile);
        if (! fclose ($zfile)) diedie ("error closing $zname.tmp\n");
        renameck ("$zname.tmp", $zname);
        return TRUE;
    }

    // rename file with error check
    function renameck ($oldname, $newname)
    {
        if (! rename ($oldname, $newname)) {
            diedie ("error renaming $oldname -> $newname\n");
        }
    }

    // run a command
    // return only/last line of output
    function runoneline ($cmd)
    {
        $line = system ($cmd, $rc);
        if ($rc != 0) diedie ("rc $rc from $cmd\n");
        return $line;
    }

    // output error message then exit with error status
    function diedie ($msg)
    {
        fputs (STDERR, $msg);
        exit (1);
    }
?>
