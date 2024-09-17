package javaslicer;

import org.eclipse.jdt.core.dom.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class RestTryU {
    public static void main(String[] args) {
      
        String directoryPath = "C:\\eclipse-java-2023-12-R-win32-x86_64\\java"; // 文件目录路径
        String outputFilePath = "C:\\eclipse-java-2023-12-R-win32-x86_64\\java\\TEXT\\RestTry2.txt"; // 输出文件路径

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
                Type returnType = method.getReturnType2();
                if (returnType != null && returnType.isPrimitiveType() && 
                    ((PrimitiveType) returnType).getPrimitiveTypeCode() == PrimitiveType.VOID) {
                    Javadoc javadoc = method.getJavadoc();
                    Block body = method.getBody();
                    StringBuilder stmts = new StringBuilder();
                    if (body != null) {
                        for (Object obj : body.statements()) {
                            Statement stmt = (Statement) obj;
                            if (!(stmt instanceof TryStatement) && !(stmt instanceof ThrowStatement) && !(stmt instanceof IfStatement)) {
                                stmts.append(stmt.toString()).append("\n");
                            }
                        }
                    }
                    if (javadoc != null && stmts.length() > 0) {
                        String javadocWithoutTags = getJavadocWithoutTags(javadoc);
                        if (!javadocWithoutTags.isEmpty()) {
                        	writeToFile("文件: " + filePath,outputFilePath);
                            writeToFile(" \n" + stmts.toString() +  "Javadoc:"+javadocWithoutTags+ " \n", outputFilePath);
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
    private static void writeToFile(String content, String filePath) {
        try (FileWriter fw = new FileWriter(filePath, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getJavadocWithoutTags(Javadoc javadoc) {
        StringBuilder sb = new StringBuilder();
        List<TagElement> tags = javadoc.tags();
        for (TagElement tag : tags) {
            if (tag.getTagName() == null) {
                for (Object fragment : tag.fragments()) {
                    sb.append(fragment.toString());
                }
            }
        }
        return sb.toString();
    }
}

