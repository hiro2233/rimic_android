package bo.htakey.rimic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import java.util.Calendar;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class RimicWakeUpMon extends BroadcastReceiver {
    public static final String WAKE_UP_ACTION_MON = "bo.htakey.rimic.RimicWakeUpMon.WAKE_UP_ACTION_MON";

    //private static final ToneGenerator tn = new ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME / 2);
    private static final Lock vObjectLockTone = new ReentrantLock();
    private static final Object vObjectLockDelay = new Object();

    private void fireBroadcast(Context context) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        Log.v(Constants.TAG, "Wake up at - " + calendar.getTime().toGMTString());
    }

    public void delay(long millis, int type)
    {
        synchronized (vObjectLockDelay) {
            if (type == 0) {
                long now = System.currentTimeMillis();
                while ((System.currentTimeMillis() - now) < millis) {
                    SystemClock.sleep(1);
                }
            } else if (type == 1) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    //Restore interrupt status.
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void run_tones(final String action) {
        Handler mainHandler = new Handler();
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                ToneGenerator tn = new ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME / 2);
                if (RimicService.WAKE_UP_ACTION.equals(action)) {
                    tn.startTone(ToneGenerator.TONE_CDMA_INTERCEPT, 550);
                } else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                    tn.startTone(ToneGenerator.TONE_CDMA_PRESSHOLDKEY_LITE, 550);
                } else if (WAKE_UP_ACTION_MON.equals(action)) {
                    if (BuildConfig.DEBUG) {
                        tn.startTone(ToneGenerator.TONE_PROP_BEEP2, 300);
                    }
                } else {
                    tn.startTone(ToneGenerator.TONE_CDMA_ONE_MIN_BEEP, 550);
                }
                delay(600, 0);
                tn.stopTone();
                delay(250, 0);
            }
        });
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        fireBroadcast(context);
        int mistake_cnt = RimicService.getMistakeCntConn();
        if (RimicService.isInMistakeConnection() && mistake_cnt > 1) {
            Log.v(Constants.TAG, "Intent: Increasing mistake cnt: " + mistake_cnt);
            RimicService.increaseMistakeCntConn();
        }
        Log.v(Constants.TAG, "Intent: " + action);
        try {
            vObjectLockTone.lock();
            try {
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (am != null && !am.isMusicActive()) {
                    try {
                        run_tones(action);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        } finally {
            vObjectLockTone.unlock();
        }
    }
}
