<?php

    /*
     * Read csv line from post stream.
     * It does not have a newline char on it.
     */
    $csvline = file_get_contents ("php://input");

    /*
     * Append record to existing georefs/manual.csv file.
     * All previous records must remain intact, or at least their length.
     */
    $csvfile = fopen ('georefs/manual.csv', 'a');
    if (!$csvfile) die ("fopen error\n");
    if (!fputs ($csvfile, "$csvline\n")) die ("fwrite error\n");
    if (!fclose ($csvfile)) die ("fclose error\n");

    /*
     * Tell app we sucuessfully saved it.
     */
    echo "OK\n";
?>
