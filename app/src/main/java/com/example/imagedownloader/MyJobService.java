/*
 * Copyright 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.imagedownloader;

import android.Manifest;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import static com.example.imagedownloader.MainActivity.MESSENGER_INTENT_KEY;
import static com.example.imagedownloader.MainActivity.MSG_UPDATE_FAIL;
import static com.example.imagedownloader.MainActivity.MSG_UPDATE_PROGRESS;

/**
 * Service to handle callbacks from the JobScheduler. Requests scheduled with the JobScheduler
 * ultimately land on this service's "onStartJob" method. It runs jobs for a specific amount of time
 * and finishes them. It keeps the activity updated with changes via a Messenger.
 */
public class MyJobService extends JobService {

    private static final String TAG = MyJobService.class.getSimpleName();

    private static final int MSG_WHAT_DOWNLOAD = 1;
    private static final int MSG_WHAT_CHECKSTATUS = 2;

    private static final long DELAY_TO_UPDATE =  2* 1000;

    private Messenger mActivityMessenger;
    Handler mImgDownloadHandler, mStatusHandler;

    private int mDownloadProgress = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service created");

        HandlerThread imgDLhandlerThread = new HandlerThread("ImageDownloaHandlerThread");
        imgDLhandlerThread.start();
        mImgDownloadHandler = new Handler(imgDLhandlerThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                if(message.what == MSG_WHAT_DOWNLOAD) {
                    JobParameters params = (JobParameters) message.obj;
                    String urlSting = params.getExtras().getString("URL");
                    downLoadImage(urlSting, params);
                }
                return false;
            }
        });

        HandlerThread statusThread = new HandlerThread("statusThread");
        statusThread.start();
        mStatusHandler = new Handler(statusThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                if(message.what == MSG_WHAT_CHECKSTATUS) {
                    Log.i(TAG," MSG_WHAT_CHECKSTATUS :"+ mDownloadProgress);
                    if(mDownloadProgress > 0) {
                        sendMessageTOUI(MSG_UPDATE_PROGRESS, mDownloadProgress);
                    }

                    if (mDownloadProgress < 100) {
                        Message m = Message.obtain();
                        m.obj = message.obj;
                        m.what = MSG_WHAT_CHECKSTATUS;
                        mStatusHandler.sendMessageDelayed(m, DELAY_TO_UPDATE);
                    } else {
                        //stop the job
                        Log.d(TAG,"Stop the job in mStatusHandler");
                        JobParameters params = (JobParameters) message.obj;
                        Log.d(TAG," job id is :"+params.getJobId());
                        jobFinished((JobParameters)message.obj, false);
                    }
                }
                return false;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Service destroyed");
    }

    /**
     * When the app's MainActivity is created, it starts this service. This is so that the
     * activity and this service can communicate back and forth.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mActivityMessenger = intent.getParcelableExtra(MESSENGER_INTENT_KEY);
        return START_NOT_STICKY;
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        // The work that this service "does" is simply wait for a certain duration and finish
        // the job (on another thread).

        Message m = Message.obtain();
        m.what = MSG_WHAT_DOWNLOAD;
        m.obj = params;
        mImgDownloadHandler.sendMessage(m);

        Log.i(TAG, "on start job: " + params.getJobId());

        // Return true as there's more work to be done with this job.
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // Stop tracking these job parameters, as we've 'finished' executing.
        Log.i(TAG, "on stop job: " + params.getJobId());
        // Return false to drop the job.
        return false;
    }

    private void sendMessageTOUI(int messageID, @Nullable Object params) {
        // If this service is launched by the JobScheduler, there's no callback Messenger. It
        // only exists when the MainActivity calls startService() with the callback in the Intent.
        if (mActivityMessenger == null) {
            Log.d(TAG, "Service is bound, not started. There's no callback to send a message to.");
            return;
        }
        Message m = Message.obtain();
        m.what = messageID;
        m.obj = params;
        try {
            mActivityMessenger.send(m);
        } catch (RemoteException e) {
            Log.e(TAG, "Error passing service object back to activity.");
        }
    }

    private void downLoadImage(String urlString, JobParameters params) {
        Log.d(TAG," downLoadImage :"+urlString);

        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpsURLConnection.HTTP_OK) {
                mDownloadProgress = 0;
                Message sMessage = Message.obtain();
                sMessage.obj = params;
                sMessage.what = MSG_WHAT_CHECKSTATUS;
                mStatusHandler.sendMessageDelayed(sMessage, DELAY_TO_UPDATE);

                FileOutputStream fileStream = null;
                ByteArrayOutputStream baoStream = new ByteArrayOutputStream();
                boolean hasPermission = (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
                if (hasPermission) {
                    String filePath = Environment.getExternalStorageDirectory().getPath()+"/"+getPackageName()+"/";

                    File dir = new File(filePath);

                    if(dir.exists() || dir.mkdirs()) {
                        //Assuming the image is jpeg extention
                        String fileName = String.valueOf(String.format(
                                Environment.getExternalStorageDirectory().getPath() +
                                        "/" + getPackageName() + "/" + "%d.jpg",
                                System.currentTimeMillis()));
                        fileStream = new FileOutputStream(fileName);
                    }
                }

                InputStream input = connection.getInputStream();
                int fileLength = connection.getContentLength();
                byte data[] = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        mDownloadProgress = (int) (total * 100 / fileLength);
                    }
                    if(fileStream != null) {
                        fileStream.write(data, 0, count);
                    } else {
                        baoStream.write(data, 0, count);
                    }
                }
                if(fileStream != null) {
                    fileStream.close();
                } else {
                    baoStream.close();
                }

                mStatusHandler.removeMessages(MSG_WHAT_CHECKSTATUS);
                sendMessageTOUI(MSG_UPDATE_PROGRESS, mDownloadProgress);
                connection.disconnect();
            } else {
                sendMessageTOUI(MSG_UPDATE_FAIL, 0);
            }
            //stop the job
            Log.d(TAG, " Stop the job,id is :" + params.getJobId());
            jobFinished(params, false);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
