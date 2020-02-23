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

package moe.maple.scheduler.tasks.retry;

import moe.maple.scheduler.tasks.MoeTask;

import java.util.function.Consumer;

/**
 * A retry task is a task that will update a {@link MoeTask} delegate,
 * catching all exceptions. This task's {@link #isEventDone} is not set
 * until the delegated Task has run successfully once or the max retry amount
 * has been reached.
 */
public class MoeRetryTask implements MoeTask {

    private final MoeTask delegate;
    private final MoeTask onFailure;
    private final Consumer<Throwable> failureHandler;

    private boolean ran;
    private int tries, maxTries;

    public MoeRetryTask(MoeTask delegate) {
        this(delegate, 2);
    }

    public MoeRetryTask(MoeTask delegate, int maxTries) {
        this(delegate, maxTries, MoeTask.EMPTY_TASK, Throwable::printStackTrace);
    }

    public MoeRetryTask(MoeTask delegate,
                        int maxTries,
                        MoeTask onFailure) {
        this(delegate, maxTries, onFailure, Throwable::printStackTrace);
    }

    public MoeRetryTask(MoeTask delegate,
                        int maxTries,
                        MoeTask onFailure,
                        Consumer<Throwable> exceptionHandler) {
        if (delegate == null)
            throw new IllegalArgumentException("Delegated task is null.");
        if (onFailure == null)
            throw new NullPointerException("Failure event handler is null.");
        if (exceptionHandler == null)
            throw new NullPointerException("Exception handler is null.");
        this.delegate = delegate;
        this.maxTries = maxTries;
        this.onFailure = onFailure;
        this.failureHandler = exceptionHandler;
    }

    @Override
    public boolean isEventAsync() {
        return false;
    }

    @Override
    public boolean isEventDone() {
        return ran && ((tries >= maxTries) || delegate.isEventDone());
    }

    @Override
    public void update(long currentTime) {
        if (isEventDone())
            return;
        tries++;
        try {
            delegate.update(currentTime);
            ran = true;
        } catch (Exception e) {
            // Only accept the exception IF we have failed all attempts :(
            if (tries >= maxTries)
                failureHandler.accept(e);
        }
        if (tries >= maxTries && !delegate.isEventDone())
            onFailure.update(currentTime);
    }
}
