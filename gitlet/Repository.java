package gitlet;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.io.IOException;

import static gitlet.Utils.*;

public class Repository {
    public static final File CWD = new File(System.getProperty("user.dir"));
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    public static final File BLOBS = join(GITLET_DIR, "blobs");
    public static final File COMMITS = join(GITLET_DIR, "commits");
    public static final File ADDITION = join(GITLET_DIR, "additionStage");
    public static final File REMOVAL = join(GITLET_DIR, "removalStage");
    // map branch name -> commit hash
    public static final File BRANCHES = join(GITLET_DIR, "branches");
    // the current working branch
    public static final File HEAD = join(GITLET_DIR, "HEAD");

    public static void init() {
        if (GITLET_DIR.exists()) {
            System.out.println("A Gitlet version-control system "
                + "already exists in  the current directory.");
            System.exit(0);
        }
        // create required directories
        GITLET_DIR.mkdir();
        BLOBS.mkdir();
        COMMITS.mkdir();
        REMOVAL.mkdir();
        ADDITION.mkdir();

        Commit initialCommit = new Commit("initial commit", null, null);
        initialCommit.create(COMMITS);

        //set HEAD
        createFile(HEAD);
        Utils.writeContents(HEAD, "master");

        Repository.createFile(BRANCHES);
        Branches branches = new Branches(initialCommit.hash);
        Utils.writeObject(BRANCHES, branches);
    }

    public static void add(String fileName) {
        // if current file does not exist, exit
        File currentFile = join(CWD, fileName);
        if (!currentFile.exists()) {
            System.out.println("File does not exist.");
            System.exit(0);
        }
        // write content to new file in addition stage
        File addFile = join(ADDITION, fileName);
        if (!addFile.exists()) {
            createFile(addFile);
        }
        String fileContent = readContentsAsString(currentFile);
        Utils.writeContents(addFile, fileContent);
        /*
        If the current working version of
        the file is identical to the version in the current commit,
        do not stage it to be added,
        and remove it from the staging area if it is already there
        In other words, since we
        already added it (potentially overwritten), remove it
        */
        if (Repository.hasHash(Utils.sha1(fileContent + fileName))) {
            addFile.delete();
        }
        //      remove file from delete area
        unstage(fileName, "REMOVAL");
    }

    // returns HEAD commit
    static Commit currentCommit() {
        String currentBranch = readContentsAsString(HEAD);
        String hash = readObject(BRANCHES, Branches.class).branches.get(currentBranch);
        return readObject(join(COMMITS, hash), Commit.class);
    }

    // returns true if current commit has hash
    static boolean hasHash(String hash) {
        Commit currentCommit = currentCommit();
        if (currentCommit.fileHashes.contains(hash)) {
            return true;
        }
        return false;
    }
    // returns true if current commit has fileName
    static boolean hasName(String fileName) {
        Commit currentCommit = currentCommit();
        if (currentCommit.fileReferences.containsKey(fileName)) {
            return true;
        }
        return false;
    }

    public static boolean unstage(String fileName, String from) {
        if (from.equals("REMOVAL")) {
            File removeFile = join(REMOVAL, fileName);
            if (removeFile.exists()) {
                removeFile.delete();
                return true;
            }
        } else if (from.equals("ADDITION")) {
            File addFile = join(ADDITION, fileName);
            if (addFile.exists()) {
                addFile.delete();
                return true;
            }
        }
        return false;
    }

    static void commit(String message) {
        Commit currentCommit = currentCommit();
        Commit newCommit = new Commit(message, currentCommit.hash, COMMITS);
        // update files from adding stage
        // get names of all files in the adding stage
        newCommit.modify(ADDITION, REMOVAL, BLOBS);
        newCommit.create(COMMITS);
        // change branch
        Branches branches = readObject(BRANCHES, Branches.class);
        String currentBranch = readContentsAsString(HEAD);
        branches.branches.remove(currentBranch);
        branches.branches.put(currentBranch, newCommit.hash);
        Utils.writeObject(BRANCHES, branches);
    }

