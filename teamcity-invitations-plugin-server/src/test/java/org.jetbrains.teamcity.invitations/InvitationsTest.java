package org.jetbrains.teamcity.invitations;

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.RootUrlHolder;
import jetbrains.buildServer.controllers.ActionMessages;
import jetbrains.buildServer.controllers.AuthorizationInterceptor;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.*;
import jetbrains.buildServer.serverSide.impl.auth.SecurityContextImpl;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.XmlUtil;
import jetbrains.buildServer.web.impl.PagePlacesRegistry;
import jetbrains.buildServer.web.impl.TeamCityInternalKeys;
import jetbrains.buildServer.web.impl.WebControllerManagerImpl;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.util.SessionUser;
import jetbrains.spring.web.UrlMapping;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.StringReader;
import java.util.List;

import static java.util.Arrays.asList;
import static jetbrains.buildServer.serverSide.auth.RoleScope.projectScope;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Test
public class InvitationsTest extends BaseTestCase {

    private InvitationsStorage invitations;
    private InvitationsController invitationsController;
    private InvitationAdminController invitationsAdminController;
    private CreateNewProjectInvitationType createNewProjectInvitationType;
    private JoinProjectInvitationType joinProjectInvitationType;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockHttpSession session;
    private FakeTeamCityCoreFacade core;
    private SProject testDriveProject;
    private SecurityContextImpl securityContext;

    private Role adminRole;
    private Role developerRole;
    private Role systemAdminRole;
    private SUser systemAdmin;

    @BeforeMethod
    public void setUp() throws Exception {
        super.setUp();
        core = new FakeTeamCityCoreFacade(createTempDir());
        systemAdminRole = core.addRole("SYSTEM_ADMIN", new Permissions(Permission.values()));
        adminRole = core.addRole("PROJECT_ADMIN", new Permissions(Permission.CREATE_SUB_PROJECT, Permission.CHANGE_USER_ROLES_IN_PROJECT));
        developerRole = core.addRole("PROJECT_DEVELOPER", new Permissions(Permission.RUN_BUILD));

        core.createProjectAsSystem(null, "_Root");
        testDriveProject = core.createProjectAsSystem("_Root", "TestDriveProjectId");
        securityContext = new SecurityContextImpl();
        createNewProjectInvitationType = new CreateNewProjectInvitationType(core, securityContext);
        joinProjectInvitationType = new JoinProjectInvitationType(core, securityContext);
        invitations = createInvitationStorage();

        systemAdmin = core.createUser("admin");
        systemAdmin.addRole(RoleScope.globalScope(), systemAdminRole);

        WebControllerManagerImpl webControllerManager = new WebControllerManagerImpl();
        webControllerManager.setHandlerMapping(new UrlMapping());
        invitationsController = new InvitationsController(webControllerManager, invitations, Mockito.mock(AuthorizationInterceptor.class),
                Mockito.mock(RootUrlHolder.class));

        PluginDescriptor pluginDescriptor = Mockito.mock(PluginDescriptor.class);
        when(pluginDescriptor.getPluginResourcesPath(anyString())).thenReturn("fake.jsp");
        invitationsAdminController = new InvitationAdminController(new PagePlacesRegistry(), webControllerManager,
                pluginDescriptor, invitations, core, invitationsController, asList(createNewProjectInvitationType, joinProjectInvitationType));

        newRequest(HttpMethod.GET, "/");
    }

    @NotNull
    private InvitationsStorage createInvitationStorage() {
        return new InvitationsStorage(core, asList(createNewProjectInvitationType, joinProjectInvitationType));
    }

