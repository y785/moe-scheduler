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

package tests;

import moe.maple.scheduler.MoeBasicScheduler;
import moe.maple.scheduler.MoeScheduler;
import moe.maple.scheduler.tasks.MoeTask;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Setup;

import java.util.ArrayList;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class SchedulerTests {

    private static MoeScheduler scheduler;

    @Setup
    @BeforeAll
    public static void setup() {
        scheduler = new MoeBasicScheduler();
        scheduler.start();
    }

    @Test
    public void testDeadlock() {
        final var phaser = new Phaser();
        phaser.register();

        final var lock = new ReentrantLock();
        lock.lock();
        scheduler.register((d) -> {
            System.out.println("Lock: " + lock.isLocked());
            lock.lock();
        });
        phaser.awaitAdvance(0);
        phaser.awaitAdvance(1);
    }

    @Test
    public void testAsyncGroups() {
        final var testCount = 20;
        var tasks = new ArrayList<MoeTask>();
        var atomic = new AtomicInteger();
        for (int i = 0; i < testCount; i++)
            tasks.add((d) -> {
                try {
                    Thread.sleep(atomic.incrementAndGet() * 1000);
                    atomic.decrementAndGet();
                } catch (Exception e) { e.printStackTrace(); }
            });

        scheduler.awaitAsync(tasks, Throwable::printStackTrace);
        assert atomic.get() == 0;
    }

}
