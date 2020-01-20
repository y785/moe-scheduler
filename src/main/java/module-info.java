module moe.maple.scheduler {
    requires org.apache.logging.log4j;

    exports moe.maple.scheduler;
    exports moe.maple.scheduler.tasks;
    exports moe.maple.scheduler.tasks.delay;
    exports moe.maple.scheduler.tasks.tick;
}