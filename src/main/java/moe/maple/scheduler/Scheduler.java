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

import moe.maple.scheduler.tasks.AsyncTask;
import moe.maple.scheduler.tasks.delay.DelayedTask;
import moe.maple.scheduler.tasks.tick.TickTask;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface Scheduler {

    Telescope telescope();

    default <T> void future(Supplier<T> sup, Consumer<T> cons) {
        registerAsync(d1 -> {
            var a = sup.get();
            registerDelayed(d2 -> cons.accept(a), 0);
        });
    }

    default void registerAsync(Task original) {
        register(new AsyncTask(original));
    }

    default void registerDelayed(Task original, long delay, long start) {
        register(new DelayedTask(original, delay, start));
    }

    default void registerDelayed(Task original, long delay) {
        registerDelayed(original, delay, System.currentTimeMillis());
    }

    default void registerTick(Task original, long ticks) {
        register(new TickTask(original, ticks));
    }

    void register(Task update);

    void unregister(Task update);

    void remove(Predicate<Task> check);

    void start();

    void stop();

    int DEFAULT_PERIOD = 20;
}
