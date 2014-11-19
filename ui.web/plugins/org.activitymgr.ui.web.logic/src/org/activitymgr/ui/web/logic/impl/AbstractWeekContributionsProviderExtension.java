package org.activitymgr.ui.web.logic.impl;

import java.util.Calendar;

import org.activitymgr.core.beans.Collaborator;
import org.activitymgr.core.beans.Contribution;
import org.activitymgr.core.beans.Task;
import org.activitymgr.core.beans.TaskContributions;

public abstract class AbstractWeekContributionsProviderExtension {

	public abstract TaskContributions[] getWeekContributions(LogicContext context, Collaborator contributor, Calendar firstDayOfWeek);

	public TaskContributions newTaskContributions(Collaborator contributor, Calendar firstDayOfWeek, Task task, String taskCodePath) {
		TaskContributions tc = new TaskContributions();
		tc.setTaskCodePath(taskCodePath);
		tc.setTask(task);
		tc.setContributions(new Contribution[7]);
		return tc;
	}

}
