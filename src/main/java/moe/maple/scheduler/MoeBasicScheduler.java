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

import moe.maple.scheduler.tasks.MoeFutureTask;
import moe.maple.scheduler.tasks.MoeTask;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class MoeBasicScheduler implements MoeScheduler {



    private final String name;
    private final int delay, period;

    private final ThreadFactory factory;
    private final ScheduledExecutorService executor;
    private final ThreadPoolExecutor asyncExecutor;

    private final MoeRollingTelescope telescope;
    private final Set<MoeTask> registry;

    private ScheduledFuture<?> updateLoop;

    private Consumer<Exception> exceptionConsumer;

    public MoeBasicScheduler(Consumer<Exception> exceptionConsumer, String name, int delay, int period) {
        this.exceptionConsumer = exceptionConsumer;
        this.name = name;
        this.delay = delay;
        this.period = period;

        this.factory = new MoeBasicThreadFactory(name);
        this.executor = Executors.newSingleThreadScheduledExecutor(factory);
        this.asyncExecutor = new ThreadPoolExecutor(0,
                MoeScheduler.THREADS,
                20L,
                TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(),
                factory);

        this.telescope = new MoeRollingTelescope(period);
        this.registry = ConcurrentHashMap.newKeySet();
    }

    public MoeBasicScheduler(Consumer<Exception> exceptionConsumer, String name) {
        this(exceptionConsumer, name, 0, MoeScheduler.DEFAULT_PERIOD);
    }

    public MoeBasicScheduler() {
        this(Throwable::printStackTrace, "moe");
    }

    @Override
    public MoeTelescope telescope() { return telescope; }

    @Override
    public void register(MoeTask task) {
        Objects.requireNonNull(task);
        if(task instanceof MoeFutureTask) {
            ((MoeFutureTask) task).registerScheduler(this);
        }
        registry.add(task);
    }

    @Override
    public boolean unregister(MoeTask task) {
        Objects.requireNonNull(task);
        return registry.remove(task);
    }

    @Override
    public boolean remove(Predicate<MoeTask> check) {
        return registry.removeIf(check);
    }

    @Override
    public void start() {
        if (updateLoop != null)
            throw new IllegalStateException("Scheduler has already started.");
        updateLoop = executor.scheduleAtFixedRate(() -> {
            final var delta = System.currentTimeMillis();
            final var iter = registry.iterator();
            while (iter.hasNext()) {
                final var task = iter.next();
                if (task.isEventAsync()) {
                    asyncExecutor.submit(() -> {
                        try {
                            task.update(delta);
                        } catch (Exception e) { exceptionConsumer.accept(e); }
                    });
                } else {
                    try {
                        task.update(delta);
                    } catch (Exception e) { exceptionConsumer.accept(e); }
                }

                if (task.isEventDone()) iter.remove();
            }
            telescope.update(delta);
        }, delay, period, TimeUnit.MILLISECONDS);
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
}
