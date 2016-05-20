package com.Webtrekk.SDKTest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.Suppress;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.webtrekk.webtrekksdk.Webtrekk;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

/**
 * Created by vartbaronov on 01.04.16.
 */
public class AttributionTest extends ActivityInstrumentationTestCase2BaseMain<MainActivity> {

    volatile String mAdvID;
    private Context mContext;
    volatile boolean mNotifierDone;
    final Object mWaiter = new Object();
    final String MEDIA_CODE = "MEDIA_CODE";


    public AttributionTest(){
        super(MainActivity.class);
        }


    @Override
    protected void setUp()throws Exception{
        super.setUp();
    }

    public void testAttributionRunLinkWithAdID()
    {
        launchClickID("http://appinstall.webtrekk.net/appinstall/v1/redirect?mc="+MEDIA_CODE+"&trackid=&as1=market%3A//details%3Fid%3Dcom.Webtrekk.SDKTest&aid=", true);
    }


    public void testAttributionRunLinkWithoutAdID()
    {
        launchClickID("http://appinstall.webtrekk.net/appinstall/v1/redirect?mc="+MEDIA_CODE+"&trackid=&as1=market%3A//details%3Fid%3Dcom.Webtrekk.SDKTest", false);
    }

    private String[] getFileList(final String contains)
    {
        return getInstrumentation().getTargetContext().getFilesDir().list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.contains(contains);
            }
        });

    }

    private String getFileNameOnly(String fullName)
    {
        return fullName.substring(0, fullName.indexOf("."));
    }

    private void launchClickID(String url, boolean useAdvID)
    {
        if (!mIsExternalCall)
            return;

        getActivity();
        String trackID = Webtrekk.getInstance().getTrackId();
        url = url.replace("&trackid=", "&trackid="+trackID);

        if (useAdvID) {
            String[] fileList = getFileList(".adv");

            assertTrue(fileList.length == 1);


            String advID = getFileNameOnly(fileList[0]);

            assertNotNull(advID);
            url = url.replace("&aid=", "&aid="+advID);
        }

        final String URLFinal = url;


        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

                WebView webView = (WebView) getActivity().findViewById(R.id.main_web_view);
                webView.setVisibility(View.VISIBLE);


                webView.setWebViewClient(new WebViewClient() {

                                             @Override
                                             public boolean shouldOverrideUrlLoading(WebView view, String url) {
                                                 URLParsel parcel = new URLParsel();

                                                 parcel.parseURL(url);
                                                 String clickID = parcel.getValue("referrer").split("%3D")[1];
                                                 File file = new File(getActivity().getFilesDir(), clickID + ".clk");

                                                 try {
                                                     file.createNewFile();
                                                 } catch (IOException e) {
                                                     e.printStackTrace();
                                                 }

                                                 synchronized (mWaiter) {
                                                     mNotifierDone = true;
                                                     mWaiter.notifyAll();
                                                 }
                                                 return true;
                                             }
                                         }
                );

                webView.loadUrl(URLFinal);
            }
        });


        try {
            synchronized (mWaiter) {
                while (!mNotifierDone)
                   mWaiter.wait();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        finishActivitySync(getActivity());
        setActivity(null);
    }

    public void testAdID()
    {
        if (!mIsExternalCall)
            return;
        Object notifier = new Object();
        mContext = getInstrumentation().getTargetContext();

        new Thread(new AdvIDReader(notifier)).start();

        synchronized (notifier)
        {
            try {
                while (!mNotifierDone)
                   notifier.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        assertNotNull(mAdvID);


        File file = new File(getInstrumentation().getTargetContext().getFilesDir(), mAdvID+".adv");

        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.d(getClass().getName(), "Create advID file:" + file.getAbsolutePath());
    }

    class AdvIDReader implements Runnable
    {
        private final Object mNotifier;

        public AdvIDReader(Object notifier)
        {
            mNotifier = notifier;
        }

        @Override
        public void run() {

            AdvertisingIdClient.Info adInfo = null;
            //get adv ID

            try {
                adInfo = AdvertisingIdClient.getAdvertisingIdInfo(mContext);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (GooglePlayServicesNotAvailableException e) {
                e.printStackTrace();
            } catch (GooglePlayServicesRepairableException e) {
                e.printStackTrace();
            }

            mAdvID = adInfo.getId();

            synchronized (mNotifier) {
                mNotifierDone = true;
                mNotifier.notifyAll();
            }

        }
    }

    public void testFirstStart()
    {
        if (!mIsExternalCall)
            return;

        Webtrekk.getInstance().initWebtrekk(mApplication);

        LocalBroadcastManager.getInstance(getInstrumentation().getTargetContext()).registerReceiver(mSDKReceiver,
                new IntentFilter("com.Webtrekk.CampainMediaMessage"));

        synchronized (mWaiter) {
            while (!mNotifierDone)
                try {
                    mWaiter.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
        }
        LocalBroadcastManager.getInstance(getInstrumentation().getTargetContext()).unregisterReceiver(mSDKReceiver);
    }

    private BroadcastReceiver mSDKReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            String mediaCode = intent.getStringExtra("INSTALL_SETTINGS_MEDIA_CODE");
            String advID = intent.getStringExtra("INSTALL_SETTINGS_ADV_ID");

            Log.d(getClass().getName(), "Broad cast message from SDK is received");

            String[] advFileList = getFileList(".adv");
            if (advFileList.length == 1)
            {
                assertEquals(advID, getFileNameOnly(advFileList[0]));
            }

            assertEquals(mediaCode, MEDIA_CODE);

            mNotifierDone = true;

            synchronized (mWaiter)
            {
                mWaiter.notifyAll();
            }
        };
    };

}
