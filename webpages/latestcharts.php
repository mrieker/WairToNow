<HTML>
    <HEAD><TITLE>Latest Charts</TITLE></HEAD>
    <BODY>
        <TABLE>
            <TR>
                <TH ALIGN=LEFT><A HREF="?sortby=eff">Eff Date</A></TH>
                <TH ALIGN=LEFT><A HREF="?sortby=exp">Exp Date</A></TH>
                <TH ALIGN=LEFT><A HREF="?sortby=chart">Chart</A></TH>
            </TR>
            <?php
                $sortby = "chart";
                if (isset ($_REQUEST["sortby"])) $sortby = $_REQUEST["sortby"];
                $chartfiles = scandir ("charts");
                $chartnames = array ();
                $effdates = array ();
                $expdates = array ();
                foreach ($chartfiles as $chartfile) {
                    if (strpos ($chartfile, '.csv') === strlen ($chartfile) - 4) {
                        if (!haslateredition ($chartfile, $chartfiles)) {
                            $csvline   = trim (file_get_contents ("charts/$chartfile"));
                            $csvparts  = explode (',', $csvline);
                            $effdate   = $csvparts[14];
                            $expdate   = $csvparts[15];
                            $chartname = $csvparts[16];
                            $chartnames[$chartname] = $chartname;
                            $effdates[$chartname] = $effdate;
                            $expdates[$chartname] = $expdate;
                        }
                    }
                }
                switch ($sortby) {
                    case "chart": {
                        $sorted = $chartnames;
                        break;
                    }
                    case "eff": {
                        $sorted = $effdates;
                        break;
                    }
                    case "exp": {
                        $sorted = $expdates;
                        break;
                    }
                }
                $keys = array ();
                foreach ($sorted as $chartname => $value) {
                    $keys["$value $chartname"] = $chartname;
                }
                ksort ($keys);
                foreach ($keys as $chartname) {
                    $effdate = $effdates[$chartname];
                    $expdate = $expdates[$chartname];
                    echo "<TR><TD ALIGN=RIGHT>$effdate</TD><TD ALIGN=RIGHT>$expdate</TD><TD ALIGN=LEFT>$chartname</TD></TR>\n";
                }

                /**
                 * See if thisfile has a later revision.
                 * eg, New_York_SEC_99.csv vs New_York_SEC_100.csv
                 */
                function haslateredition ($thisfile, $chartfiles)
                {
                    $thisfile = str_replace (".csv", "", $thisfile);
                    $i = strrpos ($thisfile, "_");
                    if ($i === FALSE) return FALSE;
                    $thisrevn = intval (substr ($thisfile, ++ $i));
                    foreach ($chartfiles as $thatfile) {
                        if (strpos ($thatfile, ".csv") !== strlen ($thatfile) - 4) continue;
                        $thatfile = str_replace (".csv", "", $thatfile);
                        $j = strrpos ($thatfile, "_");
                        if ($j === FALSE) continue;
                        $thatrevn = intval (substr ($thatfile, ++ $j));
                        if ((substr ($thisfile, 0, $i) == substr ($thatfile, 0, $j)) &&
                            ($thatrevn > $thisrevn)) return TRUE;
                    }
                    return FALSE;
                }
            ?>
        </TABLE>
    </BODY>
</HTML>
