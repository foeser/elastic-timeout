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

    /*private SBuildFeatureDescriptor getElasticTimeoutFeature(SRunningBuild build) {
        SBuildType buildType = build.getBuildType();
        if (buildType == null) {
            return null;
        }

        for (SBuildFeatureDescriptor feature : buildType.getResolvedSettings().getBuildFeatures()) {
            if (ElasticTimeoutFailureCondition.TYPE.equals(feature.getType())) {
                return feature;
            }
        }

        return null;
    }*/

    private SBuildFeatureDescriptor getFeature(SRunningBuild build) {
        // ToDo: test that SBuilds getBuildFeaturesOfType() returns really just enabled ones (compared to the method from BuildTypeSettings)
        final Collection<SBuildFeatureDescriptor> avgBuildTimeFailureConditions = build.getBuildFeaturesOfType(ElasticTimeoutFailureCondition.TYPE);
        // ToDo: change handling, exception for more then one build and log when there is nothing
        if(avgBuildTimeFailureConditions.size() != 1) {
            // if the build doesn't use our failure condition we just can skip
            LOGGER.debug(String.format("%s [%s]", "Either none or more then one enabled AvgBuildTimeFailureCondition feature (failure condition) in that build", build));
            return null;
        }
        return avgBuildTimeFailureConditions.stream().findFirst().get();
    }
}
