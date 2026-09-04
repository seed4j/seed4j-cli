package com.seed4j.cli.bootstrap.infrastructure.primary;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

final class ChildFirstRuntimeExtensionResourceClassLoader extends URLClassLoader {

  private static final String FIXTURE_CLASS_PREFIX = "com.mycompany.seed4j.extension.runtime.";
  private static final String CLASS_ANCHORED_PROJECT_FILES_READER = "com.seed4j.module.infrastructure.secondary.FileSystemProjectFiles";
  private static final String FIXTURE_RESOURCE_PREFIX = "com/mycompany/seed4j/extension/runtime/";
  private static final String GENERATOR_RESOURCE_PREFIX = "generator/";

  ChildFirstRuntimeExtensionResourceClassLoader(URL[] urls, ClassLoader parent) {
    super(urls, parent);
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
      Class<?> loadedClass = findLoadedClass(name);
      if (loadedClass == null) {
        loadedClass = loadClassFromExtensionOrParent(name);
      }
      if (resolve) {
        resolveClass(loadedClass);
      }
      return loadedClass;
    }
  }

  private Class<?> loadClassFromExtensionOrParent(String className) throws ClassNotFoundException {
    if (childFirstClass(className)) {
      return findClass(className);
    }

    return super.loadClass(className, false);
  }

  private boolean childFirstClass(String className) {
    return extensionFixtureClass(className) || classAnchoredResourceReader(className);
  }

  private boolean extensionFixtureClass(String className) {
    return className.startsWith(FIXTURE_CLASS_PREFIX);
  }

  private boolean classAnchoredResourceReader(String className) {
    return CLASS_ANCHORED_PROJECT_FILES_READER.equals(className);
  }

  @Override
  public URL getResource(String name) {
    if (extensionResourceLookup(name)) {
      URL extensionResource = findResource(name);
      if (extensionResource != null) {
        return extensionResource;
      }
    }

    return super.getResource(name);
  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    if (!extensionResourceLookup(name)) {
      return super.getResources(name);
    }

    List<URL> resources = new ArrayList<>();
    Enumeration<URL> extensionResources = findResources(name);
    while (extensionResources.hasMoreElements()) {
      resources.add(extensionResources.nextElement());
    }
    Enumeration<URL> parentResources = super.getResources(name);
    while (parentResources.hasMoreElements()) {
      resources.add(parentResources.nextElement());
    }
    return Collections.enumeration(resources);
  }

  private boolean extensionResourceLookup(String name) {
    return name.startsWith(FIXTURE_RESOURCE_PREFIX) || name.startsWith(GENERATOR_RESOURCE_PREFIX);
  }
}
