package com.example.zhanghui.avplayer;

import android.app.Activity;
import android.content.Intent;
import android.view.KeyEvent;
import android.widget.MediaController;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class PlayerActivity extends Activity implements SurfaceHolder.Callback{

    private static final String TAG = "PlayerActivity";
    private MediaCodecPlayer mMediaCodecPlayer;
    private SurfaceView mSurfaceV;
    private SurfaceHolder mSurfaceHolder;
    private MediaController mediaController;
    private Uri mFileUrl;
    private static final int SLEEP_TIME_MS = 1000;
    private static final long PLAY_TIME_MS = TimeUnit.MILLISECONDS.convert(4, TimeUnit.MINUTES);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_player);
        mSurfaceV = (SurfaceView) findViewById(R.id.surfaceView);
        mSurfaceV.getHolder().addCallback(this);
        View root = findViewById(R.id.root);
        mediaController = new MediaController(this);
        mediaController.setAnchorView(root);
        root.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                return mediaController.dispatchKeyEvent(event);
            }
        });
        Intent intent = getIntent();
        mFileUrl = intent.getData();
        Intent i = new Intent("com.android.music.musicservicecommand");
        i.putExtra("command", "pause");
        this.sendBroadcast(i);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mMediaCodecPlayer != null) {
            mMediaCodecPlayer.reset();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mMediaCodecPlayer != null) {
            mMediaCodecPlayer.reset();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        this.mSurfaceHolder = holder;
        mSurfaceHolder.setKeepScreenOn(true);
        new DecodeTask().execute();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                          int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mMediaCodecPlayer != null) {
            mMediaCodecPlayer.reset();
        }
    }

    public class DecodeTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... params) {
            //this runs on a new thread
            initializePlayer();
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            //this runs on ui thread
        }
    }

    private void initializePlayer() {
        mMediaCodecPlayer = new MediaCodecPlayer(mSurfaceHolder, getApplicationContext());

        mMediaCodecPlayer.setAudioDataSource(mFileUrl, null);
        mMediaCodecPlayer.setVideoDataSource(mFileUrl, null);
        mMediaCodecPlayer.start(); //from IDLE to PREPARING
        try {
            mMediaCodecPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // starts video playback
        mMediaCodecPlayer.startThread();

        long timeOut = System.currentTimeMillis() + 4*PLAY_TIME_MS;
        while (timeOut > System.currentTimeMillis() && !mMediaCodecPlayer.isEnded()) {
            try {
                Thread.sleep(SLEEP_TIME_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (mMediaCodecPlayer.getCurrentPosition() >= mMediaCodecPlayer.getDuration() ) {
                Log.d(TAG, "testVideoPlayback -- current pos = " +
                        mMediaCodecPlayer.getCurrentPosition() +
                        ">= duration = " + mMediaCodecPlayer.getDuration());
                break;
            }
        }

        if (timeOut > System.currentTimeMillis()) {
            Log.e(TAG, "video playback timeout exceeded!");
            return;
        }

        Log.d(TAG, "playVideo player.reset()");
        mMediaCodecPlayer.reset();
    }
}
