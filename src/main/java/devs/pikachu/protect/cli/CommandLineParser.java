package devs.pikachu.protect.cli;

import devs.pikachu.protect.config.ObfuscationConfig;
import devs.pikachu.protect.utility.MapReader;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public final class CommandLineParser {
    private CommandLineParser() {
    }

    public static ObfuscationConfig parse(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("input.jar と output.jar が必要です");
        }
        ObfuscationConfig config = new ObfuscationConfig();
        config.input = Paths.get(args[0]).toAbsolutePath().normalize();
        config.output = Paths.get(args[1]).toAbsolutePath().normalize();
        Path mapPath = null;
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if (arg.isBlank() || arg.startsWith("#")) {
                continue;
            }
            if (arg.equals("-inClass")) {
                i++;
                while (i < args.length && !args[i].startsWith("-")) {
                    config.includePatterns.add(Pattern.compile(args[i++]));
                }
                i--;
                continue;
            }
            if (arg.equals("-exClass")) {
                i++;
                while (i < args.length && !args[i].startsWith("-")) {
                    config.excludePatterns.add(Pattern.compile(args[i++]));
                }
                i--;
                continue;
            }
            switch (arg) {
                case "-?", "--help", "-help" -> throw new HelpRequestedException();
                case "-full" -> config.enableFull();
                case "-dontEncode" -> config.dontEncode = true;
                case "-dontVerify", "-dontverify" -> config.dontVerify = true;
                case "-useJunkCode" -> config.useJunkCode = true;
                case "-useMoreJunkCode" -> config.useMoreJunkCode = true;
                case "-useMoreJunkCode2" -> config.useMoreJunkCode2 = true;
                case "-useSuperJunkCode" -> config.useSuperJunkCode = true;
                case "-useInvokeDynamicObf" -> config.useInvokeDynamicObf = true;
                case "-useInvokeDynamicObfT" -> {
                    config.useInvokeDynamicObf = true;
                    config.useInvokeDynamicObfT = true;
                }
                case "-useStringObf" -> config.useStringObf = true;
                case "-useStringObfT" -> {
                    config.useStringObf = true;
                    config.useStringObfT = true;
                }
                case "-useNumberObf" -> config.useNumberObf = true;
                case "-reverse" -> config.useReverse = true;
                case "-obfLocalVar" -> config.obfLocalVar = true;
                case "-delLocalVar" -> config.delLocalVar = true;
                case "-fixVersion" -> config.fixVersion = true;
                case "-addSyntheticFlag" -> config.addSyntheticFlag = true;
                case "-classToFolder" -> config.classToFolder = true;
                case "-bigBrainNumberObf" -> config.bigBrainNumberObf = true;
                case "-classRandomName" -> config.classRandomName = true;
                case "-packageRemover" -> config.packageRemover = true;
                case "-noClassRename" -> config.classRandomName = false;
                case "-noPackageRemover" -> config.packageRemover = false;
                case "-applymap" -> {
                    i = requireValue(args, i, arg);
                    mapPath = resolveMap(args[i]);
                }
                case "-seed" -> {
                    i = requireValue(args, i, arg);
                    config.seed = Long.decode(args[i]);
                }
                case "-lib" -> {
                    i = requireValue(args, i, arg);
                    config.libraries.add(Paths.get(args[i]).toAbsolutePath().normalize());
                }
                case "-asmVer" -> {
                    i = requireValue(args, i, arg);
                    int value = Integer.parseInt(args[i]);
                    if (value < 4 || value > 10) {
                        throw new IllegalArgumentException("-asmVer は 4 から 10 の範囲で指定してください");
                    }
                }
                default -> throw new IllegalArgumentException("不明な引数: " + arg);
            }
        }
        if (!Files.isRegularFile(config.input)) {
            throw new IllegalArgumentException("Input JAR が見つかりません: " + config.input);
        }
        if (config.input.equals(config.output)) {
            throw new IllegalArgumentException("Input JAR と Output JAR は別のファイルを指定してください");
        }
        if (mapPath != null) {
            config.remapStrings = MapReader.read(mapPath);
            if (config.remapStrings.length == 0) {
                throw new IllegalArgumentException("map ファイルに有効な名前がありません: " + mapPath);
            }
        }
        return config;
    }

    private static int requireValue(String[] args, int index, String option) {
        if (index + 1 >= args.length) {
            throw new IllegalArgumentException(option + " の値がありません");
        }
        return index + 1;
    }

    private static Path resolveMap(String value) throws Exception {
        Path direct = Paths.get(value);
        if (Files.isRegularFile(direct)) {
            return direct.toAbsolutePath().normalize();
        }
        Path cwdMap = Paths.get("maps").resolve(value);
        if (Files.isRegularFile(cwdMap)) {
            return cwdMap.toAbsolutePath().normalize();
        }
        URI location = CommandLineParser.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path tool = Paths.get(location).toAbsolutePath().normalize();
        Path base = Files.isDirectory(tool) ? tool : tool.getParent();
        if (base != null) {
            Path besideJar = base.resolve("maps").resolve(value);
            if (Files.isRegularFile(besideJar)) {
                return besideJar.toAbsolutePath().normalize();
            }
        }
        throw new IllegalArgumentException("map ファイルが見つかりません: " + value);
    }

    public static final class HelpRequestedException extends RuntimeException {
    }
}
