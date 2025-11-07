package mattmc.nbt;

import mattmc.client.Minecraft;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Simple NBT (Named Binary Tag) utility for reading/writing Minecraft-style data.
 * Supports only the subset of NBT needed for world saving/loading.
 */
public class NBTUtil {
    
    // Tag types
    private static final byte TAG_END = 0;
    private static final byte TAG_BYTE = 1;
    private static final byte TAG_SHORT = 2;
    private static final byte TAG_INT = 3;
    private static final byte TAG_LONG = 4;
    private static final byte TAG_FLOAT = 5;
    private static final byte TAG_DOUBLE = 6;
    private static final byte TAG_BYTE_ARRAY = 7;
    private static final byte TAG_STRING = 8;
    private static final byte TAG_LIST = 9;
    private static final byte TAG_COMPOUND = 10;
    private static final byte TAG_INT_ARRAY = 11;
    private static final byte TAG_LONG_ARRAY = 12;
    
    // Maximum sizes for deserialization to prevent DoS attacks
    private static final int MAX_BYTE_ARRAY_SIZE = 16777216;  // 16MB
    private static final int MAX_LONG_ARRAY_SIZE = 2097152;   // 2M longs (16MB)
    private static final int MAX_LIST_SIZE = 1048576;         // 1M elements
    
    /**
     * Write a compound tag to a stream (compressed with gzip).
     */
    public static void writeCompressed(Map<String, Object> compound, OutputStream out) throws IOException {
        try (GZIPOutputStream gzip = new GZIPOutputStream(out);
             DataOutputStream dos = new DataOutputStream(gzip)) {
            writeCompoundTag(dos, "", compound);
        } catch (NBTSerializationException e) {
            throw e;
        } catch (IOException e) {
            throw new NBTSerializationException("Failed to write compressed NBT stream", "", "COMPOUND", e);
        }
    }
    
