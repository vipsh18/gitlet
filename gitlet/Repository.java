package gitlet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static gitlet.Utils.*;

/** Represents a gitlet repository.
 *  This class contains most of the logic of Gitlet. Methods of this class are called
 *  from the Main class.
 *
 *  @author Vipul Sharma
 */
public class Repository {
    /** Checks overall directory structure and command validity. */
    public static void checkValidStructure(String[] args, int minArgs, int maxArgs) {
        checkIfGitletDirectory();
        checkValidArguments(args, minArgs, maxArgs);
        checkCorruptedRepository();
    }

    /** Exits if the directory is not a working Gitlet directory. */
    private static void checkIfGitletDirectory() {
        if (!GITLET_DIR.exists()) {
            exitWithError(NOT_GITLET_DIR, false);
        }
    }

    /** Exits if the repository has been corrupted. */
    private static void checkCorruptedRepository() {
        File[] commitObjectList = COMMIT_OBJECT_DIR.listFiles();

        if (Objects.requireNonNull(commitObjectList).length == 0) {
            exitWithError(CORRUPTED_REPO, false);
        }
    }

    /** Exits if wrong number of arguments are provided. */
    public static void checkValidArguments(String[] args, int minArgs, int maxArgs) {
        if (!(args.length >= minArgs && args.length <= maxArgs)) {
            minArgs -= 1;
            maxArgs -= 1;
            int argsProvided = args.length - 1;
            exitWithError(args[0] + " can be used with at least " + minArgs +
                    " argument(s) and at most " + maxArgs + " argument(s). Number of argument(s)" +
                    " provided: " + argsProvided, false);
        }
    }

    /******************************* INIT FUNCTION ****************************** //
     /** Initializes the repository. */
    public static void initRepo() {
        if (GITLET_DIR.exists()) {
	        System.out.println("A Gitlet version-control system already exists in the current directory.");
            return;
        }
        if (!GITLET_DIR.mkdir() || !BRANCH_TRACKING.mkdir()) {
            System.out.println("Could not initialize Gitlet directory.");
            return;
        }
	    System.out.println("Initialized empty repository in " + CWD + FILE_SEPARATOR + ".gitlet" + FILE_SEPARATOR);

        Commit initialCommit = new Commit("initial commit", null);
        Branch masterBranch = new Branch("master", initialCommit, true);

        System.out.println("Created an initial commit with the hash: " + initialCommit.getHash());
    }

    /******************************* ADD FUNCTION ****************************** //
     /** Checks if the file exists and calls the function that adds the file to the staging area. */
    public static void add(String fileName, boolean verbose) {
        File fileToAdd = new File(fileName);

        if (!fileToAdd.isFile()) {
            exitWithError("File does not exist.", false);
        } else {
            addFile(fileName, fileToAdd, verbose);
        }
    }

    /******************************* COMMIT FUNCTION ****************************** //
    /** Create a commit object. */
    public static void commit(String message) {
        if ((INDEX_FILE.exists() && FileStager.getNumberOfStagedFiles() >= 1) || UNTRACKING_FILE.exists()) {
            String[] branchAndCommit = Branch.getBranchAndItsHead();
            Commit newCommit = Commit.cloneAndModifyCommit(branchAndCommit[1], message,
                    "", false);

            Commit.afterCommit(branchAndCommit[0], newCommit.getHash(), message);
        } else {
            System.out.println(NO_CHANGES_COMMIT);
        }
    }

    /******************************* LOG FUNCTION ****************************** //
     /** Prepares a log of all commits on this branch. */
    public static void logCommits() {
        String[] branchAndCommit = Branch.getBranchAndItsHead();
        String commitHash = branchAndCommit[1];

        // Prints out the current branch name.
        System.out.println("On branch " + branchAndCommit[0]);

		Commit.logCommitsInfo(commitHash);
    }

