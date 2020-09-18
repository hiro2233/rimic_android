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

package bo.htakey.rimic.protocol;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

import bo.htakey.rimic.Constants;
import bo.htakey.rimic.audio.AudioInput;
import bo.htakey.rimic.audio.AudioOutput;
import bo.htakey.rimic.audio.encoder.CELT11Encoder;
import bo.htakey.rimic.audio.encoder.CELT7Encoder;
import bo.htakey.rimic.audio.encoder.IEncoder;
import bo.htakey.rimic.audio.encoder.OpusEncoder;
import bo.htakey.rimic.audio.encoder.PreprocessingEncoder;
import bo.htakey.rimic.audio.encoder.ResamplingEncoder;
import bo.htakey.rimic.audio.inputmode.IInputMode;
import bo.htakey.rimic.exception.AudioException;
import bo.htakey.rimic.exception.AudioInitializationException;
import bo.htakey.rimic.exception.NativeAudioException;
import bo.htakey.rimic.model.User;
import bo.htakey.rimic.net.PacketBuffer;
import bo.htakey.rimic.net.RimicConnection;
import bo.htakey.rimic.net.RimicUDPMessageType;
import bo.htakey.rimic.protobuf.Mumble;
import bo.htakey.rimic.util.RimicLogger;
import bo.htakey.rimic.util.RimicNetworkListener;

/**
 * Bridges the protocol's audio messages to our input and output threads.
 * A useful intermediate for reducing code coupling.
 * Audio playback and recording is exclusively controlled by the protocol.
 * Changes to input/output instance vars after the audio threads have been initialized will recreate
 * them in most cases (they're immutable for the purpose of avoiding threading issues).
 * Calling shutdown() will cleanup both input and output threads. It is safe to restart after.
 * Created by andrew on 23/04/14.
 *
 * Removed setMaxBandwidth() this was unnecessary, codec already perform this automatically through
 * bitrate and sample rate.
 * Fixed sample rate and buffers, now we can set to 10 ms regardless sampling rate ;)
 * Updated by hiroshi on 21/08/2020
 */
public class AudioHandler extends RimicNetworkListener implements AudioInput.AudioInputListener {
    public static final int SAMPLE_RATE = 16000;
    public static final int FRAME_SIZE = SAMPLE_RATE/100;
    public static final int MAX_BUFFER_SIZE = FRAME_SIZE * 12;

    private final Context mContext;
    private final RimicLogger mLogger;
    private final AudioManager mAudioManager;
    private final AudioInput mInput;
    private final AudioOutput mOutput;
    private AudioOutput.AudioOutputListener mOutputListener;
    private AudioEncodeListener mEncodeListener;

    private int mSession;
    private RimicUDPMessageType mCodec;
    private IEncoder mEncoder;
    private int mFrameCounter;

    private final int mAudioStream;
    private final int mAudioSource;
    private int mSampleRate;
    private int mBitrate;
    private int mFramesPerPacket;
    private final IInputMode mInputMode;
    private final float mAmplitudeBoost;

    private boolean mInitialized;
    /** True if the user is muted on the server. */
    private boolean mMuted;
    private boolean mBluetoothOn;
    private boolean mHalfDuplex;
    private boolean mPreprocessorEnabled;
    /** The last observed talking state. False if muted, or the input mode is not active. */
    private boolean mTalking;

    private final Object mEncoderLock;
    private byte mTargetId;

    public AudioHandler(Context context, RimicLogger logger, int audioStream, int audioSource,
                        int sampleRate, int targetBitrate, int targetFramesPerPacket,
                        IInputMode inputMode, byte targetId, float amplitudeBoost,
                        boolean bluetoothEnabled, boolean halfDuplexEnabled,
                        boolean preprocessorEnabled, AudioEncodeListener encodeListener,
                        AudioOutput.AudioOutputListener outputListener) throws AudioInitializationException, NativeAudioException {
        mContext = context;
        mLogger = logger;
        mAudioStream = audioStream;
        mAudioSource = audioSource;
        mSampleRate = sampleRate;
        mBitrate = targetBitrate;
        mFramesPerPacket = targetFramesPerPacket;
        mInputMode = inputMode;
        mAmplitudeBoost = amplitudeBoost;
        mBluetoothOn = bluetoothEnabled;
        mHalfDuplex = halfDuplexEnabled;
        mPreprocessorEnabled = preprocessorEnabled;
        mEncodeListener = encodeListener;
        mOutputListener = outputListener;
        mTalking = false;
        mTargetId = targetId;

        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mEncoderLock = new Object();

        mInput = new AudioInput(this, mAudioSource, mSampleRate);
        Log.v(Constants.TAG, "Handler Input Created");
        mOutput = new AudioOutput(mOutputListener);
        Log.v(Constants.TAG, "Handler Output Created");
    }