    /**
     * Read a compound tag from a stream (compressed with gzip).
     */
    public static Map<String, Object> readCompressed(InputStream in) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(in);
             DataInputStream dis = new DataInputStream(gzip)) {
            byte type = dis.readByte();
            if (type != TAG_COMPOUND) {
                throw new NBTDeserializationException("Root tag must be compound, but got type " + type, null, type, 0);
            }
            dis.readUTF(); // Read and discard root name
            return readCompound(dis, 0);
        } catch (NBTDeserializationException e) {
            throw e;
        } catch (IOException e) {
            throw new NBTDeserializationException("Failed to read compressed NBT stream", null, TAG_COMPOUND, -1, e);
        }
    }
    
    /**
     * Write a compound tag to a stream (deflate compression).
     */
    public static void writeDeflated(Map<String, Object> compound, OutputStream out) throws IOException {
        try (DeflaterOutputStream deflate = new DeflaterOutputStream(out);
             DataOutputStream dos = new DataOutputStream(deflate)) {
            writeCompoundTag(dos, "", compound);
        } catch (NBTSerializationException e) {
            throw e;
        } catch (IOException e) {
            throw new NBTSerializationException("Failed to write deflated NBT stream", "", "COMPOUND", e);
        }
    }
    
    /**
     * Read a compound tag from a stream (deflate compression).
     */
    public static Map<String, Object> readDeflated(InputStream in) throws IOException {
        try (InflaterInputStream inflate = new InflaterInputStream(in);
             DataInputStream dis = new DataInputStream(inflate)) {
            byte type = dis.readByte();
            if (type != TAG_COMPOUND) {
                throw new NBTDeserializationException("Root tag must be compound, but got type " + type, null, type, 0);
            }
            dis.readUTF(); // Read and discard root name
            return readCompound(dis, 0);
        } catch (NBTDeserializationException e) {
            throw e;
        } catch (IOException e) {
            throw new NBTDeserializationException("Failed to read deflated NBT stream", null, TAG_COMPOUND, -1, e);
        }
    }
    
    private static void writeCompoundTag(DataOutputStream dos, String name, Map<String, Object> compound) throws IOException {
        try {
            dos.writeByte(TAG_COMPOUND);
            dos.writeUTF(name);
            
            for (Map.Entry<String, Object> entry : compound.entrySet()) {
                writeTag(dos, entry.getKey(), entry.getValue());
            }
            
            dos.writeByte(TAG_END); // End compound
        } catch (NBTSerializationException e) {
            throw e;
        } catch (IOException e) {
            throw new NBTSerializationException("Failed to write compound tag", name, "COMPOUND", e);
        }
    }
    
    private static void writeTag(DataOutputStream dos, String name, Object value) throws IOException {
        try {
            if (value instanceof Byte) {
                dos.writeByte(TAG_BYTE);
                dos.writeUTF(name);
                dos.writeByte((Byte) value);
            } else if (value instanceof Integer) {
                dos.writeByte(TAG_INT);
                dos.writeUTF(name);
                dos.writeInt((Integer) value);
            } else if (value instanceof Long) {
                dos.writeByte(TAG_LONG);
                dos.writeUTF(name);
                dos.writeLong((Long) value);
            } else if (value instanceof Float) {
                dos.writeByte(TAG_FLOAT);
                dos.writeUTF(name);
                dos.writeFloat((Float) value);
            } else if (value instanceof Double) {
                dos.writeByte(TAG_DOUBLE);
                dos.writeUTF(name);
                dos.writeDouble((Double) value);
            } else if (value instanceof String) {
                dos.writeByte(TAG_STRING);
                dos.writeUTF(name);
                dos.writeUTF((String) value);
            } else if (value instanceof byte[]) {
                dos.writeByte(TAG_BYTE_ARRAY);
                dos.writeUTF(name);
                byte[] array = (byte[]) value;
                dos.writeInt(array.length);
                dos.write(array);
            } else if (value instanceof long[]) {
                dos.writeByte(TAG_LONG_ARRAY);
                dos.writeUTF(name);
                long[] array = (long[]) value;
                dos.writeInt(array.length);
                for (long l : array) {
                    dos.writeLong(l);
                }
            } else if (value instanceof List) {
                writeList(dos, name, (List<?>) value);
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) value;
                writeCompoundTag(dos, name, map);
            }
        } catch (NBTSerializationException e) {
            throw e;
        } catch (IOException e) {
            String typeName = value == null ? "null" : value.getClass().getSimpleName();
            throw new NBTSerializationException("Failed to write tag value of type " + typeName, name, getTypeNameForValue(value), e);
        }
    }
    
    private static void writeList(DataOutputStream dos, String name, List<?> list) throws IOException {
        try {
            dos.writeByte(TAG_LIST);
            dos.writeUTF(name);
            
            if (list.isEmpty()) {
                dos.writeByte(TAG_END);
                dos.writeInt(0);
                return;
            }
            
            // Determine list type from first element
            Object first = list.get(0);
            byte listType = getTypeId(first);
            dos.writeByte(listType);
            dos.writeInt(list.size());
            
            for (Object item : list) {
                writeListElement(dos, item, listType);
            }
        } catch (NBTSerializationException e) {
            throw e;
        } catch (IOException e) {
            throw new NBTSerializationException("Failed to write list", name, "LIST", e);
        }
    }
    
    private static byte getTypeId(Object value) {
        if (value instanceof Byte) return TAG_BYTE;
        if (value instanceof Integer) return TAG_INT;
        if (value instanceof Long) return TAG_LONG;
        if (value instanceof Float) return TAG_FLOAT;
        if (value instanceof Double) return TAG_DOUBLE;
        if (value instanceof String) return TAG_STRING;
        if (value instanceof Map) return TAG_COMPOUND;
        return TAG_END;
    }
    
    private static String getTypeNameForValue(Object value) {
        if (value instanceof Byte) return "BYTE";
        if (value instanceof Integer) return "INT";
        if (value instanceof Long) return "LONG";
        if (value instanceof Float) return "FLOAT";
        if (value instanceof Double) return "DOUBLE";
        if (value instanceof String) return "STRING";
        if (value instanceof byte[]) return "BYTE_ARRAY";
        if (value instanceof long[]) return "LONG_ARRAY";
        if (value instanceof List) return "LIST";
        if (value instanceof Map) return "COMPOUND";
        return "UNKNOWN";
    }
    
    private static void writeListElement(DataOutputStream dos, Object value, byte type) throws IOException {
        try {
            switch (type) {
                case TAG_BYTE -> dos.writeByte((Byte) value);
                case TAG_INT -> dos.writeInt((Integer) value);
                case TAG_LONG -> dos.writeLong((Long) value);
                case TAG_FLOAT -> dos.writeFloat((Float) value);
                case TAG_DOUBLE -> dos.writeDouble((Double) value);
                case TAG_STRING -> dos.writeUTF((String) value);
                case TAG_COMPOUND -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) value;
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        writeTag(dos, entry.getKey(), entry.getValue());
                    }
                    dos.writeByte(TAG_END);
                }
            }
        } catch (NBTSerializationException e) {
            throw e;
        } catch (IOException e) {
            throw new NBTSerializationException("Failed to write list element", "", getTypeNameFromId(type), e);
        }
    }
    
    private static String getTypeNameFromId(byte type) {
        return switch (type) {
            case TAG_END -> "END";
            case TAG_BYTE -> "BYTE";
            case TAG_SHORT -> "SHORT";
            case TAG_INT -> "INT";
            case TAG_LONG -> "LONG";
            case TAG_FLOAT -> "FLOAT";
            case TAG_DOUBLE -> "DOUBLE";
            case TAG_BYTE_ARRAY -> "BYTE_ARRAY";
            case TAG_STRING -> "STRING";
            case TAG_LIST -> "LIST";
            case TAG_COMPOUND -> "COMPOUND";
            case TAG_INT_ARRAY -> "INT_ARRAY";
            case TAG_LONG_ARRAY -> "LONG_ARRAY";
            default -> "UNKNOWN";
        };
    }
    
    private static Map<String, Object> readCompound(DataInputStream dis, long basePosition) throws IOException {
        Map<String, Object> compound = new HashMap<>();
        long position = basePosition;
        
        while (true) {
            byte type;
            try {
                type = dis.readByte();
                position++;
            } catch (IOException e) {
                throw new NBTDeserializationException("Failed to read tag type", null, TAG_COMPOUND, position, e);
            }
            
            if (type == TAG_END) {
                break;
            }
            
            String name;
            try {
                name = dis.readUTF();
                position += 2 + name.length(); // UTF strings are length-prefixed with 2 bytes
            } catch (IOException e) {
                throw new NBTDeserializationException("Failed to read tag name", null, type, position, e);
            }
            
            try {
                Object value = readTagPayload(dis, type, name, position);
                compound.put(name, value);
                // Note: position tracking after readTagPayload is approximate
            } catch (NBTDeserializationException e) {
                throw e;
            } catch (IOException e) {
                throw new NBTDeserializationException("Failed to read tag payload", name, type, position, e);
            }
        }
        
        return compound;
    }
    
    private static Object readTagPayload(DataInputStream dis, byte type, String tagName, long position) throws IOException {
        try {
            return switch (type) {
                case TAG_BYTE -> dis.readByte();
                case TAG_INT -> dis.readInt();
                case TAG_LONG -> dis.readLong();
                case TAG_FLOAT -> dis.readFloat();
                case TAG_DOUBLE -> dis.readDouble();
                case TAG_STRING -> dis.readUTF();
                case TAG_BYTE_ARRAY -> {
                    int length = dis.readInt();
                    if (length < 0 || length > MAX_BYTE_ARRAY_SIZE) {
                        throw new NBTDeserializationException("Invalid byte array length: " + length, tagName, type, position);
                    }
                    byte[] array = new byte[length];
                    dis.readFully(array);
                    yield array;
                }
                case TAG_LONG_ARRAY -> {
                    int length = dis.readInt();
                    if (length < 0 || length > MAX_LONG_ARRAY_SIZE) {
                        throw new NBTDeserializationException("Invalid long array length: " + length, tagName, type, position);
                    }
                    long[] array = new long[length];
                    for (int i = 0; i < length; i++) {
                        array[i] = dis.readLong();
                    }
                    yield array;
                }
                case TAG_LIST -> readList(dis, tagName, position);
                case TAG_COMPOUND -> readCompound(dis, position);
                default -> throw new NBTDeserializationException("Unknown tag type: " + type, tagName, type, position);
            };
        } catch (NBTDeserializationException e) {
            throw e;
        } catch (IOException e) {
            throw new NBTDeserializationException("Failed to read tag payload", tagName, type, position, e);
        }
    }
    
    private static List<Object> readList(DataInputStream dis, String tagName, long position) throws IOException {
        try {
            byte listType = dis.readByte();
            int length = dis.readInt();
            
            if (length < 0 || length > MAX_LIST_SIZE) {
                throw new NBTDeserializationException("Invalid list length: " + length, tagName, TAG_LIST, position);
            }
            
            List<Object> list = new ArrayList<>(length);
            
            for (int i = 0; i < length; i++) {
                if (listType == TAG_COMPOUND) {
                    list.add(readCompound(dis, position));
                } else {
                    list.add(readTagPayload(dis, listType, tagName + "[" + i + "]", position));
                }
            }
            
            return list;
        } catch (NBTDeserializationException e) {
            throw e;
        } catch (IOException e) {
            throw new NBTDeserializationException("Failed to read list", tagName, TAG_LIST, position, e);
        }
    }
}
