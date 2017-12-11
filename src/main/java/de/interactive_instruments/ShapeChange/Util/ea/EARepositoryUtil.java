/**
 * ShapeChange - processing application schemas for geographic information
 *
 * This file is part of ShapeChange. ShapeChange takes a ISO 19109 
 * Application Schema from a UML model and translates it into a 
 * GML Application Schema or other implementation representations.
 *
 * Additional information about the software can be found at
 * http://shapechange.net/
 *
 * (c) 2002-2017 interactive instruments GmbH, Bonn, Germany
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact:
 * interactive instruments GmbH
 * Trierer Strasse 70-72
 * 53115 Bonn
 * Germany
 */
package de.interactive_instruments.ShapeChange.Util.ea;

import java.io.File;

import org.sparx.Collection;
import org.sparx.Connector;
import org.sparx.CreateModelType;
import org.sparx.Element;
import org.sparx.Package;
import org.sparx.Repository;

import de.interactive_instruments.ShapeChange.Model.PackageInfo;

/**
 * @author Johannes Echterhoff (echterhoff <at> interactive-instruments
 *         <dot> de)
 *
 */
public class EARepositoryUtil extends AbstractEAUtil {

	/*
	 * Performance & speed: Repository.BatchAppend and Repository.EnableCache
	 * may be useful.
	 * (http://sparxsystems.com/forums/smf/index.php?topic=1308.0).
	 */

	/**
	 * Runs Java garbage collector (System.gc()) and then the finalization
	 * (System.runFinalization()).
	 */
	public static void compact() {

		/*
		 * Inspired by:
		 * http://sparxsystems.com/forums/smf/index.php?topic=1308.0
		 * http://www.sparxsystems.com/forums/smf/index.php?topic=2134.0
		 */
		System.gc();
		System.runFinalization();
	}

	/**
	 * Documentation provided by EA: <i>Set this property to <code>true</code>
	 * when your automation client has to rapidly insert many elements,
	 * operations, attributes and/or operation parameters. Set to
	 * <code>false</code> when work is complete. This can result in 10- to
	 * 20-fold improvement in adding new elements in bulk.</i>
	 * 
	 * @param rep
	 * @param batchAppend
	 */
	public static void setEABatchAppend(Repository rep, boolean batchAppend) {

		rep.SetBatchAppend(batchAppend);
	}

	/**
	 * Documentation provided by EA: <i>Set this property to <code>false</code>
	 * to improve the performance of changes to the model; for example, bulk
	 * addition of elements to a Package. To reveal changes to the user, call
	 * 'Repository.RefreshModelView()'.</i>
	 * 
	 * @param rep
	 * @param batchAppend
	 */
	public static void setEAEnableUIUpdates(Repository rep,
			boolean enableUIUpdates) {

		rep.SetEnableUIUpdates(enableUIUpdates);
	}

	/**
	 * @param rep
	 *            the repository to query
	 * @param eaParentPkgId
	 *            EA ID of the parent package
	 * @param name
	 *            Name of the child package to look up
	 * @return the EA ID of the package if it was found, else <code>null</code>
	 */
	public static Integer getEAChildPackageByName(Repository rep,
			int eaParentPkgId, String name) {

		Package eaParentPkg = rep.GetPackageByID(eaParentPkgId);

		Collection<Package> pkgs = eaParentPkg.GetPackages();

		for (short i = 0; i < pkgs.GetCount(); i++) {

			Package p = pkgs.GetAt(i);
			if (p.GetName().equals(name)) {
				return p.GetPackageID();
			}
		}

		return null;
	}

	/**
	 * Identify the child package with given stereotype. NOTE: Additional such
	 * packages are ignored.
	 * 
	 * @param rep
	 *            the repository to query
	 * @param eaParentPkgId
	 *            EA ID of the parent package
	 * @param stereotype
	 *            Stereotype of the child package to look up
	 * @return the EA ID of the package if it was found, else <code>null</code>
	 */
	public static Integer getEAChildPackageByStereotype(Repository rep,
			int eaParentPkgId, String stereotype) {

		Package eaParentPkg = rep.GetPackageByID(eaParentPkgId);

		Collection<Package> pkgs = eaParentPkg.GetPackages();

		for (short i = 0; i < pkgs.GetCount(); i++) {

			Package p = pkgs.GetAt(i);
			if (p.GetStereotypeEx().contains(stereotype)) {
				return p.GetPackageID();
			}
		}

		return null;
	}

