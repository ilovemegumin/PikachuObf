package devs.pikachu.protect.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ObfuscationConfig {
    public Path input;
    public Path output;
    public final List<Pattern> includePatterns = new ArrayList<>();
    public final List<Pattern> excludePatterns = new ArrayList<>();
    public final List<Path> libraries = new ArrayList<>();
    public boolean useJunkCode;
    public boolean useMoreJunkCode;
    public boolean useMoreJunkCode2;
    public boolean useSuperJunkCode;
    public boolean useInvokeDynamicObf;
    public boolean useInvokeDynamicObfT;
    public boolean useStringObf;
    public boolean useStringObfT;
    public boolean useNumberObf;
    public boolean useReverse;
    public boolean obfLocalVar;
    public boolean delLocalVar;
    public boolean fixVersion;
    public boolean addSyntheticFlag;
    public boolean dontVerify;
    public boolean classToFolder;
    public boolean bigBrainNumberObf;
    public boolean dontEncode;
    public boolean classRandomName;
    public boolean packageRemover;
    public long seed = System.nanoTime();
    public String[] remapStrings = new String[0];

    public boolean shouldTransform(String internalName) {
        if (internalName == null || internalName.equals("module-info")) {
            return false;
        }
        boolean included = includePatterns.isEmpty();
        for (Pattern pattern : includePatterns) {
            if (pattern.matcher(internalName).matches()) {
                included = true;
                break;
            }
        }
        if (!included) {
            return false;
        }
        for (Pattern pattern : excludePatterns) {
            if (pattern.matcher(internalName).matches()) {
                return false;
            }
        }
        return true;
    }

    public void enableFull() {
        useJunkCode = true;
        delLocalVar = true;
        useNumberObf = true;
        useStringObf = true;
        useStringObfT = true;
        useInvokeDynamicObf = true;
        useInvokeDynamicObfT = true;
        classRandomName = true;
        packageRemover = true;
        addSyntheticFlag = true;
    }
}
