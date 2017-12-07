package com.example.zhanghui.avplayer;

/*
 * Interface used by CodecState to retrieve Media timing info from parent Player
 */
public interface MediaTimeProvider {
    public long getNowUs();
    public long getRealTimeUsForMediaTime(long mediaTimeUs);
    public long getVsyncDurationNs();
}
