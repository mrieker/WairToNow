<?php
//    Copyright (C) 2021, Mike Rieker, Beverly, MA USA
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

    // maintain common contribution manual georeference database

    require_once 'quotedcsvsplit.php';

    date_default_timezone_set ("UTC");

    $sqldb = new SQLite3 ("../webdata/manualiapgeorefs.db");

    $sqldb->exec ("CREATE TABLE IF NOT EXISTS contributions (co_icaoid TEXT NOT NULL, " .
            "co_plateid TEXT NOT NULL, co_effdate INTEGER NOT NULL, " .
            "co_username TEXT NOT NULL, co_entdate INTEGER NOT NULL, " .
            "co_mapping TEXT NOT NULL, " .
            "PRIMARY KEY (co_icaoid,co_plateid,co_effdate,co_username))");

    $sqldb->exec ("CREATE TABLE IF NOT EXISTS users (u_username TEXT NOT NULL PRIMARY KEY, " .
            "u_passhash TEXT NOT NULL, u_emailaddr TEXT NOT NULL, u_confirm TEXT NOT NULL, " .
            "u_pwreset TEXT NOT NULL)");

    switch ($_REQUEST["func"]) {

        // user clicked on email link to confirm email address
        // just clear u_confirm of confirmation code
        case "confemail": {
            $stmt = $sqldb->prepare ("UPDATE users SET u_confirm='' WHERE u_username=:user AND u_confirm=:conf");
            $stmt->bindValue (":user", $_REQUEST["user"]);
            $stmt->bindValue (":conf", $_REQUEST["conf"]);
            $stmt->execute ();
            echo "Email Address Confirmed\n";
            break;
        }

        // download georeferences made by all users
        // outputs the database in json format
        case "download": {
            $since  = intval ($_REQUEST["since"]);
            $result = $sqldb->query ("SELECT * FROM contributions WHERE co_entdate>=$since");
            echo "[";
            $sep = "";
            while ($row = $result->fetchArray (SQLITE3_ASSOC)) {
                $un = addslashes ($row['co_username']);
                $ic = addslashes ($row['co_icaoid']);
                $pl = addslashes ($row['co_plateid']);
                $ef = intval     ($row['co_effdate']);
                $ma = addslashes ($row['co_mapping']);
                $en = intval     ($row['co_entdate']);
                echo "$sep{\"username\":\"$un\",\"icaoid\":\"$ic\",\"plateid\":\"$pl\",\"effdate\":$ef,\"mapping\":\"$ma\",\"entdate\":$en}\n";
                $sep = ",";
            }
            echo "]\n";
            break;
        }

        // html listing of all contributions
        case "listrefs": {
            echo <<<END
                <!DOCTYPE html>
                <HTML>
                    <HEAD><TITLE> Contributed Manual GeoReferences </TITLE></HEAD>
                    <BODY>
                        <H3> Contributed Manual GeoReferences </H3>
                        <TABLE>
                            <TR><TH>ICAO</TH><TH>Plate</TH><TH>EffDate</TH><TH>EntDate</TH><TH>User</TH></TR>
END;
                        $res = $sqldb->query ("SELECT * FROM contributions ORDER BY co_icaoid,co_plateid,co_effdate DESC,co_entdate DESC,co_username");
                        while ($row = $res->fetchArray (SQLITE3_ASSOC)) {
                            $icao  = $row['co_icaoid'];
                            $plate = $row['co_plateid'];
                            $eff   = $row['co_effdate'];
                            $ent   = $row['co_entdate'];
                            $user  = $row['co_username'];
                            $_pl   = urlencode ($plate);
                            $_un   = urlencode ($user);
                            $link  = "?func=markplate&icaoid=$icao&plateid=$_pl&effdate=$eff&entdate=$ent&username=$_un";
                            $_pl   = htmlspecialchars ($plate);
                            $_un   = htmlspecialchars ($user);
                            echo "<TR><TD>$icao</TD><TD><A HREF=\"$link\" TARGET=_BLANK>$_pl</A></TD><TD>$eff</TD><TD>$ent</TD><TD>$_un</TD></TR>\n";
                        }
                        echo <<<END
                        </TABLE>
                    </BODY>
                </HTML>
END;
            break;
        }

        // display the gif file, the gifname ends with '.p1' which confuses browsers
        case "markgif": {
            $gifname = $_REQUEST["gifname"];
            if (strpos ($gifname, "datums/") !== 0) break;
            if (strpos ($gifname, "..") !== FALSE) break;
            $gifsize = filesize ($gifname);
            header ("Content-length: $gifsize");
            header ("Content-type: image/gif");
            readfile ($gifname);
            break;
        }

        // draw a plate with its manual georefd markings
        case "markplate": {
            $icaoid   = trim ($_REQUEST["icaoid"]);     // airport icao id
            $plateid  = trim ($_REQUEST["plateid"]);    // displayable plate id
            $effdate  = intval ($_REQUEST["effdate"]);  // plate effective date yyyymmdd
            $entdate  = intval ($_REQUEST["entdate"]);  // date user uploaded entry yyyymmdd
            $username = trim ($_REQUEST["username"]);   // user who uploaded georef entry

            // find gif file for given icaoid,plateid,effdate
            $epcsvs = scandir ("datums");
            foreach ($epcsvs as $epcsv) {
                if (strpos ($epcsv, "europlatecsvs_") === 0) {
                    $states = scandir ("datums/$epcsv");
                    foreach ($states as $state) {
                        if (($state[0] != ".") && is_dir ("datums/$epcsv/$state")) {
                            $csvs = scandir ("datums/$epcsv/$state");
                            foreach ($csvs as $csv) {
                                if (substr ($csv, -4) == ".csv") {
                                    $csvfile = fopen ("datums/$epcsv/$state/$csv", "r");
                                    while ($csvline = fgets ($csvfile)) {
                                        $csvwords = QuotedCSVSplit (trim ($csvline));
                                        if ((count ($csvwords) > 3) &&
                                                ($csvwords[0] == $icaoid) &&
                                                ($csvwords[1] == $plateid) &&
                                                (str_replace ("-", "", $csvwords[2]) == $effdate)) {
                                            fclose ($csvfile);
                                            $gifname = "datums/" . str_replace ("csvs_", "gifs_", $epcsv) . "/$state/" . $csvwords[3] . ".p1";
                                            goto gotgifname;
                                        }
                                    }
                                    fclose ($csvfile);
                                }
                            }
                        }
                    }
                }
            }
            echo "no such gif\n";
            break;

        gotgifname:
            $imagegif = imagecreatefromgif ($gifname);
            $imgwidth = imagesx ($imagegif);
            $imheight = imagesy ($imagegif);
            imagedestroy ($imagegif);

            // find mappings for the plate uploaded by the given user on the given date
            $stmt     = $sqldb->prepare ("SELECT co_mapping FROM contributions WHERE co_icaoid=:icao AND co_plateid=:plt AND co_effdate=:eff AND co_entdate=:ent AND co_username=:user");
            $stmt->bindValue (":icao", $icaoid);
            $stmt->bindValue (":plt",  $plateid);
            $stmt->bindValue (":eff",  $effdate);
            $stmt->bindValue (":ent",  $entdate);
            $stmt->bindValue (":user", $username);
            $res      = $stmt->execute ();
            $row      = $res->fetchArray (SQLITE3_ASSOC);
            $mapjson  = $row['co_mapping'];
            $maparray = json_decode ($mapjson);

            // output html to display plate
            $title = htmlspecialchars ("$icaoid") . " &bull; " . htmlspecialchars ("$plateid") . " &bull; " . htmlspecialchars ("$effdate") . " &bull; " . htmlspecialchars ("$entdate") . " &bull; " . htmlspecialchars ("$username");
            echo <<<END
                <!DOCTYPE html>
                <HTML>
                    <HEAD>
                        <TITLE> $title </TITLE>
                        <SCRIPT LANGUAGE=JAVASCRIPT>
                            var marked = false;
                            var numloaded = 0;

                            function somethingloaded ()
                            {
                                if (++ numloaded > 1) canvasclicked ();
                            }

                            function canvasclicked ()
                            {
                                marked = ! marked;
                                var can = document.getElementById ('thecan');
                                var ctx = can.getContext ('2d');
                                var img = document.getElementById ('theimg');
                                ctx.drawImage (img, 0, 0);

                                if (marked) {
                                    ctx.font = '20px Arial';
END;
                                foreach ($maparray as $mapping) {
                                    $lat = isset ($mapping->lat) ? addslashes (formatll ($mapping->lat, "N", "S")) : "";
                                    $lon = isset ($mapping->lon) ? addslashes (formatll ($mapping->lon, "E", "W")) : "";
                                    $bmx = $mapping->bmx;
                                    $bmy = $mapping->bmy;
                                    echo "drawmark (ctx, $bmx, $bmy, '$lat', '$lon');\n";
                                }
                                echo <<<END
                                }
                            }

                            function drawmark (ctx, bmx, bmy, lat, lon)
                            {
                                // draw a cross at the bmx,bmy point
                                ctx.strokeStyle = 'red';
                                ctx.lineWidth   = 3;
                                ctx.moveTo (bmx - 20, bmy);
                                ctx.lineTo (bmx + 20, bmy);
                                ctx.moveTo (bmx, bmy - 20);
                                ctx.lineTo (bmx, bmy + 20);
                                ctx.stroke ();

                                // get width and height of latlon text
                                var text  = lat + lon;
                                var textm = ctx.measureText (text);
                                var textw = textm.width;
                                var texth = 20; //???? textm.emHeightDescent;

                                // place text a little to right and above bmx,bmy point
                                var textx = bmx + 5;
                                var texty = bmy - 5;

                                // but if text runs off right side of image, put it to the left of bmx,bmy point
                                if (textx + textw >= $imgwidth) {
                                    textx = bmx - 5 - textw;
                                }

                                // draw white background for text then draw text
                                ctx.fillStyle = 'white';
                                ctx.fillRect (textx - 2, texty - texth - 2, textw + 4, texth + 4);
                                ctx.fillStyle = 'red';
                                ctx.lineWidth = 1;
                                ctx.fillText (text, textx, texty);
                            }
                        </SCRIPT>
                    </HEAD>
                    <BODY ONLOAD="somethingloaded()">
                        <H3> $title </H3>
                        <P>Click image to toggle markings</P>
                        <CANVAS ID=thecan WIDTH=$imgwidth HEIGHT=$imheight STYLE="border:1px solid #000000;" ONCLICK="canvasclicked()"></CANVAS>
                        <IMG ID=theimg SRC="manualgeoref.php?func=markgif&gifname=$gifname" HIDDEN ONLOAD="somethingloaded()">
                    </BODY>
                </HTML>
END;
            break;
        }

        // modify existing user emailaddress
        // write new email address to database with confirmation code
        // send confirmation email with link that clears confirmation code when clicked
        case "modemail": {
            $username  = trim ($_REQUEST["username"]);
            $password  = trim ($_REQUEST["password"]);
            $emailaddr = trim ($_REQUEST["emailaddr"]);
            validateuserpass ($sqldb, $username, $password, $password);

            $confirm = bin2hex (openssl_random_pseudo_bytes (16));

            $stmt = $sqldb->prepare ("UPDATE users SET u_emailaddr=:email,u_confirm=:conf WHERE u_username=:user");
            $stmt->bindValue (":email", $emailaddr);
            $stmt->bindValue (":conf", $confirm);
            $stmt->execute ();

            sendemailconfirm ($emailaddr, $username, $confirm);
            echo "OK\n";
            break;
        }

        // modify existing user password
        // does not require that email is confirmed
        case "modpass": {
            $username = trim ($_REQUEST["username"]);
            $oldpw    = trim ($_REQUEST["oldpw"]);
            $newpw    = trim ($_REQUEST["newpw"]);
            validateuserpass ($sqldb, $username, $oldpw, $newpw);

            $stmt = $sqldb->prepare ("UPDATE users SET u_passhash=:hash WHERE u_username=:user");
            $stmt->bindValue (":user", $username);
            $stmt->bindValue (":hash", passhash ($newpw));
            $stmt->execute ();
            echo "OK\n";
            break;
        }

        // add new user to the database
        // must have a nice username
        // writes database record with email confirmation code
        // sends confirmation email with link that clears confirmation code when clicked
        case "newuser": {
            $username  = trim ($_REQUEST["username"]);
            $password  = trim ($_REQUEST["password"]);
            $emailaddr = trim ($_REQUEST["emailaddr"]);
            $confirm   = bin2hex (openssl_random_pseudo_bytes (16));

            // make sure username is sane
            $len = strlen ($username);
            if (($len < 3) || ($len > 16)) {
                echo "BAD USERNAME LENGTH $len\n";
                break;
            }
            for ($i = 0; $i < $len; $i ++) {
                $c = $username[$i];
                if (strpos ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_.", $c) === FALSE) {
                    echo "BAD USERNAME CHARACTER '$c'\n";
                    exit;
                }
            }

            $sqldb->exec ("BEGIN");

            // if already have record with same username and password, treat it like updating email address
            $stmt = $sqldb->prepare ("SELECT u_passhash FROM users WHERE u_username=:user");
            $stmt->bindValue (":user", $username);
            $res  = $stmt->execute ();
            $row  = $res->fetchArray (SQLITE3_ASSOC);
            if ($row && passtest ($password, $row['u_passhash'])) {
                $stmt = $sqldb->prepare ("UPDATE users SET u_emailaddr=:email,u_confirm=:conf WHERE u_username=:user");
                $stmt->bindValue (":user",  $username);
                $stmt->bindValue (":email", $emailaddr);
                $stmt->bindValue (":conf",  $confirm);
                $stmt->execute ();
                $sqldb->exec ("COMMIT");
                sendemailconfirm ($emailaddr, $username, $confirm);
                echo "OK\n";
                break;
            }

            // if already have record with same username but different password, say name is already taken
            if ($row) {
                $sqldb->exec ("ROLLBACK");
                echo "USERNAME ALREADY TAKEN\n";
                break;
            }

            // first time for this username, create new record and require email address confirmation
            $stmt = $sqldb->prepare ("INSERT INTO users (u_username,u_passhash,u_emailaddr,u_confirm,u_pwreset) VALUES (:user,:hash,:email,:conf,'')");
            $stmt->bindValue (":user",  $username);
            $stmt->bindValue (":hash",  passhash ($password));
            $stmt->bindValue (":email", $emailaddr);
            $stmt->bindValue (":conf",  $confirm);
            $stmt->execute ();
            $sqldb->exec ("COMMIT");

            sendemailconfirm ($emailaddr, $username, $confirm);
            echo "OK\n";
            break;
        }

        // password reset button clicked on Options page
        // email a link to the user that will reset the password
        case "pwreset": {
            $username = trim ($_REQUEST["username"]);
            $stmt = $sqldb->prepare ("SELECT u_emailaddr FROM users WHERE u_username=:user");
            $stmt->bindValue (":user", $username);
            $res  = $stmt->execute ();
            $row  = $res->fetchArray (SQLITE3_ASSOC);
            if (! $row) {
                echo "USERNAME NOT FOUND, CLEAR THEN RE-REGISTER\n";
                break;
            }
            $emailaddr = $row['u_emailaddr'];

            $pwreset   = bin2hex (openssl_random_pseudo_bytes (16));
            $stmt = $sqldb->prepare ("UPDATE users SET u_pwreset=:pwr WHERE u_username=:user");
            $stmt->bindValue (":user", $username);
            $stmt->bindValue (":pwr",  $pwreset);
            $stmt->execute ();

            $host    = $_SERVER["HTTP_HOST"];
            $script  = $_SERVER["REQUEST_SCHEME"] . "://$host" . $_SERVER["SCRIPT_NAME"];

            $_un     = urlencode ($username);
            $message = "Dear $username,\n" .
                    "You can reset your password by clicking this link:\n" .
                    "   $script?func=pwreset2&pwr=$pwreset&user=$_un\n" .
                    "Thank you.\n";
            $headers = "From: WairToNow Server <noreply@$host>";
            mail ($emailaddr, "WairToNow Password Reset", $message, $headers);
            echo "OK\n";
            break;
        }

        case "pwreset2": {
            $username = $_REQUEST["user"];
            $pwreset  = $_REQUEST["pwr"];
            $stmt = $sqldb->prepare ("SELECT u_pwreset,u_emailaddr FROM users WHERE u_username=:user");
            $stmt->bindValue (":user", $username);
            $res  = $stmt->execute ();
            $row  = $res->fetchArray (SQLITE3_ASSOC);
            if (! $row || ($row['u_pwreset'] != $pwreset)) {
                echo "BAD PASSWORD RESET LINK\n";
                break;
            }

            $newpw = bin2hex (openssl_random_pseudo_bytes (4));
            $stmt = $sqldb->prepare ("UPDATE users SET u_passhash=:hash WHERE u_username=:user");
            $stmt->bindValue (":user", $username);
            $stmt->bindValue (":hash", passhash ($newpw));
            $res  = $stmt->execute ();

            echo "<P>Password set to <B>$newpw</B></P>\n";
            echo "<P>Put that password in the Password box on the Options page then click <B>SAVE</B> button.</P>\n";
            break;
        }

        // save a new georeference entry
        // requires username, password and that email address has been confirmed
        case "saventry": {
            $username = trim ($_REQUEST["username"]);
            $password = trim ($_REQUEST["password"]);
            $row = validateuserpass ($sqldb, $username, $password, $password);
            if ($row['u_confirm'] != "") {
                echo "USERNAME AND EMAIL ADDRESS NOT CONFIRMED\n";
                break;
            }

            // don't copy same exact mapping by possibly another user
            $stmt = $sqldb->prepare ("SELECT co_username,co_entdate FROM contributions WHERE co_icaoid=:icaoid AND co_plateid=:plateid AND co_effdate=:effdate AND co_mapping=:mapping");
            $stmt->bindValue (":icaoid",   trim ($_REQUEST["icaoid"]));
            $stmt->bindValue (":plateid",  trim ($_REQUEST["plateid"]));
            $stmt->bindValue (":effdate",  intval ($_REQUEST["effdate"]));
            $stmt->bindValue (":mapping",  trim ($_REQUEST["mapping"]));
            $res  = $stmt->execute ();
            $row  = $res->fetchArray (SQLITE3_ASSOC);
            if ($row) {
                $u = $row['co_username'];
                $e = $row['co_entdate'];
                echo "DUP OF ENTRY BY $u SAVED $e\n";
                break;
            }

            // replaces entry by same username,icaoid,plateid,effdate
            $stmt = $sqldb->prepare ("INSERT OR REPLACE INTO contributions (co_username,co_icaoid,co_plateid,co_effdate,co_mapping,co_entdate) VALUES (:username,:icaoid,:plateid,:effdate,:mapping,:entdate)");
            $stmt->bindValue (":username", $username);
            $stmt->bindValue (":icaoid",   trim ($_REQUEST["icaoid"]));
            $stmt->bindValue (":plateid",  trim ($_REQUEST["plateid"]));
            $stmt->bindValue (":effdate",  intval ($_REQUEST["effdate"]));
            $stmt->bindValue (":mapping",  trim ($_REQUEST["mapping"]));
            $stmt->bindValue (":entdate",  intval (date ("Ymd")));
            $stmt->execute ();
            echo "OK\n";
            break;
        }
    }

    // validate username and password
    // return user row
    function validateuserpass ($sqldb, $username, $password, $newpw)
    {
        $stmt   = $sqldb->prepare ("SELECT u_passhash,u_confirm FROM users WHERE u_username=:username");
        $stmt->bindValue (":username", $username);
        $result = $stmt->execute ();
        $row    = $result->fetchArray (SQLITE3_ASSOC);
        if (! $row || (! passtest ($password, $row['u_passhash']) && ! passtest ($newpw, $row['u_passhash']))) {
            echo "BAD USERNAME OR PASSWORD\n";
            exit;
        }
        return $row;
    }

    // send user confirmation email
    function sendemailconfirm ($emailaddr, $username, $confirm)
    {
        $host    = $_SERVER["HTTP_HOST"];
        $script  = $_SERVER["REQUEST_SCHEME"] . "://$host" . $_SERVER["SCRIPT_NAME"];

        $_un     = urlencode ($username);
        $message = "Dear $username,\n" .
                "Please confirm email address by clicking this link:\n" .
                "   $script?func=confemail&conf=$confirm&user=$_un\n" .
                "Thank you.\n";
        $headers = "From: WairToNow Server <noreply@$host>";
        mail ($emailaddr, "WairToNow Email Confirmation", $message, $headers);
    }

    // format lat/lon number nicely
    //  input:
    //   llbin = floating-point degrees
    //   pos = char if positive
    //   neg = char if negative
    function formatll ($llbin, $pos, $neg)
    {
        if ($llbin < 0) {
            $pos = $neg;
            $llbin = - $llbin;
        }
        $degsym  = chr (176);
        $lls100  = intval (round ($llbin * 360000.0));
        if ($lls100 == 0) return "00${degsym}00'";
        $llstr   = sprintf ("$pos %02d${degsym}%02d'", intval ($lls100 / 360000), intval ($lls100 / 6000 % 60));
        $lls100 %= 6000;
        if ($lls100 > 0) {
            $llstr  .= sprintf ("%02d", intval ($lls100 / 100));
            $lls100 %= 100;
            if ($lls100 > 0) $llstr .= sprintf (".%02d", $lls100);
        }
        return $llstr;
    }

    /**
     * Compute hash of the given password.
     */
    function passhash ($password)
    {
        $salt = base64_encode (openssl_random_pseudo_bytes (17));
        $salt = '$2y$07$' . str_replace ('+', '.', substr ($salt, 0, 22));
        $hash = crypt ($password, $salt);
        if (!$hash) die ("crypt() failed\n");
        return $hash;
    }

    /**
     * See if the given password matches the given hash.
     */
    function passtest ($password, $passhash)
    {
        if (strpos ($passhash, '$2y$07$') !== 0) return FALSE;
        $salt = substr ($passhash, 0, 29);
        $hash = crypt ($password, $salt);
        return $hash == $passhash;
    }
?>
