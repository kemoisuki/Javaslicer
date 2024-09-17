package javaslicer;

import org.eclipse.jdt.core.dom.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class TestUR {
    public static void main(String[] args) {
   
        String directoryPath =  "C:\\eclipse-java-2023-12-R-win32-x86_64\\java"; // 文件目录路径
        String outputFilePath = "C:\\eclipse-java-2023-12-R-win32-x86_64\\java\\TEXT\\Rest2.txt"; // 输出文件路径

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
            public boolean visit(ReturnStatement node) {
                MethodDeclaration method = getEnclosingMethod(node);
                if (method != null) {
                    Javadoc javadoc = method.getJavadoc();
                    if (javadoc != null && hasReturnTag(javadoc)) {
                        try (FileWriter fw = new FileWriter(outputFilePath, true);
                             BufferedWriter bw = new BufferedWriter(fw);
                             PrintWriter out = new PrintWriter(bw)) {
                            out.println("文件: " + filePath);

                            // 获取方法签名信息
                            String modifiers = getModifiersString(method.modifiers());
                            Type returnType = method.getReturnType2();
                            String methodName = method.getName().getIdentifier();
                            String parameters = getParametersString(method.parameters());
                            out.println("返回值处理代码:");
                            // 输出方法签名
                            out.println(modifiers + " " + returnType + " " + methodName + "(" + parameters + ")");

                            // 获取返回值处理代码
                            Expression returnExpression = node.getExpression();
                            if (returnExpression != null) {
                                List<Statement> relatedStatements = getRelatedStatements(returnExpression, method.getBody());
                                for (Object obj : method.getBody().statements()) {
                                    Statement statement = (Statement) obj;
                                    if (!relatedStatements.contains(statement) && !(statement instanceof ReturnStatement) && !(statement instanceof ThrowStatement) && !(statement instanceof TryStatement)) {
                                        out.println(statement.toString());
                                    }
                                }
                            }

                            out.println("Javadoc: " +getJavadocWithoutTags(javadoc));
                            out.println();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                return super.visit(node);
            }
        });
    }
    private static boolean hasReturnTag(Javadoc javadoc) {
        List<?> tags = javadoc.tags();
        if (tags != null) {
            for (Object tag : tags) {
                if (tag instanceof TagElement && ((TagElement) tag).getTagName() != null && ((TagElement) tag).getTagName().equals("@return")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String getReturnDescription(Javadoc javadoc) {
        List<?> tags = javadoc.tags();
        if (tags != null) {
            for (Object tag : tags) {
                if (tag instanceof TagElement && ((TagElement) tag).getTagName() != null && ((TagElement) tag).getTagName().equals("@return")) {
                    return ((TagElement) tag).fragments().toString();
                }
            }
        }
        return "";
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

    private static Block getEnclosingBlock(ASTNode node) {
        while (node != null) {
            if (node instanceof Block) {
                return (Block) node;
            }
            node = node.getParent();
        }
        return null;
    }
    private static String getModifiersString(List<?> modifiers) {
        StringBuilder sb = new StringBuilder();
        for (Object modifier : modifiers) {
            if (modifier instanceof Modifier) {
                sb.append(((Modifier) modifier).getKeyword().toString()).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private static String getParametersString(List<?> parameters) {
        StringBuilder sb = new StringBuilder();
        for (Object parameter : parameters) {
            if (parameter instanceof SingleVariableDeclaration) {
                SingleVariableDeclaration svd = (SingleVariableDeclaration) parameter;
                sb.append(getModifiersString(svd.modifiers())).append(" ");
                sb.append(svd.getType().toString()).append(" ");
                sb.append(svd.getName().getIdentifier()).append(", ");
            }
        }
        if (sb.length() > 0) {
            sb.delete(sb.length() - 2, sb.length()); // 删除最后一个", "
        }
        return sb.toString();
    }
    private static class VarFound {
        boolean value = false;
    }

    private static List<Statement> getRelatedStatements(Expression returnExpression, Block methodBody) {
        List<Statement> relatedStatements = new ArrayList<>();
        Set<String> varNames = new HashSet<>();
        Set<Statement> outputtedStatements = new HashSet<>(); // 存储已经输出过的语句

        // 检查返回表达式的类型并提取变量
        if (returnExpression instanceof SimpleName) {
            varNames.add(returnExpression.toString());
        } else if (returnExpression instanceof InfixExpression) {
            InfixExpression infixExpression = (InfixExpression) returnExpression;
            if (infixExpression.getLeftOperand() instanceof SimpleName) {
                varNames.add(infixExpression.getLeftOperand().toString());
            }
            if (infixExpression.getRightOperand() instanceof SimpleName) {
                varNames.add(infixExpression.getRightOperand().toString());
            }
        } else if (returnExpression instanceof MethodInvocation) {
            MethodInvocation methodInvocation = (MethodInvocation) returnExpression;
            for (Object argument : methodInvocation.arguments()) {
                if (argument instanceof SimpleName) {
                    varNames.add(((SimpleName) argument).getIdentifier());
                }
            }
        }

        // 找出含有返回变量的句子
        VarFound newVarFound = new VarFound();
        do {
            newVarFound.value = false;
            List<Statement> newStatements = new ArrayList<>();
            methodBody.accept(new ASTVisitor() {
                public boolean visit(SimpleName node) {
                    if (varNames.contains(node.getIdentifier())) {
                        ASTNode parent = node.getParent();
                        while (!(parent instanceof Statement)) {
                            parent = parent.getParent();
                        }
                        if (!relatedStatements.contains((Statement) parent) && !outputtedStatements.contains((Statement) parent)) {
                            newStatements.add((Statement) parent);
                            outputtedStatements.add((Statement) parent); // 将语句添加到已输出语句的集合中
                            newVarFound.value = true;
                        }
                    }
                    return super.visit(node);
                }
            });

            // 找出新句子中的所有变量
            for (Statement statement : newStatements) {
                statement.accept(new ASTVisitor() {
                    public boolean visit(SimpleName node) {
                        varNames.add(node.getIdentifier());
                        return super.visit(node);
                    }
                });
            }

            // 将新的语句按照在源代码中的顺序插入到 relatedStatements 中
            newStatements.sort(Comparator.comparingInt(statement -> methodBody.statements().indexOf(statement)));
            relatedStatements.addAll(newStatements);
        } while (newVarFound.value);

        // 按照源代码的顺序，从上往下，对于源代码中的每个句子，如果该句子中的变量在完整的varNames集合中，则输出这个句子，否则不输出
        List<Statement> finalRelatedStatements = new ArrayList<>();
        for (Object obj : methodBody.statements()) {
            Statement statement = (Statement) obj;
            statement.accept(new ASTVisitor() {
                public boolean visit(SimpleName node) {
                    if (varNames.contains(node.getIdentifier()) && !finalRelatedStatements.contains(statement)) {
                        finalRelatedStatements.add(statement);
                        return false; // 停止访问这个语句的其他部分
                    }
                    return super.visit(node);
                }
            });
        }

        return finalRelatedStatements;
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