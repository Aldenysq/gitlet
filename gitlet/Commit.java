package gitlet;
import java.io.File;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Date;
import java.util.List;

import static gitlet.Utils.*;
import static gitlet.Utils.sha1;

public class Commit implements Serializable, Dumpable {

    String message;
    String hash;
    String parentHash; //parent's hash
    String timestamp;
    Map<String, File> fileReferences; // file name -> blob reference
    Set<String> fileHashes; // file hashes that I have
    String parent2Hash;

    public Commit(String message, String parentHash, File dir) {
        this.parent2Hash = null;
        this.message = message;
        this.parentHash = parentHash;
        this.fileReferences = new HashMap<>();
        this.fileHashes = new HashSet<>();
        if (parentHash == null) {
            this.timestamp = String.format("%1$ta %1$tb %1$td %1$tT %1$tY %1$tz", new Date(0));
        } else {
            // read parent's commit
            Commit parent = Utils.readObject(Utils.join(dir, parentHash), Commit.class);


            // copy parent's files
            fileReferences.putAll(parent.fileReferences);
            for (String parentFileHash: parent.fileHashes) {
                this.fileHashes.add(parentFileHash);
            }
            this.timestamp = String.format("%1$ta %1$tb %1$td %1$tT %1$tY %1$tz", new Date());
        }
    }
    public Commit(String message, String parentHash, String parent2Hash,
                  Map<String, File> fileReferences, Set<String> fileHashes) {
        this.message = message;
        this.parent2Hash = parent2Hash;
        this.parentHash = parentHash;
        this.fileReferences = fileReferences;
        this.fileHashes = fileHashes;
        this.timestamp = String.format("%1$ta %1$tb %1$td %1$tT %1$tY %1$tz", new Date());
    }

    public void create(File commits) {
        this.hash = Utils.sha1(Utils.serialize(this));
        File commitFile =  join(commits, this.hash);
        Repository.createFile(commitFile);
        Utils.writeObject(commitFile, this);
    }

    public void modify(File addition, File removal, File blobs) {
        // add from addition
        List<String> fileNamesAdd = plainFilenamesIn(addition);
        for (String fileName: fileNamesAdd) {
            File addingFile = join(addition, fileName);
            String fileContent = readContentsAsString(addingFile);
            String newFileHash = sha1(fileContent + fileName);

            // create blob
            File fileBlob = join(blobs, newFileHash);
            if (!fileBlob.exists()) {
                Repository.createFile(fileBlob);
                Utils.writeContents(fileBlob, fileContent);
            }
            if (this.fileReferences.containsKey(fileName)) {
                // we should remove old hash from our set
                File oldFile = this.fileReferences.get(fileName);
                String oldHash = sha1(readContentsAsString(oldFile) + fileName);
                this.fileHashes.remove(oldHash);
            }
            this.fileReferences.put(fileName, fileBlob);
            this.fileHashes.add(newFileHash);
            // delete file from addition stage
            addingFile.delete();
        }

        // remove from removal
        List<String> fileNamesRemove = plainFilenamesIn(removal);
        for (String fileName: fileNamesRemove) {
            if (this.fileReferences.containsKey(fileName)) {
                String fileContent = readContentsAsString(this.fileReferences.get(fileName));
                this.fileHashes.remove(sha1(fileContent + fileName));
                this.fileReferences.remove(fileName);
                File removalFile = join(removal, fileName);
                removalFile.delete();
            }
        }
        if (fileNamesAdd.size() + fileNamesRemove.size() == 0) {
            System.out.println("No changes added to the commit.");
            System.exit(0);
        }
    }

    public void addFiles(File cwd, File blobs) {
        for (Map.Entry<String, File> entry : this.fileReferences.entrySet()) {
            String fileName = entry.getKey();
            File blobFile = entry.getValue();
            File cwdFile = join(cwd, fileName);
            Repository.createFile(cwdFile);
            Utils.writeContents(cwdFile, Utils.readContentsAsString(blobFile));
        }
    }

    public void log() {
        System.out.println("===");
        System.out.println("commit " + this.hash);
        System.out.println("Date: " + this.timestamp);
        System.out.println(this.message);
        // TODO two parents ?? merging issue
        System.out.println();
    }

    @Override
    public void dump() {


    }
}
