package scriptengine;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by JiangCheng on 2017/6/15.
 */
public class LuaScriptEngineFactory implements ScriptEngineFactory {

    private static final String ENGINE_NAME = "Lua Crystal";
    private static final String ENGINE_VERSION = "0.1";
    private static final String LANGUAGE_NAME = "Lua";
    private static final String LANGUAGE_VERSION = "5.3";
    private static final List<String> ENGINE_EXTENSIONS = Collections.unmodifiableList(Arrays.asList("lua"));
    private static final List<String> ENGINE_MIME_TYPES = Collections.unmodifiableList(Arrays.asList("lua"));
    private static final List<String> ENGINE_NAMES = Collections.unmodifiableList(Arrays.asList("lua", "LUA", "Lua", "Crystal"));

    private static final String THREADING = null;

    @Override
    public String getEngineName() {
        return ENGINE_NAME;
    }

    @Override
    public String getEngineVersion() {
        return ENGINE_VERSION;
    }

    @Override
    public List<String> getExtensions() {
        return ENGINE_EXTENSIONS;
    }

    @Override
    public List<String> getMimeTypes() {
        return ENGINE_MIME_TYPES;
    }

    @Override
    public List<String> getNames() {
        return ENGINE_NAMES;
    }

    @Override
    public String getLanguageName() {
        return LANGUAGE_NAME;
    }

    @Override
    public String getLanguageVersion() {
        return LANGUAGE_VERSION;
    }

    @Override
    public Object getParameter(String key) {
        switch (key) {
            case "ScriptEngine.ENGINE":
                return getEngineName();
            case "ScriptEngine.ENGINE_VERSION":
                return getEngineVersion();
            case "ScriptEngine.LANGUAGE":
                return getLanguageName();
            case "ScriptEngine.LANGUAGE_VERSION":
                return getLanguageVersion();
            case "ScriptEngine.NAME":
                return getNames().get(0);
            case "THREADING":
                return THREADING;
            default:
                return null;
        }
    }

    @Override
    public String getMethodCallSyntax(String obj, String m, String... args) {
        return null;
    }

    @Override
    public String getOutputStatement(String toDisplay) {
        return "print(" + toDisplay + ")";
    }

    @Override
    public String getProgram(String... statements) {
        if (statements == null || statements.length == 0) {
            return "";
        }
        String statement = "";
        for (String s : statements) {
            statement += s + " ";
        }
        return statement;
    }

    @Override
    public ScriptEngine getScriptEngine() {
        return new LuaScriptEngine();
    }
}
