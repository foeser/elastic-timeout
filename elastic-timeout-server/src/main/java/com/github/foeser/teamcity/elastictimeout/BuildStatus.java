package com.github.foeser.teamcity.elastictimeout;

public enum BuildStatus {
    STARTED,
    // finishing in our case are builds which are going to be terminated (including interrupted ones)
    FINISHING
}
