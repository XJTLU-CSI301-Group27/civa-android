package edu.xjtlu.civa;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Bytecode gate from PROJECT_DOC.md 6.2/14.6: registered Android public symbols and
 * their member names must exist in the compiled classpath. Server symbols are checked
 * in the server project, not here. Exact per-signature matching is not yet enforced.
 */
public class PublicSurfaceArchTest {
    private static Map<String, JavaClass> androidClasses() {
        Map<String, JavaClass> byName = new HashMap<>();
        for (JavaClass klass : new ClassFileImporter().importPackages("edu.xjtlu.civa")) {
            byName.put(klass.getName().replace('$', '.'), klass);
        }
        return byName;
    }

    private static JsonArray symbols() throws Exception {
        Path surface = Path.of(System.getProperty("civa.publicSurface"));
        try (var reader = new InputStreamReader(Files.newInputStream(surface), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("symbols");
        }
    }

    @Test public void registeredAndroidSymbolsExistInCompiledClasses() throws Exception {
        Map<String, JavaClass> classes = androidClasses();
        for (JsonElement element : symbols()) {
            String type = element.getAsJsonObject().get("type").getAsString().replaceAll("<.*>", "");
            if (type.startsWith("edu.xjtlu.civa.server.")) continue;
            assertTrue("registered symbol not found in compiled classes: " + type, classes.containsKey(type));
        }
    }

    @Test public void registeredAndroidMembersMatchSignatures() throws Exception {
        Map<String, JavaClass> classes = androidClasses();
        for (JsonElement element : symbols()) {
            JsonObject symbol = element.getAsJsonObject();
            String type = symbol.get("type").getAsString().replaceAll("<.*>", "");
            if (type.startsWith("edu.xjtlu.civa.server.")) continue;
            JavaClass klass = classes.get(type);
            if (klass == null) continue; // existence is asserted by the other test
            Map<String, List<List<String>>> methods = new HashMap<>();
            for (var method : klass.getAllMethods()) {
                List<String> sig = new ArrayList<>();
                sig.add(simpleName(method.getRawReturnType()));
                method.getRawParameterTypes().forEach(t -> sig.add(simpleName(t)));
                methods.computeIfAbsent(method.getName(), ignored -> new ArrayList<>()).add(sig);
            }
            List<List<String>> constructors = klass.getConstructors().stream()
                    .map(c -> { List<String> sig = new ArrayList<>(); c.getRawParameterTypes().forEach(t -> sig.add(simpleName(t))); return sig; })
                    .collect(Collectors.toList());
            Map<String, String> fields = new HashMap<>();
            for (var field : klass.getAllFields()) {
                fields.put(field.getName(), simpleName(field.getRawType()));
            }
            String simple = klass.getSimpleName();
            for (JsonElement memberElement : symbol.getAsJsonArray("members")) {
                String member = memberElement.getAsString();
                String name = memberName(member);
                if (member.indexOf('(') >= 0) {
                    List<String> expected = new ArrayList<>();
                    if (!name.equals(simple)) expected.add(returnType(member));
                    expected.addAll(paramTypes(member));
                    boolean match = name.equals(simple)
                            ? constructors.stream().anyMatch(expected::equals)
                            : methods.getOrDefault(name, List.of()).stream().anyMatch(expected::equals);
                    assertTrue("signature mismatch: " + member + " in " + type, match);
                } else {
                    assertTrue("missing field " + name + " in " + type, fields.containsKey(name));
                    if (member.contains(" ")) { // enum constants carry no type
                        assertTrue("field type mismatch: " + member + " in " + type, fieldType(member).equals(fields.get(name)));
                    }
                }
            }
        }
        assertFalse(classes.isEmpty());
    }

    private static String simpleName(JavaClass type) {
        return type.getSimpleName();
    }

    private static String memberName(String member) {
        int paren = member.indexOf('(');
        if (paren >= 0) {
            String before = member.substring(0, paren).trim();
            int space = before.lastIndexOf(' ');
            return space >= 0 ? before.substring(space + 1) : before;
        }
        String[] parts = member.trim().split("\\s+");
        return parts[parts.length - 1];
    }

    private static String returnType(String member) {
        int paren = member.indexOf('(');
        String before = member.substring(0, paren).trim();
        int space = before.lastIndexOf(' ');
        if (space < 0) return ""; // constructor
        String rest = before.substring(0, space).trim();
        String[] tokens = rest.split("\\s+");
        return tokens[tokens.length - 1].replaceAll("<.*>", "");
    }

    private static String fieldType(String member) {
        String[] tokens = member.trim().split("\\s+");
        int i = 0;
        while (i < tokens.length && (tokens[i].startsWith("@") || tokens[i].equals("final"))) i++;
        if (i >= tokens.length) return "";
        String type = tokens[i].replaceAll("<.*>", "");
        return type.matches("[A-Z]") ? "Object" : type;
    }

    private static List<String> paramTypes(String member) {
        List<String> types = new ArrayList<>();
        int open = member.indexOf('(');
        int close = member.lastIndexOf(')');
        if (open < 0 || close < 0 || close < open) return types;
        String params = member.substring(open + 1, close).trim();
        if (params.isEmpty()) return types;
        for (String param : splitTopLevel(params)) {
            String type = param.trim().replaceAll("<.*>", "");
            while (type.startsWith("@")) {
                int space = type.indexOf(' ');
                if (space < 0) { type = ""; break; }
                type = type.substring(space + 1).trim();
            }
            int space = type.indexOf(' ');
            if (space >= 0) type = type.substring(0, space);
            if (type.matches("[A-Z]")) type = "Object"; // generic type variable erases to Object
            types.add(type);
        }
        return types;
    }

    private static List<String> splitTopLevel(String params) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < params.length(); i++) {
            char c = params.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ',' && depth == 0) { result.add(params.substring(start, i)); start = i + 1; }
        }
        result.add(params.substring(start));
        return result;
    }
}
