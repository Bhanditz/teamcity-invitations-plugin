package org.jetbrains.teamcity.invitations;

import jetbrains.buildServer.controllers.ActionMessages;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.admin.AdminPage;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.web.openapi.*;
import jetbrains.buildServer.web.util.SessionUser;
import jetbrains.buildServer.web.util.WebUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

public class InvitationAdminController extends BaseFormXmlController {

    public static final String MESSAGES_KEY = "teamcity.invitations.plugin";

    @NotNull
    private final WebControllerManager webControllerManager;
    @NotNull
    private final InvitationsStorage invitations;
    @NotNull
    private final TeamCityCoreFacade teamCityCoreFacade;
    @NotNull
    private final InvitationsController invitationsController;
    @NotNull
    private final List<InvitationType> invitationTypes;

    public InvitationAdminController(@NotNull PagePlaces pagePlaces,
                                     @NotNull WebControllerManager webControllerManager,
                                     @NotNull PluginDescriptor pluginDescriptor,
                                     @NotNull InvitationsStorage invitations,
                                     @NotNull TeamCityCoreFacade teamCityCoreFacade,
                                     @NotNull InvitationsController invitationsController,
                                     @NotNull List<InvitationType> invitationTypes) {
        this.webControllerManager = webControllerManager;
        this.invitations = invitations;
        this.teamCityCoreFacade = teamCityCoreFacade;
        this.invitationsController = invitationsController;
        this.invitationTypes = invitationTypes;
        new InvitationsAdminPage(pagePlaces, pluginDescriptor).register();
        webControllerManager.registerController("/admin/invitations.html", this);
        webControllerManager.registerAction(this, new CreateInvitationAction());
        webControllerManager.registerAction(this, new EditInvitationAction());
        webControllerManager.registerAction(this, new RemoveInvitationAction());
    }

    @Override
    protected ModelAndView doGet(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response) {
        if (request.getParameter("addInvitation") != null) {
            Optional<InvitationType> found = getInvitationType(request);
            if (!found.isPresent()) return null;

            if (!found.get().isAvailableFor(SessionUser.getUser(request))) {
                throw new AccessDeniedException(SessionUser.getUser(request), "You don't have permissions to create invitation of type " + found.get().getId());
            }

            return found.get().getEditPropertiesView(null);
        }

        if (request.getParameter("editInvitation") != null && request.getParameter("token") != null) {
            String token = request.getParameter("token");
            if (token == null) return null;
            Invitation found = invitations.getInvitation(token);
            if (found == null) return null;

            if (!found.isAvailableFor(SessionUser.getUser(request))) {
                throw new AccessDeniedException(SessionUser.getUser(request), "You don't have permissions to edit invitation " + found.getToken());
            }

            return found.getType().getEditPropertiesView(found);
        }

        return null;
    }

    @Override
    protected synchronized void doPost(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response, @NotNull final Element xmlResponse) {
        ControllerAction action = webControllerManager.getAction(this, request);
        if (action == null) {
            Loggers.SERVER.warn("Unrecognized request: " + WebUtil.getRequestDump(request));
        } else {
            action.process(request, response, xmlResponse);

        }
    }

    @NotNull
    private Invitation createFromRequest(String token, HttpServletRequest request) {
        Optional<InvitationType> invitationType = getInvitationType(request);
        if (!invitationType.isPresent()) {
            throw new InvitationException("Invitation type is not specified or doesn't exist");
        }
        Invitation created = invitationType.get().createNewInvitation(request, token);
        return invitations.addInvitation(token, created);
    }

    @NotNull
    private Optional<InvitationType> getInvitationType(@NotNull HttpServletRequest request) {
        String invitationType = request.getParameter("invitationType");
        if (invitationType == null) {
            Loggers.SERVER.warn("Unrecognized invitation type request: " + WebUtil.getRequestDump(request));
            return Optional.empty();
        }

        Optional<InvitationType> found = invitationTypes.stream().filter(type -> type.getId().equals(invitationType)).findFirst();
        if (!found.isPresent()) {
            Loggers.SERVER.warn("Unrecognized invitation type request: " + WebUtil.getRequestDump(request));
            return Optional.empty();
        }
        return found;
    }

    public class InvitationsAdminPage extends AdminPage {

