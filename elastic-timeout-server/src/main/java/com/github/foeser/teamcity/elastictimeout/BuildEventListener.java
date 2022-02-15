package com.github.foeser.teamcity.elastictimeout;

import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class BuildEventListener extends BuildServerAdapter {

    private final BuildTimeoutHandler buildTimeoutHandler;
    // in order to work with logback we need to reference org.slf4j and not org.apache.log4j
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildEventListener.class);
    //private static final Logger LOGGER = org.apache.log4j.Logger.getLogger(BuildEventListener.class);
    public BuildEventListener(@NotNull EventDispatcher<BuildServerListener> events,
                              @NotNull BuildTimeoutHandler buildTimeoutHandler) {
            events.addListener(this);
            this.buildTimeoutHandler = buildTimeoutHandler;
    }

    @Override
    public void buildStarted(@NotNull SRunningBuild build) {
        SBuildFeatureDescriptor feature = getFeature(build);
        if (feature != null) {
            buildTimeoutHandler.handleBuild(build, feature, BuildStatus.STARTED);
        }
    }

    // This event get also called when build get interrupted
    @Override
    public void beforeBuildFinish(@NotNull SRunningBuild build) {
        SBuildFeatureDescriptor feature = getFeature(build);
        if (feature != null) {
            buildTimeoutHandler.handleBuild(build, feature, BuildStatus.FINISHING);
        }
    }

    private SBuildFeatureDescriptor getFeature(SRunningBuild build) {
        final Collection<SBuildFeatureDescriptor> elasticTimeoutFailureConditions = build.getBuildFeaturesOfType(ElasticTimeoutFailureCondition.TYPE);
        if(elasticTimeoutFailureConditions.size() == 0) {
            LOGGER.debug(String.format("%s doesn't have the elastic timeout failure condition set or enabled. Skipping...", build));
            return null;
        } else if(elasticTimeoutFailureConditions.size() > 1) {
            // should never happen due to isMultipleFeaturesPerBuildTypeAllowed is set to false
            LOGGER.error(String.format("%s has more then elastic timeout failure condition set!", build));
            return null;
        }
        return elasticTimeoutFailureConditions.stream().findFirst().get();
    }
}
