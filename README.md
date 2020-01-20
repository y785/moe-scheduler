# Moe Scheduler

A simple tick-based event scheduler. Used for synchronizing game server events.
Goal is to update actors with a time offset to determine proper event timings.
Repeating tasks can't be registered from the scheduler through lambda.
Repeating tasks added this way would be memory leaked, since they are never removed.
By default, all tasks are removed based on the ``isEventDone()``, which returns true after any task's ``update(long: delta)`` is successfully run.  

### Dependencies
None.

### Examples
##### Simple: Dump a log every 10s
```
new MoeRepeatingDelayedTask((delta) -> log.debug("{}, PacketStats: {}", uptime, stats), 10000)
```
##### Advanced: Lazily unloading .img/.wz files
```
public class LazyImgTask implements MoeTask {

    private final LazyImg img;
    private final long delay;

    private boolean done;

    public LazyImgTask(LazyImg img, long delay) {
        this.img = img;
        this.delay = delay;
        this.done = false;
    }

    @Override
    public boolean isEventDone() { return done; }

    @Override
    public boolean isEventAsync() { return false; }

    @Override
    public void update(long delta) {
        if (delta - img.getLastAccess() >= delay && !done) {
            this.img.clear();
            this.done = true; // Done is set, task is removed on next update loop.
        }
    }
}
```
