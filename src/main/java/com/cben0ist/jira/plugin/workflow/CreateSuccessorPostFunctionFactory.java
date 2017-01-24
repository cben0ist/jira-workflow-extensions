package com.cben0ist.jira.plugin.workflow;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.link.IssueLinkType;
import com.atlassian.jira.issue.link.IssueLinkTypeManager;
import com.atlassian.jira.plugin.workflow.AbstractWorkflowPluginFactory;
import com.atlassian.jira.plugin.workflow.WorkflowPluginFunctionFactory;
import com.opensymphony.workflow.loader.AbstractDescriptor;
import com.opensymphony.workflow.loader.FunctionDescriptor;

/**
 * This is the factory class responsible for dealing with the UI for the post-function. This is typically where you put default values into the
 * velocity context and where you store user input.
 */

public class CreateSuccessorPostFunctionFactory extends AbstractWorkflowPluginFactory implements WorkflowPluginFunctionFactory {
	private static final Logger log = LoggerFactory.getLogger(CreateSuccessorPostFunctionFactory.class);

	public static final String SELECTED_ISSUE_LINK_TYPE_IDS = "selectedIssueLinkTypeIds";
	public static final String ISSUE_LINK_TYPES = "issueLinkTypes";
	public static final String REVERSE = "reverse";
	private final IssueLinkTypeManager issueLinkTypeManager;

	public CreateSuccessorPostFunctionFactory() {
		this.issueLinkTypeManager = ComponentAccessor.getComponent(IssueLinkTypeManager.class);
	}

	@Override
	protected void getVelocityParamsForInput(Map<String, Object> velocityParams) {
		// all available links
		velocityParams.put(ISSUE_LINK_TYPES, Collections.unmodifiableCollection(issueLinkTypeManager.getIssueLinkTypes()));
		velocityParams.put(REVERSE, "");
	}

	@Override
	protected void getVelocityParamsForEdit(Map<String, Object> velocityParams, AbstractDescriptor descriptor) {
		getVelocityParamsForInput(velocityParams);
		velocityParams.put(SELECTED_ISSUE_LINK_TYPE_IDS, getSelectedIssueLinkTypeId(descriptor));

		velocityParams.put(REVERSE, getSelectedReverse(descriptor));
	}

	@Override
	protected void getVelocityParamsForView(Map<String, Object> velocityParams, AbstractDescriptor descriptor) {

		Collection<Long> selectedIssueLinkTypeIds = getSelectedIssueLinkTypeId(descriptor);
		List<IssueLinkType> selectedIssueLinkTypes = new LinkedList<IssueLinkType>();
		for (Iterator<Long> iterator = selectedIssueLinkTypeIds.iterator(); iterator.hasNext();) {
			Long issueLinkTypeId = iterator.next();
			IssueLinkType selectedIssueLinkType = null;
			try {
				selectedIssueLinkType = issueLinkTypeManager.getIssueLinkType(issueLinkTypeId);
			} catch (Exception e) {
				log.warn("Invalid issueLinkTypeId", e);
			}
			if (selectedIssueLinkType != null) {
				selectedIssueLinkTypes.add(selectedIssueLinkType);
			}
		}
		velocityParams.put(ISSUE_LINK_TYPES, Collections.unmodifiableCollection(selectedIssueLinkTypes));

		velocityParams.put(REVERSE, getSelectedReverse(descriptor));
	}

	public Map<String, String> getDescriptorParams(Map<String, Object> functionParams) {
		Collection<String> issueLinkTypeIds = functionParams.keySet();
		StringBuffer iltIds = new StringBuffer();

		String reverse = "";
		for (Iterator<String> iterator = issueLinkTypeIds.iterator(); iterator.hasNext();) {
			String next = iterator.next();
			if ("reverse".equals(next)) {
				log.error("Next ?" + next);
				reverse = "reverse";
			} else {
				try {
					Long.parseLong(next);
					iltIds.append(next + ",");
				} catch (Exception e) {
				}
			}
		}
		String issueLinkIds = iltIds.length() > 0 ? iltIds.substring(0, iltIds.length() - 1) : "";

		Map<String, String> params = new HashMap<String, String>();
		params.put(ISSUE_LINK_TYPES, issueLinkIds);
		params.put(REVERSE, reverse);

		return params;
	}

	private Collection<Long> getSelectedIssueLinkTypeId(AbstractDescriptor descriptor) {
		Collection<Long> selectedIssueLinkTypeIds = new LinkedList<Long>();

		if (!(descriptor instanceof FunctionDescriptor)) {
			throw new IllegalArgumentException("Descriptor must be a FunctionDescriptor.");
		}

		FunctionDescriptor functionDescriptor = (FunctionDescriptor) descriptor;

		String issueLinkTypes = (String) functionDescriptor.getArgs().get(ISSUE_LINK_TYPES);
		StringTokenizer st = new StringTokenizer(issueLinkTypes, ",");

		while (st.hasMoreTokens()) {
			Long selectedIssueLinkTypeid = null;
			try {
				selectedIssueLinkTypeid = Long.parseLong(st.nextToken());
			} catch (Exception e) {
				// log.warn("Invalid issueLinkTypeId", e);
			}
			if (selectedIssueLinkTypeid != null) {
				selectedIssueLinkTypeIds.add(selectedIssueLinkTypeid);
			}
		}

		return selectedIssueLinkTypeIds;
	}

	private String getSelectedReverse(AbstractDescriptor descriptor) {
		log.error("getSelectedReverse");

		if (!(descriptor instanceof FunctionDescriptor)) {
			throw new IllegalArgumentException("Descriptor must be a FunctionDescriptor.");
		}

		FunctionDescriptor functionDescriptor = (FunctionDescriptor) descriptor;
		String selectedReverse = (String) functionDescriptor.getArgs().get(REVERSE);

		return selectedReverse;
	}

}
