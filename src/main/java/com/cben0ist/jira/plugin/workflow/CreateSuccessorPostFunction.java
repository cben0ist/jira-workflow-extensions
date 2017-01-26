package com.cben0ist.jira.plugin.workflow;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.bc.issue.IssueService.CreateValidationResult;
import com.atlassian.jira.bc.issue.IssueService.IssueResult;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.exception.CreateException;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.link.IssueLinkManager;
import com.atlassian.jira.issue.link.IssueLinkType;
import com.atlassian.jira.issue.link.IssueLinkTypeManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.workflow.function.issue.AbstractJiraFunctionProvider;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.WorkflowException;

/*
This is the post-function class that gets executed at the end of the transition.
Any parameters that were saved in your factory class will be available in the transientVars Map.
 */

public class CreateSuccessorPostFunction extends AbstractJiraFunctionProvider {

	private static final Logger log = LoggerFactory.getLogger(CreateSuccessorPostFunction.class);

	private final IssueLinkManager issueLinkManager;
	private final IssueLinkTypeManager issueLinkTypeManager;
	private final JiraAuthenticationContext authenticationContext;
	private final IssueService issueService;

	public CreateSuccessorPostFunction() {
		this.issueLinkManager = ComponentAccessor.getIssueLinkManager();
		this.issueLinkTypeManager = ComponentAccessor.getComponent(IssueLinkTypeManager.class);
		this.authenticationContext = ComponentAccessor.getJiraAuthenticationContext();
		// this.versionManager = ComponentAccessor.getVersionManager();
		this.issueService = ComponentAccessor.getIssueService();
	}

	public void execute(Map transientVars, Map args, PropertySet ps) throws WorkflowException {
		// Get the user and current issue details
		ApplicationUser user = this.authenticationContext.getLoggedInUser();
		MutableIssue currentIssue = getIssue(transientVars);

		// Create the new issue
		MutableIssue newIssue = null;
		try {
			newIssue = createIssue(user, currentIssue.getProjectId(), currentIssue.getIssueTypeId(), currentIssue.getSummary(),
					currentIssue.getReporterId(), currentIssue.getAssigneeId(), currentIssue.getDescription(), currentIssue.getEnvironment(),
					currentIssue.getPriority().getId(), currentIssue.getSecurityLevelId());
		} catch (WorkflowException e) {
			// This is a tentative if the previous issue cannot be created due to users problems (?!?)
			log.warn("Unable to create the issue, trying again with different users this time... " + e.getMessage());
			newIssue = createIssue(user, currentIssue.getProjectId(), currentIssue.getIssueTypeId(), currentIssue.getSummary(), user.getKey(),
					currentIssue.getProjectObject().getProjectLead().getKey(), currentIssue.getDescription(), currentIssue.getEnvironment(),
					currentIssue.getPriority().getId(), currentIssue.getSecurityLevelId());
		}

		// Link both issues together
		// get the list of links selected on wf creation (Comma separated list of issueLinkType ids)
		List<IssueLinkType> selectedIssueLinkTypes = getIssueLinkTypeList(args);
		boolean reverse = isReverse(args);

		// Link the 2 issues together
		linkTasks(user, currentIssue, selectedIssueLinkTypes, reverse, newIssue);

	}

	private MutableIssue createIssue(ApplicationUser user, Long projectId, String issueTypeId, String summary, String reporterId, String assigneeId,
			String description, String environment, String priorityId, Long securityLevelId) throws WorkflowException {
		// Create the new task
		IssueInputParameters issueInputParameters = issueService.newIssueInputParameters();
		issueInputParameters.setProjectId(projectId).setIssueTypeId(issueTypeId).setSummary(summary).setReporterId(reporterId)
				.setAssigneeId(assigneeId).setDescription(description).setEnvironment(environment).setPriorityId(priorityId)
				.setSecurityLevelId(securityLevelId);

		/* TODO: set fixVersion +1 and sprint +1 ? */

		CreateValidationResult createValidationResult = issueService.validateCreate(user, issueInputParameters);
		if (createValidationResult.isValid()) {
			IssueResult createResult = issueService.create(user, createValidationResult);
			if (createResult.isValid()) {
				return createResult.getIssue();
			} else {
				throw new WorkflowException("Unable to create the issue, IssueResult is not valid: " + createResult.getErrorCollection().toString());
			}
		} else {
			throw new WorkflowException(
					"Unable to create the issue, CreateValidationResult is not valid: " + createValidationResult.getErrorCollection().toString());
		}
	}

	private void linkTasks(ApplicationUser user, MutableIssue currentIssue, List<IssueLinkType> selectedIssueLinkTypes, boolean reverse,
			MutableIssue clonedIssue) {
		// Link both task as requested
		for (IssueLinkType issueLinkType : selectedIssueLinkTypes) {
			try {
				if (!reverse) {
					issueLinkManager.createIssueLink(clonedIssue.getId(), currentIssue.getId(), issueLinkType.getId(), null, user);
				} else {
					issueLinkManager.createIssueLink(currentIssue.getId(), clonedIssue.getId(), issueLinkType.getId(), null, user);
				}
			} catch (CreateException e) {
				log.error("Unable to link issue", e);
			}
		}
	}

	private List<IssueLinkType> getIssueLinkTypeList(Map args) {
		String issueLinkTypeids = (String) args.get(CreateSuccessorPostFunctionFactory.ISSUE_LINK_TYPES);
		StringTokenizer st = new StringTokenizer(issueLinkTypeids, ",");

		List<IssueLinkType> selectedIssueLinkTypes = new LinkedList<IssueLinkType>();
		while (st.hasMoreTokens()) {
			String issueLinkTypeId = st.nextToken();
			IssueLinkType selectedIssueLinkType = null;
			try {
				selectedIssueLinkType = issueLinkTypeManager.getIssueLinkType(Long.parseLong(issueLinkTypeId));
			} catch (Exception e) {
				// log.warn("Invalid issueLinkTypeId", e);
			}
			if (selectedIssueLinkType != null) {
				selectedIssueLinkTypes.add(selectedIssueLinkType);
			}

		}
		return selectedIssueLinkTypes;
	}

	private boolean isReverse(Map args) {
		String reversArg = (String) args.get(CreateSuccessorPostFunctionFactory.REVERSE);
		boolean reverse = false;
		if (reversArg != null) {
			reverse = (reversArg).equals("reverse");
		}
		return reverse;
	}
}