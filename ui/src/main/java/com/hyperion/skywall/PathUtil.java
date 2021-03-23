package com.hyperion.skywall;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathUtil {

    private static final boolean inDevMode = "dev".equalsIgnoreCase(System.getenv("SKYWALL_RUN_MODE"));

    public static Path getWindowsPath(String... input) {
        if (inDevMode) {
            String[] leftovers = new String[input.length - 1];
            System.arraycopy(input, 1, leftovers, 0, leftovers.length);
            return Paths.get(input[0], leftovers);
        } else {
            String[] contents = new String[input.length + 1];
            contents[0] = "SkyWall";
            System.arraycopy(input, 0, contents, 1, input.length);
            return Paths.get(System.getenv("LOCALAPPDATA"), contents);
        }
    }

    public static File getWindowsFile(File input) {
        if (inDevMode) {
            return input;
        } else {
            return new File(System.getenv("LOCALAPPDATA") + "/SkyWall/" + input.getPath());
        }
    }
}
