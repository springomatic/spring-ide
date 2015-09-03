/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.cloudfoundry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.springframework.boot.loader.tools.JarWriter;
import org.springframework.boot.loader.tools.Libraries;
import org.springframework.boot.loader.tools.Library;
import org.springframework.boot.loader.tools.LibraryCallback;
import org.springframework.boot.loader.tools.LibraryScope;
import org.springframework.boot.loader.tools.Repackager;
import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.ops.CloudApplicationArchiverStrategy;
import org.springframework.ide.eclipse.boot.dash.model.UserInteractions;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.util.FileUtil;
import org.springframework.ide.eclipse.boot.util.JavaProjectUtil;
import org.springsource.ide.eclipse.commons.frameworks.core.maintype.MainTypeFinder;

public class CloudApplicationArchiverStrategyAsJar implements CloudApplicationArchiverStrategy {

	private static final String TEMP_FOLDER_NAME = "springidetempFolderForJavaAppJar";

	/**
	 * Classpath entries spilt into two lists, one that correspond to the current project's output folders
	 * and all the others (which correspond to the project's dependencies). The dependencies could be
	 * jars or output folders for other projects in th workspace.
	 */
	private static class SplitClasspath {
		private List<File> projectContents = new ArrayList<File>(2); //one or two is typical
		private List<File> dependencies = new ArrayList<File>();
		public SplitClasspath(IJavaProject jp, File[] entries) {
			Set<File> outputFolders = toFileSet(JavaProjectUtil.getOutputFolders(jp));
			for (File file : entries) {
				if (contains(outputFolders, file)) {
					projectContents.add(file);
				} else {
					dependencies.add(file);
				}
			}
		}

		private boolean contains(Set<File> outputFolders, File file) {
			return outputFolders.contains(canonical(file));
		}

		private File canonical(File file) {
			try {
				return file.getCanonicalFile();
			} catch (IOException e) {
				//Next best thing:
				return file.getAbsoluteFile();
			}
		}

		/**
		 * Convert a collectio of Eclipse IContainer to List of java.io.File. Containers that
		 * don't correspond to stuff on disk are silently ignored.
		 */
		private Set<File> toFileSet(List<IContainer> folders) {
			Set<File> files = new HashSet<File>();
			for (IContainer folder : folders) {
				IPath loc = folder.getLocation();
				if (loc!=null) {
					File file = loc.toFile();
					files.add(canonical(file)); //use canonical file to make equals / Set work as expected.
				}
			}
			return files;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder("SplitClasspath(\n");
			for (File file : projectContents) {
				builder.append("  "+file+"\n");
			}
			builder.append("   ------------\n");
			for (File file : dependencies) {
				builder.append("  "+file+"\n");
			}
			builder.append(")");
			return builder.toString();
		}
	}



	private static final File[] NO_FILES = new File[]{};

	private static class Archiver implements ICloudApplicationArchiver {

		private IJavaProject jp;
		private IType mainType;
		private ILaunchConfiguration conf;
		private BootLaunchConfigurationDelegate delegate;

		Archiver(IJavaProject jp, IType mainType) throws CoreException {
			this.jp = jp;
			this.mainType = mainType;
			this.conf = BootLaunchConfigurationDelegate.createWorkingCopy(mainType);
			this.delegate = new BootLaunchConfigurationDelegate();
		}

		private SplitClasspath getRuntimeClasspath() throws CoreException {
			return new SplitClasspath(jp, toFiles(delegate.getClasspath(conf)));
		}

		private File[] toFiles(String[] classpath) {
			if (classpath!=null) {
				File[] files = new File[classpath.length];
				for (int i = 0; i < files.length; i++) {
					files[i] = new File(classpath[i]);
				}
				return files;
			}
			return NO_FILES;
		}

		@Override
		public CloudZipApplicationArchive getApplicationArchive(IProgressMonitor mon) throws Exception {
			SplitClasspath classpath = getRuntimeClasspath();
			File tempFolder = FileUtil.getTempFolder(TEMP_FOLDER_NAME);
			File baseJar = new File(tempFolder, jp.getElementName()+".original.jar");
			File repackagedJar = new File(tempFolder, jp.getElementName()+".repackaged.jar");

			createBaseJar(classpath.projectContents, baseJar);
			repackage(baseJar, classpath.dependencies, repackagedJar);
			return new CloudZipApplicationArchive(new ZipFile(repackagedJar));
		}

		private void createBaseJar(List<File> projectContents, File baseJar) throws FileNotFoundException, IOException {
			JarWriter jarWriter = new JarWriter(baseJar);
			try {
				for (File outputFolder : projectContents) {
					writeFolder(jarWriter, outputFolder);
				}
			} finally {
				jarWriter.close();
			}
		}

		private void writeFolder(JarWriter jarWriter, File baseFolder) throws FileNotFoundException, IOException {
			for (String name : baseFolder.list()) {
				write(jarWriter, baseFolder, name);
			}
		}

