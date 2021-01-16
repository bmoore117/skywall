package com.hyperion.skywall;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathUtil {

    private static final boolean inDevMode = "dev".equalsIgnoreCase(System.getenv("SKYWALL_RUN_MODE"));

    public static Path getWindowsPath(Path input) {
        if (inDevMode) {
            return input;
        } else {
            return Paths.get(System.getenv("LOCALAPPDATA"), "SkyWall", input.toString());
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
