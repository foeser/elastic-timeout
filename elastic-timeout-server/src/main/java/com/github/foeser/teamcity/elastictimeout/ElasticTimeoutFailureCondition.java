package com.github.foeser.teamcity.elastictimeout;

import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.BuildFeature;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ElasticTimeoutFailureCondition extends BuildFeature {
    public static final String TYPE = ElasticTimeoutFailureCondition.class.getName();

    public static final String PARAM_STATUS = "status_radio";
    public static final String PARAM_STATUS_SUCCESSFUL = "Successful";
    public static final String PARAM_BUILD_COUNT = "build_count";
    public static final String PARAM_EXCEED_VALUE = "exceed_value";
    public static final String PARAM_EXCEED_UNIT = "exceed_unit";
    public static final String PARAM_EXCEED_UNIT_PERCENT = "percent";
    public static final String PARAM_STOP_BUILD = "stop_build";

    //private static final Logger LOGGER = Logger.getLogger(ElasticTimeoutFailureCondition.class.getName());
    private static final Logger LOGGER = Logger.getLogger(Loggers.SERVER_CATEGORY);

    private final String myEditUrl;

    public ElasticTimeoutFailureCondition(@NotNull final PluginDescriptor descriptor) {
        myEditUrl = descriptor.getPluginResourcesPath("ElasticTimeoutFailureConditionSettings.jsp");
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
        return "Fail build if its execution time exceeds the average time of previous ones (with a given threshold)";
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
        // ToDo: align with desc from metric based fail condition to be more consistent
        StringBuilder sb = new StringBuilder();
        LOGGER.log(Level.WARN, "param: " + getParameterWithDefaults(params, PARAM_STOP_BUILD));
        if(getParameterWithDefaults(params, PARAM_STOP_BUILD).equals("true")) {
            sb.append("Fail if current build time exceeds the avg. of the last");
        } else {
            sb.append("Add build problem if current build time exceeds the avg. of the last");
        }

        if(!getParameterWithDefaults(params, PARAM_BUILD_COUNT).isEmpty()) {
            sb.append(" ").append(getParameterWithDefaults(params, PARAM_BUILD_COUNT));
        }

        if (getParameterWithDefaults(params, PARAM_STATUS).equals(PARAM_STATUS_SUCCESSFUL)) {
            sb.append(" successful");
        }

        sb.append(" builds by ").append(getParameterWithDefaults(params, PARAM_EXCEED_VALUE)).append(" ").append(getParameterWithDefaults(params, PARAM_EXCEED_UNIT));
        return sb.toString();
    }

    public String getParameterWithDefaults(Map<String, String> parameters, String name) {
        if (parameters.containsKey(name)) {
            return parameters.get(name);
        }

        Map<String, String> defaultParameters = getDefaultParameters();
        if (defaultParameters != null && defaultParameters.containsKey(name)) {
            return defaultParameters.get(name);
        }

        return "UNDEFINED";
    }

    @Nullable
    @Override
    public PropertiesProcessor getParametersProcessor() {
        return params -> {
            List<InvalidProperty> errors = new ArrayList<>();

            String buildCount = params.get(PARAM_BUILD_COUNT);
            if (StringUtil.isEmptyOrSpaces(buildCount)) {
                errors.add(new InvalidProperty(PARAM_BUILD_COUNT, "You need to define a build count."));
            } else if (!StringUtil.isAPositiveNumber(buildCount)) {
                errors.add(new InvalidProperty(PARAM_BUILD_COUNT, "Only positive numbers are allowed for the build count."));
            } else if (StringUtil.parseInt(buildCount, 2) < 2) {
                errors.add(new InvalidProperty(PARAM_BUILD_COUNT, "The minimum build count is 2."));
            }

            String exceedValue = params.get(PARAM_EXCEED_VALUE);
            if (StringUtil.isEmptyOrSpaces(exceedValue)) {
                errors.add(new InvalidProperty(PARAM_EXCEED_VALUE, "You need to define a threshold value."));
            } else if (!StringUtil.isAPositiveNumber(exceedValue)) {
                errors.add(new InvalidProperty(PARAM_EXCEED_VALUE, "Only positive numbers are allowed for the threshold."));
            }
            return errors;
        };
    }

    @Override
    public Map<String, String> getDefaultParameters() {
        final HashMap<String, String> map = new HashMap<>();
        map.put(PARAM_STATUS, "Successful");
        map.put(PARAM_BUILD_COUNT, "5");
        map.put(PARAM_EXCEED_VALUE, "25");
        map.put(PARAM_EXCEED_UNIT, "seconds");
        map.put(PARAM_STOP_BUILD, "true");
        return map;
    }

    /*private void logWarn(@NotNull SRunningBuild build, @NotNull String message) {
        build.getBuildLog().message(message, Status.WARNING, new Date(), null, BuildMessage1.DEFAULT_FLOW_ID, Collections.<String>emptyList());
    }*/
}
