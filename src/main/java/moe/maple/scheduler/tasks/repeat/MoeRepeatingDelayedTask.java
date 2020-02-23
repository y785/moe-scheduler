/*
 * Copyright (C) 2020, y785, http://github.com/y785
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

package moe.maple.scheduler.tasks.repeat;

import moe.maple.scheduler.tasks.MoeTask;

import java.util.function.BooleanSupplier;

public class MoeRepeatingDelayedTask extends MoeRepeatingTask implements MoeTask {

    private final long delay;
    private long start;

    private boolean ran;

    public MoeRepeatingDelayedTask(MoeTask delegate, boolean always, long delay, long start) {
        super(delegate, always);
        this.delay = delay;
        this.start = start;
    }

    public MoeRepeatingDelayedTask(MoeTask delegate, BooleanSupplier isDoneSupplier, long delay, long start) {
        super(delegate, isDoneSupplier);
        this.delay = delay;
        this.start = start;
    }

    @Override
    public boolean isEventDone() {
        return ran && super.isEventDone();
    }

    @Override
    public void update(long currentTime) {
        if (currentTime - start >= delay) {
            super.update(currentTime);
            start = currentTime;
            ran = true;
        }
    }
}
