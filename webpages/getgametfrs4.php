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

    require_once 'gettzatll.php';

    $data = "../webdata/tfrs";
    @mkdir ($data);
    chdir ($data);

    date_default_timezone_set ("UTC");

    // make sure only one runs at a time
    $lockfile = fopen ("getgametfrs4.lock", "c");
    if (!$lockfile) diedie ("error opening lockfile\n");
    if (!flock ($lockfile, LOCK_EX)) diedie ("error locking lockfile\n");

    // open/create database file that gets sent to the app
    $sqldb = new SQLite3 ("gametfrs4.db");
    if (!$sqldb) diedie ("error opening gametfrs4.db");

    $sqldb->exec ("CREATE TABLE IF NOT EXISTS asof (as_of INTEGER NOT NULL)");
    $sqldb->exec ("CREATE TABLE IF NOT EXISTS stadiums (s_name TEXT PRIMARY KEY, s_lat FLOAT NOT NULL, s_lon FLOAT NOT NULL, s_tz TEXT NOT NULL)");
    $sqldb->exec ("CREATE TABLE IF NOT EXISTS gametfrs (g_eff INTEGER NOT NULL, g_stadium TEXT NOT NULL, g_exp INTEGER NOT NULL, g_desc TEXT NOT NULL, PRIMARY KEY (g_eff, g_stadium), FOREIGN KEY (g_stadium) REFERENCES stadiums (s_name))");
    $sqldb->exec ("CREATE INDEX IF NOT EXISTS gametfrs_stadium ON gametfrs (g_stadium)");

    // see if we already have today's updates
    $now = time ();
    $day = date ("Ymd", $now);
    if (! file_exists ("gametfrs4_$day.db.gz")) {

        $sqldb->exec ("BEGIN");

        // read list of stadiums from arcgis.com
        // gives locations of all stadiums
        //  webpage:  https://ais-faa.opendata.arcgis.com/datasets/67af16061c014365ae9218c489a321be_0/geoservice
        $stmt = $sqldb->prepare ("INSERT INTO stadiums (s_name,s_lat,s_lon,s_tz) VALUES (:name,:lat,:lon,:tz) ON CONFLICT (s_name) DO UPDATE SET s_lat=:lat,s_lon=:lon,s_tz=:tz WHERE s_name=:name");
        $arcgisjson = file_get_contents ("https://services6.arcgis.com/ssFJjBXIUyZDrSYZ/arcgis/rest/services/Stadiums/FeatureServer/0/query?where=1%3D1&outFields=*&outSR=4326&f=json");
        if (!$arcgisjson) dieie ("error accessing arcgis.com\n");
        file_put_contents ("arcgis_$now.json", $arcgisjson);
        $arcgisparsed = json_decode ($arcgisjson);
        foreach ($arcgisparsed->features as $feature) {
            $lat = floatval ($feature->geometry->y);
            $lon = floatval ($feature->geometry->x);
            $tz  = getTZAtLL ($lat, $lon);
            $stmt->bindValue (":name", $feature->attributes->NAME, SQLITE3_TEXT);
            $stmt->bindValue (":lat",  $lat, SQLITE3_FLOAT);
            $stmt->bindValue (":lon",  $lon, SQLITE3_FLOAT);
            $stmt->bindValue (":tz",   $tz,  SQLITE3_TEXT);
            $stmt->execute ();
            $stmt->reset ();
        }

        // get ready to get game schedules for next few days
        $stmt = $sqldb->prepare ("INSERT INTO gametfrs (g_eff,g_stadium,g_exp,g_desc) VALUES (:eff,:stadium,:exp,:desc) ON CONFLICT (g_eff,g_stadium) DO UPDATE SET g_exp=:exp,g_desc=:desc WHERE g_eff=:eff AND g_stadium=:stadium");

        // read MLB schedule from espn.com
        $html = file_get_contents ("https://www.espn.com/mlb/schedule");
        file_put_contents ("mlb_$now.html", $html);
        $html = str_replace ("Undecided</a>", "Undecided", $html);
        $dm = new DecodeMLB ();
        $dm->parse ($html);

        // read NASCAR schedule from espn.com
        $html = file_get_contents ("https://www.espn.com/racing/schedule");
        file_put_contents ("nascar_$now.html", $html);
        $tablebeg  = strpos ($html, "<table");
        $tableend  = strpos ($html, "</table>");
        $tablehtml = substr ($html, $tablebeg, $tableend + 8 - $tablebeg);
        $tablehtml = str_replace ("&nbsp;", " ", $tablehtml);
        $dn = new DecodeNascar ();
        $dn->parse ($tablehtml);

        // read NCAA football schedule from espn
        $html = file_get_contents ("https://www.espn.com/college-football/schedule");
        file_put_contents ("ncaa_$now.html", $html);
        $dn = new DecodeNFL ();
        $dn->league = "NCAA";
        $dn->parse ($html);

        // read NFL schedule from espn.com
        $html = file_get_contents ("https://www.espn.com/nfl/schedule");
        file_put_contents ("nfl_$now.html", $html);
        ////$et = new EchoTags ();
        ////$et->parse ($html);
    donflpage:
        $dn = new DecodeNFL ();
        $dn->league = "NFL";
        $dn->parse ($html);
        ////foreach ($nflpages as $url => $st) {
        ////    if ($st) {
        ////        $html = file_get_contents ("https://www.espn.com$url");
        ////        if ($html) {
        ////            $nflpages[$url] = FALSE;
        ////            goto donflpage;
        ////        }
        ////    }
        ////}

        // delete entries more than a couple days old cuz they can't possibly be active any more
        $sqldb->exec ("DELETE FROM gametfrs WHERE g_eff<$now-200000");

        // write when the database was last updated
        $sqldb->exec ("DELETE FROM asof");
        $sqldb->exec ("INSERT INTO asof (as_of) VALUES ($now)");

        $sqldb->exec ("COMMIT");

        $sqldb->close ();

        // make today's gzip file
        $rc = -1;
        if ((system ("gzip -c gametfrs4.db > gametfrs4_$day.db.gz.tmp", $rc) === FALSE) || ($rc != 0)) {
            diedie ("gzip command failed $rc\n");
        }
        rename ("gametfrs4_$day.db.gz.tmp", "gametfrs4_$day.db.gz");
    }

    fclose ($lockfile);
    readfile ("gametfrs4_$day.db.gz");

    exit;

    // eg, writeDebugFiles ("mlb", $now, $html);
    function writeDebugFiles ($league, $now, $html)
    {
        file_put_contents ("$league-$now.html", $html);
        $dump = fopen ("$league-$now.parsed", "w");
        $et = new EchoTags ($dump);
        $et->parse ($html);
        fclose ($dump);
    }

    // print error to STDERR and exit with error status
    function diedie ($msg)
    {
        fprintf (STDERR, "diedie: $msg\n");
        exit (1);
    }

    // convert such as "2020-08-23T20:00Z" to unix time integer
    function myTimeConv ($efftime)
    {
        date_default_timezone_set ("UTC");
        $efftime = str_replace ("T", " ", str_replace ("Z", "", $efftime));
        $effdate = DateTime::createFromFormat ("Y-m-d H:i", $efftime);
        return $effdate->getTimestamp ();
    }

    // write record to gametfrs table
    //  input:
    //   startime = game start time unix time integer
    //   duration = game duration in seconds
    //   stadium  = stadium name in arcgis.com format
    //   desc     = event description
    function writeGameTFR ($startime, $duration, $stadium, $desc)
    {
        global $stmt;

        $efftime = $startime - 3600;
        $exptime = $startime + $duration + 3600;
        $stmt->bindValue (":eff",     $efftime, SQLITE3_INTEGER);
        $stmt->bindValue (":stadium", $stadium, SQLITE3_TEXT);
        $stmt->bindValue (":exp",     $exptime, SQLITE3_INTEGER);
        $stmt->bindValue (":desc",    $desc,    SQLITE3_TEXT);
        $stmt->execute ();
        $stmt->reset ();
    }

    /*************************\
     *  General HTML parser  *
    \*************************/

    abstract class ParseHtml {

        // callbacks as html is parsed
        abstract function openTag ($tags, $attrs);
        abstract function gotText ($tags, $text);
        abstract function closeTag ($tags);

        // https://html.spec.whatwg.org/multipage/syntax.html#void-elements
        private static $selfclosing = array (
                "!doctype" => TRUE,
                "area" => TRUE,
                "base" => TRUE,
                "br" => TRUE,
                "col" => TRUE,
                "embed" => TRUE,
                "hr" => TRUE,
                "img" => TRUE,
                "input" => TRUE,
                "link" => TRUE,
                "meta" => TRUE,
                "param" => TRUE,
                "source" => TRUE,
                "track" => TRUE,
                "wbr" => TRUE,
        );

        // parse html, call callbacks as we go along
        function parse ($html)
        {
            $len = strlen ($html);
            $tags = "";

            // find next tag
            for ($i = 0; ($j = strpos ($html, "<", $i)) !== FALSE;) {

                // text is everything after last > and before this <
                $text = trim (substr ($html, $i, $j - $i));
                if ($text != "") $this->gotText ($tags, self::unspecial ($text));

                // find > following the <
                $i = strpos ($html, ">", ++ $j);
                if ($i === FALSE) break;

                // get whole tag without < > brackets
                $wholetag = trim (substr ($html, $j, $i ++ - $j));
                $wtlen = strlen ($wholetag);

                // check for comment, skip everything to -->
                if (($wtlen >= 3) && (substr ($wholetag, 0, 3) == "!--")) {
                    $i = strpos ($html, "-->", $j);
                    if ($i === FALSE) return;
                    $i += 3;
                    continue;
                }

                // self-closing via trailing /
                $selfclose = ($wtlen > 0) && ($wholetag[$wtlen-1] == "/");
                if ($selfclose) $wholetag = trim (substr ($wholetag, 0, -- $wtlen));

                // tag name is first word after the <
                // might include leading /
                $wtwrd = strpos ($wholetag, " ");
                if ($wtwrd === FALSE) $wtwrd = $wtlen;
                $tagname = strtolower (substr ($wholetag, 0, $wtwrd));

                if ($tagname != "") {
                    if ($tagname[0] == "/") {
                        // leading /, it is close
                        $tagname = substr ($tagname, 1);
                        // ignore if self-closing (eg </br>) cuz we already closed it
                        if (! isset (self::$selfclosing[$tagname])) {
                            $sptagsp = " $tagname ";
                            do {
                                if ($tags == "") return;
                                $this->closeTag ($tags);
                                $k = strrpos ($tags, "  ");
                                if ($k === FALSE) $k = -1;
                                $splastsp = substr ($tags, ++ $k);
                                $tags = substr ($tags, 0, $k);
                            } while ($splastsp != $sptagsp);
                        }
                    } else {
                        // no leading /, it is open
                        $k = strlen ($tags);
                        $tags .= " $tagname ";
                        $attrs = self::getattrs ($wholetag, $wtwrd);
                        $this->openTag ($tags, $attrs);
                        if ($selfclose || isset (self::$selfclosing[$tagname])) {
                            $this->closeTag ($tags);
                            $tags = substr ($tags, 0, $k);
                        } else if ($tagname == "script") {
                            $endtag = "</script>";
                            $begscript = $i;
                            $matched = 0;
                            for ($k = $i; $matched < 9; $k ++) {
                                if ($matched == 0) $endscript = $k;
                                if ($k >= $len) break;
                                $c = strtolower ($html[$k]);
                                if ($c <= " ") continue;
                                if ($c == $endtag[$matched]) $matched ++;
                                else $matched = 0;
                            }
                            $script = trim (substr ($html, $begscript, $endscript - $begscript));
                            if ($script > "") $this->gotText ($tags, $script);
                            $i = $endscript;
                        }
                    }
                }
            conti:;
            }

            $text = trim (substr ($html, $i));
            if ($text != "") $this->gotText ($tags, self::unspecial ($text));
        }

        // build attribute array
        //  input:
        //   $wholetag = whole tag string without < and >
        //   $wtwrd = offset in wholetag at end of first word
        //  output:
        //   returns array of attributes from wholetag
        private static function getattrs ($wholetag, $wtwrd)
        {
            $wtlen = strlen ($wholetag);
            $attrs = array ();
            $key = "";
            while ($wtwrd < $wtlen) {
                $c = $wholetag[$wtwrd++];

                // skipping spaces while getting key
                if ($c <= " ") {
                    if ($key == "") continue;
                    if ($wtwrd < $wtlen) {
                        $c = $wholetag[$wtwrd];
                        if ($c <= " ") continue;
                        if ($c == "=") continue;
                    }
                    $attrs[$key] = FALSE;
                    $key = "";
                    continue;
                }

                // non-space char for key
                if ($c != "=") {
                    $key .= strtolower ($c);
                    continue;
                }

                // got = so a value follows
                $val = "";
                $q = "";
                while ($wtwrd < $wtlen) {
                    if ($wholetag[$wtwrd] > " ") break;
                    $wtwrd ++;
                }
                while ($wtwrd < $wtlen) {
                    $c = $wholetag[$wtwrd++];
                    if ($c == "\\") {
                        if ($wtwrd < $wtlen) $val .= $wholetag[$wtwrd++];
                        continue;
                    }
                    if (($q != "") && ($c != $q)) {
                        $val .= $c;
                        continue;
                    }
                    if (($q != "") && ($c == $q)) {
                        $q = "";
                        continue;
                    }
                    if (($c == "'") || ($c == '"')) {
                        $q = $c;
                        continue;
                    }
                    if ($c <= " ") break;
                    $val .= $c;
                }
                $attrs[$key] = $val;
                $key = "";
            }
            if ($key != "") $attrs[$key] = FALSE;
            return $attrs;
        }

        // remove html-style escapes
        private static function unspecial ($html)
        {
            $html = str_replace ("&gt;",  ">", $html);
            $html = str_replace ("&lt;",  "<", $html);
            $html = str_replace ("&amp;", "&", $html);
            return $html;
        }
    }

    /* Parser that just prints the decoded HTML */

    class EchoTags extends ParseHtml {
        private $dump;

        function __construct ($dump)
        {
            $this->dump = $dump;
        }

        function openTag ($tags, $attrs)
        {
            fwrite ($this->dump, " OPEN: <$tags>\n");
            foreach ($attrs as $k => $v) {
                if ($v === FALSE) $v = "FALSE";
                else if (is_string ($v)) {
                    $v = '"' . addslashes ($v) . '"';
                    $v = str_replace ("\n", "\\n", $v);
                }
                fwrite ($this->dump, "    $k=$v\n");
            }
        }

        function gotText ($tags, $text)
        {
            fwrite ($this->dump, " TEXT: <$tags> $text\n");
        }

        function closeTag ($tags)
        {
            fwrite ($this->dump, "CLOSE: <$tags>\n");
        }
    }

    /************\
     *  NASCAR  *
    \************/

    class DecodeNascar extends ParseHtml {
        public $colnum;
        public $columns;
        public $year;

        private static $monthnums = array (
                "Jan" =>  1,
                "Feb" =>  2,
                "Mar" =>  3,
                "Apr" =>  4,
                "May" =>  5,
                "Jun" =>  6,
                "Jul" =>  7,
                "Aug" =>  8,
                "Sep" =>  9,
                "Oct" => 10,
                "Nov" => 11,
                "Dec" => 12);

        function openTag ($tags, $attrs)
        {
            switch ($tags) {
                case " table  tr ": {
                    $this->colnum = 0;
                    $this->columns = array ();
                    break;
                }
                case " table  tr  td ": {
                    $this->columns[$this->colnum] = array ();
                    break;
                }
            }
        }

        function gotText ($tags, $text)
        {
            switch ($tags) {
                case " table  tr  td  b ":
                case " table  tr  td ": {
                    $this->columns[$this->colnum][] = str_replace ("&nbsp;", " ", $text);
                    break;
                }
            }
        }

        function closeTag ($tags)
        {
            switch ($tags) {

                // end of column
                case " table  tr  td ": {
                    $this->colnum ++;
                    break;
                }

                // end of row
                case " table  tr ": {

                    // "2020 Schedule"
                    if ((count ($this->columns) == 1) && (count ($this->columns[0]) == 1)) {
                        $sched = explode (" ", $this->columns[0][0]);
                        if ((count ($sched) == 2) && ($sched[1] == "Schedule")) {
                            $this->year = $sched[0];    // "2020"
                        }
                    }

                    // date/time  title/venue  tv  tickets
                    if (count ($this->columns) == 4) {
                        $datetime = $this->columns[0];
                        $titlvenu = $this->columns[1];
                        if ((count ($datetime) == 2) && (count ($titlvenu) >= 2)) {
                            $date  = explode (" ", $datetime[0]);   // "Mon, Feb 17"
                            $time  = explode (" ", $datetime[1]);   // "4:00 PM ET"
                            $mname = $date[1];
                            $dom   = intval ($date[2]);
                            $hhmm  = explode (":", $time[0]);
                            $ampm  = $time[1];
                            $tznam = $time[2];

                            $mnum  = self::$monthnums[$mname];
                            if (!$mnum) die ("bad month $mname\n");
                            $hour  = intval ($hhmm[0]) % 12;
                            if ($ampm == "PM") $hour += 12;
                            $min   = intval ($hhmm[1]);
                            if ($tznam != "ET") die ("bad tz $tznam\n");

                            date_default_timezone_set ("America/New_York");
                            $unix = mktime ($hour, $min, 0, $mnum, $dom, $this->year);

                            writeGameTFR ($unix, 5*3600, $titlvenu[1], $titlvenu[0]);
                        }
                    }
                    break;
                }
            }
        }
    }

    /*************************************\
     *  NFL                              *
     *  Also works for college football  *
    \*************************************/

    $nflpages = array ();   // linked-to pages

    class DecodeNFL extends ParseHtml {
        public $league;

        private $awayteam;  // "Denver Donkeys"
        private $colnum;
        private $hometeam;  // "New England Cheats"
        private $location;  // "Ford Field, Detroit"
        private $startime;  // unix timestamp

        private $locolumn;

        // stadiums with names not as in arcgis.com database
        private static $propernames = array (
                "WilliamsStadium,Lynchburg,VA"      => "Williams Brice Stadium",
                "M.M.RobertsStadium,Hattiesburg,MS" => "M.M. Roberts Stadium",
                "EmpowerFieldatMileHigh,Denver"     => "Sports Authority Field at Mile High",
        );

        function openTag ($tags, $attrs)
        {
            global $nflpages;

            // links to other weekly schedule pages
            if (isset ($attrs["data-url"])) {
                $dataurl = $attrs["data-url"];
                if ((strpos ($dataurl, "/nfl/schedule/_/week/") === 0) && (strpos ($dataurl, "/year/") === FALSE)) {
                    if (! isset ($nflpages[$dataurl])) {
                        $nflpages[$dataurl] = TRUE;
                    }
                }
            }

            // start of a table row
            if (substr ($tags, -4) == " tr ") {
                $this->awayteam = FALSE;
                $this->colnum   = 0;
                $this->hometeam = FALSE;
                $this->location = FALSE;
                $this->startime = FALSE;
            }

            // maybe this column has team name in the title
            if ((substr ($tags, -6) == " abbr ") && isset ($attrs["title"])) {
                switch ($this->colnum) {
                    case 0: $this->awayteam = $attrs["title"]; break;
                    case 1: $this->hometeam = $attrs["title"]; break;
                }
            }

            // maybe we have a time
            if (isset ($attrs["data-date"])) {
                $this->startime = myTimeConv ($attrs["data-date"]);
            }

            // maybe this column has the location in the text
            $this->locolumn = isset ($attrs["class"]) && ($attrs["class"] == "schedule-location");
        }

        function gotText ($tags, $text)
        {
            if ($this->locolumn) $this->location = $text;
        }

        function closeTag ($tags)
        {
            $this->locolumn = FALSE;

            // closing table column, increment column number
            if (substr ($tags, -4) == " td ") $this->colnum ++;

            // closing table row, write game TFR record
            if ((substr ($tags, -4) == " tr ") && $this->startime && $this->location) {

                // maybe fixup stadium name to match arcgis.com database
                $nospaces = str_replace (" ", "", $this->location);
                if (isset (self::$propernames[$nospaces])) {
                    $loc = self::$propernames[$nospaces];
                } else {
                    $i = strpos ($this->location, ",");
                    $loc = ($i === FALSE) ? $this->location : substr ($this->location, 0, $i);
                }

                writeGameTFR ($this->startime, 4*3600, $loc, "$this->awayteam vs $this->hometeam");
            }
        }
    }

    /*********\
     *  MLB  *
    \*********/

    class DecodeMLB extends ParseHtml {
        private $awayteam;
        private $colnum;
        private $homecode;
        private $hometeam;
        private $startime;

        // convert home team code to stadium lat,lon
        private static $stadiums = array (
                "ARI" => "33.445274,-112.066811",   // "Chase Field",                     // Arizona Diamondbacks
                "ATL" => "33.890634, -84.467634",   // "Truist Park",                     // Atlanta Braves
                "BAL" => "39.283957, -76.621560",   // "Oriole Park at Camden Yards",     // Baltimore Orioles
                "BOS" => "42.346228, -71.097708",   // "Fenway Park",                     // Boston Red Sox
                "CHC" => "41.948066, -87.655652",   // "Wrigley Field",                   // Chicago Cubs
                "CHW" => "41.829857, -87.633655",   // "Guaranteed Rate Field",           // Chicago White Sox
                "CIN" => "39.097215, -84.506462",   // "Great American Ball Park",        // Cincinnati Reds
                "CLE" => "41.495794, -81.685301",   // "Progressive Field",               // Cleveland Indians
                "COL" => "39.756356,-104.994149",   // "Coors Field",                     // Colorado Rockies
                "DET" => "42.339285, -83.048834",   // "Comerica Park",                   // Detroit Tigers
                "HOU" => "29.757182, -95.355545",   // "Minute Maid Park",                // Houston Astros
                "KC"  => "39.051645, -94.480435",   // "Kauffman Stadium",                // Kansas City Royals
                "LAA" => "33.800313,-117.882720",   // "Angel Stadium of Anaheim",        // Los Angeles Angels
                "LAD" => "34.073882,-118.239962",   // "Dodger Stadium",                  // Los Angeles Dodgers
                "MIA" => "25.778093, -80.219530",   // "Marlins Ballpark",                // Miami Marlins
                "MIL" => "43.028127, -87.971183",   // "Miller Park",                     // Milwaukee Brewers
                "MIN" => "44.981757, -93.277777",   // "Target Field",                    // Minnesota Twins
                "NYM" => "40.757047, -73.845890",   // "Citi Field",                      // New York Mets
                "NYY" => "40.829636, -73.926240",   // "Yankee Stadium",                  // New York Yankees
                "OAK" => "37.751613,-122.200632",   // "Oakland Alameda Coliseum",        // Oakland Athletics
                "PHI" => "39.906185, -75.166471",   // "Citizens Bank Park",              // Philadelphia Phillies
                "PIT" => "40.447057, -80.006161",   // "PNC Park",                        // Pittsburgh Pirates
                "SD"  => "32.707574,-117.157050",   // "Petco Park",                      // San Diego Padres
                "SF"  => "32.747802, -97.093035",   // "AT&T Park",                       // San Francisco Giants
                "SEA" => "47.591799,-122.331900",   // "Safeco Field",                    // Seattle Mariners
                "STL" => "38.622584, -90.193064",   // "Busch Stadium",                   // St. Louis Cardinals
                "TAM" => "27.768200, -82.653400",   // "Tropicana Field",                 // Tampa Bay Rays
                "TEX" => "32.746705, -97.087807",   // "Globe Life Field",                // Texas Rangers
                "TOR" => "43.641399, -79.389399",   // "Rogers Centre",                   // Toronto Blue Jays
                "WSH" => "38.873057, -77.007402",   // "Nationals Park"                   // Washington Nationals
        );

        function openTag ($tags, $attrs)
        {
            // start of a table row
            if (substr ($tags, -4) == " tr ") {
                $this->awayteam = FALSE;
                $this->colnum   = 0;
                $this->hometeam = FALSE;
                $this->homecode = FALSE;
                $this->startime = FALSE;
            }

            // maybe we have a time
            // eg "2020-08-17T21:15Z"
            if (isset ($attrs["data-date"])) {
                $this->startime = myTimeConv ($attrs["data-date"]);
            }

            // maybe we have a team name
            if ((substr ($tags, -6) == " abbr ") && isset ($attrs["title"])) {
                switch ($this->colnum) {
                    case 0: $this->awayteam = $attrs["title"]; break;
                    case 1: $this->hometeam = $attrs["title"]; break;
                }
            }
        }

        function gotText ($tags, $text)
        {
            if ($this->colnum === 1) {
                $this->homecode = $text;
            }
        }

        function closeTag ($tags)
        {
            global $sqldb;

            if (substr ($tags, -4) == " td ") $this->colnum ++;

            if (substr ($tags, -4) == " tr ") {
                if ($this->homecode && $this->startime) {
                    $stalls = explode (",", self::$stadiums[$this->homecode]);
                    $stalat = floatval ($stalls[0]);
                    $stalon = floatval ($stalls[1]);
                    $stanam = $sqldb->querySingle ("SELECT s_name FROM stadiums WHERE s_lat>$stalat-0.001 AND s_lat<$stalat+0.001 AND s_lon>$stalon-0.001 AND s_lon<$stalon+0.001");
                    if ($stanam) {
                        writeGameTFR ($this->startime, 5*3600, $stanam, "$this->awayteam vs $this->hometeam");
                    } else {
                        fprintf (STDERR, "no stadium for mlb $this->homecode at $stalat,$stalon\n");
                    }
                }
                $this->colnum = FALSE;
            }
        }
    }
?>