    /******************************* REMOVE FUNCTION ****************************** //
     /** Un-stages the file if it is currently staged for addition. If the file is tracked in the
     current commit, stages it for removal and removes the file from the working directory if the
     user has not already done so. */
    public static void rm(String fileRelativePath) {
        boolean isFileStaged = FileStager.isFileStaged(fileRelativePath);
        boolean isFileTrackedVar = isFileNameInFile(TRACKING_FILE, fileRelativePath);

        if (!isFileTrackedVar && !isFileStaged) {
            System.out.println("No reason to remove the file.");
            return;
        }

        if (isFileStaged) {
            FileStager.removeFileFromStagingArea(fileRelativePath);
        }
        if (isFileTrackedVar) {
			FileStager.unstageTrackedFile(fileRelativePath);
        }

        removeFileNameFromFile(TRACKING_FILE, fileRelativePath);
        System.out.println("Removed " + fileRelativePath);
    }

    /******************************* GLOBAL LOG FUNCTION ****************************** //
     /** Like log displays list of commits, just on all branches, in random order. */
    public static void globalLog() {
        File[] commitObjectList = COMMIT_OBJECT_DIR.listFiles();

        if (commitObjectList != null) {
            for (File commitFile : commitObjectList) {
                Commit commitObject = readObject(commitFile, Commit.class);

                System.out.println(TRIPLE_EQUALS + "\ncommit " + commitObject.getHash() + "\nDate: "
                        + commitObject.getDate() + "\n" + commitObject.getMessage() + "\n");
            }
        }
    }

    /******************************* FIND FUNCTION ****************************** //
     /** Prints the ids of all the commits that have the given message. */
    public static void find(String commitMsg) {
        File[] commitObjectList = COMMIT_OBJECT_DIR.listFiles();
        boolean foundCommitWithMessage = false;

        for (File commitFile : Objects.requireNonNull(commitObjectList)) {
            if (doesCommitMessageMatch(commitFile, commitMsg)) {
                foundCommitWithMessage = true;
            }
        }

        if (!foundCommitWithMessage) {
            System.out.println("Found no commit with that message.");
        }
    }

    /******************************* STATUS FUNCTION ****************************** //
     /** Crafts a status message for current Gitlet repository. */
    public static void status() {
        System.out.printf("%s Branches (* denotes current branch) %s%n", TRIPLE_EQUALS, TRIPLE_EQUALS);
        Branch.listBranches();

        System.out.printf("%n%s Staged Files %s%n", TRIPLE_EQUALS, TRIPLE_EQUALS);
        FileStager.listStagedFiles();

        System.out.printf("%n%s Removed Files %s%n", TRIPLE_EQUALS, TRIPLE_EQUALS);
        listRemovedFiles();

        System.out.printf("%n%s Modifications Not Staged For Commit %s%n", TRIPLE_EQUALS, TRIPLE_EQUALS);
        listModifiedButNotStagedFiles();

        System.out.printf("%n%s Untracked Files %s%n", TRIPLE_EQUALS, TRIPLE_EQUALS);
        listUntrackedFiles();
    }

    /******************************* CHECKOUT FUNCTION ****************************** //
     /** Checks out i.e. brings into the working directory, either a file (from either the
     * head commit or from some previous mentioned commit) or a branch depending on arguments
     * provided. Usages:
     * 1. java gitlet.Main checkout -- [file name]
     * 2. java gitlet.Main checkout [commit id] -- [file name]
     * 3. java gitlet.Main checkout [branch name]
     * */
    public static void checkout(String[] args) {
        if (args.length == 2) {
            String branchName = args[1];
            Branch.checkoutBranch(branchName);
        } else if (args.length == 3 && args[1].equals("--")) {
            String fileRelativePath = args[2];
            checkoutFileFromCommit(fileRelativePath, null, true);
        } else if (args.length == 4 && args[2].equals("--")) {
            String commitHash = args[1];
            String fileRelativePath = args[3];
            checkoutFileFromCommit(fileRelativePath, commitHash, false);
        } else {
            System.out.println("Invalid use of checkout command. Valid usages:\n" + CHECKOUT_USAGES);
        }
    }

