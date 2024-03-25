package com.cflint.plugins;

import com.cflint.BugList;

/**
 * This interface supports plugins recieving structure notifications (file
 * start/end, component start/end, function start/end).
 *
 * Normally it is used for initialization or summarization types of behaviours.
 */
public interface CFLintStructureListener {

    /**
     * Called when processing of a new file has started
     * 
     * @param fileName fileName
     *            Name of the file that started
     * @param bugs bugs
     *            List of errors reported
     */
    void startFile(final String fileName, BugList bugs);

    /**
     * Called when processing of current file has ended
     * 
     * @param fileName fileName
     *            Name of the file that finished
     * @param bugs bugs
     *            List of errors reported
     */
    void endFile(final String fileName, BugList bugs);

    /**
     * Called when processing of current file has almost ended
     * 
     * @param fileName fileName
     *            Name of the file that finished
     * @param context context
     *            Current context
     * @param bugs bugs
     *            List of errors reported
     */
    void beforeEndFile(final String fileName, Context context, BugList bugs);

    /**
     * Called when processing a new component has started
     * 
     * @param context context
     *            Current context
     * @param bugs bugs
     *            List of errors reported
     */
    void startComponent(final Context context, BugList bugs);

    /**
     * Called when processing of current component has ended
     * 
     * @param context context
     *            Current context
     * @param bugs bugs
     *            List of errors reported
     */
    void endComponent(final Context context, BugList bugs);

    /**
     * Called when processing of a new function has started
     * 
     * @param context context
     *            Current context
     * @param bugs bugs
     *            List of errors reported
     */
    void startFunction(final Context context, BugList bugs);

    /**
     * Called when processing of current function has ended
     * 
     * @param context context
     *            Current context
     * @param bugs bugs
     *            List of errors reported
     */
    void endFunction(final Context context, BugList bugs);

}
