package com.github.foeser.teamcity.elastictimeout;

import com.github.foeser.teamcity.elastictimeout.schedulers.Scheduler;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.serverSide.*;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import static jetbrains.buildServer.BuildProblemTypes.TC_EXECUTION_TIMEOUT_TYPE;

public class BuildTimeoutHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildTimeoutHandler.class);
    //private static final Logger LOGGER = Logger.getLogger(Loggers.SERVER_CATEGORY);
    // concurrent hashmap is weak consistent but should be okay for our usage (esp. since we don't update values but rather add or remove keys)
    private final ConcurrentHashMap<Long, Map.Entry<Long, Boolean>> mapBuildIdMaxBuildDuration;
    private final BuildHistory buildHistory;
    private final RunningBuildsManager runningBuildsManager;

    public BuildTimeoutHandler(@NotNull Scheduler executorServices,
                               @NotNull RunningBuildsManager runningBuildsManager,
                               @NotNull BuildHistory buildHistory) {
        mapBuildIdMaxBuildDuration = new ConcurrentHashMap<>();
        this.buildHistory = buildHistory;
        this.runningBuildsManager = runningBuildsManager;
        executorServices.setRunnable(() -> checkBuildsForTimeout());
        executorServices.invoke();
    }

    private void checkBuildsForTimeout() {
        try {
            // ToDo: check with thread mentioned from log4j log and remove afterwards
            LOGGER.debug("Current thread: " + Thread.currentThread());
            // weak consistent but we can live with it when a potential - just now  - added build get checked on next task run
            if(mapBuildIdMaxBuildDuration.isEmpty()) {
                LOGGER.debug("No builds to check, quitting early");
                return;
            }

            mapBuildIdMaxBuildDuration.forEach((id, buildDescriptor) -> {
                SRunningBuild build = runningBuildsManager.findRunningBuildById(id);
                if(build == null) {
                    // due to the weak consistency it can happen that the build has already finished, and we are working on stale/transient data (from the iterator of the map)
                    LOGGER.debug(String.format("Build with id: %d is not running anymore.", id));
                    return;
                }
                long currentBuildDuration = build.getDuration();
                long maxAllowedBuildDuration = buildDescriptor.getKey();
                Boolean stopBuildOnTimeout = buildDescriptor.getValue();
                if (currentBuildDuration > maxAllowedBuildDuration) {
                    // don't consider this build anymore and remove from map
                    mapBuildIdMaxBuildDuration.remove(id);
                    if(stopBuildOnTimeout) {
                        String buildTimeoutDescription = String.format("The build %s has been running for more than %d. Terminating...", build, maxAllowedBuildDuration);
                        // adding a dummy user is important, otherwise the build get constantly re-queued
                        build.stop(new DummyUser(), buildTimeoutDescription);
                        //build.setInterrupted(RunningBuildState.INTERRUPTED_BY_SYSTEM, null, buildTimeoutDescription);
                        build.addBuildProblem(BuildProblemData.createBuildProblem(String.valueOf(buildTimeoutDescription.hashCode()), TC_EXECUTION_TIMEOUT_TYPE, buildTimeoutDescription));
                        LOGGER.info(String.format("%s is running already %d and exceed maximum allowed time of %d and got stopped.", build, currentBuildDuration, maxAllowedBuildDuration));
                    } else {
                        String buildTimeoutDescription = String.format("The build %s has been running for more than %d. Adding problem to build...", build, maxAllowedBuildDuration);
                        build.addBuildProblem(BuildProblemData.createBuildProblem(String.valueOf(buildTimeoutDescription.hashCode()), TC_EXECUTION_TIMEOUT_TYPE, buildTimeoutDescription));
                        LOGGER.info(String.format("%s is running already %d and exceed maximum allowed time of %d and got annotated with build problem.", build, currentBuildDuration, maxAllowedBuildDuration));
                    }
                } else {
                    LOGGER.debug(String.format("Checked build %s which is currently running for %d seconds and will exceed once reaching %d.", build, currentBuildDuration, maxAllowedBuildDuration));
                }
            });
        } catch (Exception e) {
            LOGGER.error("There was an exception while checking build duration times: " + e);
        }
    }

    public void handleBuild(SRunningBuild build, SBuildFeatureDescriptor elasticTimeoutFailureCondition, BuildStatus buildStatus) {
        switch (buildStatus) {
            case STARTED:
                addBuild(build, elasticTimeoutFailureCondition);
                break;
            case FINISHING:
                removeBuild(build);
                break;
            default:
                LOGGER.error(String.format("BuildTimeOutHandler can't proceed with unknown buildStatus for %s.", build));
        }
    }

    private void addBuild(SRunningBuild build, SBuildFeatureDescriptor elasticTimeoutFailureCondition) {
        Map<String, String> timeoutParameters = elasticTimeoutFailureCondition.getParameters();
        int numPreviousBuildsToConsider = Integer.parseInt(timeoutParameters.get(ElasticTimeoutFailureCondition.PARAM_BUILD_COUNT));
        boolean successfulBuildsOnly = timeoutParameters.get(ElasticTimeoutFailureCondition.PARAM_STATUS).equals(ElasticTimeoutFailureCondition.PARAM_STATUS_SUCCESSFUL);
        // get previous builds based on the condition settings
        List<SFinishedBuild> previousBuilds = buildHistory.getEntriesBefore(build, successfulBuildsOnly);
        if(previousBuilds.size() < numPreviousBuildsToConsider) {
            // in case we don't have enough builds we simply don't care
            LOGGER.debug(String.format("[%s] has elastic timout enabled but doesn't have enough builds in history to consider (%d/%d). Skipping...", build, previousBuilds.size(), numPreviousBuildsToConsider));
        } else {
            List<SFinishedBuild> buildsToConsider = previousBuilds.subList(0, numPreviousBuildsToConsider);
            LOGGER.debug(String.format("Calculating time out times for %s based on those previous builds: %s", build, buildsToConsider));
            // get the total time of all relevant previous builds
            int anchorValue = Integer.parseInt(timeoutParameters.get(ElasticTimeoutFailureCondition.PARAM_ANCHOR_VALUE));
            boolean usePercentage = timeoutParameters.get(ElasticTimeoutFailureCondition.PARAM_ANCHOR_UNIT).equals(ElasticTimeoutFailureCondition.PARAM_ANCHOR_UNIT_PERCENT);
            LOGGER.debug(String.format("Used anchor value: %d (%s)", anchorValue, timeoutParameters.get(ElasticTimeoutFailureCondition.PARAM_ANCHOR_UNIT)));
            long maxRunTime = calculateMaxRunTime(buildsToConsider, anchorValue, usePercentage);
            boolean stopBuildOnTimeout = timeoutParameters.get(ElasticTimeoutFailureCondition.PARAM_STOP_BUILD).equals("true");
            // put() is enough since we can't have the same build id twice per definition (compared to using putIfAbsent())
            mapBuildIdMaxBuildDuration.put(build.getBuildId(), Map.entry(maxRunTime, stopBuildOnTimeout));
            LOGGER.info(String.format("Start checking %s build duration which should not take longer then %d seconds.", build, maxRunTime));
        }
    }

    private long calculateMaxRunTime(List<SFinishedBuild> buildsToConsider, int anchorValue, boolean isPercentage) {
        long totalTime = buildsToConsider.stream().mapToLong(b -> b.getDuration()).sum();
        long avgBuildTime = totalTime / buildsToConsider.size();
        LOGGER.debug(String.format("Avg. build time of all considered builds: %d", avgBuildTime));
        // maxRunTime will be avg. build time of previous builds plus a given anchor value either applied as percentage or fixed time
        long maxRunTime = avgBuildTime;
        if(isPercentage) {
            maxRunTime += (avgBuildTime * anchorValue) / 100;
        } else {
            maxRunTime += anchorValue;
        }
        return maxRunTime;
    }

    private void removeBuild(SRunningBuild build) {
        if(mapBuildIdMaxBuildDuration.remove(build.getBuildId()) != null) {
            LOGGER.info(String.format("%s finished and will no longer be considered.", build));
        } else {
            LOGGER.debug(String.format("%s was removed earlier already from consideration due to timeout.", build));
        }
    }

    public int getCurrentBuildsInConsideration() {
        return mapBuildIdMaxBuildDuration.size();
    }
}
