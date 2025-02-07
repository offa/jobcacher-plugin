package jenkins.plugins.jobcacher.arbitrary;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import hudson.util.DirScanner;
import hudson.util.io.ArchiverFactory;
import jenkins.MasterToSlaveFileCallable;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

public class TarGzArbitraryFileCacheStrategy extends AbstractCompressingArbitraryFileCacheStrategy {

    @Override
    protected String getArchiveExtension() {
        return ".tgz";
    }

    @Override
    protected void uncompress(FilePath source, FilePath target) throws IOException, InterruptedException {
        source.untar(target, FilePath.TarCompression.GZIP);
    }

    @Override
    protected void compress(FilePath source, String includes, String excludes, boolean useDefaultExcludes, FilePath target) throws IOException, InterruptedException {
        target.act(new CreateTarGzCallable(source, includes, excludes, useDefaultExcludes));
    }

    private static class CreateTarGzCallable extends MasterToSlaveFileCallable<Void> {

        private final FilePath source;
        private final String includes;
        private final String excludes;
        private final boolean useDefaultExcludes;

        public CreateTarGzCallable(FilePath source, String includes, String excludes, boolean useDefaultExcludes) {
            this.source = source;
            this.includes = includes;
            this.excludes = excludes;
            this.useDefaultExcludes = useDefaultExcludes;
        }

        @Override
        public Void invoke(File targetFile, VirtualChannel channel) throws IOException, InterruptedException {
            try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(targetFile.toPath()))) {
                source.archive(ArchiverFactory.TARGZ, outputStream, new DirScanner.Glob(includes, excludes, useDefaultExcludes));
            }

            return null;
        }
    }
}
