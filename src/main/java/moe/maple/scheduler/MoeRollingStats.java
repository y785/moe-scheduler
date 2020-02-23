/*
 * Copyright (C) 2019, y785, http://github.com/y785
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package moe.maple.scheduler;

import moe.maple.scheduler.tasks.MoeTask;

public class MoeRollingStats implements MoeTask, SchedulerStats {

    private long last;
    private int period;

    private int[] times;
    private int i, idx;

    private int sum;

    public MoeRollingStats(int period, int count) {
        this.period = period;
        this.times = new int[count];
    }

    public MoeRollingStats(int period) {
        this(period, 100);
    }

    @Override
    public int max() {
        var max = 0;
        for (int i = 0; i < times.length; i++) {
            var t = times[i];
            if (max < t)
                max = t;
        }
        return max;
    }

    @Override
    public float avg() {
        return sum * 1.0f / i;
    }

    private void roll(int time) {
        if (i < times.length)
            i++;
        sum -= times[idx];
        sum += time;
        times[idx++] = time;
        idx %= times.length;
    }

    @Override
    public void update(long currentTime) {
        if (last == 0L) {
            last = currentTime;
        } else {
            roll((int) Math.max(currentTime - last - period, 0));
            last = currentTime;
        }
    }

    @Override
    public String toString() {
        var max = max();
        var avg = avg();
        var prefix = avg >= period ? "Error! We're taking too long!" : avg >= period / 2 ? "We're over half load." : "Great! OwO";
        return String.format("%s Last average for %d ticks, max: %d, average: %.3f", prefix, times.length, max, avg);
    }
}
