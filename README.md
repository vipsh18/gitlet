# gitlet
💻 Source Code for Berkeley's CS61B Gitlet (Spring 2021) project  

The course was not taken as part of Berkeley University's curriculum but rather self-undertaken as a skill enhancement course.

Gitlet is a command-line tool which works as a repository manager and helps in version controlling of software.  
Gitlet is just like Git (excluding some features like Git contains support for remote repositories while Gitlet does not, it is a
single source version control system). Most of the command names have been kept similar to Git.

## How to use
Clone this repository and run `javac gitlet/*.java`.  
Then to initialize a repository run `java gitlet.Main init`.  
Adding a file to be tracked and staged is as simple as `java gitlet.Main filename`.

## Screenshots
### Initialize the Gitlet repository.
![Initialize the Gitlet repository](./images/init.png)

### Adding a file to the staging area
![Adding a file to the staging area](./images/add.png)

### Directory Structure after adding a file
![Directory Structure after adding a file](./images/structure.png)

### Creating a new commit
![Creating a new commit](./images/commit.png)

### Checking out a file
![Checking out a file](./images/checkout.png)

### Creating a new branch
![Creating a new branch](./images/branch.png)

### Get repository status
![Get repository status](./images/status.png)

### Get branch log
![Get branch log](./images/log.png)