    @Test
    public void invite_user_to_create_a_project() throws Exception {
        login(systemAdmin);
        String token = createInvitationToCreateProject("PROJECT_ADMIN", "TestDriveProjectId", "{username} project", true).getToken();

        //user go to invitation url
        logout();
        ModelAndView invitationResponse = goToInvitationUrl(token);
        assertRedirectTo(invitationResponse, "/login.html");

        //user registered
        SUser user = core.createUser("oleg");
        login(user);
        ModelAndView afterRegistrationMAW = goToAfterRegistrationUrl();
        then(afterRegistrationMAW.getView()).isInstanceOf(RedirectView.class);
        then(core.getProject("oleg project")).isNotNull();
        then(core.getProject("oleg project").getParentProjectExternalId()).isEqualTo("TestDriveProjectId");
        then(user.getRolesWithScope(projectScope("oleg project"))).extracting(Role::getId).contains("PROJECT_ADMIN");
    }

    @Test
    public void invite_user_to_join_the_project() throws Exception {
        login(systemAdmin);
        String token = createInvitationToJoinProject("PROJECT_DEVELOPER", "TestDriveProjectId", true).getToken();

        //user go to invitation url
        logout();
        ModelAndView invitationResponse = goToInvitationUrl(token);
        assertRedirectTo(invitationResponse, "/login.html");

        //user registered
        SUser user = core.createUser("oleg");
        login(user);
        ModelAndView afterRegistrationMAW = goToAfterRegistrationUrl();
        then(afterRegistrationMAW.getView()).isInstanceOf(RedirectView.class);
        then(user.getRolesWithScope(projectScope("TestDriveProjectId"))).extracting(Role::getId).contains("PROJECT_DEVELOPER");
    }

    @Test
    public void project_with_such_name_already_exists() throws Exception {
        login(systemAdmin);
        String token = createInvitationToCreateProject("PROJECT_ADMIN", "TestDriveProjectId", "{username} project", true).getToken();
        core.createProjectAsSystem("TestDriveProjectId", "oleg project");

        //user go to invitation url
        logout();
        ModelAndView invitationResponse = goToInvitationUrl(token);
        assertRedirectTo(invitationResponse, "/login.html");

        //user registered
        SUser user = core.createUser("oleg");
        login(user);
        ModelAndView afterRegistrationMAW = goToAfterRegistrationUrl();
        then(afterRegistrationMAW.getView()).isInstanceOf(RedirectView.class);
        then(core.getProject("oleg project1")).isNotNull();
        then(user.getRolesWithScope(projectScope("oleg project1"))).extracting(Role::getId).contains("PROJECT_ADMIN");
    }

    public void should_survive_server_restart() throws Exception {
        login(systemAdmin);
        String token1 = createInvitationToCreateProject("PROJECT_ADMIN", "TestDriveProjectId", "{username} project", true).getToken();
        String token2 = createInvitationToJoinProject("PROJECT_DEVELOPER", "_Root", false).getToken();
        Element beforeRestartEl1 = new Element("invitation");
        Element beforeRestartEl2 = new Element("invitation");
        invitations.getInvitation(token1).writeTo(beforeRestartEl1);
        invitations.getInvitation(token2).writeTo(beforeRestartEl2);

        invitations = createInvitationStorage();

        then(invitations.getInvitation(token1)).isNotNull();
        then(invitations.getInvitation(token2)).isNotNull();

        Element afterRestartEl1 = new Element("invitation");
        Element afterRestartEl2 = new Element("invitation");
        invitations.getInvitation(token1).writeTo(afterRestartEl1);
        invitations.getInvitation(token2).writeTo(afterRestartEl2);
        then(XmlUtil.to_s(afterRestartEl1)).isEqualTo(XmlUtil.to_s(beforeRestartEl1));
        then(XmlUtil.to_s(afterRestartEl2)).isEqualTo(XmlUtil.to_s(beforeRestartEl2));
    }

    public void remove_invitation() throws Exception {
        login(systemAdmin);
        String token = createInvitationToCreateProject("PROJECT_ADMIN", "TestDriveProjectId", "{username} project", true).getToken();
        invitations.removeInvitation(token);

        then(invitations.getInvitation(token)).isNull();

        //user go to invitation url
        logout();
        ModelAndView invitationResponse = goToInvitationUrl(token);
        assertRedirectTo(invitationResponse, "/");
    }

