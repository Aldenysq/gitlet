package gitlet;

import java.io.Serializable;
import java.util.HashMap;

public class Branches implements Serializable {

    HashMap<String, String> branches;
    // name -> hash
    public Branches(String masterHash) {
        branches = new HashMap<>();
        branches.put("master", masterHash);
    }
}
