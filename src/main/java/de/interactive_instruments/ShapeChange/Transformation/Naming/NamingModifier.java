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
 * (c) 2002-2014 interactive instruments GmbH, Bonn, Germany
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
package de.interactive_instruments.ShapeChange.Transformation.Naming;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import de.interactive_instruments.ShapeChange.MessageSource;
import de.interactive_instruments.ShapeChange.Options;
import de.interactive_instruments.ShapeChange.ProcessRuleSet;
import de.interactive_instruments.ShapeChange.ShapeChangeAbortException;
import de.interactive_instruments.ShapeChange.ShapeChangeResult;
import de.interactive_instruments.ShapeChange.ShapeChangeResult.MessageContext;
import de.interactive_instruments.ShapeChange.TransformerConfiguration;
import de.interactive_instruments.ShapeChange.Model.Info;
import de.interactive_instruments.ShapeChange.Model.Generic.GenericClassInfo;
import de.interactive_instruments.ShapeChange.Model.Generic.GenericModel;
import de.interactive_instruments.ShapeChange.Model.Generic.GenericPropertyInfo;
import de.interactive_instruments.ShapeChange.Transformation.Transformer;

/**
 * Encapsulates the logic for execution of rules that modify the naming of
 * application schema elements.
 * 
 * @author Johannes Echterhoff (echterhoff <at> interactive-instruments
 *         <dot> de)
 * 
 */
public class NamingModifier implements Transformer, MessageSource {

	/* ------------------------------------------- */
	/* --- configuration parameter identifiers --- */
	/* ------------------------------------------- */

	/**
	 * Identifier of the parameter that provides the suffix to add.
	 * 
	 * Applies to rule {@value #RULE_TRF_ADD_SUFFIX}.
	 * 
	 * The parameter is optional - the default value is:
	 * {@value #DEFAULT_SUFFIX}.
	 */
	public static final String PARAM_SUFFIX = "suffix";

	/**
	 * Identifier of the parameter that defines the regular expression for
	 * matching model element names to add a suffix.
	 * 
	 * Applies to rule {@value #RULE_TRF_ADD_SUFFIX}.
	 * 
	 * The parameter is required.
	 */
	public static final String PARAM_SUFFIX_REGEX = "modelElementNamesToAddSuffixRegex";

	/**
	 * Identifier of the parameter that contains a (comma-separated) list of
	 * strings that shall be ignored by
	 * {@value #RULE_TRF_CAMEL_CASE_TO_UPPER_CASE} when they occur as suffix in
	 * the name of a model element. Note that case matters when the process
	 * checks if a model element name ends with one of the given strings.
	 */
	public static final String PARAM_SUFFIXES_TO_IGNORE = "suffixesToIgnore";

	/* ------------------------ */
	/* --- rule identifiers --- */
	/* ------------------------ */

	/**
	 * Identifies the rule used for adding a suffix to the name of specific
	 * model elements.
	 */
	public static final String RULE_TRF_ADD_SUFFIX = "rule-trf-add-suffix";

