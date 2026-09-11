package com.cflint.config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.cflint.config.CFLintPluginInfo.PluginInfoRule;
import com.cflint.config.CFLintPluginInfo.PluginInfoRule.PluginParameter;
import com.cflint.plugins.CFLintScanner;

public abstract class BaseConfig implements CFLintConfiguration{

    /** Cache entry standing in for a parameter that resolves to null, so misses are cached too. */
    private static final Object NULL_VALUE = new Object();

    /**
     * Resolved parameter values, keyed by plugin instance and then parameter name. The rule
     * plugins ask for their parameters once per expression/name/tag, and each unanswered lookup
     * walks the rule list (and, for a chained config, every parent config), which dominates the
     * cost of a scan. The configuration is fully built before scanning starts; anything that
     * mutates it afterwards has to call clearParameterCache(). System property overrides
     * (-DCheckerClass.parameter=value) are therefore read on the first lookup only.
     */
    private final Map<CFLintScanner, Map<String, Object>> parameterCache = new IdentityHashMap<>();

    /** As parameterCache, for the typed getParameter(linter, name, clazz). */
    private final Map<CFLintScanner, Map<String, Object>> typedParameterCache = new IdentityHashMap<>();

    /**
     * Discard cached parameter values. Call after changing rules or parameters on a
     * configuration that has already been used.
     */
    protected void clearParameterCache() {
        parameterCache.clear();
        typedParameterCache.clear();
    }

    /**
     * get the string property from the configuration.
     * This can be overriden with -DcheckerClass.propertyname=value
     *
     * @param linter    instance of current CFLintScanner plugin
     * @param name      the name of the parameter
     * @return          the value of the parameter (may be null)
     */
    @Override
    public String getParameter(final CFLintScanner linter, final String name) {
        final Map<String, Object> cacheForLinter = parameterCache.computeIfAbsent(linter, k -> new HashMap<>());
        final Object cached = cacheForLinter.get(name);
        if (cached != null) {
            return cached == NULL_VALUE ? null : (String) cached;
        }
        final String retval = resolveParameter(linter, name);
        cacheForLinter.put(name, retval == null ? NULL_VALUE : retval);
        return retval;
    }

    private String resolveParameter(final CFLintScanner linter, final String name) {
        final String propertyForName = System.getProperty(linter.getClass().getSimpleName() + "." + name);
        if (propertyForName != null && propertyForName.trim().length() > 0) {
            return propertyForName;
        }
        //Return the property from the config.parameters if available.
        final Object overrideProperty = getParameter(linter.getClass().getSimpleName() + "." + name);
        if(overrideProperty != null){
            return overrideProperty.toString();
        }
        final PluginInfoRule relativePlugin = getRuleForPlugin(linter);
        if(name != null && relativePlugin != null && relativePlugin.getParameters()!=null){
            for(PluginParameter contextParm : relativePlugin.getParameters()){
                if(name.equalsIgnoreCase(contextParm.getName()) && contextParm.getValue()!=null){
                    return contextParm.getValue().toString();
                }
            }
        }

        return null;
    }

    /**
     * get the string property from the configuration.  If it is null return empty string.
     * This can be overriden with -DcheckerClass.propertyname=value
     *
     * @param linter    instance of current CFLintScanner plugin
     * @param name      the name of the parameter
     * @return          the value of the parameter (will not be null)
     */
    @Override
    public String getParameterNotNull(final CFLintScanner linter, final String name) {
        final String retval = getParameter(linter, name);
        if(retval != null){
            return retval;
        }
        return "";
    }

    /**
     * get the property from the configuration.
     * This can be overriden with -DcheckerClass.propertyname=value
     *
     * @param linter    instance of current CFLintScanner plugin
     * @param name      the name of the parameter
     * @param clazz     the desired class of the returned value.
     * @return          the value of the parameter
     */
    @SuppressWarnings("unchecked")
    @Override
    public <E> E getParameter(final CFLintScanner linter, final String name, final Class<E> clazz) {
        final Map<String, Object> cacheForLinter = typedParameterCache.computeIfAbsent(linter, k -> new HashMap<>());
        final String cacheKey = name + '\u0000' + clazz.getName();
        final Object cached = cacheForLinter.get(cacheKey);
        if (cached != null) {
            return cached == NULL_VALUE ? null : (E) cached;
        }
        final E retval = resolveParameter(linter, name, clazz);
        cacheForLinter.put(cacheKey, retval == null ? NULL_VALUE : retval);
        return retval;
    }

    @SuppressWarnings("unchecked")
    private <E> E resolveParameter(final CFLintScanner linter, final String name, final Class<E> clazz) {
        final String propertyForName = System.getProperty(linter.getClass().getSimpleName() + "." + name);
        if (propertyForName != null && propertyForName.trim().length() > 0) {
            if (clazz.equals(String.class)) {
                return (E) propertyForName;
            }
            if (List.class.isAssignableFrom(clazz)) {
                return (E) Arrays.asList(propertyForName.split(","));
            }
            System.err.println("Cannot associate property " + linter.getClass().getSimpleName() + "." + name + " as a " + clazz.getName());
        }
        //Return the property from the config.parameters if available.
        final Object overrideProperty = getParameter(linter.getClass().getSimpleName() + "." + name);
        if(overrideProperty != null){
            if (List.class.isAssignableFrom(clazz) && overrideProperty instanceof String) {
                return (E) Arrays.asList(overrideProperty.toString().split(","));
            }
            return (E) overrideProperty;
        }
        final PluginInfoRule relativePlugin = getRuleForPlugin(linter);
        if(name != null && relativePlugin != null && relativePlugin.getParameters()!=null){
            for(PluginParameter contextParm : relativePlugin.getParameters()){
                if(name.equalsIgnoreCase(contextParm.getName()) && contextParm.getValue()!=null){
                    return (E) contextParm.getValue();
                }
            }
        }
        return null;
    }
}