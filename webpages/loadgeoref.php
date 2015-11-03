<?php
    /*
     * Send records to the client.
     */
    $filesize = filesize ('georefs/manual.csv');
    echo "$filesize\n";
    $since   = intval ($_REQUEST['since']);
    $csvfile = fopen ('georefs/manual.csv', 'r');
    fseek ($csvfile, $since, SEEK_SET);
    while ($csvline = fgets ($csvfile)) {
        echo $csvline;
    }
    fclose ($csvfile);
?>
