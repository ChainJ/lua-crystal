package scriptengine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import exception.LuaException;
import net.sandius.rembulan.*;
import net.sandius.rembulan.compiler.CompilerChunkLoader;
import net.sandius.rembulan.env.RuntimeEnvironments;
import net.sandius.rembulan.exec.CallException;
import net.sandius.rembulan.exec.CallPausedException;
import net.sandius.rembulan.exec.DirectCallExecutor;
import net.sandius.rembulan.impl.StateContexts;
import net.sandius.rembulan.lib.StandardLibrary;
import net.sandius.rembulan.load.ChunkLoader;
import net.sandius.rembulan.load.LoaderException;
import net.sandius.rembulan.runtime.LuaFunction;
import org.apache.commons.collections.map.ListOrderedMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by JiangCheng on 2017/6/8.
 */
public class LuaExecutor {
    /**
     * default context
     */
    private StateContext state = StateContexts.newDefaultInstance();
    /**
     * all the context of the executor, actually key-value map
     * default context is standard library of lua 5.3, but compilation of code chunk may add new values
     */
    private Table env = StandardLibrary.in(RuntimeEnvironments.system()).installInto(state);
    /**
     * default executor of Rembulan
     */
    private DirectCallExecutor executor = DirectCallExecutor.newExecutor();

    static private final String ROOT_CLASS_PREFIX = "LUA_CLASSES";
    static private final String FUNCTION_NAME = "LUA_FUNCTION";

    private static final Logger logger = LoggerFactory.getLogger(LuaExecutor.class);

    /**
     * run a code chunk
     *
     * @param script a code chunk to be compiled and called
     * @param args   optional parameters, but userdata will be converted to table in lua
     * @return results of the chunk if anything is returned or null
     * @throws LuaException
     */
    public Object[] run(String script, Object... args) throws LuaException {
        if (StringUtils.isBlank(script)) {
            return null;
        }
        ChunkLoader loader = CompilerChunkLoader.of(ROOT_CLASS_PREFIX);
        try {
            LuaFunction function = loader.loadTextChunk(new Variable(env), FUNCTION_NAME, script);
            return executor.call(state, function, convertArgs(args));
        } catch (LoaderException | InterruptedException | CallException | CallPausedException e) {
            logger.info(e.getMessage(), e);
            throw new LuaException(e.getMessage());
        }
    }

