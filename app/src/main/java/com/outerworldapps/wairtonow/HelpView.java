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


import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.View;
import android.webkit.WebView;

/**
 * Display help page in a web view.
 * Allows internal links to other resource pages
 * and allows external links to web pages.
 */
@SuppressLint("ViewConstructor")
public class HelpView extends WebView
        implements WairToNow.CanBeMainView {
    private WairToNow wairToNow;

    @SuppressLint({ "AddJavascriptInterface", "SetJavaScriptEnabled" })
    public HelpView (WairToNow wtn)
    {
        super (wtn);
        wairToNow = wtn;

        getSettings ().setBuiltInZoomControls (true);
        getSettings ().setJavaScriptEnabled (true);
        getSettings ().setDefaultFontSize (Math.round (wairToNow.textSize / 2.0F));
        getSettings ().setDefaultFixedFontSize (Math.round (wairToNow.textSize / 2.0F));
        getSettings ().setSupportZoom (true);
        addJavascriptInterface (new JavaScriptObject (), "hvjso");

        ReClicked ();
    }

    // implement WairToNow.CanBeMainView
    public String GetTabName ()
    {
        return "Help";
    }
    public void OpenDisplay () { }
    public void CloseDisplay () { }
    public void ReClicked ()
    {
        loadHtmlAsset ("help.html");
    }
    public View GetBackPage ()
    {
        goBack ();
        return this;
    }

    @Override  // CanBeMainView
    public int GetOrientation ()
    {
        return ActivityInfo.SCREEN_ORIENTATION_USER;
    }

    @Override  // CanBeMainView
    public boolean IsPowerLocked ()
    {
        return false;
    }

    /**
     * Display the named HTML asset in the view.
     */
    public void loadHtmlAsset (String assetName)
    {
        loadUrl ("file:///android_asset/" + assetName);
    }

    /**
     * Accessed via javascript in the internal .HTML files.
     */
    private class JavaScriptObject {

        @SuppressWarnings ("unused")
        public String getVersionName ()
        {
            try {
                PackageInfo pInfo = wairToNow.getPackageManager ().getPackageInfo (wairToNow.getPackageName (), 0);
                return pInfo.versionName;
            } catch (PackageManager.NameNotFoundException nnfe) {
                return "";
            }
        }

        @SuppressWarnings ("unused")
        public int getVersionCode ()
        {
            try {
                PackageInfo pInfo = wairToNow.getPackageManager ().getPackageInfo (wairToNow.getPackageName (), 0);
                return pInfo.versionCode;
            } catch (PackageManager.NameNotFoundException nnfe) {
                return -1;
            }
        }

        @SuppressWarnings ("unused")
        public String getGithubLink ()
        {
            String fullhash = BuildConfig.GitHash;
            String abbrhash = fullhash.substring (0, 7);
            String status = BuildConfig.GitStatus;
            String[] lines = status.split ("\n");
            for (String line : lines) {
                line = line.trim ();
                if (line.startsWith ("On branch ")) {
                    if (line.equals ("On branch github")) {
                        String link = "https://github.com/mrieker/WairToNow/commit/" + fullhash;
                        return "<A HREF=\"" + link + "\">" + abbrhash + "</A>";
                    }
                    break;
                }
            }
            return abbrhash;
        }

        @SuppressWarnings ("unused")
        public boolean getGitDirtyFlag ()
        {
            String status = BuildConfig.GitStatus;
            String[] lines = status.split ("\n");
            for (String line : lines) {
                if (line.contains ("modified:") && !line.contains ("app.iml")) {
                    return true;
                }
            }
            return false;
        }
    }
}
