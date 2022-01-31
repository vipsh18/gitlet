package gitlet;

import static gitlet.Utils.*;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author Vipul Sharma
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please enter a command.\n" + VALID_COMMANDS);
            return;
        }
        
        String firstArg = args[0];
    
        switch (firstArg) {
            case "init" -> {
                Repository.checkValidArguments(args, 1, 1);
                Repository.initRepo();
            }
            case "add" -> {
                Repository.checkValidStructure(args, 2, 2);
                Repository.add(args[1], true);
            }
            case "commit" -> {
                Repository.checkValidStructure(args, 2, 2);
                Repository.commit(args[1]);
            }
            case "rm" -> {
                Repository.checkValidStructure(args, 2, 2);
                Repository.rm(args[1]);
            }
            case "log" -> {
                Repository.checkValidStructure(args, 1, 1);
                Repository.logCommits();
            }
            case "global-log" -> {
                Repository.checkValidStructure(args, 1, 1);
                Repository.globalLog();
            }
            case "find" -> {
                Repository.checkValidStructure(args, 2, 2);
                Repository.find(args[1]);
            }
            case "status" -> {
                Repository.checkValidStructure(args, 1, 1);
                Repository.status();
            }
            case "checkout" -> {
                Repository.checkValidStructure(args, 2, 4);
                Repository.checkout(args);
            }
            case "branch" -> {
                Repository.checkValidStructure(args, 2, 2);
                Repository.branch(args[1]);
            }
            case "rm-branch" -> {
                Repository.checkValidStructure(args, 2, 2);
                Repository.removeBranch(args[1]);
            }
            case "reset" -> {
                Repository.checkValidStructure(args, 2, 2);
                Repository.reset(args[1]);
            }
            case "merge" -> {
                Repository.checkValidStructure(args, 2, 2);
                Repository.merge(args[1]);
            }
            case "help" -> {
                Repository.checkValidArguments(args, 1, 1);
                System.out.println(VALID_COMMANDS);
            }
            default -> System.out.println("No command with that name exists.\n" + VALID_COMMANDS);
        }
    }
}
