# Set the base image to Ubuntu
FROM ubuntu:latest

# Install dependencies
RUN apt-get update && \
    apt-get install -y openjdk-8-jdk wget curl unzip && \
    rm -rf /var/lib/apt/lists/*

# Set environment variables
ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
# ENV SPARK_VERSION=2.4.5
# ENV HADOOP_VERSION=2.7
ENV LIVY_VERSION=0.8.0-incubating-SNAPSHOT-bin
# ARG LIVY_VERSION=0.8.0-incubating-SNAPSHOT-bin
ARG ROOT_PATH=/opt

RUN apt-get update \
    && apt-get install -y unzip

ENV LIVY_HOME=${ROOT_PATH}/livy

ENV LIVY_PACKAGE=apache-livy-${LIVY_VERSION}

# COPY ${LIVY_PACKAGE}.zip /
# Download and install Spark
# RUN set -eux; \
#     url="https://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz"; \
#     filename="$(basename "$url")"; \
#     wget --progress=bar:force:noscroll -O "$filename" "$url"; \
#     tar xzf "$filename" -C /opt/; \
#     rm "$filename"; \
#     mv "/opt/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}" /opt/spark


ARG HADOOP_VERSION=3.3.1
ARG SPARK_VERSION=3.2.3
ARG ROOT_PATH=/opt

RUN mkdir -p ${ROOT_PATH}

ENV HADOOP_HOME=${ROOT_PATH}/hadoop
ENV HADOOP_CONF_DIR=${HADOOP_HOME}/etc/hadoop
ENV PATH=${PATH}:${HADOOP_HOME}/bin
ENV HADOOP_PACKAGE=hadoop-${HADOOP_VERSION}

COPY ${HADOOP_PACKAGE}.tar.gz ${HADOOP_PACKAGE}.tar.gz

RUN gunzip ${HADOOP_PACKAGE}.tar.gz \
 && tar -xf ${HADOOP_PACKAGE}.tar -C ${ROOT_PATH}/ \
 && ln -s ${ROOT_PATH}/${HADOOP_PACKAGE} ${HADOOP_HOME} \
 && rm -rf ${HADOOP_HOME}/share/doc \
 && chown -R root:root ${HADOOP_HOME} \
 && rm ${HADOOP_PACKAGE}.tar

ENV SPARK_HOME=${ROOT_PATH}/spark
ENV SPARK_DIST_CLASSPATH="${HADOOP_HOME}/etc/hadoop/*:${HADOOP_HOME}/share/hadoop/common/lib/*:${HADOOP_HOME}/share/hadoop/common/*:${HADOOP_HOME}/share/hadoop/hdfs/*:${HADOOP_HOME}/share/hadoop/hdfs/lib/*:${HADOOP_HOME}/share/hadoop/hdfs/*:${HADOOP_HOME}/share/hadoop/yarn/lib/*:${HADOOP_HOME}/share/hadoop/yarn/*:${HADOOP_HOME}/share/hadoop/mapreduce/lib/*:${HADOOP_HOME}/share/hadoop/mapreduce/*:${HADOOP_HOME}/share/hadoop/tools/lib/*"
ENV PATH=${PATH}:${SPARK_HOME}/bin
ENV SPARK_PACKAGE=spark-${SPARK_VERSION}-bin-without-hadoop

COPY ${SPARK_PACKAGE}.tgz ${SPARK_PACKAGE}.tgz

RUN gunzip ${SPARK_PACKAGE}.tgz \
 && tar -xf ${SPARK_PACKAGE}.tar -C ${ROOT_PATH}/ \
 && ln -s ${ROOT_PATH}/${SPARK_PACKAGE} ${SPARK_HOME} \
 && chown -R root:root ${SPARK_HOME} \
 && rm ${SPARK_PACKAGE}.tar

# WORKDIR /opt/
ARG LIVY_VERSION = apache-livy-0.8.0-incubating-SNAPSHOT-bin

COPY apache-livy-0.8.0-incubating-SNAPSHOT-bin.zip /

RUN unzip "apache-livy-0.8.0-incubating-SNAPSHOT-bin.zip"
RUN    rm "apache-livy-0.8.0-incubating-SNAPSHOT-bin.zip"
RUN    mv "apache-livy-0.8.0-incubating-SNAPSHOT-bin" /opt/livy/

# Download and install Livy
# RUN set -eux; \
#     url="https://archive.apache.org/dist/incubator/livy/${LIVY_VERSION}/apache-livy-${LIVY_VERSION}-bin.zip"; \
#     filename="$(basename "$url")"; \
#     wget --progress=bar:force:noscroll -O "$filename" "$url"; \
#     unzip "$filename"; \
#     rm "$filename"; \
#     mv "apache-livy-${LIVY_VERSION}-bin" /opt/livy
#     rm "$filename" \
#     mv "apache-livy-${LIVY_VERSION}-bin" /opt/livy/ 

# Configure Livy
ENV SPARK_HOME=/opt/spark
ENV PATH=$PATH:$SPARK_HOME/bin
# RUN cp /opt/livy/conf/log4j.properties.template /opt/livy/conf/log4j.properties
RUN mkdir -p /opt/livy/apache-livy-0.8.0-incubating-SNAPSHOT-bin/logs 
RUN apt-get update && \
    apt-get install -y scala
ENV PATH="/opt/scala/bin:${PATH}"

# COPY log4j.properties /opt/livy/che-livy-0.8.0-incubating-SNAPSHOT-bin/conf
# RUN chmod 644 /opt/livy/apache-livy-0.8.0-incubating-SNAPSHOT-bin/conf/log4j.properties
# RUN export LIVY_SERVER_JAVA_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005

# Expose ports
EXPOSE 8998
# Start Livy
RUN chmod 777 /opt/livy/apache-livy-0.8.0-incubating-SNAPSHOT-bin/bin/livy-server
CMD ["/opt/livy/apache-livy-0.8.0-incubating-SNAPSHOT-bin/bin/livy-server"]
# CMD ["/bin/bash"]