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

package moe.maple.scheduler.tasks.delay;

import moe.maple.scheduler.tasks.MoeTask;

public class MoeDelayedTask implements MoeTask {

    private final MoeTask actual;

    private long start;
    private final long delay;

    private boolean hasRun;

    public MoeDelayedTask(MoeTask actual, long delay, long start) {
        if (actual == null)
            throw new IllegalArgumentException("Actual task is set to null.");
        this.actual = actual;
        this.delay = delay;
        this.start = start;
    }

    public MoeDelayedTask(MoeTask actual, long delay) {
        this(actual, delay, System.currentTimeMillis());
    }

    @Override
    public boolean isEventAsync() {
        return actual.isEventAsync();
    }

    @Override
    public boolean isEventDone() {
        return hasRun && actual.isEventDone();
    }

    @Override
    public void update(long currentTime) {
        if (!hasRun && currentTime - start >= delay) {
            actual.update(currentTime);
            start = System.currentTimeMillis();
            hasRun = true;
        }
    }
}
