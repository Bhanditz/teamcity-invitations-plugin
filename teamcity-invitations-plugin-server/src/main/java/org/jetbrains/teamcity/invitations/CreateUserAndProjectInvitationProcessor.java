package org.jetbrains.teamcity.invitations;

import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.Role;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class CreateUserAndProjectInvitationProcessor implements InvitationProcessor {
    private final String token;
    private final String registrationUrl;
    private final String parentProjectExternalId;
    private final Role role;
    private final TeamCityCoreFacade teamCityCore;

    CreateUserAndProjectInvitationProcessor(String token, String registrationUrl, String parentProjectExternalId, Role role, TeamCityCoreFacade teamCityCore) {
        this.token = token;
        this.registrationUrl = registrationUrl;
        this.parentProjectExternalId = parentProjectExternalId;
        this.role = role;
        this.teamCityCore = teamCityCore;
    }

    @Nullable
    @Override
    public ModelAndView processInvitationRequest(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
        request.getSession().setAttribute(Invitations.TOKEN_SESSION_ATTR, this);
        return new ModelAndView(new RedirectView(registrationUrl));
    }

    public boolean userRegistered(@NotNull SUser user, @NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
        try {
            SProject createdProject = teamCityCore.createProjectAsSystem(parentProjectExternalId, user.getUsername() + " project");
            teamCityCore.addRoleAsSystem(user, role, createdProject);

            Loggers.SERVER.info("User " + user.describe(false) + " registered on invitation '" + token + "'. " +
                    "Project " + createdProject.describe(false) + " created, user got the role " + role.describe(false));
            response.sendRedirect(teamCityCore.getEditProjectPageUrl(createdProject.getExternalId()));
            return false;
        } catch (Exception e) {
            Loggers.SERVER.warn("Failed to create project for the invited user " + user.describe(false), e);
            return true;
        }
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Register and create own project invitation";
    }

    @Override
    public boolean isMultiUser() {
        return true;
    }
}
