package bo.htakey.rimic.audio;

/**
 * A source for an {@link IAudioMixer}.
 * Stores samples in a collection of type {@link T}.
 */
public interface IAudioMixerSource<T> {
    T getSamples();
    int getNumSamples();
}