    public void invitation_removed_during_user_registration() throws Exception {
        login(systemAdmin);
        String token = createInvitationToCreateProject("PROJECT_ADMIN", "TestDriveProjectId", "{username} project", true).getToken();

        //user go to invitation url
        logout();
        goToInvitationUrl(token);

        invitations.removeInvitation(token);

        SUser user = core.createUser("oleg");
        login(user);
        ModelAndView afterRegistrationMAW = goToAfterRegistrationUrl();
        assertRedirectTo(afterRegistrationMAW, "/");
    }

    public void multiple_user_invitation_can_be_used_several_times() throws Exception {
        login(systemAdmin);
        String token = createInvitationToCreateProject("PROJECT_ADMIN", "TestDriveProjectId", "{username} project", true).getToken();

        //first
        logout();
        assertRedirectTo(goToInvitationUrl(token), "/login.html");
        login(core.createUser("oleg"));
        goToAfterRegistrationUrl();

        //second
        logout();
        assertRedirectTo(goToInvitationUrl(token), "/login.html");
        login(core.createUser("ivan"));
        goToAfterRegistrationUrl();

        then(core.getProject("oleg project")).isNotNull();
        then(core.getProject("ivan project")).isNotNull();
    }

    public void single_user_invitation_can_be_used_once() throws Exception {
        login(systemAdmin);
        String token = createInvitationToCreateProject("PROJECT_ADMIN", "TestDriveProjectId", "{username} project", false).getToken();

        //first
        logout();
        assertRedirectTo(goToInvitationUrl(token), "/login.html");
        login(core.createUser("oleg"));
        goToAfterRegistrationUrl();

        //second
        assertRedirectTo(goToInvitationUrl(token), "/");
    }

    public void user_cant_invite_project_admin_to_inaccessible_project() throws Exception {
        SUser projectAdmin = core.createUser("oleg");
        projectAdmin.addRole(projectScope(testDriveProject.getProjectId()), adminRole);
        login(projectAdmin);

        newRequest(HttpMethod.GET, "/admin/invitations.html?addInvitation=1&invitationType=newProjectInvitation");
        ModelAndView modelAndView = invitationsAdminController.handleRequestInternal(request, response);
        then(((List<SProject>) modelAndView.getModel().get("projects"))).containsOnly(testDriveProject);

        try {
            createInvitationToCreateProject(adminRole.getId(), "_Root", "{username} project", true); //can't invite to roo
            fail("Access denied expected");
        } catch (AccessDeniedException ignored) {
        }
    }

    public void user_cant_invite_project_admin_without_assign_role_permission() throws Exception {
        SUser oleg = core.createUser("oleg");
        Role role = core.addRole("PROJECT_ADMIN2", new Permissions(Permission.CREATE_SUB_PROJECT));//no CHANGE_USER_ROLES_IN_PROJECT permission
        oleg.addRole(projectScope(testDriveProject.getProjectId()), role);
        login(oleg);

        newRequest(HttpMethod.GET, "/admin/invitations.html?addInvitation=1&invitationType=newProjectInvitation");
        invitationsAdminController.handleRequestInternal(request, response);
        then(ActionMessages.getMessages(request).getMessage("accessDenied")).isNotNull();

        try {
            createInvitationToCreateProject(adminRole.getId(), testDriveProject.getExternalId(), "{username} project", true);
            fail("Access denied expected");
        } catch (AccessDeniedException ignored) {
        }
    }

