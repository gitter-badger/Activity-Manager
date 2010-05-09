package jfb.tools.activitymgr.core.util;

import java.io.IOException;
import java.io.OutputStream;

import jfb.tools.activitymgr.core.DbException;
import jfb.tools.activitymgr.core.DbTransaction;
import jfb.tools.activitymgr.core.ModelException;
import jfb.tools.activitymgr.core.ModelMgr;
import jfb.tools.activitymgr.core.beans.Collaborator;
import jfb.tools.activitymgr.core.beans.Contribution;
import jfb.tools.activitymgr.core.beans.Task;

import org.apache.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Classe offrant des services de manipulation ou de g�n�ration
 * de documents XML.
 */
public class XmlHelper implements EntityResolver, ErrorHandler, ContentHandler {

	/**
	 * Interface de cr�ation des objets en base de donn�es.
	 */
	public static interface ModelMgrDelegate {
		
		/**
		 * Cr�e une dur�e dans un contexte de transaction.
		 * @param tx le contexte de transaction.
		 * @param duration la dur�e � cr�er.
		 * @return la dur�e cr��e.
		 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
		 * @throws ModelException lev� dans la cas ou la dur�e existe d�j�.
		 * @see jfb.tools.activitymgr.core.ModelMgr#createDuration(long)
		 */
		public long createDuration(DbTransaction tx, long duration) throws ModelException, DbException;
		
		/**
		 * Cr�e un collaborateur dans un contexte de transaction.
		 * @param tx le contexte de transaction.
		 * @param collaborator le collaborateur � cr�er.
		 * @return le collaborateur apr�s cr�ation.
		 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
		 * @throws ModelException lev� dans la cas ou la tache de destination ne peut recevoir de sous-tache.
		 * @see jfb.tools.activitymgr.core.ModelMgr#createCollaborator(Collaborator)
		 */
		public Collaborator createCollaborator(DbTransaction tx, Collaborator collaborator) throws DbException, ModelException;

		/**
		 * Cr�e une nouvelle tache dans un contexte de transaction.
		 * @param tx le contexte de transaction.
		 * @param parentTask la tache parent de destination.
		 * @param task la tache � cr�er.
		 * @return la tache cr�e.
		 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
		 * @throws ModelException lev� dans la cas ou la tache de destination ne peut recevoir de sous-tache.
		 * @see jfb.tools.activitymgr.core.ModelMgr#createTask(Task, Task)
		 */
		public Task createTask(DbTransaction tx, Task parentTask, Task task) throws DbException, ModelException;

		/**
		 * Cr�e une contribution dans un contexte de transaction.
		 * @param tx le contexte de transaction.
		 * @param contribution la contribution � cr�er.
		 * @return la contribution apr�s cr�ation.
		 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
		 * @throws ModelException lev� dans la cas ou la tache de destination ne peut recevoir de contribution.
		 * @see jfb.tools.activitymgr.core.ModelMgr#createCollaborator(Collaborator)
		 */
		public Contribution createContribution(DbTransaction tx, Contribution contribution) throws DbException, ModelException;

		/**
		 * Retourne la tache associ�e � un chemin construit � partir de 
		 * codes de taches.
		 * @param tx le contexte de transaction.
		 * @param codePath le chemin � base de code.
		 * @return la tache trouv�e.
		 * @throws DbException lev� en cas d'incident technique avec la base de donn�es.
		 * @throws ModelException lev� dans le cas ou le chemin de tache est inconnu. 
		 */
		public Task getTaskByCodePath(DbTransaction tx, final String codePath) throws DbException, ModelException;

		/**
		 * Retourne le collabirateur dont le login est sp�cifi� dans un contexte
		 * de transaction.
		 * @param tx le contexte de transaction.
		 * @param login l'identifiant de connexion du collaborateur recherch�.
		 * @return le collaborateur dont l'identifiant de connexion est sp�cifi�.
		 * @throws DbException lev� en cas d'incident technique d'acc�s � la base.
		 */
		public Collaborator getCollaborator(DbTransaction tx, String login) throws DbException;

	}
	
