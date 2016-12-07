package org.jetbrains.teamcity.invitations;

import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.DuplicateProjectNameException;
import jetbrains.buildServer.serverSide.RelativeWebLinks;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.auth.AuthorityHolder;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.Role;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.web.util.SessionUser;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static java.util.stream.Collectors.toList;

public class CreateNewProjectInvitationType implements InvitationType<CreateNewProjectInvitationType.InvitationImpl> {
    @NotNull
    private final TeamCityCoreFacade core;

    public CreateNewProjectInvitationType(@NotNull TeamCityCoreFacade core) {
        this.core = core;
    }

    @NotNull
    @Override
    public String getId() {
        return "newProjectInvitation";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Invite user to create a project";
    }

    @NotNull
    @Override
    public String getDescriptionViewPath() {
        return core.getPluginResourcesPath("createNewProjectInvitationDescription.jsp");
    }

    @NotNull
    public InvitationImpl readFrom(@NotNull Element element) {
        return new InvitationImpl(element);
    }

    @Override
    public boolean isAvailableFor(AuthorityHolder authorityHolder, @NotNull SProject project) {
        return authorityHolder.isPermissionGrantedForProject(project.getProjectId(), Permission.CHANGE_USER_ROLES_IN_PROJECT) &&
                authorityHolder.isPermissionGrantedForProject(project.getProjectId(), Permission.CREATE_SUB_PROJECT);
    }

    @NotNull
    @Override
    public ModelAndView getEditPropertiesView(@Nullable InvitationImpl invitation) {
        ModelAndView modelAndView = new ModelAndView(core.getPluginResourcesPath("createNewProjectInvitationProperties.jsp"));
        modelAndView.getModel().put("roles", core.getAvailableRoles().stream().filter(Role::isProjectAssociationSupported).collect(toList()));
        modelAndView.getModel().put("name", invitation == null ? "New Project Invitation" : invitation.getName());
        modelAndView.getModel().put("multiuser", invitation == null ? "true" : invitation.multi);
        modelAndView.getModel().put("roleId", invitation == null ? "PROJECT_ADMIN" : invitation.roleId);
        modelAndView.getModel().put("newProjectName", invitation == null ? "{username} project" : invitation.newProjectName);
        return modelAndView;
    }

    @NotNull
    @Override
    public InvitationImpl createNewInvitation(@NotNull HttpServletRequest request, @NotNull SProject project, @NotNull String token) {
        String name = request.getParameter("name");
        String roleId = request.getParameter("role");
        String newProjectName = request.getParameter("newProjectName");
        boolean multiuser = Boolean.parseBoolean(request.getParameter("multiuser"));
        SUser currentUser = SessionUser.getUser(request);
        InvitationImpl invitation = new InvitationImpl(currentUser, name, token, project.getExternalId(), roleId, newProjectName, multiuser);
        if (!invitation.isAvailableFor(currentUser)) {
            throw new AccessDeniedException(currentUser, "You don't have permissions to create the invitation");
        }
        return invitation;
    }

    public final class InvitationImpl extends AbstractInvitation {
        @NotNull
        private final String parentExtId;
        @NotNull
        private final String roleId;
        @NotNull
        private final String newProjectName;

        InvitationImpl(@NotNull SUser currentUser, @NotNull String name, @NotNull String token, @NotNull String parentExtId, @NotNull String roleId,
                       @NotNull String newProjectName, boolean multi) {
            super(name, token, multi, CreateNewProjectInvitationType.this, currentUser.getId());
            this.roleId = roleId;
            this.parentExtId = parentExtId;
            this.newProjectName = newProjectName;
        }

        InvitationImpl(@NotNull Element element) {
            super(element, CreateNewProjectInvitationType.this);
            this.parentExtId = element.getAttributeValue("parentExtId");
            this.roleId = element.getAttributeValue("roleId");
            this.newProjectName = element.getAttributeValue("newProjectName");
        }

        @NotNull
        @Override
        protected String getLandingPage() {
            return core.getPluginResourcesPath("createNewProjectInvitationLanding.jsp");
        }

        @Override
        public void writeTo(@NotNull Element element) {
            super.writeTo(element);
            element.setAttribute("parentExtId", parentExtId);
            element.setAttribute("roleId", roleId);
            element.setAttribute("newProjectName", newProjectName);
        }

        @Override
        public boolean isAvailableFor(@NotNull AuthorityHolder user) {
            return user.isPermissionGrantedForProject(getProject().getProjectId(), Permission.CREATE_SUB_PROJECT) &&
                    user.isPermissionGrantedForProject(getProject().getProjectId(), Permission.CHANGE_USER_ROLES_IN_PROJECT);
        }

        @NotNull
        public ModelAndView userRegistered(@NotNull SUser user, @NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
            try {
                SProject parent = getProject();
                if (parent == null) {
                    throw new InvitationException("Failed to proceed invitation with a non-existing project " + parentExtId);
                }

                SProject createdProject = null;
                String baseName = newProjectName.replace("{username}", user.getUsername());
                String projectName = baseName;
                int i = 1;
                while (createdProject == null) {
                    try {
                        createdProject = core.createProjectAsSystem(parent.getExternalId(), projectName);
                    } catch (DuplicateProjectNameException e) {
                        projectName = baseName + i++;
                    }
                }

                Role role = core.findRoleById(this.roleId);
                if (role == null) {
                    throw new InvitationException("Failed to proceed invitation with a non-existing role " + roleId);
                }
                core.addRoleAsSystem(user, role, createdProject);
                Loggers.SERVER.info("User " + user.describe(false) + " registered on invitation '" + token + "'. " +
                        "Project " + createdProject.describe(false) + " created, user got the role " + role.describe(false));
                return new ModelAndView(new RedirectView(new RelativeWebLinks().getCreateConfigurationPageUrl(createdProject.getExternalId()), true));
            } catch (Exception e) {
                Loggers.SERVER.warn("Failed to create project for the invited user " + user.describe(false), e);
                return new ModelAndView(new RedirectView("/", true));
            }
        }

        @NotNull
        public SProject getProject() {
            return CreateNewProjectInvitationType.this.core.findProjectByExtId(parentExtId);
        }

        @Nullable
        public SUser getUser() {
            return CreateNewProjectInvitationType.this.core.getUser(createdByUserId);
        }
    }
}
