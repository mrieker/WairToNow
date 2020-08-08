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

    // called every few minutes by TFROutlines to get latest TFR database

    $data = "../webdata/tfrs";
    @mkdir ($data);
    chdir ($data);

    date_default_timezone_set ("UTC");

    $now = time ();

    // make sure only one runs at a time
    $lockfile = fopen ("gettfrs.lock", "c");
    if (!$lockfile) diedie ("error opening lockfile\n");
    if (!flock ($lockfile, LOCK_EX)) diedie ("error locking lockfile\n");

    // if within a new 10-minute period, create new file
    $oldmtime = @filemtime ("tfrs.db.gz");
    if (intval ($now / 600) > intval ($oldmtime / 600)) {

        // get latest game tfrs from avare
        //  CREATE TABLE gametfr (effective Text, name Text, latitude float, longitude float);
        download ("http://www.apps4av.org/new/GameTFRs.zip", "GameTFRs.zip");
        @unlink ("gametfr.db");
        unzip ("GameTFRs.zip");

        // use the gametfr.db as a base for everything else
        $sqldb = new SQLite3 ("gametfr.db", SQLITE3_OPEN_READWRITE);
        if (!$sqldb) diedie ("error opening gametfr.db");

        // write the scan time to the database
        // gets displayed in the 'TFRs as of' message on screen
        $sqldb->exec ("CREATE TABLE asof (as_of INTEGER NOT NULL)");
        $sqldb->exec ("INSERT INTO asof (as_of) VALUES ($now)");

        // read game TFR stuff from arcgis.com
        //  webpage:  https://ais-faa.opendata.arcgis.com/datasets/67af16061c014365ae9218c489a321be_0/geoservice
        // gives locations of all stadiums but no time info
        $arcgisjson = file_get_contents ("https://services6.arcgis.com/ssFJjBXIUyZDrSYZ/arcgis/rest/services/Stadiums/FeatureServer/0/query?where=1%3D1&outFields=*&outSR=4326&f=json");
        $arcgiss = array ();
        if ($arcgisjson) {
            $arcgisparsed = json_decode ($arcgisjson);
            foreach ($arcgisparsed->features as $feature) {
                $obj  = new stdClass ();
                $obj->name = $feature->attributes->NAME;
                $obj->lat  = $feature->geometry->y;
                $obj->lon  = $feature->geometry->x;
                $arcgiss[] = $obj;
            }
        }

        // remove entries from $arcgiss that match entries in the gametfr database
        // assume they match if lat,lon are less than approx 0.25nm apart
        $result = $sqldb->query ("SELECT name,latitude,longitude FROM gametfr");
        while ($row = $result->fetchArray ()) {
            $db_name = $row['name'];
            $db_lat  = $row['latitude'];
            $db_lon  = $row['longitude'];

            $narcgiss = count ($arcgiss);
            for ($i = 0; $i < $narcgiss; $i ++) {
                $obj = $arcgiss[$i];
                if ($obj) {
                    $diff = hypot ($obj->lat - $db_lat, $obj->lon - $db_lon);
                    if ($diff < 1.0/256.0) {
                        $arcgiss[$i] = FALSE;
                    }
                }
            }
        }

        // add remaining $arcgiss entries to the database without time info
        $infeff = 0xFFFFFFFF * 1000;
        $stmt = $sqldb->prepare ("INSERT INTO gametfr (name,latitude,longitude,effective) VALUES (:name,:lat,:lon,$infeff)");
        foreach ($arcgiss as $obj) {
            if ($obj) {
                $stmt->bindValue (":name", $obj->name, SQLITE3_TEXT);
                $stmt->bindValue (":lat",  $obj->lat,  SQLITE3_FLOAT);
                $stmt->bindValue (":lon",  $obj->lon,  SQLITE3_FLOAT);
                $stmt->execute ();
                $stmt->reset ();
            }
        }

        // set up a table for the path-style TFRs
        $sqldb->exec ("CREATE TABLE pathtfrs (p_id TEXT PRIMARY KEY, p_link TEXT NOT NULL, p_eff INTEGER NOT NULL, p_exp INTEGER NOT NULL, p_tz TEXT NOT NULL, p_low TEXT NOT NULL, p_top TEXT NOT NULL, p_ints TEXT NOT NULL)");

        // read path-style XML files from FAA and write to database

        $baseurl  = "https://tfr.faa.gov";
        $listhtml = file_get_contents ("$baseurl/tfr2/list.html");
        if (!$listhtml) diedie ("error fetching tfr list");
        $beenseen = array ();
        for ($i = 0; ($j = strpos ($listhtml, "/save_pages/", $i)) !== FALSE;) {
            $j += 12;
            $i  = strpos ($listhtml, ".html", $j);
            $name = substr ($listhtml, $j, $i - $j);    // "detail_0_9374"

            // make ID look like the link on https://tfr.faa.gov
            $id = $name;
            if (strpos ($id, "detail_") === 0) $id = substr ($id, 7);
            $id = str_replace ("_", "/", $id);

            // that link is seen more than once in the html file
            if (!isset ($beenseen[$id])) {
                $beenseen[$id] = TRUE;

                // download XML file from FAA if we don't already have it
                if (!file_exists ("$name.xml")) {
                    if (! download ("$baseurl/save_pages/$name.xml", "$name.xml")) continue;
                }

                // decode the XML file and write database record
                $tfr = new TFR ($id, "$name.xml", "$baseurl/save_pages/$name.html");
                if (count ($tfr->lls) > 2) {
                    $tfr->writedb ($sqldb);
                }
            }
        }

        $sqldb->close ();
        system ("gzip -c gametfr.db > tfrs.db.gz", $rc);
        if ($rc !== 0) diedie ("error gzipping database\n");
    }

    // database current, send it
    readfile ("tfrs.db.gz");

    fclose ($lockfile);
    exit;

    // download web file into disk file
    //  input:
    //   webname = url for web file
    //   name = name of disk file
    //  output:
    //   returns TRUE iff successful
    function download ($webname, $name)
    {
        $tmpname = "$name.tmp";
        $webfile = fopen ($webname, "r");
        if (!$webfile) return FALSE;
        @unlink ($tmpname);
        $tmpfile = fopen ($tmpname, "w");
        if (!$tmpfile) diedie ("error creating tmpfile $nane\n");
        while ($buff = fread ($webfile, 8192)) {
            fwrite ($tmpfile, $buff);
        }
        fclose ($webfile);
        if (!fclose ($tmpfile)) diedie ("error closing $name\n");
        if (!rename ($tmpname, $name)) diedie ("error renaming $name\n");
        return TRUE;
    }

    // unzip a file, extract contents to current directory
    function unzip ($name)
    {
        $zip = new ZipArchive ();
        if ($zip->open ($name) !== TRUE) diedie ("error opening zip $name\n");
        if (!$zip->extractTo (".")) diedie ("error extracting zip $name\n");
        $zip->close ();
    }

    // contains a single path-style TFR
    class TFR {
        public $id;
        public $link;
        public $eff;
        public $exp;
        public $low;
        public $top;
        public $lls;
        public $tz;

        // create from the given XML file
        public function __construct ($id, $url, $link)
        {
            $this->id = $id;
            $this->link = $link;

            $xml = simplexml_load_file ($url);

            // effective and expiration datetimes
            // - xml times are always UTC
            //   timezone is for display purposes
            $this->eff = decodedate ($xml->Group->Add->Not->dateEffective, 0);
            $this->exp = decodedate ($xml->Group->Add->Not->dateExpire, 0xFFFFFFFF);
            $this->tz  = $xml->Group->Add->Not->codeTimeZone;

            // altitude range
            //  code = ALT : MSL
            //         HEI : AGL
            //   uom = FT or FL
            $this->low = decodealt (
                    $xml->Group->Add->Not->TfrNot->TFRAreaGroup->aseTFRArea->codeDistVerLower,
                    $xml->Group->Add->Not->TfrNot->TFRAreaGroup->aseTFRArea->valDistVerLower,
                    $xml->Group->Add->Not->TfrNot->TFRAreaGroup->aseTFRArea->uomDistVerLower);
            $this->top = decodealt (
                    $xml->Group->Add->Not->TfrNot->TFRAreaGroup->aseTFRArea->codeDistVerUpper,
                    $xml->Group->Add->Not->TfrNot->TFRAreaGroup->aseTFRArea->valDistVerUpper,
                    $xml->Group->Add->Not->TfrNot->TFRAreaGroup->aseTFRArea->uomDistVerUpper);

            // outline points
            $Avx = $xml->Group->Add->Not->TfrNot->TFRAreaGroup->abdMergedArea->Avx;
            $numavx = count ($Avx);
            $this->lls = array ();
            for ($i = 0; $i < $numavx; $i ++) {
                $AvxElement = $Avx[$i];
                $ll = new stdClass ();
                $ll->lat = cvtlatlon ($AvxElement->geoLat,  "N", "S");
                $ll->lon = cvtlatlon ($AvxElement->geoLong, "E", "W");
                $this->lls[] = $ll;
            }
        }

        // write database record
        function writedb ($sqldb)
        {
            // make integer array of the lat/lons in big-endian blob format
            $ints = "";
            $iarr = array ();
            foreach ($this->lls as $ll) {
                $ilat   = 0xFFFFFFFF & intval (round ($ll->lat / 90.0 * 0x40000000));
                $ilon   = 0xFFFFFFFF & intval (round ($ll->lon / 90.0 * 0x40000000));
                $ints  .= pack ("N", $ilat) . pack ("N", $ilon);
                $iarr[] = $ilat;
                $iarr[] = $ilon;
            }

            // make sure the last point is same as first point as required by PathOut java class
            $ilen = count ($iarr);
            if (($iarr[0] != $iarr[$ilen-2]) || ($iarr[1] != $iarr[$ilen-1])) {
                $ints .= substr ($ints, 0, 8);
            }

            // write database record
            $stmt = $sqldb->prepare ("INSERT INTO pathtfrs (p_id,p_link,p_eff,p_exp,p_tz,p_low,p_top,p_ints) VALUES (:id,:link,:eff,:exp,:tz,:low,:top,:ints)");
            $stmt->bindValue (":id",   $this->id,   SQLITE3_TEXT);
            $stmt->bindValue (":link", $this->link, SQLITE3_TEXT);
            $stmt->bindValue (":eff",  $this->eff,  SQLITE3_INTEGER);
            $stmt->bindValue (":exp",  $this->exp,  SQLITE3_INTEGER);
            $stmt->bindValue (":tz",   $this->tz,   SQLITE3_TEXT);
            $stmt->bindValue (":low",  $this->low,  SQLITE3_TEXT);
            $stmt->bindValue (":top",  $this->top,  SQLITE3_TEXT);
            $stmt->bindValue (":ints", $ints,       SQLITE3_BLOB);
            $stmt->execute ();
        }
    }

    // write the string to database as is
    //  $altype = 'ALT' or 'HEI'
    //  $altuom = 'FT' or 'FL'
    // make sure exactly 3 words so app can easily parse
    function decodealt ($altype, $altval, $altuom)
    {
        $altype = str_replace (" ", "", $altype);
        $altval = str_replace (" ", "", $altval);
        $altuom = str_replace (" ", "", $altuom);
        return "$altype $altval $altuom";
    }

    // convert such as 39.7662294 to 39.766229
    //          and 074.04956895W to -74.04956895
    function cvtlatlon ($ll, $pos, $neg)
    {
        switch (substr ($ll, -1)) {
            case $pos: return   floatval (substr ($ll, 0, -1));
            case $neg: return - floatval (substr ($ll, 0, -1));
            default: return FALSE;
        }
    }

    // convert 2020-08-06T18:15:00 to unix timestamp
    function decodedate ($st, $def)
    {
        if ($st == "") return $def;
        $st = str_replace ("T", " ", $st);
        $dt = DateTime::createFromFormat ("Y-m-d H:i:s", $st);
        if (!$dt) {
            fprintf (STDERR, "decodedate ('$st') failed\n");
            return $def;
        }
        return $dt->getTimestamp ();
    }

    // write error message to stderr and exit
    function diedie ($msg)
    {
        fprintf (STDERR, "diedie: $msg\n");
        exit (1);
    }
?>