    static void rm(String fileName) {
        File file = join(CWD, fileName);
        boolean unstaged = unstage(fileName, "ADDITION");
        if (hasName(fileName)) {
            // stage for removal
            File removalFile = join(REMOVAL, fileName);
            if (!removalFile.exists()) {
                createFile(removalFile);
            }
            if (file.exists()) {
                Utils.writeContents(removalFile, readContentsAsString(file));
                file.delete();
            }
        } else if (!unstaged) {
            System.out.println("No reason to remove the file.");
        }
    }

    static void log() {
        Commit currentCommit = currentCommit();
        while (currentCommit.parentHash != null) {
            currentCommit.log();
            currentCommit = readObject(join(COMMITS, currentCommit.parentHash), Commit.class);
        }
        currentCommit.log();
    }

    static void globalLog() {
        List<String> commitNames = plainFilenamesIn(COMMITS);
        for (String hash: commitNames) {
            Commit commit = readObject(join(COMMITS, hash), Commit.class);
            commit.log();
        }
    }

    static void find(String message) {
        List<String> commitNames = plainFilenamesIn(COMMITS);
        boolean found = false;
        for (String hash: commitNames) {
            Commit commit = readObject(join(COMMITS, hash), Commit.class);
            if (commit.message.equals(message)) {
                System.out.println(commit.hash);
                found = true;
            }
        }
        if (!found) {
            System.out.println("Found no commit with that message.");
        }
    }

    static void currentCheckout(String fileName) {
        String currentBranch = readContentsAsString(HEAD);
        String hash = readObject(BRANCHES, Branches.class).branches.get(currentBranch);
        idCheckout(fileName, join(COMMITS, hash));
    }

    static void idCheckout(String hash, String fileName) {
        if (hash.length() == 40) {
            File commitFile = join(COMMITS, hash);
            if (commitFile.exists()) {
                idCheckout(fileName, commitFile);
            } else {
                System.out.println("No commit with that id exists.");
            }
            return;
        }
        List<String> commitNames = plainFilenamesIn(COMMITS);
        for (String commitHash: commitNames) {
            if (commitHash.startsWith(hash)) {
                File commitFile = join(COMMITS, commitHash);
                idCheckout(fileName, commitFile);
                return;
            }
        }
    }

    static void idCheckout(String fileName, File serializedCommit) {
        Commit currentCommit = readObject(serializedCommit, Commit.class);
        File blob = currentCommit.fileReferences.getOrDefault(fileName, null);
        if (blob == null) {
            System.out.println("File does not exist in that commit.");
            System.exit(0);
        }
        File addFile = join(CWD, fileName);
        if (!addFile.exists()) {
            createFile(addFile);
        }
        Utils.writeContents(addFile, readContentsAsString(blob));
    }

    static void branchCheckOut(String branchName) {
        Branches branches = readObject(BRANCHES, Branches.class);
        if (!branches.branches.containsKey(branchName)) {
            System.out.println("No such branch exists.");
            System.exit(0);
        }
        Commit currentCommit = currentCommit();
        String currentBranch = readContentsAsString(HEAD);
        if (currentBranch.equals(branchName)) {
            System.out.println("No need to checkout the current branch.");
            System.exit(0);
        }
        File commitFile = join(COMMITS, branches.branches.get(branchName));
        Commit changeToCommit = Utils.readObject(commitFile, Commit.class);
        List<String> fileNames = plainFilenamesIn(CWD);
        for (String fileName : fileNames) {
            if (changeToCommit.fileReferences.containsKey(fileName)
                    && !currentCommit.fileReferences.containsKey(fileName)) {
                System.out.println("There is an untracked file in the way; "
                    + "delete it, or add and commit it first.");
                System.exit(0);
            }
        }

        // delete tracked files
        for (String fileName : currentCommit.fileReferences.keySet()) {
            File file = join(CWD, fileName);
            file.delete();
        }

        // add files from branch
        for (Map.Entry<String, File> entry : changeToCommit.fileReferences.entrySet()) {
            File cwdFile = join(CWD, entry.getKey());
            File blobFile = entry.getValue();
            Utils.writeContents(cwdFile, readContentsAsString(blobFile));
        }

        // clear stages
        List<String> additionFiles = plainFilenamesIn(ADDITION);
        for (String fileName : additionFiles) {
            File file = join(ADDITION, fileName);
            file.delete();
        }

        List<String> removalFiles = plainFilenamesIn(REMOVAL);
        for (String fileName : removalFiles) {
            File file = join(REMOVAL, fileName);
            file.delete();
        }
        //change HEAD
        Utils.writeContents(HEAD, branchName);

    }


