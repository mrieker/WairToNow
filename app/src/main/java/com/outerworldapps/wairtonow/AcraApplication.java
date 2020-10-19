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

import android.app.Application;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.acra.collector.CrashReportData;
import org.acra.config.ACRAConfiguration;
import org.acra.config.ConfigurationBuilder;
import org.acra.sender.ReportSender;
import org.acra.sender.ReportSenderException;
import org.acra.sender.ReportSenderFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPOutputStream;

/**
 * Captures any crashes and forwards the stack dump info to the web server
 * when the web server is available.
 */

// https://github.com/ACRA/acra/wiki/BasicSetup
// https://github.com/ACRA/acra/wiki/AdvancedUsage#reports-content
@ReportsCrashes (
        reportSenderFactoryClasses = { AcraApplication.YourOwnSenderFactory.class },
        mode = ReportingInteractionMode.TOAST,
        resToastText = R.string.crash_toast_text
)
public class AcraApplication extends Application {
    @Override
    protected void attachBaseContext(Context base)
    {
        super.attachBaseContext(base);

        // The following line triggers the initialization of ACRA
        ACRA.init (this, new ConfigurationBuilder (this).build (), false);
    }

    /**
     * The webserver is accessible, try to send any pending ACRA report files.
     */
    public static void sendReports (Context context)
    {
        /*
         * Loop through all files in the data directory.
         */
        File dir = context.getFilesDir ();
        File[] filelist = dir.listFiles ();
        assert filelist != null;
        for (File file : filelist) {

            /*
             * Delete any files ending in .acra.gz.tmp cuz they weren't finished.
             */
            if (file.getPath ().endsWith (".acra.gz.tmp")) {
                Lib.Ignored (file.delete ());
                continue;
            }

            /*
             * Send and delete any files ending in .acra.gz to acraupload.php script.
             */
            if (file.getPath ().endsWith (".acra.gz")) {
                long len = file.length ();
                try {
                    URL url = new URL (MaintView.dldir + "/acraupload.php");
                    HttpURLConnection httpCon = (HttpURLConnection)url.openConnection ();
                    try {
                        httpCon.setRequestMethod ("POST");
                        httpCon.setDoOutput (true);
                        httpCon.setFixedLengthStreamingMode ((int) len);
                        InputStream is = new FileInputStream (file);
                        OutputStream os = httpCon.getOutputStream ();
                        byte[] buf = new byte[4096];
                        int rc;
                        while ((rc = is.read (buf)) > 0) {
                            os.write (buf, 0, rc);
                        }
                        is.close ();
                        rc = httpCon.getResponseCode ();
                        if (rc != HttpURLConnection.HTTP_OK) {
                            throw new IOException ("http response code " + rc);
                        }
                        Log.i ("WairToNow", "sent ACRA report " + file.getPath ());
                        Lib.Ignored (file.delete ());
                    } finally {
                        httpCon.disconnect ();
                    }
                } catch (Exception e) {
                    Log.e ("WairToNow", "exception sending ACRA report " + file.getPath (), e);
                }
            }
        }
    }

    // https://github.com/ACRA/acra/wiki/Report-Destinations

    public static class YourOwnSender implements ReportSender {
        @Override
        public void send (@NonNull Context context, @NonNull CrashReportData report)
                throws ReportSenderException {
            try {
                long nowms = System.currentTimeMillis ();
                File dir   = context.getFilesDir ();
                File perm  = new File (dir, nowms + ".acra.gz");
                File temp  = new File (dir, nowms + ".acra.gz.tmp");
                PrintWriter pw = new PrintWriter (new GZIPOutputStream (new FileOutputStream (temp)));
                try {
                    pw.println (nowms);
                    for (ReportField reportField : report.keySet ()) {
                        String reportValue = report.get (reportField);
                        pw.println ("::" + reportField.toString () + "::" + reportValue);
                    }
                } finally {
                    pw.close ();
                }
                if (!temp.renameTo (perm)) {
                    throw new IOException ("error renaming " + temp.getPath () + " to " + perm.getPath ());
                }
                Log.i ("WairToNow", "created ACRA report " + perm.getPath ());
                sendReports (context);
            } catch (Exception e) {
                throw new ReportSenderException ("error creating ACRA file", e);
            }
        }
    }

    public static class YourOwnSenderFactory implements ReportSenderFactory {
        @NonNull
        public ReportSender create (@NonNull Context context, @NonNull ACRAConfiguration config)
        {
            return new YourOwnSender ();
        }
    }
}