    public Object[] run(File file, Object... args) throws LuaException {
        StringBuffer script = new StringBuffer();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            char[] buffer = new char[200];
            int len = reader.read(buffer, 0, 200);
            while (len > 0) {
                script.append(buffer, 0, len);
                len = reader.read(buffer, 0, 200);
            }
        } catch (IOException e) {
            throw new LuaException("fail to load source file");
        }
        return run(script.toString(), args);
    }

    /**
     * call a function with given arguments
     *
     * @param functionName a function declared in env
     * @param args         arguments used by the function, userdata will be converted to table in lua
     * @return
     * @throws NoSuchMethodException
     * @throws LuaException
     */
    public Object[] call(String functionName, Object... args) throws NoSuchMethodException, LuaException {
        LuaFunction function = getFunction(functionName);
        if (function == null) {
            throw new NoSuchMethodException("no such method called " + functionName);
        }
        try {
            return executor.call(state, function, convertArgs(args));
        } catch (CallException | CallPausedException | InterruptedException e) {
            logger.info(e.getMessage(), e);
            throw new LuaException(e.getMessage());
        }
    }

    /**
     * run a code chunk with context add to executor's env
     *
     * @param script  a code chunk to be executed
     * @param context a key-value context add into env or replace existing value
     * @param args    will be used if a function is called in the code chunk
     * @return
     * @throws LuaException
     */
    public Object[] runWithContext(String script, Map<String, Object> context, Object... args) throws LuaException {
        for (String key : context.keySet()) {
            try {
                env.rawset(key, new MetaTable(context.get(key)));
            } catch (LuaException e) {
                env.rawset(key, context.get(key));
            }
        }
        ChunkLoader loader = CompilerChunkLoader.of(ROOT_CLASS_PREFIX);
        try {
            LuaFunction function = loader.loadTextChunk(new Variable(env), FUNCTION_NAME, script);
            return executor.call(state, function, convertArgs(args));
        } catch (LoaderException | InterruptedException | CallException | CallPausedException e) {
            logger.info(e.getMessage(), e);
            throw new LuaException(e.getMessage());
        }
    }

    /**
     * get a lua function from executor's env
     *
     * @param name function's name
     * @return
     */
    public LuaFunction getFunction(String name) {
        Object function = env.rawget(name);
        if (function != null && function instanceof LuaFunction) {
            return (LuaFunction) function;
        } else {
            return null;
        }
    }

    /**
     * add context to executor's env
     *
     * @param context the key-value context add to env or replace existing value, userdata value will be
     *                converted to table in lua
     */
    public void putContext(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return;
        }
        for (String key : context.keySet()) {
            try {
                env.rawset(key, new MetaTable(context.get(key)));
            } catch (LuaException e) {
                env.rawset(key, context.get(key));
            }
        }
    }

    /**
     * convert arguments to lua variable except userdata, userdata will be converted to table
     *
     * @param args arguments to be converted
     * @return converted arguments
     */
    private Object[] convertArgs(Object... args) {
        if (args == null) return null;
        for (int i = 0; i < args.length; i++) {
            try {
                args[i] = new MetaTable(args[i]);
            } catch (LuaException e) {
                continue;
            }
        }
        return args;
    }

    /**
     * convert non-userdata lua result to exact java class object
     *
     * @param luaResult a result returned by executing scripts
     * @param clazz     target java class
     * @param <T>       target java type
     * @return a java object
     */
    public static <T> T toJavaObject(Object luaResult, Class<T> clazz) {
        return ((MetaTable) luaResult).toExactObject(clazz);
    }

    /**
     * convert lua table to java list, only number indexed value will be returned
     *
     * @param luaResult table indexed by number
     * @return number indexed value in list
     */
    public static List toJavaList(Object luaResult) {
        return ((MetaTable) luaResult).toList();
    }

    private static class MetaTable extends Table {
        /**
         * to store a table whose key type is <b>Integer</b> or <b>Long</b> in Java
         */
        private ListOrderedMap longKeyTable = new ListOrderedMap();

        /**
         * to store a table whose key type is <b>Double</b> or <b>Float</b> in Java
         */
        private ListOrderedMap doubleKeyTable = new ListOrderedMap();

        /**
         * to store a table whose key type is <b>string</b> in Lua
         */
        private ListOrderedMap stringKeyTable = new ListOrderedMap();

        /**
         * to store a table whose key type is <b>Boolean</b> in Java
         */
        private ListOrderedMap boolKeyTable = new ListOrderedMap();

        /**
         * for extension, not implemented
         */
        private Map<Object, Object> table = new HashMap<>();

        protected MetaTable() {
            super();
        }

        protected MetaTable(Map table) {
            buildMetaTable(this, table);
        }

        protected MetaTable(Collection collection) {
            buildMetaTable(this, collection);
        }

        private MetaTable(Object object) throws LuaException {
            switch (LuaType.typeOf(object)) {
                case USERDATA:
                    break;
                default:
                    throw new LuaException("fail to build a MetaTable: object type " + LuaType.typeOf(object));
            }

            if (object instanceof Collection) {
                buildMetaTable(this, (Collection) object);
                return;
            }

            if (object instanceof Map) {
                buildMetaTable(this, (Map) object);
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> table;
            try {
                String json = mapper.writeValueAsString(object);
                table = mapper.readValue(json, new TypeReference<Map<String, Object>>() {
                });
            } catch (IOException e) {
                logger.info(e.getMessage(), e);
                throw new LuaException("fail to convert the object to a MetaTable: " + e.getMessage());
            }
            if (table == null || table.isEmpty()) {
                return;
            }
            buildMetaTable(this, table);
        }

        public <T> T toExactObject(Class<T> clazz) {
            ObjectMapper mapper = new ObjectMapper();
            try {
                String objectJson = mapper.writeValueAsString(stringKeyTable);
                return mapper.readValue(objectJson, clazz);
            } catch (IOException e) {
                logger.info(e.getMessage(), e);
                return null;
            }
        }

        public List toList() {
            return longKeyTable.valueList();
        }

        @Override
        public Object rawget(Object key) {
            return getFromTable(key);
        }

        @Override
        public void rawset(Object key, Object value) {
            insertIntoTable(key, value);
        }

        @Override
        public Object initialKey() {
            if (!longKeyTable.isEmpty()) {
                return longKeyTable.firstKey();
            } else if (!stringKeyTable.isEmpty()) {
                return stringKeyTable.firstKey();
            } else {
                return null;
            }
        }

        @Override
        public Object successorKeyOf(Object key) {
            if (key instanceof Long) {
                if (longKeyTable.lastKey().equals(key)) {
                    if (!doubleKeyTable.isEmpty()) {
                        return doubleKeyTable.firstKey();
                    } else if (!stringKeyTable.isEmpty()) {
                        return stringKeyTable.firstKey();
                    } else if (!boolKeyTable.isEmpty()) {
                        return boolKeyTable.firstKey();
                    } else {
                        return null;
                    }
                } else {
                    return longKeyTable.nextKey(key);
                }
            } else if (key instanceof Double) {
                if (doubleKeyTable.lastKey().equals(key)) {
                    if (!stringKeyTable.isEmpty()) {
                        return stringKeyTable.firstKey();
                    } else if (!boolKeyTable.isEmpty()) {
                        return boolKeyTable.firstKey();
                    } else {
                        return null;
                    }
                } else {
                    return doubleKeyTable.nextKey(key);
                }
            } else if (key instanceof String) {
                if (stringKeyTable.lastKey().equals(key)) {
                    if (!boolKeyTable.isEmpty()) {
                        return boolKeyTable.firstKey();
                    } else {
                        return null;
                    }
                } else {
                    return stringKeyTable.nextKey(key);
                }
            } else if (key instanceof Boolean) {
                if (boolKeyTable.lastKey().equals(key)) {
                    return null;
                } else {
                    return boolKeyTable.nextKey(key);
                }
            } else {
                return null;
            }
        }

        @Override
        protected void setMode(boolean weakKey, boolean weakValue) {

        }

        private void buildMetaTable(MetaTable target, Map table) {
            for (Object key : table.keySet()) {
                target.insertIntoTable(key, table.get(key));
            }
        }

        private void buildMetaTable(MetaTable target, Collection collection) {
            long key = 1L;
            for (Object o : collection) {
                target.insertIntoLongKeyTable(key, o);
                key++;
            }
        }

        public void insertIntoTable(Object key, Object value) {
            if (key == null) {
                return;
            }
            if (key instanceof Long || key instanceof Integer) {
                insertIntoLongKeyTable(((Number) key).longValue(), value);
            } else if (key instanceof Double || key instanceof Float) {
                insertIntoDoubleKeyTable(Double.valueOf(key.toString()), value);
            } else if (key instanceof String) {
                insertIntoStringKeyTable((String) key, value);
            } else if (key instanceof Boolean) {
                insertIntoBoolKeyTable((Boolean) key, value);
            } else {
                table.put(key, value);
            }
        }

        private void insertIntoLongKeyTable(Long key, Object value) {
            Object inputValue;
            try {
                inputValue = new MetaTable(value);
            } catch (LuaException e) {
                inputValue = value;
            }
            if (longKeyTable.isEmpty()) {
                longKeyTable.put(key, inputValue);
                return;
            }
            binaryInsert(longKeyTable, key, inputValue, 0, longKeyTable.size());
        }

        private void insertIntoDoubleKeyTable(Double key, Object value) {
            try {
                MetaTable table = new MetaTable(value);
                doubleKeyTable.put(key, table);
            } catch (LuaException e) {
                doubleKeyTable.put(key, value);
            }
        }

        private void insertIntoStringKeyTable(String key, Object value) {
            try {
                MetaTable table = new MetaTable(value);
                stringKeyTable.put(key, table);
            } catch (LuaException e) {
                stringKeyTable.put(key, value);
            }
        }

        private void insertIntoBoolKeyTable(Boolean key, Object value) {
            try {
                MetaTable table = new MetaTable(value);
                boolKeyTable.put(key, table);
            } catch (LuaException e) {
                boolKeyTable.put(key, value);
            }
        }

        private Object getFromTable(Object key) {
            if (key instanceof Long || key instanceof Integer) {
                return longKeyTable.get(((Number) key).longValue());
            } else if (key instanceof Double || key instanceof Float) {
                return doubleKeyTable.get(((Number) key).doubleValue());
            } else if (key instanceof String || key instanceof ByteString) {
                return stringKeyTable.get(key.toString());
            } else if (key instanceof Boolean) {
                return boolKeyTable.get(key);
            } else {
                return table.get(key);
            }
        }

        private static void binaryInsert(ListOrderedMap table, Long key, Object value, int left, int right) {
            List<Long> keyList = (List<Long>) table.keyList()
                    .stream().filter(k -> k instanceof Long).collect(Collectors.toList());
            if (left >= right) {
                table.put(left, key, value);
                return;
            }
            int mid = (left + right) / 2;
            if (keyList.get(mid) < key) {
                binaryInsert(table, key, value, mid + 1, right);
            } else {
                binaryInsert(table, key, value, left, mid);
            }
        }

    }

}
