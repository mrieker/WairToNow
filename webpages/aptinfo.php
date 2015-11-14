<?php
    /**
     * Used to manually display an airport info page in web browser.
     * Not needed for normal operation.
     */

    $expdate   = trim (file_get_contents ("datums/aptinfo_expdate.dat"));
    $faaid     = strtoupper ($_GET['faaid']);
    $firstchar = $faaid[0];
    $restchars = substr ($faaid, 1);
    system ("zcat datums/aptinfo_$expdate/$firstchar/$restchars.html.gz");
?>
