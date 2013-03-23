package iron9light.coffeescriptMavenPlugin;

import com.google.common.base.Charsets;
import com.google.common.io.InputSupplier;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.Scriptable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.mozilla.javascript.NativeObject;

public class CoffeeScriptCompiler {

    private final Scriptable globalScope;
    private boolean bare;
	private boolean sourceMap;
    public String version;

    public CoffeeScriptCompiler(URL url, boolean bare, boolean sourceMap) {
        this.bare = bare;
		this.sourceMap = sourceMap;

        InputSupplier<InputStreamReader> supplier = Resources.newReaderSupplier(url, Charsets.UTF_8);
        Context context = Context.enter();
        context.setOptimizationLevel(-1); // Without this, Rhino hits a 64K bytecode limit and fails
        try {
            globalScope = context.initStandardObjects();
            context.evaluateReader(globalScope, supplier.getInput(), "coffee-script.js", 0, null);
            version = (String) context.evaluateString(globalScope, "CoffeeScript.VERSION;", "source", 0, null);
        } catch (IOException e1) {
            throw new CoffeeScriptException(e1.getMessage());
        } finally {
            Context.exit();
        }

    }

	/**
	 * Compile the source code.
	 *
	 * @param coffeeScriptSource source code.
	 * @param sourceFile source file name (used for source map generation).
	 * @param generatedFile generated file name (used for source map generation).
	 * @param sourceMapFile source map file name (used for source map generation).
	 * @return array of [complied code; source map]. Source map may be null if parameter was not specified.
	 */
    public String[] compile(String coffeeScriptSource,  String sourceFile, String generatedFile, String sourceMapFile) {
        Context context = Context.enter();
        try {
            Scriptable compileScope = context.newObject(globalScope);
            compileScope.setParentScope(globalScope);
            compileScope.put("coffeeScript", compileScope, coffeeScriptSource);
            try {

				String options = getOptions(sourceFile, generatedFile);

				String jsCode;
				String sourceMapCode;
				
				Object result = context.evaluateString(
						compileScope,
						String.format("CoffeeScript.compile(coffeeScript, %s);", options),
						"source", 0, null);
				
				if (!sourceMap) {
					jsCode = result.toString();
					sourceMapCode = null;
				} else {
					NativeObject object = (NativeObject) result;
					jsCode = object.get("js").toString() + "\n//@ sourceMappingURL=" + sourceMapFile;
					sourceMapCode = object.get("v3SourceMap").toString();
				}

				return new String[] { jsCode, sourceMapCode };
            } catch (JavaScriptException e) {
                throw new CoffeeScriptException(e.getMessage());
            }
        } finally {
            Context.exit();
        }
    }

	private String getOptions(String sourceFile, String generatedFile) {
		Map<String, Object> options = new HashMap<>();
		if (bare) {
			options.put("bare", true);
		}
		if (sourceMap) {
			options.put("sourceMap", true);
			options.put("sourceFiles", new String[]{sourceFile});
			options.put("generatedFile", generatedFile);
		}

		Gson gson = new GsonBuilder().create();
		String result = gson.toJson(options);
		return result;
	}

}
