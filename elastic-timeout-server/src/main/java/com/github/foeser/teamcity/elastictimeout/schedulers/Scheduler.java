package com.github.foeser.teamcity.elastictimeout.schedulers;
// based on https://capgemini.github.io/development/testing-timers/
public interface Scheduler {
    void invoke();
    void setRunnable(Runnable runnable);
}
