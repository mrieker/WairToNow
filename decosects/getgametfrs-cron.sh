#!/bin/bash
#
#  Runs nightly via cron to build gametfrs database.
#  Do this so we have games that are pending today
#  but will be live tomorrow so we are sure to have
#  their start time in the database.
#
cd `dirname $0`
cd ../webpages
date
php getgametfrs4.php > /dev/null
cd ../webdata/tfrs
find . -name arcgis_\*.json -mtime +5 -delete
find . -name gametfrs_\*.db\* -mtime +5 -delete
find . -name gametfrs4_\*.d\* -mtime +5 -delete
find . -name mlb_\*.html -mtime +5 -delete
find . -name nascar\*.html -mtime +5 -delete
find . -name ncaa_\*.html -mtime +5 -delete
find . -name nfl_\*.html -mtime +5 -delete
