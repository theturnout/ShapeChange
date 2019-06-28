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
package de.interactive_instruments.ShapeChange.Model.Generic.reader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import de.interactive_instruments.ShapeChange.Options;
import de.interactive_instruments.ShapeChange.ShapeChangeResult;
import de.interactive_instruments.ShapeChange.Model.Constraint;
import de.interactive_instruments.ShapeChange.Model.Constraint.ModelElmtContextType;
import de.interactive_instruments.ShapeChange.Model.Generic.GenericFolConstraint;
import de.interactive_instruments.ShapeChange.Model.Generic.GenericOclConstraint;
import de.interactive_instruments.ShapeChange.Model.Generic.GenericTextConstraint;

/**
 * @author Johannes Echterhoff (echterhoff <at> interactive-instruments <dot>
 *         de)
 *
 */
public class ConstraintContentHandler extends AbstractContentHandler {

	private static final Set<String> CONSTRAINT_FIELDS = new HashSet<String>(
			Arrays.asList(new String[] { "name", "status", "text", "type",
					"sourceType", "contextModelElementId",
					"contextModelElementType", "description" }));

	private String name = null;
	private String status = null;
	private String text = null;
	private List<String> descriptions = new ArrayList<>();
	private String type = null;
	private String sourceType = null;
	private String contextModelElementId = null;
	private String contextModelElementType = null;

	private Constraint constraint = null;

	public ConstraintContentHandler(ShapeChangeResult result, Options options,
			XMLReader reader, AbstractGenericInfoContentHandler parent) {
		super(result, options, reader, parent);
		this.parent = parent;
	}

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes atts) throws SAXException {

		if (CONSTRAINT_FIELDS.contains(localName)) {

			sb = new StringBuffer();

		} else {

			// do not throw an exception, just log a warning - the schema could
			// have been extended
			result.addWarning(null, 30800, "ConstraintContentHandler",
					localName);
		}
	}

	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {

		if (localName.equals("name")) {

			this.name = sb.toString();

		} else if (localName.equals("status")) {

			this.status = sb.toString();

		} else if (localName.equals("text")) {

			this.text = sb.toString();

		} else if (localName.equals("description")) {

			this.descriptions.add(sb.toString());

		} else if (localName.equals("type")) {

			this.type = sb.toString();

		} else if (localName.equals("sourceType")) {

			this.sourceType = sb.toString();

		} else if (localName.equals("contextModelElementId")) {

			this.contextModelElementId = sb.toString();

		} else if (localName.equals("contextModelElementType")) {

			this.contextModelElementType = sb.toString();

		} else if (localName.equals("FolConstraint")) {

			GenericFolConstraint con = new GenericFolConstraint();
			this.constraint = con;

			con.setName(name);
			con.setStatus(status);

			if (!descriptions.isEmpty()) {
				con.setComments(descriptions.toArray(new String[0]));
			}

			con.setText(text);
			con.setSourceType(sourceType);
			if (this.contextModelElementType != null
					&& this.contextModelElementType
							.equalsIgnoreCase("ATTRIBUTE")) {
				con.setContextModelElmtType(ModelElmtContextType.ATTRIBUTE);
			} else {
				con.setContextModelElmtType(ModelElmtContextType.CLASS);
			}

			returnToParent(uri, localName, qName);

		} else if (localName.equals("OclConstraint")) {

			GenericOclConstraint con = new GenericOclConstraint();
			this.constraint = con;

			con.setName(name);
			con.setStatus(status);

			if (!descriptions.isEmpty()) {
				con.setComments(descriptions.toArray(new String[0]));
			}

			con.setText(text);
			if (this.contextModelElementType != null
					&& this.contextModelElementType
							.equalsIgnoreCase("ATTRIBUTE")) {
				con.setContextModelElmtType(ModelElmtContextType.ATTRIBUTE);
			} else {
				con.setContextModelElmtType(ModelElmtContextType.CLASS);
			}

			returnToParent(uri, localName, qName);

		} else if (localName.equals("TextConstraint")) {

			GenericTextConstraint con = new GenericTextConstraint();
			this.constraint = con;

			con.setName(name);
			con.setStatus(status);
			con.setText(text);
			con.setType(type);
			if (this.contextModelElementType != null
					&& this.contextModelElementType
							.equalsIgnoreCase("ATTRIBUTE")) {
				con.setContextModelElmtType(ModelElmtContextType.ATTRIBUTE);
			} else {
				con.setContextModelElmtType(ModelElmtContextType.CLASS);
			}

			returnToParent(uri, localName, qName);

		} else {
			// do not throw an exception, just log a warning - the schema could
			// have been extended
			result.addWarning(null, 30801, "ConstraintContentHandler",
					localName);
		}
	}

	private void returnToParent(String uri, String localName, String qName)
			throws SAXException {

		// let parent know that we reached the end of the constraint entry
		// (so that for example depth can properly be tracked)
		parent.endElement(uri, localName, qName);

		// Switch handler back to parent
		reader.setContentHandler(parent);
	}

	/**
	 * @return the contextModelElementId
	 */
	public String getContextModelElementId() {
		return contextModelElementId;
	}

	/**
	 * @return the constraint
	 */
	public Constraint getConstraint() {
		return constraint;
	}

}
