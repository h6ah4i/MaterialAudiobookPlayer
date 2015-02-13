package de.ph1b.audiobook.mediaplayer;

import com.h6ah4i.android.media.IBasicMediaPlayer;
import com.h6ah4i.android.media.IMediaPlayerFactory;

import java.io.IOException;

public class FactoryGeneratedCustomMediaPlayer implements ICustomMediaPlayer, IBasicMediaPlayer.OnCompletionListener {
    private IMediaPlayerFactory mFactory;
    private IBasicMediaPlayer mPlayer;
    private OnCompletionListener mCompletionListener;

    public FactoryGeneratedCustomMediaPlayer(IMediaPlayerFactory playerFactory) {
        mFactory = playerFactory;
        mPlayer = playerFactory.createMediaPlayer();
        mPlayer.setOnCompletionListener(this);
    }

    @Override
    public void setOnCompletionListener(OnCompletionListener listener) {
        mCompletionListener = listener;
    }

    @Override
    public int getCurrentPosition() {
        if (mPlayer != null) {
            return mPlayer.getCurrentPosition();
        } else {
            return 0;
        }
    }

    @Override
    public int getDuration() {
        if (mPlayer != null) {
            return mPlayer.getDuration();
        } else {
            return 0;
        }
    }

    @Override
    public void setPlaybackSpeed(float speed) {
        // not supported yet...
    }

    @Override
    public void pause() {
        if (mPlayer != null) {
            mPlayer.pause();
        }
    }

    @Override
    public void prepare() throws IOException {
        if (mPlayer != null) {
            mPlayer.prepare();
        }
    }

    @Override
    public void start() {
        if (mPlayer != null) {
            mPlayer.start();
        }
    }

    @Override
    public void release() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
        mFactory = null;
    }

    @Override
    public void reset() {
        if (mPlayer != null) {
            mPlayer.reset();
        }
    }

    @Override
    public void seekTo(int ms) {
        if (mPlayer != null) {
            mPlayer.seekTo(ms);
        }
    }

    @Override
    public void setDataSource(String path) throws IOException {
        if (mPlayer != null) {
            mPlayer.setDataSource(path);
        }
    }

    @Override
    public void onCompletion(IBasicMediaPlayer iBasicMediaPlayer) {
        if (mCompletionListener != null) {
            mCompletionListener.onCompletion();
        }
    }
}
