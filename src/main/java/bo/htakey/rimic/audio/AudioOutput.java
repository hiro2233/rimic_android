/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package bo.htakey.rimic.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import bo.htakey.rimic.Constants;
import bo.htakey.rimic.exception.AudioInitializationException;
import bo.htakey.rimic.exception.NativeAudioException;
import bo.htakey.rimic.model.TalkState;
import bo.htakey.rimic.model.User;
import bo.htakey.rimic.net.RimicUDPMessageType;
import bo.htakey.rimic.net.PacketBuffer;
import bo.htakey.rimic.protocol.AudioHandler;
import bo.htakey.rimic.audio.encoder.PreprocessingEncoder;

/**
 * Created by andrew on 16/07/13.
 */
public class AudioOutput implements Runnable, AudioOutputSpeech.TalkStateListener {
    private Map<Integer,AudioOutputSpeech> mAudioOutputs = new HashMap<>();
    private AudioTrack mAudioTrack;
    private int mBufferSize;
    private Thread mThread;
    private final Object mInactiveLock = new Object(); // Lock that the audio thread waits on when there's no audio to play. Wake when we get a frame.
    private final Lock mPacketLock;
    private boolean mRunning = false;
    private Handler mMainHandler;
    private AudioOutputListener mListener;
    private final IAudioMixer<float[], short[]> mMixer;
    private ExecutorService mDecodeExecutorService;
    private int sessionId = 0;

    public AudioOutput(AudioOutputListener listener) {
        mListener = listener;
        mMainHandler = new Handler(Looper.getMainLooper());
        mDecodeExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        mPacketLock = new ReentrantLock();
        mMixer = new BasicClippingShortMixer();
    }

    public Thread startPlaying(int audioStream) throws AudioInitializationException {
        if (mThread != null || mRunning)
            return null;

        int minBufferSize = AudioTrack.getMinBufferSize(AudioHandler.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        mBufferSize = Math.min(minBufferSize, AudioHandler.FRAME_SIZE * 8);
        Log.v(Constants.TAG, "Using buffer size " + mBufferSize + ", system's min buffer size: " + minBufferSize);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioFormat.Builder audiofmt = new AudioFormat.Builder();

                audiofmt.setChannelMask(AudioFormat.CHANNEL_OUT_MONO);
                audiofmt.setEncoding(AudioFormat.ENCODING_PCM_16BIT);
                audiofmt.setSampleRate(AudioHandler.SAMPLE_RATE);

                AudioAttributes.Builder audioatrr = new AudioAttributes.Builder();
                audioatrr.setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION);
                audioatrr.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);

                if (sessionId == 0) {
                    sessionId = AudioManager.AUDIO_SESSION_ID_GENERATE;
                }

