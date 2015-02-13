package de.ph1b.audiobook.mediaplayer;//Copyright 2012 James Falcon
//Edited by Paul Woitaschek
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.PowerManager;

import org.vinuxproject.sonic.Sonic;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

import de.ph1b.audiobook.utils.L;

@TargetApi(16)
public class SonicCustomMediaPlayer implements ICustomMediaPlayer {
    private final static int TRACK_NUM = 0;
    private static final String TAG = SonicCustomMediaPlayer.class.getSimpleName();
    private final ReentrantLock lock = new ReentrantLock();
    private final Object mDecoderLock;
    private final float pitch;
    private final PowerManager.WakeLock wakeLock;
    private AudioTrack track;
    private Sonic sonic;
    private MediaExtractor extractor;
    private MediaCodec codec;
    private Thread decoderThread;
    private String path;
    private boolean mContinue;
    private boolean mIsDecoding;
    private float speed;
    private OnCompletionListener onCompletionListener;
    private State state;
    private long duration;

    public SonicCustomMediaPlayer(Context context) {
        L.v(TAG, "constructor called");
        state = State.IDLE;
        speed = (float) 1.0;
        pitch = (float) 1.0;
        mContinue = false;
        mIsDecoding = false;
        path = null;
        mDecoderLock = new Object();

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, SonicCustomMediaPlayer.class.getName());
        wakeLock.setReferenceCounted(false);
    }

    @Override
    public void setOnCompletionListener(OnCompletionListener listener) {
        this.onCompletionListener = listener;
    }

    @Override
    public int getCurrentPosition() {
        switch (state) {
            case ERROR:
                error("getcurrentposition", state);
                break;
            default:
                return (int) (extractor.getSampleTime() / 1000);
        }
        return 0;
    }

    @Override
    public int getDuration() {
        switch (state) {
            case INITIALIZED:
            case IDLE:
            case ERROR:
                error("getDuration()", State.ERROR);
                break;
            default:
                return (int) (duration / 1000);
        }
        return 0;
    }

    @Override
    public void setPlaybackSpeed(float speed) {
        this.speed = speed;
    }

    @Override
    public void pause() {
        L.v(TAG, "pause called");
        switch (state) {
            case PLAYBACK_COMPLETED:
                state = State.PAUSED;
                L.d(TAG, "State changed to: " + state);
                stayAwake(false);
                break;
            case STARTED:
            case PAUSED:
                track.pause();
                state = State.PAUSED;
                L.d(TAG, "State changed to: " + state);
                stayAwake(false);
                break;
            default:
                error("pause", state);
        }
    }

    private void error(String methodName, State lastState) {
        State oldState = State.values()[lastState.ordinal()];
        state = State.ERROR;
        stayAwake(false);
        L.e(TAG, "Called " + methodName + " in state=" + oldState);
    }

    @Override
    public void prepare() throws IOException {
        L.v(TAG, "prepare called in state: " + state);
        switch (state) {
            case INITIALIZED:
            case STOPPED:
                initStream();
                state = State.PREPARED;
                L.d(TAG, "State changed to: " + state);
                break;
            default:
                error("prepare", state);
        }
    }

    @Override
    public void start() {
        L.v(TAG, "start called in state:" + state);
        switch (state) {
            case PLAYBACK_COMPLETED:
                try {
                    initStream();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            case PREPARED:
                state = State.STARTED;
                L.d(TAG, "State changed to: " + state);
                mContinue = true;
                track.play();
                decode();
                stayAwake(true);
                break;
            case STARTED:
                break;
            case PAUSED:
                state = State.STARTED;
                L.d(TAG, "State changed to: " + state);
                synchronized (mDecoderLock) {
                    mDecoderLock.notify();
                }
                track.play();
                stayAwake(true);
                break;
            default:
                error("start", state);
                break;
        }
    }

    private void stayAwake(boolean awake) {
        if (awake && !wakeLock.isHeld()) {
            wakeLock.acquire();
        } else if (!awake && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public void release() {
        L.v(TAG, "release called in state:" + state);
        reset(); //reset will release wakelock
        onCompletionListener = null;
        state = State.END;
        L.d(TAG, "State changed to: " + state);
    }

    @Override
    public void reset() {
        L.v(TAG, "reste called in state: " + state);
        stayAwake(false);
        lock.lock();
        mContinue = false;
        try {
            if (decoderThread != null
                    && state != State.PLAYBACK_COMPLETED) {
                while (mIsDecoding) {
                    synchronized (mDecoderLock) {
                        mDecoderLock.notify();
                        mDecoderLock.wait();
                    }
                }
            }
        } catch (InterruptedException e) {
            L.e(TAG, "Interrupted in reset while waiting for decoder thread to stop.", e);
        }
        if (codec != null) {
            codec.release();
            codec = null;
        }
        if (extractor != null) {
            extractor.release();
            extractor = null;
        }
        if (track != null) {
            track.release();
            track = null;
        }
        state = State.IDLE;
        L.d(TAG, "State changed to: " + state);
        lock.unlock();
    }

    @Override
    public void seekTo(final int ms) {
        switch (state) {
            case PREPARED:
            case STARTED:
            case PAUSED:
            case PLAYBACK_COMPLETED:
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        lock.lock();
                        if (track == null) {
                            return;
                        }
                        track.flush();
                        long to = ((long) ms * 1000);
                        extractor.seekTo(to, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                        lock.unlock();
                    }
                });
                t.setDaemon(true);
                t.start();
                break;
            default:
                error("seekTo", state);
        }
    }

    @Override
    public void setDataSource(String path) {
        L.d(TAG, "setDataSource: " + path);
        switch (state) {
            case IDLE:
                this.path = path;
                state = State.INITIALIZED;
                L.d(TAG, "State changed to: " + state);
                break;
            default:
                error("setDataSource", state);
        }
    }


    private int findFormatFromChannels(int numChannels) {
        switch (numChannels) {
            case 1:
                return AudioFormat.CHANNEL_OUT_MONO;
            case 2:
                return AudioFormat.CHANNEL_OUT_STEREO;
            default:
                return -1; // Error
        }
    }

    private void initStream() throws IOException, IllegalArgumentException {
        L.v(TAG, "inistream called in state=" + state);
        lock.lock();
        extractor = new MediaExtractor();
        if (path != null) {
            extractor.setDataSource(path);
        } else {
            throw new IOException();
        }
        final MediaFormat oFormat = extractor.getTrackFormat(TRACK_NUM);
        int sampleRate = oFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelCount = oFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        final String mime = oFormat.getString(MediaFormat.KEY_MIME);
        duration = oFormat.getLong(MediaFormat.KEY_DURATION);
        L.v(TAG, "Sample rate: " + sampleRate);
        L.v(TAG, "Mime type: " + mime);
        initDevice(sampleRate, channelCount);
        extractor.selectTrack(TRACK_NUM);
        codec = MediaCodec.createDecoderByType(mime);
        codec.configure(oFormat, null, null, 0);
        lock.unlock();
    }

    private void initDevice(int sampleRate, int numChannels) {
        L.d(TAG, "initdevice called in state:" + state);
        lock.lock();
        final int format = findFormatFromChannels(numChannels);
        final int minSize = AudioTrack.getMinBufferSize(sampleRate, format,
                AudioFormat.ENCODING_PCM_16BIT);
        track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, format,
                AudioFormat.ENCODING_PCM_16BIT, minSize * 4,
                AudioTrack.MODE_STREAM);
        sonic = new Sonic(sampleRate, numChannels);
        lock.unlock();
    }

    private void decode() {
        L.d(TAG, "decode called ins state=" + state);
        decoderThread = new Thread(new Runnable() {
            @Override
            public void run() {
                mIsDecoding = true;
                codec.start();
                ByteBuffer[] inputBuffers = codec.getInputBuffers();
                ByteBuffer[] outputBuffers = codec.getOutputBuffers();
                boolean sawInputEOS = false;
                boolean sawOutputEOS = false;
                while (!sawInputEOS && !sawOutputEOS && mContinue) {
                    if (state == State.PAUSED) {
                        try {
                            synchronized (mDecoderLock) {
                                mDecoderLock.wait();
                            }
                        } catch (InterruptedException e) {
                            // Purposely not doing anything here
                        }
                        continue;
                    }
                    if (null != sonic) {
                        sonic.setSpeed(speed);
                        sonic.setPitch(pitch);
                    }
                    int inputBufIndex = codec.dequeueInputBuffer(200);
                    if (inputBufIndex >= 0) {
                        ByteBuffer dstBuf = inputBuffers[inputBufIndex];
                        int sampleSize = extractor.readSampleData(dstBuf, 0);
                        long presentationTimeUs = 0;
                        if (sampleSize < 0) {
                            sawInputEOS = true;
                            sampleSize = 0;
                        } else {
                            presentationTimeUs = extractor.getSampleTime();
                        }
                        codec.queueInputBuffer(
                                inputBufIndex,
                                0,
                                sampleSize,
                                presentationTimeUs,
                                sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                        : 0);
                        if (!sawInputEOS) {
                            extractor.advance();
                        }
                    }
                    final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    byte[] modifiedSamples = new byte[info.size];
                    int res;
                    do {
                        res = codec.dequeueOutputBuffer(info, 200);
                        if (res >= 0) {
                            final byte[] chunk = new byte[info.size];
                            outputBuffers[res].get(chunk);
                            outputBuffers[res].clear();
                            if (chunk.length > 0) {
                                sonic.putBytes(chunk, chunk.length);
                            } else {
                                sonic.flush();
                            }
                            int available = sonic.availableBytes();
                            if (available > 0) {
                                if (modifiedSamples.length < available) {
                                    modifiedSamples = new byte[available];
                                }
                                sonic.receiveBytes(modifiedSamples, available);
                                track.write(modifiedSamples, 0, available);
                            }
                            codec.releaseOutputBuffer(res, false);
                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                sawOutputEOS = true;
                            }
                        } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            outputBuffers = codec.getOutputBuffers();
                        } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            track.stop();
                            lock.lock();
                            track.release();
                            final MediaFormat oformat = codec
                                    .getOutputFormat();
                            initDevice(
                                    oformat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                                    oformat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                            outputBuffers = codec.getOutputBuffers();
                            track.play();
                            lock.unlock();
                        }
                    } while (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED
                            || res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
                }

                codec.stop();
                track.stop();
                mIsDecoding = false;
                if (mContinue && (sawInputEOS || sawOutputEOS)) {
                    state = State.PLAYBACK_COMPLETED;
                    L.d(TAG, "State changed to: " + state);
                    Thread t = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            onCompletionListener.onCompletion();
                            stayAwake(false);
                        }
                    });
                    t.setDaemon(true);
                    t.start();
                }
                synchronized (mDecoderLock) {
                    mDecoderLock.notifyAll();
                }
            }
        }

        );
        decoderThread.setDaemon(true);
        decoderThread.start();
    }

}