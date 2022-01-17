/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pemja.utils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.regex.Pattern;

/** A common util Class. */
public class CommonUtils {
    public static final CommonUtils INSTANCE = new CommonUtils();

    private boolean initialized = false;

    private static final String GET_PYTHON_LIB_PATH_SCRIPT =
            "import sysconfig;" + "print(sysconfig.get_config_var('LIBDIR'))";

    private static final String GET_SITE_PACKAGES_PATH_SCRIPT =
            "import sysconfig; print(sysconfig.get_paths()[\"purelib\"])";

    private static final String GET_PEMJA_MODULE_PATH_SCRIPT =
            "import pemja;" + "import os;" + "print(os.path.dirname(pemja.__file__))";

    private CommonUtils() {}

    /**
     * Because JVM can't load library globally, so we support this method to load library globally.
     */
    public void loadLibrary(String pythonExec, String library) {
        if (!initialized) {
            String utilsLibPath =
                    getLibraryPathWithPattern(pythonExec, "^pemja_utils\\.cpython-.*\\.so$");
            System.load(utilsLibPath);
            initialized = true;
        }
        loadLibrary0(library);
    }

    public String getLibraryPathWithPattern(String pythonExec, String pattern) {
        if (pythonExec == null) {
            // run in source code
            String pythonModulePath =
                    String.join(
                            File.separator,
                            System.getProperty("user.dir"),
                            "src",
                            "main",
                            "python");
            File pythonModuleFile = new File(pythonModulePath);
            for (File f : Objects.requireNonNull(pythonModuleFile.listFiles())) {
                if (f.isFile() && Pattern.matches(pattern, f.getName())) {
                    return f.getAbsolutePath();
                }
            }
            throw new IllegalArgumentException(
                    "Test in source, you need to execute"
                            + "`python setup.py build_ext --inplace --force ` to"
                            + " build pemja.");
        } else {
            String sitePackagesPath;
            try {
                String out =
                        execute(new String[] {pythonExec, "-c", GET_SITE_PACKAGES_PATH_SCRIPT});
                sitePackagesPath = String.join(File.pathSeparator, out.trim().split("\n"));
            } catch (IOException e) {
                throw new RuntimeException(
                        "Failed to get pemja path. You need to `pip install pemja` firstly.", e);
            }
            File libFile = new File(sitePackagesPath);
            if (libFile.isDirectory()) {
                for (File f : Objects.requireNonNull(libFile.listFiles())) {
                    if (f.isFile() && Pattern.matches(pattern, f.getName())) {
                        return f.getAbsolutePath();
                    }
                }
            }
            throw new RuntimeException("Failed to find PemJa Library");
        }
    }

    public String getPemJaModulePath(String pythonExec) {
        if (pythonExec == null) {
            // run in source code
            return String.join(
                    File.separator,
                    System.getProperty("user.dir"),
                    "src",
                    "main",
                    "python",
                    "pemja");
        } else {
            String out;
            try {
                out = execute(new String[] {pythonExec, "-c", GET_PEMJA_MODULE_PATH_SCRIPT});
            } catch (IOException e) {
                throw new RuntimeException("Failed to get PemJa module path", e);
            }
            return String.join(File.pathSeparator, out.trim().split("\n"));
        }
    }

    public String getPythonLibrary(String pythonExec) {
        String libPath;
        try {
            String out;
            if (pythonExec == null) {
                // run in source code, use default `python3` to find python lib library.
                out = execute(new String[] {"python3", "-c", GET_PYTHON_LIB_PATH_SCRIPT});
            } else {
                out = execute(new String[] {pythonExec, "-c", GET_PYTHON_LIB_PATH_SCRIPT});
            }
            libPath = String.join(File.pathSeparator, out.trim().split("\n"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to get python lib path", e);
        }
        File libFile = new File(libPath);

        String libPythonPathPattern;
        if (isLinuxOs()) {
            libPythonPathPattern = "^libpython.*so$";
        } else if (isMacOs()) {
            libPythonPathPattern = "^libpython.*dylib$";
        } else {
            throw new RuntimeException("Unsupported os ");
        }
        if (libFile.isDirectory()) {
            for (File f : Objects.requireNonNull(libFile.listFiles())) {
                if (f.isFile() && Pattern.matches(libPythonPathPattern, f.getName())) {
                    return f.getAbsolutePath();
                }
            }
        }
        throw new RuntimeException("Failed to find libpython");
    }

    public boolean isLinuxOs() {
        String os = System.getProperty("os.name");
        return os.startsWith("Linux");
    }

    public boolean isMacOs() {
        String os = System.getProperty("os.name");
        return os.startsWith("Mac OS X");
    }

    private String execute(String[] commands) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(commands);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        InputStream in = new BufferedInputStream(p.getInputStream());
        StringBuilder out = new StringBuilder();
        String s;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            while ((s = br.readLine()) != null) {
                out.append(s).append("\n");
            }
        }
        try {
            if (p.waitFor() != 0) {
                throw new IOException(
                        String.format(
                                "Failed to execute the command: %s\noutput: %s",
                                String.join(" ", commands), out));
            }
        } catch (InterruptedException e) {
            // Ignored. The subprocess is dead after "br.readLine()" returns null, so the call of
            // "waitFor" should return intermediately.
        }
        return out.toString();
    }

    private native void loadLibrary0(String library);
}
