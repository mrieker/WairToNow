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

package com.outerworldapps.wairtonow;

import android.database.Cursor;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Random;

/**
 * Test SQLiteDBs stuff.
 */
public class ExpView extends ScrollView implements WairToNow.CanBeMainView {
    private final static String TAG = "WairToNow";

    private TextView textView;

    private boolean enabled;
    private final Object textlock = new Object ();
    private ThreadA threada;
    private ThreadB threadb;

    public ExpView (WairToNow wtn)
    {
        super (wtn);
        textView = new TextView (wtn);
        wtn.SetTextSize (textView);
        addView (textView);
    }

    // CanBeMainView implementation
    public String GetTabName ()
    {
        return "Exp";
    }

    public void OpenDisplay ()
    {
        textView.setText ("");

        threada = new ThreadA ();
        threadb = new ThreadB ();
        enabled = true;
        threada.start ();
        threadb.start ();
    }

    public void CloseDisplay ()
    {
        enabled = false;
        try { threada.join (); } catch (Exception e) { Lib.Ignored (); }
        try { threadb.join (); } catch (Exception e) { Lib.Ignored (); }
        threada = null;
        threadb = null;
    }

    public void ReClicked ()
    {
        if (enabled) {
            CloseDisplay ();
        } else {
            OpenDisplay ();
        }
    }

    public View GetBackPage ()
    {
        return this;
    }

    private SQLiteDBs MakeDB ()
    {
        SQLiteDBs sqldb;
        synchronized (this) {
            sqldb = SQLiteDBs.create ("experiment.db");
            if (!sqldb.tableExists ("exp")) {
                sqldb.execSQL ("CREATE TABLE exp (key TEXT NOT NULL PRIMARY KEY);");
            }
        }
        return sqldb;
    }

    private void AppendTextLine (final String m)
    {
        AppendText ("\n" + m);
    }

    private void AppendText (final String m)
    {
        WairToNow.wtnHandler.runDelayed (0, new Runnable () {
            @Override
            public void run ()
            {
                synchronized (textlock) {
                    String t = textView.getText ().toString ();
                    textView.setText (t + m);
                    scrollTo (0, textView.getHeight ());
                }
            }
        });
    }

    private final static String bintohex = "0123456789ABCDEF";

    private class ThreadA extends Thread {
        @Override
        public void run ()
        {
            setName ("Exp ThreadA");
            try {
                Run ();
            } catch (Exception e) {
                enabled = false;
                AppendTextLine ("ThreadA exception: " + e.getMessage ());
                Log.e (TAG, "ThreadA exception", e);
            }
            AppendTextLine ("ThreadA exiting");
            SQLiteDBs.CloseAll ();
        }

        public void Run ()
        {
            Random random = new Random ();
            byte[] randbytes = new byte[8];
            char[] randchars = new char[randbytes.length*2];

            SQLiteDBs sqldb = MakeDB ();
            AppendTextLine ("ThreadA started");
            boolean transopen = false;
            int numintrans = 0;
            while (enabled) {
                random.nextBytes (randbytes);
                for (int i = 0; i < randbytes.length; i ++) {
                    randchars[i*2+0] = bintohex.charAt (randbytes[i] & 15);
                    randchars[i*2+1] = bintohex.charAt ((randbytes[i] / 16) & 15);
                }
                sqldb.execSQL ("INSERT OR IGNORE INTO exp (key) VALUES ('" + new String (randchars) + "')");
                numintrans ++;
                if (random.nextInt (transopen ? 512 : 16) < 2) {
                    if (transopen) {
                        sqldb.setTransactionSuccessful ();
                        sqldb.endTransaction ();
                        AppendText (numintrans + ">");
                    } else {
                        AppendText (((numintrans > 0) ? numintrans : "") + "<");
                        sqldb.beginTransaction ();
                        numintrans = 0;
                    }
                    transopen = !transopen;
                }
                //AppendText ("+");
                //int mills = random.nextInt (10) + 1;
                try { Thread.sleep (1); } catch (InterruptedException ie) { Lib.Ignored (); }
            }
            if (transopen) {
                sqldb.setTransactionSuccessful ();
                sqldb.endTransaction ();
            }
        }
    }

    private class ThreadB extends Thread {
        @Override
        public void run ()
        {
            setName ("Exp ThreadB");
            try {
                Run ();
            } catch (Exception e) {
                enabled = false;
                AppendTextLine ("ThreadB exception: " + e.getMessage ());
                Log.e (TAG, "ThreadB exception", e);
            }
            AppendTextLine ("ThreadB exiting");
            SQLiteDBs.CloseAll ();
        }

        public void Run ()
        {
            Random random = new Random ();

            String[] columns_key = new String[] { "key" };

            SQLiteDBs sqldb = MakeDB ();
            AppendTextLine ("ThreadB started");
            while (enabled) {
                Cursor result;
                result = sqldb.query ("exp", columns_key, null, null, null, null, null, "3");
                try {
                    if (result.moveToFirst ()) {
                        do {
                            String key = result.getString (0);
                            sqldb.execSQL ("DELETE FROM exp WHERE key='" + key + "'");
                            //AppendText ("-");
                        } while (result.moveToNext ());
                    }
                } finally {
                    result.close ();
                }
                int mills = random.nextInt (4) + 1;
                try { Thread.sleep (mills); } catch (InterruptedException ie) { Lib.Ignored (); }
            }
            SQLiteDBs.CloseAll ();
        }
    }
}
