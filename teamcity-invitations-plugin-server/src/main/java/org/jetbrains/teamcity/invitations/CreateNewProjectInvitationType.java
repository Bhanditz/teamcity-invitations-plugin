package org.jetbrains.teamcity.invitations;

import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.DuplicateProjectNameException;
import jetbrains.buildServer.serverSide.RelativeWebLinks;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.Role;
import jetbrains.buildServer.users.SUser;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CreateNewProjectInvitationType implements InvitationType<CreateNewProjectInvitationType.InvitationImpl> {
    @NotNull
    private final TeamCityCoreFacade core;

    @NotNull
    private final RelativeWebLinks webLinks = new RelativeWebLinks();

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

    @NotNull
    @Override
    public ModelAndView getEditPropertiesView(@Nullable InvitationImpl invitation) {
        ModelAndView modelAndView = new ModelAndView(core.getPluginResourcesPath("createNewProjectInvitationProperties.jsp"));
        modelAndView.getModel().put("projects", core.getActiveProjects());
        modelAndView.getModel().put("roles", core.getAvailableRoles());
        modelAndView.getModel().put("multiuser", invitation == null ? "true" : invitation.multi);
        modelAndView.getModel().put("parentProjectId", invitation == null ? "_Root" : invitation.parentExtId);
        modelAndView.getModel().put("roleId", invitation == null ? "PROJECT_ADMIN" : invitation.roleId);
        return modelAndView;
    }

    @NotNull
    @Override
    public InvitationImpl createNewInvitation(HttpServletRequest request, String token) {
        String parentProjectExtId = request.getParameter("parentProject");
        String roleId = request.getParameter("role");
        boolean multiuser = Boolean.parseBoolean(request.getParameter("multiuser"));
        return new InvitationImpl(token, parentProjectExtId, roleId, multiuser);
    }

    public final class InvitationImpl extends AbstractInvitation {
        @NotNull
        private final String parentExtId;
        @NotNull
        private final String roleId;

        InvitationImpl(@NotNull String token, @NotNull String parentExtId, @NotNull String roleId, boolean multi) {
            super(token, multi, CreateNewProjectInvitationType.this);
            this.roleId = roleId;
            this.parentExtId = parentExtId;
        }

        InvitationImpl(@NotNull Element element) {
            super(element, CreateNewProjectInvitationType.this);
            this.parentExtId = element.getAttributeValue("parentExtId");
            this.roleId = element.getAttributeValue("roleId");
        }

        @Override
        public void writeTo(@NotNull Element element) {
            super.writeTo(element);
            element.setAttribute("parentExtId", parentExtId);
            element.setAttribute("roleId", roleId);
        }

        @NotNull
        public ModelAndView userRegistered(@NotNull SUser user, @NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
            try {
                SProject parent = getParent();
                if (parent == null) {
                    throw new InvitationException("Failed to proceed invitation with a non-existing project " + parentExtId);
                }

                SProject createdProject = null;
                String projectName = user.getUsername();
                int i = 1;
                while (createdProject == null) {
                    try {
                        createdProject = core.createProjectAsSystem(parent.getExternalId(), projectName);
                    } catch (DuplicateProjectNameException e) {
                        projectName = user.getUsername() + i++;
                    }
                }

                Role role = core.findRoleById(this.roleId);
                if (role == null) {
                    throw new InvitationException("Failed to proceed invitation with a non-existing role " + roleId);
                }
                core.addRoleAsSystem(user, role, createdProject);
                Loggers.SERVER.info("User " + user.describe(false) + " registered on invitation '" + token + "'. " +
                        "Project " + createdProject.describe(false) + " created, user got the role " + role.describe(false));
                return new ModelAndView(new RedirectView(webLinks.getEditProjectPageUrl(createdProject.getExternalId()), true));
            } catch (Exception e) {
                Loggers.SERVER.warn("Failed to create project for the invited user " + user.describe(false), e);
                return new ModelAndView(new RedirectView("/", true));
            }
        }

        @Nullable
        public SProject getParent() {
            return CreateNewProjectInvitationType.this.core.findProjectByExtId(parentExtId);
        }
    }
}
