#!/bin/bash -v
exec nohup ./updateverything.sh < /dev/null > updateverything.log 2>&1 &
