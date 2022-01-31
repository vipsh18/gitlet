package gitlet;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static gitlet.Utils.*;

/**
 * @author Vipul Sharma
 */
public class Branch {
	private String name;
	private Commit headCommit;
	
	public Branch(String branchName, Commit headCommit, boolean initialCommit) {
		this.name = branchName;
		this.headCommit = headCommit;
		
		if (initialCommit) {
			writeContents(HEAD_FILE, "*" + this.name + " " + this.headCommit.getHash());
			return;
		}
		if (this.name.contains("*")) {
			System.out.println("Branch name can not contain an asterisk (*).");
		} else if (checkIfBranchExists(this.name)) {
			System.out.println(this.name + " branch already exists!");
		} else {
			writeContents(HEAD_FILE, readContentsAsString(HEAD_FILE) + "\n" +
						this.name + " " + this.headCommit.getHash());
			
			File branchTrackingFile = join(BRANCH_TRACKING, this.name);
			
			if (TRACKING_FILE.exists()) {
				writeContents(branchTrackingFile, readContentsAsString(TRACKING_FILE));
			}
			System.out.println("Branch " + branchName + " created.");
		}
	}
	
	/** Checks if a provided branch name is valid. */
	public static void branchNameChecks(String branchName, String checkType) {
		if (!Branch.checkIfBranchExists(branchName)) {
			exitWithError("Branch " + branchName + " does not exist.", false);
		}
		
		String currentBranch = Branch.getCurrentBranch();
		if (branchName.equals(currentBranch)) {
			System.out.print("Cannot ");
			switch (checkType) {
				case "checkout" -> System.out.print("checkout ");
				case "remove" -> System.out.print("remove ");
				case "merge" -> System.out.print("merge " + branchName + " into ");
			}
			exitWithError(currentBranch + ", since it is the current branch.", false);
		}
	}
	
	/** Returns true if a branch already exists with the given name. */
	public static boolean checkIfBranchExists(String newBranchName) {
		String[] branchList = getAllBranchesDetails();
		
		for (String branch: branchList) {
			String branchName = branch.split(" ")[0];
			
			if (branchName.startsWith("*") && branchName.substring(1).equals(newBranchName)) {
				return true;
			} else if (branchName.equals(newBranchName)) {
				return true;
			}
		}
		
		return false;
	}
	
	/** Checks out branch with the given name. If successful, the head then points to this branch. */
	public static void checkoutBranch(String branchName) {
		Branch.branchNameChecks(branchName, "checkout");
		
		String hashOfHeadCommitOfBranch = Branch.getHeadHashOfBranch(branchName);
		Commit headCommitOfBranch = Commit.getCommitFromHash(
				Objects.requireNonNull(hashOfHeadCommitOfBranch));
		switchToBranch(branchName, headCommitOfBranch);
	}
	
	/** Returns and array that contains the name and head of the branch currently checked out. */
	private static String fetchCurrentBranchDetails() {
		String[] branchList = getAllBranchesDetails();
		
		for (String branch: branchList) {
			if (branch.startsWith("*")) {
				return branch;
			}
		}
		return null;
	}
	
	/** Returns an array that contains name and heads of all branches. */
	public static String[] getAllBranchesDetails() {
		return readContentsAsString(HEAD_FILE).split("\n");
	}
	
	/** Returns an array that contains current branch and its head. */
	public static String[] getBranchAndItsHead() {
		return Objects.requireNonNull(Branch.fetchCurrentBranchDetails()).split(" ");
	}
	
	/** Returns name of the current branch. */
	public static String getCurrentBranch() {
		return getBranchAndItsHead()[0].substring(1);
	}
	
	/** Return the head commit of the current branch. */
	public static Commit getCurrentBranchHead() {
		String commitHash = Branch.getCurrentBranchHeadHash();
		return Commit.getCommitFromHash(commitHash);
	}
	
	/** Returns hash of the head commit. */
	public static String getCurrentBranchHeadHash() {
		return getBranchAndItsHead()[1];
	}
	
	/** Returns the hash of the head commit of the given branch name.
	 * This method should NOT be used to fetch the hash of the head commit of the current branch,
	 * getCurrentBranchHeadHash() should be used for that. */
	public static String getHeadHashOfBranch(String branchName) {
		String[] branchList = getAllBranchesDetails();
		
		for (String branch : branchList) {
			String[] currBranch = branch.split(" ");
			if (currBranch[0].equals(branchName)) {
				return currBranch[1];
			}
		}
		return null;
	}
	
	/** Prints out the names of all the branches. */
	public static void listBranches() {
		String[] branchList = getAllBranchesDetails();
		
		for (String branch: branchList) {
			System.out.println(branch.split(" ")[0]);
		}
	}
	
