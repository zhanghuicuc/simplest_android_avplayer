package com.example.zhanghui.avplayer;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.media.AudioAttributes;
import android.util.Log;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * Class for playing audio by using audio track.
 * audioTrack.write methods will
 * block until all data has been written to system. In order to avoid blocking, this class
 * caculates available buffer size first then writes to audio sink.
 */
public class NonBlockingAudioTrack {
    private static final String TAG = NonBlockingAudioTrack.class.getSimpleName();
    private static final int MIN_TIMESTAMP_SAMPLE_INTERVAL_US = 250000;

    class QueueElement {
        ByteBuffer data;
        int size;
        long pts;
    }

    private AudioTrack mAudioTrack;
    private int mSampleRate;
    private int mNumBytesQueued = 0;
    private LinkedList<QueueElement> mQueue = new LinkedList<QueueElement>();
    private boolean mStopped;
    private Method getLatencyMethod;
    private long mLatencyUs;
    private long mLastTimestampSampleTimeUs;
    private boolean mAudioTimestampSet;
    private final AudioTimestamp mAudioTimestamp;

    public NonBlockingAudioTrack(int sampleRate, int channelCount) {
        int channelConfig;
        switch (channelCount) {
            case 1:
                channelConfig = AudioFormat.CHANNEL_OUT_MONO;
                break;
            case 2:
                channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
                break;
            case 6:
                channelConfig = AudioFormat.CHANNEL_OUT_5POINT1;
                break;
            default:
                throw new IllegalArgumentException();
        }

        int minBufferSize =
            AudioTrack.getMinBufferSize(
                    sampleRate,
                    channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT);

        int bufferSize = 2 * minBufferSize;

        mAudioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM);

        mSampleRate = sampleRate;

        try {
            getLatencyMethod =
                    android.media.AudioTrack.class.getMethod("getLatency", (Class<?>[]) null);
        } catch (NoSuchMethodException e) {}
        mLatencyUs = 0;
        mLastTimestampSampleTimeUs = 0;
        mAudioTimestamp = new AudioTimestamp();
    }

    public long getAudioTimeUs() {
        long systemClockUs = System.nanoTime() / 1000;
        int numFramesPlayed = mAudioTrack.getPlaybackHeadPosition();
        if (systemClockUs - mLastTimestampSampleTimeUs >= MIN_TIMESTAMP_SAMPLE_INTERVAL_US) {
            mAudioTimestampSet = mAudioTrack.getTimestamp(mAudioTimestamp);
            if (getLatencyMethod != null) {
                try {
                    mLatencyUs = (Integer) getLatencyMethod.invoke(mAudioTrack, (Object[]) null) * 1000L / 2;
                    mLatencyUs = Math.max(mLatencyUs, 0);
                } catch (Exception e) {
                    getLatencyMethod = null;
                }
            }
            mLastTimestampSampleTimeUs = systemClockUs;
        }

        if (mAudioTimestampSet) {
            // Calculate the speed-adjusted position using the timestamp (which may be in the future).
            long elapsedSinceTimestampUs = System.nanoTime() / 1000 - (mAudioTimestamp.nanoTime / 1000);
            long elapsedSinceTimestampFrames = elapsedSinceTimestampUs * mSampleRate / 1000000L;
            long elapsedFrames = mAudioTimestamp.framePosition + elapsedSinceTimestampFrames;
            long durationUs = (elapsedFrames * 1000000L) / mSampleRate;
            return durationUs;
        } else {
            long durationUs = (numFramesPlayed * 1000000L) / mSampleRate - mLatencyUs;
            return durationUs;
        }
    }

    public int getNumBytesQueued() {
        return mNumBytesQueued;
    }

    public void play() {
        mStopped = false;
        mAudioTrack.play();
    }

    public void stop() {
        if (mQueue.isEmpty()) {
            mAudioTrack.stop();
            mNumBytesQueued = 0;
        } else {
            mStopped = true;
        }
    }

    public void pause() {
        mAudioTrack.pause();
    }

    public void flush() {
        if (mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            return;
        }
        mAudioTrack.flush();
        mQueue.clear();
        mNumBytesQueued = 0;
        mStopped = false;
    }

    public void release() {
        mQueue.clear();
        mNumBytesQueued = 0;
        mLatencyUs = 0;
        mLastTimestampSampleTimeUs = 0;
        mAudioTrack.release();
        mAudioTrack = null;
        mStopped = false;
        mAudioTimestampSet = false;
    }

    public void process() {
        while (!mQueue.isEmpty()) {
            QueueElement element = mQueue.peekFirst();
            int written = mAudioTrack.write(element.data, element.size,
                                            AudioTrack.WRITE_NON_BLOCKING, element.pts);
            if (written < 0) {
                throw new RuntimeException("Audiotrack.write() failed.");
            }

            mNumBytesQueued -= written;
            element.size -= written;
            if (element.size != 0) {
                break;
            }
            mQueue.removeFirst();
        }
        if (mStopped) {
            mAudioTrack.stop();
            mNumBytesQueued = 0;
            mStopped = false;
        }
    }

    public int getPlayState() {
        return mAudioTrack.getPlayState();
    }

    public void write(ByteBuffer data, int size, long pts) {
        QueueElement element = new QueueElement();
        element.data = data;
        element.size = size;
        element.pts  = pts;

        // accumulate size written to queue
        mNumBytesQueued += size;
        mQueue.add(element);
    }
}

