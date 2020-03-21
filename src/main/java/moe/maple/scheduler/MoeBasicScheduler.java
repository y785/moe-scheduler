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

import java.util.Queue;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class MoeBasicScheduler implements MoeScheduler {

    private final String name;
    private final int delay, period;

    private final ThreadFactory factory;
    private ScheduledExecutorService executor;
    private final ScheduledExecutorService asyncExecutor;

    private final MoeRollingStats stats;
    private final Queue<MoeTask> registry;

    private ScheduledFuture<?> updateLoop;
    private Thread updateThread;

    private MoeTask lastTask;
    private long lastUpdate;

    private Consumer<Throwable> exceptionHandler;

    public MoeBasicScheduler(Consumer<Throwable> exceptionHandler,
                             String name,
                             int delay,
                             int period,
                             MoeRollingStats stats,
                             ThreadFactory threadFactory,
                             ScheduledExecutorService executor,
                             ScheduledExecutorService asyncExecutor) {
        this.exceptionHandler = exceptionHandler;
        this.name = name;
        this.delay = delay;
        this.period = period;
        this.stats = stats;
        this.factory = threadFactory;
        this.executor = executor;
        this.asyncExecutor = asyncExecutor;
        this.registry = new ConcurrentLinkedQueue<>();
    }

    public MoeBasicScheduler(Consumer<Throwable> exceptionHandler,
                             String name,
                             int delay,
                             int period,
                             MoeRollingStats stats,
                             ThreadFactory threadFactory) {
        this(exceptionHandler, name, delay, period, stats, threadFactory, Executors.newSingleThreadScheduledExecutor(threadFactory), new ScheduledThreadPoolExecutor(MoeScheduler.DEFAULT_THREAD_COUNT, threadFactory));
    }

    public MoeBasicScheduler(Consumer<Throwable> exceptionHandler, String name, int delay, int period) {
        this(exceptionHandler, name, delay, period, new MoeRollingStats(period), new MoeBasicThreadFactory());
    }

    public MoeBasicScheduler(Consumer<Throwable> exceptionHandler, String name) {
        this(exceptionHandler, name, 0, MoeScheduler.DEFAULT_PERIOD);
    }

    public MoeBasicScheduler(Consumer<Throwable> exceptionHandler) {
        this(exceptionHandler, "moe", 0, MoeScheduler.DEFAULT_PERIOD);
    }

    public MoeBasicScheduler() {
        this(Throwable::printStackTrace, "moe");
    }

    @Override
    public Executor asExecutor() {
        return executor;
    }

    @Override
    public ExecutorService asExecutorService() {
        return executor;
    }

    @Override
    public ScheduledExecutorService asScheduledExecutorService() {
        return executor;
    }

    @Override
    public boolean isSchedulerThread(Thread thread) {
        if (thread == null)
            throw new NullPointerException("Thread is null. :(");
        return thread.equals(updateThread);
    }

    @Override
    public boolean isStopped() {
        return updateLoop == null || (updateLoop.isCancelled() || updateLoop.isDone());
    }

    @Override
    public SchedulerStats stats() {
        return stats;
    }

    @Override
    public int size() {
        return registry.size();
    }

    @Override
    public void register(MoeTask task) {
        if (task == null)
            throw new NullPointerException("Cannot register a null task.");
        registry.add(task);
    }

    @Override
    public void remove(Predicate<MoeTask> check) {
        registry.removeIf(check);
    }

    @Override
    public <T> T await(Supplier<T> supplier) {
        if (isSchedulerThread()) return supplier.get();
        else return CompletableFuture.supplyAsync(supplier, executor).getNow(null);
    }

    @Override
    public <T> T awaitAsync(Supplier<T> supplier) {
        if (isSchedulerThread()) return supplier.get();
        return CompletableFuture.supplyAsync(supplier, asyncExecutor).getNow(null);
    }

    private void createLoop() {
        lastUpdate = System.currentTimeMillis();
        updateLoop = executor.scheduleAtFixedRate(() -> {
            final var currentTime = System.currentTimeMillis();
            update(currentTime);
            lastUpdate = currentTime;
            stats.update(currentTime);
        }, delay, period, TimeUnit.MILLISECONDS);
        register(() -> updateThread = Thread.currentThread());
    }

    private void restartLoop() {
        executor.shutdownNow();
        executor = Executors.newSingleThreadScheduledExecutor(factory);
        createLoop();
    }

    @Override
    public void start() {
        if (updateLoop != null)
            throw new IllegalStateException("Scheduler has already started.");
        asyncExecutor.scheduleAtFixedRate(new DeadlockDetector(), 10_000, 5_000, TimeUnit.MILLISECONDS);
        createLoop();
    }

    @Override
    public void stop() {
        registry.clear();
        if (!updateLoop.cancel(true))
            throw new IllegalThreadStateException("Couldn't cancel update loop.");
        executor.shutdown();
        asyncExecutor.shutdown();
        updateLoop = null;
    }

    protected void update(MoeTask task, long currentTime) {
        if (task.isEventAsync()) {
            asyncExecutor.submit(() -> {
                try {
                    task.update(currentTime);
                } catch (Exception e) { exceptionHandler.accept(e); }
            });
        } else {
            try {
                this.lastTask = task;
                task.update(currentTime);
            } catch (Exception e) { exceptionHandler.accept(e); }
        }
    }

    protected void update(long currentTime) {
        final var iter = registry.iterator();
        while (iter.hasNext()) {
            final var task = iter.next();
            update(task, currentTime);
            if (task.isEventDone())
                iter.remove();
        }
    }

    @Override
    public String toString() {
        var prefix = String.format("[ %s-scheduler ]", name);
        var sb = new StringBuilder()
                .append(prefix).append(" Tasks: ").append(registry.size())
                .append(", Executor: ").append(executor.isTerminated())
                .append(", Async: ").append(asyncExecutor.isTerminated())
                .append("\r\n")
                .append(prefix).append(" Telescope: ").append(stats);
        return sb.toString();
    }

    private final class DeadlockDetector implements Runnable {
        @Override
        public void run() {
            final var currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdate >= 5_000 && updateThread != null) {
                var stack = updateThread.getStackTrace(); // Arrays.toString is ugly :(
                var sb = new StringBuilder();
                for (var s : stack) sb.append(s.toString()).append("\r\n");

                exceptionHandler.accept(new SchedulerException("REMOVING THE LAST TASK THAT RAN FROM THE QUEUE. Update loop is broke, last update was over 5 seconds ago! StackTrace of presumed loop thread:\r\n"+sb.toString()));
                // Loop thread needs to be reset. :(

                // Remove the last task that ran. We assume this task is the cause of the deadlock.
                registry.removeIf((mt) -> mt.equals(lastTask));

                // Restart the loop.
                restartLoop();
            }
        }
    }
}
