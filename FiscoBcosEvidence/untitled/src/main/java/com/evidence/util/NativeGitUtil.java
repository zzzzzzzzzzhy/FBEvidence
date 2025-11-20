package com.evidence.util;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class NativeGitUtil {

    @Value("${git.base-path:/data/git-repos}")
    private String gitBasePath;

    @Value("${git.username:#{null}}")
    private String gitUsername;

    @Value("${git.password:#{null}}")
    private String gitPassword;

    public static class GitCommitInfo {
        private String commitHash;
        private String message;
        private String authorName;
        private String authorEmail;
        private LocalDateTime commitTime;

        public GitCommitInfo(String commitHash, String message, String authorName, String authorEmail, LocalDateTime commitTime) {
            this.commitHash = commitHash;
            this.message = message;
            this.authorName = authorName;
            this.authorEmail = authorEmail;
            this.commitTime = commitTime;
        }

        public String getCommitHash() { return commitHash; }
        public String getMessage() { return message; }
        public String getAuthorName() { return authorName; }
        public String getAuthorEmail() { return authorEmail; }
        public LocalDateTime getCommitTime() { return commitTime; }
    }

    public static class GitFileInfo {
        private String fileName;
        private String fileHash;
        private long fileSize;
        private String content;

        public GitFileInfo(String fileName, String fileHash, long fileSize, String content) {
            this.fileName = fileName;
            this.fileHash = fileHash;
            this.fileSize = fileSize;
            this.content = content;
        }

        public String getFileName() { return fileName; }
        public String getFileHash() { return fileHash; }
        public long getFileSize() { return fileSize; }
        public String getContent() { return content; }
    }

    public String initRepository(String groupName, String projectName, String description) throws GitAPIException, IOException {
        String repoPath = buildRepositoryPath(groupName, projectName);
        File repoDir = new File(repoPath);

        if (repoDir.exists()) {
            log.warn("Repository directory already exists: {}. This may indicate an orphaned directory from a previous failed operation.", repoPath);
            
            // Check if this is a valid Git repository
            if (isValidGitRepository(repoPath)) {
                log.warn("Found existing valid Git repository at: {}. This suggests data inconsistency.", repoPath);
                throw new IllegalStateException("Repository directory exists with valid Git data, but may not be registered in database: " + repoPath);
            } else {
                // Directory exists but is not a valid Git repository, clean it up
                log.info("Cleaning up orphaned directory: {}", repoPath);
                try {
                    org.apache.commons.io.FileUtils.deleteDirectory(repoDir);
                    log.info("Successfully cleaned up orphaned directory: {}", repoPath);
                } catch (Exception e) {
                    log.error("Failed to clean up orphaned directory: {}, error: {}", repoPath, e.getMessage());
                    throw new RuntimeException("Cannot clean up existing directory: " + repoPath, e);
                }
            }
        }

        Files.createDirectories(Paths.get(repoPath));

        try (Git git = Git.init().setDirectory(repoDir).call()) {
            createInitialCommit(git, description);
            log.info("Repository initialized successfully: {}", repoPath);
            return repoPath;
        }
    }

    public String createBranch(String repoPath, String branchName, String baseBranch) throws GitAPIException, IOException {
        try (Git git = Git.open(new File(repoPath))) {
            Repository repository = git.getRepository();
            
            log.info("Creating branch: {} in repository: {}", branchName, repoPath);
            boolean hasCommits = hasAnyCommits(repository);
            log.info("Repository has commits: {}", hasCommits);
            
            // Check if we're creating the main branch for a new repository
            if ("main".equals(branchName) && !hasCommits) {
                // For new repositories, the main branch will be created automatically with the first commit
                log.info("Main branch will be created automatically with first commit");
                return branchName;
            }
            
            // Check if the branch already exists
            boolean branchAlreadyExists = branchExists(git, branchName);
            log.info("Branch {} already exists: {}", branchName, branchAlreadyExists);
            
            if (branchAlreadyExists) {
                throw new IllegalArgumentException("Branch already exists: " + branchName);
            }
            
            if (!StringUtils.hasText(baseBranch)) {
                baseBranch = "main";
            }
            
            // For non-main branches, verify the base branch exists
            if (!"main".equals(branchName)) {
                boolean baseBranchExists = branchExists(git, baseBranch);
                log.info("Base branch {} exists: {}", baseBranch, baseBranchExists);
                
                if (!baseBranchExists) {
                    throw new IllegalArgumentException("Base branch does not exist: " + baseBranch);
                }
                git.checkout()
                    .setCreateBranch(true)
                    .setName(branchName)
                    .setStartPoint(baseBranch)
                    .call();
            } else {
                // For main branch, create without specifying startPoint if repo has no commits
                if (hasCommits) {
                    log.info("Creating main branch from existing commit");
                    git.checkout()
                        .setCreateBranch(true)
                        .setName(branchName)
                        .setStartPoint(baseBranch)
                        .call();
                } else {
                    // For new repo, we can't create main branch until first commit
                    // Just return, the branch will be created automatically with first commit
                    log.info("Main branch will be created automatically with first commit in new repository");
                    return branchName;
                }
            }

            log.info("Branch created successfully: {} from {}", branchName, baseBranch);
            return branchName;
        }
    }

    public List<String> listBranches(String repoPath) throws GitAPIException, IOException {
        try (Git git = Git.open(new File(repoPath))) {
            List<String> branches = new ArrayList<>();
            List<Ref> branchList = git.branchList().call();
            
            for (Ref ref : branchList) {
                String branchName = ref.getName();
                if (branchName.startsWith("refs/heads/")) {
                    branchName = branchName.substring("refs/heads/".length());
                }
                branches.add(branchName);
            }
            
            return branches;
        }
    }

    public GitCommitInfo addAndCommit(String repoPath, String fileName, String content, String commitMessage, String authorName, String authorEmail) throws GitAPIException, IOException {
        try (Git git = Git.open(new File(repoPath))) {
            File targetFile = new File(repoPath, fileName);
            Files.createDirectories(targetFile.getParentFile().toPath());

            try (FileWriter writer = new FileWriter(targetFile)) {
                writer.write(content);
            }

            git.add().addFilepattern(fileName).call();

            RevCommit commit = git.commit()
                .setMessage(commitMessage)
                .setAuthor(authorName, authorEmail)
                .call();

            LocalDateTime commitTime = LocalDateTime.ofInstant(
                new Date(commit.getCommitTime() * 1000L).toInstant(),
                ZoneId.systemDefault()
            );

            GitCommitInfo commitInfo = new GitCommitInfo(
                commit.getName(),
                commit.getFullMessage(),
                commit.getAuthorIdent().getName(),
                commit.getAuthorIdent().getEmailAddress(),
                commitTime
            );

            log.info("File committed successfully: {} in {}", fileName, repoPath);
            return commitInfo;
        }
    }

    public GitCommitInfo addAndCommitMultiple(String repoPath, Map<String, String> fileContentMap, String commitMessage, String authorName, String authorEmail) throws GitAPIException, IOException {
        try (Git git = Git.open(new File(repoPath))) {
            List<String> addedFiles = new ArrayList<>();
            
            for (Map.Entry<String, String> entry : fileContentMap.entrySet()) {
                String fileName = entry.getKey();
                String content = entry.getValue();
                
                File targetFile = new File(repoPath, fileName);
                Files.createDirectories(targetFile.getParentFile().toPath());

                try (FileWriter writer = new FileWriter(targetFile)) {
                    writer.write(content);
                }
                
                git.add().addFilepattern(fileName).call();
                addedFiles.add(fileName);
            }

            RevCommit commit = git.commit()
                .setMessage(commitMessage)
                .setAuthor(authorName, authorEmail)
                .call();

            LocalDateTime commitTime = LocalDateTime.ofInstant(
                new Date(commit.getCommitTime() * 1000L).toInstant(),
                ZoneId.systemDefault()
            );

            GitCommitInfo commitInfo = new GitCommitInfo(
                commit.getName(),
                commit.getFullMessage(),
                commit.getAuthorIdent().getName(),
                commit.getAuthorIdent().getEmailAddress(),
                commitTime
            );

            log.info("Multiple files committed successfully: {} in {}", addedFiles, repoPath);
            return commitInfo;
        }
    }

    public GitCommitInfo mergeBranch(String repoPath, String sourceBranch, String targetBranch, String authorName, String authorEmail) throws GitAPIException, IOException {
        try (Git git = Git.open(new File(repoPath))) {
            Repository repository = git.getRepository();
            
            log.info("Starting merge: {} -> {} in repository: {}", sourceBranch, targetBranch, repoPath);
            
            // 验证分支存在
            if (!branchExists(git, sourceBranch)) {
                throw new IllegalArgumentException("Source branch does not exist: " + sourceBranch);
            }
            if (!branchExists(git, targetBranch)) {
                throw new IllegalArgumentException("Target branch does not exist: " + targetBranch);
            }
            
            // 切换到目标分支
            git.checkout().setName(targetBranch).call();
            
            // 获取源分支的引用
            Ref sourceBranchRef = repository.findRef("refs/heads/" + sourceBranch);
            if (sourceBranchRef == null) {
                throw new IllegalArgumentException("Cannot find reference for source branch: " + sourceBranch);
            }
            
            // 执行合并
            org.eclipse.jgit.api.MergeCommand mergeCommand = git.merge()
                .include(sourceBranchRef)
                .setCommit(true)
                .setFastForward(org.eclipse.jgit.api.MergeCommand.FastForwardMode.FF);
            
            org.eclipse.jgit.api.MergeResult mergeResult = mergeCommand.call();
            
            if (mergeResult.getMergeStatus().isSuccessful()) {
                // 获取合并后的提交信息
                ObjectId mergeCommitId = mergeResult.getNewHead();
                
                if (mergeCommitId != null) {
                    try (RevWalk revWalk = new RevWalk(repository)) {
                        RevCommit mergeCommit = revWalk.parseCommit(mergeCommitId);
                        
                        LocalDateTime commitTime = LocalDateTime.ofInstant(
                            new Date(mergeCommit.getCommitTime() * 1000L).toInstant(),
                            ZoneId.systemDefault()
                        );

                        GitCommitInfo commitInfo = new GitCommitInfo(
                            mergeCommit.getName(),
                            mergeCommit.getFullMessage(),
                            mergeCommit.getAuthorIdent().getName(),
                            mergeCommit.getAuthorIdent().getEmailAddress(),
                            commitTime
                        );
                        
                        log.info("Branch merge completed successfully: {} -> {} with commit {}", 
                                sourceBranch, targetBranch, commitInfo.getCommitHash());
                        return commitInfo;
                    }
                } else {
                    // 快进合并，没有新的提交，获取当前HEAD
                    return getLatestCommit(repoPath, targetBranch);
                }
            } else {
                String errorMsg = String.format("Branch merge failed: %s -> %s, status: %s", 
                    sourceBranch, targetBranch, mergeResult.getMergeStatus());
                
                if (mergeResult.getConflicts() != null && !mergeResult.getConflicts().isEmpty()) {
                    errorMsg += ", conflicts: " + mergeResult.getConflicts().keySet();
                }
                
                log.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }
        }
    }

    public GitCommitInfo getLatestCommit(String repoPath, String branchName) throws GitAPIException, IOException {
        try (Git git = Git.open(new File(repoPath))) {
            Repository repository = git.getRepository();
            
            log.debug("Getting latest commit for branch '{}' in repository: {}", branchName, repoPath);
            
            // Check if repository has any commits
            if (!hasAnyCommits(repository)) {
                throw new IllegalStateException("Repository has no commits yet");
            }
            
            // Determine which branch/ref to use
            String refName = "HEAD"; // Default to HEAD
            if (StringUtils.hasText(branchName)) {
                // Check if branch exists
                if (!branchExists(git, branchName)) {
                    throw new IllegalArgumentException("Branch does not exist: " + branchName);
                }
                refName = "refs/heads/" + branchName;
            }
            
            // Get commit directly through repository resolve (more reliable than git.log())
            ObjectId commitId = repository.resolve(refName);
            if (commitId == null) {
                throw new IllegalStateException("Cannot resolve commit for: " + refName);
            }
            
            // Parse the commit using RevWalk
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(commitId);
                
                LocalDateTime commitTime = LocalDateTime.ofInstant(
                    new Date(commit.getCommitTime() * 1000L).toInstant(),
                    ZoneId.systemDefault()
                );

                GitCommitInfo commitInfo = new GitCommitInfo(
                    commit.getName(),
                    commit.getFullMessage(),
                    commit.getAuthorIdent().getName(),
                    commit.getAuthorIdent().getEmailAddress(),
                    commitTime
                );
                
                log.debug("Successfully retrieved commit info: {} for branch '{}'", commitInfo.getCommitHash(), branchName);
                return commitInfo;
            }
        }
    }

    public GitFileInfo getFileFromCommit(String repoPath, String commitHash, String fileName) throws GitAPIException, IOException {
        try (Git git = Git.open(new File(repoPath))) {
            Repository repository = git.getRepository();
            ObjectId commitId = repository.resolve(commitHash);

            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(commitId);
                RevTree tree = commit.getTree();

                try (TreeWalk treeWalk = new TreeWalk(repository)) {
                    treeWalk.addTree(tree);
                    treeWalk.setRecursive(true);
                    treeWalk.setFilter(PathFilter.create(fileName));

                    if (treeWalk.next()) {
                        ObjectId objectId = treeWalk.getObjectId(0);
                        ObjectLoader loader = repository.open(objectId);
                        
                        String content = new String(loader.getBytes());
                        String fileHash = objectId.getName();
                        long fileSize = loader.getSize();

                        return new GitFileInfo(fileName, fileHash, fileSize, content);
                    }
                }
            }
        }
        return null;
    }

    public boolean pushToRemote(String repoPath, String remoteUrl, String branch) throws GitAPIException, IOException {
        try (Git git = Git.open(new File(repoPath))) {
            if (!hasRemote(git, "origin")) {
                RemoteAddCommand remoteAdd = git.remoteAdd();
                remoteAdd.setName("origin");
                remoteAdd.setUri(new URIish(remoteUrl));
                remoteAdd.call();
            }

            PushCommand pushCommand = git.push()
                .setRemote("origin")
                .add(branch);

            if (StringUtils.hasText(gitUsername) && StringUtils.hasText(gitPassword)) {
                pushCommand.setCredentialsProvider(
                    new UsernamePasswordCredentialsProvider(gitUsername, gitPassword)
                );
            }

            Iterable<PushResult> results = pushCommand.call();
            
            for (PushResult result : results) {
                if (!result.getMessages().isEmpty()) {
                    log.warn("Push result messages: {}", result.getMessages());
                }
            }

            log.info("Successfully pushed branch {} to remote {}", branch, remoteUrl);
            return true;
        } catch (Exception e) {
            log.error("Failed to push to remote: {}", e.getMessage());
            return false;
        }
    }

    public Git cloneRepository(String remoteUrl, String localPath) throws GitAPIException {
        CloneCommand cloneCommand = Git.cloneRepository()
            .setURI(remoteUrl)
            .setDirectory(new File(localPath));

        if (StringUtils.hasText(gitUsername) && StringUtils.hasText(gitPassword)) {
            cloneCommand.setCredentialsProvider(
                new UsernamePasswordCredentialsProvider(gitUsername, gitPassword)
            );
        }

        return cloneCommand.call();
    }

    public Map<String, Object> getRepositoryStatus(String repoPath) throws GitAPIException, IOException {
        try (Git git = Git.open(new File(repoPath))) {
            Map<String, Object> status = new HashMap<>();
            
            String currentBranch = git.getRepository().getBranch();
            status.put("currentBranch", currentBranch);
            
            List<String> branches = listBranches(repoPath);
            status.put("branches", branches);
            
            GitCommitInfo latestCommit = getLatestCommit(repoPath, null);
            status.put("latestCommit", latestCommit);
            
            org.eclipse.jgit.api.Status gitStatus = git.status().call();
            status.put("hasChanges", !gitStatus.isClean());
            status.put("modifiedFiles", gitStatus.getModified());
            status.put("untrackedFiles", gitStatus.getUntracked());
            
            return status;
        }
    }

    private String buildRepositoryPath(String groupName, String projectName) {
        return Paths.get(gitBasePath, groupName, projectName).toString();
    }
    
    public String getGitBasePath() {
        return gitBasePath;
    }

    private void createInitialCommit(Git git, String description) throws GitAPIException, IOException {
        File readmeFile = new File(git.getRepository().getDirectory().getParentFile(), "README.md");
        
        String readmeContent = String.format("# %s\n\n%s\n\nCreated at: %s",
            git.getRepository().getDirectory().getParentFile().getName(),
            StringUtils.hasText(description) ? description : "Auto-generated repository",
            LocalDateTime.now().toString()
        );

        log.info("Creating initial commit with README.md in repository: {}", 
                git.getRepository().getDirectory().getParentFile().getAbsolutePath());

        try (FileWriter writer = new FileWriter(readmeFile)) {
            writer.write(readmeContent);
        }

        git.add().addFilepattern("README.md").call();
        RevCommit initialCommit = git.commit()
            .setMessage("Initial commit")
            .setAuthor("System", "system@evidence.local")
            .call();
        
        log.info("Initial commit created successfully: {} in repository: {}", 
                initialCommit.getName(), git.getRepository().getDirectory().getParentFile().getName());
        
        // Check current branch and rename to main if necessary
        try {
            String currentBranch = git.getRepository().getBranch();
            log.info("Current branch after initial commit: {}", currentBranch);
            
            // If the current branch is not 'main', rename it to 'main'
            if (!"main".equals(currentBranch)) {
                log.info("Renaming branch from '{}' to 'main'", currentBranch);
                
                // Create main branch from current HEAD
                git.checkout()
                    .setCreateBranch(true)
                    .setName("main")
                    .setStartPoint("HEAD")
                    .call();
                
                // Delete the old branch (master)
                if ("master".equals(currentBranch)) {
                    try {
                        git.branchDelete()
                            .setBranchNames("master")
                            .setForce(true)
                            .call();
                        log.info("Deleted old master branch");
                    } catch (Exception e) {
                        log.warn("Failed to delete old master branch: {}", e.getMessage());
                    }
                }
                
                currentBranch = "main";
                
                // Wait briefly for Git state to stabilize after branch operations
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // Verify the main branch state is stable
                validateMainBranchState(git);
            }
            
            // List all branches to confirm main branch exists
            List<Ref> branches = git.branchList().call();
            log.info("Available branches after branch setup: {}", 
                    branches.stream().map(ref -> ref.getName()).collect(java.util.stream.Collectors.toList()));
                    
        } catch (Exception e) {
            log.error("Failed to setup main branch: {}", e.getMessage());
            throw new RuntimeException("Failed to setup main branch: " + e.getMessage(), e);
        }
    }

    private boolean hasRemote(Git git, String remoteName) throws GitAPIException {
        return git.remoteList().call().stream()
            .anyMatch(remote -> remote.getName().equals(remoteName));
    }
    
    private boolean hasAnyCommits(Repository repository) throws IOException {
        try {
            ObjectId head = repository.resolve("HEAD");
            log.debug("Checking commits in repository {}, HEAD: {}", 
                     repository.getDirectory().getParentFile().getName(), head);
            
            if (head == null) {
                log.debug("HEAD is null, no commits found");
                return false;
            }
            // Check if HEAD points to a valid commit (not just a symbolic ref)
            try (RevWalk revWalk = new RevWalk(repository)) {
                revWalk.parseCommit(head);
                log.debug("Found valid commit: {}", head.getName());
                return true;
            } catch (Exception e) {
                log.debug("HEAD exists but no valid commit found: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.warn("Error checking for commits: {}", e.getMessage());
            return false;
        }
    }
    
    private boolean branchExists(Git git, String branchName) throws GitAPIException {
        List<Ref> branches = git.branchList().call();
        return branches.stream()
            .anyMatch(ref -> ref.getName().equals("refs/heads/" + branchName));
    }
    
    private void validateMainBranchState(Git git) throws GitAPIException, IOException {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                // Check if main branch exists
                if (!branchExists(git, "main")) {
                    throw new IllegalStateException("Main branch does not exist after creation");
                }
                
                // Check if we can get commits from main branch
                git.checkout().setName("main").call();
                Iterable<RevCommit> commits = git.log().setMaxCount(1).call();
                if (!commits.iterator().hasNext()) {
                    throw new IllegalStateException("Main branch has no commits");
                }
                
                log.info("Main branch state validation successful on attempt {}", i + 1);
                return;
                
            } catch (Exception e) {
                log.warn("Main branch validation failed on attempt {} of {}: {}", i + 1, maxRetries, e.getMessage());
                if (i == maxRetries - 1) {
                    throw new RuntimeException("Failed to validate main branch state after " + maxRetries + " attempts", e);
                }
                
                // Wait before retry
                try {
                    Thread.sleep(50 * (i + 1)); // Progressive delay: 50ms, 100ms, 150ms
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during main branch validation", ie);
                }
            }
        }
    }
    
    private boolean isValidGitRepository(String repoPath) {
        try {
            File repoDir = new File(repoPath);
            
            // Check if .git directory exists
            File gitDir = new File(repoDir, ".git");
            if (!gitDir.exists() || !gitDir.isDirectory()) {
                log.debug("No .git directory found in: {}", repoPath);
                return false;
            }
            
            // Try to open as Git repository
            try (Git git = Git.open(repoDir)) {
                Repository repository = git.getRepository();
                
                // Check if repository has any commits
                boolean hasCommits = hasAnyCommits(repository);
                log.debug("Repository {} has commits: {}", repoPath, hasCommits);
                
                // Check if it has branches
                List<Ref> branches = git.branchList().call();
                boolean hasBranches = !branches.isEmpty();
                log.debug("Repository {} has branches: {}", repoPath, hasBranches);
                
                // Consider it valid if it has commits and branches
                return hasCommits && hasBranches;
                
            } catch (Exception e) {
                log.debug("Failed to validate Git repository {}: {}", repoPath, e.getMessage());
                return false;
            }
            
        } catch (Exception e) {
            log.debug("Error checking if {} is a valid Git repository: {}", repoPath, e.getMessage());
            return false;
        }
    }
}