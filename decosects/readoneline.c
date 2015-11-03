//    Copyright (C) 2015, Mike Rieker, Beverly, MA USA
//    www.outerworldapps.com
//
//    This program is free software; you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation; version 2 of the License.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    EXPECT it to FAIL when someone's HeALTh or PROpeRTy is at RISk.
//
//    You should have received a copy of the GNU General Public License
//    along with this program; if not, write to the Free Software
//    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//    http://www.gnu.org/licenses/gpl-2.0.html


// cc -O2 -Wall -o readoneline readoneline.c

#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/file.h>
#include <unistd.h>

int main (int argc, char **argv)
{
    char c;
    int fd, rc;

    fd = 0; // stdin
    if (argc != 1) {
        if (argc != 2) abort ();
        fd = open (argv[1], O_RDONLY);
        if (fd < 0) abort ();
    }
    if (flock (fd, LOCK_EX) < 0) abort ();
    do {
        rc = read (fd, &c, 1);
        if (rc < 0) abort ();
        if (rc == 0) break;
        putchar (c);
    } while (c != '\n');
    flock (fd, LOCK_UN);
    return 0;
}
