package com.github.foeser.teamcity.elastictimeout;

import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.util.EventDispatcher;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.concurrent.TimeUnit.SECONDS;
import static jetbrains.buildServer.BuildProblemTypes.TC_EXECUTION_TIMEOUT_TYPE;

public class FinishBuildListener extends BuildServerAdapter {

    //private final ScheduledExecutorService scheduledExecutorService;
    private static long SCHEDULER_INITALDELAY_INSECONDS = 10;
    private static long SCHEDULER_PERIOD_INSECONDS = 10;
    RunningBuildsManager runningBuilds;
    private static final Logger LOGGER = Logger.getLogger(FinishBuildListener.class.getName());
    //private static final Logger LOGGER = Logger.getLogger(Loggers.SERVER_CATEGORY);
    // concurrent hashmap is weak consistent but should be okay for our usage (esp. since we don't update values but rather add or remove keys)
    private ConcurrentHashMap<Long, Long> mapBuildIdMaxBuildDuration;
    //ReentrantLock lock = new ReentrantLock();
    private BuildHistory buildHistory;
    // ToDo: rename to BuildStateListener
    public FinishBuildListener(@NotNull EventDispatcher<BuildServerListener> events,
                               @NotNull ExecutorServices executorServices,
                               @NotNull RunningBuildsManager runningBuilds,
                               @NotNull BuildHistory buildHistory) {

        events.addListener(this);
        mapBuildIdMaxBuildDuration = new ConcurrentHashMap();
        this.buildHistory = buildHistory;
        this.runningBuilds = runningBuilds;

        executorServices.getNormalExecutorService().scheduleAtFixedRate(checkBuildTimes(),SCHEDULER_INITALDELAY_INSECONDS, SCHEDULER_PERIOD_INSECONDS, SECONDS);
        /*executorService.submit(ExceptionUtil.catchAll("BuildCommitStatus Handler", new Runnable() {
            @Override
            public void run() {
                try {
                    statusHandler.handle(build, feature, buildStatus);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error updating commit status.", e);
                }
            }
        }));*/
    }

    @Override
    public void buildStarted(@NotNull SRunningBuild build) {
        // ToDo: move actual logic to dedicated class or feature class but not in Listener (see teamcity-plugins-master or teamcity-tests-watchdog-master)
        /*for (SBuildFeatureDescriptor descriptor : featuresOfType) {
            ((TestDurationFailureCondition) descriptor.getBuildFeature()).checkBuild(build, descriptor);
        }*/

        // ToDo: this can go to dedicated function and just return the proper feature
        final Collection<SBuildFeatureDescriptor> avgBuildTimeFailureConditions = build.getBuildFeaturesOfType(AvgBuildTimeFailureCondition.TYPE);
        if(avgBuildTimeFailureConditions.size() != 1) {
            // if the build doesn't use our failure condition we just can skip
            LOGGER.debug(String.format("%s [%s]", "Either none or more then one enabled AvgBuildTimeFailureCondition feature (failure condition) in that build", build));
            return;
        } else {
            // ToDo: test that SBuilds getBuildFeaturesOfType() returns really just enabled ones (compared to the method from BuildTypeSettings)
            // if the build has the failure condition enabled, calculate the max running time for this build based on the settings
            SBuildFeatureDescriptor avgBuildTimeFailureCondition = avgBuildTimeFailureConditions.stream().findFirst().get();
            int previousBuildsToConsider = Integer.parseInt(avgBuildTimeFailureCondition.getParameters().get(AvgBuildTimeFailureCondition.PARAM_BUILD_COUNT));
            boolean successfulOnly = avgBuildTimeFailureCondition.getParameters().get(AvgBuildTimeFailureCondition.PARAM_STATUS).equals(AvgBuildTimeFailureCondition.PARAM_STATUS_SUCCESSFUL);
            List<SFinishedBuild> previousBuilds = buildHistory.getEntriesBefore(build, successfulOnly);
            if(previousBuilds.size() <= previousBuildsToConsider) {
                // in case we don't have enough builds we simple don't take care
                LOGGER.debug(String.format("[%s] has elastic timout enabled but doesn't have enough builds in history to consider (%d/%d). Skipping...", build, previousBuilds.size(), previousBuildsToConsider));
                return;
            } else {
                List<SFinishedBuild> buildsToConsider = previousBuilds.subList(0, previousBuildsToConsider);
                LOGGER.debug(String.format("Calculating time out times for %s based on those previous builds: %s", build, buildsToConsider));
                // get the total time of all previous builds
                long totalTime = buildsToConsider.stream().mapToLong(b -> b.getDuration()).sum();
                // maxRunTime = average build time of previous builds + certain percentage or fixed time
                long avgBuildTime = totalTime / previousBuildsToConsider;
                LOGGER.debug(String.format("Avg. build time of all considered builds: %d", avgBuildTime));
                long maxRunTime = avgBuildTime;
                int exceedValue = Integer.parseInt(avgBuildTimeFailureCondition.getParameters().get(AvgBuildTimeFailureCondition.PARAM_EXCEED_VALUE));
                if(avgBuildTimeFailureCondition.getParameters().get(AvgBuildTimeFailureCondition.PARAM_EXCEED_UNIT).equals(AvgBuildTimeFailureCondition.PARAM_EXCEED_UNIT_PERCENT)) {
                   maxRunTime += (avgBuildTime * exceedValue) / 100;
                } else {
                    // in case of constant value
                   maxRunTime += exceedValue;
                }
                LOGGER.debug(String.format("Used exceed value: %d (%s)", exceedValue, avgBuildTimeFailureCondition.getParameters().get(AvgBuildTimeFailureCondition.PARAM_EXCEED_UNIT)));
                // in theory normal put() should be enough since there can't be the same ID twice per definition
                mapBuildIdMaxBuildDuration.putIfAbsent(build.getBuildId(), maxRunTime);
                LOGGER.info(String.format("Start checking %s build duration which should not take longer then %d seconds.", build, maxRunTime));
                // check if we have already a scheduler, if not, create one
                /*lock.lock();
                try {
                    if (future == null || future.isCancelled()) {
                        future = scheduledExecutorService.scheduleAtFixedRate(() -> {
                          foobar()
                        }, 10, 10, SECONDS);
                    }
                } finally {
                    lock.unlock();
                }*/
            }
        }
    }

