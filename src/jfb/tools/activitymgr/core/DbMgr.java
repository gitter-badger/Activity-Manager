/*
 * Copyright (c) 2004, Jean-Fran�ois Brazeau. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 * 
 *  2. Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 * 
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIEDWARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package jfb.tools.activitymgr.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import jfb.tools.activitymgr.core.beans.Collaborator;
import jfb.tools.activitymgr.core.beans.Contribution;
import jfb.tools.activitymgr.core.beans.Task;
import jfb.tools.activitymgr.core.beans.TaskSums;
import jfb.tools.activitymgr.core.util.StringHelper;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.log4j.Logger;

/**
 * Classe offrant les services de base de persistence de 
 * l'application.
 */
public class DbMgr {

	/** Logger */
	private static Logger log = Logger.getLogger(DbMgr.class);

	/** Formatteur de date */
	private static SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");

	/** Datasource */
	private static BasicDataSource ds = null;
	
	/** Contexte de thread utilis� pour d�tecter les anomalies associ�es � la gestion de transaction */
	private static ThreadLocal threadLocal = new ThreadLocal();

	/**
	 * Initialise la connexion � la base de donn�es.
	 * @param driverName le nom du driver JDBC.
	 * @param url l'URL de connexion au serveur.
	 * @param user l'identifiant de connexion/
	 * @param password le mot de passe de connexion.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static void initDatabaseAccess(String driverName, String url, String user, String password) throws DbException {
		try {
			// Si la datasource existe on la ferme
			if (ds!=null) {
				closeDatabaseAccess();
			}
			// Fermeture de la datasource
			BasicDataSource newDs = new BasicDataSource();
			
			// Initialisation de la Datasource
			newDs = new BasicDataSource();
			log.info("Connecting to '" + url + "'");
			newDs.setDriverClassName(driverName);
			newDs.setUrl(url);
			newDs.setUsername(user);
			newDs.setPassword(password);
			newDs.setDefaultAutoCommit(false);

			// Tentative de r�cup�ration d'une connexion
			// pour d�tecter les probl�mes de connexion
			Connection con = newDs.getConnection();
			con.close();
			
			// Sauvegarde de la r�f�rence
			ds = newDs;
		}
		catch (SQLException e) {
			log.info("SQL Exception", e);
			throw new DbException("Couldn't get a SQL Connection", e);
		}
	}
	
	/**
	 * Ferme la base de donn�es.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la BDD.
	 */
	protected static void closeDatabaseAccess() throws DbException {
		try {
			if (ds!=null){
				// R�cup�ration de la connexion
				Connection con = ds.getConnection();
				
				// Cas d'une base HSQLDB embarqu�e
				if (isEmbeddedHSQLDB(con)) {
					// Extinction de la base de donn�es
					con.createStatement().execute("shutdown");
				}

				// Fermeture de la datasource
				ds.close();
				ds = null;
			}
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la fermeture de la base de donn�es", e);
		}
	}
	