    /**
     * Starts the audio output and input threads.
     * Will create both the input and output modules if they haven't been created yet.
     */
    public synchronized void initialize(User self, int maxBandwidth, RimicUDPMessageType codec) throws AudioException {
        if(mInitialized) return;
        mSession = self.getSession();

        Log.v(Constants.TAG, "Handler: Initializing");
        setCodec(codec);
        Log.v(Constants.TAG, "Handler: Codec Initialized");
        startRecording();
        setServerMuted(self.isMuted() || self.isLocalMuted() || self.isSuppressed());
        Log.v(Constants.TAG, "Handler: Recording Initialized");

        int sessid = 0;
        synchronized (mInput) {
            sessid = mInput.getAudioSessionId();
        }

        mOutput.setmAudioTrackSessionID(sessid);
        int audiostream = mAudioStream;
        if (mAudioManager.isWiredHeadsetOn()) {
            audiostream = AudioManager.STREAM_VOICE_CALL;
        }

        mOutput.startPlaying(audiostream);
        Log.v(Constants.TAG, "Handler: Playing Initialized");

        if (audiostream == AudioManager.STREAM_MUSIC) {
            if (mAudioManager.isWiredHeadsetOn()) {
                mAudioManager.setSpeakerphoneOn(false);
            } else {
                mAudioManager.setSpeakerphoneOn(true);
            }
        } else {
            mAudioManager.setSpeakerphoneOn(false);
        }

        mInitialized = true;
    }

    /**
     * Starts a recording AudioInput thread.
     * @throws AudioException if the input thread failed to initialize, or if a thread was already
     *                        recording.
     */
    private void startRecording() throws AudioException {
        synchronized (mInput) {
            if (!mInput.isRecording()) {
                mInput.startRecording();
            } else {
                throw new AudioException("Attempted to start recording while recording!");
            }
        }
    }

    /**
     * Stops the recording AudioInput thread.
     * @throws AudioException if there was no thread recording.
     */
    private void stopRecording() throws AudioException {
        synchronized (mInput) {
            if (mInput.isRecording()) {
                mInput.stopRecording();
            } else {
                throw new AudioException("Attempted to stop recording while not recording!");
            }
        }
    }

    /**
     * Sets whether or not the server wants the client muted.
     * @param muted Whether the user is muted on the server.
     */
    private void setServerMuted(boolean muted) throws AudioException {
        mMuted = muted;
        synchronized (mInput) {
            mInput.setMuted(muted);
        }
    }

    /**
     * Returns whether or not the handler has been initialized.
     * @return true if the handler is ready to play and record audio.
     */
    public boolean isInitialized() {
        return mInitialized;
    }

    public boolean isPlaying() {
        synchronized (mOutput) {
            return mOutput.isPlaying();
        }
    }

    public RimicUDPMessageType getCodec() {
        return mCodec;
    }

    public void recreateEncoder() throws NativeAudioException {
        setCodec(mCodec);
    }

