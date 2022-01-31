package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static gitlet.Utils.*;

/**
 * Helper class that creates a map that maps file paths to their blobs.
 *
 * @author Vipul Sharma
 */
public class FileStager implements Serializable {
	private final Map<String, String> stageFileMap;
	
	public FileStager() {
		this.stageFileMap = new HashMap<>();
	}
	
	/** Clears the staging area. */
	public static void clearStagingArea() {
		Repository.deleteFileIfEmpty(INDEX_FILE, true);
	}
	
	/** Deletes the old blob file for the file at path provided. */
	public static void deleteOldStagedFile(String fileRelativePath) {
		FileStager fs = getAllStagedFiles();
		
		String blobName = fs.stageFileMap.remove(fileRelativePath);
		writeObject(INDEX_FILE, fs);
		
		if (blobName != null) {
			deleteBlobFile(blobName);
		}
		deleteIndexFileIfEmpty();
	}
	
	/** Returns number of staged files. */
	public static int getNumberOfStagedFiles() {
		if (INDEX_FILE.exists()) {
			return Objects.requireNonNull(getStagedFiles()).size();
		}
		return 0;
	}
	
	/** Returns the map of staged files. */
	public static Map<String, String> getStagedFiles() {
		if (INDEX_FILE.exists()) {
			FileStager fs = getAllStagedFiles();
			return fs.stageFileMap;
		}
		return null;
	}
	
	/** Gets currently staged hash for the file specified by the path. */
	public static String getStagedHash(String fileRelativePath) {
		return Objects.requireNonNull(getStagedFiles()).get(fileRelativePath);
	}
	