	/**
	 * Permet de commencer une transaction.
	 * 
	 * <p>Une connexion � la base de donn�es est �tablie. Celle ci
	 * doit �tre valid�e par la couche appelante par une invocation
	 * de <code>endTransaction</code>.</p>
	 * 
	 * @return le contexte de transaction.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static DbTransaction beginTransaction() throws DbException {
		try {
			// Est-on connect� � la BDD ?
			if (ds==null)
				throw new DbException("Database connection not established", null);
			// Obtention d'une connexion
			Connection con = ds.getConnection();
			if (threadLocal.get()!=null)
				throw new Error("Conflicting transaction");
			threadLocal.set(con);
			//log.debug("Active : " + ds.getNumActive() + ", Idle : " + ds.getNumIdle() + ", Connexion : " + con);
			return new DbTransaction(con);
		}
		catch (SQLException e) {
			log.info("SQL Exception", e);
			throw new DbException("Couldn't get a SQL Connection", e);
		}
	}

	/**
	 * Valide une transactrion.
	 * @param tx contexte de transaction.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static void commitTransaction(DbTransaction tx) throws DbException {
		try { tx.getConnection().commit();	}
		catch (SQLException e ) {
			log.info("Incident SQL", e);
			throw new DbException("Echec du commit", e);
		}
	}
	
	/**
	 * V�rifie si les tables existent dans le mod�le.
	 * @param tx le contexte de transaction.
	 * @return un bool�en indiquant si la table sp�cifi�e existe dans le mod�le.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static boolean tablesExist(DbTransaction tx) throws DbException {
		boolean tablesExist = true;
		tablesExist &= tableExists(tx, "COLLABORATOR");
		tablesExist &= tableExists(tx, "CONTRIBUTION");
		tablesExist &= tableExists(tx, "DURATION");
		tablesExist &= tableExists(tx, "TASK");
		return tablesExist;
	}
	
	/**
	 * V�rifie si une table existe dans le mod�le.
	 * @param tx le contexte de transaction.
	 * @param tableName le nom de la table.
	 * @return un bool�en indiquant si la table sp�cifi�e existe dans le mod�le.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	private static boolean tableExists(DbTransaction tx, String tableName) throws DbException {
		PreparedStatement pStmt = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();

			// Recherche de la table
			ResultSet rs = con.getMetaData().getTables(null, null, tableName, new String[] { "TABLE" } );

			// R�cup�ration du r�sultat
			boolean exists = rs.next();

			// Retour du r�sultat
			return exists;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors du test d'existance de la table '" + tableName + "'", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}
	
	/**
	 * Cr�e les tables du mod�le de donn�es.
	 * @param tx contexte de transaction.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static void createTables(DbTransaction tx) throws DbException {
		Statement stmt = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();
			
			// Lecture du fichier SQL de cr�ation de la BDD
			String batchName = "sql/" + (isHSQLDB(con) ? "hsqldb.sql" : "mysqldb.sql");
			InputStream in = DbMgr.class.getResourceAsStream(batchName);
			String batchContent = null;
			try { batchContent = StringHelper.fromInputStream(in); }
			catch (IOException e) {
				log.info("I/O error while loading table creation SQL script.", e);
				throw new DbException("I/O error while loading table creation SQL script.", null);
			}

			// D�coupage et ex�cution du batch
			stmt = con.createStatement();
			LineNumberReader lnr = new LineNumberReader(new StringReader(batchContent));
			// TODO Externaliser le d�coupage du script SQL
			StringBuffer buf = new StringBuffer();
			boolean proceed = true;
			do {
				String line = null;
				// On ne lit dans le flux que si la ligne courante n'est pas
				// encore totalement trait�e
				if (line==null) {
					try { line = lnr.readLine(); }
					catch (IOException e) {
						log.info("Unexpected I/O error while reading memory stream!", e);
						throw new DbException("Unexpected I/O error while reading memory stream!", null);
					}
					log.debug("Line read : '" + line + "'");
				}
				// Si le flux est vide, on sort de la boucle
				if (line==null) {
					proceed = false;
				}
				// Sinon on traite la ligne
				else {
					line = line.trim();
					// Si la ligne est un commentaire on l'ignore
					if (line.startsWith("--")) {
						line = null;
					}
					else {
						// Sinon on regarde si la ligne poss�de
						// un point virgule
						int idx = line.indexOf(';');
						// Si c'est le cas, on d�coupe la cha�ne et on 
						// ex�cute la requ�te
						if (idx>=0) {
							buf.append(line.subSequence(0, idx));
							line = line.substring(idx);
							String sql = buf.toString();
							buf.setLength(0);
							log.debug(" - sql='" + sql + "'"); 
							if (!"".equals(sql)) 
								stmt.executeUpdate(sql);
						}
						// sinon on ajoute la ligne au buffer de reque�te
						else {
							buf.append(line);
							buf.append('\n');
						}
					}
				}
				
			}
			while (proceed);
			
			// Test de l'existence des tables
			if (!tablesExist(tx))
				throw new DbException("Database table creation failure", null);

			// Fermeture du statement
			stmt.close();
			stmt = null;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Database table creation failure", e);
		}
		finally {
			if (stmt!=null) try { stmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * Cr�e un collaborateur.
	 * 
	 * @param tx contexte de transaction.
	 * @param newCollaborator le collaborateur � cr�er.
	 * @return le collaborateur apr�s cr�ation.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static Collaborator createCollaborator(DbTransaction tx, Collaborator newCollaborator) throws DbException {
		PreparedStatement pStmt = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();
			
			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("insert into collaborator (clb_login, clb_first_name, clb_last_name) values (?, ?, ?)");
			pStmt.setString(1, newCollaborator.getLogin());
			pStmt.setString(2, newCollaborator.getFirstName());
			pStmt.setString(3, newCollaborator.getLastName());
			pStmt.executeUpdate();

			// R�cup�ration de l'identifiant g�n�r�
			long generatedId = getGeneratedId(pStmt);
			log.debug("Generated id=" + generatedId);
			newCollaborator.setId(generatedId);

			// Fermeture du statement
			pStmt.close();
			pStmt = null;
			
			// Retour du r�sultat
			return newCollaborator;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la cr�ation du collaborateur '" + newCollaborator.getLogin() + "'", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * Cr�e une contribution.
	 * 
	 * @param tx contexte de transaction.
	 * @param newContribution la nouvelle contribution.
	 * @return la contribution apr�s cr�ation.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static Contribution createContribution(DbTransaction tx, Contribution newContribution) throws DbException {
		PreparedStatement pStmt = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();
			
			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("insert into contribution (ctb_year, ctb_month, ctb_day, ctb_contributor, ctb_task, ctb_duration) values (?, ?, ?, ?, ?, ?)");
			pStmt.setInt   (1, newContribution.getYear());
			pStmt.setInt   (2, newContribution.getMonth());
			pStmt.setInt   (3, newContribution.getDay());
			pStmt.setLong  (4, newContribution.getContributorId());
			pStmt.setLong  (5, newContribution.getTaskId());
			pStmt.setLong  (6, newContribution.getDuration());
			pStmt.executeUpdate();

			// Fermeture du statement
			pStmt.close();
			pStmt = null;
			
			// Retour du r�sultat
			return newContribution;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la cr�ation d'une contribution", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * Cr�e une contribution.
	 * 
	 * @param tx contexte de transaction.
	 * @param newDuration la nouvelle dur�e.
	 * @return la dur�e apr�s cr�ation.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static long createDuration(DbTransaction tx, long newDuration) throws DbException {
		PreparedStatement pStmt = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();
			
			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("insert into duration (dur_id) values (?)");
			pStmt.setLong (1, newDuration);
			pStmt.executeUpdate();

			// Fermeture du statement
			pStmt.close();
			pStmt = null;
			
			// Retour du r�sultat
			return newDuration;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la cr�ation de la dur�e : '" + newDuration + "'", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}
	
	/**
	 * Cr�e une tache.
	 * 
	 * <p>La tache parent peut �tre nulle pour indiquer que la nouvelle tache
	 * est une tache racine.</p>
	 * 
	 * @param tx le contexte de transaction.
	 * @param parentTask la tache parent accueillant la nouvelle tache.
	 * @param newTask la nouvelle tache.
	 * @return la tache apr�s cr�ation.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static Task createTask(DbTransaction tx, Task parentTask, Task newTask) throws DbException {
		PreparedStatement pStmt = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();
			
			// Mise � jour du chemin de la t�che
			String parentPath = parentTask==null ? "" : parentTask.getFullPath();
			newTask.setPath(parentPath);

			// G�n�ration du num�ro de la t�che
			byte taskNumber = newTaskNumber(tx, parentPath);
			newTask.setNumber(taskNumber);
			
			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("insert into task (tsk_path, tsk_number, tsk_code, tsk_name, tsk_budget, tsk_initial_cons, tsk_todo) values (?, ?, ?, ?, ?, ?, ?)");
			pStmt.setString(1, newTask.getPath());
			pStmt.setByte  (2, newTask.getNumber());
			pStmt.setString(3, newTask.getCode());
			pStmt.setString(4, newTask.getName());
			pStmt.setLong  (5, newTask.getBudget());
			pStmt.setLong  (6, newTask.getInitiallyConsumed());
			pStmt.setLong  (7, newTask.getTodo());
			pStmt.executeUpdate();

			// R�cup�ration de l'identifiant g�n�r�
			long generatedId = getGeneratedId(pStmt);
			log.debug("Generated id=" + generatedId);
			newTask.setId(generatedId);

			// Fermeture du ResultSet
			pStmt.close();
			pStmt = null;
			
			// Retour du r�sultat
			return newTask;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la cr�ation de la tache '" + newTask.getName() + "'", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}
	
	/**
	 * V�rifie si la dur�e est utilis�e en base.
	 * @param tx le contexte de transaction.
	 * @param duration la dur�e � v�rifier.
	 * @return un bool�en indiquant si la dur�e est utilis�e.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static boolean durationIsUsed(DbTransaction tx, long duration) throws DbException {
		PreparedStatement pStmt = null;
		ResultSet rs = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();
			
			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("select count(*) from contribution where ctb_duration=?");
			pStmt.setLong  (1, duration);
	
			// Ex�cution de la requ�te
			rs = pStmt.executeQuery();
			
			// Pr�paration du r�sultat
			if (!rs.next())
				throw new DbException("Nothing returned by the query", null);
			boolean durationIsUsed = rs.getInt(1)>0;

			// Fermeture du statement
			pStmt.close();
			pStmt = null;
			
			// Retour du r�sultat
			return durationIsUsed;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la v�rification de l'utilisation de la dur�e '" + duration + "'", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * Ferme une transactrion.
	 * @param tx le contexte de transaction.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static void endTransaction(DbTransaction tx) throws DbException {
		try { tx.getConnection().close();	}
		catch (SQLException e ) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la cloture de la connexion", e);
		}
		threadLocal.set(null);
	}
	
	/**
	 * @param tx le contexte de transaction.
	 * @param collaboratorId l'identifiant du collaborateur recherch�.
	 * @return le collaborateur dont l'identifiant est sp�cifi�.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static Collaborator getCollaborator(DbTransaction tx, long collaboratorId) throws DbException {
		PreparedStatement pStmt = null;
		ResultSet rs = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();
			
			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("select clb_login, clb_first_name, clb_last_name from collaborator where clb_id=?");
			pStmt.setLong  (1, collaboratorId);
	
			// Ex�cution de la requ�te
			rs = pStmt.executeQuery();
			
			// Pr�paration du r�sultat
			Collaborator collaborator = null;
			if (rs.next()) {
				collaborator = new Collaborator();
				collaborator.setId(collaboratorId);
				collaborator.setLogin(rs.getString(1));
				collaborator.setFirstName(rs.getString(2));
				collaborator.setLastName(rs.getString(3));
			}

			// Fermeture du statement
			pStmt.close();
			pStmt = null;
			
			// Retour du r�sultat
			return collaborator;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la r�cup�ration du collaborateur d'identifiant '" + collaboratorId + "'", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * @param tx le contexte de transaction.
	 * @param login l'identifiant de connexion du collaborateur recherch�.
	 * @return le collaborateur dont l'identifiant de connexion est sp�cifi�.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static Collaborator getCollaborator(DbTransaction tx, String login) throws DbException {
		PreparedStatement pStmt = null;
		ResultSet rs = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();
			
			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("select clb_id from collaborator where clb_login=?");
			pStmt.setString(1, login);
	
			// Ex�cution de la requ�te
			rs = pStmt.executeQuery();
			
			// Pr�paration du r�sultat
			Collaborator collaborator = null;
			if (rs.next()) {
				long collaboratorId = rs.getLong(1);
				collaborator = getCollaborator(tx, collaboratorId);
			}

			// Fermeture du ResultSet
			pStmt.close();
			pStmt = null;
			
			// Retour du r�sultat
			return collaborator;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la r�cup�ration ddu collaborateur de login '" + login + "'", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * @param tx le contexte de transaction.
	 * @return la liste des collaborateurs.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static Collaborator[] getCollaborators(DbTransaction tx) throws DbException {
		PreparedStatement pStmt = null;
		ResultSet rs = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();
			
			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("select clb_id from collaborator");

			// Ex�cution de la requ�te
			rs = pStmt.executeQuery();

			// Recherche des sous-taches
			ArrayList list = new ArrayList();
			while (rs.next()) {
				long collaboratorId = rs.getLong(1);
				Collaborator collaborator = getCollaborator(tx, collaboratorId);
				list.add(collaborator);
			}

			// Fermeture du ResultSet
			pStmt.close();
			pStmt = null;
			
			// Retour du r�sultat
			log.debug("  => found " + list.size() + " entrie(s)");
			return (Collaborator[]) list.toArray(new Collaborator[list.size()]);
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la r�cup�ration des collaborateurs'", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}
	
	/**
	 * @param tx le contexte de transaction.
	 * @param contributor le collaborateur associ� aux contributions.
	 * @param task la tache associ�e aux contributions.
	 * @param fromDate la date de d�part.
	 * @param toDate la date de fin.
	 * @return la liste des contributions associ�es aux param�tres sp�cifi�s.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static Contribution[] getContributions(DbTransaction tx, Collaborator contributor, Task task, Calendar fromDate, Calendar toDate) throws DbException {
		log.debug("getContributions(" + contributor + ", " + task + ", " + sdf.format(fromDate.getTime()) + ", " + sdf.format(toDate.getTime()) + ")");
		PreparedStatement pStmt = null;
		ResultSet rs = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();
			
			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("select ctb_year, ctb_month, ctb_day, ctb_contributor, ctb_task, ctb_duration from contribution where ctb_contributor=? and ctb_task=? and ctb_year*10000 + ( ctb_month*100 + ctb_day ) between ? and ?");
			pStmt.setLong  (1, contributor.getId());
			pStmt.setLong  (2, task.getId());
			pStmt.setString(3, sdf.format(fromDate.getTime()));
			pStmt.setString(4, sdf.format(toDate.getTime()));

			// Ex�cution de la requ�te
			rs = pStmt.executeQuery();

			// Extraction du r�sultat
			Contribution[] result = extractContributions(rs);
			
			// Fermeture du ResultSet
			pStmt.close();
			pStmt = null;
			
			// Retour du r�sultat
			return result;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la r�cup�ration des contributions", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * Extrait les contributions du resultat de la requ�te SQL.
	 * @param rs le r�sultat de la requ�te SQL.
	 * @return les contributions extraites.
	 * @throws SQLException lev� en cas d'incident avec la base de donn�es.
	 */
	private static Contribution[] extractContributions(ResultSet rs) throws SQLException {
		// Recherche des sous-taches
		ArrayList list = new ArrayList();
		while (rs.next()) {
			// Pr�paration du r�sultat
			Contribution contribution = new Contribution();
			contribution.setYear(rs.getInt(1));
			contribution.setMonth(rs.getInt(2));
			contribution.setDay(rs.getInt(3));
			contribution.setContributorId(rs.getInt(4));
			contribution.setTaskId(rs.getInt(5));
			contribution.setDuration(rs.getLong(6));
			list.add(contribution);
		}
		log.debug("  => found " + list.size() + " entrie(s)");
		return (Contribution[]) list.toArray(new Contribution[list.size()]);
	}
		