	/** Checks how two branches can be merged if they can be. */
	public static void mergeChecks(String currentBranchHeadHash, String mergingBranchHeadHash,
	                               String currentBranch, String mergingBranch, String splitPoint,
	                               Map<String, String> currentBranchHeadFiles) {
		Commit mergingBranchHeadCommit = Commit.getCommitFromHash(mergingBranchHeadHash);
		Map<String, String> mergingBranchHeadFiles = mergingBranchHeadCommit.getStagedFilesCommit();
		
		if (Objects.equals(mergingBranchHeadHash, currentBranchHeadHash)) {
			System.out.println(NO_CHANGES_COMMIT);
		} else if (splitPoint.equals(mergingBranchHeadHash)) {
			System.out.println("Given branch is an ancestor of the current branch.");
		} else if (splitPoint.equals(currentBranchHeadHash)) {
			fastForwardBranch(mergingBranchHeadHash, mergingBranchHeadFiles);
		} else {
			mergeBranches(currentBranch, mergingBranch, splitPoint, currentBranchHeadHash,
					mergingBranchHeadHash, currentBranchHeadFiles, mergingBranchHeadFiles);
		}
	}
	
	/** Updates head of the current branch. */
	public static void updateBranchHead(String newHead) {
		String headContent = readContentsAsString(HEAD_FILE);
		String[] branchList = headContent.split("\n");
		
		for (String branch: branchList) {
			if (branch.startsWith("*")) {
				String temp = branch;
				branch = branch.replace(branch.split(" ")[1], newHead);
				headContent = headContent.replace(temp, branch);
				break;
			}
		}
		
		writeContents(HEAD_FILE, headContent);
	}
	
	/******************************* PRIVATE HELPER FUNCTIONS ****************************** //
	 /** Fast-forwards a branch, basically checks out a given branch, i.e. places files from another branch
	 * into the current branch. */
	private static void fastForwardBranch(String mergingBranchHeadHash,
	                                      Map<String, String> mergingBranchHeadFiles) {
		Repository.replaceFilesInWorkingDirectory(mergingBranchHeadFiles);
		Repository.deleteTrackedFiles(mergingBranchHeadFiles);
		Branch.updateBranchHead(mergingBranchHeadHash);
		System.out.println("Current branch fast-forwarded.");
	}
	
	/** Does what it should do. */
	private static void mergeBranches(String currentBranch, String mergingBranch, String splitPoint,
	                                  String currentBranchHeadHash, String mergingBranchHeadHash,
	                                  Map<String, String> currentBranchHeadStagedFiles,
	                                  Map<String, String> mergingBranchHeadStagedFiles) {
		
		Commit splitPointCommit = Commit.getCommitFromHash(splitPoint);
		Map<String, String> splitPointCommitFiles = splitPointCommit.getStagedFilesCommit();
		
		for (var splitFileAndHash: splitPointCommitFiles.entrySet()) {
			String fileRelativePath = splitFileAndHash.getKey();
			String fileSplitPointHash = splitFileAndHash.getValue();
			String fileMergingBranchHash = mergingBranchHeadStagedFiles.
					getOrDefault(fileRelativePath, "");
			String fileCurrentBranchHash = currentBranchHeadStagedFiles.
					getOrDefault(fileRelativePath, "");
			
			if (!fileMergingBranchHash.equals(fileSplitPointHash) &&
					!fileMergingBranchHash.equals("") && fileCurrentBranchHash.equals(fileSplitPointHash)) {
				/* For files that have been modified in the given branch since split point, but not
				* modified in the current branch. - 1 */
				Repository.restoreFileFromCommit(fileRelativePath, fileMergingBranchHash,
						false, false, "");
				Repository.add(fileRelativePath, true);
			} else if (fileCurrentBranchHash.equals(fileSplitPointHash) &&
					fileMergingBranchHash.equals("")) {
				/* For files present at the split point, unmodified in the current branch, and absent in
				 the given branch should be removed (and untracked) - 6 */
				Repository.rm(fileRelativePath);
			} else if ((!fileCurrentBranchHash.equals("") && !fileMergingBranchHash.equals("") &&
			!fileCurrentBranchHash.equals(fileSplitPointHash) && !fileMergingBranchHash.equals(fileSplitPointHash)
					&& !fileCurrentBranchHash.equals(fileMergingBranchHash)) ||
					(!fileCurrentBranchHash.equals(fileSplitPointHash) && !fileCurrentBranchHash.equals("") &&
							fileMergingBranchHash.equals("")) || (!fileMergingBranchHash.equals(fileSplitPointHash)
					&& !fileMergingBranchHash.equals("") && fileCurrentBranchHash.equals(""))) {
				/* Any files modified in different ways in the current and given branches are in conflict. - 8 */
				mergeConflict(fileRelativePath, fileCurrentBranchHash, fileMergingBranchHash);
			}
		}
		
		/* Any files that were not present at the split point and are present only in the given branch
		should be checked out and staged. */
		for (var mergingBranchFileAndHash: mergingBranchHeadStagedFiles.entrySet()) {
			String fileRelativePath = mergingBranchFileAndHash.getKey();
			String mergingBranchFileHash = mergingBranchFileAndHash.getValue();
			String currentBranchFileHash = currentBranchHeadStagedFiles.get(fileRelativePath);
			
			if (!splitPointCommitFiles.containsKey(fileRelativePath) && currentBranchFileHash == null) {
				Repository.restoreFileFromCommit(fileRelativePath, mergingBranchFileHash,
						false, false, "");
				Repository.add(fileRelativePath, true);
			} else if (!splitPointCommitFiles.containsKey(fileRelativePath) && currentBranchFileHash != null
			&& !currentBranchFileHash.equals(mergingBranchFileHash)) {
				mergeConflict(fileRelativePath, currentBranchFileHash, mergingBranchFileHash);
			}
		}
		
		String mergeCommitMessage = "Merging " + mergingBranch + " with " + currentBranch;
		if ((INDEX_FILE.exists() && FileStager.getNumberOfStagedFiles() >= 1) || UNTRACKING_FILE.exists()) {
			Commit mergeCommit = Commit.cloneAndModifyCommit(currentBranchHeadHash, mergeCommitMessage,
					mergingBranchHeadHash, true);
			Commit.afterCommit("*" + currentBranch, mergeCommit.getHash(), mergeCommitMessage);
			System.out.println(mergingBranch + " merged into " + currentBranch);
		} else {
			System.out.println(NO_CHANGES_COMMIT);
		}
	}
	
