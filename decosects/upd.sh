#!/bin/bash -v
#
#  Run during a cycle to get files for the current cycle
#
export next28=0
exec nohup ./updateverything.sh < /dev/null > updateverything.log 2>&1 &
