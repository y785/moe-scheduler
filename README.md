# Moe Scheduler

A simple tick-based event scheduler. Used for synchronizing game server events.
Goal is to update actors with a time offset to determine proper event timings.
Repeating tasks can't be registered from the scheduler through lambda.
Repeating tasks added this way would be memory leaked, since they are never removed.
By default, all tasks are removed based on the ``isEventDone()``, which returns true after any task's ``update(long: delta)`` is successfully run. Registration of always-on tasks can be done through ``registerRepeating``. There is currently no way to remove these tasks except for ``remove(Predicate<MoeTask>)``

### Maven
[![CircleCI](https://circleci.com/gh/y785/moe-scheduler.svg?style=svg)](https://circleci.com/gh/y785/moe-scheduler)
[![Download](https://api.bintray.com/packages/moe/maple/scheduler/images/download.svg) ](https://bintray.com/moe/maple/scheduler/_latestVersion)
```
<dependency>
  <groupId>moe.maple</groupId>
  <artifactId>scheduler</artifactId>
  <version>2.1.1</version>
</dependency>
```
