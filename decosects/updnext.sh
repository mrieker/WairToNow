#!/bin/bash -v
#
#  Run near the end of a 28-day cycle to fetch files for next cycle
#
export next28=1
exec nohup ./updateverything.sh < /dev/null > updateverything.log 2>&1 &