	/**
	 * Deletes the package with given EA ID. Also works for model packages.
	 * 
	 * @param rep
	 *            the repository to query
	 * @param eaPkgId
	 *            EA ID of the package to delete
	 */
	public static void deletePackage(Repository rep, int eaPkgId) {

		Package eaPkg = rep.GetPackageByID(eaPkgId);

		if (eaPkg != null) {

			Collection<Package> pkgs;

			if (eaPkg.GetParentID() == 0) {

				// the package is a model (that is, it has no parent)
				pkgs = rep.GetModels();

			} else {

				Package eaParentPkg = rep.GetPackageByID(eaPkg.GetParentID());
				pkgs = eaParentPkg.GetPackages();
			}

			for (short i = 0; i < pkgs.GetCount(); i++) {

				Package p = pkgs.GetAt(i);

				if (p.GetPackageID() == eaPkgId) {
					pkgs.Delete(i);
					pkgs.Refresh();
					break;
				}
			}
		}
	}

	public static void createEAGeneralization(Repository rep, int c1ElementId,
			String c1Name, int c2ElementId, String c2Name) throws EAException {

		Element c1 = rep.GetElementByID(c1ElementId);

		Collection<Connector> c1Cons = c1.GetConnectors();

		Connector con = c1Cons.AddNew("", "Generalization");

		con.SetSupplierID(c2ElementId);

		if (!con.Update()) {
			throw new EAException(createMessage(message(102), c1Name, c2Name,
					con.GetLastError()));
		}

		c1Cons.Refresh();
	}

	public static void closeRepository(Repository rep) {

		rep.SetEnableUIUpdates(true);
		rep.RefreshModelView(0);
		rep.SetEnableCache(false);
		rep.SetBatchAppend(false);
		rep.CloseFile();
		rep.Exit();
	}

	public static Repository openRepository(File repfileIn,
			boolean createIfNotExisting) throws EAException {

		File repfile = repfileIn;

		/*
		 * Check file name; append '.eap' if necessary.
		 */
		if (!repfileIn.getName().toLowerCase().endsWith(".eap")) {
			repfile = new java.io.File(repfileIn.getName() + ".eap");
		}

		String absname = repfile.getAbsolutePath();

		Repository rep = new Repository();

		if (!repfile.exists() && createIfNotExisting) {
			if (!rep.CreateModel(CreateModelType.cmEAPFromBase, absname, 0)) {
				String errormsg = rep.GetLastError();
				rep = null;
				throw new EAException("Could not create EA repository at "
						+ absname + ". EA error message is: " + errormsg);
			}
		}

		if (!rep.OpenFile(absname)) {
			String errormsg = rep.GetLastError();
			rep = null;
			throw new EAException("Could not open EA repository at " + absname
					+ ". EA error message is: " + errormsg);
		}

		return rep;
	}

	public static Element createEAClass(Repository rep, String className,
			int eaPkgId) throws EAException {

		return createEAClass(rep, className, eaPkgId, "Class");
	}

	public static Element createEAClass(Repository rep, String className,
			int eaPkgId, String type) throws EAException {

		Package eaPkg = rep.GetPackageByID(eaPkgId);

		Collection<Element> elements = eaPkg.GetElements();

		Element e = elements.AddNew(className, type);

		if (!e.Update()) {
			throw new EAException(
					createMessage(message(101), className, e.GetLastError()));
		}

		elements.Refresh();

		return e;
	}

	/**
	 * Creates an EA package for the given PackageInfo. The new EA package will
	 * be a child of the given EA parent package. Properties such as stereotype
	 * and tagged values are not set by this method and thus need to be added
	 * later on.
	 * 
	 * @param pi
	 *            package to create in EA
	 * @param eaParentPkg
	 *            EA Package element that is the parent of the EA Package to
	 *            create for pi
	 * @throws EAException
	 *             If an EA error was encountered while updating the package
	 */
	public static int createEAPackage(Repository rep, PackageInfo pi,
			int eaParentPkgId) throws EAException {

		Package eaParentPkg = rep.GetPackageByID(eaParentPkgId);

		Collection<Package> eaParentPkgs = eaParentPkg.GetPackages();

		Package eaPkg = eaParentPkgs.AddNew(pi.name(), "Package");

		if (!eaPkg.Update()) {

			throw new EAException(createMessage(message(103), pi.name(),
					eaPkg.GetLastError()));
		}

		eaParentPkgs.Refresh();

		return eaPkg.GetPackageID();
	}

	public static String message(int mnr) {

		switch (mnr) {

		case 101:
			return "EA error encountered while updating new EA class element '$1$'. Error message is: $2$";
		case 102:
			return "EA error encountered while updating new EA (generalization) connector between classes '$1$' and '$2$'. Error message is: $3$";
		case 103:
			return "EA error encountered while updating new EA package '$1$'. Error message is: $2$";
		default:
			return "(" + EARepositoryUtil.class.getName()
					+ ") Unknown message with number: " + mnr;
		}
	}
}
