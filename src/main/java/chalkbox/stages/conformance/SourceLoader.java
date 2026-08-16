package chalkbox.stages.conformance;

import chalkbox.api.files.FileLoader;
import com.github.therapi.runtimejavadoc.ClassJavadoc;
import com.github.therapi.runtimejavadoc.internal.JsonJavadocReader;
import com.github.therapi.runtimejavadoc.repack.com.eclipsesource.json.Json;
import com.github.therapi.runtimejavadoc.repack.com.eclipsesource.json.JsonObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class SourceLoader extends ClassLoader {

    private File classDirectory;
    private List<String> files;

    public SourceLoader(String classDirectory, ClassLoader parent)
        throws IOException {
        super(parent);
        File file = new File(classDirectory);

        if (!file.exists() || !file.isDirectory()) {
            throw new IOException("Provided class directory does not exist");
        }

        FileLoader loader = new FileLoader(file.getPath(), "", ".class");
        loader.setRemoveSuffix(true);

        this.classDirectory = file;
        this.files = new ArrayList<>();
        for (String fileName : loader.loadFiles(file)) {
            this.files.add(fileName.replace(File.separator, "."));
        }
    }

    public SourceLoader(String classDirectory) throws IOException {
        this(classDirectory, getSystemClassLoader());
    }

    public List<String> getSourceFiles() {
        return files;
    }

    public Map<String, Class> getClassMap() throws ClassNotFoundException {
        Map<String, Class> classes = new TreeMap<>();
        for (String file : files) {
            // Any GUI-related classes break conformance, don't load them
            // TODO find a better fix for this
            if (
                file.contains("$") ||
                file.contains("Canvas") ||
                file.contains("Launcher")
            ) {
                continue;
            }
            classes.put(file, loadClass(file));
        }
        return classes;
    }

    private File getFile(String className) {
        return new File(
            classDirectory.getPath() +
            File.separator +
            className.replace(".", File.separator) +
            ".class"
        );
    }

    @Override
    public Class loadClass(String name) throws ClassNotFoundException {
        Class<?> loadedClass = findLoadedClass(name);

        if (loadedClass != null || !files.contains(name)) {
            return super.loadClass(name);
        }

        try {
            byte[] classData = Files.readAllBytes(getFile(name).toPath());

            return defineClass(name, classData, 0, classData.length);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private File loadJavaDoc(String className) {
        return new File(
            classDirectory.getPath() +
            File.separator +
            className.replace(".", File.separator) +
            "__Javadoc.json"
        );
    }

    public ClassJavadoc getTestJavadoc(String className) {
        try {
            BufferedReader reader = new BufferedReader(
                new FileReader(loadJavaDoc(className))
            );
            JsonObject json = Json.parse(reader).asObject();
            return JsonJavadocReader.readClassJavadoc(className, json);
        } catch (Exception ignored) {
            //            System.out.println(ignored);
        }
        return ClassJavadoc.createEmpty(className);
    }
}
