/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.log4j.nt;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;
import org.apache.log4j.LogManager;


/**
 * Tests for the NTEventLogAppender DLL path resolution
 * (log4j.nteventlog.dll.path system property).
 */
public class NTEventLogAppenderDllPathTest extends TestCase {

  /**
   * Error thrown while initializing NTEventLogAppender, null if it loaded.
   * The class is initialized here, before setUp clears the DLL path property,
   * so its static initializer sees the value this JVM was started with.
   */
  private static Throwable initError;

  static {
    try {
      Class.forName("org.apache.log4j.nt.NTEventLogAppender");
    } catch (Throwable t) {
      initError = t;
    }
  }

  private String originalDllPath;

  public void setUp() {
    originalDllPath = System.getProperty(NTEventLogAppender.DLL_PATH_PROPERTY);
    System.getProperties().remove(NTEventLogAppender.DLL_PATH_PROPERTY);
  }

  public void tearDown() {
    if (originalDllPath != null) {
      System.setProperty(NTEventLogAppender.DLL_PATH_PROPERTY, originalDllPath);
    } else {
      System.getProperties().remove(NTEventLogAppender.DLL_PATH_PROPERTY);
    }
    LogManager.shutdown();
  }

  /**
   * When the system property names an existing file by absolute path, the
   * static initializer must load that file with System.load instead of
   * searching PATH/java.library.path, where a planted DLL could be picked up.
   * Off Windows the build starts this test with the property pointing at a JDK
   * shared library and no NTEventLogAppender library on the search path, so the
   * appender only initializes if the configured absolute path was used.
   */
  public void testStaticInitializerLoadsConfiguredDllPath() {
    if (originalDllPath == null) {
      return;
    }
    if (initError != null) {
      fail("NTEventLogAppender did not load " + originalDllPath + ": " + initError);
    }
  }

  /**
   * When the system property is not set, resolveConfiguredDllPath should return null.
   */
  public void testResolveConfiguredDllPath_notSet() {
    System.getProperties().remove(NTEventLogAppender.DLL_PATH_PROPERTY);
    assertNull(NTEventLogAppender.resolveConfiguredDllPath());
  }

  /**
   * When the system property is an empty string, resolveConfiguredDllPath should return null.
   */
  public void testResolveConfiguredDllPath_emptyString() {
    System.setProperty(NTEventLogAppender.DLL_PATH_PROPERTY, "");
    assertNull(NTEventLogAppender.resolveConfiguredDllPath());
  }

  /**
   * When the system property is whitespace only, resolveConfiguredDllPath should return null.
   */
  public void testResolveConfiguredDllPath_whitespaceOnly() {
    System.setProperty(NTEventLogAppender.DLL_PATH_PROPERTY, "   ");
    assertNull(NTEventLogAppender.resolveConfiguredDllPath());
  }

  /**
   * When the system property is a relative path, resolveConfiguredDllPath should return null.
   */
  public void testResolveConfiguredDllPath_relativePath() {
    System.setProperty(NTEventLogAppender.DLL_PATH_PROPERTY, "relative/path/NTEventLogAppender.dll");
    assertNull(NTEventLogAppender.resolveConfiguredDllPath());
  }

  /**
   * A relative path must be rejected even when a matching DLL has been planted
   * in the working directory, so it is never loaded through that path.
   */
  public void testResolveConfiguredDllPath_relativePathToPlantedFile() throws IOException {
    File planted = File.createTempFile("NTEventLogAppender", ".dll",
        new File(System.getProperty("user.dir")));
    planted.deleteOnExit();
    try {
      String relativePath = planted.getName();
      assertTrue(new File(relativePath).exists());
      System.setProperty(NTEventLogAppender.DLL_PATH_PROPERTY, relativePath);
      assertNull(NTEventLogAppender.resolveConfiguredDllPath());
    } finally {
      planted.delete();
    }
  }

  /**
   * When the system property is an absolute path to a file that does not exist,
   * resolveConfiguredDllPath should return null.
   */
  public void testResolveConfiguredDllPath_absoluteButMissing() {
    String missingPath = new File(System.getProperty("java.io.tmpdir"), "no_such_NTEventLogAppender.dll")
        .getAbsolutePath();
    System.setProperty(NTEventLogAppender.DLL_PATH_PROPERTY, missingPath);
    assertNull(NTEventLogAppender.resolveConfiguredDllPath());
  }

  /**
   * When the system property is an absolute path to an existing file,
   * resolveConfiguredDllPath should return that absolute path.
   */
  public void testResolveConfiguredDllPath_absoluteAndExists() throws IOException {
    File tempFile = File.createTempFile("NTEventLogAppender", ".dll");
    tempFile.deleteOnExit();
    try {
      String absolutePath = tempFile.getAbsolutePath();
      System.setProperty(NTEventLogAppender.DLL_PATH_PROPERTY, absolutePath);
      String result = NTEventLogAppender.resolveConfiguredDllPath();
      assertNotNull(result);
      assertEquals(absolutePath, result);
    } finally {
      tempFile.delete();
    }
  }
}