	/** Write to conflicted file in case of overlapping content. */
	private static void mergeConflict(String fileRelativePath, String fileCurrentBranchHash, String
	                                  fileMergingBranchHash) {
		File conflictedFile = new File(fileRelativePath);
		File conflictedFileInCurrentBranch = join(GITLET_DIR, fileCurrentBranchHash);
		File conflictedFileInMergingBranch = join(GITLET_DIR, fileMergingBranchHash);
		
		String conflictedFileContent = "<<<<<<< HEAD\n";
		if (!fileCurrentBranchHash.equals("")) {
			conflictedFileContent += readContentsAsString(conflictedFileInCurrentBranch) + "\n";
		}
		conflictedFileContent += "=======\n";
		if (!fileMergingBranchHash.equals("")) {
			conflictedFileContent += readContentsAsString(conflictedFileInMergingBranch) + "\n";
		}
		conflictedFileContent += ">>>>>>>";
		
		writeContents(conflictedFile, conflictedFileContent);
		Repository.add(fileRelativePath, false);
		
		System.out.println("Encountered a merge conflict. Check the contents of " + fileRelativePath +
				" to resolve.");
	}
	
	/** Saves the tracking file of the current branch and loads the tracking file (if it exists)
	 * of the branch awe are switching into. */
	private static void saveAndLoadTrackingFile(String branchName) {
		File currentBranchTrackingFile = join(BRANCH_TRACKING, Branch.getCurrentBranch());
		if (TRACKING_FILE.exists()) {
			writeContents(currentBranchTrackingFile, readContentsAsString(TRACKING_FILE));
		}
		
		File checkoutBranchTrackingFile = join(BRANCH_TRACKING, branchName);
		if (checkoutBranchTrackingFile.exists()) {
			writeContents(TRACKING_FILE, readContentsAsString(checkoutBranchTrackingFile));
		}
	}
	
	/** Checks if repo head can be switched to the given branch name. */
	private static void switchToBranch(String branchName, Commit headCommitOfBranch) {
		List<String> untrackedFiles = Repository.getUntrackedFiles(CWD, new ArrayList<>());
		
		Map<String, String> filesOfHeadCommitOfBranch = headCommitOfBranch.getStagedFilesCommit();
		Repository.checkPendingOrUntrackedChanges(untrackedFiles, filesOfHeadCommitOfBranch);
		successfulSwitchToBranch(branchName, filesOfHeadCommitOfBranch);
	}
	
	/** Called when branch can be switched successfully. */
	private static void successfulSwitchToBranch(String branchName,
	                                             Map<String, String> filesOfHeadCommitOfBranch) {
		Repository.replaceFilesInWorkingDirectory(filesOfHeadCommitOfBranch);
		if (INDEX_FILE.exists()) {
			FileStager.clearStagingArea();
		}
		Repository.deleteTrackedFiles(filesOfHeadCommitOfBranch);
		saveAndLoadTrackingFile(branchName);
		Repository.updateRepositoryHead(branchName);
		
		System.out.println("Checked out branch " + branchName);
	}
}