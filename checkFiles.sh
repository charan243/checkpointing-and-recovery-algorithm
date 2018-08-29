CONFIG=$1
line=$(head -1 $CONFIG)
node_count=$(echo $line | cut -f1 -d" ")
failure_count=$(echo $line | cut -f2 -d" ")

javac checkFiles.java
java checkFiles $node_count $failure_count