    static void branch(String branchName) {
        Branches branches = readObject(BRANCHES, Branches.class);
        if (branches.branches.containsKey(branchName)) {
            System.out.println("A branch with that name already exists.");
            System.exit(0);
        }
        Commit currentCommit = currentCommit();
        branches.branches.put(branchName, currentCommit.hash);
        Utils.writeObject(BRANCHES, branches);
    }

    static void removeBranch(String branchName) {
        Branches branches = readObject(BRANCHES, Branches.class);
        if (!branches.branches.containsKey(branchName)) {
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        }
        String currentBranch = readContentsAsString(HEAD);
        if (currentBranch.equals(branchName)) {
            System.out.println("Cannot remove the current branch.");
            System.exit(0);
        }
        branches.branches.remove(branchName);
        Utils.writeObject(BRANCHES, branches);
    }

    static void status() {
        Branches branches = readObject(BRANCHES, Branches.class);
        Commit currentCommit = currentCommit();
        String currentBranch = readContentsAsString(HEAD);

        System.out.println("=== Branches ===");
        List<String> branchesNames = new ArrayList<>(branches.branches.keySet());
        Collections.sort(branchesNames);
        for (String branch : branchesNames) {
            if (branch.equals(currentBranch)) {
                System.out.print("*");
            }
            System.out.println(branch);
        }
        System.out.println();

        System.out.println("=== Staged Files ===");
        List<String> additionFiles = plainFilenamesIn(ADDITION);
        for (String file : additionFiles) {
            System.out.println(file);
        }
        System.out.println();

        System.out.println("=== Removed Files ===");
        List<String> removalFiles = plainFilenamesIn(REMOVAL);
        for (String file : removalFiles) {
            System.out.println(file);
        }
        System.out.println();

        System.out.println("=== Modifications Not Staged For Commit ===");
        // Tracked in the current commit, changed in the working directory, but
        // not staged or staged with different content
        List<String> cwdFiles = plainFilenamesIn(CWD);
        for (String file : cwdFiles) {
            // not tracked
            if (currentCommit.fileReferences.get(file) == null) {
                continue;
            }
            File cwdFile = join(CWD, file);
            if (cwdFile.isDirectory()) {
                continue;
            }
            String currentHash = sha1(Utils.readContentsAsString(cwdFile) + file);
            File commitHashBlobFile = currentCommit.fileReferences.get(file);
            File stagedFile = join(ADDITION, file);
            if (!commitHashBlobFile.getName().equals(currentHash)
                    && (!stagedFile.exists()
                    || !currentHash.equals(sha1(readContentsAsString(stagedFile) + file)))) {
                System.out.println(file + " (modified)");
            }
        }

        // Staged for addition, but deleted in the working directory or
        // Not staged for removal, but tracked in the current commit
        // and deleted from the working directory.
        for (Map.Entry<String, File> entry : currentCommit.fileReferences.entrySet()) {
            String fileName = entry.getKey();
            File cwdFile = join(CWD, fileName);
            File remFile = join(REMOVAL, fileName);
            if (!cwdFile.exists() && !remFile.exists()) {
                System.out.println(fileName + "(deleted)");
            }
        }
        System.out.println();

        System.out.println("=== Untracked Files ===");
        for (String fileName : cwdFiles) {
            File file = join(CWD, fileName);
            if (file.isDirectory()) {
                continue;
            }
            File addFile = join(ADDITION, fileName);
            if (!addFile.exists() && currentCommit.fileReferences.get(fileName) == null) {
                System.out.println(fileName);
            }
        }
        System.out.println();

    }

