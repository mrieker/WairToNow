<?php
    /**
     * @brief Aggregate all the charts/*.csv files into one file for downloading.
     */
    $dir_entries = scandir ('charts');
    foreach ($dir_entries as $dir_entry) {
        $len = strlen ($dir_entry);
        if (($len > 4) && (substr ($dir_entry, $len - 4) == '.csv')) {
            readfile ("charts/$dir_entry");
        }
    }
?>
