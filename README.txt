To run the program execute "./launcher.sh <config-file-name> <netid>"
To check if program has ended run "cat *.out|wc -l". If the program has ended output of this command will be (number of node*number of failure events)
To verify if the program ran successfully(all the states after recovery are consistent) run "./checkFiles.sh <config-file-name>"
To run cleanup execute "./cleanup.sh <config-file-name> <netid>"