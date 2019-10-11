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

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 *
 * @author <a href="mailto:haducloc13@gmail.com">Loc Ha</a>
 *
 */
@Mojo(name = "process", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class JspProcessorMojo extends AbstractMojo {

	@Parameter(property = "skip", defaultValue = "false")
	private boolean skip;

	@Parameter(property = "minimize", defaultValue = "false")
	private boolean minimize;

	@Parameter(property = "jspDir", defaultValue = "/WEB-INF/__jsp")
	protected String jspDir;

	@Parameter(property = "genDirName", defaultValue = "jsp")
	protected String genDirName;

	@Parameter(property = "pageEncoding")
	protected String pageEncoding;

	@Parameter(property = "webContentDir", defaultValue = "${project.basedir}/WebContent")
	protected File webContentDir;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		getLog().info("Invoking com.appslandia.jspprocessor.impl.JspProcessorMojo.execute()");

		if (this.skip) {
			getLog().info("Skip flag is on, will skip goal.");
			return;
		}

		getLog().info("webContentDir: " + this.webContentDir.getAbsolutePath());
		getLog().info("jspDir: " + this.jspDir);
		getLog().info("genDirName: " + this.genDirName);
		getLog().info("pageEncoding: " + this.pageEncoding);
		getLog().info("minimize: " + this.minimize);

		try {
			new JspProcessor(this.webContentDir.getAbsolutePath()).minimize(this.minimize).jspDir(this.jspDir).genDirName(this.genDirName).pageEncoding(this.pageEncoding)
					.process();

		} catch (Exception ex) {
			throw new MojoExecutionException(ex.getMessage(), ex);
		}

		getLog().info("Done com.appslandia.jspprocessor.impl.JspProcessorMojo.execute()");
	}
}