    public void setCodec(RimicUDPMessageType codec) throws NativeAudioException {
        mCodec = codec;

        if (mEncoder != null) {
            mEncoder.destroy();
            mEncoder = null;
        }

        if (codec == null) {
            Log.w(Constants.TAG, "AudioHandler setCodec(null) Input disabled.");
            return;
        }

        String msgcodec = "";
        IEncoder encoder;
        switch (codec) {
            case UDPVoiceCELTAlpha:
                msgcodec = "CELTAlpha";
                Log.v(Constants.TAG, "Handler: creating codec " + msgcodec);
                encoder = new CELT7Encoder(SAMPLE_RATE, AudioHandler.FRAME_SIZE, 1,
                        mFramesPerPacket, mBitrate, MAX_BUFFER_SIZE);
                break;
            case UDPVoiceCELTBeta:
                msgcodec = "CELTBeta";
                Log.v(Constants.TAG, "Handler: creating codec " + msgcodec);
                encoder = new CELT11Encoder(SAMPLE_RATE, 1, mFramesPerPacket);
                break;
            case UDPVoiceOpus:
                msgcodec = "Opus";
                Log.v(Constants.TAG, "Handler: creating codec " + msgcodec);
                encoder = new OpusEncoder(SAMPLE_RATE, 1, FRAME_SIZE, mFramesPerPacket, mBitrate,
                        MAX_BUFFER_SIZE);
                break;
            default:
                Log.w(Constants.TAG, "Unsupported codec, input disabled.");
                return;
        }

        Log.v(Constants.TAG, "Handler: created " + msgcodec + " codec");

        if (mPreprocessorEnabled) {
            encoder = new PreprocessingEncoder(encoder, FRAME_SIZE, SAMPLE_RATE);
        }

        if (mInput.getSampleRate() != SAMPLE_RATE) {
            encoder = new ResamplingEncoder(encoder, 1, mInput.getSampleRate(), FRAME_SIZE, SAMPLE_RATE);
        }

        mEncoder = encoder;
    }

    public int getAudioStream() {
        return mAudioStream;
    }

    public int getAudioSource() {
        return mAudioSource;
    }

    public int getSampleRate() {
        return mSampleRate;
    }

    public int getBitrate() {
        return mBitrate;
    }

    /**
     * Sets the maximum bandwidth available for audio input as obtained from the server.
     * Adjusts the bitrate and frames per packet accordingly to meet the server's requirement.
     */
/*
    private void setMaxBandwidth(int maxBandwidth) throws AudioException {
        if (maxBandwidth == -1) {
            return;
        }
        int bitrate = mBitrate;
        int framesPerPacket = mFramesPerPacket;
        // Logic as per desktop Mumble's AudioInput::adjustBandwidth for consistency.
        if (RimicConnection.calculateAudioBandwidth(bitrate, framesPerPacket) > maxBandwidth) {
            if (framesPerPacket <= 4 && maxBandwidth <= 32000) {
                framesPerPacket = 4;
            } else if (framesPerPacket == 1 && maxBandwidth <= 64000) {
                framesPerPacket = 2;
            } else if (framesPerPacket == 2 && maxBandwidth <= 48000) {
                framesPerPacket = 4;
            }
            while (RimicConnection.calculateAudioBandwidth(bitrate, framesPerPacket)
                    > maxBandwidth && bitrate > 8000) {
                bitrate -= 1000;
            }
        }
        bitrate = Math.max(8000, bitrate);

        if (bitrate != mBitrate ||
                framesPerPacket != mFramesPerPacket) {
            mBitrate = bitrate;
            mFramesPerPacket = framesPerPacket;

            mLogger.logInfo(mContext.getString(R.string.audio_max_bandwidth,
                    maxBandwidth/1000, maxBandwidth/1000, framesPerPacket * 10));
        }
    }
*/
    public int getFramesPerPacket() {
        return mFramesPerPacket;
    }

    public float getAmplitudeBoost() {
        return mAmplitudeBoost;
    }

    /**
     * Returns whether or not the audio handler is operating in half duplex mode, muting outgoing
     * audio when incoming audio is received.
     * @return true if the handler is in half duplex mode.
     */
    public boolean isHalfDuplex() {
        return mHalfDuplex;
    }

    public int getCurrentBandwidth() {
        return RimicConnection.calculateAudioBandwidth(mBitrate, mFramesPerPacket);
    }

