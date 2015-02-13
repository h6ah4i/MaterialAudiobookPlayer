package de.ph1b.audiobook.mediaplayer;


import android.content.Context;
import android.os.Build;

import com.h6ah4i.android.media.IMediaPlayerFactory;

import java.io.IOException;

public class MediaPlayerCompat {

    private final boolean useCustomMediaPlayer;
    private android.media.MediaPlayer androidMediaPlayer;
    private ICustomMediaPlayer customCustomMediaPlayer;

    public MediaPlayerCompat(Context c, IMediaPlayerFactory factory) {
        useCustomMediaPlayer = (Build.VERSION.SDK_INT >= 16);

        if (useCustomMediaPlayer) {
            if (factory == null) {
                customCustomMediaPlayer = new SonicCustomMediaPlayer(c);
            } else {
                customCustomMediaPlayer = new FactoryGeneratedCustomMediaPlayer(factory);
            }
        } else {
            androidMediaPlayer = new android.media.MediaPlayer();
        }
    }

    public void setPlaybackSpeed(float speed) {
        if (useCustomMediaPlayer) {
            customCustomMediaPlayer.setPlaybackSpeed(speed);
        }
    }

    public void reset() {
        if (useCustomMediaPlayer) {
            customCustomMediaPlayer.reset();
        } else {
            androidMediaPlayer.reset();
        }
    }

    public int getDuration() {
        return (useCustomMediaPlayer ? customCustomMediaPlayer.getDuration() : androidMediaPlayer.getDuration());
    }

    public void release() {
        if (useCustomMediaPlayer) {
            customCustomMediaPlayer.release();
        } else {
            androidMediaPlayer.release();
        }
    }

    public void prepare() {
        try {
            if (useCustomMediaPlayer) {
                customCustomMediaPlayer.prepare();
            } else {
                androidMediaPlayer.prepare();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setDataSource(String path) {
        if (useCustomMediaPlayer) {
            try {
                customCustomMediaPlayer.setDataSource(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                androidMediaPlayer.setDataSource(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void seekTo(int position) {
        if (useCustomMediaPlayer) {
            customCustomMediaPlayer.seekTo(position);
        } else {
            androidMediaPlayer.seekTo(position);
        }
    }

    public void setOnCompletionListener(final OnCompletionListener listener) {
        if (useCustomMediaPlayer) {
            customCustomMediaPlayer.setOnCompletionListener(new SonicCustomMediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion() {
                    listener.onCompletion();
                }
            });
        } else {
            androidMediaPlayer.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(android.media.MediaPlayer mp) {
                    listener.onCompletion();
                }
            });
        }
    }

    public int getCurrentPosition() {
        return (useCustomMediaPlayer ? customCustomMediaPlayer.getCurrentPosition() : androidMediaPlayer.getCurrentPosition());
    }

    public void start() {
        if (useCustomMediaPlayer) {
            customCustomMediaPlayer.start();
        } else {
            androidMediaPlayer.start();
        }
    }

    public void pause() {
        if (useCustomMediaPlayer) {
            customCustomMediaPlayer.pause();
        } else {
            androidMediaPlayer.pause();
        }
    }

    /**
     * Sets wakeMode. Custom Mediaplayer handles wake mode on its own.
     *
     * @param c    Context
     * @param mode PowerManager flags
     */
    public void setWakeMode(Context c, int mode) {
        if (!useCustomMediaPlayer) {
            androidMediaPlayer.setWakeMode(c, mode);
        }
    }


    public interface OnCompletionListener {
        public void onCompletion();
    }
}
