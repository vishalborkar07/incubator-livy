#   Remote Debugging
* #### Clone currunt repository 
* #### Switch to branch newbranch2
* #### Build the code with 
--- 
    mvn clean install 
---
* #### Copy the `apache-livy-0.8.0-incubating-SNAPSHOT-bin` zip file from `assembly\target` to currunt directory

* #### Build the docker image using 
---
    docker build -t <imagename> .
--- 
* #### Run doceker image with exposing ports 8998 for livy ui and 5005 for remote debugging with command 
---
    docker run -p 8998:8998 -p 5005:5005 -e "LIVY_SERVER_JAVA_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"  <imagename> 
---

* #### Open project in IDE for example intellij IDEA connect to debugging port 5005
