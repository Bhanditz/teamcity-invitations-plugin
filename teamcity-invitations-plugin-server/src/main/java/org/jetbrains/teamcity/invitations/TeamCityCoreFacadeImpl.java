package org.jetbrains.teamcity.invitations;

import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.Role;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.serverSide.auth.RolesManager;
import jetbrains.buildServer.serverSide.identifiers.ProjectIdentifiersManager;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TeamCityCoreFacadeImpl implements TeamCityCoreFacade {
    private final RolesManager rolesManager;
    private final ProjectManager projectManager;
    private final ProjectIdentifiersManager projectIdentifiersManager;
    private final SecurityContextEx securityContext;
    private final ServerPaths serverPaths;
    private final PluginDescriptor pluginDescriptor;
    private final UserModel userModel;
    private final ConfigActionFactory myConfigActionFactory;

    public TeamCityCoreFacadeImpl(RolesManager rolesManager, ProjectManager projectManager, ProjectIdentifiersManager projectIdentifiersManager, SecurityContextEx securityContext,
                                  ServerPaths serverPaths, PluginDescriptor pluginDescriptor, UserModel userModel, ConfigActionFactory myConfigActionFactory) {
        this.rolesManager = rolesManager;
        this.projectManager = projectManager;
        this.projectIdentifiersManager = projectIdentifiersManager;
        this.securityContext = securityContext;
        this.serverPaths = serverPaths;
        this.pluginDescriptor = pluginDescriptor;
        this.userModel = userModel;
        this.myConfigActionFactory = myConfigActionFactory;
    }

    @Nullable
    @Override
    public Role findRoleById(String roleId) {
        return rolesManager.findRoleById(roleId);
    }

    @NotNull
    @Override
    public SProject createProjectAsSystem(@Nullable String parentExtId, @NotNull String name) {
        try {
            return securityContext.runAsSystem(() -> {
                SProject parent = projectManager.findProjectByExternalId(parentExtId);
                if (parent == null) {
                    throw new ProjectNotFoundException("Unable to create project for user: parent project with external id = " + parentExtId + " not found");
                }
                String projectExternalId = projectIdentifiersManager.generateNewExternalId(parentExtId, name, null);
                SProject project = parent.createProject(projectExternalId, name);
                project.persist();
                return project;
            });
        } catch (Throwable throwable) {
            ExceptionUtil.rethrowAsRuntimeException(throwable);
            return null;
        }
    }

    @Nullable
    @Override
    public SProject findProjectByExtId(String projectExtId) {
        return projectManager.findProjectByExternalId(projectExtId);
    }

    @Override
    public void addRoleAsSystem(@NotNull SUser user, @NotNull Role role, @NotNull SProject project) {
        try {
            securityContext.runAsSystem(() -> {
                user.addRole(RoleScope.projectScope(project.getProjectId()), role);
            });
        } catch (Throwable throwable) {
            ExceptionUtil.rethrowAsRuntimeException(throwable);
        }
    }

    @NotNull
    @Override
    public List<SProject> getActiveProjects() {
        return projectManager.getActiveProjects();
    }

    @Override
    public void persist(@NotNull SProject project, @NotNull String description) {
        project.persist(myConfigActionFactory.createAction(project, description));
    }

    @NotNull
    @Override
    public List<Role> getAvailableRoles() {
        return rolesManager.getAvailableRoles();
    }

    @Nullable
    @Override
    public SUser getUser(long userId) {
        return userModel.findUserById(userId);
    }

    @NotNull
    @Override
    public String getPluginResourcesPath(@NotNull String path) {
        return pluginDescriptor.getPluginResourcesPath(path);
    }
}
