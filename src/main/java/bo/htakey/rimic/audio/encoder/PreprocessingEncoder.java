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

package bo.htakey.rimic.audio.encoder;

import com.googlecode.javacpp.IntPointer;
import com.googlecode.javacpp.Pointer;

import java.nio.BufferUnderflowException;

import bo.htakey.rimic.audio.javacpp.Speex;
import bo.htakey.rimic.exception.NativeAudioException;
import bo.htakey.rimic.net.PacketBuffer;

/**
 * Wrapper performing preprocessing options on the nested encoder.
 * Uses Speex preprocessor.
 * Created by andrew on 17/04/14.
 *
 * Added echo canceller, fixed VAD and AGC sampling rate.
 * Updated by hiroshi on 23/08/2020
 */
public class PreprocessingEncoder implements IEncoder {
    private IEncoder mEncoder;
    private Speex.SpeexPreprocessState mPreprocessor;
    public static Speex.SpeexEchoState mEcho;

    public PreprocessingEncoder(IEncoder encoder, int frameSize, int sampleRate) {
        mEncoder = encoder;
        mPreprocessor = new Speex.SpeexPreprocessState(frameSize, sampleRate);
        mEcho = new Speex.SpeexEchoState(frameSize, frameSize * 24);

        IntPointer arg = new IntPointer(1);

        arg.put(sampleRate);
        mEcho.control(Speex.SpeexEchoState.SPEEX_ECHO_SET_SAMPLING_RATE, arg);
        Pointer mpEcho = mEcho.getPointer();
        mPreprocessor.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_SET_ECHO_STATE, mpEcho);
        arg.put(-60);
        mPreprocessor.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_GET_ECHO_SUPPRESS, arg);
        arg.put(-60);
        mPreprocessor.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_GET_ECHO_SUPPRESS_ACTIVE, arg);

        arg.put(0);
        mPreprocessor.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_SET_VAD, arg);
        arg.put(1);
        mPreprocessor.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_SET_AGC, arg);
        arg.put(1);
        mPreprocessor.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_SET_DENOISE, arg);
        arg.put(1);
        mPreprocessor.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_SET_DEREVERB, arg);

        arg.put(9000);
        mPreprocessor.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_SET_AGC_TARGET, arg);
    }

    @Override
    public int encode(short[] input, int inputSize) throws NativeAudioException {
        short[] out_buf = new short[inputSize];
        mEcho.reset_echo();
        mEcho.echo_capture(input, out_buf);
        mPreprocessor.preprocess(out_buf);
        return mEncoder.encode(out_buf, inputSize);
    }

    @Override
    public int getBufferedFrames() {
        return mEncoder.getBufferedFrames();
    }

    @Override
    public boolean isReady() {
        return mEncoder.isReady();
    }

    @Override
    public void getEncodedData(PacketBuffer packetBuffer) throws BufferUnderflowException {
        mEncoder.getEncodedData(packetBuffer);
    }

    @Override
    public void terminate() throws NativeAudioException {
        mEncoder.terminate();
    }

    public void setEncoder(IEncoder encoder) {
        if(mEncoder != null) mEncoder.destroy();
        mEncoder = encoder;
    }

    @Override
    public void destroy() {
        mPreprocessor.destroy();
        mEncoder.destroy();
        mPreprocessor = null;
        mEncoder = null;
    }
}
