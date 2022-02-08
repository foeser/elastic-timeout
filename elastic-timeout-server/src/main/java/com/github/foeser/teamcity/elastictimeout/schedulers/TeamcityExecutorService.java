package com.github.foeser.teamcity.elastictimeout.schedulers;

import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.SECONDS;

public class TeamcityExecutorService implements Scheduler {
    private static final long SCHEDULER_INITIAL_DELAY_IN_SECONDS = 10;
    private static final long SCHEDULER_PERIOD_IN_SECONDS = 10;
    private final ScheduledExecutorService scheduledExecutorService;
    private Runnable runnable;

    public TeamcityExecutorService(@NotNull ExecutorServices executorServices) {
        scheduledExecutorService = executorServices.getNormalExecutorService();
    }

    @Override
    public void invoke() {
        if(runnable != null && scheduledExecutorService != null) {
            scheduledExecutorService.scheduleAtFixedRate(runnable, SCHEDULER_INITIAL_DELAY_IN_SECONDS, SCHEDULER_PERIOD_IN_SECONDS, SECONDS);
        }
    }

    @Override
    public void setRunnable(Runnable runnable) {
        this.runnable = runnable;
    }
}