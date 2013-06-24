package org.activitymgr.ui.web.logic.impl.internal;

import java.util.ArrayList;
import java.util.List;

import org.activitymgr.core.beans.TaskContributions;
import org.activitymgr.ui.web.logic.impl.AbstractContributionLogicImpl;
import org.activitymgr.ui.web.logic.impl.AbstractSafeContributionsActionHandler;

public class AddTaskContributionActionHandler extends
	AbstractSafeContributionsActionHandler {

	@Override
	public String getLabel() {
		return "New task";
	}

	@Override
	public void unsafeHandle(AbstractContributionLogicImpl logic) {
		List<Long> selectedTaskIds = new ArrayList<Long>();
		for (TaskContributions tc : logic.getWeekContributions()) {
			selectedTaskIds.add(tc.getTask().getId());
		}
		new TaskChooserLogicImpl(logic, selectedTaskIds);
	}

}