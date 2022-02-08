package com.github.foeser.teamcity.elastictimeout.schedulers;

public class ManualScheduler implements Scheduler {
    private Runnable runnable;

    @Override
    public void invoke() {
        if(runnable != null) {
            runnable.run();
        }
    }

    @Override
    public void setRunnable(Runnable runnable) {
        this.runnable = runnable;
    }
}
