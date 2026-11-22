package io.github.admiralxy.agent.service.git.impl;

import io.github.admiralxy.agent.config.AiHttpClientBuilderFactory;
import io.github.admiralxy.agent.service.file.FileTrees;
import io.github.admiralxy.agent.service.git.GitFile;
import io.github.admiralxy.agent.service.git.GitRepositoryInfo;
import io.github.admiralxy.agent.service.git.GitRepositoryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory;
import org.eclipse.jgit.transport.http.NoCheckX509TrustManager;
import org.springframework.stereotype.Service;

import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class GitRepositoryServiceImpl implements GitRepositoryService {

    private static final String HEADS_PREFIX = "refs/heads/";
    private static final String GIT_DIRECTORY = ".git";
    private static final long MAX_FILE_BYTES = 512 * 1024;
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
            GIT_DIRECTORY, "node_modules", ".gradle", ".idea", ".vscode", ".mvn", "target", "build", "dist", "out", ".next",
            ".nuxt", ".output", "vendor", "__pycache__", ".venv", "venv", "coverage"
    );
    private static final Set<String> SKIPPED_FILES = Set.of(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "bun.lockb", "composer.lock", "poetry.lock",
            "cargo.lock", "gemfile.lock", "go.sum", "gradlew", "gradlew.bat", "mvnw", "mvnw.cmd"
    );
    private static final List<String> SKIPPED_SUFFIXES = List.of(
            ".min.js", ".min.css", ".map", ".lock", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".webp", ".bmp",
            ".svg", ".pdf", ".zip", ".gz", ".tar", ".jar", ".war", ".class", ".so", ".dll", ".exe", ".woff",
            ".woff2", ".ttf", ".eot", ".mp3", ".mp4", ".mov", ".bin"
    );
    private static final HttpConnectionFactory INSECURE_HTTP_CONNECTION_FACTORY = new InsecureHttpConnectionFactory();
    private static final String INFO_TEMP_PREFIX = "rag-git-info-";
    private static final String FILES_TEMP_PREFIX = "rag-git-doc-";
    private static final String SEPARATOR = "/";
    private static final String WINDOWS_SEPARATOR = "\\";
    private static final int BINARY_PROBE_BYTES = 1024;
    private static final String INSPECT_FAILED = "Failed to inspect git repository";
    private static final String BRANCH_REQUIRED = "Branch is required for git provider";
    private static final String LOAD_FAILED = "Failed to load files from git repository";
    private static final String BRANCHES_FAILED = "Failed to fetch branches from git repository";
    private static final String CLONE_FAILED = "Failed to clone git repository";
    private static final String URL_REQUIRED = "Repository URL is required";
    private static final String TOP_LEVEL_ONLY = "Only top-level folder is allowed";
    private static final String FOLDER_NOT_FOUND = "Top-level folder not found: %s";
    private static final String READ_FAILED = "Failed to read file: %s";
    private static final String INSECURE_SSL_FAILED = "Failed to configure insecure SSL for JGit HTTP transport";

    private final AiHttpClientBuilderFactory httpClientBuilderFactory;

    @Override
    public GitRepositoryInfo getRepositoryInfo(String repositoryUrl, String login, String password) {
        validateRepositoryUrl(repositoryUrl);
        CredentialsProvider credentialsProvider = credentialsProvider(login, password);

        List<String> branches = loadBranches(repositoryUrl, credentialsProvider);
        return withClone(repositoryUrl, null, credentialsProvider, INFO_TEMP_PREFIX, INSPECT_FAILED,
                root -> new GitRepositoryInfo(branches, listTopLevelFolders(root)));
    }

    @Override
    public List<GitFile> getFiles(String repositoryUrl, String branch, String folder, String login, String password) {
        validateRepositoryUrl(repositoryUrl);
        if (StringUtils.isBlank(branch)) {
            throw new IllegalArgumentException(BRANCH_REQUIRED);
        }
        validateFolder(folder);
        return withClone(repositoryUrl, branch, credentialsProvider(login, password), FILES_TEMP_PREFIX, LOAD_FAILED,
                root -> listFiles(root, resolveSourceRoot(root, folder)));
    }

    private <T> T withClone(String repositoryUrl, String branch, CredentialsProvider credentialsProvider,
                            String tempPrefix, String failure, CloneAction<T> action) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory(tempPrefix);
            cloneRepository(repositoryUrl, branch, tempDir, credentialsProvider);
            return action.apply(tempDir);
        } catch (IOException e) {
            throw new IllegalStateException(failure, e);
        } finally {
            FileTrees.deleteQuietly(tempDir);
        }
    }

    private List<GitFile> listFiles(Path repositoryRoot, Path sourceRoot) throws IOException {
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> isIndexable(path, repositoryRoot))
                    .sorted(Comparator.comparing(path -> repositoryRoot.relativize(path).toString()))
                    .map(path -> toFile(path, repositoryRoot))
                    .filter(file -> file != null && StringUtils.isNotBlank(file.content()))
                    .toList();
        }
    }

    @FunctionalInterface
    private interface CloneAction<T> {
        T apply(Path root) throws IOException;
    }

    private List<String> loadBranches(String repositoryUrl, CredentialsProvider credentialsProvider) {
        try {
            LsRemoteCommand command = Git.lsRemoteRepository()
                    .setHeads(true)
                    .setRemote(repositoryUrl);
            configure(command, credentialsProvider);
            return command.call().stream()
                    .map(Ref::getName)
                    .filter(name -> name.startsWith(HEADS_PREFIX))
                    .map(name -> name.substring(HEADS_PREFIX.length()))
                    .sorted()
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException(BRANCHES_FAILED, e);
        }
    }

    private void cloneRepository(String repositoryUrl, String branch, Path tempDir,
                                 CredentialsProvider credentialsProvider) {
        try {
            CloneCommand command = Git.cloneRepository()
                    .setURI(repositoryUrl)
                    .setDirectory(tempDir.toFile())
                    .setCloneAllBranches(false)
                    .setDepth(1);
            configure(command, credentialsProvider);
            if (StringUtils.isNotBlank(branch)) {
                command.setBranch(branch);
            }
            command.call().close();
        } catch (GitAPIException e) {
            throw new IllegalStateException(CLONE_FAILED, e);
        }
    }

    private List<String> listTopLevelFolders(Path root) throws IOException {
        try (Stream<Path> stream = Files.list(root)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> !GIT_DIRECTORY.equals(path.getFileName().toString()))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    private void validateRepositoryUrl(String repositoryUrl) {
        if (StringUtils.isBlank(repositoryUrl)) {
            throw new IllegalArgumentException(URL_REQUIRED);
        }
    }

    private void validateFolder(String folder) {
        if (StringUtils.isBlank(folder)) {
            return;
        }
        if (folder.contains(SEPARATOR) || folder.contains(WINDOWS_SEPARATOR)) {
            throw new IllegalArgumentException(TOP_LEVEL_ONLY);
        }
    }

    private Path resolveSourceRoot(Path root, String folder) {
        if (StringUtils.isBlank(folder)) {
            return root;
        }
        Path folderPath = root.resolve(folder).normalize();
        if (!folderPath.startsWith(root) || !Files.isDirectory(folderPath)) {
            throw new IllegalArgumentException(FOLDER_NOT_FOUND.formatted(folder));
        }
        return folderPath;
    }

    private GitFile toFile(Path filePath, Path repositoryRoot) {
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            if (isProbablyBinary(bytes)) {
                return null;
            }
            String relativePath = repositoryRoot.relativize(filePath).toString().replace(WINDOWS_SEPARATOR, SEPARATOR);
            return new GitFile(relativePath, new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(READ_FAILED.formatted(filePath), e);
        }
    }

    private boolean isIndexable(Path filePath, Path repositoryRoot) {
        Path relative = repositoryRoot.relativize(filePath);
        for (Path segment : relative) {
            if (SKIPPED_DIRECTORIES.contains(segment.toString())) {
                return false;
            }
        }
        String name = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (SKIPPED_FILES.contains(name) || SKIPPED_SUFFIXES.stream().anyMatch(name::endsWith)) {
            return false;
        }
        try {
            return Files.size(filePath) <= MAX_FILE_BYTES;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isProbablyBinary(byte[] bytes) {
        int limit = Math.min(bytes.length, BINARY_PROBE_BYTES);
        for (int i = 0; i < limit; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private CredentialsProvider credentialsProvider(String login, String password) {
        if (StringUtils.isBlank(login)) {
            return null;
        }
        return new UsernamePasswordCredentialsProvider(login, StringUtils.defaultString(password));
    }

    private void configure(TransportCommand<?, ?> command, CredentialsProvider credentialsProvider) {
        if (httpClientBuilderFactory.isInsecureSslEnabled()) {
            command.setTransportConfigCallback(this::configureTransport);
        }
        if (credentialsProvider != null) {
            command.setCredentialsProvider(credentialsProvider);
        }
    }

    private void configureTransport(Transport transport) {
        if (transport instanceof TransportHttp transportHttp) {
            transportHttp.setHttpConnectionFactory(INSECURE_HTTP_CONNECTION_FACTORY);
        }
    }

    private static final class InsecureHttpConnectionFactory implements HttpConnectionFactory {

        private static final TrustManager[] TRUST_ALL_MANAGERS = new TrustManager[]{new NoCheckX509TrustManager()};
        private final JDKHttpConnectionFactory delegate = new JDKHttpConnectionFactory();

        @Override
        public HttpConnection create(URL url) throws IOException {
            HttpConnection connection = delegate.create(url);
            configure(connection);
            return connection;
        }

        @Override
        public HttpConnection create(URL url, Proxy proxy) throws IOException {
            HttpConnection connection = delegate.create(url, proxy);
            configure(connection);
            return connection;
        }

        private void configure(HttpConnection connection) {
            try {
                connection.configure(null, TRUST_ALL_MANAGERS, new SecureRandom());
                connection.setHostnameVerifier((ignoredHost, ignoredSession) -> true);
            } catch (NoSuchAlgorithmException | KeyManagementException ex) {
                throw new IllegalStateException(INSECURE_SSL_FAILED, ex);
            }
        }
    }
}