    /******************************* BRANCH FUNCTION ****************************** //
     /** Creates a new branch with given name and points it at the current head commit. */
    public static void branch(String branchName) {
        String headCommitHash = Branch.getCurrentBranchHeadHash();
        File commitFile = join(COMMIT_OBJECT_DIR, headCommitHash);
        Commit commitObject = readObject(commitFile, Commit.class);

        new Branch(branchName, commitObject, false);
    }

    /******************************** REMOVE BRANCH FUNCTION ******************************
     * Removes the branch with the given name. */
    public static void removeBranch(String branchName) {
        Branch.branchNameChecks(branchName, "remove");

        String headContent = headFileContentAfterRemovingBranch(branchName);
        writeContents(HEAD_FILE, headContent);

        System.out.println("Branch " + branchName + " removed successfully!");
    }

    /******************************** RESET FUNCTION ******************************
     * Checks out an arbitrary commit and also changes the current branch head. */
    public static void reset(String commitHash) {
        Commit checkoutCommit = Commit.getCommitFromHash(commitHash);
        List<String> untrackedFiles = getUntrackedFiles(CWD, new ArrayList<>());

        Map<String, String> filesOfCheckoutCommit = checkoutCommit.getStagedFilesCommit();
        checkPendingOrUntrackedChanges(untrackedFiles, filesOfCheckoutCommit);

		replaceFilesInWorkingDirectory(filesOfCheckoutCommit);
        FileStager.clearStagingArea();
		deleteTrackedFiles(filesOfCheckoutCommit);
        Branch.updateBranchHead(Commit.searchCommitUsingTruncatedHash(commitHash));

        System.out.println("Checked out " + Branch.getCurrentBranch() +  " to commit " +
                "[" + commitHash + "]");
    }

    /******************************* MERGE FUNCTION *****************************
     /** Merges the provided branch name with the current branch. */
    public static void merge(String mergingBranch) {
		Branch.branchNameChecks(mergingBranch, "merge");

        Commit currentBranchHeadCommit = Branch.getCurrentBranchHead();
        Map<String, String> currentBranchHeadFiles = currentBranchHeadCommit.getStagedFilesCommit();
        List<String> untrackedFiles = Repository.getUntrackedFiles(CWD, new ArrayList<>());
        checkPendingOrUntrackedChanges(untrackedFiles, currentBranchHeadFiles);

        String mergingBranchHeadHash = Branch.getHeadHashOfBranch(mergingBranch);
        String currentBranch = Branch.getCurrentBranch();
        String currentBranchHeadHash = currentBranchHeadCommit.getHash();

        String splitPoint = calculateSplitPoint(currentBranchHeadHash, mergingBranchHeadHash);

        Branch.mergeChecks(currentBranchHeadHash, mergingBranchHeadHash, currentBranch,
                mergingBranch, splitPoint, currentBranchHeadFiles);
    }

    /******************************* PRIVATE HELPER FUNCTIONS ****************************** //
     /** Adds the file to the staging area. */
    private static void addFile(String fileName, File fileToAdd, boolean verbose) {
        String fileRelativePath = getRelativePath(fileToAdd).toString();

        // calculate the hash
        byte[] contents = readContents(fileToAdd);
        String blobName = sha1(contents);

        checkAndStage(fileRelativePath, blobName, fileName, fileToAdd, verbose);
    }

    /** Adds filename to the contents of another file. */
    public static void addFileNameToFile(File file, String fileName) {
        if (!file.exists()) {
            writeContents(file, fileName);
        } else {
            writeContents(file, readContentsAsString(file) + "\n" + fileName);
        }
    }

    /** Returns the latest common ancestor of both the branches. */
    private static String calculateSplitPoint(String currentBranchHeadHash,
                                              String mergingBranchHeadHash) {
        // fetch all ancestors of current branch in order, the first one that matches any ancestor of
        // merging branch is the split point.

        List<String> currentBranchAncestors = Commit.getCommitAncestors(currentBranchHeadHash);
        List<String> mergingBranchAncestors = Commit.getCommitAncestors(mergingBranchHeadHash);

        for (String currentBranchAncestor: currentBranchAncestors) {
            if (mergingBranchAncestors.contains(currentBranchAncestor)) {
                return currentBranchAncestor;
            }
        }
        return "";
    }

