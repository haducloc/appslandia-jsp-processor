// The MIT License (MIT)
// Copyright © 2015 AppsLandia. All rights reserved.

// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:

// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.

// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.appslandia.jspprocessor.impl;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.appslandia.jspprocessor.utils.AssertUtils;
import com.appslandia.jspprocessor.utils.FileNameUtils;
import com.appslandia.jspprocessor.utils.Jdk8FileUtils;
import com.appslandia.jspprocessor.utils.StringUtils;
import com.appslandia.jspprocessor.utils.ValueUtils;

/**
 *
 * @author <a href="mailto:haducloc13@gmail.com">Loc Ha</a>
 *
 */
public class JspProcessor {

	final File appDir;
	final String configDirName = "__config";

	private String jspDir = "/WEB-INF/__jsp";
	private boolean minimize;
	private String genDirName = "jsp";

	final boolean session = false;
	final boolean trimDirectiveWhitespaces = true;
	private Charset charset;

	public JspProcessor(String appDir) {
		AssertUtils.assertNotNull(appDir);
		this.appDir = new File(appDir);
		AssertUtils.assertTrue(this.appDir.exists() && this.appDir.isDirectory(), "appDir is invalid.");
	}

	public JspProcessor minimize(boolean minimize) {
		this.minimize = minimize;
		return this;
	}

	public JspProcessor pageEncoding(String pageEncoding) {
		pageEncoding = StringUtils.trimToNull(pageEncoding);
		if (pageEncoding != null) {
			this.charset = Charset.forName(pageEncoding);
		}
		return this;
	}

	public JspProcessor jspDir(String jspDir) {
		this.jspDir = StringUtils.trimToNull(jspDir);
		return this;
	}

	public JspProcessor genDirName(String genDirName) {
		this.genDirName = StringUtils.trimToNull(genDirName);
		return this;
	}

	public void process() throws Exception {
		AssertUtils.assertNotNull(this.jspDir);
		AssertUtils.assertNotNull(this.genDirName);

		Queue<File> q = new LinkedList<>();
		q.add(this.appDir);

		while (!q.isEmpty()) {
			File file = q.remove();
			if (file.toPath().toUri().toURL().getPath().endsWith(this.jspDir + "/")) {

				Path jspPath = file.toPath();
				Path configPath = jspPath.resolve(this.configDirName);
				Path genPath = jspPath.getParent().resolve(this.genDirName);

				if (genPath.toFile().exists()) {
					Jdk8FileUtils.deleteRecursively(genPath);
				}
				processJspDir(jspPath, configPath, genPath);
			} else {
				Arrays.stream(file.listFiles()).filter(f -> f.isDirectory()).forEach(f -> q.add(f));
			}
		}
	}

