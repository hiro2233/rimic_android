/*
 * Copyright (C) 2016 Andrew Comminos <andrew@comminos.com>
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

package bo.htakey.rimic.audio.inputmode;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An input mode that depends on a toggle, such as push to talk.
 * Created by andrew on 13/02/16.
 */
public class ToggleInputMode implements IInputMode {
    private boolean mInputOn;
    private final Lock mToggleLock;

    public ToggleInputMode() {
        mInputOn = false;
        mToggleLock = new ReentrantLock();
    }

    public void toggleTalkingOn() {
        setTalkingOn(!mInputOn);
    }

    public boolean isTalkingOn() {
        return mInputOn;
    }

    public void setTalkingOn(boolean talking) {
        mToggleLock.lock();
        mInputOn = talking;
        mToggleLock.unlock();
    }

    @Override
    public boolean shouldTransmit(short[] pcm, int length) {
        return mInputOn;
    }

    @Override
    public void waitForInput() {
        if (!mInputOn) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                //e.printStackTrace();
            }
        }
    }
}
