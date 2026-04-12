package io.github.admiralxy.agent.service.git.impl;

import io.github.admiralxy.agent.service.git.GitRepositoryInfo;
import io.github.admiralxy.agent.service.git.GitRepositoryService;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class GitRepositoryServiceImpl implements GitRepositoryService {

    private static final String HEADS_PREFIX = "refs/heads/";

    @Override
    public GitRepositoryInfo getRepositoryInfo(String repositoryUrl, String login, String password) {
        validateRepositoryUrl(repositoryUrl);
        CredentialsProvider credentialsProvider = credentialsProvider(login, password);

        List<String> branches = loadBranches(repositoryUrl, credentialsProvider);
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("rag-git-info-");
            cloneRepository(repositoryUrl, null, tempDir, credentialsProvider);
            List<String> folders = listTopLevelFolders(tempDir);
            return new GitRepositoryInfo(branches, folders);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to inspect git repository", e);
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    @Override
    public List<String> getFileChunks(String repositoryUrl, String branch, String folder, String login, String password) {
        validateRepositoryUrl(repositoryUrl);
        if (StringUtils.isBlank(branch)) {
            throw new IllegalArgumentException("Branch is required for git provider");
        }
        validateFolder(folder);

        CredentialsProvider credentialsProvider = credentialsProvider(login, password);
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("rag-git-doc-");
            cloneRepository(repositoryUrl, branch, tempDir, credentialsProvider);

            Path repositoryRoot = tempDir;
            Path sourceRoot = resolveSourceRoot(repositoryRoot, folder);
            try (Stream<Path> stream = Files.walk(sourceRoot)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(path -> !isInsideGitDirectory(path, repositoryRoot))
                        .sorted(Comparator.comparing(path -> repositoryRoot.relativize(path).toString()))
                        .map(path -> toChunk(path, repositoryRoot))
                        .filter(StringUtils::isNotBlank)
                        .toList();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load files from git repository", e);
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    private List<String> loadBranches(String repositoryUrl, CredentialsProvider credentialsProvider) {
        try {
            LsRemoteCommand command = Git.lsRemoteRepository()
                    .setHeads(true)
                    .setRemote(repositoryUrl);
            if (credentialsProvider != null) {
                command.setCredentialsProvider(credentialsProvider);
            }
            return command.call().stream()
                    .map(Ref::getName)
                    .filter(name -> name.startsWith(HEADS_PREFIX))
                    .map(name -> name.substring(HEADS_PREFIX.length()))
                    .sorted()
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch branches from git repository", e);
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
            if (StringUtils.isNotBlank(branch)) {
                command.setBranch(branch);
            }
            if (credentialsProvider != null) {
                command.setCredentialsProvider(credentialsProvider);
            }
            command.call().close();
        } catch (GitAPIException e) {
            throw new IllegalStateException("Failed to clone git repository", e);
        }
    }

    private List<String> listTopLevelFolders(Path root) throws IOException {
        try (Stream<Path> stream = Files.list(root)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> !".git".equals(path.getFileName().toString()))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    private void validateRepositoryUrl(String repositoryUrl) {
        if (StringUtils.isBlank(repositoryUrl)) {
            throw new IllegalArgumentException("Repository URL is required");
        }
    }

    private void validateFolder(String folder) {
        if (StringUtils.isBlank(folder)) {
            return;
        }
        if (folder.contains("/") || folder.contains("\\")) {
            throw new IllegalArgumentException("Only top-level folder is allowed");
        }
    }

    private Path resolveSourceRoot(Path root, String folder) {
        if (StringUtils.isBlank(folder)) {
            return root;
        }
        Path folderPath = root.resolve(folder).normalize();
        if (!folderPath.startsWith(root) || !Files.isDirectory(folderPath)) {
            throw new IllegalArgumentException("Top-level folder not found: " + folder);
        }
        return folderPath;
    }

    private String toChunk(Path filePath, Path repositoryRoot) {
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            if (isProbablyBinary(bytes)) {
                return StringUtils.EMPTY;
            }
            String relativePath = repositoryRoot.relativize(filePath).toString().replace('\\', '/');
            String content = new String(bytes, StandardCharsets.UTF_8);
            return "File: " + relativePath + "\n\n" + content;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read file: " + filePath, e);
        }
    }

    private boolean isInsideGitDirectory(Path filePath, Path root) {
        Path relative = root.relativize(filePath);
        return relative.getNameCount() > 0 && ".git".equals(relative.getName(0).toString());
    }

    private boolean isProbablyBinary(byte[] bytes) {
        int limit = Math.min(bytes.length, 1024);
        for (int i = 0; i < limit; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Cleanup failure should not break business flow.
                        }
                    });
        } catch (IOException ignored) {
            // Cleanup failure should not break business flow.
        }
    }

    private CredentialsProvider credentialsProvider(String login, String password) {
        if (StringUtils.isBlank(login)) {
            return null;
        }
        return new UsernamePasswordCredentialsProvider(login, StringUtils.defaultString(password));
    }
}
