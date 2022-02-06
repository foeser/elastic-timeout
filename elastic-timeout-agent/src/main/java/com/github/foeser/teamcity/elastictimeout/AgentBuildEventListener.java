package com.github.foeser.teamcity.elastictimeout;

import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.BuildHistory;
import jetbrains.buildServer.util.EventDispatcher;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class AgentBuildEventListener extends AgentLifeCycleAdapter {
    //private final AgentBuildTimeoutHandler buildTimeoutHandler;
    private final BuildHistory buildHistory;
    private static final Logger LOGGER = Logger.getLogger(Loggers.AGENT_CATEGORY);
    public AgentBuildEventListener(@NotNull EventDispatcher<AgentLifeCycleListener> events,
                                   @NotNull BuildHistory buildHistory) {
        events.addListener(this);
        this.buildHistory = buildHistory;
        //this.buildTimeoutHandler = buildTimeoutHandler;
    }

    @Override
    public void buildStarted(AgentRunningBuild build) {
        AgentBuildFeature feature = getFeature(build);
        if (feature != null) {
            //buildTimeoutHandler.handleBuild(build, feature, BuildStatus.STARTED);
        }
        /*int numPreviousBuildsToConsider = Integer.parseInt(build.getBuildFeatures().stream().findFirst().get().getParameters().get(ElasticTimeoutFailureCondition.PARAM_BUILD_COUNT));
        /build.getBuildFeatures().forEach( it ->
                LOGGER.info(it.getType())
        );*/
        //LOGGER.info("count " + numPreviousBuildsToConsider);
    }

    // This event get also called when build get interrupted
    @Override
    public void beforeBuildFinish(@NotNull AgentRunningBuild build, BuildFinishedStatus buildStatus) {
        //AgentBuildFeature feature = getFeature(build);
        /*if (feature != null) {
            LOGGER.info("remove");
            //buildTimeoutHandler.handleBuild(build, feature, BuildStatus.FINISHING);
        }*/
    }

    private AgentBuildFeature getFeature(AgentRunningBuild build) {
        // ToDo: test that SBuilds getBuildFeaturesOfType() returns really just enabled ones (compared to the method from BuildTypeSettings)
        final Collection<AgentBuildFeature> avgBuildTimeFailureConditions = build.getBuildFeaturesOfType(ElasticTimeoutFailureCondition.TYPE);
        if(avgBuildTimeFailureConditions.size() != 1) {
            // if the build doesn't use our failure condition we just can skip
            return null;
        }
        return avgBuildTimeFailureConditions.stream().findFirst().get();
    }
}