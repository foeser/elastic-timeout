package com.github.foeser.teamcity.elastictimeout;

import jetbrains.buildServer.serverSide.BuildFeature;
import jetbrains.buildServer.serverSide.BuildHistory;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AvgBuildTimeFailureCondition extends BuildFeature {
    public static final String TYPE = AvgBuildTimeFailureCondition.class.getName();

    public static final String PARAM_STATUS = "status_radio";
    public static final String PARAM_STATUS_SUCCESSFUL = "Successful";
    public static final String PARAM_BUILD_COUNT = "build_count";
    public static final String PARAM_EXCEED_VALUE = "exceed_value";
    public static final String PARAM_EXCEED_UNIT = "exceed_unit";
    public static final String PARAM_EXCEED_UNIT_PERCENT = "percent";

    private static final Logger LOGGER = Logger.getLogger(AvgBuildTimeFailureCondition.class.getName());

    private final String myEditUrl;

    public AvgBuildTimeFailureCondition(@NotNull BuildHistory buildHistory, @NotNull final PluginDescriptor descriptor) {
        myEditUrl = descriptor.getPluginResourcesPath("avgBuildTimeFailureConditionSettings.jsp");
    }

    @Override
    public PlaceToShow getPlaceToShow() {
        return PlaceToShow.FAILURE_REASON;
    }

    @NotNull
    @Override
    public String getType() {
        return TYPE;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "Fail build if time exceeds avg. time of previous ones";
    }

    @Nullable
    @Override
    public String getEditParametersUrl() {
        return myEditUrl;
    }

    @Override
    public boolean isMultipleFeaturesPerBuildTypeAllowed() {
        // having multiple timeout failure condition doesn't make sense
        return false;
    }

    @Override
    public boolean isRequiresAgent() {
        return false;
    }

    @NotNull
    @Override
    public String describeParameters(@NotNull Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        sb.append("Fail if current build time exceeds the avg. of the last");
        if(!getParameterWithDefaults(params, PARAM_BUILD_COUNT).isEmpty())
            sb.append( " " + getParameterWithDefaults(params, PARAM_BUILD_COUNT));
        if (getParameterWithDefaults(params, PARAM_STATUS).equals(PARAM_STATUS_SUCCESSFUL))
            sb.append(" successfull");
        sb.append(" builds by " + getParameterWithDefaults(params, PARAM_EXCEED_VALUE) + " " + getParameterWithDefaults(params, PARAM_EXCEED_UNIT));
        return sb.toString();
    }

    public String getParameterWithDefaults(Map<String, String> parameters, String name) {
        if (parameters.containsKey(name)) {
            return parameters.get(name);
        }

        Map<String, String> defaultParameters = getDefaultParameters();
        if (defaultParameters.containsKey(name)) {
            return defaultParameters.get(name);
        }

        return "UNDEFINED";
    }

    @Nullable
    @Override
    public PropertiesProcessor getParametersProcessor() {
        // ToDo: we need to validate the user input
        return new PropertiesProcessor() {
            public Collection<InvalidProperty> process(Map<String, String> params) {
                List<InvalidProperty> errors = new ArrayList<InvalidProperty>();
                return errors;
            }
        };
    }

    @Override
    public Map<String, String> getDefaultParameters() {
        final HashMap<String, String> map = new HashMap<String, String>();
        map.put(PARAM_STATUS, "Successful");
        map.put(PARAM_BUILD_COUNT, "5");
        map.put(PARAM_EXCEED_VALUE, "25");
        map.put(PARAM_EXCEED_UNIT, "percent");
        return map;
    }

    /*public void checkBuild(@NotNull SRunningBuild build, @NotNull SBuildFeatureDescriptor featureDescriptor) {
        int buildCount = Integer.valueOf(featureDescriptor.getParameters().get(PARAM_BUILD_COUNT));
        logWarn(build, "status param set to:" + featureDescriptor.getParameters().get(PARAM_STATUS));
        logWarn(build, "build count set to:" + featureDescriptor.getParameters().get(PARAM_BUILD_COUNT));
        List<SFinishedBuild> previousBuilds = myBuildHistory.getEntriesBefore(build, featureDescriptor.getParameters().get(PARAM_STATUS) == PARAM_STATUS_SUCCESSFUL);
        if(previousBuilds.size() < buildCount) {
            return;
        } else {
            logWarn(build, "builds to consider:" + previousBuilds.subList(0, buildCount));
            // calc avg time
            long totalTime = previousBuilds.subList(0, buildCount).stream().mapToLong(b -> b.getBuildStatistics(new BuildStatisticsOptions()).getTotalDuration()).sum();
            if(build.getElapsedTime() > totalTime / buildCount) {
                logWarn(build, "I'm running to long, please kill me!");
            }
        }
        // goes to system log
        //LOG.warn(previousBuilds);
    }

    private void logWarn(@NotNull SRunningBuild build, @NotNull String message) {
        build.getBuildLog().message(message, Status.WARNING, new Date(), null, BuildMessage1.DEFAULT_FLOW_ID, Collections.<String>emptyList());
    }*/
}
