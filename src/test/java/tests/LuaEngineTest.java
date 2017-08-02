package tests;

import interfaces.TestInterface;
import org.junit.Assert;
import org.junit.Test;
import scriptengine.LuaScriptEngine;

import javax.script.ScriptException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by 01083446 on 2017/6/15.
 */
public class LuaEngineTest {

    @Test
    public void testEval() {
        LuaScriptEngine engine = new LuaScriptEngine();
        String script = "print(hello) return 0";
        Map<String, Object> context = new HashMap<>();
        context.put("hello", "hello Lua");
        engine.putAll(context);
        try {
            Object result = engine.eval(script);
            Assert.assertEquals(0L, result);
        } catch (ScriptException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testInvokeFunction() {
        String script = "testAdd=function(a,b) return a+b end";
        LuaScriptEngine engine = new LuaScriptEngine();
        try {
            engine.eval(script);
            Object result = engine.invokeFunction("testAdd", 1, 2);
            Assert.assertEquals(3L, result);
        } catch (ScriptException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testEngineInvokable() {
        String add = "testAdd=function(a,b) return a+b end", sub = "testSub=function(a,b) return a-b end";
        LuaScriptEngine engine = new LuaScriptEngine();
        try {
            engine.eval(add);
            engine.eval(sub);
            TestInterface impl = engine.getInterface(TestInterface.class);
            Assert.assertNotNull(impl);
            Assert.assertEquals(3L, impl.testAdd(1, 2));
            Assert.assertEquals(1L, impl.testSub(3, 2));
        } catch (ScriptException e) {
            e.printStackTrace();
        }
    }

}
