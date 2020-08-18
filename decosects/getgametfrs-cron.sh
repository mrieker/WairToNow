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
exec php getgametfrs4.php > /dev/null
