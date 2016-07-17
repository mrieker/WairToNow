<?php
    $incoming = fopen ("php://input", "r");
    if (!$incoming) die ("error opening input stream\n");
    $savename = time () . "." . posix_getpid ();
    $savefile = fopen ("../webdata/acrauploads/$savename.acra.gz", "w");
    if (!$savefile) die ("error creating output file $savename\n");
    while ($data = fread ($incoming, 4096)) {
        fwrite ($savefile, $data);
    }
    fclose ($savefile);
    fclose ($incoming);
?>
