# PikachuObf 3.0 Full Recode

## 対応

- ツール本体: Java 17以上
- class file: Java 17からJava 26を含むASM 9.10.1対応範囲
- Gradle Wrapper: 9.7.0
- ASM: 9.10.1

`-asmVer` は旧CLIとの互換用に受け付けますが、ASM APIの切り替えには使いません。
## Build

Windows:

```bat
gradlew.bat clean test fatJar
```

Linux/macOS:

```bash
./gradlew clean test fatJar
```

生成物は`build/libs/PikachuObf-3.0-FullRecode.jar`です。

## Usage

```text
java -jar PikachuObf-3.0-FullRecode.jar input.jar output.jar [options]
```

例:

```text
java -jar PikachuObf-3.0-FullRecode.jar input.jar output.jar -full -reverse
```

特定クラスだけ:

```text
java -jar PikachuObf-3.0-FullRecode.jar input.jar output.jar -inClass "com/example/(.*)" -exClass "com/example/api/(.*)" -useStringObf -useNumberObf
```

外部依存をframe計算へ渡す場合:

```text
java -jar PikachuObf-3.0-FullRecode.jar input.jar output.jar -full -lib libs/dependency.jar
```

## Options

- `-full`
- `-useStringObf`
- `-useStringObfT`
- `-useNumberObf`
- `-bigBrainNumberObf`
- `-reverse`
- `-useInvokeDynamicObf`
- `-useInvokeDynamicObfT`
- `-dontEncode`
- `-useJunkCode`
- `-useMoreJunkCode`
- `-useMoreJunkCode2`
- `-useSuperJunkCode`
- `-obfLocalVar`
- `-delLocalVar`
- `-applymap <file>`
- `-classRandomName`
- `-packageRemover`
- `-noClassRename`
- `-noPackageRemover`
- `-addSyntheticFlag`
- `-classToFolder`
- `-fixVersion`
- `-dontVerify`
- `-inClass <regex...>`
- `-exClass <regex...>`
- `-lib <jar>`
- `-seed <long>`
- `-asmVer <4-10>`

`-full`ではString、2層String、Number、InvokeDynamic、2層InvokeDynamic、Junk、LocalVariable削除、Synthetic、ClassRandomName、PackageRemoverを有効にします。