    static void reset(String commitHash) {
        File commitFile = join(COMMITS, commitHash);
        if (!commitFile.exists()) {
            System.out.println("No commit with that id exists.");
            System.exit(0);
        }
        Commit changeToCommit = Utils.readObject(commitFile, Commit.class);
        Commit currentCommit = currentCommit();
        List<String> fileNames = plainFilenamesIn(CWD);
        for (String fileName : fileNames) {
            if (changeToCommit.fileReferences.containsKey(fileName)
                    && !currentCommit.fileReferences.containsKey(fileName)) {
                System.out.println("There is an untracked file in the way; delete it, "
                       + "or add and commit it first.");
                System.exit(0);
            }
        }

        // delete tracked files
        for (String fileName : currentCommit.fileReferences.keySet()) {
            File file = join(CWD, fileName);
            file.delete();
        }

        // add files from branch
        for (Map.Entry<String, File> entry : changeToCommit.fileReferences.entrySet()) {
            File cwdFile = join(CWD, entry.getKey());
            File blobFile = entry.getValue();
            Utils.writeContents(cwdFile, readContentsAsString(blobFile));
        }

        // clear stages
        List<String> additionFiles = plainFilenamesIn(ADDITION);
        for (String fileName : additionFiles) {
            File file = join(ADDITION, fileName);
            file.delete();
        }

        List<String> removalFiles = plainFilenamesIn(REMOVAL);
        for (String fileName : removalFiles) {
            File file = join(REMOVAL, fileName);
            file.delete();
        }

        // change BRANCHES
        Branches branches = readObject(BRANCHES, Branches.class);
        String currentBranch = readContentsAsString(HEAD);
        branches.branches.remove(currentBranch);
        branches.branches.put(currentBranch, commitHash);
        Utils.writeObject(BRANCHES, branches);
    }

    static void mergeCheck(String branchName) {

        Commit currentCommit = currentCommit();
        Branches branches = readObject(BRANCHES, Branches.class);
        String currentBranch = readContentsAsString(HEAD);
        if (branches.branches.get(branchName) == null) {
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        }
        String otherHash = branches.branches.get(branchName);
        Commit otherCommit = readObject(join(COMMITS, otherHash), Commit.class);
        // find split point
        Commit splitPoint = splitPoint(currentCommit, otherCommit);
        //       System.out.println("found split point: " +
        //       splitPoint.hash + " with message " + splitPoint.message);
        // check if addition or removal area are not empty
        if (plainFilenamesIn(ADDITION).size() + plainFilenamesIn(REMOVAL).size() != 0) {
            System.out.println("You have uncommitted changes.");
            System.exit(0);
        }
        if (branchName.equals(currentBranch)) {
            System.out.println("Cannot merge a branch with itself.");
            System.exit(0);
        }

        if (splitPoint.hash.equals(otherHash)) {
            System.out.println("Given branch is an ancestor of the current branch.");
            System.exit(0);
        }
        List<String> fileNames = plainFilenamesIn(CWD);
        for (String fileName : fileNames) {
            if (otherCommit.fileReferences.containsKey(fileName)
                    && !currentCommit.fileReferences.containsKey(fileName)) {
                System.out.println("There is an untracked file in the way; "
                        + "delete it, or add and commit it first.");
                System.exit(0);
            }
        }


        if (splitPoint.hash.equals(currentCommit.hash)) {
            branchCheckOut(branchName);
            System.out.println("Current branch fast-forwarded.");
            System.exit(0);
        }

    }

