package gitlet;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author TODO
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please enter a command.");
            System.exit(0);
        }
        String firstArg = args[0];
        switch (firstArg) {
            case "init":
                if (args.length > 1) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }
                Repository.init();
                break;
            case "add":
                Repository.initialized();
                if (args.length > 2) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }
                Repository.add(args[1]);
                break;
            case "commit":
                Repository.initialized();
                if (args.length > 2) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                } else if (args.length == 1 || args[1].length() == 0) {
                    System.out.println("Please enter a commit message.");
                    System.exit(0);
                }
                Repository.commit(args[1]);
                break;
            case "rm":
                if (args.length > 2) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }
                Repository.initialized();
                Repository.rm(args[1]);
                break;
            case "log":
                if (args.length > 1) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }
                Repository.initialized();
                Repository.log();
                break;
            case "global-log":
                if (args.length > 1) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }
                Repository.initialized();
                Repository.globalLog();
                break;
            case "find":
                if (args.length > 2 || args.length == 1) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }
                Repository.initialized();
                Repository.find(args[1]);
                break;
            case "checkout":
                if (args.length == 1) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }
                if (args[1].equals("--")) {
                    Repository.currentCheckout(args[2]);
                } else if (args.length == 2) {
                    // branch checkout
                    Repository.branchCheckOut(args[1]);
                } else if (args[2].equals("--")) {
                    Repository.idCheckout(args[1], args[3]);
                } else {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }
                break;
            case "branch":
                if (args.length == 1) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }
                Repository.initialized();
                Repository.branch(args[1]);
                break;
            case "rm-branch":
                if (args.length != 2) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }
                Repository.initialized();
                Repository.removeBranch(args[1]);
                break;
            case "status":
                Repository.initialized();
                Repository.status();
                break;
            case "reset":
                if (args.length != 2) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }
                Repository.initialized();
                Repository.reset(args[1]);
                break;
            case "merge":
                if (args.length != 2) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }
                Repository.initialized();
                Repository.merge(args[1]);
                break;
            default:
                System.out.println("No command with that name exists.");
                break;
        }
    }
}