	/** Logger */
	private static Logger log = Logger.getLogger(XmlHelper.class);

	/** Constantes */
	public static final String MODEL_NODE = "model";
	public static final String DURATIONS_NODE = "durations";
	public static final String DURATION_NODE = "duration";
	public static final String COLLABORATORS_NODE = "collaborators";
	public static final String COLLABORATOR_NODE = "collaborator";
	public static final String TASKS_NODE = "tasks";
	public static final String TASK_NODE = "task";
	public static final String CONTRIBUTIONS_NODE = "contributions";
	public static final String CONTRIBUTION_NODE = "contribution";
	public static final String LOGIN_NODE = "login";
	public static final String FIRST_NAME_NODE = "first-name";
	public static final String LAST_NAME_NODE = "last-name";
	public static final String YEAR_ATTRIBUTE = "year";
	public static final String MONTH_ATTRIBUTE = "month";
	public static final String DAY_ATTRIBUTE = "day";
	public static final String DURATION_ATTRIBUTE = "duration";
	public static final String CONTRIBUTOR_REF_NODE = "contributor-ref";
	public static final String TASK_REF_NODE = "task-ref";
	public static final String PATH_NODE = "path";
	public static final String NAME_NODE = "name";
	public static final String BUDGET_NODE = "budget";
	public static final String INITIALLY_CONSUMED_NODE = "initially-consumed";
	public static final String TODO_NODE = "todo";
	
	/** Contexte de transaction */
	private DbTransaction transaction;
	
	/** Gestionnaire du mod�le */
	private ModelMgrDelegate modelMgrDelegate;
	
	/** Sauvegarde des objets en cours de chargement */
	private Collaborator currentCollaborator;
	private Task currentParentTask;
	private Task currentTask;
	private Contribution currentContribution;
	private StringBuffer currentText = new StringBuffer();
	
	/** Pointeur de location dans le fichier XML pars� */
	private Locator locator;
	
	/**
	 * Constructeur par d�faut.
	 * @param modelMgrDelegate d�l�gu� du gestionnaire de mod�le.
	 * @param tx contexte de transaction.
	 */
	public XmlHelper(ModelMgrDelegate modelMgrDelegate, DbTransaction tx) {
		this.modelMgrDelegate = modelMgrDelegate;
		this.transaction = tx;
	}
	
	/** EntityResolver interface methods */