	/**
	 * Updates the names of application schema classes and their properties as
	 * follows:
	 * <ul>
	 * <li>All lower case letters are replaced with upper case letters.</li>
	 * <li>If a letter or decimal digit is followed by an upper-case letter, the
	 * two are separated by an underscore.</li>
	 * <li>An underscore in the original name is replaced by two underscores.
	 * </li>
	 * <li>A decimal digit is kept as-is.</li>
	 * <li>If the original name contains a suffix identified by parameter
	 * {@value #PARAM_SUFFIXES_TO_IGNORE} then that suffix is kept as-is. If
	 * multiple suffixes (given by the parameter) match the end of the string, a
	 * warning is logged and the suffix with greatest length is chosen.</li>
	 * </ul>
	 * This rule can be useful when Oracle DB naming conventions play a role,
	 * and when the name transformation shall be reversible.
	 * <p/>
	 * NOTE: This rule does not modify the names of enums and codes (i.e. the
	 * properties of enumeration and codelist classes). If these names shall be
	 * modified as well, add
	 * {@value #RULE_TRF_CAMEL_CASE_TO_UPPER_CASE_INCLUDE_ENUMS} and
	 * {@value #RULE_TRF_CAMEL_CASE_TO_UPPER_CASE_INCLUDE_CODES}.
	 * <p/>
	 * Examples:
	 * <ul>
	 * <li>abcDefGhi (parameter '{@value #PARAM_SUFFIXES_TO_IGNORE}' not set) ->
	 * ABC_DEF_GHI</li>
	 * <li>abc_DefGhi (parameter '{@value #PARAM_SUFFIXES_TO_IGNORE}' not set)
	 * -> ABC__DEF_GHI</li>
	 * <li>ABCDefGhi (parameter '{@value #PARAM_SUFFIXES_TO_IGNORE}' not set) ->
	 * A_B_C_DEF_GHI</li>
	 * <li>AbcDEfGHI (parameter '{@value #PARAM_SUFFIXES_TO_IGNORE}' not set) ->
	 * ABC_D_EF_G_H_I</li>
	 * <li>AbcDefGhiID (parameter '{@value #PARAM_SUFFIXES_TO_IGNORE}' set to
	 * 'ID') -> ABC_DEF_GHI_ID</li>
	 * <li>AbcDefGHIID (parameter '{@value #PARAM_SUFFIXES_TO_IGNORE}' set to
	 * 'ID') -> ABC_DEF_G_H_I_ID</li>
	 * <li>AbcDefGHIID (parameter '{@value #PARAM_SUFFIXES_TO_IGNORE}' set to
	 * 'ID, GHIID') -> ABC_DEF_GHIID</li>
	 * <li>AbcDefGhiCL (parameter '{@value #PARAM_SUFFIXES_TO_IGNORE}' set to
	 * 'CL') -> ABC_DEF_GHI_CL</li>
	 * <li>abcDefGhi_CL (parameter '{@value #PARAM_SUFFIXES_TO_IGNORE}' set to
	 * 'CL') -> ABC_DEF_GHI__CL</li>
	 * <li>Abc1D1efG2HI (parameter '{@value #PARAM_SUFFIXES_TO_IGNORE}' not set)
	 * -> ABC1_D1EF_G2_H_I</li>
	 * </ul>
	 */
	public static final String RULE_TRF_CAMEL_CASE_TO_UPPER_CASE = "rule-trf-camelcase-to-uppercase";

	/**
	 * Extends the behavior of {@value #RULE_TRF_CAMEL_CASE_TO_UPPER_CASE} so
	 * that the names of enums (i.e. properties of enumerations) are modified as
	 * well.
	 */
	public static final String RULE_TRF_CAMEL_CASE_TO_UPPER_CASE_INCLUDE_ENUMS = "rule-trf-camelcase-to-uppercase-include-enums";

	/**
	 * Extends the behavior of {@value #RULE_TRF_CAMEL_CASE_TO_UPPER_CASE} so
	 * that the names of codes (i.e. properties of codelists) are modified as
	 * well.
	 */
	public static final String RULE_TRF_CAMEL_CASE_TO_UPPER_CASE_INCLUDE_CODES = "rule-trf-camelcase-to-uppercase-include-codes";

	/* -------------------- */
	/* --- other fields --- */
	/* -------------------- */

	/**
	 * Default suffix used for adding a suffix to model element names, in case
	 * that the configuration parameter {@value #PARAM_SUFFIX} is not used.
	 */
	public static final String DEFAULT_SUFFIX = "_";

	private Options options = null;
	private ShapeChangeResult result = null;

	public NamingModifier() {
		// nothing special to do here
	}

	/**
	 * @see de.interactive_instruments.ShapeChange.Transformation.Transformer#process(de.interactive_instruments.ShapeChange.Model.Generic.GenericModel,
	 *      de.interactive_instruments.ShapeChange.Options,
	 *      de.interactive_instruments.ShapeChange.TransformerConfiguration,
	 *      de.interactive_instruments.ShapeChange.ShapeChangeResult)
	 */
	public void process(GenericModel genModel, Options options,
			TransformerConfiguration trfConfig, ShapeChangeResult result)
			throws ShapeChangeAbortException {

		this.options = options;
		this.result = result;

		Map<String, ProcessRuleSet> ruleSets = trfConfig.getRuleSets();

		// get the set of all rules defined for the transformation
		Set<String> rules = new HashSet<String>();
		if (!ruleSets.isEmpty()) {
			for (ProcessRuleSet ruleSet : ruleSets.values()) {
				if (ruleSet.getAdditionalRules() != null) {
					rules.addAll(ruleSet.getAdditionalRules());
				}
			}
		}

		/*
		 * because there are no mandatory - in other words default - rules for
		 * this transformer simply return the model if no rules are defined in
		 * the rule sets (which the schema allows)
		 */
		if (rules.isEmpty())
			return;

		// apply pre-processing (nothing to do right now)

		// execute rules

		if (rules.contains(RULE_TRF_ADD_SUFFIX)) {
			applyRuleAddSuffix(genModel, trfConfig);
		}

		if (rules.contains(RULE_TRF_CAMEL_CASE_TO_UPPER_CASE)) {
			applyRuleCamelCaseToUpperCase(genModel, trfConfig);
		}

		// apply post-processing (nothing to do right now)

	}

