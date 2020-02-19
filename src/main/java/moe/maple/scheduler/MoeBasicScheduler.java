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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class MoeBasicScheduler implements MoeScheduler {
    
    private static final Logger log = LogManager.getLogger( MoeBasicScheduler.class );

    private final String name;
    private final int delay, period;

    private final MoeBasicThreadFactory factory;
    private ScheduledExecutorService executor;
    private final ScheduledExecutorService asyncExecutor;

    private final MoeRollingStats telescope;
    private final Set<MoeTask> registry;

    private ScheduledFuture<?> updateLoop;
    private Thread updateThread;
    private long lastUpdate;

    private Consumer<Exception> exceptionConsumer;

    public MoeBasicScheduler(Consumer<Exception> exceptionConsumer, String name, int delay, int period) {
        this.exceptionConsumer = exceptionConsumer;
        this.name = name;
        this.delay = delay;
        this.period = period;

        this.factory = new MoeBasicThreadFactory(name);
        this.executor = Executors.newSingleThreadScheduledExecutor(factory);
        this.asyncExecutor = new ScheduledThreadPoolExecutor(MoeScheduler.THREADS, factory);

        this.telescope = new MoeRollingStats(period);
        this.registry = ConcurrentHashMap.newKeySet();
    }

    public MoeBasicScheduler(Consumer<Exception> exceptionConsumer, String name) {
        this(exceptionConsumer, name, 0, MoeScheduler.DEFAULT_PERIOD);
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
        return thread.getName().startsWith(name);
    }

    @Override
    public boolean isStopped() {
        return updateLoop == null || (updateLoop.isCancelled() || updateLoop.isDone());
    }

    @Override
    public SchedulerStats stats() {
        return telescope;
    }

    @Override
    public void register(MoeTask task) {
        Objects.requireNonNull(task);
        registry.add(task);
    }

    @Override
    public void unregister(MoeTask task) {
        Objects.requireNonNull(task);
        registry.remove(task);
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

    @Override
    public void start() {
        if (updateLoop != null)
            throw new IllegalStateException("Scheduler has already started.");
        lastUpdate = System.currentTimeMillis();
        updateLoop = executor.scheduleAtFixedRate(() -> {
            final var currentTime = System.currentTimeMillis();
            update(currentTime);
            lastUpdate = currentTime;
            telescope.update(currentTime);
        }, delay, period, TimeUnit.MILLISECONDS);
        register(() -> updateThread = Thread.currentThread());

        asyncExecutor.scheduleAtFixedRate(new Nurse(), 10_000, 5_000, TimeUnit.MILLISECONDS);
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

    @Override
    public String toString() {
        var prefix = String.format("[ %s-scheduler ]", name);
        var sb = new StringBuilder()
                .append(prefix).append(" Tasks: ").append(registry.size())
                .append(", Executor: ").append(executor.isTerminated())
                .append(", Async: ").append(asyncExecutor.isTerminated())
                .append("\r\n")
                .append(prefix).append(" Telescope: ").append(telescope);
        return sb.toString();
    }

    protected void update(MoeTask task, long currentTime) {
        if (task.isEventAsync()) {
            asyncExecutor.submit(() -> {
                try {
                    task.update(currentTime);
                } catch (Exception e) { exceptionConsumer.accept(e); }
            });
        } else {
            try {
                task.update(currentTime);
            } catch (Exception e) { exceptionConsumer.accept(e); }
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

    private final class Nurse implements Runnable {

        @Override
        public void run() {
            if (System.currentTimeMillis() - lastUpdate >= 5_000 && updateThread != null) {
                var stack = updateThread.getStackTrace(); // Arrays.toString is ugly :(
                var sb = new StringBuilder();
                for (var s : stack) sb.append(s.toString()).append("\r\n");

                exceptionConsumer.accept(new SchedulerException("Update loop is broke, last update was over 5 seconds ago! StackTrace of presumed loop thread: "+sb.toString()));
                // Loop thread needs to be reset. :(

                executor.shutdownNow();
                executor = Executors.newSingleThreadScheduledExecutor(factory);
                updateLoop = null;
                start();
            }
        }
    }
}
