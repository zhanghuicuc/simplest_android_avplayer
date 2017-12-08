package com.example.zhanghui.avplayer;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MediaCodecPlayer implements MediaTimeProvider {
    private static final String TAG = MediaCodecPlayer.class.getSimpleName();

    private static final int STATE_IDLE = 1;
    private static final int STATE_PREPARING = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 4;

    private Boolean mThreadStarted = false;
    private CodecState mAudioTrackState;
    private int mMediaFormatHeight;
    private int mMediaFormatWidth;
    private Integer mState;
    private long mDeltaTimeUs;
    private long mDurationUs;
    private Map<Integer, CodecState> mAudioCodecStates;
    private Map<Integer, CodecState> mVideoCodecStates;
    private Map<String, String> mAudioHeaders;
    private Map<String, String> mVideoHeaders;
    private MediaExtractor mAudioExtractor;
    private MediaExtractor mVideoExtractor;
    private SurfaceHolder mSurfaceHolder;
    private Thread mThread;
    private Uri mAudioUri;
    private Uri mVideoUri;
    private VideoFrameReleaseTimeHelper mFrameReleaseTimeHelper;

    /*
     * Media player class to playback video using MediaCodec.
     */
    public MediaCodecPlayer(SurfaceHolder holder, Context context) {
        mSurfaceHolder = holder;
        mFrameReleaseTimeHelper = new VideoFrameReleaseTimeHelper(context);
        mAudioTrackState = null;
        mState = STATE_IDLE;
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    synchronized (mThreadStarted) {
                        if (mThreadStarted == false) {
                            break;
                        }
                    }
                    synchronized (mState) {
                        if (mState == STATE_PLAYING) {
                            doSomeWork();
                            if (mAudioTrackState != null) {
                                mAudioTrackState.process();
                            }
                        }
                    }
                    try {
                        Thread.sleep(5);//5ms loop
                    } catch (InterruptedException ex) {
                        Log.d(TAG, "Thread interrupted");
                    }
                }
            }
        });
    }

    public void setAudioDataSource(Uri uri, Map<String, String> headers) {
        mAudioUri = uri;
        mAudioHeaders = headers;
    }

    public void setVideoDataSource(Uri uri, Map<String, String> headers) {
        mVideoUri = uri;
        mVideoHeaders = headers;
    }

    private boolean prepareAudio() throws IOException {
        for (int i = mAudioExtractor.getTrackCount(); i-- > 0;) {
            MediaFormat format = mAudioExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);

            if (!mime.startsWith("audio/")) {
                continue;
            }

            Log.d(TAG, "audio track #" + i + " " + format + " " + mime +
                  " Is ADTS:" + getMediaFormatInteger(format, MediaFormat.KEY_IS_ADTS) +
                  " Sample rate:" + getMediaFormatInteger(format, MediaFormat.KEY_SAMPLE_RATE) +
                  " Channel count:" +
                  getMediaFormatInteger(format, MediaFormat.KEY_CHANNEL_COUNT));

            mAudioExtractor.selectTrack(i);
            if (!addTrack(i, format)) {
                Log.e(TAG, "prepareAudio - addTrack() failed!");
                return false;
            }

            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                long durationUs = format.getLong(MediaFormat.KEY_DURATION);

                if (durationUs > mDurationUs) {
                    mDurationUs = durationUs;
                }
                Log.d(TAG, "audio track format #" + i +
                        " Duration:" + mDurationUs + " microseconds");
            }
        }
        return true;
    }

    private boolean prepareVideo() throws IOException {
        for (int i = mVideoExtractor.getTrackCount(); i-- > 0;) {
            MediaFormat format = mVideoExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);

            if (!mime.startsWith("video")) {
                continue;
            }

            mMediaFormatHeight = getMediaFormatInteger(format, MediaFormat.KEY_HEIGHT);
            mMediaFormatWidth = getMediaFormatInteger(format, MediaFormat.KEY_WIDTH);
            Log.d(TAG, "video track #" + i + " " + format + " " + mime +
                  " Width:" + mMediaFormatWidth + ", Height:" + mMediaFormatHeight);

            mVideoExtractor.selectTrack(i);
            if (!addTrack(i, format)) {
                Log.e(TAG, "prepareVideo - addTrack() failed!");
                return false;
            }

            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                long durationUs = format.getLong(MediaFormat.KEY_DURATION);

                if (durationUs > mDurationUs) {
                    mDurationUs = durationUs;
                }
                Log.d(TAG, "track format #" + i + " Duration:" +
                        mDurationUs + " microseconds");
            }
        }
        return true;
    }

    public boolean prepare() throws IOException {
        if (null == mAudioExtractor) {
            mAudioExtractor = new MediaExtractor();
            if (null == mAudioExtractor) {
                Log.e(TAG, "prepare - Cannot create Audio extractor.");
                return false;
            }
        }

        if (null == mVideoExtractor){
            mVideoExtractor = new MediaExtractor();
            if (null == mVideoExtractor) {
                Log.e(TAG, "prepare - Cannot create Video extractor.");
                return false;
            }
        }

        mAudioExtractor.setDataSource(mAudioUri.toString(), mAudioHeaders);
        mVideoExtractor.setDataSource(mVideoUri.toString(), mVideoHeaders);

        if (null == mVideoCodecStates) {
            mVideoCodecStates = new HashMap<Integer, CodecState>();
        } else {
            mVideoCodecStates.clear();
        }

        if (null == mAudioCodecStates) {
            mAudioCodecStates = new HashMap<Integer, CodecState>();
        } else {
            mAudioCodecStates.clear();
        }

        if (!prepareAudio()) {
            Log.e(TAG,"prepare - prepareAudio() failed!");
            return false;
        }
        if (!prepareVideo()) {
            Log.e(TAG,"prepare - prepareVideo() failed!");
            return false;
        }

        synchronized (mState) {
            mState = STATE_PAUSED;
        }
        return true;
    }

    private boolean addTrack(int trackIndex, MediaFormat format) throws IOException {
        String mime = format.getString(MediaFormat.KEY_MIME);
        boolean isVideo = mime.startsWith("video/");
        boolean isAudio = mime.startsWith("audio/");
        MediaCodec codec;

        codec = MediaCodec.createDecoderByType(mime);
        if (codec == null) {
            Log.e(TAG, "addTrack - Could not create regular playback codec for mime "+
                    mime+"!");
            return false;
        }
        codec.configure(
                format,
                isVideo ? mSurfaceHolder.getSurface() : null, null, 0);

        CodecState state;
        if (isVideo) {
            state = new CodecState((MediaTimeProvider)this, mVideoExtractor,
                            trackIndex, format, codec, true);
            mVideoCodecStates.put(Integer.valueOf(trackIndex), state);
        } else {
            state = new CodecState((MediaTimeProvider)this, mAudioExtractor,
                            trackIndex, format, codec, true);
            mAudioCodecStates.put(Integer.valueOf(trackIndex), state);
        }

        if (isAudio) {
            mAudioTrackState = state;
        }

        return true;
    }

    protected int getMediaFormatInteger(MediaFormat format, String key) {
        return format.containsKey(key) ? format.getInteger(key) : 0;
    }

    public boolean start() {
        Log.d(TAG, "start");

        synchronized (mState) {
            if (mState == STATE_PLAYING || mState == STATE_PREPARING) {
                return true;
            } else if (mState == STATE_IDLE) {
                mState = STATE_PREPARING;
                return true;
            } else if (mState != STATE_PAUSED) {
                throw new IllegalStateException();
            }

            for (CodecState state : mVideoCodecStates.values()) {
                state.start();
            }

            for (CodecState state : mAudioCodecStates.values()) {
                state.start();
            }

            mDeltaTimeUs = -1;
            mState = STATE_PLAYING;
        }
        return false;
    }

    public void startThread() {
        mFrameReleaseTimeHelper.enable();
        start();
        synchronized (mThreadStarted) {
            mThreadStarted = true;
            mThread.start();
        }
    }

    public void pause() {
        Log.d(TAG, "pause");

        synchronized (mState) {
            if (mState == STATE_PAUSED) {
                return;
            } else if (mState != STATE_PLAYING) {
                throw new IllegalStateException();
            }

            for (CodecState state : mVideoCodecStates.values()) {
                state.pause();
            }

            for (CodecState state : mAudioCodecStates.values()) {
                state.pause();
            }

            mState = STATE_PAUSED;
        }
    }

    public void flush() {
        Log.d(TAG, "flush");

        synchronized (mState) {
            if (mState == STATE_PLAYING || mState == STATE_PREPARING) {
                return;
            }

            for (CodecState state : mAudioCodecStates.values()) {
                state.flush();
            }

            for (CodecState state : mVideoCodecStates.values()) {
                state.flush();
            }
        }
    }

    public void reset() {
        synchronized (mState) {
            if (mState == STATE_PLAYING) {
                pause();
            }
            if (mVideoCodecStates != null) {
                for (CodecState state : mVideoCodecStates.values()) {
                    state.release();
                }
                mVideoCodecStates = null;
            }

            if (mAudioCodecStates != null) {
                for (CodecState state : mAudioCodecStates.values()) {
                    state.release();
                }
                mAudioCodecStates = null;
            }

            if (mAudioExtractor != null) {
                mAudioExtractor.release();
                mAudioExtractor = null;
            }

            if (mVideoExtractor != null) {
                mVideoExtractor.release();
                mVideoExtractor = null;
            }

            if (mFrameReleaseTimeHelper != null) {
                mFrameReleaseTimeHelper.disable();
                mFrameReleaseTimeHelper = null;
            }

            mDurationUs = -1;
            mState = STATE_IDLE;
        }
        synchronized (mThreadStarted) {
            mThreadStarted = false;
        }
        try {
            mThread.join();
        } catch (InterruptedException ex) {
            Log.d(TAG, "mThread.join " + ex);
        }
    }

    public boolean isEnded() {
        for (CodecState state : mVideoCodecStates.values()) {
          if (!state.isEnded()) {
            return false;
          }
        }

        for (CodecState state : mAudioCodecStates.values()) {
            if (!state.isEnded()) {
              return false;
            }
        }

        return true;
    }

    private void doSomeWork() {
        try {
            for (CodecState state : mVideoCodecStates.values()) {
                state.doSomeWork();
            }
        } catch (IllegalStateException e) {
            throw new Error("Video CodecState.doSomeWork" + e);
        }

        try {
            for (CodecState state : mAudioCodecStates.values()) {
                state.doSomeWork();
            }
        } catch (IllegalStateException e) {
            throw new Error("Audio CodecState.doSomeWork" + e);
        }

    }

    public long getNowUs() {
        //返回audio播放的时间
        if (mAudioTrackState == null) {
            return System.currentTimeMillis() * 1000;
        }

        return mAudioTrackState.getAudioTimeUs();
    }

    public long getRealTimeUsForMediaTime(long mediaTimeUs) {
        if (mDeltaTimeUs == -1) {
            long nowUs = getNowUs();
            mDeltaTimeUs = nowUs - mediaTimeUs;
        }
        long earlyUs = mDeltaTimeUs + mediaTimeUs - getNowUs();
        long unadjustedFrameReleaseTimeNs = System.nanoTime() + (earlyUs * 1000);
        long adjustedReleaseTimeNs = mFrameReleaseTimeHelper.adjustReleaseTime(
                mDeltaTimeUs + mediaTimeUs, unadjustedFrameReleaseTimeNs);
        return adjustedReleaseTimeNs / 1000;
    }

    public long getVsyncDurationNs() {
        if (mFrameReleaseTimeHelper != null) {
            return mFrameReleaseTimeHelper.getVsyncDurationNs();
        } else {
            return -1;
        }
    }

    public int getDuration() {
        return (int)((mDurationUs + 500) / 1000);
    }

    public int getCurrentPosition() {
        if (mVideoCodecStates == null) {
                return 0;
        }

        long positionUs = 0;

        for (CodecState state : mVideoCodecStates.values()) {
            long trackPositionUs = state.getCurrentPositionUs();

            if (trackPositionUs > positionUs) {
                positionUs = trackPositionUs;
            }
        }
        return (int)((positionUs + 500) / 1000);
    }

}
