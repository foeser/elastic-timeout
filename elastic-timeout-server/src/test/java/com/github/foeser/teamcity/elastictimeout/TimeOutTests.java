package com.github.foeser.teamcity.elastictimeout;

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.util.EventDispatcher;
import org.jmock.Mockery;
import org.jmock.Expectations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class TimeOutTests extends BaseTestCase {
    private SRunningBuild sRunningBuild;
    private SBuild sBuild;
    private Mockery m;
    private BuildEventListener buildEventListener;

    private SFinishedBuild build3;
    private SFinishedBuild build2;
    private SFinishedBuild build1;

    @BeforeMethod
    @Override
    public void setUp() throws Exception {
        super.setUp();
        m = new Mockery();
        final EventDispatcher<BuildServerListener> eventDispatcher = EventDispatcher.create(BuildServerListener.class);
        final ExecutorServices executorServices = m.mock(ExecutorServices.class);
        final RunningBuildsManager runningBuildsManager = m.mock(RunningBuildsManager.class);
        final BuildHistory buildHistory = m.mock(BuildHistory.class);
        sRunningBuild = m.mock(SRunningBuild.class);
        sBuild = m.mock(SBuild.class);
        // https://github.com/sferencik/SinCity/blob/master/sin-city-server/src/test/java/sferencik/teamcity/sincity/FinishedBuildWithChangesTest.java
        final SBuildFeatureDescriptor mockbf = m.mock(SBuildFeatureDescriptor.class);
        build1 = m.mock(SFinishedBuild.class, "Build1");
        build2 = m.mock(SFinishedBuild.class, "Build2");
        build3 = m.mock(SFinishedBuild.class, "Build3");

        m.checking(new Expectations() {
            {
                allowing(mockbf).getType(); will (returnValue(ElasticTimeoutFailureCondition.TYPE));
                allowing(sRunningBuild).getBuildFeaturesOfType(ElasticTimeoutFailureCondition.TYPE); will (returnValue(Collections.singleton(mockbf)));
                allowing(mockbf).getParameters(); will (returnValue(Collections.singletonMap(ElasticTimeoutFailureCondition.PARAM_BUILD_COUNT, "99")));
                ScheduledExecutorService ses = new ScheduledThreadPoolExecutor(1);
                allowing(executorServices).getNormalExecutorService(); will(returnValue(ses));
                allowing(buildHistory.getEntriesBefore(sBuild, true)); will (returnValue(Arrays.asList(build1, build2, build3)));
            }});
        buildEventListener = new BuildEventListener(eventDispatcher,new BuildTimeoutHandler(executorServices, runningBuildsManager, buildHistory));
    }

    @Test
    public void addBuild() throws IOException {
        buildEventListener.buildStarted(sRunningBuild);
    }
}