	/* (non-Javadoc)
	 * @see org.xml.sax.EntityResolver#resolveEntity(java.lang.String, java.lang.String)
	 */
	public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
		log.debug("resolveEntity(" + publicId + ", " + systemId + ")");
		return new InputSource(ModelMgr.class.getResourceAsStream("activitymgr.dtd"));
	}

	/** ErrorHandler interface methods */
	
	/* (non-Javadoc)
	 * @see org.xml.sax.ErrorHandler#error(org.xml.sax.SAXParseException)
	 */
	public void error(SAXParseException e) throws SAXParseException {
		log.error("SAX error line : " + e.getLineNumber() + " column : " + e.getColumnNumber(), e);
		throw e;
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ErrorHandler#fatalError(org.xml.sax.SAXParseException)
	 */
	public void fatalError(SAXParseException e) throws SAXParseException {
		error(e);
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ErrorHandler#warning(org.xml.sax.SAXParseException)
	 */
	public void warning(SAXParseException e) throws SAXParseException {
		error(e);
	}

	/** ContentHandlet interface methods */
	
	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
		log.debug("start(" + qName + ")");
		if (MODEL_NODE.equals(qName)
			|| DURATIONS_NODE.equals(qName)
			|| DURATION_NODE.equals(qName)
			|| COLLABORATORS_NODE.equals(qName)
			|| LOGIN_NODE.equals(qName)
			|| FIRST_NAME_NODE.equals(qName)
			|| LAST_NAME_NODE.equals(qName)
			|| TASKS_NODE.equals(qName)
			|| PATH_NODE.equals(qName)
			|| NAME_NODE.equals(qName)
			|| BUDGET_NODE.equals(qName)
			|| INITIALLY_CONSUMED_NODE.equals(qName)
			|| TODO_NODE.equals(qName)
			|| CONTRIBUTIONS_NODE.equals(qName)
			|| CONTRIBUTOR_REF_NODE.equals(qName)
			|| TASK_REF_NODE.equals(qName)) {
			// Do nothing...
		}
		else if (COLLABORATOR_NODE.equals(qName)) {
			currentCollaborator = new Collaborator();
		}
		else if (TASK_NODE.equals(qName)) {
			currentTask = new Task();
		}
		else if (CONTRIBUTION_NODE.equals(qName)) {
			currentContribution = new Contribution();
			currentContribution.setYear((int) getNumAttrValue(atts, YEAR_ATTRIBUTE));
			currentContribution.setMonth((int) getNumAttrValue(atts, MONTH_ATTRIBUTE));
			currentContribution.setDay((int) getNumAttrValue(atts, DAY_ATTRIBUTE));
			currentContribution.setDuration(getNumAttrValue(atts, DURATION_ATTRIBUTE));
		}
		else {
			error(new SAXParseException("Unexpected node '" + qName + "'", locator));
		}
	}

	/**
	 * Retourne la valeur d'un attribut num�rique ou l�ve un exception si 
	 * l'attribut n'existe pas ou qu'il n'a pas un format correct.
	 * @param atts la liste des attributs.
	 * @param qName le nom de l'attribut.
	 * @return la valeur de l'attribut.
	 * @throws SAXParseException lev� en cas d'absence ou de mauvais format de l'attribut.
	 */
	private long getNumAttrValue(Attributes atts, String qName) throws SAXParseException {
		String value = atts.getValue(qName);
		if (value==null)
			error(new SAXParseException("Missing attribute value for '" + qName + "'", locator));
		// Parsing
		long result = -1;
		try { result = Long.parseLong(value); }
		catch (NumberFormatException e) {
			error(new SAXParseException("Invalid attribute value '" + value + "' for '" + qName + "'", locator));
		}
		// Retour du r�sultat
		return result;
	}
	
	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
		log.debug("end(" + qName + ")");
		try {
			String textToSave = currentText.toString();
			// R�initialisation de la sauvegarde du texte courant
			currentText.setLength(0);
			if (MODEL_NODE.equals(qName)
				|| DURATIONS_NODE.equals(qName)
				|| COLLABORATORS_NODE.equals(qName)
				|| TASKS_NODE.equals(qName)
				|| CONTRIBUTIONS_NODE.equals(qName)) {
				// Do nothing...
			}
			else if (DURATION_NODE.equals(qName)) {
				long durationToCreate = Long.parseLong(textToSave);
				modelMgrDelegate.createDuration(transaction, durationToCreate);
			}
			else if (COLLABORATOR_NODE.equals(qName)) {
				Collaborator collaboratorToCreate = currentCollaborator;
				currentCollaborator = null;
				modelMgrDelegate.createCollaborator(transaction, collaboratorToCreate);
			}
			else if (LOGIN_NODE.equals(qName)) {
				currentCollaborator.setLogin(textToSave);
			}
			else if (FIRST_NAME_NODE.equals(qName)) {
				currentCollaborator.setFirstName(textToSave);
			}
			else if (LAST_NAME_NODE.equals(qName)) {
				currentCollaborator.setLastName(textToSave);
			}
			else if (TASK_NODE.equals(qName)) {
				Task taskToCreate = currentTask;
				Task parentOfTaskToCreate = currentParentTask;
				currentTask = null;
				currentParentTask = null;
				modelMgrDelegate.createTask(transaction, parentOfTaskToCreate, taskToCreate);
			}
			else if (PATH_NODE.equals(qName)) {
				log.debug("textToSave='" + textToSave + "'");
				String parentPath = textToSave.substring(0, textToSave.lastIndexOf('/'));
				log.debug("parentPath='" + parentPath + "'");
				currentParentTask = "".equals(parentPath) ? null : modelMgrDelegate.getTaskByCodePath(transaction, parentPath);
				String taskCode = textToSave.substring(parentPath.length()+1);
				currentTask.setCode(taskCode);
			}
			else if (NAME_NODE.equals(qName)) {
				currentTask.setName(textToSave);
			}
			else if (BUDGET_NODE.equals(qName)) {
				currentTask.setBudget(Long.parseLong(textToSave));
			}
			else if (INITIALLY_CONSUMED_NODE.equals(qName)) {
				currentTask.setInitiallyConsumed(Long.parseLong(textToSave));
			}
			else if (TODO_NODE.equals(qName)) {
				currentTask.setTodo(Long.parseLong(textToSave));
			}
			else if (CONTRIBUTION_NODE.equals(qName)) {
				Contribution contributionToCreate = currentContribution;
				currentContribution = null;
				modelMgrDelegate.createContribution(transaction, contributionToCreate);
			}
			else if (CONTRIBUTOR_REF_NODE.equals(qName)) {
				Collaborator collaborator = modelMgrDelegate.getCollaborator(transaction, textToSave);
				currentContribution.setContributorId(collaborator.getId());
			}
			else if (TASK_REF_NODE.equals(qName)) {
				Task task = modelMgrDelegate.getTaskByCodePath(transaction, textToSave);
				currentContribution.setTaskId(task.getId());
			}
			else {
				error(new SAXParseException("Unexpected node '" + qName + "'", locator));
			}
		}
		catch (NumberFormatException e) {
			log.error("Number format error", e);
			error(new SAXParseException("Number format error : " + e.getMessage(), locator, e));
		}
		catch (ModelException e) {
			log.error("Model violation", e);
			error(new SAXParseException("Model violation : " + e.getMessage(), locator, e));
		}
		catch (DbException e) {
			log.error("Unexpected database access error", e);
			throw new SAXException("Unexpected database access error", e);
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endDocument()
	 */
	public void endDocument() throws SAXException {
		log.debug("endDocument()");
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#startDocument()
	 */
	public void startDocument() throws SAXException {
		log.debug("startDocument()");
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#characters(char[], int, int)
	 */
	public void characters(char[] ch, int start, int length) throws SAXException {
		String chars = new String(ch, start, length);
		log.debug("characters(" + chars + ")");
		currentText.append(chars);
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#ignorableWhitespace(char[], int, int)
	 */
	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
		log.debug("ignorableWhitespace(" + new String(ch, start, length) + ")");
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endPrefixMapping(java.lang.String)
	 */
	public void endPrefixMapping(String prefix) throws SAXException {
		saxFeatureNotImplemented("endPrefixMapping");
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#skippedEntity(java.lang.String)
	 */
	public void skippedEntity(String name) throws SAXException {
		saxFeatureNotImplemented("skippedEntity");
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#setDocumentLocator(org.xml.sax.Locator)
	 */
	public void setDocumentLocator(Locator locator) {
		log.debug("setDocumentLocator(" + locator + ")");
		this.locator = locator;
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#processingInstruction(java.lang.String, java.lang.String)
	 */
	public void processingInstruction(String target, String data) throws SAXException {
		saxFeatureNotImplemented("processingInstruction");
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#startPrefixMapping(java.lang.String, java.lang.String)
	 */
	public void startPrefixMapping(String prefix, String uri) throws SAXException {
		saxFeatureNotImplemented("startPrefixMapping");
	}

	/**
	 * L�ve une exception indiquant qu'une fontionnalit� n'est pas impl�ment�e pour ActivityManager
	 * @param featureName nom de la fonctionnalit�
	 * @throws SAXException lev�e dans tous les cas.
	 */
	private void saxFeatureNotImplemented(String featureName) throws SAXException {
		error(new SAXParseException("SAX feature not implemented for ActivityManager ('" + featureName + "')", locator));
	}
	
	/** Static XML serialization methods */
	
	/**
	 * Commence un noeud XML dans le flux d'�criture.
	 * @param indent l'indentation.
	 * @param out le flux d'�criture.
	 * @param name le nom du noeud XML.
	 * @throws IOException lev� en cas d'incident I/O lors de l'�criture sur le flux de sortie.
	 */
	public static void startXmlNode(
			OutputStream out, 
			String indent,
			String name) throws IOException {
		print(out, indent);
		out.write('<');
		print(out, name);
		out.write('>');
		out.write('\n');
	}

	/**
	 * Termine un noeud XML dans le flux d'�criture avec une indentation.
	 * @param out le flux d'�criture.
	 * @param indent l'indentation.
	 * @param name le nom du noeud XML.
	 * @throws IOException lev� en cas d'incident I/O lors de l'�criture sur le flux de sortie.
	 */
	public static void endXmlNode(
			OutputStream out, 
			String indent,
			String name) throws IOException {
		print(out, indent);
		endXmlNode(out, name);
	}

	/**
	 * Termine un noeud XML dans le flux d'�criture.
	 * @param out le flux d'�criture.
	 * @param name le nom du noeud XML.
	 * @throws IOException lev� en cas d'incident I/O lors de l'�criture sur le flux de sortie.
	 */
	public static void endXmlNode(
			OutputStream out, 
			String name) throws IOException {
		out.write('<');
		out.write('/');
		print(out, name);
		out.write('>');
		out.write('\n');
	}

	/**
	 * Ecrit un noeud XML dans le flux d'�criture.
	 * @param indent l'indentation.
	 * @param out le flux d'�criture.
	 * @param name le nom du noeud XML.
	 * @param value la valeur du noeud XML.
	 * @throws IOException lev� en cas d'incident I/O lors de l'�criture sur le flux de sortie.
	 */
	public static void printTextNode(
			OutputStream out, 
			String indent, 
			String name, 
			String value) throws IOException {
		print(out, indent);
		out.write('<');
		print(out, name);
		out.write('>');
		printTextValue(out, value);
		endXmlNode(out, name);
	}

	/**
	 * Ecrit un attribut de noeud XML dans le flux d'�criture.
	 * @param out le flux d'�criture.
	 * @param name le nom de l'attribut XML.
	 * @param value la valeur de l'attribut XML.
	 * @throws IOException lev� en cas d'incident I/O lors de l'�criture sur le flux de sortie.
	 */
	public static void printTextAttribute(
			OutputStream out, 
			String name, 
			String value) throws IOException {
		out.write(' ');
		print(out, name);
		print(out, "=\"");
		printTextValue(out, value);
		print(out, "\"");
	}

	/**
	 * Ecrit une cha�ne de caract�res dans le flux de sortie en rempla�ant les
	 * caract�res sp�ciaux.
	 * @param out le flux de sortie.
	 * @param str la cha�ne de caract�res.
	 * @throws IOException lev� en cas d'incident lors de l'�criture des donn�es sur le flux.
	 */
	public static void printTextValue(OutputStream out, String str) throws IOException {
		str = str.replaceAll( "&", "&amp;");
		str = str.replaceAll( ">", "&gt;");
		str = str.replaceAll( "<", "&lt;");
		print(out, str);
	}

	/**
	 * Ecrit une cha�ne de caract�res dans le flux de sortie.
	 * @param out le flux de sortie.
	 * @param str la cha�ne de caract�res.
	 * @throws IOException lev� en cas d'incident lors de l'�criture des donn�es sur le flux.
	 */
	public static void print(OutputStream out, String str) throws IOException {
		out.write(str.getBytes("UTF-8"));
	}

	/**
	 * Ecrit une cha�ne de caract�res dans le flux de sortie.
	 * @param out le flux de sortie.
	 * @param s la cha�ne de caract�res.
	 * @throws IOException lev� en cas d'incident lors de l'�criture des donn�es sur le flux.
	 */
	public static void println(OutputStream out, String s) throws IOException {
		print(out, s);
		out.write('\n');
	}

}