    private Runnable checkBuildTimes() {
        return () -> {
            try {
                // weak consistent but we can live with it when a potential just now added build get checked on next task run
                if(mapBuildIdMaxBuildDuration.isEmpty()) {
                    LOGGER.debug("No builds to check, quitting early");
                    return;
                }
                /*for (Iterator<ConcurrentHashMap.Entry<Long, Long>> iter = mapBuildIdMaxBuildDuration.entrySet().iterator(); iter.hasNext();) {
                    ConcurrentHashMap.Entry<Long,Long> entry = iter.next();
                    ....
                }*/
                mapBuildIdMaxBuildDuration.forEach((id, maxAllowedBuildDuration) -> {
                    SRunningBuild build = runningBuilds.findRunningBuildById(id);
                    if(build == null) {
                        // due to the weak consistency it can happen that the build has already finished and we are working on stale/transient data (the iterator of the map)
                        LOGGER.debug(String.format("Build with id: %d is not running anymore.", id));
                        return;
                    }
                    // ToDO: print thread name and compare with log output
                    long currentBuildDuration = build.getDuration();
                    if (currentBuildDuration > maxAllowedBuildDuration) {
                        // ToDo: adapt to desc of TC native implementation
                        String buildTimeoutDescrition = "Build duration exceed maximum allowed time of " + maxAllowedBuildDuration;
                        build.addBuildProblem(BuildProblemData.createBuildProblem(String.valueOf(buildTimeoutDescrition.hashCode()), TC_EXECUTION_TIMEOUT_TYPE, buildTimeoutDescrition));
                        // don't consider this build anymore
                        if(mapBuildIdMaxBuildDuration.remove(id, maxAllowedBuildDuration)) {
                            // a normal remove() should be enough since we don't change/update the value
                        }
                        // ToDo: stop build depending on the settings
                        LOGGER.info(String.format("%s is running already %d and exceed maximum allowed time of %d and got annotated with build problem.", build, currentBuildDuration, maxAllowedBuildDuration));
                    } else {
                        // Todo: debug log for still processing build xyz
                    }
                });
            } catch (Exception e) {
                LOGGER.error("There was an exception while checking build duration times: " + e);
            }
        };
    }

    // get also called when build get interrupted
    @Override
    public void beforeBuildFinish(@NotNull SRunningBuild build) {
        final Collection<SBuildFeatureDescriptor> featuresOfType = build.getBuildFeaturesOfType(AvgBuildTimeFailureCondition.TYPE);
        if(featuresOfType.size() != 1) {
            // if the build doesn't use our failure condition we just can skip
            LOGGER.debug(String.format("%s [%s]", "Either none or more then one enabled AvgBuildTimeFailureCondition feature (failure condition) in that build", build));
            return;
        } else {
            if(mapBuildIdMaxBuildDuration.remove(build.getBuildId()) != null) {
                LOGGER.info(String.format("%s finished and will no longer be considered.", build));
            } else {
                LOGGER.debug(String.format("%s was removed already from consideration due to timeout.", build));
            }
        }
    }
}
