<?php
    /**
     * @brief Download an openstreepmap tile
     *        streets.php?tile=zoom/x/y.png
     */

    $tile = $_GET['tile'];
    if (strpos ($tile, "..") === FALSE) {

        // see if we already have the file
        // also re-download if file is a year old
        $newpath = "streets/$tile";
        if (!file_exists ($newpath) || (filemtime ($newpath) < time () - 60*60*24*365)) {

            // if not, pick a tile server at random
            // http://wiki.openstreetmap.org/wiki/Slippy_map_tilenames
            $servers = array (
                'http://a.tile.openstreetmap.org',
                'http://b.tile.openstreetmap.org',
                'http://c.tile.openstreetmap.org',
                //'http://a.tile.opencyclemap.org/cycle',
                //'http://b.tile.opencyclemap.org/cycle',
                //'http://c.tile.opencyclemap.org/cycle',
                //'http://otile1.mqcdn.com/tiles/1.0.0/osm',
                //'http://otile2.mqcdn.com/tiles/1.0.0/osm',
                //'http://otile3.mqcdn.com/tiles/1.0.0/osm',
                //'http://otile4.mqcdn.com/tiles/1.0.0/osm',
            );
            $i = rand (0, count ($servers) - 1);
            $server = $servers[$i];

            // try to open the url for downloading
            $ifile = fopen ("$server/$tile", 'rb');
            if ($ifile) {

                // create its directory
                $i = strrpos ($newpath, '/');
                $dirpath = substr ($newpath, 0, $i);
                if (!file_exists ($dirpath)) {
                    mkdir ($dirpath, 0777, true);
                }

                // copy to a temporary file
                $tmppath = "$newpath.tmp";
                $ofile = fopen ($tmppath, 'wb');
                if (!$ofile) return;
                while (!feof ($ifile)) {
                    fwrite ($ofile, fread ($ifile, 8192));
                }
                fclose ($ifile);
                fclose ($ofile);

                // all downloaded, rename to permanent file
                rename ($tmppath, $newpath);
            }
        }

        // dump tile file to client
        readfile ($newpath);
    }
?>
