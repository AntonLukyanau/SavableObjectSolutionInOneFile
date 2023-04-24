package org.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Solution {
    public static interface SavableObjectWriter {
        void saveTo(String path, Object obj);
    }
    public static interface SavableObjectReader {
        Object readFrom(String path);
    }
    public static interface IdHolder {
        Long resolveId(SavableObject obj);
    }
    public static interface FieldToYamlConverter {
        String toYAMLLine(Object source, Field field);
        boolean canConvert(Field field);
    }
    public static class Config {
        public static final String FOLDER_TO_SAVE = SavableObject.class.getSimpleName();
        public static final String EXTENSION = ".yaml";
        public static final int INDENT = 2;
        public static final String PATH_TO_SEQ = FOLDER_TO_SAVE + File.separator + "saved_object.seq";
        public static final int SEQ_STEP = 1;
    }
    public static class StringUtil {
        public int indentsBeforeText(String line) {
            int count = 0;
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == ' ') {
                    count++;
                } else {
                    break;
                }
            }
            return count / Config.INDENT;
        }

        public String trimLastSymbol(String str) {
            return str.substring(0, str.length() - 1);
        }

        public String addIndents(String str, int indentCount) {
            return " ".repeat(indentCount * Config.INDENT) + str;
        }
    }
    public static class GeneralFieldToYamlConverter implements FieldToYamlConverter {
        public String toYAMLLine(Object source, Field field) {
            if (canConvert(field)) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(source);
                    return field.getName() + ": " + value;
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            throw new IllegalArgumentException("I can't convert this field");
        }

        public boolean canConvert(Field field) {
            Class<?> type = field.getType();
            return type.isPrimitive()
                    || type == String.class
                    || type == Boolean.class
                    || type == Character.class
                    || type.getSuperclass() == Number.class;
        }
    }
    public static class YAMLSavableObjectReader implements SavableObjectReader {
        private final StringUtil stringUtil = new StringUtil();

        public Object readFrom(String path) {
            try (Stream<String> linesStream = Files.lines(Paths.get(path))) {
                String[] strings = linesStream
                        .filter(line -> !line.isBlank())
                        .toArray(String[]::new);
                return readObject(strings);
            } catch (IOException | ClassNotFoundException e) {
                throw new IllegalArgumentException("Incorrect file!", e);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new IllegalStateException("Savable object must has public constructor without parameters", e);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        private Object readObject(String[] yamlLines) throws ReflectiveOperationException {
            Class<?> aClass = Class.forName(stringUtil.trimLastSymbol(yamlLines[0]).trim());
            Object instance = aClass.getConstructor().newInstance();
            String id = yamlLines[1].split(":")[1].trim();
            trySetId(id, aClass, instance);
            Map<String, Field> fields = fieldNamesToFields(aClass);
            readFields(yamlLines, instance, fields);
            return instance;
        }

        private static void trySetId(String value, Class<?> aClass, Object instance) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
            if (aClass.getSuperclass() == SavableObject.class) {
                Method setIdMethod = aClass.getSuperclass().getDeclaredMethod("setId", Long.class);
                setIdMethod.setAccessible(true);
                setIdMethod.invoke(instance, Long.parseLong(value));
            }
        }

        private static Map<String, Field> fieldNamesToFields(Class<?> aClass) {
            return Arrays.stream(aClass.getDeclaredFields())
                    .collect(Collectors.toMap(Field::getName, field -> field));
        }

        private void readFields(String[] strings, Object instance, Map<String, Field> fields) throws ReflectiveOperationException {
            for (int curLinePos = 1; curLinePos < strings.length; curLinePos++) {
                String[] entry = strings[curLinePos].split(":");
                String fieldName = entry[0].trim();
                Field field = fields.get(fieldName);
                if (entry.length == 1 && field != null) {
                    String[] typeWithRest = concatLinesToTypeInfo(strings, curLinePos, field.getType());
                    Object value = readObject(typeWithRest);
                    field.setAccessible(true);
                    field.set(instance, value);
                } else if (field != null) {
                    field.setAccessible(true);
                    field.set(instance, entry[1].trim());
                }
            }
        }

        private String[] concatLinesToTypeInfo(String[] lines, int currentPosition, Class<?> fieldType) {
            int indentForType = stringUtil.indentsBeforeText(lines[0]);
            String type = stringUtil.addIndents(fieldType.getName() + ":", indentForType + 1);
            String[] rest = Arrays.copyOfRange(lines, currentPosition + 1, lines.length);
            String[] typeWithRest = new String[1 + rest.length];
            typeWithRest[0] = type;
            System.arraycopy(rest, 0, typeWithRest, 1, rest.length);
            return typeWithRest;
        }
    }
    public static class YAMLSavableObjectWriter implements SavableObjectWriter {
        private final Set<FieldToYamlConverter> converters;
        private final StringUtil stringUtil = new StringUtil();

        public YAMLSavableObjectWriter() {
            converters = new HashSet<>();
            converters.add(new GeneralFieldToYamlConverter());
        }

        public void saveTo(String pathToSave, Object obj) {
            List<String> lines = new ArrayList<>();
            Class<?> aClass = obj.getClass();
            lines.add(aClass.getName() + ":");
            lines.addAll(createLines(obj, 0));
            try (FileWriter writer = new FileWriter(pathToSave)) {
                for (int i = 0; i < lines.size() - 1; i++) {
                    writer.write(lines.get(i));
                    writer.write("\n");
                }
                writer.write(lines.get(lines.size() - 1));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private List<String> createLines(Object obj, int indentCount) {
            try {
                List<String> lines = new ArrayList<>();
                Class<?> aClass = obj.getClass();
                int fieldIndent = indentCount + 1;
                tryAddLineWithId(obj, lines, fieldIndent);
                Field[] fields = aClass.getDeclaredFields();
                for (Field field : fields) {
                    FieldToYamlConverter converter = resolveConverterFor(field);
                    if (converter != null) {
                        String line = stringUtil.addIndents(converter.toYAMLLine(obj, field), fieldIndent);
                        lines.add(line);
                    } else {
                        field.setAccessible(true);
                        Object objFieldValue = field.get(obj);
                        lines.add(stringUtil.addIndents(field.getName() + ":", fieldIndent));
                        List<String> restLines = createLines(objFieldValue, fieldIndent);
                        lines.addAll(restLines);
                    }
                }
                return lines;
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        private void tryAddLineWithId(Object obj, List<String> lines, int fieldIndent)
                throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
            Class<?> superclass = obj.getClass().getSuperclass();
            if (superclass == SavableObject.class) {
                Method getIdMethod = superclass.getMethod("getId");
                Long id = (Long) getIdMethod.invoke(obj);
                lines.add(stringUtil.addIndents("id: " + id, fieldIndent));
            }
        }

        private FieldToYamlConverter resolveConverterFor(Field field) {
            for (FieldToYamlConverter converter : converters) {
                if (converter.canConvert(field)) {
                    return converter;
                }
            }
            return null;
        }
    }
    public static class CommonIdHolder implements IdHolder {
        public Long resolveId(SavableObject obj) {
            if (obj.getId() != null) {
                return obj.getId();
            }
            File entitySeqContainer = new File(Config.PATH_TO_SEQ);
            if (entitySeqContainer.exists()) {
                long lastId;
                try (BufferedReader reader = new BufferedReader(new FileReader(Config.PATH_TO_SEQ))) {
                    lastId = Long.parseLong(reader.readLine());
                } catch (IOException e) {
                    throw new RuntimeException(e); /* todo create new own runtime exception */
                }
                Long newId = lastId + Config.SEQ_STEP;
                setSeqValue(newId);
                return newId;
            }
            setSeqValue(1L);
            return 1L;
        }

        private void setSeqValue(Long id) {
            try (FileWriter out = new FileWriter(Config.PATH_TO_SEQ)) {
                out.write(id.toString());
            } catch (IOException e) {
                throw new RuntimeException(e); /* todo create new own runtime exception */
            }
        }
    }
    public static class SavableObjectsStorage {
        private static final Logger log = Logger.getLogger(SavableObjectsStorage.class.getName());

        private final SavableObjectWriter writer;
        private final SavableObjectReader reader;
        private final IdHolder idHolder;

        public SavableObjectsStorage() {
            this(new YAMLSavableObjectReader(), new YAMLSavableObjectWriter(), new CommonIdHolder());
            File folder = new File(Config.FOLDER_TO_SAVE);
            if (folder.mkdir()) {
                log.info("Folder: " + Config.FOLDER_TO_SAVE + " was created");
            }
        }

        private SavableObjectsStorage(SavableObjectReader reader, SavableObjectWriter writer, IdHolder idHolder) {
            this.reader = reader;
            this.writer = writer;
            this.idHolder = idHolder;
        }

        public void save(SavableObject savableObject) {
            Long objId = idHolder.resolveId(savableObject);
            savableObject.setId(objId);
            String path = Config.FOLDER_TO_SAVE + File.separator + objId + Config.EXTENSION;
            writer.saveTo(path, savableObject);
        }

        public void delete(SavableObject savableObject) {
            if (savableObject.getId() != null) {
                File entry = new File(Config.FOLDER_TO_SAVE + File.separator + savableObject.getId() + Config.EXTENSION);
                if (entry.delete()) {
                    savableObject.setId(null);
                } else {
                    log.warning("entity was not deleted!");
                }
            } else {
                log.warning("detected attempt to delete unsaved object");
            }
        }

        public SavableObject findById(Long id) {
            if (id == null) {
                throw new IllegalArgumentException("Id must be non null");
            }
            File entry = new File(Config.FOLDER_TO_SAVE + File.separator + id + Config.EXTENSION);
            if (entry.exists()) {
                return (SavableObject) reader.readFrom(Config.FOLDER_TO_SAVE + File.separator + id + Config.EXTENSION);
            }
            return null;
        }
    }
    public static abstract class SavableObject {
        private static final SavableObjectsStorage storage = new SavableObjectsStorage();
        private Long id;

        public SavableObject() {
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public void save() {
            storage.save(this);
        }

        public void delete() {
            storage.delete(this);
        }

        @SuppressWarnings("unchecked")
        public static <T extends SavableObject> T find(Long id) {
            return (T) storage.findById(id);
        }
    }
    public static class Address {
        private String street;
        private String city;
        private String stateCode;
        private String postIndex;

        public Address() {
        }

        public Address(String street, String city, String stateCode, String postIndex) {
            this.street = street;
            this.city = city;
            this.stateCode = stateCode;
            this.postIndex = postIndex;
        }

        public String getStreet() {
            return street;
        }

        public String getCity() {
            return city;
        }

        public String getStateCode() {
            return stateCode;
        }

        public String getPostIndex() {
            return postIndex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Address address)) return false;
            return Objects.equals(getStreet(), address.getStreet())
                    && Objects.equals(getCity(), address.getCity())
                    && Objects.equals(getStateCode(), address.getStateCode())
                    && Objects.equals(getPostIndex(), address.getPostIndex());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getStreet(), getCity(), getStateCode(), getPostIndex());
        }

        @Override
        public String toString() {
            return "org.example.Address{" +
                    "\n\tstreet='" + street + '\'' +
                    ",\n\tcity='" + city + '\'' +
                    ",\n\tstateCode='" + stateCode + '\'' +
                    ",\n\tpostIndex='" + postIndex + '\'' +
                    '}';
        }
    }
    public static class Person extends SavableObject {
        private String firstName;
        private String lastName;
        private Address address;

        public Person() {
        }

        public Person(String firstName, String lastName, Address address) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.address = address;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public Address getAddress() {
            return address;
        }
    }
    public static class Business extends SavableObject {
        private String name;
        private Address address;

        public Business() {
        }

        public Business(String name, Address address) {
            this.name = name;
            this.address = address;
        }

        public String getName() {
            return name;
        }

        public Address getAddress() {
            return address;
        }
    }
}
