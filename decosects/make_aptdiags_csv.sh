#!/bin/bash
#
#  Make aptdiags.csv for Avare
#
cycles28=`./cureffdate -28 -x yyyymmdd`
cycles56=`./cureffdate     -x yyyymmdd`
cat datums/apdgeorefs_$cycles28/*.csv | php make_aptdiags_csv.php $cycles56 | sort > aptdiags_$cycles28.csv
