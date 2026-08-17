package devs.pikachu.protect;

import devs.pikachu.protect.cli.CommandLineParser;
import devs.pikachu.protect.config.ObfuscationConfig;
import devs.pikachu.protect.core.ObfuscatorEngine;

public final class Main {
    public static final String VERSION = "3.0-FullRecode";

    private Main() {
    }

    public static void main(String[] args) {
        try {
            ObfuscationConfig config = CommandLineParser.parse(args);
            ObfuscatorEngine.Result result = new ObfuscatorEngine(config).run();
            System.out.println("PikachuObf " + VERSION);
            System.out.println("classes: " + result.transformedClasses() + "/" + result.totalClasses());
            System.out.println("output: " + config.output);
            for (String warning : result.warnings()) {
                System.out.println("warning: " + warning);
            }
        } catch (CommandLineParser.HelpRequestedException e) {
            printUsage();
        } catch (Throwable e) {
            System.err.println("PikachuObf failed: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("PikachuObf " + VERSION);
        System.out.println("java -jar PikachuObf.jar <InputJar> <OutputJar> [options]");
        System.out.println("-inClass <regex...>          対象クラス");
        System.out.println("-exClass <regex...>          除外クラス");
        System.out.println("-useStringObf               String LDC/定数フィールドを暗号化");
        System.out.println("-useStringObfT              Stringを2層化");
        System.out.println("-useNumberObf               int/longをXOR化");
        System.out.println("-reverse                    intをhashCode前像へ変換");
        System.out.println("-useInvokeDynamicObf        static/virtual/interface呼び出しをindy化");
        System.out.println("-useInvokeDynamicObfT       indyメタデータを2層暗号化");
        System.out.println("-dontEncode                 indyメタデータを暗号化しない");
        System.out.println("-classRandomName            クラス名をランダム化");
        System.out.println("-packageRemover             元パッケージ名を除去して1パッケージへ集約");
        System.out.println("-applymap <file>            local variable用mapを読み込む");
        System.out.println("-obfLocalVar                local variable名を変更");
        System.out.println("-delLocalVar                local variable/parameter名を削除");
        System.out.println("-useJunkCode                verifier-safe junkを追加");
        System.out.println("-useMoreJunkCode            junk量を増加");
        System.out.println("-useMoreJunkCode2           junk量をさらに増加");
        System.out.println("-useSuperJunkCode           最大junk量");
        System.out.println("-addSyntheticFlag           field/methodへsyntheticを付与");
        System.out.println("-classToFolder              .class/形式でZIP entryを書き出す");
        System.out.println("-fixVersion                 必要な最低class versionへ補正");
        System.out.println("-lib <jar>                  frame計算用依存JAR");
        System.out.println("-seed <long>                乱数seed固定");
        System.out.println("-full                       安全系transformをまとめて有効化");
        System.out.println("-noClassRename              -full後のclass renameを無効化");
        System.out.println("-noPackageRemover           -full後のpackage removerを無効化");
        System.out.println("-asmVer <4-10>              旧CLI互換。ASM API選択には使用しない");
    }
}