    static void merge(String branchName) {
        mergeCheck(branchName);
        Commit currentCommit = currentCommit();
        Branches branches = readObject(BRANCHES, Branches.class);
        String currentBranch = readContentsAsString(HEAD);
        String otherHash = branches.branches.get(branchName);
        Commit otherCommit = readObject(join(COMMITS, otherHash), Commit.class);
        // find split point
        Commit splitPoint = splitPoint(currentCommit, otherCommit);
        Map<String, File> fileReferences = new TreeMap<>(); // file name -> blob reference
        Set<String> fileHashes = new HashSet<>(); // file hashes that I have
        boolean mergeConflict = false;
        boolean changed = false;
        for (Map.Entry<String, File> entry : currentCommit.fileReferences.entrySet()) {
            String fileName = entry.getKey();
            File blobFile = entry.getValue();
            String currentFileHash = blobFile.getName();
            //            System.out.println("doing things with file " + fileName);
            File fileInOtherCommit = otherCommit.fileReferences.get(fileName);
            String hashInOtherCommit = null;
            if (fileInOtherCommit != null) {
                hashInOtherCommit = fileInOtherCommit.getName();
            }

            File fileInSplitPoint = splitPoint.fileReferences.get(fileName);
            String hashInSplitPoint = null;
            if (fileInSplitPoint != null) {
                hashInSplitPoint = fileInSplitPoint.getName();
            }

            // rule 1
            if (currentFileHash.equals(hashInSplitPoint) && hashInOtherCommit != null
                    && !hashInOtherCommit.equals(currentFileHash)) {
                fileReferences.put(fileName, fileInOtherCommit);
                fileHashes.add(hashInOtherCommit);
                //                System.out.println("file " + fileName + " falls in rule 1");
                changed = true;
            } else if (hashInOtherCommit != null && hashInOtherCommit.equals(hashInSplitPoint)
                    && !hashInOtherCommit.equals(currentFileHash)) {
                // rule 2
                fileReferences.put(fileName, blobFile);
                fileHashes.add(currentFileHash);
            } else if (currentFileHash.equals(hashInOtherCommit)) {
                // rule 3
                fileReferences.put(fileName, blobFile);
                fileHashes.add(currentFileHash);
            } else if (hashInOtherCommit == null && hashInSplitPoint == null) {
            // rule 4
                fileReferences.put(fileName, blobFile);
                fileHashes.add(currentFileHash);
            } else if (currentFileHash.equals(hashInSplitPoint) && hashInOtherCommit == null) {
                // rule 6
                File file = join(CWD, fileName);
                file.delete();
                changed = true;
            } else {
                // rule 8

                String newContent = "<<<<<<< HEAD\n" + readContentsAsString(blobFile) + "=======\n";
                if (hashInOtherCommit != null) {
                    newContent += readContentsAsString(fileInOtherCommit);
                }
                newContent += ">>>>>>>\n";
                // create blob
                String newHash = sha1(newContent + fileName);
                File newBlob = join(BLOBS, newHash);
                createFile(newBlob);
                Utils.writeContents(newBlob, newContent);
                fileReferences.put(fileName, newBlob);
                fileHashes.add(newHash);
                mergeConflict = true;
                changed = true;
            }
        }

        mergeSecond(otherCommit, currentCommit, splitPoint, fileReferences,
                fileHashes, changed, mergeConflict, branchName);
    }