	/** Returns true if given file path is staged. */
	public static boolean isFileStaged(String filePath) {
		if (INDEX_FILE.exists()) {
			for(var fileAndBlob: Objects.requireNonNull(getStagedFiles()).entrySet()) {
				if (filePath.equals(fileAndBlob.getKey())) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	/** Checks for and lists the staged files. */
	public static void listStagedFiles() {
		if (!INDEX_FILE.exists()) {
			return;
		}
		
		for(var fileAndBlob: Objects.requireNonNull(getStagedFiles()).entrySet()) {
			System.out.println(fileAndBlob.getKey());
		}
	}
	
	/** Removes a file from the staging area. This is done to stop tracking the file. */
	public static void removeFileFromStagingArea(String fileRelativePath) {
		FileStager fs = getAllStagedFiles();
		Objects.requireNonNull(fs.stageFileMap).remove(fileRelativePath);
		writeObject(INDEX_FILE, fs);
		deleteIndexFileIfEmpty();
	}
	
	/** Creates a map object, that maps file to its blob name, also creates a blob object
	 * for that file. */
	public static void stageFile(String fileRelativePath, String blobName, File blobFile, File fileToAdd) {
		FileStager fs;
		if (!INDEX_FILE.exists()) {
			fs = new FileStager();
		} else {
			fs = getAllStagedFiles();
		}
		
		fs.stageFileMap.put(fileRelativePath, blobName);
		writeObject(INDEX_FILE, fs);
		
		writeContents(blobFile, readContentsAsString(fileToAdd));
	}
	
	/** Stages accordingly if the file does not exist. */
	public static void stageIfFileDoesNotExist(String fileRelativePath, String blobName,
	                                           String fileName, File blobFile, File fileToAdd,
	                                           boolean verbose) {
		// Check in files being tracked
		if (!Repository.isFileNameInFile(TRACKING_FILE, fileRelativePath)) {
			Repository.addFileNameToFile(TRACKING_FILE, fileRelativePath);
		} else if (!isCurrentSameAsStaged(fileRelativePath, blobName) && INDEX_FILE.exists()) {
			FileStager.deleteOldStagedFile(fileRelativePath);
		}
		
		updateStagedFileSha(fileRelativePath, blobName, fileName, blobFile, fileToAdd, verbose);
	}
	
	/** Stages accordingly if the file already exists. */
	public static void stageIfFileExists(String fileRelativePath, String blobName,
	                                     File blobFile, File fileToAdd, boolean verbose) {
		if (isCurrentSameAsStaged(fileRelativePath, blobName) && verbose) {
				System.out.println("File is already added.");
		} else if (Commit.isFileAlreadyCommitted(fileRelativePath)) {
			stageIfFileIsCommitted(fileRelativePath, blobName, blobFile, fileToAdd, verbose);
		} else {
			// file was removed, now it's being added again.
			FileStager.stageFile(fileRelativePath, blobName, blobFile, fileToAdd);
			if (!Repository.isFileNameInFile(TRACKING_FILE, fileRelativePath)) {
				Repository.addFileNameToFile(TRACKING_FILE, fileRelativePath);
			}
			if (verbose) {
				System.out.println("Added " + fileRelativePath + " as " +
						truncateString(blobName, 7) + ".");
			}
		}
	}
	
	/** Unstages a file that is being tracked. */
	public static void unstageTrackedFile(String fileRelativePath) {
		File fileToBeUnstaged = new File(fileRelativePath);
		byte[] contents = readContents(fileToBeUnstaged);
		
		String fileToBeUnstagedHash = sha1(contents);
		if (Commit.isFileInHeadCommit(fileRelativePath, fileToBeUnstagedHash)) {
			Repository.addFileNameToFile(UNTRACKING_FILE, fileRelativePath);
		}
		
		File fileToBeRemoved = new File(fileRelativePath);
		if (fileToBeRemoved.exists() && !fileToBeRemoved.delete()) {
			exitWithError("Unable to delete the file - " + fileRelativePath, false);
		}
	}

	/******************************* PRIVATE HELPER FUNCTIONS ****************************** /
	/** Deletes the blob file with given name. */
	private static void deleteBlobFile(String blobName) {
		File blobFile = join(GITLET_DIR, blobName);
		if (!blobFile.delete()) {
			exitWithError("Could not update file's sha1 blob!", true);
		}
	}
	
	/** Deletes the index file if no file is staged. */
	private static void deleteIndexFileIfEmpty() {
		if (getNumberOfStagedFiles() == 0) {
			if (!INDEX_FILE.delete()) {
				System.out.println("Could not update INDEX file.");
			}
		}
	}
	
	/** Returns an object of all the staged files. */
	private static FileStager getAllStagedFiles() {
		return readObject(INDEX_FILE, FileStager.class);
	}
	
	/** Returns true if current hash is same as the hash of the file currently staged. */
	private static boolean isCurrentSameAsStaged(String fileRelativePath, String sha) {
		if (INDEX_FILE.exists()) {
			String stagedHash = FileStager.getStagedHash(fileRelativePath);
			return Objects.equals(sha, stagedHash);
		}
		return false;
	}
	
	/** Remove the removed files from the staged files map. */
	public static void modifyStagedFilesUsingRemovedFiles(Map<String, String> stagedFiles) {
		String[] untrackedFiles = readContentsAsString(UNTRACKING_FILE).split("\n");
		
		for (String untrackedFile: untrackedFiles) {
			stagedFiles.remove(untrackedFile);
		}
	}
	
	/** Completes staging if file is already committed. */
	private static void stageIfFileIsCommitted(String fileRelativePath, String blobName,
	                                           File blobFile, File fileToAdd, boolean verbose) {
		if (INDEX_FILE.exists()) {
			FileStager.deleteOldStagedFile(fileRelativePath);
		}
		if (!Commit.isFileInHeadCommit(fileRelativePath, blobName)) {
			// file was added, committed, removed and now is being added again.
			FileStager.stageFile(fileRelativePath, blobName, blobFile, fileToAdd);
			if (verbose) {
				System.out.println("Added " + fileRelativePath + " as " +
						truncateString(blobName, 7) + ".");
			}
		} else {
			if (Repository.isFileNameInFile(UNTRACKING_FILE, fileRelativePath)) {
				Repository.removeFileNameFromFile(UNTRACKING_FILE, fileRelativePath);
			}
			if (!Repository.isFileNameInFile(TRACKING_FILE, fileRelativePath)) {
				Repository.addFileNameToFile(TRACKING_FILE, fileRelativePath);
			}
			if (verbose) {
				System.out.println("Current version of " + fileRelativePath + " already exists in " +
						"the HEAD commit.");
			}
		}
	}
	
	/** Updates sha of the staged file in INDEX file. */
	private static void updateStagedFileSha(String fileRelativePath, String blobName,
	                                        String fileName, File blobFile, File fileToAdd,
	                                        boolean verbose) {
		/* File doesn't exist already and is being tracked, update its sha1 */
		FileStager.stageFile(fileRelativePath, blobName, blobFile, fileToAdd);
		
		if (verbose && Repository.isInGitletIgnore(fileRelativePath)) {
			System.out.println("Overriding specified GITLET_IGNORE behavior...");
		}
		if (verbose) {
			System.out.println("Added " + fileName + " as " + truncateString(blobName, 7) + ".");
		}
	}
 }
