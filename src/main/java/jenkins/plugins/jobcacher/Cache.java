/*
 * The MIT License
 *
 * Copyright 2016 Peter Hayes.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.plugins.jobcacher;

import hudson.*;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import jenkins.MasterToSlaveFileCallable;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class provides the Cache extension point that when implemented provides the caching logic for saving files
 * from the executor to the master and sending them back to the executor.
 *
 * @author Peter Hayes
 */
public abstract class Cache extends AbstractDescribableImpl<Cache> implements ExtensionPoint {

    /**
     * Calculate the size of the cache on the executor which will be used to determine if the total size of the cache
     * if returned to the master would be greater than the configured maxiumum cache size.
     *
     * @param build The build in progress
     * @param workspace The executor workspace
     * @param launcher The launcher
     * @param listener The task listener
     * @return The size in bytes of the remote cache
     * @throws IOException If an error occurs connecting to the potentially remote executor
     * @throws InterruptedException If interrupted
     */
    public abstract long calculateSize(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException;

    /**
     * To be implemented method that will be called to seed the cache on the executor from the master
     *
     * @param cacheDir The root cache directory on the master where the cached files are stored.  Cache implementations must
     *                 define a subdirectory within the root cache directory. @see deriveCachePath method
     * @param build The build in progress
     * @param workspace The executor workspace
     * @param launcher The launcher
     * @param listener The task listener
     * @param initialEnvironment The initial environment variables
     * @throws IOException If an error occurs connecting to the potentially remote executor
     * @throws InterruptedException If interrupted
     */
    public abstract void cache(File cacheDir, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException;

    /**
     * This method recursively copies files from the sourceDir to the path on the executor
     *
     * @param sourceDir The source directory of the cache
     * @param workspace The executor workspace that the destination path will be referenced
     * @param listener The task listener
     * @param path The path on the executor to store the source cache on
     * @param includes The glob expression that will filter the contents of the path
     * @param excludes The excludes expression that will filter contents of the path
     * @throws IOException If an error occurs connecting to the potentially remote executor
     * @throws InterruptedException If interrupted
     */
    protected void cachePath(FilePath sourceDir, FilePath workspace, TaskListener listener, String path, String includes, String excludes) throws IOException, InterruptedException {

        if (sourceDir.exists()) {
            FilePath targetDirectory = workspace.child(path);

            if (!targetDirectory.exists()) {
                targetDirectory.mkdirs();
            }

            listener.getLogger().println("Caching " + path + " to executor");

            sourceDir.copyRecursiveTo(includes, excludes, targetDirectory);
        } else {
            listener.getLogger().println("Skip caching as no cache exists for " + path);
        }
    }

    /**
     * To be implemented method that will be called to save the files from the executor to the master
     *
     * @param cacheDir The root cache directory on the master where the cached files are stored.  Cache implementations must
     *                 define a subdirectory within the root cache directory. @see deriveCachePath method
     * @param build The build in progress
     * @param workspace The executor workspace
     * @param launcher The launcher
     * @param listener The task listener
     * @throws IOException If an error occurs connecting to the potentially remote executor
     * @throws InterruptedException If interrupted
     */
    public abstract void save(File cacheDir, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException;

    /**
     * This method recursively copies files from the path on the executor to the master target directory
     *
     * @param targetDir The target directory of the cache
     * @param workspace The executor workspace that the destination path will be referenced
     * @param listener The task listener
     * @param path The path on the executor to store the source cache on
     * @param includes The glob expression that will filter the contents of the path
     * @param excludes The excludes expression that will filter contents of the path
     * @throws IOException If an error occurs connecting to the potentially remote executor
     * @throws InterruptedException If interrupted
     */
    protected void savePath(FilePath targetDir, FilePath workspace, TaskListener listener, String path, String includes, String excludes) throws IOException, InterruptedException {

        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        FilePath sourceDirectory = workspace.child(path);

        listener.getLogger().println("Storing " + path + " in cache");

        // TODO remove files that are no longer in cache
        sourceDirectory.copyRecursiveTo(includes, excludes, targetDir);
    }

    /**
     * Get the human readable title for this cache to be shown on the user interface
     *
     * @return The title of the cache
     */
    public abstract String getTitle();

    /**
     * Generate a path within the cache dir given a relative or absolute path that is being cached
     *
     * @param cacheDir The job's cache directory
     * @param path The relative or absolute path that is being cached
     * @return A filepath where to save and read from the cache
     */
    protected FilePath deriveCachePath(File cacheDir, String path) {
        return new FilePath(new File(cacheDir, Util.getDigestOf(path)));
    }

    /**
     * Utility class to calculate the size of a potentially remote directory given a pattern and excludes
     */
    public static class DirectorySize extends MasterToSlaveFileCallable<Long> {
        private final String glob;
        private final String excludes;
        public DirectorySize(String glob, String excludes) {
            this.glob = glob;
            this.excludes = excludes;
        }
        @Override public Long invoke(File f, VirtualChannel channel) throws IOException {
            final AtomicLong total = new AtomicLong(0L);

            new DirScanner.Glob(glob, excludes).scan(f, new FileVisitor() {
                @Override
                public void visit(File f, String relativePath) throws IOException {
                    if (f.isFile()) {
                        total.addAndGet(f.length());
                    }
                }
            });
            return total.get();
        }
    }
}
