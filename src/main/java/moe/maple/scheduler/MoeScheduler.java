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
import moe.maple.scheduler.tasks.repeat.MoeRepeatingDelayedTask;
import moe.maple.scheduler.tasks.repeat.MoeRepeatingTask;
import moe.maple.scheduler.tasks.repeat.MoeRepeatingTickTask;
import moe.maple.scheduler.tasks.retry.MoeAsyncRetryTask;
import moe.maple.scheduler.tasks.retry.MoeRetryTask;
import moe.maple.scheduler.tasks.tick.MoeTickTask;

import java.util.Collection;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface MoeScheduler {

    int DEFAULT_PERIOD = 20;
    int DEFAULT_THREAD_COUNT = Runtime.getRuntime().availableProcessors();

    /**
     * See {@link #isSchedulerThread()}
     * @param thread - The thread to check.
     * @return       - TRUE IF THE CURRENT THREAD IS THE SAME AS THE UPDATE LOOP.
     */
    boolean isSchedulerThread(Thread thread);

    /**
     * Used to determine if this current thread is the update loop thread.
     * @return - TRUE IF THE CURRENT THREAD IS THE SAME AS THE UPDATE LOOP.
     */
    default boolean isSchedulerThread() {
        return isSchedulerThread(Thread.currentThread());
    }

    /**
     * Used to check if the scheduler is currently active.
     * @return - TRUE IF UPDATE LOOP IS RUNNING.
     */
    default boolean isRunning() {
        return !isStopped();
    }

    /**
     * See {@link #isRunning()}
     * @return - TRUE IF UPDATE LOOP IS NOT RUNNING.
     */
    boolean isStopped();

    /**
     * Casts the current scheduler as an executor.
     * Used primarily to share this single-threaded scheduler with libraries, etc.
     * @return - The current scheduler as an Executor
     */
    Executor asExecutor();

    /**
     * See {@link #asExecutor()}
     * @return - The current scheduler as an ExecutorService
     */
    ExecutorService asExecutorService();

    /**
     * See {@link #asExecutor()}
     * @return - The current scheduler as an ExecutorService
     */
    ScheduledExecutorService asScheduledExecutorService();

    /**
     * see {@link SchedulerStats}
     * @return The stats object attached to this scheduler.
     */
    SchedulerStats stats();

    /**
     * Returns all tasks currently residing in the scheduler to be processed.
     * @return - The size of the current task queue.
     */
    int size();

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
     * @param supplier - The Object Supplier to be run asynchronously.
     */
    <T> T awaitAsync(Supplier<T> supplier);

    /**
     * See {@link #awaitAsync(Collection)}.
     * @param tasks             - The tasks to submit
     * @param exceptionConsumer - The exception consumer.
     */
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

    /**
     * This method will fetch an object <strong>asynchronously</strong> through the supplier,
     * but consumer it <strong>synchronously</strong> on the update loop.
     * @param supplier - The supplier to run asynchronously.
     * @param consumer - The consumer of the supplier, to be run synchronously
     *                 - on the update thread.
     */
    default <T> void future(Supplier<T> supplier, Consumer<T> consumer) {
        registerAsync(((d1) -> {
            var a = supplier.get();
            register(d2 -> consumer.accept(a));
        }));
    }

    /**
     * See {@link #future(Supplier, Consumer)}
     * @param exceptionHandler - The exception handler for supplier OR consumer exceptions.
     */
    default <T> void future(Supplier<T> supplier, Consumer<T> consumer, Consumer<Throwable> exceptionHandler) {
        registerAsync(((d1) -> {
            try {
                var supplied = supplier.get();
                register(d2 -> {
                    try {
                        consumer.accept(supplied);
                    } catch (Exception consumerException) {
                        exceptionHandler.accept(consumerException);
                    }
                });
            } catch (Exception supplierException) {
                exceptionHandler.accept(supplierException);
            }
        }));
    }

    /**
     * See {@link MoeRetryTask}.
     * tldr: "Well, if an exception is thrown or something breaks,
     *        just keep retrying until it works."
     * @param task             - The task to run.
     * @param maxTries         - The maximum attempt count.
     */
    default void retry(MoeTask task,
                       int maxTries) {
        retry(task, maxTries, MoeTask.EMPTY_TASK);
    }

    /**
     * Async variant, see {@link #retry(MoeTask, int)}
     */
    default void retryAsync(MoeTask task,
                       int maxTries) {
        retryAsync(task, maxTries, MoeTask.EMPTY_TASK);
    }

    /**
     * See {@link MoeRetryTask}.
     * @param task             - The task to run.
     * @param maxTries         - The maximum attempt count.
     * @param onFailure        - The task to run if this task fails.
     */
    default void retry(MoeTask task,
                       int maxTries,
                       MoeTask onFailure) {
        retry(task, maxTries, onFailure, (e) -> {});
    }

    /**
     * Async variant, see {@link #retry(MoeTask, int, MoeTask)}
     */
    default void retryAsync(MoeTask task,
                       int maxTries,
                       MoeTask onFailure) {
        retryAsync(task, maxTries, onFailure, (e) -> {});
    }

    /**
     * See {@link MoeRetryTask}.
     * @param task             - The task to run.
     * @param maxTries         - The maximum attempt count.
     * @param onFailure        - The task to run if this task fails.
     * @param exceptionHandler - The exception handler called on last exception thrown.
     */
    default void retry(MoeTask task,
                       int maxTries,
                       MoeTask onFailure,
                       Consumer<Throwable> exceptionHandler) {
        register(new MoeRetryTask(task, maxTries, onFailure, exceptionHandler));
    }

    /**
     * Async variant, see {@link #retry(MoeTask, int, MoeTask, Consumer)}
     */
    default void retryAsync(MoeTask task,
                       int maxTries,
                       MoeTask onFailure,
                       Consumer<Throwable> exceptionHandler) {
        register(new MoeAsyncRetryTask(task, maxTries, onFailure, exceptionHandler));
    }

    /**
     * Retry variant of {@link #future(Supplier, Consumer)}.
     * @param supplier - Supplier of a NON-NULL VALUE.
     */
    default <T> void retryFuture(Supplier<T> supplier, Consumer<T> consumer, int maxTries) {
        retryAsync((ct1) -> {
            final var a = supplier.get();
            if (a == null)
                throw new NullPointerException("Supplier gave null value. :(");
            retry(ct2 -> consumer.accept(a), maxTries);
        },  maxTries);
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

    default void registerRepeating(MoeTask original, boolean always) {
        register(new MoeRepeatingTask(original, always));
    }

    default void registerRepeating(MoeTask original, BooleanSupplier isDoneSupplier) {
        register(new MoeRepeatingTask(original, isDoneSupplier));
    }

    default void registerRepeatingDelay(MoeTask original, boolean always, long delay) {
        registerRepeatingDelay(original, always, delay, System.currentTimeMillis());
    }

    default void registerRepeatingDelay(MoeTask original, BooleanSupplier isDoneSupplier, long delay) {
        registerRepeatingDelay(original, isDoneSupplier, delay, System.currentTimeMillis());
    }

    default void registerRepeatingDelay(MoeTask original, boolean always, long delay, long start) {
        register(new MoeRepeatingDelayedTask(original, always, delay, start));
    }

    default void registerRepeatingDelay(MoeTask original, BooleanSupplier isDoneSupplier, long delay, long start) {
        register(new MoeRepeatingDelayedTask(original, isDoneSupplier, delay, start));
    }

    default void registerRepeatingTick(MoeTask original, boolean always, long delay) {
        register(new MoeRepeatingTickTask(original, always, delay));
    }

    default void registerRepeatingTick(MoeTask original, BooleanSupplier isDoneSupplier, long delay) {
        register(new MoeRepeatingTickTask(original, isDoneSupplier, delay));
    }

    default void registerTick(MoeTask original, long ticks) {
        register(new MoeTickTask(original, ticks));
    }

    default void registerTick(Runnable runnable, long ticks) {
        registerTick((delta) -> runnable.run(), ticks);
    }

    /**
     * Registers a task in the scheduler. Tasks will be auto-removed if the
     * task is finished. Tasks are synchronous and ran based on insertion into the scheduler.
     * To determine if a task is finished, the scheduler checks the {@link MoeTask#isEventDone()}
     * method. By default, all tasks will run once. To determine if a task is synchronous
     * or asynchronous, the scheduler checks the {@link MoeTask#isEventAsync()} method. By default,
     * all tasks are synchronous.
     *
     * See {@link MoeRepeatingTask} or {@link #registerRepeating(MoeTask, boolean)} for
     * tasks that need to be run continuously.
     * @param task - The task to register.
     */
    void register(MoeTask task);

    default void register(Runnable runnable) {
        register((delta) -> runnable.run());
    }

    /**
     * Removes tasks based on certain conditions.
     * If <code>check</code> returns true, the task will be removed.
     * @param check - The removal check.
     */
    void remove(Predicate<MoeTask> check);

    /**
     * Starts the scheduler. MUST BE CALLED FOR THE SCHEDULER TO RUN.
     */
    void start();

    /**
     * Stops the scheduler. WILL CLEAR ALL TASKS REGISTERED.
     */
    void stop();

    /**
     * Creates a new scheduler with default values.
     * @return - A new scheduler instance.
     */
    static MoeScheduler newScheduler() {
        return new MoeBasicScheduler();
    }

    /**
     * Creates a new scheduler builder.
     * @return - A new scheduler builder.
     */
    static Builder newBuilder() {
        return new Builder();
    }

    final class Builder {
        private Consumer<Throwable> exceptionHandler;
        private String threadPrefix;
        private int delay, period;

        private int threadCount, statCount;

        private MoeRollingStats stats;
        private ThreadFactory threadFactory;
        private ScheduledExecutorService executor;
        private ScheduledExecutorService asyncExecutor;


        public Builder execptionHandler(Consumer<Throwable> exceptionHandler) {
            this.exceptionHandler = exceptionHandler;
            return this;
        }

        public Builder threadPrefix(String threadPrefix) {
            this.threadPrefix = threadPrefix;
            return this;
        }

        public Builder delay(int delay) {
            this.delay = delay;
            return this;
        }

        public Builder period(int period) {
            this.period = period;
            return this;
        }

        public Builder threadCount(int count) {
            this.threadCount = count;
            return this;
        }

        public Builder statCount(int count) {
            this.statCount = count;
            return this;
        }

        public MoeScheduler build() {
            if (exceptionHandler == null)
                this.exceptionHandler = Throwable::printStackTrace;
            if (threadPrefix == null || threadPrefix.isBlank())
                this.threadPrefix = "moe";
            if (delay <= 0)
                delay = 0;
            if (period <= 0)
                period = DEFAULT_PERIOD;
            if (threadCount <= 0)
                threadCount = DEFAULT_THREAD_COUNT;
            if (statCount <= 0)
                statCount = 3000; // PERIOD * COUNT = 60_000ms

            if (stats == null)
                stats = new MoeRollingStats(period, statCount);
            if (threadFactory == null)
                threadFactory = new MoeBasicThreadFactory(threadPrefix);
            if (executor == null)
                this.executor = Executors.newSingleThreadScheduledExecutor(threadFactory);
            if (asyncExecutor == null)
                this.asyncExecutor = new ScheduledThreadPoolExecutor(threadCount, threadFactory);

            return new MoeBasicScheduler(exceptionHandler,
                    threadPrefix,
                    delay,
                    period,
                    stats,
                    threadFactory,
                    executor,
                    asyncExecutor);
        }
    }
}
