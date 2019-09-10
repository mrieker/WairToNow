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

    if (isset ($argv[1])) {
        switch ($argv[1]) {

            // command line: hash <filename> <hashtype>
            case "hash": {
                $name = $argv[2];
                $hash = $argv[3];
                switch ($hash) {
                    case "md5": {
                        $md5 = md5_file ($name);
                        echo "@@md5=$md5\n";
                        break;
                    }
                    case "sum": {
                        $sum  = 0;
                        $file = fopen ($name, "r");
                        if (!$file) die ("error opening $name\n");
                        while (!feof ($file)) {
                            $buf = fread ($file, 8192);
                            $len = strlen ($buf);
                            for ($i = 0; $i < $len; $i ++) {
                                $sum += ord ($buf[$i]) & 0xFF;
                            }
                        }
                        fclose ($file);
                        echo "@@sum=$sum\n";
                        break;
                    }
                    default: {
                        die ("bad hash type $hash\n");
                    }
                }
            }
        }
        exit;
    }

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
    ob_start (NULL, 4000);
    $n = 0;
    while (isset ($_REQUEST["f$n"])) {

        // get parameters for one file to send out
        $name = $_REQUEST["f$n"];
        if ($name[0] == '/') {
            die ("name $name cannot start with /\n");
        }
        if (strpos ($name, '../') !== FALSE) {
            die ("name $name cannot contain ../\n");
        }
        $skip = isset ($_REQUEST["s$n"]) ? intval ($_REQUEST["s$n"]) : 0;
        $hash = isset ($_REQUEST["h$n"]) ? $_REQUEST["h$n"] : "";

        // maybe start hashing thread
        if ($hash) {
            $myfile = __FILE__;
            $hashpipe = popen ("php $myfile hash $name $hash", "r");
        }

        // echo parameters back
        echo "@@name=$name\n";
        if ($skip > 0) echo "@@skip=$skip\n";
        $size = filesize ($name);
        if ($size === FALSE) die ("file $name not found\n");
        echo "@@size=$size\n";
        ob_end_flush ();

        // send file contents
        if (($skip > 0) || ($size > 1024 * 1024)) {
            $file = fopen ($name, 'rb');
            if (!$file) die ("error opening $name\n");
            if (fseek ($file, $skip, SEEK_SET)) die ("error seeking $name $skip\n");
            $read = $skip;
            while (($len = $size - $read) > 0) {
                if ($len > 8192) $len = 8192;
                $data  = fread ($file, $len);
                if ($data === FALSE) die ("error reading $name\n");
                $read += $len;
                echo $data;
            }
            fclose ($file);
        } else {
            $read = readfile ($name);
        }
        if ($read != $size) {
            die ("size $size ne read $read for $name\n");
        }

        // wait for hash
        ob_start (NULL, 4000);
        if ($hash) {
            echo fgets ($hashpipe);
            pclose ($hashpipe);
        } else {
            echo "@@eof\n";
        }

        // on to next file if any
        $n ++;
    }
    echo "@@done\n";
    ob_end_flush ();
?>
