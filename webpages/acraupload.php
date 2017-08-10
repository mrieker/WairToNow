<?php
    $incoming = fopen ("php://input", "r");
    if (!$incoming) die ("error opening input stream\n");
    $savename = time () . "." . posix_getpid ();
    $savepath = "../webdata/acrauploads/$savename.acra.gz";
    $savefile = fopen ($savepath, "w");
    if (!$savefile) die ("error creating output file $savename\n");
    while ($data = fread ($incoming, 4096)) {
        fwrite ($savefile, $data);
    }
    fclose ($savefile);
    fclose ($incoming);
    if (file_exists ("../webdata/acranotify.php")) {
        require_once "../webdata/acranotify.php";
    }
?>
