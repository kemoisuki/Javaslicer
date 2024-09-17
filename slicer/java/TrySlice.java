package javaslicer;

import org.eclipse.jdt.core.dom.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class TrySlice {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("缺少文件目录路径");
            return;
        }
        String directoryPath = "C:\\eclipse-java-2023-12-R-win32-x86_64\\java"; // 文件目录路径
        String outputFilePath = "C:\\eclipse-java-2023-12-R-win32-x86_64\\java\\TEXT\\exception-try.txt"; // 输出文件路径

        // 获取目录下的所有Java文件
        try (Stream<Path> paths = Files.walk(Paths.get(directoryPath))) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".java"))
                 .forEach(path -> analyzeCode(path, outputFilePath));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void analyzeCode(Path filePath, String outputFilePath) {
        String source = readFileToString(filePath.toString());
        ASTParser parser = ASTParser.newParser(AST.JLS3);
        parser.setSource(source.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);

        cu.accept(new ASTVisitor() {
            public boolean visit(MethodDeclaration method) {
                String methodName = method.getName().getIdentifier();
                Block body = method.getBody();
                if (body != null) {
                    for (Object obj : body.statements()) {
                        Statement stmt = (Statement) obj;
                        if (stmt instanceof TryStatement) {
                            TryStatement tryStmt = (TryStatement) stmt;
                            List catchClauses = tryStmt.catchClauses();
                            for (Object ccObj : catchClauses) {
                                CatchClause cc = (CatchClause) ccObj;
                                Javadoc javadoc = method.getJavadoc();
                                if (javadoc != null) {
                                    List<TagElement> tags = javadoc.tags();
                                    for (TagElement tag : tags) {
                                        if (tag.getTagName() != null && tag.getTagName().equals("@throws")) {
                                            //writeToFile("文件: " + filePath + "\n在方法 " + " 中捕获异常: " + cc.getException().getType(), outputFilePath);
                                            //writeToFile("处理该异常的代码:", outputFilePath);
                                            cc.getBody().statements().forEach(statement -> writeToFile(statement.toString(), outputFilePath));
                                            writeToFile( tag.toString(), outputFilePath);
                                            writeToFile("", outputFilePath); // 添加空行以分隔不同文件的输出
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                return true; 
            }
        });
    }

    private static String readFileToString(String filePath) {
        StringBuilder source = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(filePath));
            String line;
            while ((line = reader.readLine()) != null) {
                source.append(line).append('\n');
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return source.toString();
    }

    // 将信息写入文件的方法
 // 将信息写入文件的方法
    private static void writeToFile(String content, String filePath) {
        try (FileWriter fw = new FileWriter(filePath, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            if (!content.isEmpty()) {
                out.println(content);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
//1245