	private void applyRuleCamelCaseToUpperCase(GenericModel genModel,
			TransformerConfiguration trfConfig) {

		SortedSet<String> suffixesToIgnore = new TreeSet<String>();

		/* --- determine and validate parameter values --- */
		String suffixToIgnore = trfConfig
				.getParameterValue(PARAM_SUFFIXES_TO_IGNORE);

		if (suffixToIgnore != null) {

			String[] split = suffixToIgnore.split(",");

			for (String s1 : split) {
				String s2 = s1.trim();
				if (!s2.isEmpty()) {
					suffixesToIgnore.add(s2);
				}
			}
		}

		/*
		 * --- update names of properties ---
		 * 
		 * NOTE: we update properties first so that messages (e.g. warnings)
		 * that refer to the class name use the original class name
		 */
		for (GenericPropertyInfo genPi : genModel.selectedSchemaProperties()) {

			if ((genPi.inClass().category() == Options.ENUMERATION
					&& !trfConfig.getAllRules().contains(
							RULE_TRF_CAMEL_CASE_TO_UPPER_CASE_INCLUDE_ENUMS))
					|| (genPi.inClass().category() == Options.CODELIST
							&& !trfConfig.getAllRules().contains(
									RULE_TRF_CAMEL_CASE_TO_UPPER_CASE_INCLUDE_CODES))) {
				continue;
			}

			String newName = camelCaseToUpperCaseName(genPi, suffixesToIgnore);

			genPi.setName(newName);
		}

		/* --- update names of classes --- */
		for (GenericClassInfo genCi : genModel.selectedSchemaClasses()) {

			String newName = camelCaseToUpperCaseName(genCi, suffixesToIgnore);

			genModel.updateClassName(genCi, newName);
		}
	}

	private String camelCaseToUpperCaseName(Info modelElement,
			SortedSet<String> suffixesToIgnore) {

		String name = modelElement.name();

		StringBuffer newName = new StringBuffer();
		String identifiedSuffix = null;
		boolean multipleSuffixesMatch = false;

		/*
		 * detect suffix match - watch out for multiple matches and choose
		 * longest one
		 */
		for (String suffix : suffixesToIgnore) {

			if (name.endsWith(suffix)) {

				// check if there are multiple matches
				if (identifiedSuffix != null) {

					multipleSuffixesMatch = true;

					// compare length to choose match with greater length
					if (suffix.length() > identifiedSuffix.length()) {
						identifiedSuffix = suffix;
					}

				} else {

					identifiedSuffix = suffix;
				}
			}
		}

		if (multipleSuffixesMatch) {
			MessageContext mc = result.addWarning(this, 4, identifiedSuffix);
			mc.addDetail(modelElement.fullName());
		}

		if (identifiedSuffix != null) {
			name = name.substring(0, name.lastIndexOf(identifiedSuffix));
		}

		if (!name.isEmpty()) {

			for (int i = 0; i < name.length(); i++) {

				int codePoint1 = name.codePointAt(i);
				int type1 = Character.getType(codePoint1);
				int codePoint2;
				boolean reachedEndOfName = false;

				if (i + 1 < name.length()) {

					codePoint2 = name.codePointAt(i + 1);

				} else if (identifiedSuffix != null) {

					codePoint2 = identifiedSuffix.codePointAt(0);

				} else {

					reachedEndOfName = true;
					codePoint2 = Integer.MIN_VALUE;

				}

				if (type1 == Character.LOWERCASE_LETTER
						|| type1 == Character.UPPERCASE_LETTER) {

					/*
					 * codePoint1 is a relevant letter, add uppercased value to
					 * newName
					 */
					newName.append((char) Character.toUpperCase(codePoint1));

				} else if (name.charAt(i) == '\u005F') {

					/*
					 * codePoint1 is an underscore, add two underscores to
					 * newName
					 */
					newName.append("__");

				} else if (type1 == Character.DECIMAL_DIGIT_NUMBER) {

					/*
					 * codePoint1 is a number [0-9], add it to newName
					 */
					newName.append((char) codePoint1);
				}

				if (!reachedEndOfName) {

					int type2 = Character.getType(codePoint2);

					if ((type1 == Character.LOWERCASE_LETTER
							|| type1 == Character.UPPERCASE_LETTER
							|| type1 == Character.DECIMAL_DIGIT_NUMBER)
							&& type2 == Character.UPPERCASE_LETTER) {

						/*
						 * codePoint1 is a relevant letter, and codePoint2 is an
						 * uppercase letter: add an underscore to newName
						 */
						newName.append("_");
					}
				}
			}
		}

		// finally, add the suffix (if one has been identified) to newName
		if (identifiedSuffix != null) {
			newName = newName.append(identifiedSuffix);
		}

		return newName.toString();
	}

