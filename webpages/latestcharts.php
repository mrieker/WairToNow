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
                if (isset ($_GET["sortby"])) $sortby = $_GET["sortby"];
                $chartfiles = scandir ("charts");
                $chartnames = array ();
                $effdates = array ();
                $expdates = array ();
                foreach ($chartfiles as $chartfile) {
                    if (strpos ($chartfile, '.csv') === strlen ($chartfile) - 4) {
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
            ?>
        </TABLE>
    </BODY>
</HTML>
