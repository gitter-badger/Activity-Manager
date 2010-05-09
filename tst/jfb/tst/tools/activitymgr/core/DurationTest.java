package jfb.tst.tools.activitymgr.core;

import java.util.GregorianCalendar;

import jfb.tools.activitymgr.core.DbException;
import jfb.tools.activitymgr.core.ModelException;
import jfb.tools.activitymgr.core.ModelMgr;
import jfb.tools.activitymgr.core.beans.Collaborator;
import jfb.tools.activitymgr.core.beans.Contribution;
import jfb.tools.activitymgr.core.beans.Task;
import jfb.tst.tools.activitymgr.AbstractModelTestCase;

public class DurationTest extends AbstractModelTestCase {

	public void testGetList() throws DbException {
		long[] durations = ModelMgr.getDurations();
		long previousDuration = -1;
		for (int i=0; i<durations.length; i++) {
			long duration = durations[i];
			assertTrue("Dur�e nulle", duration!=0);
			assertTrue("Dur�e non positive", duration>0);
			assertTrue("Durations are not correctly sorted", duration>previousDuration);
			previousDuration = duration;
		}
	}

	public void testNullCreation() throws DbException {
		// Cr�ation de dur�e nulle
		try {
			ModelMgr.createDuration(0);
			fail("Manage to create a null duration");
		}
		catch (ModelException ignored) {
			// success!
		}
	}

	public void testConflictingCreation() throws DbException, ModelException {
		// Cr�ation d'une dur�e
		long newDuration = generateNewDuration();
		ModelMgr.createDuration(newDuration);
		try {
			// Tentative de recr�ation
			ModelMgr.createDuration(newDuration);
			fail("" + newDuration + " is supposed to exist in database, so it musn't be possible to create it");
		}
		catch (ModelException ignored) {
			// success!
			// Suppression
			ModelMgr.removeDuration(newDuration);
		}
	}

//	public void testNegativeCreation() throws DbException {
//		// Cr�ation de dur�e n�gative
//		try {
//			ModelMgr.createDuration(-1);
//			fail("Manage to create a negative duration");
//		}
//		catch (ModelException ignored) {
//			// success!
//		}
//	}

	public void testRemove() throws DbException, ModelException {
		long duration = generateNewDuration();

		// Cr�ation
		ModelMgr.createDuration(duration);
		assertTrue(ModelMgr.durationExists(duration));

		// Suppression
		ModelMgr.removeDuration(duration);
		assertFalse(ModelMgr.durationExists(duration));
	}

	public void testUpdateWithAnExistingDuration() throws DbException, ModelException {
		// Cr�ation
		long duration = generateNewDuration();
		ModelMgr.createDuration(duration);
		assertTrue(ModelMgr.durationExists(duration));
		long duration2 = generateNewDuration();
		ModelMgr.createDuration(duration2);
		assertTrue(ModelMgr.durationExists(duration2));

		// Modif de la dur�e en une dur�e existante
		try {
			ModelMgr.updateDuration(duration, duration2);
			fail("Manage to update a duration whith the value of an existing duration");
		}
		catch (ModelException expected) {
			// Success
		}
		
		// Suppression des donn�es
		ModelMgr.removeDuration(duration);
		ModelMgr.removeDuration(duration2);
	}
	
	public void testUpdateWithAnUnusedDuration() throws DbException, ModelException {
		// Cr�ation
		long duration = generateNewDuration();
		ModelMgr.createDuration(duration);
		assertTrue(ModelMgr.durationExists(duration));

		// Modif de la dur�e en une autre dur�e
		long oldDuration = duration;
		duration = generateNewDuration();
		ModelMgr.updateDuration(oldDuration, duration);

		// Suppression des donn�es
		ModelMgr.removeDuration(duration);
	}
	
	public void testUpdateDurationUsedByAContribution() throws DbException, ModelException {
		// Cr�ation
		long duration = generateNewDuration();
		ModelMgr.createDuration(duration);
		assertTrue(ModelMgr.durationExists(duration));

		// Cr�ation d'une contribution
		Collaborator collaborator = ModelMgr.createNewCollaborator();
		Task task = ModelMgr.createNewTask(null);
		Contribution ctb = new Contribution();
		ctb.setContributorId(collaborator.getId());
		ctb.setTaskId(task.getId());
		ctb.setDate(new GregorianCalendar());
		ctb.setDuration(duration);
		ctb = ModelMgr.createContribution(ctb);

		// Tentative de modif d'une dur�e utilis�e
		try {
			long duration2 = generateNewDuration();
			ModelMgr.updateDuration(duration, duration2);
			fail("Manage to update a duration used by a contribution");
		}
		catch (ModelException expected) {
			// Success
		}
		
		// Suppression des donn�es
		ModelMgr.removeContribution(ctb);
		ModelMgr.removeTask(task);
		ModelMgr.removeCollaborator(collaborator);
		ModelMgr.removeDuration(duration);
	}
	
	private long generateNewDuration() throws DbException {
		long duration = 1;
		// Recherche d'une dur�e inexistante en base
		while (ModelMgr.durationExists(duration))
			duration ++;
		return duration;
	}

}
