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

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Build;
import android.util.Log;

import bo.htakey.rimic.Constants;
import bo.htakey.rimic.exception.AudioInitializationException;
import bo.htakey.rimic.exception.NativeAudioException;
import bo.htakey.rimic.protocol.AudioHandler;

/**
 * Created by andrew on 23/08/13.
 */
public class AudioInput implements Runnable {
    public static final int[] SAMPLE_RATES = { 48000, 44100, 16000, 8000 };

    // AudioRecord state
    private AudioInputListener mListener;
    private AudioRecord mAudioRecord;
    private final int mFrameSize;

    private Thread mRecordThread;
    private boolean mRecording;
    private boolean vMuted;
    private final Object mInactiveLock = new Object();

    public AudioInput(AudioInputListener listener, int audioSource, int targetSampleRate)
            throws NativeAudioException, AudioInitializationException {
        mListener = listener;

        // Attempt to construct an AudioRecord with the target sample rate first.
        // If it fails, keep producing AudioRecord instances until we find one that initializes
        // correctly. Maybe one day Android will let us probe for supported sample rates, as we
        // aren't even guaranteed that 44100hz will work across all devices.
        for (int i = SAMPLE_RATES.length - 1; i >= 0; i--) {
            //int sampleRate = i == 0 ? targetSampleRate : SAMPLE_RATES[i];
            int sampleRate = SAMPLE_RATES[i];
            try {
                mAudioRecord = setupAudioRecord(sampleRate, audioSource);
                if (targetSampleRate == sampleRate) {
                    Log.v(Constants.TAG, "OK! mAudioRecod at: " + sampleRate + " rate");
                    break;
                } else if (i != 0) {
                    mAudioRecord = null;
                    Log.v(Constants.TAG,"Missmatch target rate at: " + sampleRate);
                }
            } catch (Exception e) {
                Log.v(Constants.TAG,"Exception hardware rate at: " + sampleRate);
                // Continue iteration, probing for a supported sample rate.
            }
        }

        if (mAudioRecord == null) {
            throw new AudioInitializationException("Unable to initialize AudioInput.");
        }

        int sampleRate = getSampleRate();
                // FIXME: does not work properly if 10ms frames cannot be represented as integers
        mFrameSize = (sampleRate * AudioHandler.FRAME_SIZE) / AudioHandler.SAMPLE_RATE;
    }

    private static AudioRecord setupAudioRecord(int sampleRate, int audioSource) throws AudioInitializationException {
        int minBufferSizetmp = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO,
                                                                AudioFormat.ENCODING_PCM_16BIT);
        int minBufferSize;
        minBufferSize = Math.min(minBufferSizetmp, ((sampleRate * AudioHandler.FRAME_SIZE) / AudioHandler.SAMPLE_RATE) * 8);
        if (minBufferSize <= 0)
            throw new AudioInitializationException("Invalid buffer size returned (unsupported sample rate).");

        AudioRecord audioRecord = null;
        try {
            audioRecord = new AudioRecord(audioSource, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                                                 AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
            Log.v(Constants.TAG, "#1 INPUT using buf size " + minBufferSize + ", system's min buf size: " + minBufferSizetmp + ", Sample rate: " + sampleRate);
            if(audioRecord.getState() == AudioRecord.STATE_UNINITIALIZED) {
                audioRecord.release();
                audioRecord = null;
            }
        } catch (Exception e) {
            Log.v(Constants.TAG, "Failed input 1" + ", Sample rate: " + sampleRate);
        }

        // Try with 1024 buffer size
        minBufferSize = Math.min(minBufferSizetmp, 1024);

        if (audioRecord == null) {
            try {
                audioRecord = new AudioRecord(audioSource, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
                Log.v(Constants.TAG, "#2 INPUT using buf size " + minBufferSize + ", system's min buf size: " + minBufferSizetmp + ", Sample rate: " + sampleRate);
                if(audioRecord.getState() == AudioRecord.STATE_UNINITIALIZED) {
                    audioRecord.release();
                    audioRecord = null;
                }
            } catch (Exception e) {
                Log.v(Constants.TAG, "Failed input 2" + ", Sample rate: " + sampleRate);
            }
        }

        if (audioRecord == null) {
            try {
                audioRecord = new AudioRecord(audioSource, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, minBufferSizetmp);
                Log.v(Constants.TAG, "#3 INPUT using buf size " + minBufferSizetmp + ", system's min buf size: " + minBufferSizetmp + ", Sample rate: " + sampleRate);
            } catch (Exception e) {
                throw new AudioInitializationException(e);
            }
        }

        if(audioRecord.getState() == AudioRecord.STATE_UNINITIALIZED) {
            audioRecord.release();
            throw new AudioInitializationException("AudioRecord failed to initialize!");
        }

        Log.v(Constants.TAG, "Passed Sample rate: " + sampleRate);
        return audioRecord;
    }
    /**
     * Starts the recording thread.
     * Not thread-safe.
     */
    public void startRecording() {
        mRecording = true;
        mRecordThread = new Thread(this);
        mRecordThread.start();
    }

    /**
     * Stops the record loop after the current iteration, joining it.
     * Not thread-safe.
     */
    public void stopRecording() {
        if(!mRecording) return;
        mRecording = false;
        try {
            mRecordThread.interrupt();
            mRecordThread.join();
            mRecordThread = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Stops the record loop and waits on it to finish.
     * Releases native audio resources.
     * NOTE: It is not safe to call startRecording after.
     */
    public void shutdown() {
        stopRecording();
        if(mAudioRecord != null) {
            mAudioRecord.release();
            mAudioRecord = null;
        }
    }

    public boolean isRecording() {
        return mRecording;
    }

    public void setMuted(boolean muted) {
        vMuted = muted;
        synchronized (mInactiveLock) {
            mInactiveLock.notify();
        }
    }

    /**
     * @return the sample rate used by the AudioRecord instance.
     */
    public int getSampleRate() {
        return mAudioRecord.getSampleRate();
    }

    /**
     * @return the frame size used, varying depending on the sample rate selected.
     */
    public int getFrameSize() {
        return mFrameSize;
    }

    @Override
    public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

        Log.i(Constants.TAG, "AudioInput: started");

        mAudioRecord.startRecording();

        if(mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED)
            return;

        final short[] mAudioBuffer = new short[mFrameSize];
        // We loop when the 'recording' instance var is true instead of checking audio record state because we want to always cleanly shutdown.
        while(mRecording) {
            int shortsRead;
            if (vMuted) {
                shortsRead = -1;
            } else {
                if (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED) {
                   mAudioRecord.startRecording();
                    Log.v(Constants.TAG, "Record started");
                }
                shortsRead = mAudioRecord.read(mAudioBuffer, 0, mFrameSize);
            }

            if(shortsRead > 0) {
                mListener.onAudioInputReceived(mAudioBuffer, mFrameSize);
            } else if (!(shortsRead == -1)) {
                Log.e(Constants.TAG, "Error fetching audio! AudioRecord error " + shortsRead);
            } else {
                if (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    mAudioRecord.stop();
                    Log.v(Constants.TAG, "Record Stopped");
                }
                synchronized (mInactiveLock) {
                    try {
                        mInactiveLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        mAudioRecord.stop();

        Log.i(Constants.TAG, "AudioInput: stopped");
    }

    public int getAudioSessionId() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            return mAudioRecord.getAudioSessionId();
        } else {
            return 0;
        }
    }
    public interface AudioInputListener {
        void onAudioInputReceived(short[] frame, int frameSize);
    }
}
