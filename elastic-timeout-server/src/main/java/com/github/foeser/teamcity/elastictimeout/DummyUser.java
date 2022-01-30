package com.github.foeser.teamcity.elastictimeout;

import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.Permissions;
import jetbrains.buildServer.users.PropertyKey;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class DummyUser implements User {
    @Override
    public long getId() {
        return 0;
    }

    @Override
    public String getRealm() {
        return null;
    }

    @Override
    public String getUsername() {
        return "Elastic plugin";
    }

    @Override
    public String getName() {
        return "Elastic plugin";
    }

    @Override
    public String getEmail() {
        return null;
    }

    @Override
    public String getDescriptiveName() {
        return null;
    }

    @Override
    public String getExtendedName() {
        return null;
    }

    @Override
    public Date getLastLoginTimestamp() {
        return null;
    }

    @Override
    public List<String> getVisibleProjects() {
        return null;
    }

    @Override
    public List<String> getAllProjects() {
        return null;
    }

    @NotNull
    @Override
    public String describe(boolean b) {
        return null;
    }

    @Override
    public boolean isPermissionGrantedGlobally(@NotNull Permission permission) {
        return false;
    }

    @NotNull
    @Override
    public Permissions getGlobalPermissions() {
        return null;
    }

    @NotNull
    @Override
    public Map<String, Permissions> getProjectsPermissions() {
        return null;
    }

    @Override
    public boolean isPermissionGrantedForProject(@NotNull String s, @NotNull Permission permission) {
        return false;
    }

    @Override
    public boolean isPermissionGrantedForAllProjects(@NotNull Collection<String> collection, @NotNull Permission permission) {
        return false;
    }

    @Override
    public boolean isPermissionGrantedForAnyProject(@NotNull Permission permission) {
        return false;
    }

    @Override
    public boolean isPermissionGrantedForAnyOfProjects(@NotNull Collection<String> collection, @NotNull Permission permission) {
        return false;
    }

    @NotNull
    @Override
    public Permissions getPermissionsGrantedForProject(@NotNull String s) {
        return null;
    }

    @NotNull
    @Override
    public Permissions getPermissionsGrantedForAllProjects(@NotNull Collection<String> collection) {
        return null;
    }

    @NotNull
    @Override
    public Permissions getPermissionsGrantedForAnyOfProjects(@NotNull Collection<String> collection) {
        return null;
    }

    @Nullable
    @Override
    public User getAssociatedUser() {
        return null;
    }

    @Nullable
    @Override
    public String getPropertyValue(PropertyKey propertyKey) {
        return null;
    }

    @Override
    public boolean getBooleanProperty(PropertyKey propertyKey) {
        return false;
    }

    @NotNull
    @Override
    public Map<PropertyKey, String> getProperties() {
        return null;
    }
}