	/**
	 * Retourne les contributions associ�es aux param�tres sp�cifi�s.
	 * 
	 * <p>Tous les param�tres sont facultatifs. Chaque param�tre sp�cifi� ag�t
	 * comme un filtre sur le r�sultat. A l'inverse, l'omission d'un param�tre
	 * provoque l'inclusion de toutes les contributions, quelque soit leurs
	 * valeurs pour l'attribut consid�r�.</p>
	 * 
	 * <p>La sp�cification des param�tres r�pond aux m�mes r�gles que pour la
	 * m�thode <code>getContributionsSum</code>.</p>
	 * 
	 * @param tx le contexte de transaction.
	 * @param task la t�che associ�e aux contributions (facultative).
	 * @param contributor le collaborateur associ� aux contributions (facultatif).
	 * @param year l'ann�e (facultative).
	 * @param month le mois (facultatif).
	 * @param day le jour (facultatif).
	 * @return les contributions.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 * 
	 * @see jfb.tools.activitymgr.core.DbMgr#getContributionsSum(DbTransaction, Task, Collaborator, Integer, Integer, Integer)
	 */
	protected static Contribution[] getContributions(DbTransaction tx, Task task, Collaborator contributor, Integer year, Integer month, Integer day) throws DbException {
		log.debug("getContributions(" + task + ", " + contributor + ", " + year + ", " + month + ", " + day + ")");
		PreparedStatement pStmt = null;
		ResultSet rs = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();
			
			StringBuffer baseRequest = new StringBuffer("select ctb_year, ctb_month, ctb_day, ctb_contributor, ctb_task, ctb_duration from contribution, task where ctb_task=tsk_id");
			String orderByClause = " order by ctb_year, ctb_month, ctb_day, tsk_path, tsk_number, ctb_contributor, ctb_duration";
			// Cas ou la tache n'est pas sp�cifi�e
			if (task==null) {
				// Pr�paration de la requ�te
				completeContributionRequest(
						baseRequest,
						contributor,
						year,
						month,
						day
					);
				baseRequest.append(orderByClause);
				String request = baseRequest.toString();
				pStmt = con.prepareStatement(request);
				completeContributionReqParams(pStmt, 1, contributor, year, month, day);
			}
			// Si la tache n'admet pas de sous-taches, le cumul de 
			// budget, de consomm� initial, de reste � faire sont
			// �gaux � ceux de la tache
			else if (task.getSubTasksCount()==0) {
				// Pr�paration de la requ�te
				baseRequest.append(" and tsk_id=?");
				completeContributionRequest(
						baseRequest,
						contributor,
						year,
						month,
						day
					);
				baseRequest.append(orderByClause);
				String request = baseRequest.toString();
				pStmt = con.prepareStatement(request);
				pStmt.setLong(1, task.getId());
				log.debug(" taskId=" + task.getId());
				completeContributionReqParams(pStmt, 2, contributor, year, month, day);
			}
			// Sinon, il faut calculer
			else {
				// Param�tre pour la clause 'LIKE'
				String pathLike = task.getFullPath() + "%";
					
				// Pr�paration de la requ�te
				baseRequest.append(" and tsk_path like ?");
				completeContributionRequest(
						baseRequest,
						contributor,
						year,
						month,
						day
					);
				baseRequest.append(orderByClause);
				String request = baseRequest.toString();
				pStmt = con.prepareStatement(request);
				pStmt.setString(1, pathLike);
				completeContributionReqParams(pStmt, 2, contributor, year, month, day);
			}		

			// Ex�cution de la requ�te
			log.debug("Request : " + baseRequest);
			rs = pStmt.executeQuery();

			// Extraction du r�sultat
			Contribution[] result = extractContributions(rs);
			
			// Fermeture du statement
			pStmt.close();
			pStmt = null;
			
			// Retour du r�sultat
			return result;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la r�cup�ration des contributions", e);
		}
		finally {
			try { if (pStmt!=null) pStmt.close(); } catch (Throwable ignored) { }
		}
	}


	/**
	 * Calcule le nombre des contributions associ�e aux param�tres sp�cifi�s.
	 * 
	 * <p>Tous les param�tres sont facultatifs. Chaque param�tre sp�cifi� ag�t
	 * comme un filtre sur le r�sultat. A l'inverse, l'omission d'un param�tre
	 * provoque l'inclusion de toutes les contributions, quelque soit leurs
	 * valeurs pour l'attribut consid�r�.</p>
	 * 
	 * <p>En sp�cifiant la tache X, on conna�tra la somme des contribution pour
	 * la taches X. En ne sp�cifiant pas de tache, la somme sera effectu�e quelque
	 * soit les t�ches.</p>
	 * 
	 * @param tx le contexte de transaction.
	 * @param task la t�che associ�e aux contributions (facultative).
	 * @param contributor le collaborateur associ� aux contributions (facultatif).
	 * @param year l'ann�e (facultative).
	 * @param month le mois (facultatif).
	 * @param day le jour (facultatif).
	 * @return la seomme des contributions.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static long getContributionsNb(DbTransaction tx, Task task, Collaborator contributor, Integer year, Integer month, Integer day) throws DbException {
		log.debug("getContributionsSum(" + task + ", " + contributor + ", " + year + ", " + month + ", " + day + ")");
		return getContributionsAggregation(tx, "count(ctb_duration)", task, contributor, year, month, day);
	}

	/**
	 * Calcule le cumuls des consommations associees aux contributions pour
	 * les param�tres sp�cifi�s.
	 * 
	 * <p>Tous les param�tres sont facultatifs. Chaque param�tre sp�cifi� ag�t
	 * comme un filtre sur le r�sultat. A l'inverse, l'omission d'un param�tre
	 * provoque l'inclusion de toutes les contributions, quelque soit leurs
	 * valeurs pour l'attribut consid�r�.</p>
	 * 
	 * <p>En sp�cifiant la tache X, on conna�tra la somme des contribution pour
	 * la taches X. En ne sp�cifiant pas de tache, la somme sera effectu�e quelque
	 * soit les t�ches.</p>
	 * 
	 * @param tx le contexte de transaction.
	 * @param task la t�che associ�e aux contributions (facultative).
	 * @param contributor le collaborateur associ� aux contributions (facultatif).
	 * @param year l'ann�e (facultative).
	 * @param month le mois (facultatif).
	 * @param day le jour (facultatif).
	 * @return la seomme des contributions.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static long getContributionsSum(DbTransaction tx, Task task, Collaborator contributor, Integer year, Integer month, Integer day) throws DbException {
		log.debug("getContributionsSum(" + task + ", " + contributor + ", " + year + ", " + month + ", " + day + ")");
		return getContributionsAggregation(tx, "sum(ctb_duration)", task, contributor, year, month, day);
	}

	/**
	 * Calcule une aggregation associee aux contributions pour les param�tres
	 * sp�cifi�s.
	 * 
	 * <p>Tous les param�tres sont facultatifs. Chaque param�tre sp�cifi� ag�t
	 * comme un filtre sur le r�sultat. A l'inverse, l'omission d'un param�tre
	 * provoque l'inclusion de toutes les contributions, quelque soit leurs
	 * valeurs pour l'attribut consid�r�.</p>
	 * 
	 * <p>En sp�cifiant la tache X, on conna�tra la somme des contribution pour
	 * la taches X. En ne sp�cifiant pas de tache, la somme sera effectu�e quelque
	 * soit les t�ches.</p>
	 * 
	 * @param tx le contexte de transaction.
	 * @param aggregation la cha�ne repr�sentant l'aggr�gation (ex: <code>sum(ctb_contribution)</code>).
	 * @param task la t�che associ�e aux contributions (facultative).
	 * @param contributor le collaborateur associ� aux contributions (facultatif).
	 * @param year l'ann�e (facultative).
	 * @param month le mois (facultatif).
	 * @param day le jour (facultatif).
	 * @return la seomme des contributions.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	private static long getContributionsAggregation(DbTransaction tx, String aggregation, Task task, Collaborator contributor, Integer year, Integer month, Integer day) throws DbException {
		log.debug("getContributionsSum(" + task + ", " + contributor + ", " + year + ", " + month + ", " + day + ")");
		PreparedStatement pStmt = null;
		ResultSet rs = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();
			
			StringBuffer baseRequest = new StringBuffer("select ")
				.append(aggregation)
				.append(" from contribution, task where ctb_task=tsk_id");
			// Cas ou la tache n'est pas sp�cifi�e
			if (task==null) {
				// Pr�paration de la requ�te
				completeContributionRequest(
						baseRequest,
						contributor,
						year,
						month,
						day
					);
				String request = baseRequest.toString();
				pStmt = con.prepareStatement(request);
				completeContributionReqParams(pStmt, 1, contributor, year, month, day);
			}
			// Si la tache n'admet pas de sous-taches, le cumul de 
			// budget, de consomm� initial, de reste � faire sont
			// �gaux � ceux de la tache
			else if (task.getSubTasksCount()==0) {
				// Pr�paration de la requ�te
				baseRequest.append(" and tsk_id=?");
				completeContributionRequest(
						baseRequest,
						contributor,
						year,
						month,
						day
					);
				String request = baseRequest.toString();
				pStmt = con.prepareStatement(request);
				pStmt.setLong(1, task.getId());
				log.debug(" taskId=" + task.getId());
				completeContributionReqParams(pStmt, 2, contributor, year, month, day);
			}
			// Sinon, il faut calculer
			else {
				// Param�tre pour la clause 'LIKE'
				String pathLike = task.getFullPath() + "%";
					
				// Pr�paration de la requ�te
				baseRequest.append(" and tsk_path like ?");
				completeContributionRequest(
						baseRequest,
						contributor,
						year,
						month,
						day
					);
				String request = baseRequest.toString();
				pStmt = con.prepareStatement(request);
				pStmt.setString(1, pathLike);
				completeContributionReqParams(pStmt, 2, contributor, year, month, day);
			}		

			// Ex�cution de la requ�te
			log.debug("Request : " + baseRequest);
			rs = pStmt.executeQuery();
			if (!rs.next())
				throw new DbException("Nothing returned from this query", null);
			long agregation = rs.getLong(1);
			
			// Fermeture du statement
			pStmt.close();
			pStmt = null;
			
			// Retour du r�sultat
			log.info("agregation=" + agregation);
			return agregation;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la r�cup�ration des cumuls", e);
		}
		finally {
			try { if (pStmt!=null) pStmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * @param tx le contexte de transaction.
	 * @return la liste des dur�es.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static long[] getDurations(DbTransaction tx) throws DbException {
		PreparedStatement pStmt = null;
		ResultSet rs = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();
			
			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("select dur_id from duration order by dur_id asc");

			// Ex�cution de la requ�te
			rs = pStmt.executeQuery();

			// Recherche des sous-taches
			ArrayList list = new ArrayList();
			while (rs.next()) {
				long durationId = rs.getLong(1);
				list.add(new Long(durationId));
			}

			// Fermeture du ResultSet
			pStmt.close();
			pStmt = null;
			
			// Retour du r�sultat
			log.debug("  => found " + list.size() + " entrie(s)");
			long[] result = new long[list.size()];
			for (int i=0; i<result.length; i++)
				result[i] = ((Long) list.get(i)).longValue();
			return result;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la r�cup�ration des collaborateurs'", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * @param tx le contexte de transaction.
	 * @param task la tache dont on veut connaitre la tache parent.
	 * @return la tache parent d'une tache sp�cifi�e.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static Task getParentTask(DbTransaction tx, Task task) throws DbException {
		Task parentTask = null;
		String parentTaskFullPath = task.getPath();
log.debug(parentTaskFullPath);
		// Si le chemin est vide, la tache parent est nulle (tache racine)
		if (parentTaskFullPath!=null && !"".equals(parentTaskFullPath)) {
			// Extraction du chemin et du num�ro de la tache recherch�e
			log.debug("Fullpath='" + parentTaskFullPath + "'");
			String path = parentTaskFullPath.substring(0, parentTaskFullPath.length()-2);
			byte number = StringHelper.toByte(parentTaskFullPath.substring(parentTaskFullPath.length()-2));
			log.debug(" => path=" + path);
			log.debug(" => number=" + number);
			
			// Recherche de la tache
			parentTask = getTask(tx, path, number);
		}
		// Retour du r�sultat
		return parentTask;
	}

	/**
	 * @param tx le contexte de transaction.
	 * @param path le chemin dont on veut conna�tre les taches.
	 * @return la liste des taches associ�es � un chemin donn�.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static Task[] getTasks(DbTransaction tx, String path) throws DbException {
		PreparedStatement pStmt = null;
		ResultSet rs = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();
			
			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("select tsk_id from task where tsk_path=? order by tsk_number");
			pStmt.setString(1, path);

			// Ex�cution de la requ�te
			rs = pStmt.executeQuery();

			// Recherche des sous-taches
			ArrayList list = new ArrayList();
			while (rs.next()) {
				long taskId = rs.getLong(1);
				Task task = getTask(tx, taskId);
				list.add(task);
			}

			// Fermeture du ResultSet
			pStmt.close();
			pStmt = null;
			
			// Retour du r�sultat
			log.debug("  => found " + list.size() + " entrie(s)");
			return (Task[]) list.toArray(new Task[list.size()]);
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la r�cup�ration des sous taches de chemin '" + path + "'", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * @param tx le contexte de transaction.
	 * @param parentTask la tache parent dont on veut connaitre les sous-taches.
	 * @return la liste des sous-taches.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static Task[] getSubtasks(DbTransaction tx, Task parentTask) throws DbException {
		// R�cup�ration du chemin � partir de la tache parent
		String fullpath = parentTask==null ? "" : parentTask.getFullPath();
		log.debug("Looking for tasks with path='" + fullpath + "'");
		return getTasks(tx, fullpath);
	}

	/**
	 * @param tx le contexte de transaction.
	 * @param taskId l'identifiant de la tache recherch�e.
	 * @return la tache dont l'identifiant est sp�cifi�.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static Task getTask(DbTransaction tx, long taskId) throws DbException {
		PreparedStatement pStmt = null;
		ResultSet rs = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();

			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("select tsk_path, tsk_number, tsk_code, tsk_name, tsk_budget, tsk_initial_cons, tsk_todo from task where tsk_id=?");
			pStmt.setLong  (1, taskId);
	
			// Ex�cution de la requ�te
			rs = pStmt.executeQuery();
			
			// Pr�paration du r�sultat
			Task task = null;
			if (rs.next()) {
				task = new Task();
				task.setId(taskId);
				task.setPath(rs.getString(1));
				task.setNumber(rs.getByte(2));
				task.setCode(rs.getString(3));
				task.setName(rs.getString(4));
				task.setBudget(rs.getLong(5));
				task.setInitiallyConsumed(rs.getLong(6));
				task.setTodo(rs.getLong(7));
			}
			// Fermeture du ResultSet
			pStmt.close();
			pStmt = null;
			
			// Si la tache existe bien
			if (task!=null) {
				// Recherche du nombre de sous-taches
				String taskFullPath = task.getFullPath();
				pStmt = con.prepareStatement("select count(*) from task where tsk_path=?");
				pStmt.setString(1, taskFullPath);
		
				// Ex�cution de la requ�te
				rs = pStmt.executeQuery();
				if (rs.next()) {
					int subTasksCount = rs.getInt(1);
					task.setSubTasksCount(subTasksCount);
				}
				// Fermeture du ResultSet
				pStmt.close();
				pStmt = null;
			}
	
			// Retour du r�sultat
			return task;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la r�cup�ration de la tache d'identifiant '" + taskId + "'", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * @param tx le contexte de transaction.
	 * @param taskPath le chemin de la tache recherch�e.
	 * @param taskNumber le num�ro de la tache recherch�e.
	 * @return la tache dont le chemin et le num�ro sont sp�cifi�s.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static Task getTask(DbTransaction tx, String taskPath, byte taskNumber) throws DbException {
		log.debug("getTask(" + taskPath + ", " + taskNumber + ")");
		PreparedStatement pStmt = null;
		ResultSet rs = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();

			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("select tsk_id from task where tsk_path=? and tsk_number=?");
			pStmt.setString(1, taskPath);
			pStmt.setByte  (2, taskNumber);

			// Ex�cution de la requ�te
			rs = pStmt.executeQuery();
			
			// Pr�paration du r�sultat
			Task task = null;
			if (rs.next()) {
				long taskId = rs.getLong(1);
				task = getTask(tx, taskId);
			}
			// Fermeture du ResultSet
			pStmt.close();
			pStmt = null;
			
			// Retour du r�sultat
			log.debug("task = " + task);
			return task;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la r�cup�ration de la tache N� " + taskNumber + " du chemin '" + taskPath + "'", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * @param tx le contexte de transaction.
	 * @param taskPath le chemin de la tache recherch�e.
	 * @param taskCode le code de la tache recherch�e.
	 * @return la tache dont le code et la tache parent sont sp�cifi�s.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static Task getTask(DbTransaction tx, String taskPath, String taskCode) throws DbException {
		PreparedStatement pStmt = null;
		ResultSet rs = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();

			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("select tsk_id from task where tsk_path=? and tsk_code=?");
			pStmt.setString(1, taskPath);
			pStmt.setString(2, taskCode);

			// Ex�cution de la requ�te
			rs = pStmt.executeQuery();
			
			// Pr�paration du r�sultat
			Task task = null;
			if (rs.next()) {
				long taskId = rs.getLong(1);
				task = getTask(tx, taskId);
			}
			// Fermeture du ResultSet
			pStmt.close();
			pStmt = null;
			
			// Retour du r�sultat
			return task;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la r�cup�ration de la tache de code '" + taskCode + "'", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * @param tx le contexte de transaction.
	 * @param collaborator le collaborateur.
	 * @param fromDate date de d�but.
	 * @param toDate date de fin.
	 * @return la liste de taches associ�es au collaborateur entre les 2 dates sp�cifi�es.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static Task[] getTasks(DbTransaction tx, Collaborator collaborator, Calendar fromDate, Calendar toDate) throws DbException {
		log.debug("getTasks(" + collaborator + ", " + sdf.format(fromDate.getTime()) + ", " + sdf.format(toDate.getTime()) + ")");
		PreparedStatement pStmt = null;
		ResultSet rs = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();

			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("select distinct ctb_task, tsk_path from contribution, task where ctb_task=tsk_id and ctb_contributor=? and ctb_year*10000 + ( ctb_month*100 + ctb_day ) between ? and ? order by tsk_path");
			pStmt.setLong  (1, collaborator.getId());
			pStmt.setString(2, sdf.format(fromDate.getTime()));
			pStmt.setString(3, sdf.format(toDate.getTime()));

			// Ex�cution de la requ�te
			rs = pStmt.executeQuery();

			// Recherche des sous-taches
			ArrayList list = new ArrayList();
			while (rs.next()) {
				long taskId = rs.getLong(1);
				Task task = getTask(tx, taskId);
				list.add(task);
			}

			// Fermeture du ResultSet
			pStmt.close();
			pStmt = null;
			
			// Retour du r�sultat
			log.debug("  => found " + list.size() + " entrie(s)");
			return (Task[]) list.toArray(new Task[list.size()]);
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la r�cup�ration des taches associ�es � un collaborateur", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * @param tx le contexte de transaction.
	 * @param task la t�che pour laquelle on souhaite conna�tre les totaux.
	 * @return les totaux associ�s � une tache (consomm�, etc.).
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static TaskSums getTaskSums(DbTransaction tx, Task task) throws DbException {
		// TODO Factoriser cette m�those avec getContributionsSum
		PreparedStatement pStmt = null;
		ResultSet rs = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();

			// Pr�paration du r�sultat
			TaskSums taskSums = new TaskSums();
			
			// Si la tache n'admet pas de sous-taches, le cumul de 
			// budget, de consomm� initial, de reste � faire sont
			// �gaux � ceux de la tache
			if (task!=null && task.getSubTasksCount()==0) {
				taskSums.setBudgetSum(task.getBudget());
				taskSums.setInitiallyConsumedSum(task.getInitiallyConsumed());
				taskSums.setTodoSum(task.getTodo());
	
				// Calcul du consomm�
				pStmt = con.prepareStatement("select sum(ctb_duration), count(ctb_duration) from contribution, task where ctb_task=tsk_id and tsk_id=?");
				pStmt.setLong(1, task.getId());
				rs = pStmt.executeQuery();
				if (!rs.next())
					throw new DbException("Nothing returned from this query", null);
				taskSums.setConsumedSum(rs.getLong(1));
				taskSums.setContributionsNb(rs.getLong(2));
				pStmt.close();
				pStmt = null;
			}
			// Sinon, il faut calculer
			else {
				// Param�tre pour la clause 'LIKE'
				String pathLike = (task==null ? "" : task.getFullPath()) + "%";
	
				// Calcul des cumuls
				pStmt = con.prepareStatement("select sum(tsk_budget), sum(tsk_initial_cons), sum(tsk_todo) from task where tsk_path like ?");
				pStmt.setString(1, pathLike);
				rs = pStmt.executeQuery();
				if (!rs.next())
					throw new DbException("Nothing returned from this query", null);
				taskSums.setBudgetSum(rs.getLong(1));
				taskSums.setInitiallyConsumedSum(rs.getLong(2));
				taskSums.setTodoSum(rs.getLong(3));
				pStmt.close();
				pStmt = null;
				
				// Calcul du consomm�
				pStmt = con.prepareStatement("select sum(ctb_duration), count(ctb_duration) from contribution, task where ctb_task=tsk_id and tsk_path like ?");
				pStmt.setString(1, pathLike);
				rs = pStmt.executeQuery();
				if (!rs.next())
					throw new DbException("Nothing returned from this query", null);
				taskSums.setConsumedSum(rs.getLong(1));
				taskSums.setContributionsNb(rs.getLong(2));
				pStmt.close();
				pStmt = null;
				
			}		
			// Retour du r�sultat
			return taskSums;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la r�cup�ration des cumuls pour la tache d'identifiant '" + task.getId()+ "'", e);
		}
		finally {
			try { if (pStmt!=null) pStmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * Supprime un collaborateur.
	 * @param tx le contexte de transaction.
	 * @param collaborator le collaborateur � supprimer.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static void removeCollaborator(DbTransaction tx, Collaborator collaborator) throws DbException {
		PreparedStatement pStmt = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();

			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("delete from collaborator where clb_id=?");
			pStmt.setLong  (1, collaborator.getId());

			// Ex�cution de la requ�te
			int removed = pStmt.executeUpdate();
			if (removed!=1)
				throw new SQLException("No row was deleted");

			// Fermeture du statement
			pStmt.close();
			pStmt = null;
			
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la suppression du collaborateur '" + collaborator.getLogin() + "'", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * Supprime une contribution.
	 * @param tx le contexte de transaction.
	 * @param contribution la contribution � supprimer.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static void removeContribution(DbTransaction tx, Contribution contribution) throws DbException {
		PreparedStatement pStmt = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();

			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("delete from contribution where ctb_year=? and ctb_month=? and ctb_day=? and ctb_contributor=? and ctb_task=?");
			pStmt.setInt   (1, contribution.getYear());
			pStmt.setInt   (2, contribution.getMonth());
			pStmt.setInt   (3, contribution.getDay());
			pStmt.setLong  (4, contribution.getContributorId());
			pStmt.setLong  (5, contribution.getTaskId());

			// Ex�cution de la requ�te
			int removed = pStmt.executeUpdate();
			if (removed!=1)
				throw new SQLException("No row was deleted");

			// Fermeture du statement
			pStmt.close();
			pStmt = null;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la suppression d'une contribution", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * Supprime une dur�e du r�f�rentiel de dur�es.
	 * @param tx le contexte de transaction.
	 * @param duration la dur�e � supprimer.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static void removeDuration(DbTransaction tx, long duration) throws DbException {
		PreparedStatement pStmt = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();

			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("delete from duration where dur_id=?");
			pStmt.setLong  (1, duration);

			// Ex�cution de la requ�te
			int removed = pStmt.executeUpdate();
			if (removed!=1)
				throw new SQLException("No row was deleted");

			// Fermeture du statement
			pStmt.close();
			pStmt = null;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la suppression de la dur�e '" + duration + "'", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * Supprime une tache.
	 * @param tx le contexte de transaction.
	 * @param task la tache � supprimer.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static void removeTask(DbTransaction tx, Task task) throws DbException {
		PreparedStatement pStmt = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();

			// Control sur les sous taches
			Task[] subTasks = DbMgr.getSubtasks(tx, task);
			for (int i=0; i<subTasks.length; i++) {
				DbMgr.removeTask(tx, subTasks[i]);
			}

			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("delete from task where tsk_id=?");
			pStmt.setLong  (1, task.getId());

			// Ex�cution de la requ�te
			int removed = pStmt.executeUpdate();
			if (removed!=1)
				throw new SQLException("No row was deleted");

			// Fermeture du statement
			pStmt.close();
			pStmt = null;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la suppression de la tache '" + task + "'", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * Annule le modifications effectu�es dans le cadre d'une transactrion.
	 * @param tx contexte de transaction.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static void rollbackTransaction(DbTransaction tx) throws DbException {
		try { tx.getConnection().rollback();	}
		catch (SQLException e ) {
			log.info("Incident SQL", e);
			throw new DbException("Echec du rollback", e);
		}
	}

	/**
	 * Modifie les attributs d'un collaborateur.
	 * @param tx contexte de transaction.
	 * @param collaborator le collaborateur � modifier.
	 * @return le collaborateur modifi�.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static Collaborator updateCollaborator(DbTransaction tx, Collaborator collaborator) throws DbException {
		PreparedStatement pStmt = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();

			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("update collaborator set clb_login=?, clb_first_name=?, clb_last_name=? where clb_id=?");
			pStmt.setString(1, collaborator.getLogin());
			pStmt.setString(2, collaborator.getFirstName());
			pStmt.setString(3, collaborator.getLastName());
			pStmt.setLong  (4, collaborator.getId());

			// Ex�cution de la requ�te
			int updated = pStmt.executeUpdate();
			if (updated!=1)
				throw new SQLException("No row was updated");

			// Fermeture du statement
			pStmt.close();
			pStmt = null;

			// Retour du r�sultat
			return collaborator;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la mise � jour du collaborateur '" + collaborator.getLogin() + "'", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * Modifie les attributs d'une contribution.
	 * @param tx contexte de transaction.
	 * @param contribution la contribution � modifier.
	 * @return la contribution modifi�e.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static Contribution updateContribution(DbTransaction tx, Contribution contribution) throws DbException {
		PreparedStatement pStmt = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();

			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("update contribution set ctb_duration=? where ctb_year=? and ctb_month=? and ctb_day=? and ctb_contributor=? and ctb_task=?");
			pStmt.setLong  (1, contribution.getDuration());
			pStmt.setInt   (2, contribution.getYear());
			pStmt.setInt   (3, contribution.getMonth());
			pStmt.setInt   (4, contribution.getDay());
			pStmt.setLong  (5, contribution.getContributorId());
			pStmt.setLong  (6, contribution.getTaskId());
			
			// Ex�cution de la requ�te
			int updated = pStmt.executeUpdate();
			if (updated!=1)
				throw new SQLException("No row was updated");

			// Fermeture du statement
			pStmt.close();
			pStmt = null;

			// Retour du r�sultat
			return contribution;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la mise � jour d'une contribution", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * Change la tache d'une contribution.
	 * @param tx contexte de transaction.
	 * @param contribution la contribution.
	 * @param newContributionTask la tache � affecter.
	 * @return la contribution mise � jour.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static Contribution changeContributionTask(
		DbTransaction tx, Contribution contribution, Task newContributionTask)
		throws DbException {
		PreparedStatement pStmt = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();

			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("update contribution set ctb_task=? where ctb_year=? and ctb_month=? and ctb_day=? and ctb_contributor=? and ctb_task=?");
			pStmt.setLong  (1, newContributionTask.getId());
			pStmt.setInt   (2, contribution.getYear());
			pStmt.setInt   (3, contribution.getMonth());
			pStmt.setInt   (4, contribution.getDay());
			pStmt.setLong  (5, contribution.getContributorId());
			pStmt.setLong  (6, contribution.getTaskId());
			
			// Ex�cution de la requ�te
			int updated = pStmt.executeUpdate();
			if (updated!=1)
				throw new SQLException("No row was updated");

			// Mise � jour de la contribution
			contribution.setTaskId(newContributionTask.getId());

			// Fermeture du statement
			pStmt.close();
			pStmt = null;

			// Retour du r�sultat
			return contribution;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la mise � jour d'une contribution", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}
	
	/**
	 * Modifie les attributs d'une tache.
	 * @param tx contexte de transaction.
	 * @param task la tache � modifier.
	 * @return la tache modifi�e.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static Task updateTask(DbTransaction tx, Task task) throws DbException {
		PreparedStatement pStmt = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();

			// Pr�paration de la requ�te
			pStmt = con.prepareStatement("update task set tsk_path=?, tsk_number=?, tsk_code=?, tsk_name=?, tsk_budget=?, tsk_initial_cons=?, tsk_todo=? where tsk_id=?");
			pStmt.setString(1, task.getPath());
			pStmt.setByte  (2, task.getNumber());
			pStmt.setString(3, task.getCode());
			pStmt.setString(4, task.getName());
			pStmt.setLong  (5, task.getBudget());
			pStmt.setLong  (6, task.getInitiallyConsumed());
			pStmt.setLong  (7, task.getTodo());
			pStmt.setLong  (8, task.getId());
	
			// Ex�cution de la requ�te
			int updated = pStmt.executeUpdate();
			if (updated!=1)
				throw new SQLException("No row was updated");
	
			// Fermeture du statement
			pStmt.close();
			pStmt = null;

			// Retour du r�sultat
			return task;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la mise � jour de la tache '" + task.getName() + "'", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}
	
	/**
	 * Complete la requete de calcul de la somme des contributions.
	 * @param requestBase buffer utilis� pour la construction de la requ�te.
	 * @param contributor le collaborateur ayant contribu� � la tache (facultatif).
	 * @param year l'ann�e (facultative)
	 * @param month le mois (facultatif)
	 * @param day le jour (facultatif)
	 */
	private static void completeContributionRequest(StringBuffer requestBase, Collaborator contributor, Integer year, Integer month, Integer day) {
		if (contributor!=null) requestBase.append(" and ctb_contributor=?");
		if (year!=null) requestBase.append(" and ctb_year=?");
		if (month!=null) requestBase.append(" and ctb_month=?");
		if (day!=null) requestBase.append(" and ctb_day=?");
		log.debug("built request : " + requestBase.toString());
	}

	/**
	 * Complete les param�tres de la requete de calcul de la somme des contributions.
	 * @param pStmt le statement.
	 * @param startIndex
	 * @param contributor le collaborateur ayant contribu� � la tache (facultatif).
	 * @param year l'ann�e (facultative)
	 * @param month le mois (facultatif)
	 * @param day le jour (facultatif)
	 * @throws SQLException lev� en cas d'incident avec la base de donn�es.
	 */
	private static void completeContributionReqParams(PreparedStatement pStmt, int startIndex, Collaborator contributor, Integer year, Integer month, Integer day) throws SQLException {
		int idx = startIndex;
		log.debug("contributorId=" + (contributor!=null ? String.valueOf(contributor.getId()) : "null"));
		log.debug("year=" + year);
		log.debug("month=" + month);
		log.debug("day=" + day);
		if (contributor!=null) pStmt.setLong(idx++, contributor.getId());
		if (year!=null) pStmt.setInt(idx++, year.intValue());
		if (month!=null) pStmt.setInt(idx++, month.intValue());
		if (day!=null) pStmt.setInt(idx++, day.intValue());
	}
	
	/**
	 * G�n�re un nouveau num�ro de tache pour un chemin donn�.
	 * @param tx le contexte de transaction.
	 * @param path le chemin consid�r�.
	 * @return le num�ro g�n�r�.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	protected static byte newTaskNumber(DbTransaction tx, String path) throws DbException {
		PreparedStatement pStmt = null;
		ResultSet rs = null;
		try {
			// R�cup�ration de la connexion
			Connection con = tx.getConnection();
	
			// Recherche du max
			pStmt = con.prepareStatement("select max(tsk_number) from task where tsk_path=?");
			pStmt.setString(1, path);
			rs = pStmt.executeQuery();
			if (!rs.next())
				throw new DbException("Nothing returned from this query", null);
			byte max = rs.getByte(1);
			log.debug("  => max= : " + max);
	
			// Fermeture du statement
			pStmt.close();
			pStmt = null;
			
			// Retour du r�sultat
			return (byte) (max + 1);
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la g�n�ration d'un nouveau num�ro de tache pour le chemin '" + path + "'", e);
		}
		finally {
			if (pStmt!=null) try { pStmt.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * Retourne l'identifiant g�n�r� automatiquement par la base de donn�es.
	 * @param pStmt le statement SQL.
	 * @return l'identifiant g�n�r�.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	private static long getGeneratedId(PreparedStatement pStmt) throws DbException {
		long generatedId = -1;
		PreparedStatement pStmt1 = null;
		try {
			// R�cup�ration de la connexion
			Connection con = pStmt.getConnection();
			// Cas de HSQLDB
			if (isHSQLDB(con)) {
				log.debug("HSQL Database detected");
				pStmt1 = con.prepareStatement("call identity()");
				ResultSet rs = pStmt1.executeQuery();
				if (!rs.next())
					throw new DbException("Nothing returned from this query", null);
				generatedId = rs.getLong(1);
				
				// Fermeture du statement
				pStmt1.close();
				pStmt1 = null;
			}
			else {
				log.debug("Generic Database detected");
				// R�cup�ration de l'identifiant g�n�r�
				ResultSet rs = pStmt.getGeneratedKeys();
				if (!rs.next())
					throw new DbException("Nothing returned from this query", null);
				generatedId = rs.getLong(1);
			}
			// Retour du r�sultat
			log.debug("Generated id=" + generatedId);
			return generatedId;
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la r�cup�ration de l'identifiant du nouvel objet", e);
		}
		finally {
			if (pStmt1!=null) try { pStmt1.close(); } catch (Throwable ignored) { }
		}
	}

	/**
	 * Indique si la BDD de donn�es est une base HSQLDB.
	 * @param con la connexion SQL.
	 * @return un bool�en indiquant si la BDD est de type HSQLDB.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	private static boolean isHSQLDB(Connection con) throws DbException {
		try {
			// R�cup�ration du nom de la base de donn�es
			String dbName = con.getMetaData().getDatabaseProductName();
			log.debug("DbName=" + dbName);
			return "HSQL Database Engine".equals(dbName);
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la r�cup�ration du nom de la BDD", e);
		}
	}

	/**
	 * Indique si la BDD de donn�es est une base HSQLDB embarqu�e.
	 * @param con la connexion SQL.
	 * @return un bool�en indiquant si la BDD est de type HSQLDB embarqu�e.
	 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
	 */
	private static boolean isEmbeddedHSQLDB(Connection con) throws DbException {
		try {
			// R�cup�ration du nom de la base de donn�es
			String dbName = con.getMetaData().getDatabaseProductName();
			log.debug("DbName=" + dbName);
			return isHSQLDB(con) && ds.getUrl().startsWith("jdbc:hsqldb:file");
		}
		catch (SQLException e) {
			log.info("Incident SQL", e);
			throw new DbException("Echec lors de la r�cup�ration du nom de la BDD", e);
		}
	}

}
