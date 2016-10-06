<%@ taglib prefix="forms" uri="http://www.springframework.org/tags/form" %>
<%@ include file="/include-internal.jsp" %>
<jsp:useBean id="invitations" type="java.util.Map" scope="request"/>

<bs:linkScript>
    ${teamcityPluginResourcesPath}invitationsAdmin.js
</bs:linkScript>

<style type="text/css">
    #invitationsTable {
        margin-top: 1em;
    }

    #invitationsTable td,
    #invitationsTable th {
        padding: 0.6em 1em;
    }

    #invitationsTable .edit {
        vertical-align: top;
        width: 6%;
        padding-left: 0.5em;
        padding-right: 0.5em;
        white-space: nowrap;
    }

</style>

<div>
    <forms:addButton
            onclick="BS.InvitationsDialog.showCentered(); return false;">Create new invitation...</forms:addButton>
</div>

<bs:modalDialog formId="invitationsForm"
                title="Create Invitation"
                closeCommand="BS.InvitationsDialog.close();"
                action="/admin/invitations.html?createInvitation=1"
                saveCommand="BS.InvitationsDialog.submit();">

    <span class="greyNote">Invite user to create a project and give him administrator role in the project</span>

    <div class="clr spacing"></div>

    <label for="registrationUrl" class="tableLabel">Registration Endpoint: <l:star/></label>
    <forms:textField name="registrationUrl" value="/registerUser.html"/>
    <div class="clr spacing"></div>

    <label for="parentProject" class="tableLabel">Parent Project: <l:star/></label>
    <forms:select id="parentProject" name="parentProject">
        <c:forEach items="${projects}" var="project">

            <forms:option value="${project.externalId}" selected="${project.externalId eq '_Root'}"
                          title="${project.name}"><c:out value="${project.name}"/>
            </forms:option>
        </c:forEach>
    </forms:select>

    <div class="clr spacing"></div>
    <label for="multiuser" class="tableLabel">Multi-user</label>

    <forms:checkbox name="multiuser"
                    checked="true"
                    onmouseover="BS.Tooltip.showMessage(this, {shift: {x: 10, y: 20}, delay: 600}, 'Invitation can be used many times if checked')"
                    onmouseout="BS.Tooltip.hidePopup()"/>

    <div class="popupSaveButtonsBlock">
        <forms:submit id="createInvitationSumbit" label="Add"/>
        <forms:cancel onclick="BS.InvitationsDialog.close();"/>
        <forms:saving id="invitationsFormProgress"/>
    </div>
</bs:modalDialog>


<bs:refreshable containerId="invitationsList" pageUrl="${pageUrl}">

    <c:if test="${not empty invitations}">

        <h2>Pending invitations</h2>

        <table id="invitationsTable" class="highlightable parametersTable">
            <tr>
                <th>URL</th>
                <th>Description</th>
                <th colspan="2">Multi-user</th>
            </tr>
            <c:forEach items="${invitations}" var="invitation">
                <tr>
                    <td class="highlight">
                        <span class="clipboard-btn tc-icon icon16 tc-icon_copy" data-clipboard-action="copy"
                              data-clipboard-target="#token_${invitation.key}"></span>
                        <span id="token_${invitation.key}"><c:out
                                value="${invitationRootUrl}?token=${invitation.key}"/></span>
                    </td>
                    <td class="highlight">
                        <bs:out value="${invitation.value.description}"/>
                    </td>
                    <td class="highlight">
                        <c:out value="${invitation.value.multiUser}"/>
                    </td>
                    <td class="highlight edit">
                        <a href="#"
                           onclick="BS.Invitations.deleteInvitation('${invitation.key}'); return false">Delete</a>
                    </td>
                </tr>
            </c:forEach>
        </table>
    </c:if>
</bs:refreshable>

<script type="text/javascript">
    BS.Clipboard('.clipboard-btn');
</script>
