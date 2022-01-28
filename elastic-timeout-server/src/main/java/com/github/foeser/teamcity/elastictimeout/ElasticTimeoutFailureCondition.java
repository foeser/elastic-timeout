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

public class ElasticTimeoutFailureCondition extends BuildFeature {
    public static final String TYPE = ElasticTimeoutFailureCondition.class.getName();

    public static final String PARAM_STATUS = "status_radio";
    public static final String PARAM_STATUS_SUCCESSFUL = "Successful";
    public static final String PARAM_BUILD_COUNT = "build_count";
    public static final String PARAM_EXCEED_VALUE = "exceed_value";
    public static final String PARAM_EXCEED_UNIT = "exceed_unit";
    public static final String PARAM_EXCEED_UNIT_PERCENT = "percent";

    private static final Logger LOGGER = Logger.getLogger(ElasticTimeoutFailureCondition.class.getName());

    private final String myEditUrl;

    public ElasticTimeoutFailureCondition(@NotNull BuildHistory buildHistory, @NotNull final PluginDescriptor descriptor) {
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

    /*private void logWarn(@NotNull SRunningBuild build, @NotNull String message) {
        build.getBuildLog().message(message, Status.WARNING, new Date(), null, BuildMessage1.DEFAULT_FLOW_ID, Collections.<String>emptyList());
    }*/
}
