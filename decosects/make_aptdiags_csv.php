<?php
    // cycles28=`./cureffdate -28 -x yyyymmdd`
    // cycles56=`./cureffdate     -x yyyymmdd`
    // cat datums/apdgeorefs_$cycles28/*.csv | php make_aptdiags_csv.php $cycles56 | sort > aptdiags.csv

    $cycles56 = $argv[1];
    $file = fopen ("datums/airports_$cycles56.csv", "r");
    if (!$file) die ("airports file open error\n");
    $faaids = array ();
    while ($line = fgets ($file)) {
        $cols = explode (',', $line);
        $faaids[$cols[0]] = $cols[1];
    }
    fclose ($file);

    while ($line = fgets (STDIN)) {
        $i = strpos ($line, ',');
        $icaoid = substr ($line, 0, $i);
        $faaid  = $faaids[$icaoid];
        echo $faaid . substr ($line, $i);
    }
?>