    /** Returns true if a file can be overwritten when a branch is switched. This works by comparing the
     * files in CWD and the branch name given, if there is any file that is not being tracked currently
     * and is not present in the given branch, then that file can be overwritten. */
    private static boolean canAFileBeOverWritten(List<String> untrackedFiles,
                                                 Map<String, String> filesOfHeadCommitOfBranch) {
        for (var fileAndHash: filesOfHeadCommitOfBranch.entrySet()) {
            if (untrackedFiles.contains(fileAndHash.getKey())) {
                System.out.println(fileAndHash.getKey());
                return true;
            }
        }
        return false;
    }

    /** Checks if blob already exists, and calls methods that stage accordingly. */
    private static void checkAndStage(String fileRelativePath, String blobName,
                                      String fileName, File fileToAdd, boolean verbose) {
        File blobFile = join(GITLET_DIR, blobName);

        if (blobFile.exists()) {
            FileStager.stageIfFileExists(fileRelativePath, blobName, blobFile, fileToAdd, verbose);
        } else {
            FileStager.stageIfFileDoesNotExist(fileRelativePath, blobName, fileName, blobFile,
                    fileToAdd, verbose);
        }
    }

    /** Checks out file from either the head commit or given commit's hash. */
    private static void checkoutFileFromCommit(String fileRelativePath, String commitHash,
                                               boolean headFlag) {
        Commit checkoutFromCommit;
        if (headFlag) {
            checkoutFromCommit = Branch.getCurrentBranchHead();
        } else {
            checkoutFromCommit = Commit.getCommitFromHash(commitHash);
        }
        String fileHashInCommit = checkoutFromCommit.getStagedFilesCommit().get(fileRelativePath);

        if (fileHashInCommit == null) {
            System.out.print("File does not exist in ");
            System.out.println(headFlag ? "the head commit." : "commit [" +
                    truncateString(commitHash, 7) + "].");
        } else {
            restoreFileFromCommit(fileRelativePath, fileHashInCommit, headFlag, true,
                    commitHash);
        }
    }

    /** Checks and errors out if there are any untracked or added/removed changes yet to be committed. */
    static void checkPendingOrUntrackedChanges(List<String> untrackedFiles,
                                               Map<String, String> filesOfCommit) {
        if (untrackedFiles.size() >= 1 && canAFileBeOverWritten(untrackedFiles, filesOfCommit)) {
            exitWithError("There is an untracked file in the way; delete it," +
                    " or add and commit it first.", false);
        } else if (INDEX_FILE.exists() || UNTRACKING_FILE.exists()) {
            exitWithError("Changes are pending to be committed. Please commit them first.",
                    false);
        }
    }

    /** Deletes a file if it is empty. */
    public static void deleteFileIfEmpty(File fileToBeDeleted, boolean forceDelete) {
        if (fileToBeDeleted.exists() && forceDelete) {
            if (!fileToBeDeleted.delete()) {
                exitWithError("Could not delete file - " + fileToBeDeleted.getName(),
                            false);
            }
        } else if (fileToBeDeleted.exists() && getNumberOfLinesInFile(fileToBeDeleted)
                <= 0 && !fileToBeDeleted.delete()) {
            exitWithError("Could not delete file - " + fileToBeDeleted.getName(),
                    false);
        }
    }

    /** Delete the files that are tracked but not present in the alternate branch */
    static void deleteTrackedFiles(Map<String, String> filesOfHeadCommitOfBranch) {
        if (TRACKING_FILE.exists()) {
            String[] trackedFiles = getAllTrackedFiles();

            for (String fileName: trackedFiles) {
                if (!filesOfHeadCommitOfBranch.containsKey(fileName)) {
                    File fileToBeDeleted = new File(fileName);
                    if (fileToBeDeleted.exists() && !fileToBeDeleted.delete()) {
                        exitWithError("Unable to delete file! - " + fileName, true);
                    }
                }
            }
        }
    }