    public void user_cant_edit_invitation_without_necessary_permission() throws Exception {
        login(systemAdmin);
        String token = createInvitationToCreateProject("PROJECT_ADMIN", "_Root", "{username} project", false).getToken();

        SUser oleg = core.createUser("oleg");
        oleg.addRole(projectScope(testDriveProject.getProjectId()), adminRole);
        login(oleg);

        //try to open edit page
        newRequest(HttpMethod.GET, "/admin/invitations.html?editInvitation=1&token=" + token);
        invitationsAdminController.handleRequestInternal(request, response);
        then(ActionMessages.getMessages(request).getMessage("accessDenied")).isNotNull();

        //try to submit edit
        newRequest(HttpMethod.POST, "/admin/invitations.html?editInvitation=1&token=" + token);
        invitationsAdminController.handleRequestInternal(request, response);
        then(ActionMessages.getMessages(request).getMessage("accessDenied")).isNotNull();

        //try to remove
        newRequest(HttpMethod.POST, "/admin/invitations.html?removeInvitation=" + token);
        invitationsAdminController.handleRequestInternal(request, response);
        then(ActionMessages.getMessages(request).getMessage("accessDenied")).isNotNull();
    }

    private ModelAndView goToAfterRegistrationUrl() throws Exception {
        newRequest(HttpMethod.GET, (String) request.getSession().getAttribute(TeamCityInternalKeys.FIRST_LOGIN_REDIRECT_URL));
        return invitationsController.doHandle(request, response);
    }

    private ModelAndView goToInvitationUrl(String token) throws Exception {
        newRequest(HttpMethod.GET, "/invitations.html?token=" + token);
        return invitationsController.doHandle(request, response);
    }

    private void assertRedirectTo(ModelAndView invitationResponse, String expectedRedirect) {
        then(invitationResponse.getView()).isInstanceOf(RedirectView.class);
        then(((RedirectView) invitationResponse.getView()).getUrl()).isEqualTo(expectedRedirect);
    }

    private void newRequest(HttpMethod method, String url) {
        if (session == null) session = new MockHttpSession();
        request = MockMvcRequestBuilders.request(method, url).session(session).buildRequest(new MockServletContext());
        response = new MockHttpServletResponse();
        ActionMessages messages = ActionMessages.getMessages(request);
        if (messages != null) messages.clearMessages();
    }

    private Invitation createInvitationToCreateProject(String role, String parentExtId, String newProjectName, boolean multiuser) throws Exception {
        newRequest(HttpMethod.POST, "/admin/invitations.html?createInvitation=1");
        request.addParameter("name", "Create Project Invitation");
        request.addParameter("invitationType", createNewProjectInvitationType.getId());
        request.addParameter("parentProject", parentExtId);
        request.addParameter("role", role);
        request.addParameter("multiuser", multiuser + "");
        request.addParameter("newProjectName", newProjectName);
        invitationsAdminController.handleRequestInternal(request, response);
        if (ActionMessages.getMessages(request) != null && ActionMessages.getMessages(request).getMessage("accessDenied") != null) {
            throw new AccessDeniedException(securityContext.getAuthorityHolder(), ActionMessages.getMessages(request).getMessage("accessDenied"));
        }
        Element resp = FileUtil.parseDocument(new StringReader(response.getContentAsString()), false);
        String token = resp.getAttributeValue("token");
        return invitations.getInvitation(token);
    }

    private Invitation createInvitationToJoinProject(String role, String projectExtId, boolean multiuser) throws Exception {
        newRequest(HttpMethod.POST, "/admin/invitations.html?createInvitation=1");
        request.addParameter("invitationType", joinProjectInvitationType.getId());
        request.addParameter("name", "Join Project Invitation");
        request.addParameter("project", projectExtId);
        request.addParameter("role", role);
        request.addParameter("multiuser", multiuser + "");
        invitationsAdminController.handleRequestInternal(request, response);
        Element resp = FileUtil.parseDocument(new StringReader(response.getContentAsString()), false);
        String token = resp.getAttributeValue("token");
        return invitations.getInvitation(token);
    }

    private void login(SUser user) {
        logout();
        securityContext.setAuthorityHolder(user);
        SessionUser.setUser(request, user);
    }

    private void logout() {
        securityContext.clearContext();
        if (SessionUser.getUser(request) != null) SessionUser.removeUser(request);
    }
}