		private void write(JarWriter jarWriter, File baseFolder, String relativePath) throws FileNotFoundException, IOException {
			debug("Writing: "+relativePath + " from "+baseFolder);
			File file = new File(baseFolder, relativePath);
			if (file.isDirectory()) {
				debug("Folder");
				for (String name : file.list()) {
					write(jarWriter, baseFolder, pathJoin(relativePath, name));
				}
			} else if (file.isFile()) {
				debug("File");
				jarWriter.writeEntry(relativePath, new FileInputStream(file));
			} else {
				debug("Huh?");
			}
		}

		private void debug(String string) {
			System.out.println(string);
		}

		private String pathJoin(String relativePath, String name) {
			return relativePath + "/" +name;
		}

		private void repackage(File baseJar, List<File> dependencies, File repackagedJar) throws IOException {
			Repackager repackager = new Repackager(baseJar);
			repackager.setMainClass(mainType.getFullyQualifiedName());
			repackager.repackage(repackagedJar, asLibraries(dependencies));
		}

		private Libraries asLibraries(final List<File> dependencies) {
			return new Libraries() {
				public void doWithLibraries(LibraryCallback callback) throws IOException {
					for (File dep : dependencies) {
						if (dep.isFile()) {
							callback.library(new Library(dep, LibraryScope.COMPILE));
						} else if (dep.isDirectory()) {
							//TODO: should jar up this thing, and add it like it was a library.
							throw new IllegalStateException("Packaging of non-jar dependencies not implemented");
						}
					}
				}
			};
		}



	}



	private IProject project;
	private UserInteractions ui;

	public CloudApplicationArchiverStrategyAsJar(IProject project, UserInteractions ui) {
		this.project = project;
		this.ui = ui;
	}

	@Override
	public ICloudApplicationArchiver getArchiver(IProgressMonitor mon) {
		try {
			final IJavaProject jp = getJavaProject();
			if (jp!=null) {
				final IType type = getMainType(jp, mon);
				if (type!=null) {
					return new Archiver(jp, type);
				}
			}
		} catch (Exception e) {
			BootDashActivator.log(e);
		}
		return null;
	}

	private IJavaProject getJavaProject() {
		try {
			if (project.isAccessible() && project.hasNature(JavaCore.NATURE_ID)) {
				return JavaCore.create(project);
			}
		} catch (Exception e) {
			BootDashActivator.log(e);
		}
		return null;
	}

	private IType getMainType(IJavaProject jp, IProgressMonitor mon) {
		try {
			IType[] candidates = MainTypeFinder.guessMainTypes(jp, mon);
			if (candidates!=null && candidates.length>0) {
				if (candidates.length==1) {
					return candidates[0];
				} else {
					return ui.chooseMainType(candidates, "Choose a main type", "Deploying a standalone boot-app requires "
							+ "that the main type is identified. We found several candidates, please choose one." );
				}
			}
		} catch (Exception e) {
			BootDashActivator.log(e);
		}
		return null;
	}

//	private CloudZipApplicationArchive getArchive(IJavaProject jp, IType type, IProgressMonitor monitor) {
//		File[] classpath = getRuntimeClasspath(jp);
//		BootLaunchConfigurationDelegate.createConf(type)
//		File baseJar = createBaseJar(monitor);
//		File libraries =
//		return repackage(baseJar, )
//
//		JavaPackageFragmentRootHandler rootResolver = getPackageFragmentRootHandler(getJavaProject(), monitor);
//
//		final IPackageFragmentRoot[] roots = rootResolver.getPackageFragmentRoots(monitor);
//
//		if (roots == null || roots.length == 0) {
//			throw BootDashActivator.asCoreException("Unable to package project" + javaProject.getElementName()
//					+ " as a jar application. Please verify that the project is a valid Java project and contains a main type in source.");
//		}
//
//		IType mainType = rootResolver.getMainType(monitor);
//
//		JarPackageData jarPackageData = getJarPackageData(roots, mainType, monitor);
//
//		// generate a manifest file. Note that manifest files
//		// are only generated in the temporary jar meant for
//		// deployment.
//		// The associated Java project is no modified.
//		jarPackageData.setGenerateManifest(true);
//
//		// This ensures that folders in output folders appear at root
//		// level
//		// Example: src/main/resources, which is in the project's
//		// classpath, contains non-Java templates folder and
//		// has output folder target/classes. If not exporting output
//		// folder,
//		// templates will be packaged in the jar using this path:
//		// resources/templates
//		// This may cause problems with the application's dependencies
//		// if they are looking for just /templates at top level of the
//		// jar
//		// If exporting output folders, templates folder will be
//		// packaged at top level in the jar.
//		jarPackageData.setExportOutputFolders(true);
//
//		packagedFile = packageApplication(jarPackageData, monitor);
//
//		bootRepackage(roots, packagedFile);
//
//		return new CloudZipApplicationArchive(new ZipFile(packagedFile));
//	}

}
