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

import moe.maple.scheduler.tasks.MoeAsyncTask;
import moe.maple.scheduler.tasks.MoeTask;
import moe.maple.scheduler.tasks.delay.MoeDelayedTask;
import moe.maple.scheduler.tasks.tick.MoeTickTask;

import java.util.Collection;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface MoeScheduler {

    SchedulerStats stats();

    boolean isSchedulerThread(Thread thread);

    Executor asExecutor();

    ExecutorService asExecutorService();

    ScheduledExecutorService asScheduledExecutorService();

    default boolean isSchedulerThread() {
        return isSchedulerThread(Thread.currentThread());
    }

    boolean isStopped();

    /**
     * Synchronously wait for the supplier result.
     * If the thread that calls this is on the main loop thread, supplier
     * is called instantly. This as a thread-safe supplier method.
     */
    <T> T await(Supplier<T> supplier);

    /**
     * See {@link #await(Supplier)} for the alternative approach.
     * Submit the supplier to the asynchronous pool.
     * If the thread that calls this is on the main loop thread, supplier
     * is called instantly.
     */
    <T> T awaitAsync(Supplier<T> supplier);

    default void awaitAsync(Collection<MoeTask> tasks, Consumer<InterruptedException> exceptionConsumer) {
        try {
            awaitAsync(tasks);
        } catch (InterruptedException e) {
            exceptionConsumer.accept(e);
        }
    }

    /**
     * Sometimes I have a group of tasks that I want to run asynchronously.
     * Sometimes I even want to wait for them to all finish before going forward.
     * This is my solution to the problem of knowing when all tasks are finished.
     * @param tasks                 - The tasks to submit and wait on finishing.
     * @throws InterruptedException - If the latch breaks for whatever reason.
     */
    default void awaitAsync(Collection<MoeTask> tasks) throws InterruptedException {
        final var finishLine = tasks.size();
        final var latch = new CountDownLatch(finishLine);
        tasks.forEach(t -> registerAsync(t, (d) -> latch.countDown()));
        latch.await();
    }

    default <T> void future(Supplier<T> sup, Consumer<T> cons) {
        registerAsync(((d1) -> {
            var a = sup.get();
            register(d2 -> cons.accept(a));
        }));
    }

    default void registerAsync(MoeTask original) {
        register(new MoeAsyncTask(original));
    }

    default void registerAsync(MoeTask original, MoeTask onDone) {
        register(new MoeAsyncTask(original, onDone));
    }

    default void registerAsync(Runnable runnable) {
        registerAsync((delta) -> runnable.run());
    }

    default void registerDelayed(MoeTask original, long delay, long start) {
        register(new MoeDelayedTask(original, delay, start));
    }

    default void registerDelayed(Runnable runnable, long delay, long start) {
        registerDelayed((delta) -> runnable.run(), delay, start);
    }

    default void registerDelayed(MoeTask original, long delay) {
        registerDelayed(original, delay, System.currentTimeMillis());
    }

    default void registerDelayed(Runnable runnable, long delay) {
        registerDelayed((delta) -> runnable.run(), delay);
    }

    default void registerTick(MoeTask original, long ticks) {
        register(new MoeTickTask(original, ticks));
    }

    default void registerTick(Runnable runnable, long ticks) {
        registerTick((delta) -> runnable.run(), ticks);
    }

    void register(MoeTask task);

    default void register(Runnable runnable) {
        register((delta) -> runnable.run());
    }

    void unregister(MoeTask task);

    void remove(Predicate<MoeTask> check);

    void start();

    void stop();

    int DEFAULT_PERIOD = 20;
    int THREADS = Runtime.getRuntime().availableProcessors();
}