    /**
     * Shuts down the audio handler, halting input and output.
     */
    public synchronized void shutdown() {
        synchronized (mInput) {
            mInput.shutdown();
        }
        synchronized (mOutput) {
            mOutput.stopPlaying();
        }
        synchronized (mEncoderLock) {
            if (mEncoder != null) {
                mEncoder.destroy();
                mEncoder = null;
            }
        }
        mInitialized = false;
        mBluetoothOn = false;

        mEncodeListener.onTalkingStateChanged(false);
        mAudioManager.setSpeakerphoneOn(false);
        mAudioManager.setMode(AudioManager.MODE_NORMAL);
    }

    @Override
    public void messageCodecVersion(Mumble.CodecVersion msg) {
        if (!mInitialized)
            return; // Only listen to change events in this handler.

        RimicUDPMessageType codec;
        if (msg.hasOpus() && msg.getOpus()) {
            codec = RimicUDPMessageType.UDPVoiceOpus;
            Log.v(Constants.TAG, "UDP Opus codec");
        } else if (msg.hasBeta() && !msg.getPreferAlpha()) {
            codec = RimicUDPMessageType.UDPVoiceCELTBeta;
            Log.v(Constants.TAG, "UDP CELTBeta codec");
        } else {
            codec = RimicUDPMessageType.UDPVoiceCELTAlpha;
            Log.v(Constants.TAG, "UDP CELTAlpha codec");
        }

        if (codec != mCodec) {
            try {
                synchronized (mEncoderLock) {
                    setCodec(codec);
                }
            } catch (NativeAudioException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void messageServerSync(Mumble.ServerSync msg) {
    }

    @Override
    public void messageUserState(Mumble.UserState msg) {
        if (!mInitialized)
            return; // We shouldn't initialize on UserState- wait for ServerSync.

        // Stop audio input if the user is muted, and resume if the user has set talking enabled.
        if (msg.hasSession() && msg.getSession() == mSession &&
                (msg.hasMute() || msg.hasSelfMute() || msg.hasSuppress())) {
            try {
                setServerMuted(msg.getMute() || msg.getSelfMute() || msg.getSuppress());
            } catch (AudioException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void messageVoiceData(byte[] data, RimicUDPMessageType messageType) {
        synchronized (mOutput) {
            mOutput.queueVoiceData(data, messageType);
        }
    }

    @Override
    public void onAudioInputReceived(short[] frame, int frameSize) {
        boolean talking = mInputMode.shouldTransmit(frame, frameSize);
        talking &= !mMuted;

        if (mTalking ^ talking) {
            mEncodeListener.onTalkingStateChanged(talking);
            if (mHalfDuplex) {
                mAudioManager.setStreamMute(getAudioStream(), talking);
            }

            synchronized (mEncoderLock) {
                // Terminate encoding when talking stops.
                if (!talking && mEncoder != null) {
                    try {
                        mEncoder.terminate();
                    } catch (NativeAudioException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        if (talking) {
            // Boost/reduce amplitude based on user preference
            // TODO: perhaps amplify to the largest value that does not result in clipping.
            if (mAmplitudeBoost != 1.0f) {
                for (int i = 0; i < frameSize; i++) {
                    // Java only guarantees the bounded preservation of sign in a narrowing
                    // primitive conversion from float -> int, not float -> int -> short.
                    float val = frame[i] * mAmplitudeBoost;
                    if (val > Short.MAX_VALUE) {
                        val = Short.MAX_VALUE;
                    } else if (val < Short.MIN_VALUE) {
                        val = Short.MIN_VALUE;
                    }
                    frame[i] = (short) val;
                }
            }

            synchronized (mEncoderLock) {
                if (mEncoder != null) {
                    try {
                        mEncoder.encode(frame, frameSize);
                        mFrameCounter++;
                    } catch (NativeAudioException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        synchronized (mEncoderLock) {
            if (mEncoder != null && mEncoder.isReady()) {
                sendEncodedAudio();
            }
        }

        mTalking = talking;
        if (!talking) {
            mInputMode.waitForInput();
        }
    }

    public void setVoiceTargetId(byte id) {
        mTargetId = id;
    }

    public void clearVoiceTarget() {
        // A target ID of 0 indicates normal talking.
        mTargetId = 0;
    }

    /**
     * Fetches the buffered audio from the current encoder and sends it to the server.
     */
    private void sendEncodedAudio() {
        int frames = mEncoder.getBufferedFrames();

        int flags = 0;
        flags |= mCodec.ordinal() << 5;
        flags |= mTargetId & 0x1F;

        final byte[] packetBuffer = new byte[1024];
        packetBuffer[0] = (byte) (flags & 0xFF);

        PacketBuffer ds = new PacketBuffer(packetBuffer, 1024);
        ds.skip(1);
        ds.writeLong(mFrameCounter - frames);
        mEncoder.getEncodedData(ds);
        int length = ds.size();
        ds.rewind();

        byte[] packet = ds.dataBlock(length);
        mEncodeListener.onAudioEncoded(packet, length);
    }

    public interface AudioEncodeListener {
        void onAudioEncoded(byte[] data, int length);
        void onTalkingStateChanged(boolean talking);
    }

    /**
     * A builder to configure and instantiate the audio protocol handler.
     */
    public static class Builder {
        private Context mContext;
        private RimicLogger mLogger;
        private int mAudioStream;
        private int mAudioSource;
        private int mTargetBitrate;
        private int mTargetFramesPerPacket;
        private int mInputSampleRate;
        private float mAmplitudeBoost;
        private boolean mBluetoothEnabled;
        private boolean mHalfDuplexEnabled;
        private boolean mPreprocessorEnabled;
        private IInputMode mInputMode;
        private AudioEncodeListener mEncodeListener;
        private AudioOutput.AudioOutputListener mTalkingListener;

        public Builder setContext(Context context) {
            mContext = context;
            return this;
        }

        public Builder setLogger(RimicLogger logger) {
            mLogger = logger;
            return this;
        }

        public Builder setAudioStream(int audioStream) {
            mAudioStream = audioStream;
            return this;
        }

        public Builder setAudioSource(int audioSource) {
            mAudioSource = audioSource;
            return this;
        }

        public Builder setTargetBitrate(int targetBitrate) {
            mTargetBitrate = targetBitrate;
            return this;
        }

        public Builder setTargetFramesPerPacket(int targetFramesPerPacket) {
            mTargetFramesPerPacket = targetFramesPerPacket;
            return this;
        }

        public Builder setInputSampleRate(int inputSampleRate) {
            mInputSampleRate = inputSampleRate;
            return this;
        }

        public Builder setAmplitudeBoost(float amplitudeBoost) {
            mAmplitudeBoost = amplitudeBoost;
            return this;
        }

        public Builder setBluetoothEnabled(boolean bluetoothEnabled) {
            mBluetoothEnabled = bluetoothEnabled;
            return this;
        }

        public Builder setHalfDuplexEnabled(boolean halfDuplexEnabled) {
            mHalfDuplexEnabled = halfDuplexEnabled;
            return this;
        }

        public Builder setPreprocessorEnabled(boolean preprocessorEnabled) {
            mPreprocessorEnabled = preprocessorEnabled;
            return this;
        }

        public Builder setEncodeListener(AudioEncodeListener encodeListener) {
            mEncodeListener = encodeListener;
            return this;
        }

        public Builder setTalkingListener(AudioOutput.AudioOutputListener talkingListener) {
            mTalkingListener = talkingListener; // TODO: remove user dependency from AudioOutput
            return this;
        }

        public Builder setInputMode(IInputMode inputMode) {
            mInputMode = inputMode;
            return this;
        }

        /**
         * Creates a new AudioHandler for the given session and begins managing input/output.
         * @return An initialized audio handler.
         */
        public AudioHandler initialize(User self, int maxBandwidth, RimicUDPMessageType codec, byte targetId) throws AudioException {
            AudioHandler handler = new AudioHandler(mContext, mLogger, mAudioStream, mAudioSource,
                    mInputSampleRate, mTargetBitrate, mTargetFramesPerPacket, mInputMode, targetId,
                    mAmplitudeBoost, mBluetoothEnabled, mHalfDuplexEnabled,
                    mPreprocessorEnabled, mEncodeListener, mTalkingListener);
            handler.initialize(self, maxBandwidth, codec);
            return handler;
        }
    }
}
