package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.*;

import static gitlet.Utils.*;

/** Represents a gitlet commit object.
 *  This class contains instance variables and methods that help in the representation
 *  of a Commit and its metadata. A Commit's metadata includes its message, its timestamp,
 *  a reference to its parent commit and a list of file references.
 *
 *  @author Vipul Sharma
 */
public class Commit implements Serializable {
    /** The message of this Commit. */
    private String message;
    
    /** The timestamp for this Commit. */
    private Date timestamp;
    
    /** hash of the parent commit of this Commit.*/
    private String parent;
    
    /** hash of the head commit of the merged-in branch. */
    private String parentTwo = null;
    
    /** Commit's sha1-hash */
    private String hash;
    
    /** Keeps track of what files this commit is tracking. */
    private Map<String, String> stagedFiles;
    
    public Commit(String message, String parent) {
        this.message = message;
        this.parent = parent;
        this.stagedFiles = new HashMap<>();
        
        if (this.parent == null) {
            this.timestamp = new Date(0);
        } else {
            this.timestamp = new Date();
        }

        this.createCommit();
    }
    
    /** Adds the blobs of the files in the staging area to a commit file. */
    public void createCommit() {
        File commitFile = Utils.join(COMMIT_OBJECT_DIR, "commitFile");
    
        /* if this is the initial commit, there is no need to add staged files, that happens
         in the else case, here we create the commit objects directory. */
        if (!COMMIT_OBJECT_DIR.exists()) {
            if (!COMMIT_OBJECT_DIR.mkdir()) {
                exitWithError("Could not initialize Gitlet objects directory.", false);
            }
        } else if (FileStager.getNumberOfStagedFiles() >= 1) {
            this.stagedFiles.putAll(Objects.requireNonNull(FileStager.getStagedFiles()));
        }
        
        // write commit object to commit file
        writeObject(commitFile, this);
        // Calculate hash for this commit
        commitHash(commitFile);
        // Rename the object for this commit
        renameCommitObject(commitFile);
    }
    
    /** Clones the head commit of the current branch, modifies its properties. */
    public static Commit cloneAndModifyCommit(String hash, String message,
                                              String mergingBranchHeadHash, boolean mergeCommit) {
        // Clone the head commit
        Commit newCommit = getCommitFromHash(hash);
        
        // Modify its properties
        newCommit.setMessage(message);
        newCommit.setDate(new Date());
        newCommit.setParent(hash);
        
        if (mergeCommit) {
            newCommit.setParentTwo(mergingBranchHeadHash);
        } else if (newCommit.getParentTwo() != null) {
            newCommit.setParentTwo(null);
        }
    
        if (UNTRACKING_FILE.exists()) {
            FileStager.modifyStagedFilesUsingRemovedFiles(newCommit.stagedFiles);
        }
        newCommit.createCommit();
        
        return newCommit;
    }
    
    /** Calculates the sha1-hash for this commit based on the contents of the commit file
     *  that contains staging area details and commit metadata. */
    public void commitHash(File commitFile) {
        this.hash = sha1(readContentsAsString(commitFile));
        writeObject(commitFile, this);
    }
    
    /** Creates a file with the commit's hash as its name. This file will contain the commit object. */
    public void renameCommitObject(File commitFile) {
        File commitObject = Utils.join(COMMIT_OBJECT_DIR, this.hash);
        
        if (!commitFile.renameTo(commitObject)) {
            exitWithError("Could not create the commit. This seems like a mistake on our part.",
                    false);
        }
    }
    
    /** Returns the commit given its hash. */
    public static Commit getCommitFromHash(String commitHash) {
        commitHash = searchCommitUsingTruncatedHash(commitHash);
    
        File commitFile = join(COMMIT_OBJECT_DIR, commitHash);
        if (!commitFile.exists()) {
            exitWithError("Commit with hash " + commitHash + " does not exist.", false);
        }
        return readObject(commitFile, Commit.class);
    }
    
