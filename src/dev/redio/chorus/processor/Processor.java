package dev.redio.chorus.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeVariable;

import dev.redio.chorus.Freezable;

@SupportedAnnotationTypes("dev.redio.chorus.processor.Mutable")
@SupportedSourceVersion(SourceVersion.RELEASE_15)
public class Processor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        final var messager = processingEnv.getMessager();
        for (var annotation : annotations) {
            var annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
            var splitedElements = annotatedElements.stream()
                    .collect(Collectors.partitioningBy(e -> e instanceof TypeElement type && type.getKind() == ElementKind.RECORD));
            splitedElements.get(false)
                    .forEach(e -> messager.printError("@Mutable must be applied to a record", e));
            final var records = splitedElements.get(true);
            if (records.isEmpty())
                continue;
            for (var record : records) {
                try {
                    processRecord((TypeElement)record);
                } catch (IOException e) {
                    messager.printError("IOException:" + e.getMessage());
                }
            }
        }
    
        return true;
    }
    
    public void processRecord(TypeElement typeElement) throws IOException {
        final var qualifiedName = typeElement.getQualifiedName();
        final var name = getName(qualifiedName);
        final var path = getPath(qualifiedName);

        final var newQualifiedName = prepentMutatableToPath(qualifiedName);
        final var newName = getName(newQualifiedName);
        final var newPath = getPath(newQualifiedName);

        // final var qualifiedName = typeElement.getQualifiedName().toString();
        // final var lastDot = qualifiedName.lastIndexOf('.');
        // final var name = qualifiedName.substring(lastDot + 1);
        // final var newName = "Mutable" + name;
        // final String path;
        // final String newQualifiedName;
        // if (lastDot == -1) {
        //     path = "";
        //     newQualifiedName = newName;
        // } else {
        //     path = qualifiedName.substring(0, lastDot);
        //     newQualifiedName = path + "." + newName;
        // }

        final var fields = typeElement.getRecordComponents();
        final var messager = processingEnv.getMessager();
        
        final var builderFile = processingEnv.getFiler().createSourceFile(newQualifiedName);
        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
            if (path != null) {
                out.print("package ");
                out.print(path);
                out.println(";");
                out.println();
            }

            out.print("public class ");
            out.print(newName);

            final var paramBuilder = new StringBuilder();

            if(!typeElement.getTypeParameters().isEmpty()) {
                final var typeParameters = typeElement.getTypeParameters();
                out.print("<");
                paramBuilder.append("<");
                for(int i = 0; i < typeParameters.size(); i++) {
                    final var parameter = typeParameters.get(i);
                    out.print(parameter);
                    paramBuilder.append(parameter);
                    var bounds = parameter.getBounds();
  
                    out.print(" extends ");
                    out.print(bounds.get(0));
                    
                    for (int j = 1; j < bounds.size(); j++) {
                        out.print(" & ");
                        out.print(bounds.get(i));
                    }
                    if (i < typeParameters.size() - 1) {
                        out.print(", ");
                        paramBuilder.append(", ");
                    }
                }
                out.print(">");
                paramBuilder.append(">");
            }
            
            

            final var parameterUsed = paramBuilder.toString();

            out.print(" implements dev.redio.chorus.Freezable<");
            out.print(qualifiedName);
            out.print(parameterUsed);
            out.print(">");


            out.println(" {");
            out.println();

            for (var field : fields) {
                out.print("public ");
                var frozen = field.getAnnotation(Frozen.class);//annotation not working 
                if (frozen != null) {
                    var mutableType = frozen.mutableType();
                    if (mutableType == Freezable.class) {
                        var mutableName = prepentMutatableToPath(field.asType().toString());
                        out.print(mutableName);
                    } else {
                        out.print(mutableType.getCanonicalName());
                    }
                } else {
                    out.print(field.asType().toString());
                }

                //out.print(field.getAccessor().getReturnType() instanceof DeclaredType t && t.);
                //try to find field type info
                
                //out.print(field.asType().toString());
                out.print(" ");
                out.print(field.getSimpleName());
                out.println(";");
            }
            out.println();
            out.println("@Override");
            out.print("public ");
            out.print(qualifiedName);
            out.print(parameterUsed);
            out.println(" freeze() {");
            out.print("return new ");
            out.print(qualifiedName);
            out.print(parameterUsed);
            out.print("(");
            for (int i = 0; i < fields.size(); i++) {
                final var field = fields.get(i);
                out.print(field.getSimpleName());
                if (field.getAnnotation(Frozen.class) != null)
                    out.print(".freeze()");
                if (i < fields.size() - 1)
                    out.print(", ");
            }
            out.println(");");
            out.println("}");
            out.print("}");

        }
    }

    private static String prepentMutatableToPath(CharSequence qualifiedName) {
        Objects.requireNonNull(qualifiedName);
        final var path = getPath(qualifiedName);
        final var name = getName(qualifiedName);
        final var builder = new StringBuilder(path);
        if (!path.isEmpty())
            builder.append('.');
        builder.append("Mutable");
        builder.append(name);
        return builder.toString();
    }

    private static CharSequence getPath(CharSequence qualifiedName) {
        final var lastDot = lastIndexOfCS(qualifiedName, '.');
        if (lastDot == -1)
            return EMPTY_CHAR_SEQUENCE;
        return qualifiedName.subSequence(0, lastDot);
    }

    private static CharSequence getName(CharSequence qualifedName) {
        final var lastDot = lastIndexOfCS(qualifedName, '.');
        return qualifedName.subSequence(lastDot+1, qualifedName.length());
    }

    private static int lastIndexOfCS(CharSequence cs, char c) {
        for (int i = cs.length()-1; i >= 0; i--) {
            if (cs.charAt(i) == c)
                return i;
        }
        return -1;
    }

    private static final CharSequence EMPTY_CHAR_SEQUENCE = new CharSequence() {

        @Override
        public int length() {
            return 0;
        }

        @Override
        public char charAt(int index) {
            throw new IndexOutOfBoundsException(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            if (start != 0)
                throw new IndexOutOfBoundsException(start);
            if (end != 0)
                throw new IndexOutOfBoundsException(end);
            return this;
        }

        @Override
        public String toString() {
            return "";
        }
        
    };
    
}