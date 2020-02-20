# Moe Scheduler

A simple tick-based event scheduler. Used for synchronizing game server events.
Goal is to update actors with a time offset to determine proper event timings.
Repeating tasks can't be registered from the scheduler through lambda.
Repeating tasks added this way would be memory leaked, since they are never removed.
By default, all tasks are removed based on the ``isEventDone()``, which returns true after any task's ``update(long: delta)`` is successfully run.  

### Dependencies
None.

### Maven
```
<dependency>
  <groupId>moe.maple</groupId>
  <artifactId>scheduler</artifactId>
  <version>2.1.0</version>
  <type>pom</type>
</dependency>
```
