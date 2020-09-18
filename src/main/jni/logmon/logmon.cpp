/*
    Urus Service Manager - Service manager for URUS system.
    Copyright (c) 2015-2019 Hiroshi Takey F. (hiro2233) <htakey@gmail.com>

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA

    You can receive a copy of the GNU Lesser General Public License from
    <https://www.gnu.org/licenses/>.
 */

/*
    This file is a part from urus service manager, and isn't inside the rimic project, only for
    monitor test purposes.
*/

#include "logmon.h"
#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <fcntl.h>
#include <pthread.h>
#include <sys/times.h>
#include <sys/time.h>
#include <time.h>

#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, "Rimic:Logmon", __VA_ARGS__))

static volatile bool running = false;
static volatile bool sw = 0;

static struct {
    struct timeval start_time;
} state_tv;

uint64_t _micros64tv()
{
    struct timeval tp;
    gettimeofday(&tp, nullptr);
    uint64_t ret = 1.0e6 * ((tp.tv_sec + (tp.tv_usec * 1.0e-6)) -
                            (state_tv.start_time.tv_sec +
                             (state_tv.start_time.tv_usec * 1.0e-6)));
    return ret;
}

static void *_fire_isr_timer(void *args)
{
    if (sw) {
        return NULL;
    }

    LOGI("Started! = %d ", running);
    sw = true;
    uint64_t start_time = 0;
    while(sw) {
        usleep(1000000);
        if ((_micros64tv() - start_time) > 60000000LL) {
            start_time = _micros64tv();
            system("am broadcast -a bo.htakey.rimic.RimicWakeUpMon.WAKE_UP_ACTION_MON --user 0");
        }
    }

    running = false;
    LOGI("Stopped! = %d ", running);
    return NULL;
}

int main_test(int argc, char *argv[])
{
    gettimeofday(&state_tv.start_time, nullptr);

    LOGI("Configuring timers = %d ", running);
    signal(SIGPIPE, SIG_IGN);

    pthread_t isr_timer_thread;
    pthread_attr_t thread_attr_timer;

    pthread_attr_init(&thread_attr_timer);
    pthread_attr_setdetachstate(&thread_attr_timer, PTHREAD_CREATE_DETACHED);
    pthread_attr_setstacksize(&thread_attr_timer, 2048);

    pthread_attr_setschedpolicy(&thread_attr_timer, SCHED_FIFO);
    pthread_create(&isr_timer_thread, &thread_attr_timer, &_fire_isr_timer, NULL);
    pthread_attr_destroy(&thread_attr_timer);

    return 0;
}

bool start_ticks(int x, int y)
{
    if (running) {
        LOGI("Already running!!! %d", running);
        return running;
    }

    LOGI("\n\nData: %d - %d\n\n", x, y);
    running = true;
    int data1 = main_test(0, NULL);
    LOGI("All ok: %d", running);

    usleep(10000);
    return running;
}

bool stop_ticks()
{
    sw = false;
    LOGI("Stopping timer = %d ", running);
    return running;
}