    /** Returns an array of all the tracked files. */
    private static String[] getAllTrackedFiles() {
        return readContentsAsString(TRACKING_FILE).split("\n");
    }

    /** Returns number of lines in a file. */
    public static long getNumberOfLinesInFile(File file) {
        if (file.exists()) {
            Path path = Paths.get(file.getAbsolutePath());
            long lines = 0;

            try {
                lines = Files.lines(path).count();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return lines;
        }
        return 0;
    }

    /** Returns number of untracked files. */
    public static long getNumberOfUntrackedFiles() {
        return getNumberOfLinesInFile(UNTRACKING_FILE);
    }

    /** Get relative path for a file from the CWD. */
    private static Path getRelativePath(File file) {
        Path fileAbsolutePath = Paths.get(file.getAbsolutePath());
        Path cwdAbsolutePath = Paths.get(CWD.getAbsolutePath());
        return cwdAbsolutePath.relativize(fileAbsolutePath);
    }

    /** Get all files from the directory if they are untracked. */
    static List<String> getUntrackedFiles(File directory, List<String> untrackedFiles) {
        File[] fList = directory.listFiles();

        if (fList != null) {
            for (File file : fList) {
                String fileRelativePath = getRelativePath(file).toString();

                if (!isInGitletIgnore(fileRelativePath) && !isFileNameInFile(TRACKING_FILE,
                        fileRelativePath)) {
                    if (file.isFile()) {
                        untrackedFiles.add(fileRelativePath);
                    } else if (file.isDirectory()) {
                        untrackedFiles = getUntrackedFiles(file, untrackedFiles);
                    }
                }
            }
        }
        return untrackedFiles;
    }

    /** Clears out the removed branch from the HEAD file. */
    private static String headFileContentAfterRemovingBranch(String branchName) {
        String[] branchList = Branch.getAllBranchesDetails();
        String newHeadContent = "";

        for (String branch: branchList) {
            String repoHeadName = branch.split(" ")[0];
            if (!repoHeadName.equals(branchName)) {
                newHeadContent += branch + "\n";
            }
        }
        return newHeadContent.trim();
    }

    /** Checks if a file name is present in the contents of another file. */
    public static boolean isFileNameInFile(File file, String filePath) {
        if (!file.exists()) {
            return false;
        }
        String[] fileContent = readContentsAsString(file).split("\n");

        for (String fileName: fileContent) {
            if (fileName.equals(filePath)) {
                return true;
            }
        }
        return false;
    }

    /** Checks if file or directory is present in GITLET_IGNORE list. */
    public static boolean isInGitletIgnore(String fileName) {
        if (GITLET_IGNORE.contains(fileName)) {
            return true;
        }

        for (String regex: GITLET_IGNORE) {
            if (fileName.matches(regex)) {
                return true;
            }
        }
        return false;
    }

    /** Lists the files that are modified but not staged yet. */
    public static void listModifiedButNotStagedFiles() {
        File[] fList = CWD.listFiles();

        if (fList != null) {
            for (File file: fList) {
                String fileRelativePath = getRelativePath(file).toString();
                printModifiedButNotStagedFiles(fileRelativePath, file);
            }
        }
    }

    /** Lists removed files, i.e. files that were once staged and/or tracked but aren't tracked now. */
    private static void listRemovedFiles() {
        if (UNTRACKING_FILE.exists()) {
            System.out.println(readContentsAsString(UNTRACKING_FILE));
        }
    }

    /** Checks for and lists the untracked files. */
    private static void listUntrackedFiles() {
        List<String> untrackedFiles = getUntrackedFiles(CWD, new ArrayList<>());

        for (String fileName: untrackedFiles) {
            System.out.println(fileName);
        }
    }

    /** Matches given message with provided commit's message. */
    private static boolean doesCommitMessageMatch(File commitFile, String commitMsg) {
        Commit commitObject = readObject(commitFile, Commit.class);

        if (commitMsg.equals(commitObject.getMessage())) {
            System.out.println(commitObject.getHash());
            return true;
        }
        return false;
    }

    /** Prints the name of the file that is modified but not staged yet. */
    private static void printModifiedButNotStagedFiles(String fileRelativePath, File file) {
        if (!isInGitletIgnore(fileRelativePath) && isFileNameInFile(TRACKING_FILE, fileRelativePath)) {
            byte[] contents = readContents(file);
            String blobName = sha1(contents);
            File blobFile = join(GITLET_DIR, blobName);

            if (!blobFile.exists()) {
                System.out.println(fileRelativePath);
            }
        }
    }

    /** Removes the name of given file from the contents of another file. */
    public static void removeFileNameFromFile(File file, String fileName) {
        if (file.exists()) {
            String fileContent = readContentsAsString(file);
            String[] fileContentArray = fileContent.split("\n");

            for (String fileToBeRemoved: fileContentArray) {
                if (fileToBeRemoved.equals(fileName)) {
                    fileContent = fileContent.replace(fileToBeRemoved, "");
                }
            }
            fileContent = fileContent.trim();
            writeContents(file, fileContent);
            deleteFileIfEmpty(file, false);
        }
    }

    /** Removes * from the branch going active to inactive. Adds a * in front of the branch
     * which will be active now. */
    private static String removeFromOrSetToRepoHead(String branch, String headContent,
                                                    String repoHeadName, boolean setFlag) {
        String repoHeadNewName, temp = branch;
        if (setFlag) {
            repoHeadNewName = "*" + repoHeadName;
            branch = branch.replaceFirst(repoHeadName, repoHeadNewName);
        } else {
            repoHeadName = repoHeadName.substring(1);
            repoHeadNewName = repoHeadName;
            branch = branch.replaceFirst("\\*" + repoHeadName, repoHeadNewName);
        }

        return headContent.replace(temp, branch);
    }

    /** When a branch is switched, this function replaces the content of the files that
     * are being tracked in the current branch & also present in the branch to be
     * switched to, with the content from the branch to be switched to.*/
    static void replaceFilesInWorkingDirectory(Map<String, String> filesOfHeadCommitOfBranch) {
        for (var fileAndHash: filesOfHeadCommitOfBranch.entrySet()) {
            File toBeReplaced = new File(fileAndHash.getKey());
            File toBeReplacedWith = join(GITLET_DIR, fileAndHash.getValue());

            writeContents(toBeReplaced, readContentsAsString(toBeReplacedWith));
        }
    }

    /** Restores a file to its version in the specified commit. */
    public static void restoreFileFromCommit(String fileRelativePath, String fileHashInCommit,
                                              boolean headFlag, boolean verbose, String commitHash) {
        File fileInCommit = join(GITLET_DIR, fileHashInCommit);
        File currentVersionOfFile = new File(fileRelativePath);

		System.out.println(fileHashInCommit);
        if (!fileInCommit.exists()) {
            System.out.println("Unable to check out file at " + fileRelativePath + ". It has been deleted.");
        } else {
            writeContents(currentVersionOfFile, readContentsAsString(fileInCommit));
            if (verbose) {
                System.out.print("Checked out " + fileRelativePath + " from ");
                System.out.println(headFlag ? "the head commit." : "commit [" +
                        truncateString(commitHash, 7) + "].");
            }
        }
    }

    /** Switches to the branch with the given name. */
    static void updateRepositoryHead(String branchName) {
        String[] branchList = Branch.getAllBranchesDetails();
        String headContent = readContentsAsString(HEAD_FILE);

        for (String branch: branchList) {
            String repoHeadName = branch.split(" ")[0];
            if (branch.startsWith("*")) {
                headContent = removeFromOrSetToRepoHead(branch, headContent, repoHeadName,false);
            } else if (repoHeadName.equals(branchName)) {
                headContent = removeFromOrSetToRepoHead(branch, headContent, repoHeadName,true);
            }
        }
        writeContents(HEAD_FILE, headContent);
    }
}