	void processJspDir(Path jspPath, Path configPath, Path genPath) throws Exception {
		Charset cs = ValueUtils.valueOrAlt(this.charset, StandardCharsets.UTF_8);
		Queue<File> q = new LinkedList<>();
		q.add(jspPath.toFile());

		while (!q.isEmpty()) {
			File file = q.remove();
			if (file.equals(configPath.toFile()) || file.equals(genPath.toFile())) {
				continue;
			}
			if (file.isDirectory()) {
				Arrays.stream(file.listFiles()).forEach(f -> q.add(f));
				continue;
			}
			if (!file.isFile()) {
				continue;
			}
			Path targetFilePath = genPath.resolve(jspPath.relativize(file.toPath()));
			Files.createDirectories(targetFilePath.getParent());

			// JSP file?
			if (file.getName().toLowerCase(Locale.ENGLISH).endsWith(".jsp")) {

				// JSP model
				JspModel model = new JspModel();
				model.jspName = file.getName();
				model.jspSource = loadSource(file.toPath(), cs, false);

				Map<String, String> jspVariables = new HashMap<>();
				parseVariables(model.jspName, model.jspSource, jspVariables, configPath);
				parseSections(model);

				// Layout source
				String layoutName = getLayoutName(model.jspName, jspVariables);
				if (layoutName != null) {
					model.layoutJspName = layoutName + ".jsp";
					model.layoutSource = loadSource(configPath.resolve(model.layoutJspName), cs, true);
					model.includeJspName = FileNameUtils.insertExtra(model.jspName, "_inc");

					parseVariables(model.layoutJspName, model.layoutSource, model.mergedVariables, configPath);
				}

				// Replace sections
				if (layoutName != null) {
					replaceSections(model);
				}

				// Replace variables
				jspVariables.entrySet().stream().forEach(e -> {
					model.mergedVariables.put(e.getKey(), e.getValue());
				});
				if (layoutName != null) {
					replaceVariables(model.layoutSource, model.mergedVariables);
				}
				replaceVariables(model.jspSource, model.mergedVariables);

				// Minimize sources?
				if (this.minimize) {
					if (layoutName != null) {
						minimizeSource(model.layoutSource);
					}
					minimizeSource(model.jspSource);
				}

				// Replace directives
				if (layoutName != null) {
					replacePageDirectives(model.layoutSource);
				}
				replacePageDirectives(model.jspSource);

				// Save sources
				if (layoutName != null) {
					Path bodyFilePath = targetFilePath.getParent().resolve(model.includeJspName);
					try (BufferedWriter out = Files.newBufferedWriter(bodyFilePath, cs)) {
						saveSource(model.jspSource, out);
					}
					try (BufferedWriter out = Files.newBufferedWriter(targetFilePath, cs)) {
						saveSource(model.layoutSource, out);
					}
				} else {
					// No layout
					try (BufferedWriter out = Files.newBufferedWriter(targetFilePath, cs)) {
						saveSource(model.jspSource, out);
					}
				}
			} else {
				// Not JSP file -> Copy directly
				Files.copy(file.toPath(), targetFilePath, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	// session="false" trimDirectiveWhitespaces="true"

	final Pattern startPageDirPattern = Pattern.compile("\\s*<%@\\s*page.*");
	final Pattern endDirPattern = Pattern.compile(".*%>\\s*");
	final Pattern blankPageDirPattern = Pattern.compile("\\s*<%@\\s*page\\s*%>\\s*");

	final Pattern sessionAttrPattern = Pattern.compile("session\\s*=\\s*\"\\s*(true|false)\\s*\"");
	final Pattern trimDirectiveWhitespacesAttrPattern = Pattern.compile("trimDirectiveWhitespaces\\s*=\\s*\"\\s*(true|false)\\s*\"");
	final Pattern pageEncodingAttrPattern = Pattern.compile("pageEncoding\\s*=\\s*\"\\s*[a-zA-Z\\d-]+\\s*\"");

	void replacePageDirectives(List<String> source) {
		int start = -1;
		boolean hasDirectives = false;
		while (true) {
			while ((++start < source.size()) && !this.startPageDirPattern.matcher(source.get(start)).matches()) {
			}
			if (start == source.size()) {
				break;
			}
			int end = start;
			while ((end < source.size()) && !this.endDirPattern.matcher(source.get(end)).matches()) {
				end++;
			}
			if (end == source.size()) {
				break;
			}

			String pageDirective = toDirectiveSource(source, start, end);
			if (!hasDirectives) {
				Matcher matcher = null;

				// session
				if (!this.session) {
					matcher = this.sessionAttrPattern.matcher(pageDirective);
					if (matcher.find()) {
						pageDirective = matcher.replaceAll("session=\"false\"");
					} else {
						pageDirective = addDirectiveAttribute(pageDirective, " session=\"false\"");
					}
				}

				// trimDirectiveWhitespaces
				if (this.trimDirectiveWhitespaces) {
					matcher = this.trimDirectiveWhitespacesAttrPattern.matcher(pageDirective);
					if (matcher.find()) {
						pageDirective = matcher.replaceAll("trimDirectiveWhitespaces=\"true\"");
					} else {
						pageDirective = addDirectiveAttribute(pageDirective, " trimDirectiveWhitespaces=\"true\"");
					}
				}

				// pageEncoding
				if (this.charset != null) {
					matcher = this.pageEncodingAttrPattern.matcher(pageDirective);
					if (matcher.find()) {
						pageDirective = matcher.replaceAll("pageEncoding=\"" + this.charset.name() + "\"");
					} else {
						pageDirective = addDirectiveAttribute(pageDirective, " pageEncoding=\"" + this.charset.name() + "\"");
					}
				}

				hasDirectives = true;
			} else {
				pageDirective = this.sessionAttrPattern.matcher(pageDirective).replaceAll("");
				pageDirective = this.trimDirectiveWhitespacesAttrPattern.matcher(pageDirective).replaceAll("");

				if (this.charset != null) {
					pageDirective = this.pageEncodingAttrPattern.matcher(pageDirective).replaceAll("");
				}
			}
			removeSubSource(source, start, end);

			if (!this.blankPageDirPattern.matcher(pageDirective).matches()) {
				pageDirective = pageDirective.replaceAll("\\s{2,}", " ");
				source.add(start, pageDirective);
			} else {
				source.add(start, "<!-- @page removed -->");
			}
		}
		if (!hasDirectives) {
			if (this.charset == null) {
				source.add(0, "<%@ page session=\"false\" trimDirectiveWhitespaces=\"true\"%>");
			} else {
				source.add(0, "<%@ page session=\"false\" trimDirectiveWhitespaces=\"true\" pageEncoding=\"" + this.charset.name() + "\"%>");
			}
		}
	}

	// <!-- @doBody -->
	// <!-- @someSection? -->

	final Pattern doBodyPattern = Pattern.compile("\\s*<!--\\s*@doBody\\s*-->\\s*", Pattern.CASE_INSENSITIVE);
	final Pattern sectionPattern = Pattern.compile("\\s*<!--\\s*@[^\\s]+(\\?)?\\s*-->\\s*", Pattern.CASE_INSENSITIVE);

	void replaceSections(JspModel model) {
		// doBody
		boolean doBody = false;
		while (true) {
			int pos = -1;
			while ((++pos < model.layoutSource.size()) && !this.doBodyPattern.matcher(model.layoutSource.get(pos)).matches()) {
			}
			if (pos == model.layoutSource.size()) {
				break;
			}
			if (doBody) {
				throw new IllegalArgumentException("@doBody is duplicated (layout=" + model.layoutJspName + ")");
			}

			String bodyLine = model.layoutSource.get(pos);
			String indents = copyIndents(bodyLine);

			model.layoutSource.set(pos, indents + "<!-- @doBody processed -->");
			model.layoutSource.add(pos + 1, indents + "<%@ include file=\"" + model.includeJspName + "\" %>");

			doBody = true;
		}
		if (!doBody) {
			throw new IllegalArgumentException("@doBody is required (layout=" + model.layoutJspName + ")");
		}

		// Sections
		while (true) {
			int pos = -1;
			while ((++pos < model.layoutSource.size()) && !this.sectionPattern.matcher(model.layoutSource.get(pos)).matches()) {
			}
			if (pos == model.layoutSource.size()) {
				break;
			}

			String sectionLine = model.layoutSource.get(pos);
			String sectionName = sectionLine.substring(sectionLine.indexOf("@") + 1, sectionLine.indexOf("-->")).trim();

			boolean sectionRequired = true;
			if (sectionName.endsWith("?")) {
				sectionName = sectionName.substring(0, sectionName.length() - 1);
				sectionRequired = false;
			}

			List<String> sectionSource = model.sections.get(sectionName);
			String indents = copyIndents(sectionLine);

			if (sectionSource != null) {
				sectionSource.add(0, indents + "<!-- @" + sectionName + " begin -->");
				sectionSource.add(indents + "<!-- @" + sectionName + " end -->");

				model.layoutSource.remove(pos);
				model.layoutSource.addAll(pos, sectionSource);
			} else {
				if (sectionRequired) {
					throw new IllegalArgumentException("@" + sectionName + " is required (jsp=" + model.jspName + ")");
				} else {
					model.layoutSource.set(pos, indents + "<!-- @" + sectionName + "? undefined -->");
				}
			}
		}
	}

	// <!-- @variables:fileLocation -->
	final Pattern varFilePattern = Pattern.compile("\\s*<!--\\s*@variables\\s*:.*-->\\s*", Pattern.CASE_INSENSITIVE);

	// <!-- @variable key=value -->
	final Pattern varPattern = Pattern.compile("\\s*<!--\\s*@variable\\s+[^\\s=]+\\s*=.*-->\\s*", Pattern.CASE_INSENSITIVE);

	// <!-- @variables
	// title=expression
	// __layout=layout
	// -->

	final Pattern varStartPattern = Pattern.compile("\\s*<!--\\s*@variables\\s*", Pattern.CASE_INSENSITIVE);
	final Pattern varEndPattern = Pattern.compile("\\s*-->\\s*");
	final Pattern varNameValPattern = Pattern.compile("[^\\s=]+\\s*=.*", Pattern.CASE_INSENSITIVE);

	void parseVariables(String jspName, List<String> source, Map<String, String> variables, Path configPath) throws Exception {

		// @variables:fileLocation
		while (true) {
			int pos = -1;
			while ((++pos < source.size()) && !this.varFilePattern.matcher(source.get(pos)).matches()) {
			}
			if (pos == source.size()) {
				break;
			}

			String varFileLine = source.get(pos);
			int varIdx = varFileLine.indexOf(":");

			String fileLocation = varFileLine.substring(varIdx + 1, varFileLine.indexOf("-->", varIdx)).trim();
			if (fileLocation.isEmpty()) {
				throw new IllegalArgumentException("@variables: is invalid (jsp=" + jspName + ")");
			}

			source.set(pos, copyIndents(varFileLine) + "<!-- " + fileLocation + " processed -->");

			// Import
			Properties props = new Properties();
			try (Reader r = Files.newBufferedReader(configPath.resolve(fileLocation), StandardCharsets.UTF_8)) {
				props.load(r);
			}
			props.forEach((k, v) -> variables.put((String) k, (String) v));
		}

		// @variable key=value
		boolean outVariable = false;
		while (true) {
			int pos = -1;
			while ((++pos < source.size()) && !this.varPattern.matcher(source.get(pos)).matches()) {
			}
			if (pos == source.size()) {
				break;
			}

			String varLine = source.get(pos);
			int varIdx = varLine.indexOf("@variable");
			String nameVal = varLine.substring(varIdx + "@variable".length(), varLine.indexOf("-->", varIdx)).trim();

			int idx = nameVal.indexOf('=');
			String key = nameVal.substring(0, idx).trim();
			variables.put(key, StringUtils.trimToEmpty(nameVal.substring(idx + 1)));

			if (!outVariable) {
				outVariable = true;
				source.set(pos, copyIndents(varLine) + "<!-- @variable(s) processed -->");
			} else {
				source.remove(pos);
			}
		}

		// @variables
		while (true) {
			int start = -1;
			while ((++start < source.size()) && !this.varStartPattern.matcher(source.get(start)).matches()) {
			}
			if (start == source.size()) {
				break;
			}
			int end = start;
			while ((++end < source.size()) && !this.varEndPattern.matcher(source.get(end)).matches()) {
			}

			String variablesLine = source.get(start);
			if (end == source.size()) {
				throw new IllegalArgumentException("@variables must have a closing directive (jsp=" + jspName + ")");
			}

			// variables: start-end
			for (int i = start + 1; i < end; i++) {
				String nameVal = source.get(i).trim();
				if ((nameVal.isEmpty()) || nameVal.startsWith("//")) {
					continue;
				}
				if (!this.varNameValPattern.matcher(nameVal).matches()) {
					throw new IllegalArgumentException("Variable is invalid (name/value=" + nameVal + ", jsp=" + jspName + ")");
				}
				int idx = nameVal.indexOf('=');
				variables.put(nameVal.substring(0, idx).trim(), StringUtils.trimToEmpty(nameVal.substring(idx + 1)));
			}

			removeSubSource(source, start, end);
			source.add(start, copyIndents(variablesLine) + "<!-- @variables processed -->");
		}
	}

	// <!-- @someSection begin -->
	// HTML/JSP
	// <!-- @someSection end -->

	final Pattern sectionStartPattern = Pattern.compile("\\s*<!--\\s*@[^\\s]+\\s+begin\\s*-->\\s*", Pattern.CASE_INSENSITIVE);
	final Pattern sectionEndPattern = Pattern.compile("\\s*<!--\\s*@[^\\s]+\\s+end\\s*-->\\s*", Pattern.CASE_INSENSITIVE);

	void parseSections(JspModel model) {
		while (true) {
			int start = -1;
			while ((++start < model.jspSource.size()) && !this.sectionStartPattern.matcher(model.jspSource.get(start)).matches()) {
			}
			if (start == model.jspSource.size()) {
				break;
			}

			String sectionLine = model.jspSource.get(start);
			int idx = sectionLine.indexOf("@");
			String sectionName = sectionLine.substring(idx + 1, sectionLine.indexOf(' ', idx)).trim();

			int end = start;
			boolean hasClosing = true;
			while ((++end < model.jspSource.size()) && !this.sectionEndPattern.matcher(model.jspSource.get(end)).matches()) {
				if (this.sectionStartPattern.matcher(model.jspSource.get(end)).matches()) {
					hasClosing = false;
					break;
				}
			}
			if (!hasClosing || (end == model.jspSource.size())) {
				throw new IllegalArgumentException("@" + sectionName + " must have a closing directive (jsp=" + model.jspName + ")");
			}
			if (model.sections.containsKey(sectionName)) {
				throw new IllegalArgumentException("@" + sectionName + " is duplicated (jsp=" + model.jspName + ")");
			}

			if (end - start > 1) {
				model.sections.put(sectionName, copySubSource(model.jspSource, start + 1, end - 1));
			} else {
				model.sections.put(sectionName, new ArrayList<>());
			}

			removeSubSource(model.jspSource, start, end);
			model.jspSource.add(start, copyIndents(sectionLine) + "<!-- @" + sectionName + " removed -->");
		}
	}

	final Map<String, List<String>> sourceCache = new HashMap<String, List<String>>();

	List<String> loadSource(Path sourcePath, Charset cs, boolean cacheSource) throws Exception {
		if (!cacheSource) {
			return Files.readAllLines(sourcePath, cs);

		} else {
			// Cache?
			return new ArrayList<String>(sourceCache.computeIfAbsent(sourcePath.getFileName().toString(), k -> {
				try {
					return Files.readAllLines(sourcePath, cs);
				} catch (IOException ex) {
					throw new UncheckedIOException(ex);
				}
			}));
		}
	}

	static void replaceVariables(List<String> source, Map<String, String> variables) {
		for (int i = 0; i < source.size(); i++) {
			String line = source.get(i);
			for (Entry<String, String> entry : variables.entrySet()) {
				String holder = "@\\{\\s*" + Pattern.quote(entry.getKey()) + "\\s*}";
				line = Pattern.compile(holder, Pattern.CASE_INSENSITIVE).matcher(line).replaceAll(Matcher.quoteReplacement(entry.getValue()));

				holder = "@\\(\\s*" + Pattern.quote(entry.getKey()) + "\\s*\\)";
				line = Pattern.compile(holder, Pattern.CASE_INSENSITIVE).matcher(line).replaceAll(Matcher.quoteReplacement(entry.getValue()));
			}
			source.set(i, line);
		}
	}

	static String copyIndents(String source) {
		int end = -1;
		while ((++end < source.length()) && Character.isWhitespace(source.charAt(end))) {
		}
		return source.substring(0, end);
	}

	static List<String> copySubSource(List<String> source, int start, int end) {
		List<String> list = new ArrayList<>();
		for (int i = start; i <= end; i++) {
			list.add(source.get(i));
		}
		return list;
	}

	static void removeSubSource(List<String> source, int start, int end) {
		for (int i = end; i >= start; i--) {
			source.remove(i);
		}
	}

	final Pattern blankLinePattern = Pattern.compile("\\s*");

	void minimizeSource(List<String> source) {
		for (int i = source.size() - 1; i >= 0; i--) {
			if (this.blankLinePattern.matcher(source.get(i)).matches()) {
				source.remove(i);
			}
		}
	}

	static void saveSource(List<String> source, BufferedWriter out) throws Exception {
		for (int i = 0; i < source.size(); i++) {
			if (i > 0) {
				out.newLine();
			}
			out.write(source.get(i));
		}
	}

	static String toDirectiveSource(List<String> source, int start, int end) {
		StringBuilder sb = new StringBuilder();
		for (int i = start; i <= end; i++) {
			if (sb.length() > 0)
				sb.append(" ");
			sb.append(source.get(i));
		}
		return sb.toString();
	}

	static String addDirectiveAttribute(String directive, String attr) {
		int idx = directive.lastIndexOf("%>");
		return directive.substring(0, idx) + attr + "%>";
	}

	static String getLayoutName(String jspName, Map<String, String> variables) {
		String layoutName = variables.get("__layout");
		if (layoutName == null) {
			return null;
		}
		if (layoutName.isEmpty()) {
			throw new IllegalArgumentException("__layout is required (jsp=" + jspName + ")");
		}
		return layoutName;
	}

	static class JspModel {
		String jspName;
		List<String> jspSource;

		final Map<String, String> mergedVariables = new HashMap<>();
		final Map<String, List<String>> sections = new HashMap<>();

		// If layout
		String layoutJspName;
		List<String> layoutSource;
		String includeJspName;
	}
}
