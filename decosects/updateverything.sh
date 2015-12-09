#!/bin/bash -v
#
#  Updates everything
#
#    export next28=0 : do current cycle
#                  1 : do next cycle
#
#  Requires:
#
#   8 cores, 16GB mem recommended
#
#   mono with libgdiplus.so
#       $ mono --version
#       Mono JIT compiler version 2.10.9 (tarball-DN/85979a6 Wed May 13 08:33:35 EDT 2015)
#       Copyright (C) 2002-2011 Novell, Inc, Xamarin, Inc and Contributors. www.mono-project.com
#
#   java
#       $ java -version
#       java version "1.8.0_25"
#       Java(TM) SE Runtime Environment (build 1.8.0_25-b17)
#       Java HotSpot(TM) 64-Bit Server VM (build 25.25-b02, mixed mode)
#
#   gs (ghostscript)
#       $ gs -version
#       GPL Ghostscript 9.07 (2013-02-14)
#       Copyright (C) 2012 Artifex Software, Inc.  All rights reserved.
#
#   convert (ImageMagick)
#       $ convert -version
#       Version: ImageMagick 6.7.8-9 2014-06-10 Q16 http://www.imagemagick.org
#       Copyright: Copyright (C) 1999-2012 ImageMagick Studio LLC
#       Features: OpenMP    
#
echo STARTING UPDATE ; date
./get_aptinfo.sh
echo END GET_APTINFO.SH ; date
./get_aptplates.sh
echo END GET_APTPLATES.SH ; date
./decodeallplates.sh
echo END DECODEALLPLATES.SH ; date

./vfr_charts_download.sh
echo END VFR_CHARTS_DOWNLOAD.SH ; date
./ifr_charts_download.sh
echo END IFR_CHARTS_DOWNLOAD.SH ; date
./genallchartcsvs.sh
echo END GENALLCHARTCSVS.SH ; date
./readalltiffs.sh
echo END READALLTIFFS.SH ; date

## ./purgeoldcharts.sh

echo UPDATE COMPLETE
date