                mAudioTrack = new AudioTrack(
                        audioatrr.build(),
                        audiofmt.build(),
                        mBufferSize,
                        AudioTrack.MODE_STREAM,
                        sessionId);
                mAudioTrack.setVolume((float)0.90);
            } else {
                mAudioTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL,
                        AudioHandler.SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        mBufferSize,
                        AudioTrack.MODE_STREAM,
                        sessionId);
            }
        } catch (IllegalArgumentException e) {
            throw new AudioInitializationException(e);
        }

        mThread = new Thread(this);
        mThread.start();
        return mThread;
    }

    public void stopPlaying() {
        if(!mRunning)
            return;

        mRunning = false;
        synchronized (mInactiveLock) {
            mInactiveLock.notify(); // Wake inactive lock if active
        }
        try {
            mThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mThread = null;

        mPacketLock.lock();
        for(AudioOutputSpeech speech : mAudioOutputs.values()) {
            speech.destroy();
        }
        mPacketLock.unlock();

        mAudioOutputs.clear();
        mAudioTrack.release();
        mAudioTrack = null;
    }

    public boolean isPlaying() {
        return mRunning;
    }

    @Override
    public void run() {
        Log.v(Constants.TAG, "Started audio output thread.");
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        mRunning = true;
        mAudioTrack.play();

        final short[] mix = new short[mBufferSize];
        boolean isPaused = false;
        final long inactivity_output = 30000; // Detect activity output, if no output on 30 secs, then playing stop and wait interruption.
        long vActivityLastDetected = System.currentTimeMillis();;

        while(mRunning) {
            boolean fetched = fetchAudio(mix, 0, mBufferSize);
            mAudioTrack.write(mix, 0, mBufferSize);
            PreprocessingEncoder.mEcho.echo_playback(mix);
            if(fetched) {
                vActivityLastDetected = System.currentTimeMillis();
            }

            fetched = !((System.currentTimeMillis() - vActivityLastDetected) < inactivity_output);

            if (fetched) {
                synchronized (mInactiveLock) {
                    mAudioTrack.pause();
                    mAudioTrack.flush();
                    mAudioTrack.stop();
                    Log.v(Constants.TAG, "Output stopped");
                    try {
                        mInactiveLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    vActivityLastDetected = System.currentTimeMillis();
                    mAudioTrack.play();
                    Log.v(Constants.TAG, "Output Playing");
                }
            }
        }

        mAudioTrack.pause();
        mAudioTrack.flush();
        mAudioTrack.stop();
    }

    /**
     * Fetches audio data from registered audio output users and mixes them into the given buffer.
     * TODO: add priority speaker support.
     * @param buffer The buffer to mix output data into.
     * @param bufferOffset The offset of the
     * @param bufferSize The size of the buffer.
     * @return true if the buffer contains audio data.
     */
    private boolean fetchAudio(short[] buffer, int bufferOffset, int bufferSize) {
        Arrays.fill(buffer, bufferOffset, bufferOffset + bufferSize, (short) 0);
        final List<IAudioMixerSource<float[]>> sources = new ArrayList<>();
        try {
            if (!(mPacketLock.tryLock(20L, TimeUnit.MILLISECONDS))) {
                return false;
            }
                // Parallelize decoding using a fixed thread pool equal to the number of cores
            List<Future<AudioOutputSpeech.Result>> futureResults =
                    mDecodeExecutorService.invokeAll(mAudioOutputs.values());
            for (Future<AudioOutputSpeech.Result> future : futureResults) {
                AudioOutputSpeech.Result result = future.get();
                if (result.isAlive()) {
                    sources.add(result);
                } else {
                    AudioOutputSpeech speech = result.getSpeechOutput();
                    Log.v(Constants.TAG, "Deleted audio user " + speech.getUser().getName());
                    mAudioOutputs.remove(speech.getSession());
                    speech.destroy();
                }
            }
            mPacketLock.unlock();
        } catch (InterruptedException e) {
            Log.v(Constants.TAG, "InterruptedException on FetchAudio " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (ExecutionException e) {
            Log.v(Constants.TAG, "ExecutionException on FetchAudio " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        if (sources.size() == 0)
            return false;

        mMixer.mix(sources, buffer, bufferOffset, bufferSize);
        return true;
    }

    public void queueVoiceData(byte[] data, RimicUDPMessageType messageType) {
        if(!mRunning)
            return;

        byte msgFlags = (byte) (data[0] & 0x1f);
        PacketBuffer pds = new PacketBuffer(data, data.length);
        pds.skip(1);
        int session = (int) pds.readLong();
        User user = mListener.getUser(session);
        if(user != null && !user.isLocalMuted()) {
            // TODO check for whispers here
            int seq = (int) pds.readLong();

            // Synchronize so we don't destroy an output while we add a buffer to it.
            try {
                mPacketLock.lock();
                AudioOutputSpeech aop = mAudioOutputs.get(session);
                if(aop != null && aop.getCodec() != messageType) {
                    aop.destroy();
                    aop = null;
                }

                if(aop == null) {
                    try {
                        aop = new AudioOutputSpeech(user, messageType, mBufferSize, this);
                    } catch (NativeAudioException e) {
                        mPacketLock.unlock();
                        Log.v(Constants.TAG, "Failed to create audio user "+user.getName());
                        e.printStackTrace();
                        return;
                    }
                    Log.v(Constants.TAG, "Created audio user "+user.getName());
                    mAudioOutputs.put(session, aop);
                }
                mPacketLock.unlock();
                PacketBuffer dataBuffer = new PacketBuffer(pds.bufferBlock(pds.left()));
                aop.addFrameToBuffer(dataBuffer, msgFlags, seq);
            } finally {
            }

            synchronized (mInactiveLock) {
                mInactiveLock.notify();
            }
        }
    }

    public void setmAudioTrackSessionID(int sessId) {
        sessionId = sessId;
    }

    @Override
    public void onTalkStateUpdated(final int session, final TalkState state) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                final User user = mListener.getUser(session);
                if(user != null && user.getTalkState() != state) {
                    user.setTalkState(state);
                    mListener.onUserTalkStateUpdated(user);
                }
            }
        });
    }

    public static interface AudioOutputListener {
        /**
         * Called when a user's talking state is changed.
         * @param user The user whose talking state has been modified.
         */
        public void onUserTalkStateUpdated(User user);

        /**
         * Used to set audio-related user data.
         * @return The user for the associated session.
         */
        public User getUser(int session);
    }
}
