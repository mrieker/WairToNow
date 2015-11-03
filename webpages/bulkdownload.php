<?php
    #
    # Bulk file downloader
    #
    # Downloads several files given in POST vars f0, f1, ...
    # Each file returned in that order as:
    #   @@name=givenfilename\n
    #   @@size=sizeinbytes\n
    #   binaryfiledata
    #   @@eof\n
    # Then after all files:
    #   @@done\n
    #
    $n = 0;
    while (isset ($_POST["f$n"])) {
        $name = $_POST["f$n"];
        if ($name[0] == '/') {
            die ("name $name cannot start with /\n");
        }
        if (strpos ($name, '../') !== FALSE) {
            die ("name $name cannot contain ../\n");
        }
        echo "@@name=$name\n";
        $size = filesize ($name);
        echo "@@size=$size\n";
        $read = readfile ($name);
        if ($read != $size) {
            die ("size $size ne read $read for $name\n");
        }
        echo "@@eof\n";
        $n ++;
    }
    echo "@@done\n";
?>
