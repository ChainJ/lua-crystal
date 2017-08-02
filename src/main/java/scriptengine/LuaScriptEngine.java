package scriptengine;

import exception.LuaException;
import net.sandius.rembulan.runtime.LuaFunction;

import javax.script.*;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by JiangCheng on 2017/6/15.
 */
public class LuaScriptEngine extends AbstractScriptEngine implements Invocable {
    private LuaExecutor luaExecutor = new LuaExecutor();

    public void putAll(Map<String, Object> bindings) {
        if (bindings == null || bindings.keySet().size() == 0) {
            return;
        }
        bindings.keySet().forEach(key -> put(key, bindings.get(key)));
    }

    @Override
    public Object eval(String script, ScriptContext context) throws ScriptException {
        if (context == null) throw new NullPointerException("context must not be null");
        if (script == null) throw new NullPointerException("script must not be null");

        Map<String, Object> luaContext = new HashMap<>();
        Bindings bindings = context.getBindings(ScriptContext.GLOBAL_SCOPE);
        if (bindings != null) {
            luaContext.putAll(bindings);
        }
        bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
        if (bindings != null) {
            luaContext.putAll(bindings);
        }
        try {
            Object[] results = luaExecutor.runWithContext(script, luaContext);
            if (results != null && results.length == 1) {
                return results[0];
            } else {
                return results;
            }
        } catch (LuaException e) {
            throw new ScriptException(e.getMessage());
        }
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
        StringBuilder script = new StringBuilder();
        char[] buffer = new char[200];
        try {
            int len = reader.read(buffer, 0, 200);
            while (len > 0) {
                script.append(buffer, 0, len);
                len = reader.read(buffer, 0, 200);
            }
        } catch (IOException e) {
            throw new ScriptException(e.getMessage());
        }
        return eval(script.toString(), context);
    }

    @Override
    public Bindings createBindings() {
        return new SimpleBindings();
    }

    @Override
    public ScriptEngineFactory getFactory() {
        return new LuaScriptEngineFactory();
    }

    @Override
    public Object invokeMethod(Object target, String name, Object... args) throws ScriptException, NoSuchMethodException {
        if (!(target instanceof LuaScriptEngine)) {
            throw new ScriptException("the target object is not a class or subclass of LuaScriptEngine");
        }
        LuaScriptEngine engine = (LuaScriptEngine) target;
        try {
            Object[] results = engine.luaExecutor.call(name, args);
            if (results != null && results.length == 1) {
                return results[0];
            } else {
                return results;
            }
        } catch (LuaException e) {
            throw new ScriptException(e.getMessage());
        }
    }

    @Override
    public Object invokeFunction(String name, Object... args) throws ScriptException, NoSuchMethodException {
        if (luaExecutor.getFunction(name) == null) {
            Bindings bindings = getBindings(ScriptContext.ENGINE_SCOPE);
            if (bindings != null && bindings.get(name) instanceof LuaFunction) {
                luaExecutor.putContext(bindings);
            } else {
                throw new NoSuchMethodException("no such method called " + name);
            }
        }
        try {
            Object[] results = luaExecutor.call(name, args);
            if (results != null && results.length == 1) {
                return results[0];
            } else {
                return results;
            }
        } catch (LuaException e) {
            throw new ScriptException(e.getMessage());
        }
    }

    @Override
    public <T> T getInterface(Class<T> clazz) {
        try {
            Method[] methods = clazz.getDeclaredMethods();
            for (Method m : methods) {
                if (luaExecutor.getFunction(m.getName()) == null) {
                    Bindings bindings = getBindings(ScriptContext.ENGINE_SCOPE);
                    if (bindings != null && bindings.get(m.getName()) instanceof LuaFunction) {
                        luaExecutor.putContext(bindings);
                    } else {
                        throw new NoSuchMethodException("no such method called " + m.getName());
                    }
                }
            }
            return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz},
                    new LuaInvocationHandler(luaExecutor));
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @Override
    public <T> T getInterface(Object target, Class<T> clazz) {
        if (!(target instanceof LuaScriptEngine)) {
            return null;
        }
        return ((LuaScriptEngine) target).getInterface(clazz);
    }

    private static class LuaInvocationHandler implements InvocationHandler {

        private LuaExecutor executor;

        public LuaInvocationHandler(LuaExecutor luaExecutor) {
            executor = luaExecutor;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object[] result = executor.call(method.getName(), args);
            if (result != null) {
                return result[0];
            } else {
                return null;
            }
        }
    }

}
