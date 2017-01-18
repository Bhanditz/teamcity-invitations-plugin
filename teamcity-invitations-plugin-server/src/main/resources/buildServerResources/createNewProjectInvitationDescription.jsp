<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/include-internal.jsp" %>
<%--@elvariable id="invitation" type="org.jetbrains.teamcity.invitations.CreateNewProjectInvitationType.InvitationImpl"--%>

<div>
    Role:
    <c:choose>
        <c:when test="${invitation.role != null}">
            <c:out value="${invitation.role.name}"/>
        </c:when>
        <c:otherwise>
            not specified
        </c:otherwise>
    </c:choose>
    <br/>
    Reusable: <c:if test="${invitation.reusable}">Yes</c:if><c:if test="${!invitation.reusable}">No</c:if>
</div>