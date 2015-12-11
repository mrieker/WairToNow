#!/bin/bash -v
export next28=1
exec nohup ./updateverything.sh < /dev/null > updateverything.log 2>&1 &
