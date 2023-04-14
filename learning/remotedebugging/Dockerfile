# Set the base image to Ubuntu
FROM ubuntu:latest

# Install dependencies
RUN apt-get update && \
    apt-get install -y openjdk-8-jdk wget curl unzip && \
    rm -rf /var/lib/apt/lists/*

# Set environment variables
ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
ENV SPARK_VERSION=2.4.5
ENV HADOOP_VERSION=2.7
ENV LIVY_VERSION=0.8.0-incubating-SNAPSHOT-bin
# ARG LIVY_VERSION=0.8.0-incubating-SNAPSHOT-bin
ARG ROOT_PATH=/opt

RUN apt-get update \
    && apt-get install -y unzip

ENV LIVY_HOME=${ROOT_PATH}/livy

ENV LIVY_PACKAGE=apache-livy-${LIVY_VERSION}

# COPY ${LIVY_PACKAGE}.zip /
# Download and install Spark
RUN set -eux; \
    url="https://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz"; \
    filename="$(basename "$url")"; \
    wget --progress=bar:force:noscroll -O "$filename" "$url"; \
    tar xzf "$filename" -C /opt/; \
    rm "$filename"; \
    mv "/opt/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}" /opt/spark

# WORKDIR /opt/
ARG LIVY_VERSION=apache-livy-0.8.0-incubating-SNAPSHOT-bin

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
RUN mkdir -p /opt/livy/logs 
RUN apt-get update && \
    apt-get install -y scala
ENV PATH="/opt/scala/bin:${PATH}"

COPY log4j.properties /opt/livy/conf
# RUN chmod 644 /opt/livy/conf/log4j.properties
RUN export LIVY_SERVER_JAVA_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005

# Expose ports
EXPOSE 8998
# Start Livy
RUN chmod 777 /opt/livy/bin/livy-server
CMD ["/opt/livy/bin/livy-server"]
# CMD ["/bin/bash"]