        InvitationsAdminPage(@NotNull PagePlaces pagePlaces,
                             @NotNull PluginDescriptor pluginDescriptor) {
            super(pagePlaces, "invitations", pluginDescriptor.getPluginResourcesPath("invitationsAdmin.jsp"), "Invitations");
            setPosition(PositionConstraint.last());
        }

        @Override
        public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {
            model.put("invitationTypes", invitationTypes.stream().filter(invitationType -> invitationType.isAvailableFor(SessionUser.getUser(request))).collect(toList()));
            model.put("invitations", invitations.getInvitations().stream().filter(i -> i.isAvailableFor(SessionUser.getUser(request))).collect(toList()));
            model.put("invitationRootUrl", invitationsController.getInvitationsPath());
            model.put("projects", teamCityCoreFacade.getActiveProjects());
            model.put("roles", teamCityCoreFacade.getAvailableRoles());
        }

        @Override
        public boolean isAvailable(@NotNull HttpServletRequest request) {
            return super.isAvailable(request) && invitationTypes.stream().anyMatch(type -> type.isAvailableFor(SessionUser.getUser(request)));
        }

        @NotNull
        @Override
        public String getGroup() {
            return AdminPage.USER_MANAGEMENT_GROUP;
        }
    }

    private class CreateInvitationAction implements ControllerAction {
        @Override
        public boolean canProcess(@NotNull final HttpServletRequest request) {
            return request.getParameter("createInvitation") != null;
        }

        @Override
        public void process(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response, @Nullable final Element ajaxResponse) {
            Optional<InvitationType> invitationType = getInvitationType(request);
            if (invitationType.isPresent() && !invitationType.get().isAvailableFor(SessionUser.getUser(request))) {
                throw new AccessDeniedException(SessionUser.getUser(request), "You don't have permissions to create invitation of type " + invitationType.get().getId());
            }

            try {
                Invitation invitation = createFromRequest(StringUtil.generateUniqueHash(), request);
                ajaxResponse.setAttribute("token", invitation.getToken());
                ActionMessages.getOrCreateMessages(request).addMessage(MESSAGES_KEY, "Invitation '" + invitation.getName() + "' created.");
            } catch (AccessDeniedException e) {
                throw e;
            } catch (Exception e) {
                ActionMessages.getOrCreateMessages(request).addMessage(MESSAGES_KEY, e.getMessage());
            }
        }
    }

    private class EditInvitationAction implements ControllerAction {
        @Override
        public boolean canProcess(@NotNull HttpServletRequest request) {
            return request.getParameter("editInvitation") != null;
        }

        @Override
        public void process(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @Nullable Element element) {
            String token = request.getParameter("token");
            if (token != null) {
                Invitation invitation = invitations.getInvitation(token);
                if (invitation != null && !invitation.isAvailableFor(SessionUser.getUser(request))) {
                    throw new AccessDeniedException(SessionUser.getUser(request), "You don't have permissions to edit invitation " + token);
                }
            }

            try {
                invitations.removeInvitation(token);
                Invitation invitation = createFromRequest(token, request);
                ActionMessages.getOrCreateMessages(request).addMessage(MESSAGES_KEY, "Invitation '" + invitation.getName() + "' updated.");
            } catch (Exception e) {
                ActionMessages.getOrCreateMessages(request).addMessage(MESSAGES_KEY, e.getMessage());
            }
        }
    }

    private class RemoveInvitationAction implements ControllerAction {

        @Override
        public boolean canProcess(@NotNull final HttpServletRequest request) {
            return request.getParameter("removeInvitation") != null;
        }

        @Override
        public void process(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response, @Nullable final Element ajaxResponse) {
            String token = request.getParameter("removeInvitation");
            if (token != null) {
                Invitation invitation = invitations.getInvitation(token);
                if (invitation != null && !invitation.isAvailableFor(SessionUser.getUser(request))) {
                    throw new AccessDeniedException(SessionUser.getUser(request), "You don't have permissions to remove invitation " + token);
                }
            }

            Invitation invitation = invitations.removeInvitation(token);
            if (invitation != null) {
                ActionMessages.getOrCreateMessages(request).addMessage(MESSAGES_KEY, "Invitation '" + invitation.getName() + "' removed.");
            } else {
                ActionMessages.getOrCreateMessages(request).addMessage(MESSAGES_KEY, "Invitation '" + token + "' doesn't exist.");
            }
        }
    }
}