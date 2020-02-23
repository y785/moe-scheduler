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

package moe.maple.scheduler.tasks;

public class MoeAsyncTask implements MoeTask {

    private final MoeTask delegate;
    private volatile boolean running;

    private final MoeTask onDone;

    public MoeAsyncTask(MoeTask actual) {
        this(actual, (d) -> { });
    }

    public MoeAsyncTask(MoeTask delegate, MoeTask onDone) {
        if (delegate == null)
            throw new IllegalArgumentException("Delegated task is null.");
        this.delegate = delegate;
        this.onDone = onDone;
    }

    @Override
    public boolean isEventAsync() {
        return true;
    }

    @Override
    public boolean isEventDone() {
        return delegate.isEventDone();
    }

    @Override
    public void update(long currentTime) {
        if (!running) {
            running = true;
            delegate.update(currentTime);
            if (delegate.isEventDone())
                onDone.update(currentTime);
            running = false;
        }
    }
}