    /** Returns the complete hash if an incomplete hash is provided. */
    public static String searchCommitUsingTruncatedHash(String searchHash) {
        if (searchHash.length() < MINIMUM_UID_LENGTH || searchHash.length() > UID_LENGTH) {
            exitWithError("Length of the provided hash is not ideal.", false);
        }
    
        File[] commitFilesList = COMMIT_OBJECT_DIR.listFiles();
        
        for (File commitFile : Objects.requireNonNull(commitFilesList)) {
            String commitHash = commitFile.getName();
            if (commitHash.startsWith(searchHash)) {
                searchHash = commitHash;
                break;
            }
        }
        return searchHash;
    }
    
    /** Returns true if the given file path was in any earlier commit. */
    public static boolean isFileAlreadyCommitted(String fileRelativePath) {
        String commitHash = Branch.getCurrentBranchHeadHash();
    
        while (commitHash != null) {
            File commitFile = join(COMMIT_OBJECT_DIR, commitHash);
            Commit commitObject = readObject(commitFile, Commit.class);
        
            if (commitObject.stagedFiles.containsKey(fileRelativePath)) {
                return true;
            }
            commitHash = commitObject.getParent();
        }
        
        return false;
    }
    
    /** Returns true if the given file path is in the head commit. */
    public static boolean isFileInHeadCommit(String fileRelativePath, String fileSha) {
        Commit currentCommit = Branch.getCurrentBranchHead();
        String headCommitFileHash = currentCommit.stagedFiles.get(fileRelativePath);
        return fileSha.equals(headCommitFileHash);
    }
    
    /** Calls additional methods when a new commit is created. */
    public static void afterCommit(String branchName, String newCommitHash, String message) {
        System.out.println("+" + FileStager.getNumberOfStagedFiles() + " (addition), -" +
                        Repository.getNumberOfUntrackedFiles() + " (removal) update(s) to the repository.");
        
        Branch.updateBranchHead(newCommitHash);
        FileStager.clearStagingArea();
        Repository.deleteFileIfEmpty(UNTRACKING_FILE, true);
        
        System.out.println("[" + branchName + " " +
                truncateString(newCommitHash, 7) + "] " + message);
    }
    
    /** Prints a log of all commits on this branch. */
    public static void logCommitsInfo(String commitHash) {
        while (commitHash != null) {
            File commitFile = join(COMMIT_OBJECT_DIR, commitHash);
            Commit commitObject = readObject(commitFile, Commit.class);
        
            System.out.println(TRIPLE_EQUALS + "\ncommit " + commitObject.getHash());
            
            if (commitObject.parentTwo != null) {
                System.out.println("Merge: " + commitObject.getParent() + " " + commitObject.getParentTwo());
            }
            System.out.println("Date: " + commitObject.getDate() + "\n" + commitObject.getMessage() + "\n");
            
            commitHash = commitObject.getParent();
        }
    }
    
    /** Returns commit message. */
    public String getMessage() {
        return this.message;
    }
    
    /** Sets commit message. */
    public void setMessage(String msg) {
        this.message = msg;
    }
    
    /** Returns commit's timestamp */
    public Date getDate() {
        return this.timestamp;
    }
    
    /** Sets commit's timestamp. */
    public void setDate(Date datetime) {
        this.timestamp = datetime;
    }
    
    /** Returns commit's parent */
    public String getParent() {
        return this.parent;
    }
    
    /** */
    private String getParentTwo() {
        return this.parentTwo;
    }
    
    /** Sets commit's parent. */
    public void setParent(String parent) {
        this.parent = parent;
    }
    
    /** Sets commit's parent two in case of merge commit. */
    private void setParentTwo(String mergingBranchHeadHash) {
        this.parentTwo = mergingBranchHeadHash;
    }
    
    /** Returns commit hash. */
    public String getHash() {
        return this.hash;
    }
    
    /** Returns map object of a commit's staged files. */
    public Map<String, String> getStagedFilesCommit() {
        return this.stagedFiles;
    }
    
    /** Returns a list of all the ancestor commit's hash in a branch. */
    public static List<String> getCommitAncestors(String commitHash) {
        List<String> ancestorsList = new ArrayList<>();
        
        while (commitHash != null) {
            ancestorsList.add(commitHash);
            File commitFile = join(COMMIT_OBJECT_DIR, commitHash);
            Commit commitObject = readObject(commitFile, Commit.class);
            
            commitHash = commitObject.getParent();
        }
        
        return ancestorsList;
    }
}
