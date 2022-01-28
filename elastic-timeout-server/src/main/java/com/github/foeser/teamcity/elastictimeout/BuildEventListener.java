package com.github.foeser.teamcity.elastictimeout;

import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.EventDispatcher;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.*;


public class BuildEventListener extends BuildServerAdapter {

    private final BuildTimeoutHandler buildTimeoutHandler;
    private static final Logger LOGGER = Logger.getLogger(BuildEventListener.class.getName());

    public BuildEventListener(@NotNull EventDispatcher<BuildServerListener> events,
                              @NotNull BuildTimeoutHandler buildTimeoutHandler) {
            events.addListener(this);
            this.buildTimeoutHandler = buildTimeoutHandler;
    }

    @Override
    public void buildStarted(@NotNull SRunningBuild build) {
        SBuildFeatureDescriptor feature = getElasticTimeoutFeature(build);
        if (feature != null) {
            buildTimeoutHandler.handleBuild(build, feature, BuildStatus.STARTED);
        }
    }

    // get also called when build get interrupted
    @Override
    public void beforeBuildFinish(@NotNull SRunningBuild build) {
        SBuildFeatureDescriptor feature = getElasticTimeoutFeature(build);
        if (feature != null) {
            buildTimeoutHandler.handleBuild(build, feature, BuildStatus.FINISHING);
        }
    }

    private SBuildFeatureDescriptor getElasticTimeoutFeature(SRunningBuild build) {
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
    }

    private SBuildFeatureDescriptor getFeature(SRunningBuild build) {
        // ToDo: test that SBuilds getBuildFeaturesOfType() returns really just enabled ones (compared to the method from BuildTypeSettings)
        final Collection<SBuildFeatureDescriptor> avgBuildTimeFailureConditions = build.getBuildFeaturesOfType(ElasticTimeoutFailureCondition.TYPE);
        if(avgBuildTimeFailureConditions.size() != 1) {
            // if the build doesn't use our failure condition we just can skip
            LOGGER.debug(String.format("%s [%s]", "Either none or more then one enabled AvgBuildTimeFailureCondition feature (failure condition) in that build", build));
            return null;
        }
        return avgBuildTimeFailureConditions.stream().findFirst().get();
    }
}
