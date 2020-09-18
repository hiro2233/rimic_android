package bo.htakey.rimic.audio.javacpp;

import com.googlecode.javacpp.Loader;
import com.googlecode.javacpp.annotation.Platform;

@Platform(library= "jnilogmon", include={"logmon.h"})
public class Logmon {
    static {
        Loader.load();
    }

    public static native boolean start_ticks(int x, int y);
    public static native boolean stop_ticks();

    public static class cLogMon {

        public boolean startTicks(int x, int y) {
            return start_ticks(x, y);
        }

        public boolean stopTicks() {
            return stop_ticks();
        }
    }
}
