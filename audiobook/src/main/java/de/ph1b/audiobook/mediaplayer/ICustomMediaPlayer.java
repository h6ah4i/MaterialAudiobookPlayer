package de.ph1b.audiobook.mediaplayer;

import java.io.IOException;

public interface ICustomMediaPlayer {
    void setOnCompletionListener(OnCompletionListener listener);

    int getCurrentPosition();

    int getDuration();

    void setPlaybackSpeed(float speed);

    void pause();

    void prepare() throws IOException;

    void start();

    void release();

    void reset();

    void seekTo(int ms);

    void setDataSource(String path) throws IOException;

    public enum State {
        IDLE,
        ERROR,
        INITIALIZED,
        STARTED,
        PAUSED,
        PREPARED,
        STOPPED,
        PLAYBACK_COMPLETED,
        END
    }

    public interface OnCompletionListener {
        public void onCompletion();
    }
}
