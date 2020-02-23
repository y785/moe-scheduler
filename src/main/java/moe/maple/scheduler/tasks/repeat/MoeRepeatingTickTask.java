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

public class MoeRepeatingTickTask extends MoeRepeatingTask implements MoeTask {

    private final long tickCount;
    private long iteration;
    private boolean ran;

    public MoeRepeatingTickTask(MoeTask delegate, boolean always, long tickCount) {
        super(delegate, always);
        this.tickCount = tickCount;
    }

    public MoeRepeatingTickTask(MoeTask delegate, BooleanSupplier isDoneSupplier, long tickCount) {
        super(delegate, isDoneSupplier);
        this.tickCount = tickCount;
    }

    @Override
    public boolean isEventDone() {
        return ran && super.isEventDone();
    }

    @Override
    public void update(long currentTime) {
        iteration++;

        if (iteration >= tickCount) {
            super.update(currentTime);
            ran = true;
            iteration = 0;
        }
    }
}
