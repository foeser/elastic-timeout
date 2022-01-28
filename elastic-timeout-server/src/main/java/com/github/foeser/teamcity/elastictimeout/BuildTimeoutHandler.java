package com.github.foeser.teamcity.elastictimeout;

import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.concurrent.TimeUnit.SECONDS;
import static jetbrains.buildServer.BuildProblemTypes.TC_EXECUTION_TIMEOUT_TYPE;

public class BuildTimeoutHandler {

    private static long SCHEDULER_INITALDELAY_INSECONDS = 10;
    private static long SCHEDULER_PERIOD_INSECONDS = 10;
    RunningBuildsManager runningBuilds;
    private static final Logger LOGGER = Logger.getLogger(BuildEventListener.class.getName());
    // concurrent hashmap is weak consistent but should be okay for our usage (esp. since we don't update values but rather add or remove keys)
    private ConcurrentHashMap<Long, Long> mapBuildIdMaxBuildDuration;
    private BuildHistory buildHistory;

    public BuildTimeoutHandler(@NotNull ExecutorServices executorServices,
                               @NotNull RunningBuildsManager runningBuilds,
                               @NotNull BuildHistory buildHistory) {
        mapBuildIdMaxBuildDuration = new ConcurrentHashMap();
        this.buildHistory = buildHistory;
        this.runningBuilds = runningBuilds;

        executorServices.getNormalExecutorService().scheduleAtFixedRate(() -> {
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
        }, SCHEDULER_INITALDELAY_INSECONDS, SCHEDULER_PERIOD_INSECONDS, SECONDS);
    }

    public void handleBuild(SRunningBuild build, SBuildFeatureDescriptor elasticTimeoutFailureCondition, BuildStatus buildStatus) {
        switch (buildStatus) {
            case STARTED:
                addBuild(build, elasticTimeoutFailureCondition);
            case FINISHING:
                removeBuild(build, elasticTimeoutFailureCondition);
            default:
                // ToDo: throw exception, write error
        }
    }

    private void addBuild(SRunningBuild build, SBuildFeatureDescriptor elasticTimeoutFailureCondition) {
        int previousBuildsToConsider = Integer.parseInt(elasticTimeoutFailureCondition.getParameters().get(ElasticTimeoutFailureCondition.PARAM_BUILD_COUNT));
        boolean successfulOnly = elasticTimeoutFailureCondition.getParameters().get(ElasticTimeoutFailureCondition.PARAM_STATUS).equals(ElasticTimeoutFailureCondition.PARAM_STATUS_SUCCESSFUL);
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
            int exceedValue = Integer.parseInt(elasticTimeoutFailureCondition.getParameters().get(ElasticTimeoutFailureCondition.PARAM_EXCEED_VALUE));
            if(elasticTimeoutFailureCondition.getParameters().get(ElasticTimeoutFailureCondition.PARAM_EXCEED_UNIT).equals(ElasticTimeoutFailureCondition.PARAM_EXCEED_UNIT_PERCENT)) {
                maxRunTime += (avgBuildTime * exceedValue) / 100;
            } else {
                // in case of constant value
                maxRunTime += exceedValue;
            }
            LOGGER.debug(String.format("Used exceed value: %d (%s)", exceedValue, elasticTimeoutFailureCondition.getParameters().get(ElasticTimeoutFailureCondition.PARAM_EXCEED_UNIT)));
            // in theory normal put() should be enough since there can't be the same ID twice per definition
            mapBuildIdMaxBuildDuration.putIfAbsent(build.getBuildId(), maxRunTime);
            LOGGER.info(String.format("Start checking %s build duration which should not take longer then %d seconds.", build, maxRunTime));
        }
    }

    private void removeBuild(SRunningBuild build, SBuildFeatureDescriptor elasticTimeoutFailureCondition) {
        if(mapBuildIdMaxBuildDuration.remove(build.getBuildId()) != null) {
            LOGGER.info(String.format("%s finished and will no longer be considered.", build));
        } else {
            LOGGER.debug(String.format("%s was removed already from consideration due to timeout.", build));
        }
    }
}