    static void mergeSecond(Commit otherCommit, Commit currentCommit,
                            Commit splitPoint, Map<String, File> fileReferences,
                            Set<String> fileHashes, boolean changed, boolean mergeConflict,
                             String branchName) {
        Branches branches = readObject(BRANCHES, Branches.class);
        String currentBranch = readContentsAsString(HEAD);
        // rule 5
        for (Map.Entry<String, File> entry : otherCommit.fileReferences.entrySet()) {

            String fileName = entry.getKey();
            File blobFile = entry.getValue();
            String hashInOtherCommit = blobFile.getName();
            //            System.out.println("doing things2 with file " + fileName);
            File fileInCurrentCommit = currentCommit.fileReferences.get(fileName);
            String hashInCurrentCommit = null;
            if (fileInCurrentCommit != null) {
                hashInCurrentCommit = fileInCurrentCommit.getName();
            }

            File fileInSplitPoint = splitPoint.fileReferences.get(fileName);
            String hashInSplitPoint = null;
            if (fileInSplitPoint != null) {
                hashInSplitPoint = fileInSplitPoint.getName();
            }
            // rule 5
            if (hashInSplitPoint == null && hashInCurrentCommit == null) {
                fileReferences.put(fileName, blobFile);
                fileHashes.add(hashInOtherCommit);
                changed = true;
            } else if (hashInOtherCommit.equals(hashInSplitPoint) && hashInCurrentCommit == null) {
                // rule 7
                File file = join(CWD, fileName);
                if (file.exists()) {
                    file.delete();
                }

            } else if (hashInCurrentCommit == null) {
                // rule 8
                String newContent = "<<<<<<< HEAD\n=======\n" + readContentsAsString(blobFile);
                newContent += ">>>>>>>\n";
                // create blob
                String newHash = sha1(newContent + fileName);
                File newBlob = join(BLOBS, newHash);
                createFile(newBlob);
                Utils.writeContents(newBlob, newContent);
                fileReferences.put(fileName, newBlob);
                fileHashes.add(newHash);
                mergeConflict = true;
                changed = true;
            }

        }

        if (!changed) {
            System.out.println("No changes added to the commit.");
            System.exit(0);
        }
        Commit newCommit = new Commit("Merged " + branchName + " into "
                + currentBranch + ".", currentCommit.hash,
                otherCommit.hash, fileReferences, fileHashes);
        // check if there is nothing to commit ??

        newCommit.create(COMMITS);
        newCommit.addFiles(CWD, BLOBS);

        // change branch
        branches.branches.remove(currentBranch);
        branches.branches.put(currentBranch, newCommit.hash);
        Utils.writeObject(BRANCHES, branches);

        if (mergeConflict) {
            System.out.println("Encountered a merge conflict.");
        }
    }


    static Commit splitPoint(Commit a, Commit b) {
        Set<String> hashes = new HashSet<>();
        LinkedList<Commit> q = new LinkedList<>();
        q.addLast(a);
        hashes.add(a.hash);
        while (!q.isEmpty()) {
            Commit next = q.removeFirst();
            String hash = next.parentHash;
            for (int i = 0; i < 2; i++) {
                if (hash == null) {
                    break;
                }
                Commit parentCommit = readObject(join(COMMITS, hash), Commit.class);
                q.addLast(parentCommit);
                hashes.add(parentCommit.hash);
                hash = next.parent2Hash;
            }
        }
        q.addLast(b);
        while (!q.isEmpty()) {
            Commit next = q.removeFirst();
            if (hashes.contains(next.hash)) {
                return next;
            }
            String hash = next.parentHash;
            for (int i = 0; i < 2; i++) {
                if (hash == null) {
                    break;
                }
                Commit parentCommit = readObject(join(COMMITS, hash), Commit.class);
                q.addLast(parentCommit);
                hash = next.parent2Hash;
            }
        }
        return null;
    }


    // checks that given the correct number of arguments and work in an initialized directory
    static void initialized() {
        if (!GITLET_DIR.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }
    }

    static void createFile(File file) {
        try {
            file.createNewFile();
        } catch (IOException e) {
            System.out.println("smth went wrong");
        }
    }
}
