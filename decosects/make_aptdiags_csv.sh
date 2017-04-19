#!/bin/bash
#
#  Make aptdiags.csv for Avare
#
cycles28=`./cureffdate -28 -x yyyymmdd`
cat datums/apdgeorefs_$cycles28/*.csv | php make_aptdiags_csv.php $cycles28 | sort > aptdiags_$cycles28.csv
