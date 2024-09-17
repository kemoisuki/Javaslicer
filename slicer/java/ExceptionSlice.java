package javaslicer;

import org.eclipse.jdt.core.dom.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class ExceptionSlice {
    public static void main(String[] args) {
        String directoryPath = "C:\\eclipse-java-2023-12-R-win32-x86_64\\java"; // 文件夹路径
        String outputFilePath = "C:\\eclipse-java-2023-12-R-win32-x86_64\\java\\TEXT\\exception-throw.txt"; // 输出文件路径

        // 获取目录下的所有Java文件
        try (Stream<Path> paths = Files.walk(Paths.get(directoryPath))) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".java"))
                 .forEach(path -> processJavaFile(path.toString(), outputFilePath));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void processJavaFile(String filePath, String outputFilePath) {
        String sourceCode = readFileToString(filePath);
        ASTParser parser = ASTParser.newParser(AST.JLS3);
        parser.setSource(sourceCode.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);

        CompilationUnit cu = (CompilationUnit) parser.createAST(null);

        cu.accept(new ASTVisitor() {
            public boolean visit(ThrowStatement node) {
                try (FileWriter fw = new FileWriter(outputFilePath, true);
                     BufferedWriter bw = new BufferedWriter(fw);
                     PrintWriter out = new PrintWriter(bw)) {
                    MethodDeclaration method = getEnclosingMethod(node);
                    if (method != null) {
                        Javadoc javadoc = method.getJavadoc();
                        if (javadoc != null) {
                            List<TagElement> tags = javadoc.tags();
                            for (TagElement tag : tags) {
                                if (tag.getTagName() != null && tag.getTagName().equals("@throws")) {
                                    //out.println("File: " + filePath);
                                    //out.println("Method Name: " + method.getName());
                                    out.println("throw " + node.getExpression());
                                    out.println( tag.toString());
                                    out.println(); // 添加空行以分隔不同文件的输出
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return super.visit(node);
            }
        });
    }

    private static MethodDeclaration getEnclosingMethod(ASTNode node) {
        while (node != null) {
            if (node instanceof MethodDeclaration) {
                return (MethodDeclaration) node;
            }
            node = node.getParent();
        }
        return null;
    }

    private static String readFileToString(String filePath) {
        StringBuilder fileData = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(filePath));
            char[] buf = new char[1024];
            int numRead;
            while ((numRead = reader.read(buf)) != -1) {
                String readData = String.valueOf(buf, 0, numRead);
                fileData.append(readData);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fileData.toString();
    }
}
//7456
