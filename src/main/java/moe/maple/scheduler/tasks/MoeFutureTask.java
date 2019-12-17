package moe.maple.scheduler.tasks;

import moe.maple.scheduler.MoeScheduler;

public interface MoeFutureTask extends MoeTask {

    default boolean cancel() {
        return false;
    }

    void registerScheduler(MoeScheduler scheduler);
}
