package com.example.imagedownloader;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity {

    public static final String MESSENGER_INTENT_KEY
            = BuildConfig.APPLICATION_ID + ".MESSENGER_INTENT_KEY";
    public static final String WORK_DURATION_KEY =
            BuildConfig.APPLICATION_ID + ".WORK_DURATION_KEY";

    public static final int MSG_UPDATE_PROGRESS = 1;
    public static final int MSG_UPDATE_FAIL = 2;
    private static final int REQUEST_WRITE_STORAGE = 112;

    // Handler for incoming messages from the service.
    private IncomingMessageHandler mHandler;
    private ProgressBar progressBar;
    private TextView textView;
    private int mJobId = 0;
    private ComponentName mServiceComponent;
    EditText et;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean hasPermission = (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
        if (!hasPermission) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE);
        }

        setContentView(R.layout.activity_main);

        final Button downLoadButton  = (Button) findViewById(R.id.btnDownload);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        textView = (TextView) findViewById(R.id.textView);

        progressBar.setVisibility(View.INVISIBLE);
        textView.setVisibility(View.INVISIBLE);

        et = (EditText) findViewById(R.id.inputUrl);

        downLoadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!et.getText().toString().isEmpty()) {
                    scheduleJob(et.getText().toString());
                }
            }
        });

        mServiceComponent = new ComponentName(this, MyJobService.class);
        mHandler = new IncomingMessageHandler(this);

        //If you want to keep your activity screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Start service and provide it a way to communicate with this class.
        Intent startServiceIntent = new Intent(this, MyJobService.class);
        Messenger messengerIncoming = new Messenger(mHandler);
        startServiceIntent.putExtra(MESSENGER_INTENT_KEY, messengerIncoming);
        startService(startServiceIntent);
    }

    @Override
    protected void onStop() {
        // A service can be "started" and/or "bound". In this case, it's "started" by this Activity
        // and "bound" to the JobScheduler (also called "Scheduled" by the JobScheduler). This call
        // to stopService() won't prevent scheduled jobs to be processed. However, failing
        // to call stopService() would keep it alive indefinitely.
        stopService(new Intent(this, MyJobService.class));
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_WRITE_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //reload my activity with permission granted or use the features what required the permission
                } else {
                    Toast.makeText(this, "The app was not allowed to write to your storage." +
                            " Hence, it cannot function properly. Please consider granting" +
                            " it this permission", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    /**
     * Executed when user clicks on SCHEDULE JOB.
     */
    public void scheduleJob(String url) {
        Toast.makeText(this, "Image will be downloaded soon", Toast.LENGTH_SHORT).show();

        JobInfo.Builder builder = new JobInfo.Builder(mJobId++, mServiceComponent);

        // Extras, work duration.
        PersistableBundle extras = new PersistableBundle();

        extras.putLong(WORK_DURATION_KEY, 1000L);
        extras.putString("URL", url);

        /*if(url.contentEquals("1")) {
            extras.putString("URL", urlOne);
        } else if(url.contentEquals("2")) {
            extras.putString("URL", urlTwo);
        }*/

        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        builder.setExtras(extras);

        // Schedule job
        JobScheduler jobScheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(builder.build());
    }

    /**
     * A {@link Handler} allows you to send messages associated with a thread. A {@link Messenger}
     * uses this handler to communicate from {@link MyJobService}. It's also used to make
     * the start and stop views blink for a short period of time.
     */
    private  class IncomingMessageHandler extends Handler {

        // Prevent possible leaks with a weak reference.
        private WeakReference<MainActivity> mActivity;

        IncomingMessageHandler(MainActivity activity) {
            super(/* default looper */);
            this.mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {

            MainActivity mainActivity = mActivity.get();
            if (mainActivity == null) {
                // Activity is no longer available, exit.
                return;
            }

            switch (msg.what) {
                case MSG_UPDATE_PROGRESS:
                    int status = (int)msg.obj;
                    if(status > 0) {
                        textView.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.VISIBLE);
                    }
                    textView.setText(status+"/"+progressBar.getMax());
                    progressBar.setProgress(status);
                    break;
                case MSG_UPDATE_FAIL:
                    textView.setVisibility(View.VISIBLE);
                    textView.setText("Failed");
                    progressBar.setVisibility(View.INVISIBLE);

                    break;
            }
        }
    }
}