	/**
	 * Adds the suffix (given by configuration parameter {@value #PARAM_SUFFIX},
	 * or otherwise using the default: {@value #DEFAULT_SUFFIX}) to the name of
	 * all class info objects that match the regular expression given via the
	 * configuration parameter {@value #PARAM_SUFFIX_REGEX}.
	 * 
	 * @param genModel
	 * @param trfConfig
	 */
	private void applyRuleAddSuffix(GenericModel genModel,
			TransformerConfiguration trfConfig) {

		/* --- determine and validate parameter values --- */
		String suffix = trfConfig.getParameterValue(PARAM_SUFFIX);

		if (suffix != null) {

			suffix = suffix.trim();

			if (suffix.length() == 0) {
				MessageContext mc = result.addError(this, 1, PARAM_SUFFIX,
						RULE_TRF_ADD_SUFFIX);
				mc.addDetail(this, 0);
				return;
			}

		} else {
			// use the default suffix
			suffix = DEFAULT_SUFFIX;
		}

		String suffixRegex = trfConfig.getParameterValue(PARAM_SUFFIX_REGEX);

		if (suffixRegex != null) {

			suffixRegex = suffixRegex.trim();

			if (suffixRegex.length() == 0) {
				// the suffix regular expression is required but was not
				// provided
				MessageContext mc = result.addError(this, 1, PARAM_SUFFIX_REGEX,
						RULE_TRF_ADD_SUFFIX);
				mc.addDetail(this, 0);
				return;
			}
		} else {
			// the suffix regular expression is required but was not provided
			MessageContext mc = result.addError(this, 2, PARAM_SUFFIX_REGEX,
					RULE_TRF_ADD_SUFFIX);
			mc.addDetail(this, 0);
			return;
		}

		Pattern suffixPattern = null;

		try {

			suffixPattern = Pattern.compile(suffixRegex);

		} catch (PatternSyntaxException e) {

			MessageContext mc = result.addError(this, 3, PARAM_SUFFIX_REGEX,
					RULE_TRF_ADD_SUFFIX, suffixRegex, e.getMessage());
			mc.addDetail(this, 0);
			return;
		}

		/* --- add suffix --- */
		for (GenericClassInfo genCi : genModel.selectedSchemaClasses()) {

			Matcher matcher = suffixPattern.matcher(genCi.name());

			if (matcher.matches()) {

				String newName = genCi.name() + suffix;

				genModel.updateClassName(genCi, newName);
			}
		}
		
		// TODO document that rule applies to properties as well
		for (GenericPropertyInfo genPi : genModel.selectedSchemaProperties()) {

			Matcher matcher = suffixPattern.matcher(genPi.name());

			if (matcher.matches()) {

				String newName = genPi.name() + suffix;

				genPi.setName(newName);
			}
		}
	}

	public String message(int mnr) {

		switch (mnr) {
		case 0:
			return "Context: class NamingModifier";
		case 1:
			return "No non-empty string value provided for configuration parameter '$1$'. Execution of rule '$2$' aborted.";
		case 2:
			return "Configuration parameter '$1$' required for execution of rule '$2$' was not provided. Execution of rule '$2$' aborted.";
		case 3:
			return "Syntax exception for regular expression value of configuration parameter '$1$' (required for execution of rule '$2$'). Regular expression value was: $3$. Exception message: $4$. Execution of rule '$2$' aborted.";
		case 4:
			return "Multiple suffixes (identified by configuration parameter '"
					+ PARAM_SUFFIXES_TO_IGNORE
					+ "' match the end of the model element name. Suffix '$1$' was chosen.";
		default:
			return "(" + NamingModifier.class.getName()
					+ ") Unknown message with number: " + mnr;
		}
	}
}
