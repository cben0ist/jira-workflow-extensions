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
		// Get the user and current issue
		ApplicationUser user = this.authenticationContext.getLoggedInUser();
		MutableIssue currentIssue = getIssue(transientVars);

		// get the list of links selected on wf creation (Comma separated list of issueLinkType ids)
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

		String reversArg = (String) args.get(CreateSuccessorPostFunctionFactory.REVERSE);
		boolean reverse = false;
		if (reversArg != null) {
			reverse = (reversArg).equals("reverse");
		}

		// Create the new task
		IssueInputParameters issueInputParameters = issueService.newIssueInputParameters();
		issueInputParameters.setProjectId(currentIssue.getProjectId()).setIssueTypeId(currentIssue.getIssueTypeId())
				.setSummary(currentIssue.getSummary()).setReporterId(currentIssue.getReporterId()).setAssigneeId(currentIssue.getAssigneeId())
				.setDescription(currentIssue.getDescription()).setEnvironment(currentIssue.getEnvironment())
				// .setStatusId(currentIssue.getStatusId())
				.setPriorityId(currentIssue.getPriority().getId())
				// .setResolutionId(currentIssue.getResolutionId())
				.setSecurityLevelId(currentIssue.getSecurityLevelId())
		// .setFixVersionIds(currentIssue.getFixVersions());
		;

		/* TODO: set fixVersion and sprint ? */

		CreateValidationResult createValidationResult = issueService.validateCreate(user, issueInputParameters);

		if (createValidationResult.isValid()) {
			IssueResult createResult = issueService.create(user, createValidationResult);
			if (!createResult.isValid()) {
				log.error("Unable to clone issue", createResult.getErrorCollection());
			} else {
				// Link both task as successor/predecessor
				for (IssueLinkType issueLinkType : selectedIssueLinkTypes) {
					try {
						if (!reverse) {
							issueLinkManager.createIssueLink(createResult.getIssue().getId(), currentIssue.getId(), issueLinkType.getId(), null,
									user);
						} else {
							issueLinkManager.createIssueLink(currentIssue.getId(), createResult.getIssue().getId(), issueLinkType.getId(), null,
									user);
						}
					} catch (CreateException e) {
						log.error("Unable to link issue", e);
					}
				}
			}
		}
	}
}