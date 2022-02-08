package com.github.foeser.teamcity.elastictimeout;

import com.github.foeser.teamcity.elastictimeout.schedulers.Scheduler;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.*;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import static jetbrains.buildServer.BuildProblemTypes.TC_EXECUTION_TIMEOUT_TYPE;

public class BuildTimeoutHandler {

    //private static final Logger LOGGER = LoggerFactory.getLogger(BuildEventListener.class);
    private static final Logger LOGGER = Logger.getLogger(Loggers.SERVER_CATEGORY);
    // concurrent hashmap is weak consistent but should be okay for our usage (esp. since we don't update values but rather add or remove keys)
    private ConcurrentHashMap<Long, Map.Entry<Long, Boolean>> mapBuildIdMaxBuildDuration;
    private BuildHistory buildHistory;
    private RunningBuildsManager runningBuildsManager;

    public BuildTimeoutHandler(@NotNull Scheduler executorServices,
                               @NotNull RunningBuildsManager runningBuildsManager,
                               @NotNull BuildHistory buildHistory) {
        mapBuildIdMaxBuildDuration = new ConcurrentHashMap();
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
            /*for (Iterator<ConcurrentHashMap.Entry<Long, Long>> iter = mapBuildIdMaxBuildDuration.entrySet().iterator(); iter.hasNext();) {
                ConcurrentHashMap.Entry<Long,Long> entry = iter.next();
                ....
            }*/
            mapBuildIdMaxBuildDuration.forEach((id, buildDescriptor) -> {
                SRunningBuild build = runningBuildsManager.findRunningBuildById(id);
                if(build == null) {
                    // due to the weak consistency it can happen that the build has already finished, and we are working on stale/transient data (from the iterator of the map)
                    LOGGER.debug(String.format("Build with id: %d is not running anymore.", id));
                    return;
                }
                long currentBuildDuration = build.getDuration();
                // Todo: check net buildtime which should not include sync times (potentially check SBuild.html#getStatisticValues() and SRunningBuild.html#getCurrentPath() to check if stil syncing)
                //long currentBuildDuration = build.getElapsedTime();
                long maxAllowedBuildDuration = buildDescriptor.getKey();
                Boolean stopBuildOnTimeout = buildDescriptor.getValue();
                if (currentBuildDuration > maxAllowedBuildDuration) {
                    // don't consider this build anymore and remove from map
                    mapBuildIdMaxBuildDuration.remove(id);
                    // ToDo: adapt to desc of TC native implementation
                    String buildTimeoutDescription = "Build duration exceed maximum allowed time of " + maxAllowedBuildDuration;
                    if(stopBuildOnTimeout) {
                        build.stop(new DummyUser(), buildTimeoutDescription);
                        //build.setInterrupted(RunningBuildState.INTERRUPTED_BY_SYSTEM, null, buildTimeoutDescription);
                        build.addBuildProblem(BuildProblemData.createBuildProblem(String.valueOf(buildTimeoutDescription.hashCode()), TC_EXECUTION_TIMEOUT_TYPE, buildTimeoutDescription));
                        LOGGER.info(String.format("%s is running already %d and exceed maximum allowed time of %d and got stopped.", build, currentBuildDuration, maxAllowedBuildDuration));
                    } else {
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
            return;
        } else {
            List<SFinishedBuild> buildsToConsider = previousBuilds.subList(0, numPreviousBuildsToConsider);
            LOGGER.debug(String.format("Calculating time out times for %s based on those previous builds: %s", build, buildsToConsider));
            // get the total time of all relevant previous builds
            int exceedValue = Integer.parseInt(timeoutParameters.get(ElasticTimeoutFailureCondition.PARAM_EXCEED_VALUE));
            // Todo: we can extract PARAM_EXCEED_UNIT as var)
            boolean usePercentage = timeoutParameters.get(ElasticTimeoutFailureCondition.PARAM_EXCEED_UNIT).equals(ElasticTimeoutFailureCondition.PARAM_EXCEED_UNIT_PERCENT);
            LOGGER.debug(String.format("Used exceed value: %d (%s)", exceedValue, timeoutParameters.get(ElasticTimeoutFailureCondition.PARAM_EXCEED_UNIT)));
            long totalTime = buildsToConsider.stream().mapToLong(b -> b.getDuration()).sum();
            long avgBuildTime = totalTime / numPreviousBuildsToConsider;
            LOGGER.debug(String.format("Avg. build time of all considered builds: %d", avgBuildTime));
            // maxRunTime will be build time of previous builds + given percentage or fixed time
            long maxRunTime = avgBuildTime;
            if(usePercentage) {
                maxRunTime += (avgBuildTime * exceedValue) / 100;
            } else {
                maxRunTime += exceedValue;
            }
            boolean stopBuildOnTimeout = timeoutParameters.get(ElasticTimeoutFailureCondition.PARAM_STOP_BUILD).equals("true");
            // put() is enough since we can't have the same build id twice per definition (compared to using putIfAbsent())
            mapBuildIdMaxBuildDuration.put(build.getBuildId(), Map.entry(maxRunTime, stopBuildOnTimeout));
            LOGGER.info(String.format("Start checking %s build duration which should not take longer then %d seconds.", build, maxRunTime));
        }
    }

    private void removeBuild(SRunningBuild build) {
        if(mapBuildIdMaxBuildDuration.remove(build.getBuildId()) != null) {
            LOGGER.info(String.format("%s finished and will no longer be considered.", build));
        } else {
            LOGGER.debug(String.format("%s was removed earlier already from consideration due to timeout.", build));
        }
    }

    public int getCurrentBuildsConsidered() {
        return mapBuildIdMaxBuildDuration.size();
    }
}
